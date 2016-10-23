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
package net.jodah.failsafe;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.function.Consumer;

import org.testng.Assert;

import net.jodah.failsafe.function.CheckedRunnable;

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
      if (!expected.equals(current.getClass()))
        Assert.fail(
            String.format("Bad exception type. Expected %s but was %s", Arrays.toString(throwableHierarchy), actual));
      current = current.getCause();
    }
  }

  public static void assertThrows(CheckedRunnable runnable, Throwable throwable) {
    try {
      runnable.run();
      Assert.fail("No exception was thrown");
    } catch (Throwable t) {
      assertEquals(throwable, t, "The expected exception was not thrown");
    }
  }

  @SafeVarargs
  public static void assertThrows(CheckedRunnable runnable, Class<? extends Throwable>... throwableHierarchy) {
    assertThrows(runnable, t -> {
    } , throwableHierarchy);
  }

  @SafeVarargs
  public static void assertThrows(CheckedRunnable runnable, Consumer<Throwable> exceptionConsumer,
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
