---
layout: default
parent: Features
title: Event Listeners
nav_order: 2
---

# Event Listeners

Failsafe supports event listeners, both at the top level [Failsafe][FailsafeExecutor] API, and in the different [Policy][FailurePolicy] implementations.

## Failsafe Executor Listeners

At the top level, Failsafe can notify you when an execution completes for all policies:

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

## Policy Listeners

At the policy level, Failsafe can notify you when an execution succeeds or fails for a particular policy:

```java
policy
  .onSuccess(e -> log.info("Connected to {}", e.getResult()))
  .onFailure(e -> log.error("Failed to create connection", e.getFailure()))
  .get(this::connect);
```

When an execution attempt fails and before a retry is performed for a [retry policy][retry]:

```java
retryPolicy
  .onFailedAttempt(e -> log.error("Connection attempt failed", e.getLastFailure()))
  .onRetry(e -> log.warn("Failure #{}. Retrying.", ctx.getAttemptCount()));
```

Or when an execution fails and the max retries are [exceeded][retries-exceeded] for a [retry policy][retry]:

```java
retryPolicy.onRetriesExceeded(e -> log.warn("Failed to connect. Max retries exceeded."));
```

For [circuit breakers][circuit-breakers], Failsafe can notify you when the state changes:

```java
circuitBreaker
  .onClose(() -> log.info("The circuit breaker was closed"));
  .onOpen(() -> log.info("The circuit breaker was opened"))
  .onHalfOpen(() -> log.info("The circuit breaker was half-opened"));
```


{% include common-links.html %}