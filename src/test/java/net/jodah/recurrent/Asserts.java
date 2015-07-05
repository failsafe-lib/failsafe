package net.jodah.recurrent;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.function.Consumer;

import org.testng.Assert;

import net.jodah.recurrent.Testing.ThrowableRunnable;

public class Asserts {
  @SafeVarargs
  public static boolean matches(Throwable actual, Class<? extends Throwable>... throwableHierarchy) {
    Throwable current = actual;
    for (Class<? extends Throwable> expected : throwableHierarchy) {
      if (!expected.isInstance(current))
        return false;
      current = current.getCause();
    }
    return true;
  }
  
  @SafeVarargs
  public static void assertMatches(Throwable actual, Class<? extends Throwable>... throwableHierarchy) {
    Throwable current = actual;
    for (Class<? extends Throwable> expected : throwableHierarchy) {
      if (!expected.isInstance(current))
        Assert.fail(
            String.format("Bad exception type. Expected %s but was %s", Arrays.toString(throwableHierarchy), actual));
      current = current.getCause();
    }
  }

  public static void assertThrows(Throwable throwable, ThrowableRunnable runnable) {
    try {
      runnable.run();
      Assert.fail("No exception was thrown");
    } catch (Throwable t) {
      assertEquals(throwable, t, "The expected exception was not thrown");
    }
  }

  @SafeVarargs
  public static void assertThrows(ThrowableRunnable runnable, Class<? extends Throwable>... throwableHierarchy) {
    assertThrows(runnable, t -> {
    } , throwableHierarchy);
  }

  @SafeVarargs
  public static void assertThrows(ThrowableRunnable runnable, Consumer<Throwable> exceptionConsumer,
      Class<? extends Throwable>... throwableHierarchy) {
    boolean fail = false;
    try {
      runnable.run();
      fail = true;
    } catch (Throwable t) {
      assertMatches(t, throwableHierarchy);
      exceptionConsumer.accept(t);
    }

    if (fail)
      Assert.fail("No exception was thrown");
  }
}
