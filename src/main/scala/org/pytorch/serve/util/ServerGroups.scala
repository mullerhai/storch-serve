package org.pytorch.serve.util

import scala.jdk.CollectionConverters.*
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.EventLoopGroup
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.ChannelGroupFuture
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import org.pytorch.serve.util.Connector

import java.util
import java.util.concurrent.TimeUnit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._

object ServerGroups {
  private val logger = LoggerFactory.getLogger(classOf[ServerGroups])
}

class ServerGroups(private var configManager: ConfigManager) {
  init()
  private var allChannels: ChannelGroup = null
  private var serverGroup: EventLoopGroup = null
  private var childGroup: EventLoopGroup = null
  private var metricsGroup: EventLoopGroup = null
  private var backendGroup: EventLoopGroup = null

  final def init(): Unit = {
    allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    serverGroup = Connector.newEventLoopGroup(2)
    childGroup = Connector.newEventLoopGroup(configManager.getNettyThreads)
    if (configManager.isMetricApiEnable) metricsGroup = Connector.newEventLoopGroup(1)
    backendGroup = Connector.newEventLoopGroup(configManager.getNettyClientThreads)
  }

  def shutdown(graceful: Boolean): Unit = {
    closeAllChannels(graceful)
    val allEventLoopGroups = new util.ArrayList[EventLoopGroup]
    allEventLoopGroups.add(serverGroup)
    allEventLoopGroups.add(childGroup)
    if (configManager.isMetricApiEnable) allEventLoopGroups.add(metricsGroup)
//    import scala.collection.JavaConversions._
    for (group <- allEventLoopGroups.asScala) {
      if (graceful) group.shutdownGracefully
      else group.shutdownGracefully(0, 0, TimeUnit.SECONDS)
    }
    if (graceful) {
//      import scala.collection.JavaConversions._
      for (group <- allEventLoopGroups.asScala) {
        try group.awaitTermination(60, TimeUnit.SECONDS)
        catch {
          case e: InterruptedException =>
            Thread.currentThread.interrupt()
        }
      }
    }
  }

  def getServerGroup: EventLoopGroup = serverGroup

  def getChildGroup: EventLoopGroup = childGroup

  def getMetricsGroup: EventLoopGroup = metricsGroup

  def getBackendGroup: EventLoopGroup = backendGroup

  def registerChannel(channel: Channel): Unit = {
    allChannels.add(channel)
  }

  private def closeAllChannels(graceful: Boolean): Unit = {
    val future = allChannels.close
    // if this is a graceful shutdown, log any channel closing failures. if this isn't a
    // graceful shutdown, ignore them.
    if (graceful) {
      try future.await(10, TimeUnit.SECONDS)
      catch {
        case e: InterruptedException =>
          Thread.currentThread.interrupt()
      }
      if (!future.isSuccess) {
//        import scala.collection.JavaConversions._
        for (cf <- future.asScala) {
          if (!cf.isSuccess) ServerGroups.logger.info("Unable to close channel: " + cf.channel, cf.cause)
        }
      }
    }
  }
}