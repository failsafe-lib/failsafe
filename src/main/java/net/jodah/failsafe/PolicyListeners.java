package net.jodah.failsafe;

import net.jodah.failsafe.event.FailsafeEvent;
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
  public interface EventListener {
    void handle(Object result, Throwable failure, ExecutionContext context);

    @SuppressWarnings("unchecked")
    static <R> EventListener of(CheckedConsumer<? extends FailsafeEvent<R>> handler) {
      return (Object result, Throwable failure, ExecutionContext context) -> {
        try {
          ((CheckedConsumer<FailsafeEvent<R>>) handler).accept(new FailsafeEvent<>((R) result, failure, context));
        } catch (Exception ignore) {
        }
      };
    }

    default void handle(ExecutionResult result, ExecutionContext context) {
      handle(result.result, result.failure, context);
    }
  }

  EventListener failureListener;
  EventListener successListener;

  /**
   * Registers the {@code listener} to be called when an execution fails for a {@link Policy}.
   */
  public S onFailure(CheckedConsumer<? extends FailsafeEvent<R>> listener) {
    failureListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful for a {@link Policy}.
   */
  public S onSuccess(CheckedConsumer<? extends FailsafeEvent<R>> listener) {
    successListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
