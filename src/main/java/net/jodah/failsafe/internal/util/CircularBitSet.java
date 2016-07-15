package net.jodah.failsafe.internal.util;

import java.util.BitSet;

/**
 * A circular BitSet implementation that tracks the cardinality and ratios of positive and negative bits.
 * 
 * @author Jonathan Halterman
 */
public class CircularBitSet {
  private final BitSet bitSet;
  private final int size;

  private volatile int currentIndex;
  private volatile int occupiedBits;
  private volatile int positives;
  private volatile int negatives;

  public CircularBitSet(int size, CircularBitSet oldBitSet) {
    this.bitSet = new BitSet(size);
    this.size = size;

    // Initialize from oldBitSet
    if (oldBitSet != null) {
      for (int i = 0; i < size && i < oldBitSet.occupiedBits; i++)
        setNext(oldBitSet.bitSet.get(i));
    }
  }

  /**
   * Returns the ratio of positive bits to the number of occupied bits.
   */
  public double negativeRatio() {
    return (double) negatives / (double) occupiedBits;
  }

  /**
   * Returns the number of occupied bits in the set.
   */
  public int occupiedBits() {
    return occupiedBits;
  }

  /**
   * Returns the ratio of positive bits to the number of occupied bits.
   */
  public double positiveRatio() {
    return (double) positives / (double) occupiedBits;
  }

  /**
   * Sets the value of the next bit in the bitset, returning the previous value, else -1 if no previous value was set
   * for the bit.
   */
  public synchronized int setNext(boolean value) {
    int previousValue = -1;
    if (occupiedBits < size)
      occupiedBits++;
    else
      previousValue = bitSet.get(currentIndex) ? 1 : 0;

    bitSet.set(currentIndex, value);
    if (currentIndex == size - 1)
      currentIndex = 0;
    else
      currentIndex++;

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

  @Override
  public String toString() {
    return bitSet.toString();
  }
}
