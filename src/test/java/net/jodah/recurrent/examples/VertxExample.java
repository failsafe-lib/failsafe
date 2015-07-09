package net.jodah.recurrent.examples;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import net.jodah.recurrent.RetryPolicy;
import net.jodah.recurrent.RetryStats;

@Test
public class VertxExample {
  Vertx vertx = Vertx.vertx();

  public void example() throws Throwable {
    AtomicInteger failures = new AtomicInteger();
    RetryPolicy retryPolicy = new RetryPolicy().withDelay(1, TimeUnit.SECONDS);

    // Receiver
    vertx.eventBus().consumer("ping-address", message -> {
      // Fail 3 times then succeed
      if (failures.getAndIncrement() < 3)
        message.fail(1, "Failed");
      else {
        System.out.println("Received message: " + message.body());
        message.reply("pong!");
      }
    });

    // Sender
    retryableSend("ping-address", "ping!", retryPolicy, reply -> {
      System.out.println("Received reply " + reply.result().body());
    });

    Thread.sleep(5000);
  }

  private <T> void retryableSend(String address, Object message, RetryPolicy retryPolicy,
      Handler<AsyncResult<Message<T>>> replyHandler) {
    retryPolicy.retryWhen((ReplyException failure) -> ReplyFailure.RECIPIENT_FAILURE.equals(failure.failureType())
        || ReplyFailure.TIMEOUT.equals(failure.failureType()));

    retryableSend(address, message, new RetryStats(retryPolicy), replyHandler);
  }

  private <T> void retryableSend(String address, Object message, RetryStats retryStats,
      Handler<AsyncResult<Message<T>>> replyHandler) {
    vertx.eventBus().send(address, message, (AsyncResult<Message<T>> reply) -> {
      if (reply.succeeded() || !retryStats.canRetryOn(reply.cause())) {
        replyHandler.handle(reply);
      } else {
        System.out.println("Failure detected");
        vertx.setTimer(TimeUnit.NANOSECONDS.toMillis(retryStats.getWaitTime()), (timerId) -> {
          retryableSend(address, message, retryStats, replyHandler);
        });
      }
    });
  }
}
