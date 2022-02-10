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
package dev.failsafe;

import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for policies that handle specific failures and conditions.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class FailurePolicyConfig<R> extends PolicyConfig<R> {
  /** Indicates whether exceptions are checked by a configured failure condition */
  boolean exceptionsChecked;
  /** Conditions that determine whether an execution is a failure */
  List<CheckedBiPredicate<R, Throwable>> failureConditions;

  protected FailurePolicyConfig() {
    failureConditions = new ArrayList<>();
  }

  protected FailurePolicyConfig(FailurePolicyConfig<R> config) {
    super(config);
    exceptionsChecked = config.exceptionsChecked;
    failureConditions = new ArrayList<>(config.failureConditions);
  }

  /**
   * Returns whether exceptions are checked by a configured failure condition.
   */
  public boolean isExceptionsChecked() {
    return exceptionsChecked;
  }

  /**
   * Returns the conditions under which a result or Throwable should be treated as a failure and handled.
   *
   * @see FailurePolicyBuilder#handle(Class...)
   * @see FailurePolicyBuilder#handle(List)
   * @see FailurePolicyBuilder#handleIf(CheckedBiPredicate)
   * @see FailurePolicyBuilder#handleIf(CheckedPredicate)
   * @see FailurePolicyBuilder#handleResult(Object)
   * @see FailurePolicyBuilder#handleResultIf(CheckedPredicate)
   */
  public List<CheckedBiPredicate<R, Throwable>> getFailureConditions() {
    return failureConditions;
  }
}
