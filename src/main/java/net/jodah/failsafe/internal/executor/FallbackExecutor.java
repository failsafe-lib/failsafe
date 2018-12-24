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
package net.jodah.failsafe.internal.executor;

import net.jodah.failsafe.PolicyExecutor;
import net.jodah.failsafe.function.CheckedBiFunction;

/**
 * A PolicyExecutor that handles failures according to a fallback.
 */
public class FallbackExecutor extends PolicyExecutor {
  private final CheckedBiFunction<Object, Throwable, Object> fallback;

  public FallbackExecutor(CheckedBiFunction<Object, Throwable, Object> fallback) {
    this.fallback = fallback;
  }

  @Override
  public PolicyResult postExecute(PolicyResult pr) {
    if (pr.success)
      return pr;

    try {
      return new PolicyResult(fallback.apply(pr.result, pr.failure), null, true, true);
    } catch (Exception e) {
      return new PolicyResult(null, e, true, false);
    }
  }
}
