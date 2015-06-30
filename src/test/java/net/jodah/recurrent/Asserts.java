package net.jodah.recurrent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class Asserts {
  public static void shouldFail(Runnable runnable, Class<? extends Exception> expected) {
    try {
      runnable.run();
      fail("A failure was expected");
    } catch (Exception e) {
      assertTrue(e.getClass().isAssignableFrom(expected), "The expected exception was not of the expected type " + e);
    }
  }

  public static void shouldFail(Runnable runnable, Exception expected) {
    try {
      runnable.run();
      fail("A failure was expected");
    } catch (Exception e) {
      assertEquals(expected, e, "The expected exception was not of the expected type " + e);
    }
  }
}
