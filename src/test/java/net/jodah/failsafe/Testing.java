package net.jodah.failsafe;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;

import net.jodah.failsafe.function.CheckedRunnable;

public class Testing {
  public static Throwable getThrowable(CheckedRunnable runnable) {
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

  public static void ignoreExceptions(CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
    }
  }

  public static Exception[] failures(int numFailures, Exception failure) {
    Exception[] failures = new Exception[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failure;
    return failures;
  }

  public static void shouldFail(Runnable runnable, Class<? extends Exception> expected) {
    try {
      runnable.run();
      fail("A failure was expected");
    } catch (Exception e) {
      assertTrue(e.getClass().isAssignableFrom(expected), "The expected exception was not of the expected type " + e);
    }
  }

  public static void runInThread(CheckedRunnable runnable) {
    new Thread(() -> ignoreExceptions(runnable)).start();
  }

  public static void noop() {
  }
}
