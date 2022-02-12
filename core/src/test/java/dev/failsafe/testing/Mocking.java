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
package dev.failsafe.testing;

import dev.failsafe.DelayablePolicyBuilder;
import dev.failsafe.DelayablePolicyConfig;
import dev.failsafe.spi.DelayablePolicy;
import dev.failsafe.spi.FailurePolicy;
import dev.failsafe.Policy;
import dev.failsafe.spi.PolicyExecutor;

/**
 * Utilities to assist with creating mocks.
 */
public class Mocking extends Asserts {
  public static class FooConfig<R> extends DelayablePolicyConfig<R> {
  }

  public static class FooPolicyBuilder<R> extends DelayablePolicyBuilder<FooPolicyBuilder<R>, FooConfig<R>, R> {
    FooPolicyBuilder() {
      super(new FooConfig<>());
    }

    public FooPolicy<R> build() {
      return new FooPolicy<>(config);
    }
  }

  public static class FooPolicy<R> implements Policy<R>, FailurePolicy<R>, DelayablePolicy<R> {
    FooConfig<R> config;

    FooPolicy(FooConfig<R> config) {
      this.config = config;
    }

    public static <R> FooPolicyBuilder<R> builder() {
      return new FooPolicyBuilder<>();
    }

    @Override
    public FooConfig<R> getConfig() {
      return config;
    }

    @Override
    public PolicyExecutor<R> toExecutor(int policyIndex) {
      return null;
    }
  }

  public static class ConnectException extends RuntimeException {
  }

  /**
   * A mock Service implementation that throws a specified number of failures then returns a result.
   */
  public static class Service {
    int numFailuresExpected;
    boolean resultExpected;
    int numFailuresSoFar;

    public Service() {
      numFailuresExpected = -1;
    }

    public Service(boolean result) {
      resultExpected = result;
    }

    public Service(int numFailures, boolean result) {
      numFailuresExpected = numFailures;
      resultExpected = result;
    }

    public boolean connect() {
      if (numFailuresExpected > -1 && numFailuresExpected == numFailuresSoFar)
        return resultExpected;
      else {
        numFailuresSoFar++;
        throw new ConnectException();
      }
    }

    public void reset() {
      numFailuresSoFar = 0;
    }
  }

  public static Service mockFailingService() {
    return new Service();
  }

  public static Service mockService(boolean result) {
    return new Service(result);
  }

  public static Service mockService(int numFailures, boolean result) {
    return new Service(numFailures, result);
  }

  public static Exception[] failures(int numFailures, Exception failure) {
    Exception[] failures = new Exception[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failure;
    return failures;
  }
}
