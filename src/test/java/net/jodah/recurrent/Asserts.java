package net.jodah.recurrent;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.testng.Assert;

public class Asserts {
  @FunctionalInterface
  public interface ThrowableRunnable {
    void run() throws Throwable;
  }

  /**
   * Asserts thrown exceptions, including checked exceptions, via a lambda.
   * <p>
   * Sweet idea courtesy of Lukas Eder: http://blog.jooq.org/2014/05/23/java-8-friday-better-exceptions/
   */
  public static void assertThrows(Throwable throwable, ThrowableRunnable runnable) {
    boolean fail = false;
    try {
      runnable.run();
    } catch (Throwable t) {
      assertEquals(throwable, t, "The expected exception was not thrown");
    }

    if (fail)
      Assert.fail("No exception was thrown");
  }

  /**
   * Asserts thrown exceptions, including checked exceptions, via a lambda.
   * <p>
   * Sweet idea courtesy of Lukas Eder: http://blog.jooq.org/2014/05/23/java-8-friday-better-exceptions/
   */
  public static void assertThrows(Class<? extends Throwable> throwable, ThrowableRunnable runnable) {
    assertThrows(throwable, runnable, t -> {
    });
  }

  public static <T> T unexceptionally(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      return null;
    }
  }

  public static Throwable getThrowable(ThrowableRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      if (t instanceof ExecutionException || t instanceof RuntimeException)
        return t.getCause();
      return t;
    }

    return null;
  }

  public static void assertThrows(Class<? extends Throwable> throwable, ThrowableRunnable runnable,
      Consumer<Throwable> exceptionConsumer) {
    boolean fail = false;
    try {
      runnable.run();
      fail = true;
    } catch (Throwable t) {
      if (t instanceof ExecutionException || t instanceof RuntimeException)
        t = t.getCause();
      if (!throwable.isInstance(t)) {
        t.printStackTrace();
        Assert.fail(String.format("Bad exception type. Expected %s but was %s", throwable, t));
      }
      exceptionConsumer.accept(t);
    }

    if (fail)
      Assert.fail("No exception was thrown");
  }
}
