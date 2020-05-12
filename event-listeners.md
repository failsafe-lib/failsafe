---
layout: default
title: Event Listeners
---

# Event Listeners
{: .no_toc }

1. TOC
{:toc}

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

Additional listeners are available for [retry policies][retry-listeners], [fallbacks][fallback-listeners] and [circuit breakers][circuit-breaker-listeners].

## Determining Success

Many event listeners are based on whether an execution result is a success or failure. Each policy makes its own determination about execution success based on the policy's [failure handling configuration][failure-handling]. 

An execution is considered a success for a policy if the supplied result is a success, or if it was a failure but the policy was able to produce a successful result. An execution is considered a failure for a policy if the supplied result is a failure _and_ the policy was unable to produce a successful result through its handling.

{% include common-links.html %}