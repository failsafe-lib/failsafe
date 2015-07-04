package net.jodah.recurrent.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RetryPolicy;

@Test
public class Java8Example {
  @SuppressWarnings("unused")
  public void example() {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    RetryPolicy retryPolicy = new RetryPolicy();

    // Create a retryable functional interface
    Function<String, String> bar = value -> Recurrent.get(() -> value + "bar", retryPolicy);

    // Create a retryable runnable Stream
    Recurrent.run(() -> Stream.of("foo")
        .map(value -> Recurrent.get(() -> value + "bar", retryPolicy))
        .forEach(System.out::println), retryPolicy);
        
    // Create a retryable callable Stream
    Recurrent.get(() -> Stream.of("foo")
        .map(value -> Recurrent.get(() -> value + "bar", retryPolicy))
        .collect(Collectors.toList()), retryPolicy);

    // Create a individual retryable Stream operation
    Stream.of("foo")
        .map(value -> Recurrent.get(() -> value + "bar", retryPolicy))
        .forEach(System.out::println);
    
    // Create a retryable CompletableFuture
    Recurrent.future(() -> CompletableFuture.supplyAsync(() -> "foo")
        .thenApplyAsync(value -> value + "bar")
        .thenAccept(System.out::println), retryPolicy, executor);

    // Create an individual retryable CompletableFuture stages
    CompletableFuture.supplyAsync(() -> Recurrent.get(() -> "foo", retryPolicy))
        .thenApplyAsync(value -> Recurrent.get(() -> value + "bar", retryPolicy))
        .thenAccept(System.out::println);
  }
}
