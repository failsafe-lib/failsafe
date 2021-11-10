package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionCompletedEvent;

/**
 * Configuration for a {@link Policy}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class PolicyConfig<R> {
  volatile EventListener<ExecutionCompletedEvent<R>> successListener;
  volatile EventListener<ExecutionCompletedEvent<R>> failureListener;

  protected PolicyConfig() {
  }

  protected PolicyConfig(PolicyConfig<R> config) {
    successListener = config.successListener;
    failureListener = config.failureListener;
  }

  /**
   * Returns the success listener.
   *
   * @see PolicyListeners#onSuccess(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getSuccessListener() {
    return successListener;
  }

  /**
   * Returns the failure listener.
   *
   * @see PolicyListeners#onFailure(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getFailureListener() {
    return failureListener;
  }
}
