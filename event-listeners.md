---
layout: default
title: Event Listeners
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
  .onFailure(e -> log.error("Failed to create connection", e.getFailure()));
```

Additional listeners are available for [retry policies][retry-listeners] and [circuit breakers][circuit-breaker-listeners].

{% include common-links.html %}