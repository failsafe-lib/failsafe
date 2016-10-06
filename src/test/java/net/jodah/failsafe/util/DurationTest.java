
// DurationTest.java --
//
// DurationTest.java is part of ElectricCommander.
//
// Copyright (c) 2005-2016 Electric Cloud, Inc.
// All rights reserved.
//

package net.jodah.failsafe.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

@Test
public class DurationTest {
  public void testEquals() throws Exception {
    assertTrue(!new Duration(30, SECONDS).equals(null));
    assertEquals(new Duration(30000, MILLISECONDS), new Duration(30, SECONDS));
    assertNotEquals(new Duration(30, MILLISECONDS), new Duration(30, SECONDS));
  }

  @Test
  public void testHashCode() throws Exception {
    assertEquals(new Duration(30000, MILLISECONDS).hashCode(), new Duration(30, SECONDS).hashCode());
    assertNotEquals(new Duration(30, MILLISECONDS).hashCode(), new Duration(30, SECONDS).hashCode());
  }

  @Test
  public void testToString() throws Exception {
    assertEquals(new Duration(30, SECONDS).toString(), "30 seconds");
  }
}
