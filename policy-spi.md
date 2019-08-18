---
layout: default
title: Policy SPI
---

# Policy SPI

Failsafe provides an SPI that allows you to implement your own [Policy] and plug it into Failsafe. Each [Policy] implementation must return a [PolicyExecutor] which is responsible for performing synchronous or asynchronous execution, handling pre-execution requests, or handling post-execution results. The existing [PolicyExecutor][PolicyExecutor] [implementations][policy-executor-impls] are a good reference for creating additional implementations.

{% include common-links.html %}