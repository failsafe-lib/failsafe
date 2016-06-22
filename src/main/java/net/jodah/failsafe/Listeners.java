package net.jodah.failsafe;

/**
 * Failsafe execution event listeners.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class Listeners<T> {
  /**
   * Called when an execution is aborted.
   */
  public void onAbort(T result, Throwable failure) {
  }

  /**
   * Called when an execution is aborted.
   */
  public void onAbort(T result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called when an execution is completed.
   */
  public void onComplete(T result, Throwable failure) {
  }

  /**
   * Called when an execution is completed.
   */
  public void onComplete(T result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(T result, Throwable failure) {
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(T result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after the retry policy is exceeded and the result is a failure.
   */
  public void onFailure(T result, Throwable failure) {
  }

  /**
   * Called after the retry policy is exceeded and the result is a failure.
   */
  public void onFailure(T result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(T result, Throwable failure) {
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(T result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(T result) {
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(T result, ExecutionContext context) {
  }
}
