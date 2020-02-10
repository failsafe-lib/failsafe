---
layout: default
title: Fallback
---

# Fallback
{: .no_toc }

1. TOC
{:toc}

[Fallbacks][Fallback] allow you to provide an alternative result for a failed execution. They can also be used to suppress exceptions and provide a default result:

```java
Fallback<Object> fallback = Fallback.of(defaultResult);
```

Throw a custom exception:

```java
Fallback<Object> fallback = Fallback.ofException(e -> new CustomException(e.getLastFailure()));
```

Or compute an alternative result such as from a backup resource:

```java
Fallback<Object> fallback = Fallback.of(this::connectToBackup);
```

A [CompletionStage] can be supplied as a fallback:

```java
Fallback<Object> fallback = Fallback.ofStage(this::connectToBackup);
```

And for computations that block, a Fallback can be configured to run asynchronously:

```java
Fallback<Object> fallback = Fallback.ofAsync(this::blockingCall);
```

## Failure Handling

Like any [FailurePolicy], [Fallbacks] can be configured to handle only [certain results or failures][failure-handling]:

```java
fallback
  .handle(ConnectException.class)
  .handleResult(null);
```

## Event Listeners

[Fallbacks] support the standard [policy listeners][PolicyListeners] which can notify you when a fallback fails:

```java
fallback.onFailure(e -> log.error("Failed to connect to backup", e.getFailure()));
```

Or succeeds:

```java
fallback.onSuccess(e -> log.info("Connected to backup"));
```

{% include common-links.html %}