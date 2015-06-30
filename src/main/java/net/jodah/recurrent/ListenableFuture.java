package net.jodah.recurrent;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

public interface ListenableFuture<T> extends Future<T> {
  ListenableFuture<T> whenComplete(CompletionListener<T> completionListener);

  ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener);

  ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener, ScheduledExecutorService executor);
}
