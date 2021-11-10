package net.jodah.failsafe.event;

/**
 * Listens for events.
 *
 * @param <E> event type
 */
@FunctionalInterface
public interface EventListener<E> {
  void accept(E event) throws Throwable;

  /**
   * Accepts an {@code event} and ignores any exceptions that result.
   */
  default void acceptUnchecked(E event) {
    try {
      accept(event);
    } catch (Throwable ignore) {
    }
  }
}
