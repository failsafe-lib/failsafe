package net.jodah.failsafe.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

public class Java8Example {
  @SuppressWarnings("unused")
  public static void main(String... args) {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    RetryPolicy retryPolicy = new RetryPolicy();

    // Create a retryable functional interface
    Function<String, String> bar = value -> Failsafe.with(retryPolicy).get(() -> value + "bar");

    // Create a retryable runnable Stream
    Failsafe.with(retryPolicy).run(() -> Stream.of("foo").map(value -> value + "bar").forEach(System.out::println));

    // Create a retryable callable Stream
    Failsafe.with(retryPolicy).get(() -> Stream.of("foo")
        .map(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"))
        .collect(Collectors.toList()));

    // Create a individual retryable Stream operation
    Stream.of("foo").map(value -> Failsafe.with(retryPolicy).get(() -> value + "bar")).forEach(System.out::println);

    // Create a retryable CompletableFuture
    Failsafe.with(retryPolicy).with(executor).future(() -> CompletableFuture.supplyAsync(() -> "foo")
        .thenApplyAsync(value -> value + "bar")
        .thenAccept(System.out::println));

    // Create an individual retryable CompletableFuture stages
    CompletableFuture.supplyAsync(() -> Failsafe.with(retryPolicy).get(() -> "foo"))
        .thenApplyAsync(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"))
        .thenAccept(System.out::println);
  }
}
