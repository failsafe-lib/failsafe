package net.jodah.failsafe.testing;

/**
 * Utilities to assist with creating mocks.
 */
public class Mocking extends Asserts {
  public static class ConnectException extends RuntimeException {
  }

  /**
   * A mock Service implementation that throws a specified number of failures then returns a result.
   */
  public static class Service {
    int numFailuresExpected;
    boolean resultExpected;
    int numFailuresSoFar;

    public Service() {
      numFailuresExpected = -1;
    }

    public Service(boolean result) {
      resultExpected = result;
    }

    public Service(int numFailures, boolean result) {
      numFailuresExpected = numFailures;
      resultExpected = result;
    }

    public boolean connect() {
      if (numFailuresExpected > -1 && numFailuresExpected == numFailuresSoFar)
        return resultExpected;
      else {
        numFailuresSoFar++;
        throw new ConnectException();
      }
    }

    public void reset() {
      numFailuresSoFar = 0;
    }
  }

  public static Service mockFailingService() {
    return new Service();
  }

  public static Service mockService(boolean result) {
    return new Service(result);
  }

  public static Service mockService(int numFailures, boolean result) {
    return new Service(numFailures, result);
  }

  public static Exception[] failures(int numFailures, Exception failure) {
    Exception[] failures = new Exception[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failure;
    return failures;
  }
}
