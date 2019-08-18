---
layout: default
title: Circuit Breaker
---

# Circuit Breaker

[Circuit breakers][fowler-circuit-breaker] allow you to create systems that fail fast by temporarily disabling execution as a way of preventing system overload. Creating a [CircuitBreaker] is straightforward:

```java
CircuitBreaker<Object> breaker = new CircuitBreaker<>()
  .handle(ConnectException.class)
  .withFailureThreshold(3, 10)
  .withSuccessThreshold(5)
  .withDelay(Duration.ofMinutes(1));
```

## How it Works

When the number of execution failures exceed a configured threshold, the breaker is *opened* and further execution requests fail with `CircuitBreakerOpenException`. After a delay, the breaker is *half-opened* and trial executions are allowed which determine whether the breaker should be *closed* or *opened* again. If the trial executions meet a success threshold, the breaker is *closed* again and executions will proceed as normal, otherwise it's re-*opened*.

## Configuration

[Circuit breakers][CircuitBreaker] can be flexibly configured to express when the breaker should be opened, half-opened, and closed.

A circuit breaker can be configured to *open* when a successive number of executions have failed:

```java
breaker.withFailureThreshold(5);
```

Or when, for example, the last 3 out of 5 executions have failed:

```java
breaker.withFailureThreshold(3, 5);
```

After opening, a breaker will delay for 1 minute by default before before transitioning to *half-open*, or you can configure a specific delay:

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

If a success threshold is not configured, then the failure threshold is used to determine if a breaker should transition from half-open to either closed or open.

## Failure Handling

Like any [FailurePolicy], a [CircuitBreaker] can be configured to handle only [certain results or failures][failure-handling], in combination with any of the configuration described above:

```java
circuitBreaker
  .handle(ConnectException.class)
  .handleResult(null);
```

## Event Listeners

In addition to the standard [policy listeners][policy-listeners], a [CircuitBreaker] can notify you when the state of the breaker changes:

```java
circuitBreaker
  .onOpen(() -> log.info("The circuit breaker was opened"))
  .onClose(() -> log.info("The circuit breaker was closed"))
  .onHalfOpen(() -> log.info("The circuit breaker was half-opened"));
```

## Metrics

[CircuitBreaker] can provide metrics regarding the number of recorded [successes][breaker-success-count] or [failures][breaker-failure-count] in the current state.

## Best Practices

A circuit breaker can and *should* be shared across code that accesses common dependencies. This ensures that if the circuit breaker is opened, all executions that share the same dependency and use the same circuit breaker will be blocked until the circuit is closed again. For example, if multiple connections or requests are made to the same external server, typically they should all go through the same circuit breaker.

## Standalone Usage

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

{% include common-links.html %}