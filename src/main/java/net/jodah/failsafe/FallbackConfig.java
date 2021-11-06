package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;

import java.util.concurrent.CompletableFuture;

/**
 * Configuration for a {@link Fallback}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FallbackConfig<R> extends FailurePolicyConfig<R> {
  CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback;
  CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage;
  boolean async;

  FallbackConfig() {
  }

  FallbackConfig(FallbackConfig<R> config) {
    super(config);
    fallback = config.fallback;
    fallbackStage = config.fallbackStage;
    async = config.async;
  }

  FallbackConfig(CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback,
    CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage) {
    this.fallback = fallback;
    this.fallbackStage = fallbackStage;
  }

  /**
   * Returns the fallback function, else {@code null} if a fallback stage function was configured instead.
   *
   * @see Fallback#of(CheckedRunnable)
   * @see Fallback#of(CheckedSupplier)
   * @see Fallback#of(CheckedConsumer)
   * @see Fallback#of(CheckedFunction)
   * @see Fallback#of(Object)
   * @see Fallback#ofException(CheckedFunction)
   * @see Fallback#builder(CheckedRunnable)
   * @see Fallback#builder(CheckedSupplier)
   * @see Fallback#builder(CheckedConsumer)
   * @see Fallback#builder(CheckedFunction)
   * @see Fallback#builder(Object)
   * @see Fallback#builderOfException(CheckedFunction)
   */
  public CheckedFunction<ExecutionAttemptedEvent<R>, R> getFallback() {
    return fallback;
  }

  /**
   * Returns the fallback stage function, else {@code null} if a fallback function was configured instead.
   *
   * @see Fallback#ofStage(CheckedSupplier)
   * @see Fallback#ofStage(CheckedFunction)
   * @see Fallback#builderOfStage(CheckedSupplier)
   * @see Fallback#builderOfStage(CheckedFunction)
   */
  public CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> getFallbackStage() {
    return fallbackStage;
  }

  /**
   * Returns whether the Fallback is configured to handle execution results asynchronously, separate from execution.
   *
   * @see FallbackBuilder#withAsync()
   */
  public boolean isAsync() {
    return async;
  }
}
