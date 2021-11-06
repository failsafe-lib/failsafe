package net.jodah.failsafe.spi;

import net.jodah.failsafe.FailurePolicyBuilder;
import net.jodah.failsafe.FailurePolicyConfig;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A policy that can handle specifically configured failures.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface FailurePolicy<R> {
  FailurePolicyConfig<R> getConfig();

  /**
   * Returns whether an execution {@code result} or {@code failure} are considered a failure according to the policy
   * configuration.
   *
   * @see FailurePolicyBuilder#handle(Class...)
   * @see FailurePolicyBuilder#handle(List)
   * @see FailurePolicyBuilder#handleIf(BiPredicate)
   * @see FailurePolicyBuilder#handleIf(Predicate)
   * @see FailurePolicyBuilder#handleResult(R)
   * @see FailurePolicyBuilder#handleResultIf(Predicate)
   */
  default boolean isFailure(R result, Throwable failure) {
    FailurePolicyConfig<R> config = getConfig();
    if (config.getFailureConditions().isEmpty())
      return failure != null;

    for (BiPredicate<R, Throwable> predicate : config.getFailureConditions()) {
      try {
        if (predicate.test(result, failure))
          return true;
      } catch (Exception ignore) {
      }
    }

    // Fail by default if a failure is not checked by a condition
    return failure != null && !config.isFailuresChecked();
  }
}
