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

import dev.failsafe.*;
import dev.failsafe.event.CircuitBreakerStateChangedEvent;
import dev.failsafe.event.EventListener;
import dev.failsafe.spi.DelayablePolicy;
import dev.failsafe.spi.FailurePolicy;
import dev.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CircuitBreaker} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreakerBuilder
 * @see CircuitBreakerOpenException
 */
public class CircuitBreakerImpl<R> implements CircuitBreaker<R>, FailurePolicy<R>, DelayablePolicy<R> {
  private final CircuitBreakerConfig<R> config;

  /** Writes guarded by "this" */
  protected final AtomicReference<CircuitState<R>> state = new AtomicReference<>();

  public CircuitBreakerImpl(CircuitBreakerConfig<R> config) {
    this.config = config;
    state.set(new ClosedState<>(this));
  }

  @Override
  public CircuitBreakerConfig<R> getConfig() {
    return config;
  }

  @Override
  public boolean tryAcquirePermit() {
    return state.get().tryAcquirePermit();
  }

  @Override
  public void acquirePermit() {
    state.get().acquirePermit();
  }

  @Override
  public void close() {
    transitionTo(State.CLOSED, config.getCloseListener(), null);
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
    transitionTo(State.HALF_OPEN, config.getHalfOpenListener(), null);
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
  public void open() {
    transitionTo(State.OPEN, config.getOpenListener(), null);
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
    state.get().recordSuccess();
  }

  @Override
  public String toString() {
    return getState().toString();
  }

  protected void recordResult(R result, Throwable failure) {
    if (isFailure(result, failure))
      state.get().recordFailure(null);
    else
      state.get().recordSuccess();
  }

  /**
   * Transitions to the {@code newState} if not already in that state and calls any associated event listener.
   */
  protected void transitionTo(State newState, EventListener<CircuitBreakerStateChangedEvent> listener,
    ExecutionContext<R> context) {
    boolean transitioned = false;
    State currentState;

    synchronized (this) {
      currentState = getState();
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
        listener.accept(new CircuitBreakerStateChangedEvent(currentState));
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Records an execution failure.
   */
  protected void recordExecutionFailure(ExecutionContext<R> context) {
    state.get().recordFailure(context);
  }

  /**
   * Opens the circuit breaker and considers the {@code context} when computing the delay before the circuit breaker
   * will transition to half open.
   */
  protected void open(ExecutionContext<R> context) {
    transitionTo(State.OPEN, config.getOpenListener(), context);
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new CircuitBreakerExecutor<>(this, policyIndex);
  }
}
