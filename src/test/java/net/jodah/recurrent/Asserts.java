package net.jodah.recurrent;

import static org.testng.Assert.assertEquals;

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

  public static void assertThrows(Class<? extends Throwable> throwable, ThrowableRunnable runnable,
      Consumer<Throwable> exceptionConsumer) {
    boolean fail = false;
    try {
      runnable.run();
      fail = true;
    } catch (Throwable t) {
      if (!throwable.isInstance(t))
        Assert.fail("Bad exception type");
      exceptionConsumer.accept(t);
    }

    if (fail)
      Assert.fail("No exception was thrown");
  }
}
