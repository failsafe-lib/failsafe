---
layout: default
title: Timeout
---

# Timeout

[Timeouts][Timeout] allow you to fail an execution with `TimeoutExceededException` if it takes too long to complete:

```java
Timeout<Object> timeout = Timeout.of(Duration.ofSeconds(10));
```

You can also cancel an execution and perform an optional [interrupt] if it times out:

```
timeout.withCancel(shouldInterrupt);
```

If a cancellation is triggered by a `Timeout`, the execution is still completed with `TimeoutExceededException`. See the [execution cancellation][execution-cancellation] section for more on cancellation.

{% include common-links.html %}