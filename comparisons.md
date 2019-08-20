---
layout: default
title: Comparisons
---

# Comparisons
{: .no_toc }

1. TOC
{:toc}

## Failsafe vs Hystrix

Failsafe is intended to be a lightweight, general purpose library for handling any type of execution. Hystrix is more oriented around the execution of remote requests and offers additional features for that purpose. A few differences worth noting:

* Hystrix has several external dependencies including Archais, Guava, ReactiveX, and Apache Commons Configuration. Failsafe has zero dependencies.
* Hystrix is primarily an implementation of the [circuit breaker][fowler-circuit-breaker] and bulkhead patterns. Failsafe offers several [policies][supported-policies] in addition to circuit breakers, which you can create and combine as needed.
* Hystrix requires that executable logic be placed in a [HystrixCommand] implementation. Failsafe allows executable logic via simple lambda expressions, [Runnables][Runnable], and [Suppliers][Supplier].
* Hystrix circuit breakers are time sensitive, recording execution results per second, [by default][num-buckets], and basing open/close decisions on the last second's results. Failsafe circuit breakers are not time sensitive, basing open/close decisions on the last N executions, regardless of when they took place.
* Failsafe circuit breakers support execution timeouts and configurable success thresholds. Hystrix only performs a single execution when in half-open state to determine whether to close a circuit.
* Failsafe supports user provided thread pools and schedulers. In Hystrix, asynchronous commands are executed on internally managed thread pools for particular dependencies.
* In Failsafe, asynchronous executions can be observed via [event listeners][event-listeners] and return [Future]. In Hystrix, asynchronous executions can be [observed](http://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html#observe--) using RxJava [Observables](http://reactivex.io/RxJava/javadoc/rx/Observable.html).
* Failsafe circuit breakers can be shared across different executions, so that if a failure occurs, all executions against that component will be halted by the circuit breaker.
* Failsafe circuit breakers can be used and operated as [standalone][circuit-breaker-standalone] constructs.

Reference: [https://github.com/Netflix/Hystrix/wiki/How-it-Works](https://github.com/Netflix/Hystrix/wiki/How-it-Works)

[HystrixCommand]: https://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html
[num-buckets]: https://github.com/Netflix/Hystrix/wiki/Configuration#metricsrollingstatsnumbuckets

{% include common-links.html %}