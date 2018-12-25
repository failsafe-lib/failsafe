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
package net.jodah.failsafe.internal.util;

import net.jodah.failsafe.FailsafeFuture;

import java.util.concurrent.CompletableFuture;

/**
 * A CompletableFuture that forwards cancellation requests to an associated FailsafeFuture.
 * 
 * @author Jonathan Halterman
 * @param <T>
 */
public class CancellableFuture<T> extends CompletableFuture<T> {
  private final FailsafeFuture<T> future;

  private CancellableFuture(FailsafeFuture<T> future) {
    this.future = future;
  }

  /**
   * We use a static factory method instead of the constructor to avoid the class from being accidentally loaded on
   * pre-Java 8 VMs when it's not available.
   */
  public static <T> CompletableFuture<T> of(FailsafeFuture<T> future) {
    return new CancellableFuture<>(future);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    future.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }
}
