---
layout: default
title: Frequently Asked Questions
---

# Frequently Asked Questions
{: .no_toc }

1. TOC
{:toc}

## Does Failsafe create threads?

Failsafe requires the use of separate threads for two different purposes:

- Immediate async executions and async `Fallback` calls
- Scheduled async retries and any `Timeout` check


For async executions, you can supply your own executor via `FailsafeExecutor.with()`. If no executor is configured, Failsafe will use the `ForkJoinPool.commonPool` for performing async executions.

For scheduling retries and performing `Timeout` checks, Failsafe requires a `ScheduledExecutorService` or `Scheduler` be configured via `FailsafeExecutor.with()`. If none is provided, Failsafe with create and use a single threaded `ScheduledExecutorService` internally. This scheduler delegates executions to the configured executor or `commonPool`, will only be created if needed, and is shared across all Failsafe instances that don't configure a scheduler.

If no executor or scheduler is configured and the `ForkJoinPool.commonPool` has a parallelism of 1 (which occurs when the number of processors is <= 2), Failsafe will create and use an internal `ForkJoinPool` with a parallelism of 2 instead. This is necessary to support concurrent execution and `Timeout` checks. As with the internal `ScheduledExecutorService`, this  `ForkJoinPool` will only be created if needed, and will be shared across all Failsafe instances that don't configure an executor.

## Why is TimeoutExceededException not being handled?

It's common to use a `RetryPolicy` or `Fallback` together with a `Timeout`:

```java
Failsafe.with(fallback, retryPolicy, timeout).get(this::connect);
```

When you do, you may want to make sure that the `RetryPolicy` or `Fallback` are configured to handle `TimeoutExceededException`.

By default, a policy will handle all `Exception` types. But if you configure specific [result or failure handlers][FailurePolicy], then it may not recognize a `TimeoutExceededException` as a failure and may not handle it. Ex:

```java
// Only handles ConnectException, not TimeoutExceededException
retryPolicy.handle(ConnectException.class);
```

If you have specific failure handling configuration and also want to handle `TimeoutExceededException`, be sure to configure it.

## Why is CircuitBreakerOpenException not being handled?

As with `TimeoutExceededException` described above, if you configure specific [result or failure handlers][FailurePolicy] you may need to ensure that `CircuitBreakerOpenException` is configured to be handled.

[FailurePolicy]: https://jodah.net/failsafe/javadoc/net/jodah/failsafe/FailurePolicy.html
{% include common-links.html %}