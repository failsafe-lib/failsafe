# Failsafe

[![Build Status](https://travis-ci.org/jhalterman/failsafe.svg)](https://travis-ci.org/jhalterman/failsafe)
[![Maven Central](https://img.shields.io/maven-central/v/net.jodah/failsafe.svg?maxAge=60&colorB=53C92E)][maven] 
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![JavaDoc](https://img.shields.io/maven-central/v/net.jodah/failsafe.svg?maxAge=60&label=javadoc)](https://jhalterman.github.com/failsafe/javadoc)
[![Join the chat at https://gitter.im/jhalterman/failsafe](https://badges.gitter.im/jhalterman/failsafe.svg)][gitter]

## Introduction

Failsafe is a lightweight, zero-dependency library for handling failures in Java 8+, with a concise API for handling everyday use cases and the flexibility to handle everything else. It  works by wrapping executable logic with one or more resilience [policies], which can be combined and [composed](#policy-composition) as needed. These policies include:

* [Retries](#retries)
* [Timeouts](#timeouts)
* [Fallbacks](#fallbacks)
* [Circuit breakers](#circuit-breakers) 

It also provides features that allow you to integrate with various scenarios, including:

* [Configurable schedulers](#configurable-schedulers)
* [Event listeners](#event-listeners)
* [Strong typing](#strong-typing)
* [Execution context](#execution-context) and [cancellation](#execution-cancellation)
* [Asynchronous API integration](#asynchronous-api-integration)
* [CompletionStage](#completionstage-integration) and [functional interface](#functional-interface-integration) integration
* [Execution tracking](#execution-tracking)
* [Policy SPI](#policy-spi)

## Setup

Add the latest [Failsafe Maven dependency][maven] to your project.

## Migrating from 1.x

Failsafe 2.0 has API and behavior changes from 1.x. See the [CHANGES](CHANGES.md#20) doc for more details.

## Usage

#### Getting Started

To start, we'll create a [RetryPolicy] that defines which failures should be handled and when retries should be performed:

```java
RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
  .handle(ConnectException.class)
  .withDelay(Duration.ofSeconds(1))
  .withMaxRetries(3);
```

We can then execute a `Runnable` or `Supplier` *with* retries:

```java
// Run with retries
Failsafe.with(retryPolicy).run(() -> connect());

// Get with retries
Connection connection = Failsafe.with(retryPolicy).get(() -> connect());
```

We can also execute a `Runnable` or `Supplier` asynchronously *with* retries:

```java
// Run with retries asynchronously
CompletableFuture<Void> future = Failsafe.with(retryPolicy).runAsync(() -> connect());

// Get with retries asynchronously
CompletableFuture<Connection> future = Failsafe.with(retryPolicy).getAsync(() -> connect());
```

#### Composing Policies

Multiple [policies] can be arbitrarily composed to add additional layers of resilience or to handle different failures in different ways:

```java
CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>();
Fallback<Object> fallback = Fallback.of(this::connectToBackup);

Failsafe.with(fallback, retryPolicy, circuitBreaker).get(this::connect);
```

Order does matter when composing policies. See the [section below](#policy-composition) for more details.

#### Failsafe Executor

Policy compositions can also be saved for later use via a [FailsafeExecutor]:

```java
FailsafeExecutor<Object> executor = Failsafe.with(fallback, retryPolicy, circuitBreaker);
executor.run(this::connect);
```

## Failure Policies

Failsafe uses [policies][FailurePolicy] to handle failures. By default, policies treat any `Exception` as a failure. But policies can also be configured to handle more specific failures or conditions:

```java
policy
  .handle(ConnectException.class, SocketException.class)
  .handleIf(failure -> failure instanceof ConnectException);
```

They can also be configured to handle specific results or result conditions:

```java
policy
  .handleResult(null)
  .handleResultIf(result -> result == null);  
```

## Retries

[Retry policies][RetryPolicy] express when retries should be performed for an execution failure. 

By default, a [RetryPolicy] will perform a maximum of 3 execution attempts. You can configure a max number of [attempts][max-attempts] or [retries][max-retries]:

```java
retryPolicy.withMaxAttempts(3);
```

And a delay between attempts:

```java
retryPolicy.withDelay(Duration.ofSeconds(1));
```

You can add delay that [backs off][backoff] exponentially:

```java
retryPolicy.withBackoff(1, 30, ChronoUnit.SECONDS);
```

A random delay for some range:

```java
retryPolicy.withDelay(1, 10, ChronoUnit.SECONDS);
```

Or a [computed delay][computed-delay] based on an execution result. You can add a random [jitter factor][jitter-factor] to a delay:

```java
retryPolicy.withJitter(.1);
```

Or a [time based jitter][jitter-duration]:

```java
retryPolicy.withJitter(Duration.ofMillis(100));
```

You can add a [max retry duration][max-duration]:

```java
retryPolicy.withMaxDuration(Duration.ofMinutes(5));
```

You can specify which results, failures or conditions to [abort retries][abort-retries] on:

```java
retryPolicy
  .abortWhen(true)
  .abortOn(NoRouteToHostException.class)
  .abortIf(result -> result == true)
```

And of course you can arbitrarily combine any of these things into a single policy.

## Timeouts

[Timeouts][Timeout] allow you to fail an execution with `TimeoutExceededException` if it takes too long to complete:

```java
Timeout<Object> timeout = Timeout.of(Duration.ofSeconds(10));
```

You can also cancel an execution and perform an optional [interrupt] if it times out:

```
timeout.withCancel(shouldInterrupt);
```

If a cancellation is triggered by a `Timeout`, the execution is still completed with `TimeoutExceededException`. See the [execution cancellation](#execution-cancellation) section for more on cancellation.

## Fallbacks

[Fallbacks][Fallback] allow you to provide an alternative result for a failed execution. They can also be used to suppress exceptions and provide a default result:

```java
Fallback<Object> fallback = Fallback.of(null);
```

Throw a custom exception:

```java
Fallback<Object> fallback = Fallback.ofException(e -> new CustomException(e.getLastFailure()));
```

Or compute an alternative result such as from a backup resource:

```java
Fallback<Object> fallback = Fallback.of(this::connectToBackup);
```

For computations that block, a Fallback can be configured to run asynchronously:

```java
Fallback<Object> fallback = Fallback.ofAsync(this::blockingCall);
```

## Circuit Breakers

[Circuit breakers][fowler-circuit-breaker] allow you to create systems that [fail-fast] by temporarily disabling execution as a way of preventing system overload. Creating a [CircuitBreaker] is straightforward:

```java
CircuitBreaker<Object> breaker = new CircuitBreaker<>()
  .handle(ConnectException.class)
  .withFailureThreshold(3, 10)
  .withSuccessThreshold(5)
  .withDelay(Duration.ofMinutes(1));
```

When the number of execution failures exceed a configured threshold, the circuit is *opened* and further execution requests fail with `CircuitBreakerOpenException`. After a delay, the circuit is *half-opened* and trial executions are allowed which determine whether the circuit should be *closed* or *opened* again. If the trial executions meet a success threshold, the breaker is *closed* again and executions will proceed as normal.

#### Circuit Breaker Configuration

Circuit breakers can be flexibly configured to express when the circuit should be opened or closed.

A circuit breaker can be configured to *open* when a successive number of executions have failed:

```java
breaker.withFailureThreshold(5);
```

Or when, for example, the last 3 out of 5 executions have failed:

```java
breaker.withFailureThreshold(3, 5);
```

After opening, a breaker will delay for 1 minute by default before before attempting to *close* again, or you can configure a specific delay:

```java
breaker.withDelay(Duration.ofSeconds(30));
```

Or a [computed delay][computed-delay] based on an execution result. 

The breaker can be configured to *close* again if a number of trial executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(5);
```

The breaker can also be configured to *close* again if, for example, the last 3 out of 5 executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(3, 5);
```

#### Circuit Breaker Metrics

[CircuitBreaker] can provide metrics regarding the number of recorded [successes][breaker-success-count] or [failures][breaker-failure-count] in the current state.

#### Best Practices

A circuit breaker can and *should* be shared across code that accesses common dependencies. This ensures that if the circuit breaker is opened, all executions that share the same dependency and use the same circuit breaker will be blocked until the circuit is closed again. For example, if multiple connections or requests are made to the same external server, typically they should all go through the same circuit breaker.

#### Standalone Usage

A [CircuitBreaker] can also be manually operated in a standalone way:

```java
breaker.open();
breaker.halfOpen();
breaker.close();

if (breaker.allowsExecution()) {
  try {
    breaker.preExecute();
    doSomething();
    breaker.recordSuccess();
  } catch (Exception e) {
    breaker.recordFailure(e);
  }
}
```

## Policy Composition

Policies can be composed in any way desired, including multiple policies of the same type. Policies handle execution results in reverse order, similar to the way that function composition works. For example, consider:

```java
Failsafe.with(fallback, retryPolicy, circuitBreaker, timeout).get(supplier);
```

This results in the following internal composition when executing the `supplier` and handling its result:

```
Fallback(RetryPolicy(CircuitBreaker(Timeout(Supplier))))
```

This means the `Timeout` is first to evaluate the `Supplier`'s result, then the `CircuitBreaker`, the `RetryPolicy`, and the `Fallback`. Each policy makes its own determination as to whether the result represents a failure. This allows different policies to be used for handling different types of failures.

#### Typical Composition

A Failsafe configuration that uses multiple policies might place a `Fallback` as the outer-most policy, followed by a `RetryPolicy`, `CircuitBreaker`, and a `Timeout` as the inner-most policy:

```java
Failsafe.with(fallback, retryPolicy, circuitBreaker, timeout)
```

That said, it really depends on how the policies are being used, and different compositions make sense for different use cases.

## Additional Features

#### Configurable Schedulers

By default, Failsafe uses the [ForkJoinPool]'s [common pool][common-pool] to perform async executions, but you can also configure a specific [ScheduledExecutorService], custom [Scheduler], or [ExecutorService] to use:

```java
Failsafe.with(policy).with(scheduler).getAsync(this::connect);
```

#### Event Listeners

Failsafe supports event listeners, both in the top level [Failsafe][FailsafeExecutor] API, and in the different [Policy][FailurePolicy] implementations.

At the top level, it can notify you when an execution completes for all policies:

```java
Failsafe.with(retryPolicy, circuitBreaker)
  .onComplete(e -> {
    if (e.getResult() != null)
      log.info("Connected to {}", e.getResult());
    else if (e.getFailure() != null)
      log.error("Failed to create connection", e.getFailure());
  })
  .get(this::connect);
```

It can notify you when an execution completes *successfully* for all policies:

```java
Failsafe.with(retryPolicy, circuitBreaker)
  .onSuccess(e -> log.info("Connected to {}", e.getResult()))
  .get(this::connect);
```

Or when an execution fails for *any* policy:

```java
Failsafe.with(retryPolicy, circuitBreaker)
  .onFailure(e -> log.error("Failed to create connection", e.getFailure()))
  .get(this::connect);
```

At the policy level, it can notify you when an execution succeeds or fails for a particular policy:

```java
policy
  .onSuccess(e -> log.info("Connected to {}", e.getResult()))
  .onFailure(e -> log.error("Failed to create connection", e.getFailure()))
  .get(this::connect);
```

When an execution attempt fails and before a retry is performed for a [RetryPolicy]:

```java
retryPolicy
  .onFailedAttempt(e -> log.error("Connection attempt failed", e.getLastFailure()))
  .onRetry(e -> log.warn("Failure #{}. Retrying.", ctx.getAttemptCount()));
```

Or when an execution fails and the max retries are [exceeded][retries-exceeded] for a [RetryPolicy]:

```java
retryPolicy.onRetriesExceeded(e -> log.warn("Failed to connect. Max retries exceeded."));
```

For [CircuitBreakers][CircuitBreaker], Failsafe can notify you when the state changes:

```java
circuitBreaker
  .onClose(() -> log.info("The circuit breaker was closed"));
  .onOpen(() -> log.info("The circuit breaker was opened"))
  .onHalfOpen(() -> log.info("The circuit breaker was half-opened"));
```

#### Strong typing

Failsafe Policies are typed based on the expected result. For generic policies that are used for various executions, the result type may just be `Object`:

```java
RetryPolicy<Object> retryPolicy = new RetryPolicy<>();
```

But for other policies we may declare a more specific result type:

```java
RetryPolicy<HttpResponse> retryPolicy = new RetryPolicy<HttpResponse>()
  .handleResultIf(response -> response.getStatusCode == 500)
  .onFailedAttempt(e -> log.warn("Failed attempt: {}", e.getLastResult().getStatusCode()));
```

This allows Failsafe to ensure that the same result type used for the policy is returned by the execution:

```java
HttpResponse response = Failsafe.with(retryPolicy)
  .onSuccess(e -> log.info("Success: {}", e.getResult().getStatusCode()))  
  .get(this::sendHttpRequest);
```

#### Execution Context

Failsafe can provide an [ExecutionContext] containing execution related information such as the number of execution attempts, start and elapsed times, and the last result or failure:

```java
Failsafe.with(retryPolicy).run(ctx -> {
  log.debug("Connection attempt #{}", ctx.getAttemptCount());
  connect();
});
```

This is useful for retrying executions that depend on results from a previous attempt:

```java
int result = Failsafe.with(retryPolicy).get(ctx -> ctx.getLastResult(0) + 1);
```

#### Execution Cancellation

Failsafe supports cancellation and optional interruption of executions. Cancellation and interruption can be triggered by a [Timeout](#timeouts) or through an async execution result:

```java
Future<Connection> future = Failsafe.with(retryPolicy).getAsync(this::connect);
future.cancel(shouldInterrupt);
```

Executions can cooperate with a *non-interrupting* cancellation by checking `ExecutionContext.isCancelled()`:

```java
Failsafe.with(timeout).getAsync(ctx -> {
  while (!ctx.isCancelled())
    doWork();
});
```

Alternatively, an *interrupting* cancellation can be used to forcefully [interrupt] an execution thread. Executions can cooperate with interruption by periodically checking `ExecutionContext.isCancelled` or `Thread.currentThread().isInterrupted()`.

#### Asynchronous API Integration

Failsafe can be integrated with asynchronous code that reports completion via callbacks. The [runAsyncExecution], [getAsyncExecution] and [getStageAsyncExecution] methods provide an [AsyncExecution] reference that can be used to manually schedule retries or complete the execution from inside asynchronous callbacks:

```java
Failsafe.with(retryPolicy)
  .getAsyncExecution(execution -> service.connect().whenComplete((result, failure) -> {
    if (execution.complete(result, failure))
      log.info("Connected");
    else if (!execution.retry())
      log.error("Connection attempts failed", failure);
  }));
```

Failsafe can also perform asynchronous executions and retries on 3rd party schedulers via the [Scheduler] interface. See the [Vert.x example][Vert.x] for a more detailed implementation.

#### CompletionStage Integration

Failsafe can accept a [CompletionStage] and return a new [CompletableFuture] with failure handling built-in:

```java
Failsafe.with(retryPolicy)
  .getStageAsync(this::connectAsync)
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println));
```

#### Functional Interface Integration

Failsafe can be used to create resilient functional interfaces:

```java
Function<String, Connection> connect = address -> Failsafe.with(retryPolicy).get(() -> connect(address));
```

We can wrap Stream operations:

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
  service.scheduleRetry(execution.getWaitTime().toNanos(), TimeUnit.MILLISECONDS);
```

See the [RxJava example][RxJava] for a more detailed implementation.

#### Policy SPI

Failsafe provides an SPI that allows you to implement your own [Policy] and plug it into Failsafe. Each [Policy] implementation must return a [PolicyExecutor] which is responsible for performing synchronous or asynchronous execution, handling pre-execution requests, or handling post-execution results. The existing [PolicyExecutor] [implementations][policy-executor-impls] are a good reference for creating additional implementations.

## Additional Resources

* [Gitter Chat Room][gitter]
* [Javadocs](https://jhalterman.github.com/failsafe/javadoc)
* [FAQs](https://github.com/jhalterman/failsafe/wiki/Frequently-Asked-Questions)
* [Example Integrations](https://github.com/jhalterman/failsafe/wiki/Example-Integrations)
* [3rd Party Tools](https://github.com/jhalterman/failsafe/wiki/3rd-Party-Tools)
* [Comparisons](https://github.com/jhalterman/failsafe/wiki/Comparisons)
* [Who's Using Failsafe][whos-using]

## Library and API Integration

For library and public API developers, Failsafe integrates nicely into existing APIs, allowing your users to configure retry policies for different operations. One integration approach is to subclass the RetryPolicy class and expose that as part of your API while the rest of Failsafe remains internal. Another approach is to use something like the [Maven shade plugin](https://maven.apache.org/plugins/maven-shade-plugin/) to rename and relocate Failsafe classes into your project's package structure as desired.

## Contribute

Failsafe is a volunteer effort. If you use it and you like it, [let us know][whos-using], and also help by spreading the word!

## License

Copyright 2015-2019 Jonathan Halterman and friends. Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).

[policies]: #failure-policies
[backoff]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withBackoff-long-long-java.time.temporal.ChronoUnit-
[computed-delay]: https://jodah.net/failsafe/javadoc/net/jodah/failsafe/DelayablePolicy.html#withDelay-net.jodah.failsafe.function.DelayFunction-
[abort-retries]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#abortOn-java.lang.Class...-
[max-retries]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withMaxRetries-int-
[max-attempts]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withMaxAttempts-int-
[max-duration]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withMaxDuration-java.time.Duration-
[jitter-duration]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withJitter-java.time.Duration-
[jitter-factor]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#withJitter-double-
[breaker-timeout]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#withTimeout-java.time.Duration-
[runAsyncExecution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailsafeExecutor.html#runAsyncExecution-net.jodah.failsafe.function.AsyncRunnable-
[getAsyncExecution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailsafeExecutor.html#getAsyncExecution-net.jodah.failsafe.function.AsyncSupplier-
[getStageAsyncExecution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailsafeExecutor.html#getStageAsyncExecution-net.jodah.failsafe.function.AsyncSupplier-
[retries-exceeded]: https://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html#onRetriesExceeded-net.jodah.failsafe.function.CheckedConsumer-
[breaker-success-count]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getSuccessCount--
[breaker-failure-count]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getFailureCount--
[policy-executor-impls]: https://github.com/jhalterman/failsafe/tree/master/src/main/java/net/jodah/failsafe

[FailsafeExecutor]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailsafeExecutor.html
[Policy]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Policy.html
[FailurePolicy]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailurePolicy.html
[RetryPolicy]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/RetryPolicy.html
[Timeout]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Timeout.html
[Fallback]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Fallback.html
[CircuitBreaker]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html
[PolicyExecutor]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/PolicyExecutor.html
[ExecutionContext]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/ExecutionContext.html
[Execution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/Execution
[AsyncExecution]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/AsyncExecution
[Scheduler]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/util/concurrent/Scheduler.html

[ForkJoinPool]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html
[common-pool]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html#commonPool--
[CompletableFuture]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
[CompletionStage]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html 
[ScheduledExecutorService]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
[ExecutorService]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html
[RxJava]: https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/RxJavaExample.java
[Vert.x]: https://github.com/jhalterman/failsafe/blob/master/src/test/java/net/jodah/failsafe/examples/VertxExample.java
[interrupt]: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#interrupt--

[fail-fast]: https://en.wikipedia.org/wiki/Fail-fast
[fowler-circuit-breaker]: http://martinfowler.com/bliki/CircuitBreaker.html
[maven]: https://maven-badges.herokuapp.com/maven-central/net.jodah/failsafe

[whos-using]: https://github.com/jhalterman/failsafe/wiki/Who's-Using-Failsafe
[gitter]: https://gitter.im/jhalterman/failsafe
