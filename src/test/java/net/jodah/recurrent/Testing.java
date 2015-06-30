package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class Testing {
  public static class RecordingCallable<T> implements Callable<T> {
    public int failures;
    int n;
    RuntimeException exception;
    T eventualResult;

    RecordingCallable(int n, RuntimeException exception, T eventualResult) {
      this.n = n;
      this.exception = exception;
      this.eventualResult = eventualResult;
    }

    public T call() {
      if (n == -1 || failures < n) {
        failures++;
        throw exception;
      }
      return eventualResult;
    }
  };

  public static void withExceptions(Runnable runnable) {
    withExceptions(runnable, t -> {
    });
  }

  public static void withExceptions(Runnable runnable, Consumer<Throwable> exceptionConsumer) {
    try {
      runnable.run();
    } catch (Throwable t) {
      exceptionConsumer.accept(t);
    }
  }

  public static <T> RecordingCallable<T> failNTimes(int n, RuntimeException exception, T eventualResult) {
    return new RecordingCallable<T>(n, exception, eventualResult);
  }

  public static <T> RecordingCallable<T> failAlways(RuntimeException exception) {
    return new RecordingCallable<T>(-1, exception, null);
  }
}
