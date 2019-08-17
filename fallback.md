---
layout: default
title: Fallback
parent: Policies
nav_order: 3
---

## Fallbacks

[Fallbacks][Fallback] allow you to provide an alternative result for a failed execution. They can also be used to suppress exceptions and provide a default result:

```java
Fallback<Object> fallback = Fallback.of(null);
```

Throw a custom exception:

```java
Fallback<Object> fallback = Fallback.ofException(e -> new CustomException(e.getLastFailure()));
```

Or compute an alternative result such as from a backup resource:

```java
Fallback<Object> fallback = Fallback.of(this::connectToBackup);
```

For computations that block, a Fallback can be configured to run asynchronously:

```java
Fallback<Object> fallback = Fallback.ofAsync(this::blockingCall);
```


{% include common-links.html %}