---
layout: default
parent: Features
title: Execution Cancellation
nav_order: 5
---

# Execution Cancellation

Failsafe supports cancellation and optional interruption of executions. Cancellation and interruption can be triggered by a [Timeout][timeouts] or through an async execution's [Future]:

```java
Future<Connection> future = Failsafe.with(retryPolicy).getAsync(this::connect);
future.cancel(shouldInterrupt);
```

Cancellation will cause any async execution retries and timeout attempts to stop. Interruption will cause the execution thread's [interrupt] flag to be set.

## Handling Cancellation

Executions can cooperate with a cancellation by checking `ExecutionContext.isCancelled()`:

```java
Failsafe.with(timeout).getAsync(ctx -> {
  while (!ctx.isCancelled())
    doWork();
});
```

## Handling Interruption

Execution [Interruption][interrupt] will cause certain blocking calls to unblock and may throw [InterruptedException] within your execution.

Non-blocking executions can cooperate with [interruption][interrupt] by periodically checking `Thread.isInterrupted()`:

```java
Failsafe.with(timeout).getAsync(ctx -> {
  while (!Thread.isInterrupted())
    doBlockingWork();
});
```

{% include common-links.html %}