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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;

/**
 * Utilities to assist with performing assertions.
 */
public class Asserts {
  static class CompositeError extends Error {
    private final List<? extends Error> errors;

    CompositeError(List<? extends Error> errors) {
      this.errors = errors;
    }

    @Override
    public String getMessage() {
      Formatter fmt = new Formatter().format("Errors encountered:%n%n");
      int index = 1;
      for (Error error : errors) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        fmt.format("%s) %s%n", index++, writer.getBuffer());
      }

      if (errors.size() == 1) {
        fmt.format("1 error");
      } else {
        fmt.format("%s errors", errors.size());
      }

      return fmt.toString();
    }
  }

  /**
   * Records assertions from any thread so that they can be re-thrown from a main test thread.
   */
  public static class Recorder {
    private List<AssertionError> errors = new CopyOnWriteArrayList<>();

    public void reset() {
      errors.clear();
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
        errors.add((AssertionError) reason);
      else {
        AssertionError error = new AssertionError();
        error.initCause(reason);
        errors.add(error);
      }
    }

    public void throwFailures() {
      if (!errors.isEmpty())
        throw new CompositeError(errors);
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

  @SafeVarargs
  public static void assertMatches(Throwable actual, Class<? extends Throwable>... throwableHierarchy) {
    assertMatches(actual, Arrays.asList(throwableHierarchy));
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
  public static void assertThrows(CheckedRunnable runnable, Class<? extends Throwable>... expectedExceptions) {
    assertThrows(runnable, t -> Arrays.asList(expectedExceptions));
  }

  public static void assertThrows(CheckedRunnable runnable, List<Class<? extends Throwable>> expectedExceptions) {
    assertThrows(runnable, t -> expectedExceptions);
  }

  public static <T> void assertThrowsSup(CheckedSupplier<T> supplier,
    List<Class<? extends Throwable>> expectedExceptions) {
    assertThrows(supplier::get, t -> expectedExceptions);
  }

  public static void assertThrows(CheckedRunnable runnable,
    Function<Throwable, List<Class<? extends Throwable>>> expectedExceptionsFn) {
    try {
      runnable.run();
      Assert.fail("No exception was thrown. Expected: " + expectedExceptionsFn.apply(null));
    } catch (Throwable t) {
      assertMatches(t, expectedExceptionsFn.apply(t));
    }
  }
}
