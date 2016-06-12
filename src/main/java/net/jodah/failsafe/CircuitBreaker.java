package net.jodah.failsafe;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.jodah.failsafe.function.BiPredicate;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.Predicate;
import net.jodah.failsafe.internal.CircuitBreakerStats;
import net.jodah.failsafe.internal.CircuitState;
import net.jodah.failsafe.internal.ClosedState;
import net.jodah.failsafe.internal.HalfOpenState;
import net.jodah.failsafe.internal.OpenState;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.Duration;
import net.jodah.failsafe.util.Ratio;

/**
 * A circuit breaker that temporarily halts execution when configurable thresholds are exceeded.
 * 
 * @author Jonathan Halterman
 */
public class CircuitBreaker {
  private static final Object DEFAULT_VALUE = new Object();

  /** Writes guarded by "this" */
  private final AtomicReference<CircuitState> state = new AtomicReference<CircuitState>();
  private final AtomicInteger currentExecutions = new AtomicInteger();
  private final CircuitBreakerStats stats = new CircuitBreakerStats() {
    @Override
    public int getCurrentExecutions() {
      return currentExecutions.get();
    }
  };
  private Duration delay = Duration.NONE;
  private Duration timeout;
  private Integer failureThreshold;
  private Ratio failureThresholdRatio;
  private Integer successThreshold;
  private Ratio successThresholdRatio;
  private List<Class<? extends Throwable>> failures;
  private Predicate<Throwable> failurePredicate;
  private Object failureValue = DEFAULT_VALUE;
  private Predicate<Object> resultPredicate;
  private BiPredicate<Object, Throwable> completionPredicate;
  CheckedRunnable onOpen;
  CheckedRunnable onHalfOpen;
  CheckedRunnable onClose;

  /**
   * Creates a Circuit that opens after a single failure, closes after a single success, and has no delay.
   */
  public CircuitBreaker() {
  }

  /**
   * The state of the circuit.
   */
  public enum State {
    /* The circuit is closed and fully functional, allowing executions to occur. */
    CLOSED,
    /* The circuit is opened and not allowing executions to occur. */
    OPEN,
    /* The circuit is temporarily allowing executions to occur. */
    HALF_OPEN;
  }

  /**
   * Returns whether the circuit allows execution, possibly triggering a state transition.
   */
  public boolean allowsExecution() {
    return state.get().allowsExecution(stats);
  }

  /**
   * Closes the circuit.
   */
  public void close() {
    transitionTo(State.CLOSED, onClose);
  }

  /**
   * Specifies that a failure should be recorded if the {@code completionPredicate} matches the completion result.
   * 
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> CircuitBreaker failIf(BiPredicate<T, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    this.completionPredicate = (BiPredicate<Object, Throwable>) completionPredicate;
    return this;
  }

  /**
   * Specifies that a failure should be recorded if the {@code resultPredicate} matches the result.
   * 
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> CircuitBreaker failIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    this.resultPredicate = (Predicate<Object>) resultPredicate;
    return this;
  }

  /**
   * Specifies the types to fail on. Applies to any type that is assignable from the {@code failures}.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SuppressWarnings("unchecked")
  public CircuitBreaker failOn(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "failures cannot be empty");
    this.failures = Arrays.asList(failures);
    return this;
  }

  /**
   * Specifies the types to fail on. Applies to any type that is assignable from the {@code failures}.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  public CircuitBreaker failOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    this.failures = failures;
    return this;
  }

  /**
   * Specifies that a failure should be recorded if the {@code failurePredicate} matches the failure.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  @SuppressWarnings("unchecked")
  public CircuitBreaker failOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    this.failurePredicate = (Predicate<Throwable>) failurePredicate;
    return this;
  }

  /**
   * Specifies that a failure should be recorded if the execution result matches the {@code result}.
   */
  public CircuitBreaker failWhen(Object result) {
    this.failureValue = result;
    return this;
  }

  /**
   * Returns the delay before allowing another execution on the circuit. Defaults to {@link Duration#NONE}.
   * 
   * @see #withDelay(long, TimeUnit)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the number of successive failures that must occur when in a closed state in order to open the circuit else
   * null if none has been configured.
   * 
   * @see #withFailureThreshold(int)
   */
  public Integer getFailureThreshold() {
    return failureThreshold;
  }

  /**
   * Gets the ratio of successive failures that must occur when in a closed state in order to open the circuit.
   * 
   * @see #withFailureThreshold(int, int)
   */
  public Ratio getFailureThresholdRatio() {
    return failureThresholdRatio;
  }

  /**
   * Gets the state of the circuit.
   */
  public State getState() {
    CircuitState circuitState = state.get();
    return circuitState == null ? State.CLOSED : circuitState.getState();
  }

  /**
   * Returns the number of successive successful executions that must occur when in a half-open state in order to close
   * the circuit else null if none has been configured.
   * 
   * @see #withSuccessThreshold(int)
   */
  public Integer getSuccessThreshold() {
    return successThreshold;
  }

  /**
   * Gets the ratio of successive successful executions that must occur when in a half-open state in order to close the
   * circuit.
   * 
   * @see #withSuccessThreshold(int, int)
   */
  public Ratio getSuccessThresholdRatio() {
    return successThresholdRatio;
  }

  /**
   * Returns timeout for executions else {@code null} if none has been configured.
   * 
   * @see #withTimeout(long, TimeUnit)
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Half-opens the circuit.
   */
  public void halfOpen() {
    transitionTo(State.HALF_OPEN, onHalfOpen);
  }

  /**
   * Returns whether the circuit is closed.
   */
  public boolean isClosed() {
    return State.CLOSED.equals(getState());
  }

  /**
   * Returns whether the circuit breaker considers the {@code result} or {@code throwable} a failure based on its
   * failure configuration.
   * 
   * @see #failIf(BiPredicate)
   * @see #failIf(Predicate)
   * @see #failOn(Class...)
   * @see #failOn(List)
   * @see #failOn(Predicate)
   * @see #failWhen(Object)
   */
  public boolean isFailure(Object result, Throwable throwable) {
    // Check completion conditions
    if (completionPredicate != null && completionPredicate.test(result, throwable))
      return true;

    // Check failure condition(s)
    if (throwable != null) {
      if (failurePredicate != null && failurePredicate.test(throwable))
        return true;
      if (failures != null)
        for (Class<? extends Throwable> failureType : failures)
          if (failureType.isAssignableFrom(throwable.getClass()))
            return true;

      // Retry if the failure was not examined
      return completionPredicate == null && failurePredicate == null && failures == null;
    }

    // Check result condition(s)
    if (resultPredicate != null && resultPredicate.test(result))
      return true;
    if (!DEFAULT_VALUE.equals(failureValue))
      return failureValue == null ? result == null : failureValue.equals(result);

    return false;
  }

  /**
   * Returns whether the circuit is open.
   */
  public boolean isOpen() {
    return State.OPEN.equals(getState());
  }

  /**
   * Calls the {@code runnable} when the circuit is closed.
   */
  public void onClose(CheckedRunnable runnable) {
    onClose = runnable;
  }

  /**
   * Calls the {@code runnable} when the circuit is half-opened.
   */
  public void onHalfOpen(CheckedRunnable runnable) {
    onHalfOpen = runnable;
  }

  /**
   * Calls the {@code runnable} when the circuit is opened.
   */
  public void onOpen(CheckedRunnable runnable) {
    onOpen = runnable;
  }

  /**
   * Opens the circuit.
   */
  public void open() {
    transitionTo(State.OPEN, onOpen);
  }

  /**
   * Records an execution {@code failure} as a success or failure based on the failure configuration as determined by
   * {@link #isFailure(Object, Throwable)}.
   * 
   * @see #isFailure(Object, Throwable)
   */
  public void recordFailure(Throwable failure) {
    recordResult(null, failure);
  }

  /**
   * Records an execution {@code result} as a success or failure based on the failure configuration as determined by
   * {@link #isFailure(Object, Throwable)}.
   * 
   * @see #isFailure(Object, Throwable)
   */
  public void recordResult(Object result) {
    recordResult(result, null);
  }

  /**
   * Records an execution success.
   */
  public void recordSuccess() {
    CircuitState circuitState = state.get();
    if (state != null) {
      circuitState.recordSuccess();
      currentExecutions.decrementAndGet();
    }
  }

  @Override
  public String toString() {
    return getState().toString();
  }

  /**
   * Sets the {@code delay} to wait in open state before transitioning to half-open.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   */
  public CircuitBreaker withDelay(long delay, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.isTrue(delay > 0, "delay must be greater than 0");
    this.delay = new Duration(delay, timeUnit);
    return this;
  }

  /**
   * Sets the number of successive failures that must occur when in a closed state in order to open the circuit. 0
   * represents no threshold.
   * 
   * @throws IllegalArgumentException if {@code failureThresh} < 1
   * @throws IllegalStateException if a failure ratio has already been configured via
   *           {@link #withFailureThreshold(int, int)}
   */
  public CircuitBreaker withFailureThreshold(int failureThreshold) {
    Assert.isTrue(failureThreshold >= 1, "failureThreshold must be greater than or equal to 1");
    Assert.state(failureThresholdRatio == null,
        "failure threshold and failure threshold ratio cannot both be configured");
    this.failureThreshold = failureThreshold;
    return this;
  }

  /**
   * Sets the ratio of successive failures that must occur when in a closed state in order to open the circuit. For
   * example: 5, 10 would open the circuit if 5 out of the last 10 executions result in a failure. The circuit will not
   * be opened until at least {@code executions} executions have taken place.
   * 
   * @param failures The number of failures that must occur in order to open the circuit
   * @param executions The number of executions to measure the {@code failures} against
   * @throws IllegalArgumentException if {@code failures} < 1, {@code executions} < 1, or {@code failures} is <
   *           {@code executions}
   * @throws IllegalStateException if a failure ratio has already been configured via {@link #withFailureThreshold(int)}
   */
  public CircuitBreaker withFailureThreshold(int failures, int executions) {
    Assert.isTrue(failures >= 1, "failures must be greater than or equal to 1");
    Assert.isTrue(executions >= 1, "executions must be greater than or equal to 1");
    Assert.isTrue(executions >= failures, "executions must be greater than or equal to failures");
    Assert.state(failureThreshold == null, "failure threshold and failure threshold ratio cannot both be configured");
    this.failureThresholdRatio = new Ratio(failures, executions);
    return this;
  }

  /**
   * Sets the number of successive successful executions that must occur when in a half-open state in order to close the
   * circuit. 0 represents no threshold.
   * 
   * @throws IllegalArgumentException if {@code successThreshold} < 1
   * @throws IllegalStateException if a success ratio has already been configured via
   *           {@link #withSuccessThreshold(int, int)}
   */
  public CircuitBreaker withSuccessThreshold(int successThreshold) {
    Assert.isTrue(successThreshold >= 1, "successThreshold must be greater than or equal to 1");
    Assert.state(successThresholdRatio == null,
        "success threshold and success threshold ratio cannot both be configured");
    this.successThreshold = successThreshold;
    return this;
  }

  /**
   * Sets the ratio of successive successful executions that must occur when in a half-open state in order to close the
   * circuit. For example: 5, 10 would close the circuit if 5 out of the last 10 executions were successful. The circuit
   * will not be closed until at least {@code executions} executions have taken place.
   * 
   * @param successes The number of successful executions that must occur in order to open the circuit
   * @param executions The number of executions to measure the {@code successes} against
   * @throws IllegalArgumentException if {@code successes} < 1, {@code executions} < 1, or {@code successes} is <
   *           {@code executions}
   * @throws IllegalStateException if a success threshold has already been configured via
   *           {@link #withSuccessThreshold(int)}
   */
  public CircuitBreaker withSuccessThreshold(int successes, int executions) {
    Assert.isTrue(successes >= 1, "successes must be greater than or equal to 1");
    Assert.isTrue(executions >= 1, "executions must be greater than or equal to 1");
    Assert.isTrue(executions >= successes, "executions must be greater than or equal to successes");
    Assert.state(successThreshold == null, "success threshold and success threshold ratio cannot both be configured");
    this.successThresholdRatio = new Ratio(successes, executions);
    return this;
  }

  /**
   * Sets the {@code timeout} for executions. Executions that exceed this timeout are recorded as failures.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code timeout} <= 0
   */
  public CircuitBreaker withTimeout(long timeout, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.isTrue(timeout > 0, "timeout must be greater than 0");
    this.timeout = new Duration(timeout, timeUnit);
    return this;
  }

  /**
   * Initializes the circuit prior to use.
   */
  synchronized void initialize() {
    if (state.get() == null)
      state.set(new ClosedState(this));
  }

  /**
   * Records an execution failure.
   */
  void recordFailure() {
    CircuitState circuitState = state.get();
    if (state != null) {
      circuitState.recordFailure();
      currentExecutions.decrementAndGet();
    }
  }

  void recordResult(Object result, Throwable failure) {
    CircuitState circuitState = state.get();
    if (state != null) {
      if (isFailure(result, failure))
        circuitState.recordFailure();
      else
        circuitState.recordSuccess();
      currentExecutions.decrementAndGet();
    }
  }

  void before() {
    currentExecutions.incrementAndGet();
  }

  /**
   * Transitions to the {@code newState} if not already in that state and calls any associated event listener.
   */
  private void transitionTo(State newState, CheckedRunnable listener) {
    boolean transitioned = false;
    synchronized (this) {
      if (!getState().equals(newState)) {
        switch (newState) {
          case CLOSED:
            state.set(new ClosedState(this));
            break;
          case OPEN:
            state.set(new OpenState(this));
            break;
          case HALF_OPEN:
            state.set(new HalfOpenState(this));
            break;
        }
        transitioned = true;
      }
    }

    if (transitioned && listener != null) {
      try {
        listener.run();
      } catch (Exception ignore) {
      }
    }
  }
}
