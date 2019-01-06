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
package net.jodah.failsafe.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Tools for adapting checked exceptions to unchecked.
 *
 * @author Jonathan Halterman
 */
public final class Unchecked {
  private Unchecked() {
  }

  /**
   * Returns a Supplier for the {@code callable} that wraps and re-throws any checked Exception in a RuntimeException.
   */
  public static <T> Supplier<T> supplier(Callable<T> callable) {
    return () -> {
      try {
        return callable.call();
      } catch (Exception e) {
        throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
      }
    };
  }

  /**
   * Returns a Runnable for the {@code callable} that wraps and re-throws any checked Exception in a RuntimeException.
   */
  public static <T> Runnable runnable(Callable<T> callable) {
    return () -> {
      try {
        callable.call();
      } catch (Exception e) {
        throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
      }
    };
  }
}
