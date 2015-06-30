package net.jodah.recurrent;

public class Runnables {
  public static <T> Runnable runnable(CompletionListener<T> listener, final T result, final Throwable failure) {
    return new Runnable() {
      @Override
      public void run() {
        listener.onCompletion(result, failure);
      }
    };
  }
}
