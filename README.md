# Recurrent
[![Build Status](https://travis-ci.org/jhalterman/recurrent.svg)](https://travis-ci.org/jhalterman/recurrent)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.jodah/recurrent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.jodah/recurrent) 
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![JavaDoc](http://javadoc-badge.appspot.com/net.jodah/recurrent.svg?label=javadoc)](https://jhalterman.github.com/recurrent/javadoc)

*Simple, sophisticated retries.*

## Introduction

Recurrent is a simple, zero-dependency library for performing retries. It features:

* [Flexible retry policies](#retry-policies)
* [Synchronous](synchronous-retries) and [asynchronous retries](#asynchronous-retries)
* [Invocation statistics](#invocation-statistics)
* [CompletableFuture](#completablefuture-integration) and [Java 8 functional interface](#java-8-functional-interfaces) integration
* [Event listeners](#event-listeners)
* [Asynchronous API integration](#asynchronous-api-integration)
* [Invocation tracking](#invocation-tracking)

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
  .retryWhen(null);
  .retryIf(result -> result == null);  
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

We can also specify which results, failures or conditions to abort retries on:

```java
retryPolicy
  .abortWhen(true)
  .abortOn(NoRouteToHostException.class)
  .abortIf(result -> result == true)
```

And of course we can combine these things into a single policy.

#### Synchronous Retries

Once we've defined a retry policy, we can perform a retryable synchronous invocation:

```java
// Run with retries
Recurrent.with(retryPolicy).run(() -> doSomething());

// Get with retries
Connection connection = Recurrent.with(retryPolicy).get(() -> connect());
```

#### Asynchronous Retries

Asynchronous invocations are performed and retried on a [ScheduledExecutorService] and they return a [RecurrentFuture]. When the invocation succeeds or the retry policy is exceeded, the future is completed and any listeners registered against it are called:

```java
Recurrent.with(retryPolicy, executor)
  .get(() -> connect())
  .whenSuccess(connection -> log.info("Connected to {}", connection))
  .whenFailure((result, failure) -> log.error("Connection attempts failed", failure));
```

#### Invocation Statistics

Recurrent exposes [InvocationStats] that provide the number of invocation attempts as well as start and elapsed times:

```java
Recurrent.with(retryPolicy).get(stats -> {
  log.debug("Connection attempt #{}", stats.getAttemptCount());
  return connect();
});
```

#### CompletableFuture Integration

Java 8 users can use Recurrent to retry [CompletableFuture] calls:

```java
Recurrent.with(retryPolicy, executor)
  .future(() -> CompletableFuture.supplyAsync(() -> "foo")
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println));
```

#### Java 8 Functional Interfaces

Recurrent can be used to create retryable Java 8 functional interfaces:

```java
Function<String, Connection> connect = address -> Recurrent.with(retryPolicy).get(() -> connect(address));
```

We can retry streams:

```java
Recurrent.with(retryPolicy).run(() -> Stream.of("foo").map(value -> value + "bar"));
```

Individual Stream operations:

```java
Stream.of("foo").map(value -> Recurrent.with(retryPolicy).get(() -> value + "bar"));
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Recurrent.with(retryPolicy).get(() -> "foo"))
  .thenApplyAsync(value -> Recurrent.with(retryPolicy).get(() -> value + "bar"));
```

#### Event Listeners

Recurrent supports [event listeners][listeners] that can be notified of various events such as when retries are performed and when invocations complete:

```java
Recurrent.with(retryPolicy)
  .with(new Listeners<Connection>()
    .whenRetry((c, f, stats) -> log.warn("Failure #{}. Retrying.", stats.getAttemptCount()))
    .whenFailure((cxn, failure) -> log.error("Connection attempts failed", failure))
    .whenSuccess(cxn -> log.info("Connected to {}", cxn)))
  .get(() -> connect());
```

You can also implement listeners by extending the `Listeners` class and overriding individual event handlers:

```java
Recurrent.with(retryPolicy)
  .with(new Listeners<Connection>() {
    public void onRetry(Connection cxn, Throwable failure, InvocationStats stats) {
      log.warn("Failure #{}. Retrying.", stats.getAttemptCount());
    }
  
    public void onComplete(Connection cxn, Throwable failure) {
      if (failure != null)
        log.error("Connection attempts failed", failure);
      else
        log.info("Connected to {}", cxn);
    }
  }).get(() -> connect());
```

For asynchronous Recurrent invocations, [AsyncListeners] can also be used to receive asynchronous callbacks for failed attempt and retry events. Asynchronous completion and failure listeners can be registered via [RecurrentFuture].

#### Asynchronous API Integration

Recurrent can be integrated with asynchronous code that reports completion via callbacks. The `runAsync`, `getAsync` and `futureAsync` methods provide an [AsyncInvocation] reference that can be used to manually perform retries or completion inside asynchronous callbacks:

```java
Recurrent.with(retryPolicy, executor)
  .getAsync(invocation -> service.connect().whenComplete((result, failure) -> {
    if (invocation.complete(result, failure))
      log.info("Connected");
    else if (!invocation.retry())
      log.error("Connection attempts failed", failure);
  }));
```

Recurrent can also perform asynchronous invocations and retries on 3rd party schedulers via the [Scheduler] interface. See the [Vert.x example][Vert.x] for a more detailed implementation.

#### Invocation Tracking

In addition to automatically performing retries, Recurrent can be used to track invocations for you, allowing you to manually retry as needed:

```java
Invocation invocation = new Invocation(retryPolicy);
while (!invocation.isComplete()) {
  try {
	doSomething();
    invocation.complete();
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

## Example Integrations

Recurrent was designed to integrate nicely with existing libraries. Here are some example integrations:

* [Java 8](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/Java8Example.java)
* [Netty](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/NettyExample.java)
* [RxJava]
* [Vert.x]


## Public API Integration

For library developers, Recurrent integrates nicely into public APIs, allowing your users to configure retry policies for different opererations. One integration approach is to subclass the RetryPolicy class, then expose that as part of your API while the rest of Recurrent remains internal. Another approach is to use something like the [Maven shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to relocate Recurrent into your project's package structure as desired.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## Contribute

Recurrent is a volunteer effort. If you use it and you like it, you can help by spreading the word!

## License

Copyright 2015-2016 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

[Listeners]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/Listeners.html
[AsyncListeners]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/AsyncListeners.html
[RetryPolicy]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RetryPolicy.html
[RecurrentFuture]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/RecurrentFuture.html
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[RxJava]: https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/RxJavaExample.java
[Vert.x]: https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/VertxExample.java
[InvocationStats]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/InvocationStats.html
[Invocation]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/Invocation.html
[AsyncInvocation]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/AsyncInvocation.html
[ScheduledExecutorService]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
[Scheduler]: http://jodah.net/recurrent/javadoc/net/jodah/recurrent/util/concurrent/Scheduler.html