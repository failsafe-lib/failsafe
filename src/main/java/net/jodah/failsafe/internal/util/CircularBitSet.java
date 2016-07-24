package net.jodah.failsafe.internal.util;

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
