/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe.testing;

import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import org.testng.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;

/**
 * Utilities to assist with performing assertions.
 */
public class Asserts {
  /**
   * Records assertions from any thread so that they can be re-thrown from a main test thread.
   */
  public static class Recorder {
    private volatile AssertionError error;

    public void reset() {
      this.error = null;
    }

    public void assertEquals(Object expected, Object actual) {
      if (expected == null && actual == null)
        return;
      if (expected != null && expected.equals(actual))
        return;
      fail(format(expected, actual));
    }

    public void assertFalse(boolean condition) {
      if (condition)
        fail("expected false");
    }

    public void assertNotNull(Object object) {
      if (object == null)
        fail("expected not null");
    }

    public void assertNull(Object object) {
      if (object != null)
        fail(format("null", object));
    }

    public void assertTrue(boolean condition) {
      if (!condition)
        fail("expected true");
    }

    public void fail(String reason) {
      fail(new AssertionError(reason));
    }

    public void fail(Throwable reason) {
      if (reason instanceof AssertionError)
        error = (AssertionError) reason;
      else {
        error = new AssertionError();
        error.initCause(reason);
      }
    }

    public void throwFailure() {
      if (error != null)
        throw error;
    }

    private String format(Object expected, Object actual) {
      return "expected:<" + expected + "> but was:<" + actual + ">";
    }
  }

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

  public static void assertMatches(Throwable actual, List<Class<? extends Throwable>> throwableHierarchy) {
    Throwable current = actual;
    for (Class<? extends Throwable> expected : throwableHierarchy) {
      if (!expected.equals(current.getClass()))
        Assert.fail(
          String.format("Bad exception type. Expected %s but was %s", Arrays.toString(throwableHierarchy.toArray()),
            actual), actual);
      current = current.getCause();
    }
  }

  public static void assertThrows(CheckedRunnable runnable, Throwable throwable) {
    try {
      runnable.run();
      Assert.fail("No exception was thrown");
    } catch (Throwable t) {
      assertEquals(t, throwable, "The expected exception was not thrown");
    }
  }

  @SafeVarargs
  public static void assertThrows(CheckedRunnable runnable, Class<? extends Throwable>... throwableHierarchy) {
    assertThrows(runnable, t -> {
    }, Arrays.asList(throwableHierarchy));
  }

  public static void assertThrows(CheckedRunnable runnable, List<Class<? extends Throwable>> throwableHierarchy) {
    assertThrows(runnable, t -> {
    }, throwableHierarchy);
  }

  public static void assertThrows(CheckedRunnable runnable, Consumer<Throwable> exceptionConsumer,
    List<Class<? extends Throwable>> throwableHierarchy) {
    boolean fail = false;
    try {
      runnable.run();
      fail = true;
    } catch (Throwable t) {
      assertMatches(t, throwableHierarchy);
      exceptionConsumer.accept(t);
    }

    if (fail)
      Assert.fail("No exception was thrown. Expected: " + throwableHierarchy);
  }

  public static <T> void assertThrowsSup(CheckedSupplier<T> supplier,
    List<Class<? extends Throwable>> throwableHierarchy) {
    assertThrows(supplier::get, t -> {
    }, throwableHierarchy);
  }
}
