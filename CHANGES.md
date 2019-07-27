# Next

### Improvements

- Added `ExecutionContext.getLastResult` and `.getLastFailure` to support retries that depend on previous executions

# 2.1.1

### Improvements

- Added `Fallback.ofException` for returning custom exceptions.

# 2.1.0

### Improvements

- Added support for `Failsafe.with(List<Policy<R>>)`.
- Allow `null` `Fallback` values.

### Bug Fixes

- Issue #190 - Failure listener called on success for async executions.
- Issue #191 - Add missing listeners to RetryPolicy copy constructor.
- Issue #192 - Problem with detecting completion when performing async execution.

### Behavior Changes

- A [standalone](https://github.com/jhalterman/failsafe#execution-tracking) or [async execution](https://github.com/jhalterman/failsafe#asynchronous-api-integration) will only be marked as complete when all policies are complete. `Execution.isComplete` reflects this. 

# 2.0.1

### Improvements

- Added support for using `ExecutorService` via `FailsafeExecutor.with(ExecutorService)`.
- Added interruptable cancellation for executions ran on `ForkJoinPool` via `CompletableFuture.cancel(true)`.

### Bug Fixes

- Issue #171 - Handle completed futures when using `getStageAsync`.

# 2.0

### Improvements

* [Policy composition](README.md#policy-composition) is now supported.
* [A Policy SPI](README.md#policy-spi) is now available.
* Async execution is now supported without requiring that a `ScheduledExecutorService` or `Scheduler` be configured. When no scheduler is configured, the `ForkJoinPool`'s common pool will be used by default.
* `Fallback` now support async execution via `ofAsync`.
* `CircuitBreaker` supports execution metrics (see below).
* Strong typing based on result types is supported throughout the API.

### Behavior Changes

- `RetryPolicy` now has 3 max attempts by default.
- `CircuitBreaker` now has a 1 minute delay by default.

### JRE Changes

- Java 8+ is now required

### API Changes

Failsafe 2.0 includes a few API changes from 1.x that were meant to consolidate behavior such as the execution APIs, which are now based on common `Policy` implementations, while adding some new features such as `Policy` composition.

- Policies
  - Policy implementations now take a type parameter `R` that represents the expected result type.
  - Some of the time related policy configurations have been changed to use `Duration` instead of `long` + `TimeUnit`.
- Policy configuration
  - Multiple policies can no longer be configured by chaining multiple `Failsafe.with` calls. Instead they must be supplied in a single `Failsafe.with` call. This is was intentional to require users to consider the ordering of composed policies. See the README section on [policy composition](README.md#policy-composition) for more details.
- Fallbacks
  - Fallbacks must be wrapped in a `Fallback` instance via `Fallback.of`
- Failsafe APIs
  - `Supplier`s are now used instead of `Callable`s.
  - `withFallback` is no longer supported. Instead, `Failsafe.with(fallback...)` should be used.
- Async execution
  - Async execution is now performed with the `getAsync`, `runAsync`, `getStageAsync`, etc. methods.
  - Async API integration is now supported via the `getAsyncExecution`, `runAsyncExecution`, etc. methods.
- Event listeners
  - Event listeners now all consume a single `ExecutionEvent` object, which includes references to the result, failure, and other information.
  - Event listeners that are specific to policies, such as `onRetry` for `RetryPolicy`, must now be configured through the policy instance. The top level `Failsafe` API only supports `onComplete`, `onSuccess`, and `onFailure`. Individual `Policy` implementations still support  `onSuccess` and `onFailure` in addition to policy specific events.
  - The top level `Failsafe.onSuccess` event listener will only be called if *all* configured policies consider an execution to be successful, otherwise `onFailure` will be called. 
  - The `Listeners` class was removed, since it was mostly intended for Java 6/7 users.
  - The async event listener APIs were removed. Events will always be delivered in the same thread as the execution that they follow or preceed, including for async executions.
- Java 8
  - `java.time.Duration` is used instead of Failsafe's own `Duration` impl.
  - `ChronoUnit` is used instead of `TimeUnit` in policies.
- `ExecutionContext.getExecutions` is now `getAttemptCount`.
- `Schedulers.of(ScheduledExecutorService)` was moved to the `Scheduler` interface.

### API Additions

- `CircuitBreaker`
  - `preExecute` is now exposed to support standalone usage.
  - Execution metrics are available via `getFailureCount`, `getFailureRatio`, `getSuccessCount`, and `getSuccessRatio`.

### Bug Fixes

* Issue #152 - Min/max delay was not being computed correctly

# 1.1.0

### Bug Fixes

* Issue #115 - Jitter bigger than Delay causes a (random) failure at runtime
* Issue #116 - Setting jitter without a delay works fine bug
* Issue #123 - Ability to reset the jitterFactor

### Improvements

* Issue #110 - Added support for computed delays: `RetryPolicy.withDelay(DelayFunction)`
* Issue #126 - Added support for random delays: `RetryPolicy.withDelay(1, 10, TimeUnit.MILLISECONDS)`

# 1.0.5

### Bug Fixes

* Issue #97 - Should not increment exponential backoff time on first attempt
* Issue #92 - `handleRetriesExceeded` called incorrectly.

# 1.0.4

### API Changes

* Asynchronous execution attempts no longer throw `CircuitBreakerOpenException` if a configured `CircuitBreaker` is open when an execution is first attempted. Instead, the resulting `Future` is completed exceptionally with `CircuitBreakerOpenException`. See [issue #84](https://github.com/jhalterman/failsafe/issues/84).

### Improvements

* Issue #81 - Added single argument failure configuration to avoid varargs related warnings.

# 1.0.3

### Bug Fixes

* Fixed #76 - Make sure AsyncExecution.completeOrRetry is called when Error is thrown.

# 1.0.2

### Bug Fixes

* Fixed #75 - Incorrect future completion when a fallback is present.

# 1.0.1

### Changes

* `FailsafeException` now has public constructors, for easier mocking and testing.

# 1.0.0

### API Changes

* Failsafe will now only throw `FailsafeException` when an execution fails with a checked `Exception`. See [issue #66](https://github.com/jhalterman/failsafe/issues/66) for details.

# 0.9.5

### Bug Fixes

* Fixed #59 - Classloading issue on Java 6/7.

# 0.9.4

### Bug Fixes

* Fixed #63 - Proper handling of thread interrupts during synchronous execution delays.
* Fixed #54 - Added hashCode and equals implementations to Duration.

# 0.9.3

### New Features

* Added OSGi support.
* `FailsafeFutuer.cancel` calls completion handlers. `.get` after cancel throws `CancellationException`.

### Bug Fixes

* Fixed #52 - FailsafeFuture.cancel not working as expected.
* Fixed #55 - Fallback always called for asynchronous executions.

### API Changes

* `CircuitBreakerOpenException` now extends `FailsafeException`.

# 0.9.2

### New Features

* Various fallback and listener API additions and improvements

# 0.9.1

### New Features

* Added support for retry delay [jitter](https://github.com/jhalterman/failsafe#retry-policies).

# 0.9.0

### New Features

* Added support for [fallbacks](https://github.com/jhalterman/failsafe#fallbacks).

### Bug Fixes

* Fixed issue #36 - Failed attempt listener not always called on completion.
* Fixed issue #34 - CircuitBreaker should default to closed state.

# 0.8.3

### Bug Fixes

* Fixed #33 - `CircuitBreaker` not decrementing currentExections when under load

# 0.8.2

### New Features

* Added support for `onRetriesExceeded` listeners.
* `RetryPolicy` can be extended (it's no longer marked as final)

### Bug Fixes

* Abort should not call failure listeners.

# 0.8.1

### New Features

* Simplified listeners API.
* Added support for failure listeners via `Failsafe.with(...).onFailure(e -> {})`.
* Added `onAbort` listeners.
* Added additional async listeners.
* `RetryPolicy` and `CircuitBreaker` now support multiple configuration rules. Ex: `new RetryPolicy().retryWhen(null).retryWhen("")`. If any rule matches then the policy is matched.

### API Changes

* Added top level support for listener registration via `Failsafe.with(...).onXxx`. The `Listeners` class is now only meant for Java 6 and 7 usage via method overrides.
* Removed listener registration from `Listeners` class.
* Removed `AsyncListeners` class. 
* Removed listener registration from `FailsafeFuture` class.

# 0.8.0

### New Features

* Added support for circuit breakers

### API Changes

* Project renamed from Recurrent to Failsafe

# 0.7.1

### Bug Fixes

* Added better support for scheduling failure handling
* Fixed RetryPolicy failure assignability checking

### API Changes

* Invocation APIs were renamed to Execution to better align with the `java.util.concurrent` naming.
* `InvocationStats.getAttemptCount()` was renamed to `ExecutionStats.getExecutions()`

# 0.7.0

### New Features

* Added additional contextual callable and runnable support

### API Changes

* Changed to a new API entry point: `Recurrent.with`.
* Added `.with` for configuring listeners.


# 0.6.0

### New Features

* Added `RetryPolicy.abortOn`, `abortWhen` and `abortIf` methods to abort retries when matched.

### API Changes

* `RetryPolicy.retryWhen` was renamed to `retryIf` for retrying if a `Predicate` is matched.
* `RetryPolicy.retryFor` was renamed to `retryWhen` for retrying when a result is matched.
* `Scheduler` and `Schedulers` were moved to `net.jodah.recurrent.util.concurrent`.

# 0.5.0

### New Features

* Added support for synchronous and asynchronous event listeners
* Added support for `CheckedRunnable`

### API Changes

* The `Recurrent.run` methods now require a `CheckedRunnable` rather than `Runnable`. This allows Recurrent to be used on code that throws checked exceptions without having to wrap the code in try/catch blocks.
* The synchronous `Recurrent.run` and `Recurrent.get` methods will throw a `RecurrentException` if a failure occurs and the retry policy is exceeded.

# 0.4.0

### New Features

* Added better support for invocation tracking

### API Changes

* New Invocation and `AsyncInvocation` APIs

# 0.3.3

### New Features

* Add `Scheduler` API
* Make `RetryPolicy` copyable

### Behavior Changes

* Require `ContextualCallable` and `ContextualRunnable` to be manually retried
* Add support for checking multiple retry policy conditions

### API Changes

* Make ContextualRunnable throw Exception

# 0.3.2

### New Features

* Add support for retrying when an invocation result matches a policy

# 0.3.1

### New Features

* Added support for seprate retry tracking.

# 0.3.0

* Initial Release