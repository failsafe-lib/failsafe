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

import dev.failsafe.DelayablePolicyConfig;
import dev.failsafe.ExecutionContext;
import dev.failsafe.Policy;
import dev.failsafe.internal.util.Durations;

import java.time.Duration;

/**
 * A policy that can be delayed between executions.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface DelayablePolicy<R> extends Policy<R> {
  DelayablePolicyConfig<R> getConfig();

  /**
   * Returns a computed delay for the {@code result} and {@code context} else {@code null} if no delay function is
   * configured or the computed delay is invalid.
   */
  default Duration computeDelay(ExecutionContext<R> context) {
    DelayablePolicyConfig<R> config = getConfig();
    Duration computed = null;
    if (context != null && config.getDelayFn() != null) {
      R result = context.getLastResult();
      Throwable exception = context.getLastException();

      R delayResult = config.getDelayResult();
      Class<? extends Throwable> delayFailure = config.getDelayException();
      boolean delayResultMatched = delayResult == null || delayResult.equals(result);
      boolean delayExceptionMatched =
        delayFailure == null || (exception != null && delayFailure.isAssignableFrom(exception.getClass()));
      if (delayResultMatched && delayExceptionMatched) {
        try {
          computed = Durations.ofSafeNanos(config.getDelayFn().get(context));
        } catch (Throwable e) {
          if (e instanceof RuntimeException)
            throw (RuntimeException) e;
          else
            throw new RuntimeException(e);
        }
      }
    }

    return computed != null && !computed.isNegative() ? computed : null;
  }
}
