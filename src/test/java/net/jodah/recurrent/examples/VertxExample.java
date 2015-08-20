package net.jodah.recurrent.examples;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RetryPolicy;
import net.jodah.recurrent.Scheduler;
import net.jodah.recurrent.util.concurrent.DefaultScheduledFuture;

public class VertxExample {
  static Vertx vertx = Vertx.vertx();

  /** Create RetryPolicy to handle Vert.x failures */
  static RetryPolicy retryPolicy = new RetryPolicy()
      .retryOn((ReplyException failure) -> ReplyFailure.RECIPIENT_FAILURE.equals(failure.failureType())
          || ReplyFailure.TIMEOUT.equals(failure.failureType()));

  /** Adapt Vert.x timer to a Recurrent Scheduler */
  static Scheduler scheduler = (callable, delay, unit) -> {
    Runnable runnable = () -> {
      try {
        callable.call();
      } catch (Exception ignore) {
      }
    };

    return new DefaultScheduledFuture<Object>() {
      long timerId;

      {
        if (delay == 0)
          vertx.getOrCreateContext().runOnContext(e -> runnable.run());
        else
          timerId = vertx.setTimer(unit.toMillis(delay), tid -> runnable.run());
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return delay == 0 ? false : vertx.cancelTimer(timerId);
      };
    };
  };

  /**
   * A Vert.x sender and retryable receiver example.
   */
  public static void main(String... args) throws Throwable {
    // Receiver that fails 3 times then succeeds
    AtomicInteger failures = new AtomicInteger();
    vertx.eventBus().consumer("ping-address", message -> {
      if (failures.getAndIncrement() < 3)
        message.fail(1, "Failed");
      else {
        message.reply("pong!");
      }
    });

    // Retryable sender
    Recurrent.run(invocation -> vertx.eventBus().send("ping-address", "ping!", reply -> {
      if (reply.succeeded())
        System.out.println("Received reply " + reply.result().body());
      else if (!invocation.retryOn(reply.cause()))
        System.out.println("Invocation and retries failed");
    }), retryPolicy.copy().withDelay(1, TimeUnit.SECONDS), scheduler);

    Thread.sleep(5000);
  }
}
