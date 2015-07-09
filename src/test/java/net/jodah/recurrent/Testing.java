package net.jodah.recurrent;

import java.util.concurrent.Callable;

public class Testing {
  @FunctionalInterface
  public interface ThrowableRunnable {
    void run() throws Throwable;
  }

  public static Throwable getThrowable(ThrowableRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      return t;
    }

    return null;
  }

  public static <T> T ignoreExceptions(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      return null;
    }
  }
  

  public static void ignoreExceptions(ThrowableRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
    }
  }
  
  @SuppressWarnings("unchecked")
  public static <T> Class<? extends Exception>[] failures(int numFailures, Class<? extends Exception> failureType) {
    Class<? extends Exception>[] failures = new Class[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failureType;
    return failures;
  }
}
