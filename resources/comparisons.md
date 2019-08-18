---
layout: default
title: Comparisons
---

# Comparisons

## Failsafe vs Hystrix

Failsafe is intended to be lightweight and to blend as seamlessly into your code as possible while Hystrix appears a bit more imposing but offers many more features as a result, such as response caching, request collapsing and threadpool management. Generally, Hystrix is more oriented around the execution of remote requests and offers features around that while Failsafe is more general purpose for handling any type of execution and failure. To highlight a few differences:

* Executable logic can be passed to Failsafe as simple lambda expressions, method references, Suppliers or Runnables. In Hystrix, your executable logic needs to be placed in a [HystrixCommand] implementation.
* In addition to CircuitBreakers, Failsafe supports retries, timeouts and fallbacks.
* Asynchronous executions in Failsafe can be performed on a user supplied ThreadPool / Scheduler, or the common pool. In Hystrix, asynchronous commands are executed on internally managed thread pools for particular dependencies.
* In Failsafe, asynchronous executions can be observed via the [event listeners API][event-listeners] and return [Future]. In Hystrix, asynchronous executions can be [observed](http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#observe--) using RxJava [Observables](http://reactivex.io/RxJava/javadoc/rx/Observable.html).
* Hystrix circuit breakers are time sensitive, recording execution results per second ([by default][num-buckets]) and basing open/close decisions on the last second's results. Failsafe circuit breakers are not time sensitive, basing open/close decisions on the last N (configured) executions, regardless of when they took place.
* Failsafe circuit breakers support execution timeouts and configurable success thresholds. Hystrix only performs a single execution when in half-open state to determine whether to close a circuit.
* Failsafe circuit breakers can be shared across different executions against the same component (such as a web service), so that if a failure occurs, all executions against that component will be halted by the circuit breaker.
* Failsafe circuit breakers can be used and operated as [standalone](https://github.com/jhalterman/failsafe#standalone-usage) constructs.

Reference: [https://github.com/Netflix/Hystrix/wiki/How-it-Works](https://github.com/Netflix/Hystrix/wiki/How-it-Works)

[HystrixCommand]: https://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html
[event-listeners]: https://github.com/jhalterman/failsafe#event-listeners
[Future]: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html
[num-buckets]: https://github.com/Netflix/Hystrix/wiki/Configuration#metricsrollingstatsnumbuckets

{% include common-links.html %}