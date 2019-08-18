---
layout: default
title: Async API Support
---

# Asynchronous API Support

Failsafe can be integrated with asynchronous code that reports completion via callbacks. The [runAsyncExecution], [getAsyncExecution] and [getStageAsyncExecution] methods provide an [AsyncExecution] reference that can be used to manually schedule retries or complete the execution from inside asynchronous callbacks:

```java
Failsafe.with(retryPolicy)
  .getAsyncExecution(execution -> service.connect().whenComplete((result, failure) -> {
    if (execution.complete(result, failure))
      log.info("Connected");
    else if (!execution.retry())
      log.error("Connection attempts failed", failure);
  }));
```

Failsafe can also perform asynchronous executions and retries on 3rd party schedulers via the [Scheduler] interface. See the [Vert.x example][Vert.x] for a more detailed implementation.

{% include common-links.html %}