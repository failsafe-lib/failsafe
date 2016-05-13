package net.jodah.failsafe;

/**
 * Thrown when a synchronous Failsafe run() call fails with an exception. Use {@link Throwable#getCause()} to learn the
 * cause of the failure.
 * 
 * @author Jonathan Halterman
 */
public class FailsafeException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  FailsafeException(Throwable t) {
    super(t);
  }
}
