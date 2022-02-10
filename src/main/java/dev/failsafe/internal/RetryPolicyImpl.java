/*
 * Copyright 2016 the original author or authors.
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
package dev.failsafe.internal;

import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyBuilder;
import dev.failsafe.RetryPolicyConfig;
import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;
import dev.failsafe.spi.DelayablePolicy;
import dev.failsafe.spi.FailurePolicy;
import dev.failsafe.spi.PolicyExecutor;

import java.util.List;

/**
 * A {@link RetryPolicy} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RetryPolicyBuilder
 */
public class RetryPolicyImpl<R> implements RetryPolicy<R>, FailurePolicy<R>, DelayablePolicy<R> {
  private final RetryPolicyConfig<R> config;

  public RetryPolicyImpl(RetryPolicyConfig<R> config) {
    this.config = config;
  }

  @Override
  public RetryPolicyConfig<R> getConfig() {
    return config;
  }

  /**
   * Returns whether an execution result can be aborted given the configured abort conditions.
   *
   * @see RetryPolicyBuilder#abortOn(Class...)
   * @see RetryPolicyBuilder#abortOn(List)
   * @see RetryPolicyBuilder#abortOn(CheckedPredicate)
   * @see RetryPolicyBuilder#abortIf(CheckedBiPredicate)
   * @see RetryPolicyBuilder#abortIf(CheckedPredicate)
   * @see RetryPolicyBuilder#abortWhen(R)
   */
  public boolean isAbortable(R result, Throwable failure) {
    for (CheckedBiPredicate<R, Throwable> predicate : config.getAbortConditions()) {
      try {
        if (predicate.test(result, failure))
          return true;
      } catch (Throwable ignore) {
      }
    }
    return false;
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new RetryPolicyExecutor<>(this, policyIndex);
  }
}
