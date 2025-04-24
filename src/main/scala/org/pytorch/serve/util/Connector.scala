package org.pytorch.serve.util

import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.unix.DomainSocketAddress

import java.io.File
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.Objects
import java.util.regex.Matcher
import org.apache.commons.io.FileUtils
import org.pytorch.serve.util.ConfigManager

import scala.annotation.targetName
import scala.jdk.CollectionConverters.*

object Connector {
  private val useNativeIo = ConfigManager.getInstance.useNativeIo

  def parse(binding: String, connectorType: ConnectorType): Connector = {
    val matcher = ConfigManager.ADDRESS_PATTERN.matcher(binding)
    if (!matcher.matches) throw new IllegalArgumentException("Invalid binding address: " + binding)
    val uds = matcher.group(7) != null
    if (uds) {
      if (!useNativeIo) throw new IllegalArgumentException("unix domain socket requires use_native_io set to true.")
      val path = matcher.group(7)
      return new Connector(-1, true, "", path, false, ConnectorType.MANAGEMENT_CONNECTOR)
    }
    val protocol = matcher.group(2)
    val host = matcher.group(3)
    val listeningPort = matcher.group(5)
    val ssl = "https".equalsIgnoreCase(protocol)
    var port = 0
    if (listeningPort == null) connectorType match {
      case ConnectorType.MANAGEMENT_CONNECTOR =>
        port = if (ssl) 8444
        else 8081
      case ConnectorType.METRICS_CONNECTOR =>
        port = if (ssl) 8445
        else 8082
      case _ =>
        port = if (ssl) 443
        else 80
    }
    else port = listeningPort.toInt
    if (port >= Short.MaxValue * 2 + 1) throw new IllegalArgumentException("Invalid port number: " + binding)
    new Connector(port, false, host, String.valueOf(port), ssl, connectorType)
  }

  def newEventLoopGroup(threads: Int): EventLoopGroup = {
    if (useNativeIo && Epoll.isAvailable) return new EpollEventLoopGroup(threads)
    else if (useNativeIo && KQueue.isAvailable) return new KQueueEventLoopGroup(threads)
    val eventLoopGroup = new NioEventLoopGroup(threads)
    eventLoopGroup.setIoRatio(ConfigManager.getInstance.getIoRatio)
    eventLoopGroup
  }
}

class Connector {
  private var uds = false
  private var socketPath: String = null
  private var bindIp: String = null
  private var port = 0
  private var ssl = false
  private var connectorType: ConnectorType = null

  def this(port: Int, uds: Boolean)= {
    this()
    this.port = port
    this.uds = uds
    if (uds) {
      bindIp = ""
      socketPath = System.getProperty("java.io.tmpdir") + "/.ts.sock." + port
    }
    else {
      bindIp = "127.0.0.1"
      socketPath = String.valueOf(port)
    }
  }

  def this(port: Int) ={
    this(port, Connector.useNativeIo && (Epoll.isAvailable || KQueue.isAvailable))
  }

  def this(port: Int, uds: Boolean, bindIp: String, socketPath: String, ssl: Boolean, connectorType: ConnectorType)= {
    this()
    this.port = port
    this.uds = uds
    this.bindIp = bindIp
    this.socketPath = socketPath
    this.ssl = ssl
    this.connectorType = connectorType
  }

  def getSocketType: String = if (uds) "unix" else "tcp"

  def getSocketPath: String = socketPath

  def isUds: Boolean = uds

  def isSsl: Boolean = ssl

  def isManagement: Boolean = connectorType == ConnectorType.MANAGEMENT_CONNECTOR

  def getSocketAddress: SocketAddress = if (uds) new DomainSocketAddress(socketPath)
  else new InetSocketAddress(bindIp, port)

  def getPurpose: String = connectorType match {
    case ConnectorType.MANAGEMENT_CONNECTOR =>
      "Management"
    case ConnectorType.METRICS_CONNECTOR =>
      "Metrics"
    case _ =>
      "Inference"
  }

  def getServerChannel: Class[? <: ServerChannel] = {
    if (Connector.useNativeIo && Epoll.isAvailable) return if (uds) classOf[EpollServerDomainSocketChannel]
    else classOf[EpollServerSocketChannel]
    else if (Connector.useNativeIo && KQueue.isAvailable) return if (uds) classOf[KQueueServerDomainSocketChannel]
    else classOf[KQueueServerSocketChannel]
    classOf[NioServerSocketChannel]
  }

  def getClientChannel: Class[? <: Channel] = {
    if (Connector.useNativeIo && Epoll.isAvailable) return if (uds) classOf[EpollDomainSocketChannel]
    else classOf[EpollSocketChannel]
    else if (Connector.useNativeIo && KQueue.isAvailable) return if (uds) classOf[KQueueDomainSocketChannel]
    else classOf[KQueueSocketChannel]
    classOf[NioSocketChannel]
  }

  def clean(): Unit = {
    if (uds) FileUtils.deleteQuietly(new File(socketPath))
  }

//  @targetName("equals") //todo need do
//  override def equals(o: AnyRef): Boolean = {
//    if (this eq o) return true
//    if (o == null || (getClass ne o.getClass)) return false
//    val connector = o.asInstanceOf[Connector]
//    uds == connector.uds && port == connector.port && socketPath == connector.socketPath && bindIp == connector.bindIp
//  }

  override def hashCode: Int = Objects.hash(uds, socketPath, bindIp, port)

  override def toString: String = {
    if (uds) return "unix:" + socketPath
    else if (ssl) return "https://" + bindIp + ':' + port
    "http://" + bindIp + ':' + port
  }

//  override def equals(obj: Any): Boolean = super.equals(obj)
}