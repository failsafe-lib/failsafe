# Recurrent

*Simple, sophisticated retries.*

## Introduction

Recurrent is a simple, zero-dependency library for performing retries. It features:

* Zero external dependencies
* Synchronous and asynchronous retries
* Transparent integration into existing APIs
* Java 6/7/8 supported with Java 8 friendly functional interfaces
* Simple integration with asynchronous libraries

## Usage

#### Retry Policies

Recurrent supports flexible retry policies that allow you to express the number of retries, delay between attempts, delay between attempts including backoff, and maximum duration:

```java
RetryPolicy retryPolicy = new RetryPolicy()
	.withBackoff(1, 30, TimeUnit.SECONDS)
	.withMaxRetries(100);
```

#### Synchronous Retries

Synchronous invocations are performed and retried in the calling thread until the invocation succeeds or the retry policy is exceeded:

```java
Connection connection = Recurrent.withRetries(() -> connect(), retryPolicy);
```

#### Asynchronous Retries

Asynchronous invocations are performed and retried on a scheduled executor. When the invocation succeeds or the retry policy is exceeded, the resulting ListenableFuture is completed and any CompletionListeners registered against it are called:

```java
Recurrent.withRetries(() -> connect(), retryPolicy, executor)
  .whenSuccess((connection) -> log.info("Connection established to {}", connection)
  .whenFailure((failure) -> log.error("Connection attempts failed", failure));
```

#### Integrating with Asynchronous Code

Asynchronous code often reports failures via callbacks rather than throwing an exception. Recurrent provides nice integration with asynchronous code by allowing you to manually trigger retries as necessary:

```java
Recurrent.withRetries((invocation) -> {
    someService.connect(host, port).addFailureListener((failure) -> {
      // Manually retry invocation
      if (!invocation.retry())
        log.error("Connection attempts failed", failure)
    }
  }, retryPolicy, executor));
```

## Notes

#### On API Integration

A great way to add support for retries to your project's public API is to subclass the RetryPolicy class. This can allow you to offer retry support and policies to your users while keeping Recurrent hidden.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## License

Copyright 2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
