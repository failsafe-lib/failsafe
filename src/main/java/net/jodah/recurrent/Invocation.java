package net.jodah.recurrent;

import net.jodah.recurrent.internal.util.Assert;

/**
 * An invocation that accepts retry and completion requests.
 * 
 * @author Jonathan Halterman
 */
public class Invocation extends RetryStats {
  volatile boolean retryRequested;
  volatile boolean completionRequested;
  volatile Object result;
  volatile Throwable failure;

  public Invocation(RetryPolicy retryPolicy) {
    super(retryPolicy);
  }

  /**
   * Completes the invocation, allowing any futures waiting on the invocation to complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void complete() {
    this.complete(null);
  }

  /**
   * Completes the invocation with the {@code result}, allowing any futures waiting on the invocation to complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void complete(Object result) {
    Assert.state(!completionRequested, "Complete has already been called");
    Assert.state(!retryRequested, "Retry has already been called");
    completionRequested = true;
    this.result = result;
  }

  /**
   * Completes the invocation with the given {@code failure}, allowing any waiting futures to complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void completeExceptionally(Throwable failure) {
    Assert.state(!completionRequested, "Complete has already been called");
    Assert.state(!retryRequested, "Retry has already been called");
    completionRequested = true;
    this.failure = failure;
  }

  /**
   * Retries a failed invocation, returning true if the retry can be attempted else false if the retry policy has been
   * exceeded.
   * 
   * @throws IllegalStateException if retry or complete has already been called
   */
  public boolean retry() {
    return retryInternal(null);
  }

  /**
   * Retries a failed invocation, returning true if the retry can be attempted for the {@code failure} else false if the
   * retry policy has been exceeded.
   * 
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if retry or complete has already been called
   */
  public boolean retry(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryInternal(failure);
  }

  /**
   * Resets user requested retry and completion state.
   */
  void reset() {
    retryRequested = false;
    completionRequested = false;
    result = null;
    failure = null;
  }

  private boolean retryInternal(Throwable failure) {
    Assert.state(!retryRequested, "Retry has already been called");
    Assert.state(!completionRequested, "Complete has already been called");

    if (canRetryOn(failure)) {
      retryRequested = true;
      return true;
    }

    if (failure == null)
      failure = new RuntimeException("Retry invocations exceeded");
    completeExceptionally(failure);
    return false;
  }
}