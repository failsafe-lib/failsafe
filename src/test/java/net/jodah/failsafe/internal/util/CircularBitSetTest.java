package net.jodah.failsafe.internal.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import net.jodah.failsafe.internal.util.CircularBitSet;

@Test
public class CircularBitSetTest {
  public void shouldReturnUnitializedValues() {
    CircularBitSet bs = new CircularBitSet(100);
    for (int i = 0; i < 100; i++) {
      assertEquals(bs.setNext(true), -1);
    }

    assertEquals(bs.setNext(true), 1);
    assertEquals(bs.setNext(true), 1);
  }

  public void testRatios() {
    CircularBitSet bs = new CircularBitSet(100);

    for (int i = 0; i < 50; i++)
      bs.setNext(i % 3 == 0);
    assertEquals(bs.positiveRatio(), .34);
    assertEquals(bs.negativeRatio(), .66);

    for (int i = 0; i < 100; i++)
      bs.setNext(true);
    assertEquals(bs.positiveRatio(), 1.0);
    assertEquals(bs.negativeRatio(), 0.0);
  }
}
