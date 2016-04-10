package net.jodah.recurrent;

/**
 * Thrown when a synchronous Recurrent run() call fails with an exception. Use {@link Throwable#getCause()} to learn the
 * cause of the failure.
 * 
 * @author Jonathan Halterman
 */
public class RecurrentException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  RecurrentException(Throwable t) {
    super(t);
  }
}
