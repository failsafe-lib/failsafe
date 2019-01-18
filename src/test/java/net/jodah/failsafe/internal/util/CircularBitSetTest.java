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
package net.jodah.failsafe.internal.util;

import net.jodah.failsafe.util.Ratio;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class CircularBitSetTest {
  public void shouldReturnUnitializedValues() {
    CircularBitSet bs = new CircularBitSet(100, null);
    for (int i = 0; i < 100; i++) {
      assertEquals(bs.setNext(true), -1);
    }

    assertEquals(bs.setNext(true), 1);
    assertEquals(bs.setNext(true), 1);
  }

  public void testRatios() {
    CircularBitSet bs = new CircularBitSet(100, null);
    assertEquals(bs.positiveRatio(), new Ratio(0, 0));
    assertEquals(bs.positiveRatioValue(), 0.0);
    assertEquals(bs.negativeRatio(), new Ratio(0, 0));
    assertEquals(bs.negativeRatioValue(), 0.0);

    for (int i = 0; i < 50; i++)
      bs.setNext(i % 3 == 0);

    assertEquals(bs.positives(), 17);
    assertEquals(bs.positiveRatio(), new Ratio(17, 50));
    assertEquals(bs.positiveRatioValue(), .34);
    assertEquals(bs.negatives(), 33);
    assertEquals(bs.negativeRatio(), new Ratio(33, 50));
    assertEquals(bs.negativeRatioValue(), .66);

    for (int i = 0; i < 100; i++)
      bs.setNext(true);

    assertEquals(bs.positives(), 100);
    assertEquals(bs.positiveRatio(), new Ratio(100, 100));
    assertEquals(bs.positiveRatioValue(), 1.0);
    assertEquals(bs.negatives(), 0);
    assertEquals(bs.negativeRatio(), new Ratio(0, 100));
    assertEquals(bs.negativeRatioValue(), 0.0);
  }

  public void testCopyBitsToEqualSizedSet() {
    CircularBitSet left = new CircularBitSet(5, null);
    setBits(left, true, 2);
    setBits(left, false, 3);

    left.nextIndex = 0;
    CircularBitSet right = new CircularBitSet(5, left);
    assertValues(right, true, true, false, false, false);

    left.nextIndex = 2;
    right = new CircularBitSet(5, left);
    assertValues(right, false, false, false, true, true);

    left.nextIndex = 4;
    right = new CircularBitSet(5, left);
    assertValues(right, false, true, true, false, false);
  }

  public void testCopyBitsToSmallerSet() {
    CircularBitSet left = new CircularBitSet(10, null);
    setBits(left, true, 5);
    setBits(left, false, 5);

    left.nextIndex = 0;
    CircularBitSet right = new CircularBitSet(4, left);
    assertValues(right, false, false, false, false);

    left.nextIndex = 2;
    right = new CircularBitSet(4, left);
    assertValues(right, false, false, true, true);

    left.nextIndex = 7;
    right = new CircularBitSet(4, left);
    assertValues(right, true, true, false, false);
  }

  public void testCopyBitsToLargerSet() {
    CircularBitSet left = new CircularBitSet(5, null);
    setBits(left, true, 2);
    setBits(left, false, 3);

    left.nextIndex = 0;
    CircularBitSet right = new CircularBitSet(6, left);
    assertValues(right, true, true, false, false, false);

    left.nextIndex = 2;
    right = new CircularBitSet(6, left);
    assertValues(right, false, false, false, true, true);

    left.nextIndex = 4;
    right = new CircularBitSet(6, left);
    assertValues(right, false, true, true, false, false);
  }

  private boolean[] valuesFor(CircularBitSet bs) {
    boolean[] values = new boolean[bs.occupiedBits()];
    for (int i = 0; i < values.length; i++)
      values[i] = bs.bitSet.get(i);
    return values;
  }

  private void assertValues(CircularBitSet bs, boolean... right) {
    boolean[] left = valuesFor(bs);
    assertTrue(Arrays.equals(left, right), Arrays.toString(left) + " != " + Arrays.toString(right));
  }

  private void setBits(CircularBitSet bs, boolean value, int count) {
    for (int i = 0; i < count; i++)
      bs.setNext(value);
  }
}
