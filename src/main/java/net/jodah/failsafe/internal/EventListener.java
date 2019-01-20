package net.jodah.failsafe.internal;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.ExecutionResult;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;

/**
 * Handles an execution event.
 */
public interface EventListener {
  void handle(Object result, Throwable failure, ExecutionContext context);

  @SuppressWarnings("unchecked")
  public static <R> EventListener of(CheckedConsumer<? extends ExecutionCompletedEvent<R>> handler) {
    return (Object result, Throwable failure, ExecutionContext context) -> {
      try {
        ((CheckedConsumer<ExecutionCompletedEvent<R>>) handler).accept(
            new ExecutionCompletedEvent<>((R) result, failure, context));
      } catch (Exception ignore) {
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <R> EventListener ofAttempt(CheckedConsumer<? extends ExecutionAttemptedEvent<R>> handler) {
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