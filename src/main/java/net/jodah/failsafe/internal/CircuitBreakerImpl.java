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

import net.jodah.failsafe.*;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.spi.AbstractPolicy;
import net.jodah.failsafe.spi.DelayablePolicy;
import net.jodah.failsafe.spi.FailurePolicy;
import net.jodah.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CircuitBreaker} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreakerBuilder
 * @see CircuitBreakerOpenException
 */
public class CircuitBreakerImpl<R> extends AbstractPolicy<CircuitBreaker<R>, R>
  implements CircuitBreaker<R>, FailurePolicy<R>, DelayablePolicy<R> {

  private final CircuitBreakerConfig<R> config;

  /** Writes guarded by "this" */
  protected final AtomicReference<CircuitState<R>> state = new AtomicReference<>();
  protected final AtomicInteger currentExecutions = new AtomicInteger();

  private volatile CheckedRunnable onOpen;
  private volatile CheckedRunnable onHalfOpen;
  private volatile CheckedRunnable onClose;

  public CircuitBreakerImpl(CircuitBreakerConfig<R> config) {
    this.config = config;
    state.set(new ClosedState<>(this));
  }

  @Override
  public CircuitBreakerConfig<R> getConfig() {
    return config;
  }

  @Override
  public boolean allowsExecution() {
    return state.get().allowsExecution();
  }

  @Override
  public void close() {
    transitionTo(State.CLOSED, onClose, null);
  }

  @Override
  public State getState() {
    return state.get().getState();
  }

  @Override
  public int getExecutionCount() {
    return state.get().getStats().getExecutionCount();
  }

  @Override
  public Duration getRemainingDelay() {
    return state.get().getRemainingDelay();
  }

  @Override
  public long getFailureCount() {
    return state.get().getStats().getFailureCount();
  }

  @Override
  public int getFailureRate() {
    return state.get().getStats().getFailureRate();
  }

  @Override
  public int getSuccessCount() {
    return state.get().getStats().getSuccessCount();
  }

  @Override
  public int getSuccessRate() {
    return state.get().getStats().getSuccessRate();
  }

  @Override
  public void halfOpen() {
    transitionTo(State.HALF_OPEN, onHalfOpen, null);
  }

  @Override
  public boolean isClosed() {
    return State.CLOSED.equals(getState());
  }

  @Override
  public boolean isHalfOpen() {
    return State.HALF_OPEN.equals(getState());
  }

  @Override
  public boolean isOpen() {
    return State.OPEN.equals(getState());
  }

  @Override
  public CircuitBreakerImpl<R> onClose(CheckedRunnable runnable) {
    onClose = Assert.notNull(runnable, "runnable");
    return this;
  }

  @Override
  public CircuitBreakerImpl<R> onHalfOpen(CheckedRunnable runnable) {
    onHalfOpen = Assert.notNull(runnable, "runnable");
    return this;
  }

  @Override
  public CircuitBreakerImpl<R> onOpen(CheckedRunnable runnable) {
    onOpen = Assert.notNull(runnable, "runnable");
    return this;
  }

  @Override
  public void open() {
    transitionTo(State.OPEN, onOpen, null);
  }

  @Override
  public void preExecute() {
    currentExecutions.incrementAndGet();
  }

  @Override
  public void recordFailure() {
    recordExecutionFailure(null);
  }

  @Override
  public void recordFailure(Throwable failure) {
    recordResult(null, failure);
  }

  @Override
  public void recordResult(R result) {
    recordResult(result, null);
  }

  @Override
  public void recordSuccess() {
    try {
      state.get().recordSuccess();
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  @Override
  public String toString() {
    return getState().toString();
  }

  protected void recordResult(R result, Throwable failure) {
    try {
      if (isFailure(result, failure))
        state.get().recordFailure(null);
      else
        state.get().recordSuccess();
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  /**
   * Transitions to the {@code newState} if not already in that state and calls any associated event listener.
   */
  protected void transitionTo(State newState, CheckedRunnable listener, ExecutionContext<R> context) {
    boolean transitioned = false;
    synchronized (this) {
      if (!getState().equals(newState)) {
        switch (newState) {
          case CLOSED:
            state.set(new ClosedState<>(this));
            break;
          case OPEN:
            Duration computedDelay = computeDelay(context);
            state.set(new OpenState<>(this, state.get(), computedDelay != null ? computedDelay : config.getDelay()));
            break;
          case HALF_OPEN:
            state.set(new HalfOpenState<>(this));
            break;
        }
        transitioned = true;
      }
    }

    if (transitioned && listener != null) {
      try {
        listener.run();
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Records an execution failure.
   */
  protected void recordExecutionFailure(ExecutionContext<R> context) {
    try {
      state.get().recordFailure(context);
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  /**
   * Opens the circuit breaker and considers the {@code context} when computing the delay before the circuit breaker
   * will transition to half open.
   */
  protected void open(ExecutionContext<R> context) {
    transitionTo(State.OPEN, onOpen, context);
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new CircuitBreakerExecutor<>(this, policyIndex, successHandler, failureHandler);
  }
}
