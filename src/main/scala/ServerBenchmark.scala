package com.mypackage.benchmark

import java.net.InetSocketAddress
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong
import java.nio.charset.Charset
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelEvent
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.group._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.execution.ExecutionHandler
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor
import org.jboss.netty.handler.codec.string._
import org.jboss.netty.handler.codec.frame.Delimiters
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.timeout._
import org.jboss.netty.util.HashedWheelTimer

object BenchmarkServerMain {
  // Configure the server
  val bossExecutor:ExecutorService = Executors.newCachedThreadPool()
  val workerExecutor:ExecutorService = Executors.newCachedThreadPool()
  val channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor)
  val bootstrap = new ServerBootstrap(channelFactory)
  val timer = new HashedWheelTimer()
  val orderedMemoryAwareThreadPoolExecutor = new OrderedMemoryAwareThreadPoolExecutor(
    100, // core pool size
    0,   // maxChannelMemorySize, 0 to disable,
    0    // maxTotalMemorySize, 0 to disable
  ) 
  val executionHandler = new ExecutionHandler(orderedMemoryAwareThreadPoolExecutor)
  val log = org.log4s.getLogger

  def main(args:Array[String]) : Unit = {
    //Configgy.configure("config/nettybenchmark.conf")
    log.info("Launching Netty Server benchmark")

    // Set up the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline():ChannelPipeline = {
        val pipeline = Channels.pipeline()
        pipeline.addLast("frameDecoder",
          new DelimiterBasedFrameDecoder(65536, true,
            Delimiters.lineDelimiter()(0),
            Delimiters.lineDelimiter()(1)))
        pipeline.addLast("decoder", new StringDecoder(Charset.forName("UTF-8")))
        pipeline.addLast("encoder", new StringEncoder(Charset.forName("UTF-8")))
        pipeline.addLast("idleHandler", new IdleStateHandler(timer, 0, 5, 0))
        pipeline.addLast("pipelineExecuter", executionHandler)
        pipeline.addLast("handler", new BenchmarkServerHandler())
        pipeline
      }
    })

    // Bind and start to accept incoming connections.
    bootstrap.setOption("child.keepAlive", true) // for mobiles & our stateful app
    bootstrap.setOption("child.tcpNoDelay", true) // better latency over bandwidth
    bootstrap.setOption("reuseAddress", true) // kernel optimization
    bootstrap.setOption("child.reuseAddress", true) // kernel optimization

    //val config = Configgy.config
    //val port = config.getInt("port").get
    val port = args(0).toInt
    bootstrap.bind(new InetSocketAddress(port))
  }

  def shutdown = {
    BenchmarkServerHandler.channels.close().awaitUninterruptibly()
    timer.stop()
    bootstrap.releaseExternalResources
    orderedMemoryAwareThreadPoolExecutor.shutdownNow
    channelFactory.releaseExternalResources
    bossExecutor.shutdownNow
    workerExecutor.shutdownNow
  }
}

object BenchmarkServerHandler {
  val channels = new DefaultChannelGroup()
}

class BenchmarkServerHandler extends IdleStateAwareChannelUpstreamHandler {
  override def channelIdle(ctx:ChannelHandlerContext, e:IdleStateEvent) = {
    // send keep alive ping
    if (e.getState == IdleState.WRITER_IDLE) {
      val now = System.currentTimeMillis
      ctx.getChannel.write(now + "\n")
    }
  }

  override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent) = {
    // Discard received data silently by doing nothing.
    //transferredBytes.addAndGet(((ChannelBuffer) e.getMessage()).readableBytes())
    val msg = e.getMessage.asInstanceOf[String]
  }

  override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent) = {
    // Close the connection when an exception is raised.
    val log = org.log4s.getLogger
    log.info(s"Unexpected exception from downstream ${e.getCause()}")
    e.getChannel().close()
  }

  override def channelConnected(ctx:ChannelHandlerContext, e:ChannelStateEvent) = {
    super.channelConnected(ctx, e)
    BenchmarkServerHandler.channels.add(ctx.getChannel)
  }

  override def channelClosed(ctx:ChannelHandlerContext, e:ChannelStateEvent) = {
    super.channelClosed(ctx, e)
    BenchmarkServerHandler.channels.remove(ctx.getChannel)
  }
}
