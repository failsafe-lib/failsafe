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

import java.util.BitSet;

/**
 * A circular BitSet implementation that tracks the cardinality and ratios of positive and negative bits.
 *
 * @author Jonathan Halterman
 */
public class CircularBitSet {
  final BitSet bitSet;
  private final int size;

  /** Index to write next entry to */
  volatile int nextIndex;
  private volatile int occupiedBits;
  private volatile int positives;
  private volatile int negatives;

  public CircularBitSet(int size, CircularBitSet oldBitSet) {
    this.bitSet = new BitSet(size);
    this.size = size;

    if (oldBitSet != null) {
      synchronized (oldBitSet) {
        copyBits(oldBitSet, this);
      }
    }
  }

  /**
   * Copies the most recent bits from the {@code left} BitSet to the {@code right} BitSet in order from oldest to
   * newest.
   */
  static void copyBits(CircularBitSet left, CircularBitSet right) {
    int bitsToCopy = Math.min(left.occupiedBits, right.size);
    int index = left.nextIndex - bitsToCopy;
    if (index < 0)
      index += left.occupiedBits;
    for (int i = 0; i < bitsToCopy; i++, index = left.indexAfter(index))
      right.setNext(left.bitSet.get(index));
  }

  /**
   * Returns the number of negatives.
   */
  public int negatives() {
    return negatives;
  }

  /**
   * Returns the ratio of positive bits to the number of occupied bits.
   */
  public Ratio negativeRatio() {
    return new Ratio(negatives, occupiedBits);
  }

  /**
   * Returns the ratio value of positive bits to the number of occupied bits.
   */
  public double negativeRatioValue() {
    return occupiedBits == 0 ? 0 : (double) negatives / (double) occupiedBits;
  }

  /**
   * Returns the number of occupied bits in the set.
   */
  public int occupiedBits() {
    return occupiedBits;
  }

  /**
   * Returns the number of positives.
   */
  public int positives() {
    return positives;
  }

  /**
   * Returns the ratio of positive bits to the number of occupied bits.
   */
  public Ratio positiveRatio() {
    return new Ratio(positives, occupiedBits);
  }

  /**
   * Returns the ratio value of positive bits to the number of occupied bits.
   */
  public double positiveRatioValue() {
    return occupiedBits == 0 ? 0 : (double) positives / (double) occupiedBits;
  }

  /**
   * Sets the value of the next bit in the bitset, returning the previous value, else -1 if no previous value was set
   * for the bit.
   *
   * @param value true if positive/success, false if negative/failure
   */
  public synchronized int setNext(boolean value) {
    int previousValue = -1;
    if (occupiedBits < size)
      occupiedBits++;
    else
      previousValue = bitSet.get(nextIndex) ? 1 : 0;

    bitSet.set(nextIndex, value);
    nextIndex = indexAfter(nextIndex);

    if (value) {
      if (previousValue != 1)
        positives++;
      if (previousValue == 0)
        negatives--;
    } else {
      if (previousValue != 0)
        negatives++;
      if (previousValue == 1)
        positives--;
    }

    return previousValue;
  }

  /**
   * Returns an array representation of the BitSet entries.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append('[');
    for (int i = 0; i < occupiedBits; i++) {
      if (i > 0)
        sb.append(", ");
      sb.append(bitSet.get(i));
    }
    return sb.append(']').toString();
  }

  /**
   * Returns the index after the {@code index}.
   */
  private int indexAfter(int index) {
    return index == size - 1 ? 0 : index + 1;
  }
}
