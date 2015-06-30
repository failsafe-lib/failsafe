package net.jodah.recurrent;

public class Testing {
  public static class RecordingRunnable implements Runnable {
    public int failures;
    int n;
    RuntimeException exception;

    RecordingRunnable(int n, RuntimeException exception) {
      this.n = n;
      this.exception = exception;
    }

    public void run() {
      failures++;
      if (n == -1 || failures < n)
        throw exception;
    }
  };

  public static <T> RecordingRunnable failNTimes(int n, RuntimeException exception) {
    return new RecordingRunnable(n, exception);
  }

  public static RecordingRunnable failAlways(RuntimeException exception) {
    return new RecordingRunnable(-1, exception);
  }
}
