/*
 * Copyright 2018 the original author or authors.
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
package dev.failsafe.internal;

import java.util.BitSet;

/**
 * A CircuitStats implementation that counts execution results using a BitSet.
 */
class CountingCircuitStats implements CircuitStats {
  final BitSet bitSet;
  private final int size;

  /** Index to write next entry to */
  volatile int currentIndex;
  private volatile int occupiedBits;
  private volatile int successes;
  private volatile int failures;

  public CountingCircuitStats(int size, CircuitStats oldStats) {
    this.bitSet = new BitSet(size);
    this.size = size;

    if (oldStats != null) {
      synchronized (oldStats) {
        copyStats(oldStats);
      }
    }
  }

  /**
   * Copies the most recent stats from the {@code oldStats} into this in order from oldest to newest.
   */
  void copyStats(CircuitStats oldStats) {
    if (oldStats instanceof CountingCircuitStats) {
      CountingCircuitStats old = (CountingCircuitStats) oldStats;
      int bitsToCopy = Math.min(old.occupiedBits, size);
      int oldIndex = old.currentIndex - bitsToCopy;
      if (oldIndex < 0)
        oldIndex += old.occupiedBits;
      for (int i = 0; i < bitsToCopy; i++, oldIndex = old.indexAfter(oldIndex))
        setNext(old.bitSet.get(oldIndex));
    } else {
      copyExecutions(oldStats);
    }
  }

  @Override
  public void recordSuccess() {
    setNext(true);
  }

  @Override
  public void recordFailure() {
    setNext(false);
  }

  @Override
  public int getExecutionCount() {
    return occupiedBits;
  }

  @Override
  public int getFailureCount() {
    return failures;
  }

  @Override
  public synchronized int getFailureRate() {
    return (int) Math.round(occupiedBits == 0 ? 0 : (double) failures / (double) occupiedBits * 100.0);
  }

  @Override
  public int getSuccessCount() {
    return successes;
  }

  @Override
  public synchronized int getSuccessRate() {
    return (int) Math.round(occupiedBits == 0 ? 0 : (double) successes / (double) occupiedBits * 100.0);
  }

  @Override
  public synchronized void reset() {
    bitSet.clear();
    currentIndex = 0;
    occupiedBits = 0;
    successes = 0;
    failures = 0;
  }

  /**
   * Sets the value of the next bit in the bitset, returning the previous value, else -1 if no previous value was set
   * for the bit.
   *
   * @param value true if positive/success, false if negative/failure
   */
  synchronized int setNext(boolean value) {
    int previousValue = -1;
    if (occupiedBits < size)
      occupiedBits++;
    else
      previousValue = bitSet.get(currentIndex) ? 1 : 0;

    bitSet.set(currentIndex, value);
    currentIndex = indexAfter(currentIndex);

    if (value) {
      if (previousValue != 1)
        successes++;
      if (previousValue == 0)
        failures--;
    } else {
      if (previousValue != 0)
        failures++;
      if (previousValue == 1)
        successes--;
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