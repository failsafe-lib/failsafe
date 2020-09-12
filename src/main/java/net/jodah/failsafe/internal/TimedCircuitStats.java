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
package net.jodah.failsafe.internal;

import java.time.Duration;
import java.util.Arrays;

/**
 * A CircuitBreakerStats implementation that counts execution results within a time period, and buckets results to
 * minimize overhead.
 */
class TimedCircuitStats implements CircuitStats {
  static final int DEFAULT_BUCKET_COUNT = 10;

  private final Clock clock;
  private final long bucketSizeMillis;
  private final long windowSizeMillis;

  // Mutable state
  final Bucket[] buckets;
  private final Stat summary;
  volatile int currentIndex;

  static class Clock {
    long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  }

  static class Stat {
    int successes;
    int failures;

    void reset() {
      successes = 0;
      failures = 0;
    }

    void add(Bucket bucket) {
      successes += bucket.successes;
      failures += bucket.failures;
    }

    void remove(Bucket bucket) {
      successes -= bucket.successes;
      failures -= bucket.failures;
    }

    @Override
    public String toString() {
      return "[s=" + successes + ", f=" + failures + ']';
    }
  }

  static class Bucket extends Stat {
    long startTimeMillis = -1;

    void reset(long startTimeMillis) {
      this.startTimeMillis = startTimeMillis;
      reset();
    }

    void copyFrom(Bucket other) {
      startTimeMillis = other.startTimeMillis;
      successes = other.successes;
      failures = other.failures;
    }

    @Override
    public String toString() {
      return "[startTime=" + startTimeMillis + ", s=" + successes + ", f=" + failures + ']';
    }
  }

  public TimedCircuitStats(int bucketCount, Duration thresholdingPeriod, Clock clock, CircuitStats oldStats) {
    this.clock = clock;
    this.buckets = new Bucket[bucketCount];
    bucketSizeMillis = thresholdingPeriod.toMillis() / buckets.length;
    windowSizeMillis = bucketSizeMillis * buckets.length;
    for (int i = 0; i < buckets.length; i++)
      buckets[i] = new Bucket();
    this.summary = new Stat();

    if (oldStats == null) {
      buckets[0].startTimeMillis = clock.currentTimeMillis();
    } else {
      synchronized (oldStats) {
        copyStats(oldStats);
      }
    }
  }

  /**
   * Copies the most recent stats from the {@code oldStats} into this in order from oldest to newest and orders buckets
   * from oldest to newest, with uninitialized buckets counting as oldest.
   */
  void copyStats(CircuitStats oldStats) {
    if (oldStats instanceof TimedCircuitStats) {
      TimedCircuitStats old = (TimedCircuitStats) oldStats;
      int bucketsToCopy = Math.min(old.buckets.length, buckets.length);

      // Get oldest index to start copying from
      int oldIndex = old.indexAfter(old.currentIndex);
      for (int i = 0; i < bucketsToCopy; i++)
        oldIndex = old.indexBefore(oldIndex);

      for (int i = 0; i < bucketsToCopy; i++) {
        if (i != 0) {
          oldIndex = old.indexAfter(oldIndex);
          currentIndex = nextIndex();
        }
        buckets[currentIndex].copyFrom(old.buckets[oldIndex]);
        summary.add(buckets[currentIndex]);
      }
    } else {
      buckets[0].startTimeMillis = clock.currentTimeMillis();
      copyExecutions(oldStats);
    }
  }

  @Override
  public synchronized void recordSuccess() {
    getCurrentBucket().successes++;
    summary.successes++;
  }

  @Override
  public synchronized void recordFailure() {
    getCurrentBucket().failures++;
    summary.failures++;
  }

  @Override
  public int getExecutionCount() {
    return summary.successes + summary.failures;
  }

  @Override
  public int getFailureCount() {
    return summary.failures;
  }

  @Override
  public synchronized int getFailureRate() {
    int executions = getExecutionCount();
    return (int) Math.round(executions == 0 ? 0 : (double) summary.failures / (double) executions * 100.0);
  }

  @Override
  public int getSuccessCount() {
    return summary.successes;
  }

  @Override
  public synchronized int getSuccessRate() {
    int executions = getExecutionCount();
    return (int) Math.round(executions == 0 ? 0 : (double) summary.successes / (double) executions * 100.0);
  }

  /**
   * Returns the current bucket based on the current time, moving the internal storage to the current bucket if
   * necessary, resetting bucket stats along the way.
   */
  synchronized Bucket getCurrentBucket() {
    Bucket previousBucket, currentBucket = buckets[currentIndex];
    long currentTime = clock.currentTimeMillis();
    long timeDiff = currentTime - currentBucket.startTimeMillis;
    if (timeDiff >= bucketSizeMillis) {
      int bucketsToMove = (int) (timeDiff / bucketSizeMillis);
      if (bucketsToMove <= buckets.length) {
        // Reset some buckets
        do {
          currentIndex = nextIndex();
          previousBucket = currentBucket;
          currentBucket = buckets[currentIndex];
          long bucketStartTime = currentBucket.startTimeMillis == -1 ?
            previousBucket.startTimeMillis + bucketSizeMillis :
            currentBucket.startTimeMillis + windowSizeMillis;
          summary.remove(currentBucket);
          currentBucket.reset(bucketStartTime);
          bucketsToMove--;
        } while (bucketsToMove > 0);
      } else {
        // Reset all buckets
        long startTimeMillis = currentTime;
        for (Bucket bucket : buckets) {
          bucket.reset(startTimeMillis);
          startTimeMillis += bucketSizeMillis;
        }
        summary.reset();
        currentIndex = 0;
      }
    }

    return currentBucket;
  }

  /**
   * Returns the next index.
   */
  private int nextIndex() {
    return (currentIndex + 1) % buckets.length;
  }

  /**
   * Returns the index after the {@code index}.
   */
  private int indexAfter(int index) {
    return index == buckets.length - 1 ? 0 : index + 1;
  }

  /**
   * Returns the index before the {@code index}.
   */
  private int indexBefore(int index) {
    return index == 0 ? buckets.length - 1 : index - 1;
  }

  @Override
  public String toString() {
    return "TimedCircuitStats[summary=" + summary + ", buckets=" + Arrays.toString(buckets) + ']';
  }
}
