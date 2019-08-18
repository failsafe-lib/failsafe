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

When the number of execution failures exceed a configured threshold, the breaker is *opened* and further execution requests fail with `CircuitBreakerOpenException`. After a delay, the breaker is *half-opened* and trial executions are allowed which determine whether the breaker should be *closed* or *opened* again. If the trial executions meet a success threshold, the breaker is *closed* again and executions will proceed as normal.

## Circuit Breaker Configuration

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

## Circuit Breaker Metrics

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