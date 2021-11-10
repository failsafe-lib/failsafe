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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.RetryPolicyBuilder;
import net.jodah.failsafe.RetryPolicyConfig;
import net.jodah.failsafe.spi.DelayablePolicy;
import net.jodah.failsafe.spi.FailurePolicy;
import net.jodah.failsafe.spi.PolicyExecutor;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A {@link RetryPolicy} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RetryPolicyBuilder
 */
@SuppressWarnings("WeakerAccess")
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
   * @see RetryPolicyBuilder#abortOn(Predicate)
   * @see RetryPolicyBuilder#abortIf(BiPredicate)
   * @see RetryPolicyBuilder#abortIf(Predicate)
   * @see RetryPolicyBuilder#abortWhen(R)
   */
  public boolean isAbortable(R result, Throwable failure) {
    for (BiPredicate<R, Throwable> predicate : config.getAbortConditions()) {
      try {
        if (predicate.test(result, failure))
          return true;
      } catch (Exception ignore) {
      }
    }
    return false;
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new RetryPolicyExecutor<>(this, policyIndex);
  }
}
