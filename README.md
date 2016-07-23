# Failsafe
[![Build Status](https://travis-ci.org/jhalterman/failsafe.svg)](https://travis-ci.org/jhalterman/failsafe)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.jodah/failsafe/badge.svg)][maven] 
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![JavaDoc](http://javadoc-badge.appspot.com/net.jodah/failsafe.svg?label=javadoc)](https://jhalterman.github.com/failsafe/javadoc)

*Simple, sophisticated failure handling.*

## Introduction

Failsafe is a lightweight, zero-dependency library for handling failures. It was designed to be as easy to use as possible, with a concise API for handling everday use cases and the flexibility to handle everything else. Failsafe features:

* [Retries](#retries)
* [Circuit breakers](#circuit-breakers)
* [Fallbacks](#fallbacks)
* [Execution context](#execution-context)
* [Event listeners](#event-listeners)
* [Asynchronous API integration](#asynchronous-api-integration)
* [CompletableFuture](#completablefuture-integration) and [functional interface](#functional-interface-integration) integration
* [Execution tracking](#execution-tracking)

Supports Java 6+ though the documentation uses lambdas for simplicity.

## Setup

Add the latest [Failsafe Maven dependency][maven] to your project.

## Usage

#### Retries

One of the core Failsafe features is retries. To start, define a [RetryPolicy] that expresses when retries should be performed:

```java
RetryPolicy retryPolicy = new RetryPolicy()
  .retryOn(ConnectException.class)
  .withDelay(1, TimeUnit.SECONDS)
  .withMaxRetries(3);
```

Then use your [RetryPolicy] to execute a `Runnable` or `Callable` *with* retries:

```java
// Run with retries
Failsafe.with(retryPolicy).run(() -> connect());

// Get with retries
Connection connection = Failsafe.with(retryPolicy).get(() -> connect());
```

Java 6 and 7 are also supported:

```java
Connection connection = Failsafe.with(retryPolicy).get(new Callable<Connection>() {
  public Connection call() {
    return connect();
  }
});
```

#### Retry Policies

Failsafe's [retry policies][RetryPolicy] provide flexibility in allowing you to express when retries should be performed.

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

It can add a fixed delay between retries:

```java
retryPolicy.withDelay(1, TimeUnit.SECONDS);
```

Or a delay that backs off exponentially:

```java
retryPolicy.withBackoff(1, 30, TimeUnit.SECONDS);
```

It can add a max number of retries and a max retry duration:

```java
retryPolicy
  .withMaxRetries(100)
  .withMaxDuration(5, TimeUnit.MINUTES);
```

It can also specify which results, failures or conditions to abort retries on:

```java
retryPolicy
  .abortWhen(true)
  .abortOn(NoRouteToHostException.class)
  .abortIf(result -> result == true)
```

Retry policies support multiple retry or abort conditions of the same type:

```java
retryPolicy
  .retryWhen(null)
  .retryWhen("");
```

And of course we can combine any of these things into a single policy.

#### Synchronous Retries

With a retry policy defined, we can perform a retryable synchronous execution:

```java
// Run with retries
Failsafe.with(retryPolicy).run(this::connect);

// Get with retries
Connection connection = Failsafe.with(retryPolicy).get(this::connect);
```

#### Asynchronous Retries

Asynchronous executions can be performed and retried on a [ScheduledExecutorService] or custom [Scheduler] implementation, and return a [FailsafeFuture]. When the execution succeeds or the retry policy is exceeded, the future is completed and any registered [listeners](#event-listeners) are called:

```java
Failsafe.with(retryPolicy)
  .with(executor)
  .onSuccess(connection -> log.info("Connected to {}", connection))
  .onFailure(failure -> log.error("Connection attempts failed", failure))
  .get(this::connect);
```

#### Circuit Breakers

[Circuit breakers][FowlerCircuitBreaker] are a way of creating systems that [fail-fast][FailFast] by temporarily disabling execution as a way of preventing system overload. Creating a [CircuitBreaker] is straightforward:

```java
CircuitBreaker breaker = new CircuitBreaker()
  .withFailureThreshold(3, 10)
  .withSuccessThreshold(5)
  .withDelay(1, TimeUnit.MINUTES);
```

We can then execute a `Runnable` or `Callable` *with* the `breaker`:

```java
Failsafe.with(breaker).run(this::connect);
```

When a configured threshold of execution failures occurs on a circuit breaker, the circuit is *opened* and further execution requests fail with `CircuitBreakerOpenException`. After a delay, the circuit is *half-opened* and trial executions are attempted to determine whether the circuit should be *closed* or *opened* again. If the trial executions exceed a success threshold, the breaker is *closed* again and executions will proceed as normal.

#### Circuit Breaker Configuration

Circuit breakers can be flexibly configured to express when the circuit should be opened or closed.

A circuit breaker can be configured to *open* when a successive number of executions have failed:

```java
CircuitBreaker breaker = new CircuitBreaker()
  .withFailureThreshold(5);
```

Or when, for example, the last 3 out of 5 executions have failed:

```java
breaker.withFailureThreshold(3, 5);
```

After opening, a breaker is typically configured to delay before attempting to *close* again:

```java
breaker.withDelay(1, TimeUnit.MINUTES);
```

The breaker can be configured to *close* again if a number of trial executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(5);
```

The breaker can also be configured to *close* again if, for example, the last 3 out of 5 executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(3, 5);
```

The breaker can be configured to only recognize certain results, exceptions or conditions as failures:

```java
breaker.
  .failWhen(true)
  .failOn(NoRouteToHostException.class)
  .failIf((result, failure) -> result == 500 || failure instanceof NoRouteToHostException);
```

And the breaker can be configured to recognize executions that exceed a certain timeout as failures:

```java
breaker.withTimeout(10, TimeUnit.SECONDS);
```

#### With Retries

A CircuitBreaker can be used along with a `RetryPolicy`:

```java
Failsafe.with(retryPolicy).with(breaker).get(this::connect);
```

Execution failures are first retried according to the `RetryPolicy`, then if the policy is exceeded the failure is recorded by the `CircuitBreaker`.

#### Failing Together

A circuit breaker can and should be shared across code that accesses inter-dependent system components that fail together. This ensures that if the circuit is opened, executions against one component that rely on another component will not be allowed until the circuit is closed again.

#### Standalone Usage

A [CircuitBreaker] can also be manually operated in a standalone way:

```java
breaker.open();
breaker.halfOpen();
breaker.close();

if (breaker.allowsExecution()) {
  try {
    doSomething();
    breaker.recordSuccess();
  } catch (Exception e) {
    breaker.recordFailure(e);
  }
}
```

#### Fallbacks

Fallbacks allow you to provide an alternative result for a failed execution. They can be used to suppress exceptions and provide a default result:

```java
Failsafe.with(retryPolicy)
  .withFallback(null)
  .get(this::connect);
```

Throw a custom exception:

```java
Failsafe.with(retryPolicy)
  .withFallback(failure -> { throw new CustomException(failure); })
  .get(this::connect);
```

Or compute an alternative result such as from a backup resource:

```java
Failsafe.with(retryPolicy)
  .withFallback(f -> this::connectToBackup)
  .get(this::connectToPrimary);
```

#### Execution Context

Failsafe can provide an [ExecutionContext] containing execution related information such as the number of execution attempts as well as start and elapsed times:

```java
Failsafe.with(retryPolicy).run(ctx -> {
  log.debug("Connection attempt #{}", ctx.getExecutions());
  connect();
});
```

#### Event Listeners

Failsafe supports a variety of execution and retry event [listeners][ListenerConfig]:

```java
Failsafe.with(retryPolicy)
  .onRetry((c, f, stats) -> log.warn("Failure #{}. Retrying.", stats.getExecutions()))
  .onFailedAttempt(failure -> log.error("Connection attempts failed", failure))
  .onSuccess(cxn -> log.info("Connected to {}", cxn))
  .get(this::connect);
```

[Asynchronous listeners][AsyncListenerConfig] are also supported:

```java
Failsafe.with(retryPolicy)
  .with(executor)
  .onFailureAsync(e -> log.error("Failed to create connection", e))
  .onSuccessAsync(cxn -> log.info("Connected to {}", cxn), anotherExecutor);
```

Java 6 and 7 users can extend the [Listeners] class and override individual event handlers:

```java
Failsafe.with(retryPolicy)
  .with(new Listeners<Connection>() {
    public void onRetry(Connection cxn, Throwable failure, ExecutionStats stats) {
      log.warn("Failure #{}. Retrying.", stats.getExecutions());
    }
  }).get(() -> connect());
```

[CircuitBreaker] related event listeners can also be registered:

```java
circuitBreaker.onOpen(() -> log.info("The circuit was opened"));
```

#### Asynchronous API Integration

Failsafe can be integrated with asynchronous code that reports completion via callbacks. The `runAsync`, `getAsync` and `futureAsync` methods provide an [AsyncExecution] reference that can be used to manually schedule retries or complete the execution from inside asynchronous callbacks:

```java
Failsafe.with(retryPolicy)
  .with(executor)
  .getAsync(execution -> service.connect().whenComplete((result, failure) -> {
    if (execution.complete(result, failure))
      log.info("Connected");
    else if (!execution.retry())
      log.error("Connection attempts failed", failure);
  }));
```

Failsafe can also perform asynchronous executions and retries on 3rd party schedulers via the [Scheduler] interface. See the [Vert.x example][Vert.x] for a more detailed implementation.

#### CompletableFuture Integration

Java 8 users can use Failsafe to retry [CompletableFuture] calls:

```java
Failsafe.with(retryPolicy)
  .with(executor)
  .future(this::connectAsync)
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println));
```

#### Functional Interface Integration

Failsafe can be used to create retryable Java 8 functional interfaces:

```java
Function<String, Connection> connect = address -> Failsafe.with(retryPolicy).get(() -> connect(address));
```

We can retry streams:

```java
Failsafe.with(retryPolicy).run(() -> Stream.of("foo").map(value -> value + "bar"));
```

Individual Stream operations:

```java
Stream.of("foo").map(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"));
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Failsafe.with(retryPolicy).get(() -> "foo"))
  .thenApplyAsync(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"));
```

#### Execution Tracking

In addition to automatically performing retries, Failsafe can be used to track executions for you, allowing you to manually retry as needed:

```java
Execution execution = new Execution(retryPolicy);
while (!execution.isComplete()) {
  try {
	doSomething();
    execution.complete();
  } catch (ConnectException e) {
    execution.recordFailure(e);
  }
}
```

Execution tracking is also useful for integrating with APIs that have their own retry mechanism:

```java
Execution execution = new Execution(retryPolicy);

// On failure
if (execution.canRetryOn(someFailure))
  service.scheduleRetry(execution.getWaitMillis(), TimeUnit.MILLISECONDS);
```

See the [RxJava example][RxJava] for a more detailed implementation.

## Example Integrations

Failsafe was designed to integrate nicely with existing libraries. Here are some example integrations:

* [Java 8](https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/Java8Example.java)
* [Netty](https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/NettyExample.java)
* [RxJava]
* [Vert.x]

## Public API Integration

For library developers, Failsafe integrates nicely into public APIs, allowing your users to configure retry policies for different operations. One integration approach is to subclass the RetryPolicy class, then expose that as part of your API while the rest of Failsafe remains internal. Another approach is to use something like the [Maven shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to relocate Failsafe into your project's package structure as desired.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/failsafe/javadoc).

## Contribute

Failsafe is a volunteer effort. If you use it and you like it, you can help by spreading the word!

## License

Copyright 2015-2016 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

[Listeners]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Listeners.html
[ListenerConfig]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/ListenerConfig.html
[AsyncListenerConfig]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/AsyncListenerConfig.html
[RetryPolicy]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html
[FailsafeFuture]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailsafeFuture.html
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[RxJava]: https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/RxJavaExample.java
[Vert.x]: https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/VertxExample.java
[ExecutionContext]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/ExecutionContext.html
[Execution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Execution
[AsyncExecution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/AsyncExecution
[ScheduledExecutorService]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
[Scheduler]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/util/concurrent/Scheduler.html
[CircuitBreaker]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html
[FowlerCircuitBreaker]: http://martinfowler.com/bliki/CircuitBreaker.html
[FailFast]: https://en.wikipedia.org/wiki/Fail-fast
[maven]: https://maven-badges.herokuapp.com/maven-central/net.jodah/failsafe
