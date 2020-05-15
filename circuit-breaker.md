---
layout: default
title: Circuit Breaker
---

# Circuit Breaker
{: .no_toc }

1. TOC
{:toc}

[Circuit breakers][fowler-circuit-breaker] allow you to create systems that fail fast by temporarily disabling execution as a way of preventing system overload. There are two types of circuit breakers: *count based* and *time based*. *Count based* circuit breakers operate by tracking recent execution results up to a certain limit. *Time based* circuit breakers operate by tracking any number of execution results that occur within a time period.

Creating a [CircuitBreaker] is straightforward:

```java
CircuitBreaker<Object> breaker = new CircuitBreaker<>()
  .handle(ConnectException.class)
  .withFailureThreshold(3, 10)
  .withSuccessThreshold(5)
  .withDelay(Duration.ofMinutes(1));
```

## How it Works

When the number of recent execution failures exceed a configured threshold, the breaker is *opened* and further execution requests fail with `CircuitBreakerOpenException`. After a delay, the breaker is *half-opened* and trial executions are allowed which determine whether the breaker should be *closed* or *opened* again. If the trial executions meet a success threshold, the breaker is *closed* again and executions will proceed as normal, otherwise it's re-*opened*.

## Configuration

[Circuit breakers][CircuitBreaker] can be flexibly configured to express when the breaker should be opened, half-opened, and closed.

### Opening

A *count based* circuit breaker can be configured to *open* when a successive number of executions have failed:

```java
breaker.withFailureThreshold(5);
```

Or when, for example, 3 out of the last 5 executions have failed:

```java
breaker.withFailureThreshold(3, 5);
```

A *time based* circuit breaker can be configured to *open* when a number of failures occur within a time period:

```java
breaker.withFailureThreshold(3, Duration.ofMinutes(1));
```

Or when a number of failures occur out of a minimum number of executions within a time period:

```java
breaker.withFailureThreshold(3, 5, Duration.ofMinutes(1));
```

It can also be configured to *open* when the percentage rate of failures out of a minimum number of executions exceeds a threshold:

```java
breaker.withFailureRateThreshold(20, 5, Duration.ofMinutes(1));
```

### Half-Opening

After opening, a breaker will delay for 1 minute by default before before transitioning to *half-open*. You can configure a different delay:

```java
breaker.withDelay(Duration.ofSeconds(30));
```

Or a [computed delay][computed-delay] based on an execution result.

### Closing

The breaker can be configured to *close* again if a number of trial executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(5);
```

The breaker can also be configured to *close* again if, for example, 3 out of the last 5 executions succeed, else it will re-*open*:

```java
breaker.withSuccessThreshold(3, 5);
```

If a success threshold is not configured, then the failure threshold is used to determine if a breaker should transition from *half-open* to either *closed* or *open*.

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

[CircuitBreaker] can provide metrics for the current state that the breaker is in, including [execution count][breaker-execution-count], [success count][breaker-success-count], [failure count][breaker-failure-count], [success rate][breaker-success-rate] and [failure rate][breaker-failure-rate]. It can also return the [remaining delay][remaining-delay] when in an *open* state.

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

## Time Based Resolution

*Time based* circuit breakers use a sliding window to aggregate execution results. As time progresses and newer results are recorded, older results are discarded. In order to maintain space and time efficiency, results are grouped into 10 time slices, each representing 1/10th of the configured failure threshold period. When a time slice is no longer within the thresholding period, its results are discarded. This allows the circuit breaker to operate based on recent results without needing to track the time of each individual execution.

## Performance

Failsafe's internal [CircuitBreaker] implementation is space and time efficient, utilizing a single circular data structure to record execution results. Recording an execution and evaluating a threshold is an _O(1)_ operation, regardless of the thresholding capacity.

[remaining-delay]: https://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getRemainingDelay--
[breaker-execution-count]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getExecutionCount--
[breaker-success-count]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getSuccessCount--
[breaker-failure-count]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getFailureCount--
[breaker-success-rate]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getSuccessRate--
[breaker-failure-rate]: http://jodah.net/failsafe/javadoc/net/jodah/failsafe/CircuitBreaker.html#getFailureRate--

{% include common-links.html %}