package net.jodah.recurrent;

public interface RetryableRunnable {
  void run(Invocation invocation);
}
