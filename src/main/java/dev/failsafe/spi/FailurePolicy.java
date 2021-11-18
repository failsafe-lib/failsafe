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

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A policy that can handle specifically configured failures.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface FailurePolicy<R> extends Policy<R> {
  FailurePolicyConfig<R> getConfig();

  /**
   * Returns whether an execution {@code result} or {@code failure} are considered a failure according to the policy
   * configuration.
   *
   * @see FailurePolicyBuilder#handle(Class...)
   * @see FailurePolicyBuilder#handle(List)
   * @see FailurePolicyBuilder#handleIf(BiPredicate)
   * @see FailurePolicyBuilder#handleIf(Predicate)
   * @see FailurePolicyBuilder#handleResult(Object) 
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
