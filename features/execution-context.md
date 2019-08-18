---
layout: default
parent: Features
title: Execution Context
nav_order: 4
---

# Execution Context

Failsafe can provide an [ExecutionContext] containing execution related information such as the number of execution attempts, start and elapsed times, and the last result or failure:

```java
Failsafe.with(retryPolicy).run(ctx -> {
  log.debug("Connection attempt #{}", ctx.getAttemptCount());
  connect();
});
```

This is useful for retrying executions that depend on results from a previous attempt:

```java
int result = Failsafe.with(retryPolicy).get(ctx -> ctx.getLastResult(0) + 1);
```

{% include common-links.html %}