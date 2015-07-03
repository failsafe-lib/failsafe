# Recurrent

*Simple, sophisticated retries.*

## Introduction

Recurrent is a simple, zero-dependency library for performing retries. It features:

* Synchronous and asynchronous retries
* Java 6+ support with seamless Java 8 integration
* Simple integration with 3rd party asynchronous libraries
* Transparent integration into public APIs

## Usage

#### Retry Policies

Recurrent supports flexible retry policies that allow you to express the maximum number of retries, delay between retries including backoff, and maximum duration to retry for:

```java
RetryPolicy delayPolicy = new RetryPolicy()
  .withDelay(1, TimeUnit.SECONDS)
  .withMaxRetries(100);
    
RetryPolicy backoffPolicy = new RetryPolicy()
    .withBackoff(1, 30, TimeUnit.SECONDS)
    .withMaxDuration(5, TimeUnit.MINUTES);
```

#### Synchronous Retries

Synchronous invocations are performed and retried in the calling thread until the invocation succeeds or the retry policy is exceeded:

```java
Connection connection = Recurrent.get(() -> connect(), retryPolicy);
```

#### Asynchronous Retries

Asynchronous invocations are performed and retried on a scheduled executor. When the invocation succeeds or the retry policy is exceeded, the resulting RecurrentFuture is completed and any CompletionListeners registered against it are called:

```java
Recurrent.get(() -> connect(), retryPolicy, executor)
  .whenSuccess(connection -> log.info("Connected to {}", connection))
  .whenFailure(failure -> log.error("Connection attempts failed", failure));
```

#### Asynchronous Integration

Asynchronous code reports failures via future callbacks rather than throwing an exception. Recurrent provides nice integration with asynchronous code by allowing you to manually trigger retries via a callback:

```java
Recurrent.get(invocation -> {
  someService.connect(host, port).onFailure((failure) -> {
    // Manually retry invocation
    if (!invocation.retry(failure))
      log.error("Connection attempts failed", failure)
  }
}, retryPolicy, executor));
```

Java 8 users can also use Recurrent to retry CompletableFuture calls:

```java
Recurrent.future(() -> CompletableFuture.supplyAsync(() -> "foo")
  .thenApplyAsync(value -> value + "bar")
  .thenAccept(System.out::println), retryPolicy, executor);
```

#### Java 8 Functional Interfaces

Recurrent can be used to create retryable Java 8 functional interfaces:

```java
Function<String, Connection> connect =
  address -> Recurrent.get(() -> connect(address), retryPolicy);
```

We can retry streams:

```java
Recurrent.get(() -> Stream.of("foo")
  .map(value -> Recurrent.get(() -> value + "bar", retryPolicy))
  .collect(Collectors.toList()), retryPolicy);  
```

Individual Stream operations:

```java
Stream.of("foo")
  .map(value -> Recurrent.get(() -> value + "bar", retryPolicy))
  .forEach(System.out::println);
```

Or individual CompletableFuture stages:

```java
CompletableFuture.supplyAsync(() -> Recurrent.get(() -> "foo", retryPolicy))
  .thenApplyAsync(value -> Recurrent.get(() -> value + "bar", retryPolicy))
  .thenAccept(System.out::println);
```

## 3rd Party Integrations

Recurrent was designed to integrate nicely with existing libraries, including 3rd party asynchronous libraries. Here are some example integrations:

* [Netty](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/NettyExample.java)
* [RxJava](https://github.com/jhalterman/recurrent/blob/master/src/test/java/net/jodah/recurrent/examples/RxJavaExample.java)

## Public API Integration

Recurrent is great for integrating into other libraries and public APIs. One approach for this is to subclass the RetryPolicy class and allow your users to specify retry policies for specific implementations. The rest of Recurrent, including the actual invocation, can be kept internal. Alternatively you can use a tool such as the Maven shade plugin to entirely repackage and distribute Recurrent with your library/project.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## License

Copyright 2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
