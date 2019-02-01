package com.mypackage.benchmark

import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable._
import scala.collection.JavaConverters._


import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.channel.group._
import org.jboss.netty.handler.codec.string._
import org.jboss.netty.handler.codec.frame.Delimiters
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.util.HashedWheelTimer
import org.jboss.netty.util.TimerTask
import org.jboss.netty.util.Timeout

object BenchmarkClientMain {
  val bossExecutor:ExecutorService = Executors.newCachedThreadPool()
  val workerExecutor:ExecutorService = Executors.newCachedThreadPool()
  val channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor)
  val bootstrap = new ClientBootstrap(channelFactory)
  val timer = new HashedWheelTimer()
  val failures = new AtomicInteger(0)
  val successes = new AtomicInteger(0)
  val cancelled = new AtomicInteger(0)
  val failureCauses = new HashSet[String] with SynchronizedSet[String]
  var numConns = 0
  val TEST_TIME_SEC = 20
  var startTime:Long = 0
  val log = org.log4s.getLogger

  def main(args:Array[String]) : Unit = {
    //Configgy.configure("config/nettybenchmark.conf")
    log.info("Launching Netty Client benchmark")
    configureBootstrap
    //val config = Configgy.config
    //val hostname = config.getString("hostname").get
    //val port = config.getInt("port").get
    //numConns = config.getInt("numConns").get
    val hostname = args(0)
    val port = args(1).toInt
    numConns = args(2).toInt
    startTime = System.currentTimeMillis
    for (i <- 0 until numConns) {
      val future:ChannelFuture = bootstrap.connect(new InetSocketAddress(hostname, port))
      future.addListener(newChannelListener)
    }
    log.info(s"Boostrapped $numConns connections in ${System.currentTimeMillis - startTime} millliseconds")
  }

  def configureBootstrap = {
    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline():ChannelPipeline = {
        val pipeline = org.jboss.netty.channel.Channels.pipeline()
        pipeline.addLast("frameDecoder",
          new DelimiterBasedFrameDecoder(65536, true,
            Delimiters.lineDelimiter()(0),
            Delimiters.lineDelimiter()(1)))
        pipeline.addLast("decoder", new StringDecoder(Charset.forName("UTF-8")))
        pipeline.addLast("encoder", new StringEncoder(Charset.forName("UTF-8")))
        pipeline.addLast("handler", new BenchmarkClientHandler())
        pipeline
      }
    })

    bootstrap.setOption("tcpNoDelay", true)
    bootstrap.setOption("keepAlive", true)
    bootstrap.setOption("connectTimeoutMillis", 10000)
    bootstrap.setOption("client.reuseAddress", true)
  }

  def lastChannelConnected = {
    log.info("Finished completing all connections:")
    log.info("\tTook " + ((System.currentTimeMillis - startTime) / 1000.0) + "sec total")
    log.info("\tSucesses: " + successes.get)
    log.info("\tFailures: " + failures.get)
    log.info("\tCancelled: " + cancelled.get)
    if (!failureCauses.isEmpty) {
      log.info("Failure reasons:")
      failureCauses.foreach((failure) => log.info("\t" + failure))
    }

    // Start 1 minute timer before closing everything and finishing
    timer.start
    log.info("Waiting " + TEST_TIME_SEC + " seconds...")
    timer.newTimeout(endTestTimerTask, TEST_TIME_SEC, TimeUnit.SECONDS)
  }

  lazy val endTestTimerTask = new TimerTask() { def run(timeout:Timeout) = {
    log.info("Client timeout reached; closing all channels")
    // Wait until the connection is closed or the connection attempt fails.
    BenchmarkClientHandler.channels.disconnect.addListener(new ChannelGroupFutureListener() {
      def operationComplete(future:ChannelGroupFuture) = {
        log.info("Releasing resources & shutting down threads")
        // Shutdown thread pools and release resources
        bootstrap.releaseExternalResources
        channelFactory.releaseExternalResources
        bossExecutor.shutdownNow
        workerExecutor.shutdownNow

        printStats

        log.info("Exiting")
        // Timer is still running, so we must System.exit
        System.exit(0)
      }
    })
  }}

  def printStats = {
    val responseTimes = BenchmarkClientHandler.responseTimes.asScala.toList
    val sum = responseTimes.reduce(_+_)
    val avg = sum / responseTimes.size
    log.info("Response times:")
    log.info("\tAverage: " + avg + "msec")
    log.info("\tMin: " + responseTimes.min + "msec")
    log.info("\tMax: " + responseTimes.max + "msec")
  }

  lazy val newChannelListener = new ChannelFutureListener() {
    def operationComplete(future:ChannelFuture) = {
      assert(future.isDone)
      if (future.isCancelled()) {
        // Connection attempt cancelled by user
        cancelled.incrementAndGet
      } else if (!future.isSuccess) {
        val reason = future.getCause().toString
        failureCauses.add(reason)
        failures.incrementAndGet
      } else {
        // Connection established successfully
        successes.incrementAndGet
      }

      if (successes.get + failures.get + cancelled.get == numConns) {
        BenchmarkClientMain.lastChannelConnected
      }
    }
  }
}

object BenchmarkClientHandler {
  val channels = new DefaultChannelGroup()
  val responseTimes = new ConcurrentLinkedQueue[Long]()
}

class BenchmarkClientHandler extends SimpleChannelUpstreamHandler {
  
  override def channelConnected(ctx:ChannelHandlerContext, e:ChannelStateEvent) = {
    super.channelConnected(ctx, e)
    BenchmarkClientHandler.channels.add(ctx.getChannel)
    e.getChannel.write("Hello there!\n")
  }

  override def channelClosed(ctx:ChannelHandlerContext, e:ChannelStateEvent) = {
    super.channelClosed(ctx, e)
    BenchmarkClientHandler.channels.remove(ctx.getChannel)
  }

  override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent) = {
    val received = System.currentTimeMillis
    val msg = e.getMessage.asInstanceOf[String]
    val sentTime = msg.toLong
    val diff = received - sentTime
    BenchmarkClientHandler.responseTimes.add(diff)
  }

  override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent) = {
    // Close the connection when an exception is raised.
    val log = org.log4s.getLogger

    log.info(s"Unexpected exception from downstream: ${e.getCause()}")
    e.getChannel().close()
    // Not necessary because channelClosed will be called?
    // BenchmarkClientHandler.channels.remove(e.getChannel)
  }
}
