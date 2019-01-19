package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Policy listener configuration.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public class PolicyListeners<S, R> {
  /**
   * Handles an execution event.
   */
  public interface EventListener {
    void handle(Object result, Throwable failure, ExecutionContext context);

    @SuppressWarnings("unchecked")
    static <R> EventListener of(CheckedConsumer<? extends ExecutionCompletedEvent<R>> handler) {
      return (Object result, Throwable failure, ExecutionContext context) -> {
        try {
          ((CheckedConsumer<ExecutionCompletedEvent<R>>) handler).accept(
              new ExecutionCompletedEvent<>((R) result, failure, context));
        } catch (Exception ignore) {
        }
      };
    }

    @SuppressWarnings("unchecked")
    static <R> EventListener ofAttempt(CheckedConsumer<? extends ExecutionAttemptedEvent<R>> handler) {
      return (Object result, Throwable failure, ExecutionContext context) -> {
        try {
          ((CheckedConsumer<ExecutionAttemptedEvent<R>>) handler).accept(
              new ExecutionAttemptedEvent<>((R) result, failure, context));
        } catch (Exception ignore) {
        }
      };
    }

    default void handle(ExecutionResult result, ExecutionContext context) {
      handle(result.getResult(), result.getFailure(), context);
    }
  }

  EventListener failureListener;
  EventListener successListener;

  /**
   * Registers the {@code listener} to be called when an execution fails for a {@link Policy}.
   */
  public S onFailure(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    failureListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful for a {@link Policy}.
   */
  public S onSuccess(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    successListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
