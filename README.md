# Recurrent
[![Build Status](https://travis-ci.org/jhalterman/concurrentunit.svg)](https://travis-ci.org/jhalterman/concurrentunit)

*Simple, sophisticated retries.*

## Introduction

Recurrent is a simple, zero-dependency library for performing retries. It features:

* Synchronous and asynchronous retries
* Java 6+ support with seamless Java 8 integration
* Integration with 3rd party asynchronous APIs
* Transparent integration into public APIs

## Usage

#### Retry Policies

Recurrent supports flexible [retry policies][RetryPolicy] that allow you to express the maximum number of retries, delay between retries including backoff, and maximum duration to retry for:

```java
RetryPolicy delayPolicy = new RetryPolicy()
  .withDelay(1, TimeUnit.SECONDS)
  .withMaxRetries(100);
    
RetryPolicy backoffPolicy = new RetryPolicy()
  .withBackoff(1, 30, TimeUnit.SECONDS)
  .withMaxDuration(5, TimeUnit.MINUTES);
```

#### Synchronous Retries

Synchronous invocations are performed and retried in the calling thread until the invocation succeeds or the retry policy is exceeded:

```java
Connection connection = Recurrent.get(() -> connect(), retryPolicy);
```

#### Asynchronous Retries

Asynchronous invocations are performed and retried on a scheduled executor. When the invocation succeeds or the retry policy is exceeded, the resulting [RecurrentFuture] is completed and any [listeners](http://jodah.net/recurrent/javadoc/net/jodah/recurrent/event/package-summary.html) registered against it are called:

```java
Recurrent.get(() -> connect(), retryPolicy, executor)
  .whenSuccess(connection -> log.info("Connected to {}", connection))
  .whenFailure(failure -> log.error("Connection attempts failed", failure));
```

#### Asynchronous API Integration

Asynchronous code reports completion via callbacks rather than by throwing an exception. Recurrent provides [ContextualRunnable] and [ContextualCallable] classes that can be used in a callback to manually perform retries or completion:

```java
Recurrent.get(invocation -> {
  someService.connect(host, port).whenComplete((result, failure) -> {
	if (failure == null)
	  invocation.complete(result);
	else if (!invocation.retry(failure))
      log.error("Connection attempts failed", failure);
  }
}, retryPolicy, executor));
```

#### CompletableFuture Integration

Java 8 users can use Recurrent to retry [CompletableFuture] calls:

```java
Recurrent.future(() -> CompletableFuture.supplyAsync(() -> "foo")
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println), retryPolicy, executor);
```

#### Java 8 Functional Interfaces

Recurrent can be used to create retryable Java 8 functional interfaces:

```java
Function<String, Connection> connect =
  address -> Recurrent.get(() -> connect(address), retryPolicy);
```

We can retry streams:

```java
Recurrent.run(() -> Stream.of("foo")
  .map(value -> value + "bar"), retryPolicy);
```

Individual Stream operations:

```java
Stream.of("foo")
  .map(value -> Recurrent.get(() -> value + "bar", retryPolicy));
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Recurrent.get(() -> "foo", retryPolicy))
  .thenApplyAsync(value -> Recurrent.get(() -> value + "bar", retryPolicy));
```

## Example Integrations

Recurrent was designed to integrate nicely with existing libraries, including 3rd party asynchronous libraries. Here are some example integrations:

* [Java 8](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/Java8Example.java)
* [Netty](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/NettyExample.java)
* [RxJava](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/RxJavaExample.java)

## Public API Integration

Recurrent is great for integrating into libraries and public APIs, allowing your users to configure retry policies for different opererations. One integration approach is to subclass the RetryPolicy class, then expose that as part of your API while the rest of Recurrent remains internal. Another approach is to use something like the [Maven shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to relocate Recurrent into your project's package structure as desired.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## License

Copyright 2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

[RetryPolicy]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RetryPolicy.html
[RecurrentFuture]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RecurrentFuture.html
[ContextualRunnable]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/ContextualRunnable.html
[ContextualCallable]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/ContextualCallable.html
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html