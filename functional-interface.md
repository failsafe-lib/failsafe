---
layout: default
title: Functional Interface Support
---

# Functional Interface Support

Failsafe can be used to create resilient functional interfaces. 

It can wrap Runnable, Callables, Suppliers, Consumers and Functions:

```java
Function<String, Connection> connect = address -> 
  Failsafe.with(retryPolicy).get(() -> connect(address));
```

Stream operations:

```java
Stream.of("foo").map(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"));
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Failsafe.with(retryPolicy).get(() -> "foo"))
  .thenApplyAsync(value -> Failsafe.with(retryPolicy).get(() -> value + "bar"));
```

{% include common-links.html %}