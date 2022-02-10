/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe.spi;

import dev.failsafe.FailurePolicyBuilder;
import dev.failsafe.FailurePolicyConfig;
import dev.failsafe.Policy;
import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;

import java.util.List;

/**
 * A policy that can handle specifically configured failures.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface FailurePolicy<R> extends Policy<R> {
  FailurePolicyConfig<R> getConfig();

  /**
   * Returns whether an execution {@code result} or {@code exception} are considered a failure according to the policy
   * configuration.
   *
   * @see FailurePolicyBuilder#handle(Class...)
   * @see FailurePolicyBuilder#handle(List)
   * @see FailurePolicyBuilder#handleIf(CheckedBiPredicate)
   * @see FailurePolicyBuilder#handleIf(CheckedPredicate)
   * @see FailurePolicyBuilder#handleResult(Object)
   * @see FailurePolicyBuilder#handleResultIf(CheckedPredicate)
   */
  default boolean isFailure(R result, Throwable exception) {
    FailurePolicyConfig<R> config = getConfig();
    if (config.getFailureConditions().isEmpty())
      return exception != null;

    for (CheckedBiPredicate<R, Throwable> predicate : config.getFailureConditions()) {
      try {
        if (predicate.test(result, exception))
          return true;
      } catch (Throwable ignore) {
      }
    }

    // Fail by default if an exception is not checked by a condition
    return exception != null && !config.isExceptionsChecked();
  }
}
