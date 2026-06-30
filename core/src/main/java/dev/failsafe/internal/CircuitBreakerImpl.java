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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link CircuitBreaker} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreakerBuilder
 * @see CircuitBreakerOpenException
 */
public class CircuitBreakerImpl<R> implements CircuitBreaker<R>, FailurePolicy<R>, DelayablePolicy<R> {
  private static final Logger LOGGER = Logger.getLogger(CircuitBreakerImpl.class.getName());

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
  public void recordException(Throwable exception) {
    recordResult(null, exception);
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

  protected void recordResult(R result, Throwable exception) {
    if (isFailure(result, exception))
      state.get().recordFailure(null);
    else
      state.get().recordSuccess();
  }

  /**
   * Transitions to the {@code newState} if not already in that state and calls any associated event listener.
   */
  protected void transitionTo(State newState, EventListener<CircuitBreakerStateChangedEvent> listener, /*extracted method for solo responsibility-MITI*/
    ExecutionContext<R> context) {
    boolean transitioned = false;
    State currentState;

    synchronized (this) {
      currentState = getState();
      if (!currentState.equals(newState)) {
        state.set(createState(newState, context));
        transitioned = true;
      }
    }

    if (transitioned && listener != null) {
      try {
        listener.accept(new CircuitBreakerStateChangedEvent(currentState));
      } catch (Throwable t) {
        // PMD: EmptyCatchBlock — previously swallowed silently. A misbehaving state-change
        // listener should not be invisible; logging preserves "don't propagate to the caller"
        // semantics while keeping the failure observable for diagnosis.
        LOGGER.log(Level.WARNING, "CircuitBreaker state-change listener threw an exception", t);
      }
    }
  }

  /**
   * Factory method that builds the {@link CircuitState} implementation corresponding to {@code newState}.
   * <p>
   * Extracting this from {@link #transitionTo} resolves the prior non-exhaustive {@code switch} (PMD:
   * NonExhaustiveSwitch) by making {@link State} coverage explicit and enforced at compile time, and gives
   * state-construction a single, testable seam independent of transition/locking concerns.
   *
   * @throws IllegalArgumentException if {@code newState} is not a recognized transition target
   */
  protected CircuitState<R> createState(State newState, ExecutionContext<R> context) {
    switch (newState) {
      case CLOSED:
        return new ClosedState<>(this);
      case OPEN:
        Duration computedDelay = computeDelay(context);
        return new OpenState<>(this, state.get(), computedDelay != null ? computedDelay : config.getDelay());
      case HALF_OPEN:
        return new HalfOpenState<>(this);
      default:
        throw new IllegalArgumentException("Unexpected circuit breaker state: " + newState);
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