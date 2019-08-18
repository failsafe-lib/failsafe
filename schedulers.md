---
layout: default
title: Schedulers
---

# Schedulers

By default, Failsafe uses the [ForkJoinPool]'s [common pool][common-pool] to perform async executions, but you can also configure a specific [ScheduledExecutorService], custom [Scheduler], or [ExecutorService] to use:

```java
Failsafe.with(policy).with(scheduler).getAsync(this::connect);
```

{% include common-links.html %}