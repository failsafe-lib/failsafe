---
layout: default
title: Policies
---

# Policies
{: .no_toc }

1. TOC
{:toc}

## Failure Handling

Failsafe [policies][FailurePolicy] determine when an execution result represents a failure and how to handle it. By default, policies treat any `Exception` as a failure. But policies can also be configured to handle more specific failures or conditions:

```java
policy
  .handle(ConnectException.class, SocketException.class)
  .handleIf(failure -> failure instanceof ConnectException);
```

They can also be configured to handle specific results or result conditions:

```java
policy
  .handleResult(null)
  .handleResultIf(result -> result == null);  
```

## Policy Composition

Policies can be composed in any way desired, including multiple policies of the same type. Policies handle execution results in reverse order, similar to the way that function composition works. For example, consider:

```java
Failsafe.with(fallback, retryPolicy, circuitBreaker, timeout).get(supplier);
```

This results in the following internal composition when executing the `supplier` and handling its result:

```
Fallback(RetryPolicy(CircuitBreaker(Timeout(Supplier))))
```

This means the `Supplier` is evaluated first, then it's result is handled by the `Timeout`, then the `CircuitBreaker`, the `RetryPolicy`, and the `Fallback`. Each policy makes its own determination as to whether the result represents a failure. This allows different policies to be used for handling different types of failures.

### Typical Composition

A typical Failsafe configuration that uses multiple policies might place a `Fallback` as the outer-most policy, followed by a `RetryPolicy`, `CircuitBreaker`, and a `Timeout` as the inner-most policy:

```java
Failsafe.with(fallback, retryPolicy, circuitBreaker, timeout)
```

That said, it really depends on how the policies are being used, and different compositions make sense for different use cases.

## Supported Policies

Read about the built-in policies that Failsafe supports:

- [Retry][retry]
- [Timeout][timeouts]
- [Fallback][fallbacks]
- [Circuit Breaker][circuit-breakers]

{% include common-links.html %}