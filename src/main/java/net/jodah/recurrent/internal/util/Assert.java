package net.jodah.recurrent.internal.util;

/**
 * @author Jonathan Halterman
 */
public final class Assert {
  private Assert() {
  }

  public static void isTrue(boolean expression) {
    if (!expression)
      throw new IllegalArgumentException();
  }

  public static void isTrue(boolean expression, String errorMessageFormat, Object... args) {
    if (!expression)
      throw new IllegalArgumentException(String.format(errorMessageFormat, args));
  }

  public static <T> T notNull(T reference) {
    if (reference == null)
      throw new NullPointerException();
    return reference;
  }

  public static <T> T notNull(T reference, String parameterName) {
    if (reference == null)
      throw new NullPointerException(parameterName + " cannot be null");
    return reference;
  }

  public static void state(boolean expression) {
    if (!expression)
      throw new IllegalStateException();
  }

  public static void state(boolean expression, String errorMessageFormat, Object... args) {
    if (!expression)
      throw new IllegalStateException(String.format(errorMessageFormat, args));
  }
}