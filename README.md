# Recurrent
[![Build Status](https://travis-ci.org/jhalterman/recurrent.svg)](https://travis-ci.org/jhalterman/recurrent)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.jodah/recurrent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.jodah/recurrent) 

*Simple, sophisticated retries.*

## Introduction

Recurrent is a simple, zero-dependency library for performing retries. It features:

* [Flexible retry policies](#retry-policies)
* [Synchronous](synchronous-retries) and [asynchronous retries](#asynchronous-retries)
* [Asynchronous API integration](#asynchronous-api-integration)
* [CompletableFuture](#completablefuture-integration) and [Java 8 functional interface](#java-8-functional-interfaces) integration
* [Invocation Tracking](#invocation-tracking)
* [Event Listeners](#event-listeners)

Supports Java 6+ though the documentation uses lambdas for simplicity.

## Usage

#### Retry Policies

Recurrent supports flexible [retry policies][RetryPolicy] that allow you to express when retries should be performed.

A policy can allow retries on particular failures:

```java
RetryPolicy retryPolicy = new RetryPolicy()
  .retryOn(ConnectException.class, SocketException.class);
  .retryOn(failure -> failure instanceof ConnectException);
```

And for particular results or conditions:

```java
retryPolicy
  .retryFor(null);
  .retryWhen(result -> result == null);
```  

We can add a fixed delay between retries:

```java
retryPolicy.withDelay(1, TimeUnit.SECONDS);
```
Or a delay that backs off exponentially:

```java
retryPolicy.withBackoff(1, 30, TimeUnit.SECONDS);
```

We can add a max number of retries and a max retry duration:

```java
retryPolicy
  .withMaxRetries(100)
  .withMaxDuration(5, TimeUnit.MINUTES);
```

And of course we can combine these things into a single policy.

#### Synchronous Retries

Once we've defined a retry policy, we can perform a retryable synchronous invocation:

```java
// Run with retries
Recurrent.run(() -> doSomething(), retryPolicy);

// Get with retries
Connection connection = Recurrent.get(() -> connect(), retryPolicy);
```

#### Asynchronous Retries

Asynchronous invocations can be performed and retried on a scheduled executor and return a [RecurrentFuture]. When the invocation succeeds or the retry policy is exceeded, the future is completed and any listeners registered against it are called:

```java
Recurrent.get(() -> connect(), retryPolicy, executor)
  .whenSuccess(connection -> log.info("Connected to {}", connection))
  .whenFailure((result, failure) -> log.error("Connection attempts failed", failure));
```

#### Asynchronous API Integration

Asynchronous code reports completion via indirect callbacks. Recurrent provides [ContextualRunnable] and [ContextualCallable] classes that can be used with a callback to manually perform retries or completion:

```java
Recurrent.get(invocation -> 
  service.connect().whenComplete((result, failure) -> {
	if (invocation.complete(result, failure))
      log.info("Connected");
	else if (!invocation.retry())
      log.error("Connection attempts failed", failure);
  }
), retryPolicy, executor);
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
Recurrent.run(() -> Stream.of("foo").map(value -> value + "bar"), retryPolicy);
```

Individual Stream operations:

```java
Stream.of("foo").map(value -> Recurrent.get(() -> value + "bar", retryPolicy));
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Recurrent.get(() -> "foo", retryPolicy))
  .thenApplyAsync(value -> Recurrent.get(() -> value + "bar", retryPolicy));
```

#### Invocation Tracking

In addition to automatically performing retries, Recurrent can be used to track invocations for you, allowing you to manually retry as needed:

```java
Invocation invocation = new Invocation(retryPolicy);
while (!invocation.isComplete()) {
  try {
	doSomething();
    invocation.complete()
  } catch (ConnectException e) {
    invocation.recordFailure(e);
  }
}
```

Invocation tracking is also useful for integrating with APIs that have their own retry mechanism:

```java
Invocation invocation = new Invocation(retryPolicy);

// On failure
if (invocation.canRetryOn(someFailure))
  service.scheduleRetry(invocation.getWaitMillis(), TimeUnit.MILLISECONDS);
```

See the [RxJava example][RxJava] for a more detailed implementation.

#### Event Listeners

Recurrent supports event listeners that can be notified when retries are performed and when invocations complete:

```java
Recurrent.get(() -> connect(), retryPolicy, new Listeners<Connection>() {
  public void onRetry(Connection cxn, Throwable failure, InvocationStats stats) {
    log.warn("Failure #{}. Retrying.", stats.getAttemptCount());
  }
  
  public void onComplete(Connection cxn, Throwable failure) {
    if (failure != null)
      log.error("Connection attempts failed", failure);
    else
  	  log.info("Connected to {}", cxn);
  }
});
```

Java 8 users can register individual listeners using lambdas:

```java
Recurrent.get(() -> connect(), retryPolicy, new Listeners()
  .whenRetry((c, f, stats) -> log.warn("Failure #{}. Retrying.", stats.getAttemptCount()))
  .whenFailure((cxn, failure) -> log.error("Connection attempts failed", failure)))
  .whenSuccess(cxn -> log.info("Connected to {}", cxn)));
```

Additional listeners are available via the [Listeners] and [AsyncListeners] classes. Asynchronous completion listeners can be registered via [RecurrentFuture].

## Example Integrations

Recurrent was designed to integrate nicely with existing libraries. Here are some example integrations:

* [Java 8](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/Java8Example.java)
* [Netty](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/NettyExample.java)
* [RxJava]
* [Vert.x](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/VertxExample.java)

## Public API Integration

For library developers, Recurrent integrates nicely into public APIs, allowing your users to configure retry policies for different opererations. One integration approach is to subclass the RetryPolicy class, then expose that as part of your API while the rest of Recurrent remains internal. Another approach is to use something like the [Maven shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to relocate Recurrent into your project's package structure as desired.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## License

Copyright 2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

[Listeners]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/Listeners.html
[AsyncListeners]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/AsyncListeners.html
[RetryPolicy]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RetryPolicy.html
[RecurrentFuture]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RecurrentFuture.html
[ContextualRunnable]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/ContextualRunnable.html
[ContextualCallable]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/ContextualCallable.html
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[RxJava]: https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/RxJavaExample.java