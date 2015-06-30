# Recurrent

*Sophisticated retries made simple.*

## Introduction

Every developer has rolled their own retries.

Recurrent is a simple yet sophisticated library for performing retries. It features:

* Zero external dependencies
* Synchronous and asynchronous retries
* Transparent integration into existing APIs
* Java 8 friendly functional interfaces
* Simple integration with asynchronous libraries

## Usage

Create a RetryPolicy:

```java
RetryPolicy retryPolicy = new RetryPolicy()
	.withBackoff(1, 30, TimeUnit.SECONDS)
	.withMaxRetries(100);
```

Make a call with retries according to your RetryPolicy:

```
 Recurrent.doWithRetries(() -> connect(), retryPolicy, scheduler)
 	.whenComplete((connection, failure) -> {
       if (connect != null)
		 log.info("Connection established", connection);
	   else
	     log.error("Connection attempt failed", failure);
 	});
```

## Integrations

Recurrent was designed to play nicely with asynchronous libraries. Here are some example integrations:

### Netty



### RxJava



## Docs

JavaDocs are available [here](https://jhalterman.github.com/recurrent/javadoc).

## License

Copyright 2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
