package net.jodah.recurrent;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import net.jodah.recurrent.event.CompletionListener;
import net.jodah.recurrent.event.FailureListener;
import net.jodah.recurrent.event.SuccessListener;

public interface ListenableFuture<T> extends Future<T> {
  ListenableFuture<T> whenComplete(CompletionListener<T> completionListener);

  ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener);

  ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener, ScheduledExecutorService executor);

  ListenableFuture<T> whenFailure(FailureListener failureListener);

  ListenableFuture<T> whenFailureAsync(FailureListener failureListener);

  ListenableFuture<T> whenFailureAsync(FailureListener failureListener, ScheduledExecutorService executor);

  ListenableFuture<T> whenSuccess(SuccessListener<T> successListener);

  ListenableFuture<T> whenSuccessAsync(SuccessListener<T> successListener);

  ListenableFuture<T> whenSuccessAsync(SuccessListener<T> successListener, ScheduledExecutorService executor);
}
