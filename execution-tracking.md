---
layout: default
parent: Features
title: Execution Tracking
nav_order: 9
---

# Execution Tracking

In addition to automatically performing retries, Failsafe can be used to track executions for you, allowing you to manually retry as needed:

```java
Execution execution = new Execution(retryPolicy);
while (!execution.isComplete()) {
  try {
    doSomething();
    execution.complete();
  } catch (ConnectException e) {
    execution.recordFailure(e);
  }
}
```

Execution tracking is also useful for integrating with APIs that have their own retry mechanism:

```java
Execution execution = new Execution(retryPolicy);

// On failure
if (execution.canRetryOn(someFailure))
  service.scheduleRetry(execution.getWaitTime().toNanos(), TimeUnit.MILLISECONDS);
```

See the [RxJava example][RxJava] for a more detailed implementation.

{% include common-links.html %}