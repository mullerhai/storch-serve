import scala.collection.immutable.Seq

ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / organization := "io.github.mullerhai" //"dev.storch"
ThisBuild / organizationName := "storch.dev"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("mullerhai", "mullerhai")
)
publishTo := sonatypePublishToBundle.value
import xerial.sbt.Sonatype.sonatypeCentralHost
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)

lazy val root = (project in file("."))
  .settings(
    name := "storch-serve"
  )
//libraryDependencies ++= Seq(
//  "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
//  "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0"
//)
// https://mvnrepository.com/artifact/com.google.api.grpc/proto-google-common-protos
libraryDependencies += "com.google.api.grpc" % "proto-google-common-protos" % "2.54.1"
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % "1.0.0-alpha.1"
// https://mvnrepository.com/artifact/com.thesamet.scalapb/scalapb-runtime
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % "1.0.0-alpha.1"
// (optional) If you need scalapb/scalapb.proto or anything from
// google/protobuf/*.proto
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)
// https://mvnrepository.com/artifact/io.grpc/grpc-all
libraryDependencies += "io.grpc" % "grpc-all" % "1.71.0"
//libraryDependencies += "io.netty" % "netty-all" % "5.0.0.Alpha2"
// https://mvnrepository.com/artifact/io.netty/netty-all // https://mvnrepository.com/artifact/io.netty/netty-all
//libraryDependencies += "io.netty" % "netty-all" % "4.1.119.Final"
//libraryDependencies += "io.netty" % "netty-all" % "5.0.0.Alpha2"
libraryDependencies += "io.netty" % "netty-all" % "4.2.0.Final"
// https://mvnrepository.com/artifact/io.prometheus/simpleclient
libraryDependencies += "io.prometheus" % "simpleclient" % "0.16.0"

// https://mvnrepository.com/artifact/io.prometheus/simpleclient_servlet
libraryDependencies += "io.prometheus" % "simpleclient_servlet" % "0.16.0"
// https://mvnrepository.com/artifact/org.yaml/snakeyaml
libraryDependencies += "org.yaml" % "snakeyaml" % "2.4"
// https://mvnrepository.com/artifact/commons-cli/commons-cli
libraryDependencies += "commons-cli" % "commons-cli" % "1.9.0"
// https://mvnrepository.com/artifact/org.pytorch/torchserve-plugins-sdk
libraryDependencies += "org.pytorch" % "torchserve-plugins-sdk" % "0.0.5"
// https://mvnrepository.com/artifact/com.lmax/disruptor
libraryDependencies += "com.lmax" % "disruptor" % "4.0.0"
// https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-core
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.17.1"//3.0.0-beta3"
// https://mvnrepository.com/artifact/org.apache.groovy/groovy-all
libraryDependencies += "org.apache.groovy" % "groovy-all" % "5.0.0-alpha-12" pomOnly()
// https://mvnrepository.com/artifact/org.testng/testng
libraryDependencies += "org.testng" % "testng" % "7.11.0" % Test
libraryDependencies += "commons-io" % "commons-io" % "2.19.0"
// https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-slf4j-impl
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.1" //3.0.0-beta2"
//libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "3.0"

libraryDependencies += "com.google.code.gson" % "gson" % "2.13.0"

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.25.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-protobuf
libraryDependencies += "io.grpc" % "grpc-protobuf" % "1.71.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-api
libraryDependencies += "io.grpc" % "grpc-api" % "1.71.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-netty
libraryDependencies += "io.grpc" % "grpc-netty" % "1.71.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-stub
libraryDependencies += "io.grpc" % "grpc-stub" % "1.71.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-core
libraryDependencies += "io.grpc" % "grpc-core" % "1.71.0"
// https://mvnrepository.com/artifact/io.grpc/grpc-netty-shaded
libraryDependencies += "io.grpc" % "grpc-netty-shaded" % "1.71.0"
//api "commons-io:commons-io:2.16.1"
//api "org.slf4j:slf4j-api:${slf4j_api_version}"
//api "org.apache.logging.log4j:log4j-slf4j-impl:${slf4j_log4j_version}"
//api "com.google.code.gson:gson:${gson_version}"
//implementation "org.yaml:snakeyaml:${snakeyaml_version}"
//implementation 'org.apache.commons:commons-compress:1.25.0'
//
//testImplementation "commons-cli:commons-cli:${commons_cli_version}"
//testImplementation "org.testng:testng:${testng_version}"
//}
//implementation "io.netty:netty-all:${netty_version}"
//implementation "io.prometheus:simpleclient:${prometheus_version}"
//implementation "io.prometheus:simpleclient_servlet:${prometheus_version}"
//implementation "org.yaml:snakeyaml:${snakeyaml_version}"
//implementation project(":archive")
//implementation "commons-cli:commons-cli:${commons_cli_version}"
//implementation "org.pytorch:torchserve-plugins-sdk:${torchserve_sdk_version}"
//implementation "com.lmax:disruptor:${lmax_disruptor_version}"
//implementation "org.apache.logging.log4j:log4j-core:${slf4j_log4j_version}"
//implementation "org.codehaus.groovy:groovy-all:${groovy_version}"
//testImplementation "org.testng:testng:${testng_version}"

ThisBuild  / assemblyMergeStrategy := {
  case v if v.contains("module-info.class")   => MergeStrategy.discard
  case v if v.contains("UnusedStub")          => MergeStrategy.first
  case v if v.contains("aopalliance")         => MergeStrategy.first
  case v if v.contains("inject")              => MergeStrategy.first
  case v if v.contains("jline")               => MergeStrategy.discard
  case v if v.contains("scala-asm")           => MergeStrategy.discard
  case v if v.contains("asm")                 => MergeStrategy.discard
  case v if v.contains("scala-compiler")      => MergeStrategy.deduplicate
  case v if v.contains("reflect-config.json") => MergeStrategy.discard
  case v if v.contains("jni-config.json")     => MergeStrategy.discard
  case v if v.contains("git.properties")      => MergeStrategy.discard
  case v if v.contains("reflect.properties")      => MergeStrategy.discard
  case v if v.contains("compiler.properties")      => MergeStrategy.discard
  case v if v.contains("scala-collection-compat.properties")      => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}