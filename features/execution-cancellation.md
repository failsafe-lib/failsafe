---
layout: default
parent: Features
title: Execution Cancellation
nav_order: 5
---

# Execution Cancellation

Failsafe supports cancellation and optional interruption of executions. Cancellation and interruption can be triggered by a [Timeout][timeouts] or through an async execution result:

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

Alternatively, an *interrupting* cancellation can be used to forcefully [interrupt] an execution thread. Executions can cooperate with interruption by periodically checking `ExecutionContext.isCancelled()` or `Thread.isInterrupted()`.

{% include common-links.html %}