package net.jodah.failsafe;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

@Test
public class AbstractExecutionTest {
  public void testRandomizeDelayForFactor() {
    assertEquals(AbstractExecution.randomizeDelay(100, .5, 0), 150);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .25), 125);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .5), 100);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .75), 75);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .9999), 50);

    assertEquals(AbstractExecution.randomizeDelay(500, .5, .25), 625);
    assertEquals(AbstractExecution.randomizeDelay(500, .5, .75), 375);
    assertEquals(AbstractExecution.randomizeDelay(50000, .5, .25), 62500);
  }

  public void testRandomizeDelayForDuration() {
    assertEquals(AbstractExecution.randomizeDelay(100, 50, 0), 150);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .25), 125);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .5), 100);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .75), 75);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .9999), 50);

    assertEquals(AbstractExecution.randomizeDelay(500, 50, .25), 525);
    assertEquals(AbstractExecution.randomizeDelay(50000, 5000, .25), 52500);
  }
}
