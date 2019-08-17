---
layout: default
title: Retry Policy
parent: Policies
nav_order: 1
---

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

{% include common-links.html %}