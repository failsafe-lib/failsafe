package net.jodah.recurrent.examples;

import org.testng.annotations.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RetryPolicy;

@Test
public class NettyExample {
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));
  static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

  public void example() throws Throwable {
    EventLoopGroup group = new NioEventLoopGroup();

    Bootstrap bootstrap = createBootstrap(group);
    RetryPolicy retryPolicy = new RetryPolicy();

    Recurrent
        .get((invocation) -> bootstrap.connect(HOST, PORT).addListener((ChannelFutureListener) channelFuture -> {
          if (channelFuture.isSuccess())
            System.out.println("Connected");
          else if (!invocation.retry(channelFuture.cause()))
            System.out.println("Connection attempts failed");
        }), retryPolicy, group).whenComplete((future, failure) -> {
          try {
            future.sync();
            future.channel().closeFuture().sync();
          } catch (Exception ignore) {
            group.shutdownGracefully();
          }
        });
  }

  Bootstrap createBootstrap(EventLoopGroup group) {
    return new Bootstrap().group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          public void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.write(msg);
              }

              @Override
              public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.flush();
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                // Close the connection when an exception is raised.
                cause.printStackTrace();
                ctx.close();
              }
            });
          }
        });
  }
}
