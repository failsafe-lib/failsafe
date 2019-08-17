---
layout: default
title: Introduction
nav_order: 1
---

## Introduction

Failsafe is a lightweight, zero-dependency library for handling failures in Java 8+, with a concise API for handling everyday use cases and the flexibility to handle everything else. It  works by wrapping executable logic with one or more resilience [policies], which can be combined and [composed](#policy-composition) as needed. These policies include:

* [Retries](#retries)
* [Timeouts](#timeouts)
* [Fallbacks](#fallbacks)
* [Circuit breakers](#circuit-breakers) 

It also provides features that allow you to integrate with various scenarios, including:

* [Configurable schedulers](#configurable-schedulers)
* [Event listeners](#event-listeners)
* [Strong typing](#strong-typing)
* [Execution context](#execution-context) and [cancellation](#execution-cancellation)
* [Asynchronous API integration](#asynchronous-api-integration)
* [CompletionStage](#completionstage-integration) and [functional interface](#functional-interface-integration) integration
* [Execution tracking](#execution-tracking)
* [Policy SPI](#policy-spi)

## Setup

Add the latest [Failsafe Maven dependency][maven] to your project.

## Migrating from 1.x

Failsafe 2.0 has API and behavior changes from 1.x. See the [CHANGES](CHANGES.md#20) doc for more details.

## Usage

#### Getting Started

To start, we'll create a [RetryPolicy] that defines which failures should be handled and when retries should be performed:

```java
RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
  .handle(ConnectException.class)
  .withDelay(Duration.ofSeconds(1))
  .withMaxRetries(3);
```

We can then execute a `Runnable` or `Supplier` *with* retries:

```java
// Run with retries
Failsafe.with(retryPolicy).run(() -> connect());

// Get with retries
Connection connection = Failsafe.with(retryPolicy).get(() -> connect());
```

We can also execute a `Runnable` or `Supplier` asynchronously *with* retries:

```java
// Run with retries asynchronously
CompletableFuture<Void> future = Failsafe.with(retryPolicy).runAsync(() -> connect());

// Get with retries asynchronously
CompletableFuture<Connection> future = Failsafe.with(retryPolicy).getAsync(() -> connect());
```

#### Composing Policies

Multiple [policies] can be arbitrarily composed to add additional layers of resilience or to handle different failures in different ways:

```java
CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>();
Fallback<Object> fallback = Fallback.of(this::connectToBackup);

Failsafe.with(fallback, retryPolicy, circuitBreaker).get(this::connect);
```

Order does matter when composing policies. See the [section below](#policy-composition) for more details.

#### Failsafe Executor

Policy compositions can also be saved for later use via a [FailsafeExecutor]:

```java
FailsafeExecutor<Object> executor = Failsafe.with(fallback, retryPolicy, circuitBreaker);
executor.run(this::connect);
```

{% include common-links.html %}