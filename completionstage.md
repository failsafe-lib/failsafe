---
layout: default
title: CompletionStage Support
---

# CompletionStage Support

Failsafe can accept a [CompletionStage] and return a new [CompletableFuture] with failure handling built-in:

```java
Failsafe.with(retryPolicy)
  .getStageAsync(this::connectAsync)
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println));
```

{% include common-links.html %}