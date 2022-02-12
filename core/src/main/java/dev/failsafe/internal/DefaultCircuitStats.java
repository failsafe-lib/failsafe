package dev.failsafe.internal;

/**
 * A default CircuitStats implementation that tracks a single execution result.
 */
class DefaultCircuitStats implements CircuitStats {
  volatile int result = -1;

  @Override
  public int getFailureCount() {
    return result == 0 ? 1 : 0;
  }

  @Override
  public int getExecutionCount() {
    return result == -1 ? 0 : 1;
  }

  @Override
  public int getSuccessCount() {
    return result == 1 ? 1 : 0;
  }

  @Override
  public int getFailureRate() {
    return getFailureCount() * 100;
  }

  @Override
  public int getSuccessRate() {
    return getSuccessCount() * 100;
  }

  @Override
  public void recordFailure() {
    result = 0;
  }

  @Override
  public void recordSuccess() {
    result = 1;
  }

  @Override
  public void reset() {
    result = -1;
  }
}
