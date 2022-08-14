# 3.2.4

### Improvements

- Added additional thread safety checks.

# 3.2.3

### Bug Fixes

- Fixed an issue where Timeouts would not fire under certain conditions when used outside a RetryPolicy.

# 3.2.2

### Improvements

- Released [OkHttp](https://square.github.io/okhttp/) module.
- Released [Retrofit](https://square.github.io/retrofit/) module.
- Added `Call` support to `FailsafeExecutor`, which can cancel synchrnous calls.
- Added `onCancel` callback to `ExecutionContext`, which can propagate cancellations.

### SPI Changes

- `SyncExecutionInternal.isInterruptable()` and `.setInterrupted` were removed and `.interrupt()` was added instead to simplify performing an interruption.

# 3.2.1

### Improvements

- Issue #326 - Added support for reserving a `RateLimiter` permit with a wait time.

### API Changes

- Deprecated `ExecutionContext.getLastFailure`, `Execution.recordFailure` and similar methods throughout that API that refer to exceptions as failures. In their place, new methods have been added, such as `getLastException`, `recordException` and so on. This clarifies the difference between an exception and a failure, since an exception may or may not be a failure, depending on the policy configuration.
- Changed the policy builders to use `CheckedPredicate` and `CheckedBiPredicate` instead of `Predicate` and `BiPredicate`, allowing exceptions to be thrown which are ignored.

# 3.2.0

### Improvements

- Issue #309 - Introduced a `Bulkhead` policy.
- Issue #318 - Add non-blocking async waiting for rate limiters.

### SPI Changes

- `PolicyExecutor.preExecuteAsync` was introduced to support async pre-execution. This is backwards compatible with `preExecute`.

# 3.1.0

### Improvements

- Issue #308 - Introduced a `RateLimiter` policy.

# 3.0.2

### Bug Fixes

- Issue #311 - `with(Executor)` not working as expected in some cases.

# 3.0.1

### Improvements

- Issue #310 - Added `.builder(PolicyConfig)` methods to each of the policy interfaces, to allow new policies to be built from existing config.
- Issue #251 - Relaxed the illegal state validation in `RetryPolicyBuilder` to allow different types of delays to be configured, replacing previous configuration. Also removed the requirement that a jitter duration be configured after a delay.

### Bug Fixes

- Issue #215 - Added overflow checking for large user-provided `Duration` values.

# 3.0

### API Changes

This release introduces some breaking changes to the API:

#### General

- The maven group id for Failsafe has changed to `dev.failsafe`. Be sure to update your build config.
- All files have been moved to the `dev.failsafe` package. Be sure to update your imports.

#### Policies

- All policies now use a builder API. Using the builder API mostly requires inserting `builder()` and `build()` methods into the call chain for constructing a policy since the actual `with` configuration methods are mostly the same as in 2.x policies, with a few changes described below. Some notes:
  - A policy builder can be created via `builder()`, ex: `RetryPolicy.builder()`.
  - `RetryPolicy` and `CircuitBreaker` can also be constructed with default values using `ofDefaults()`.
  - `Fallback` and `Timeout` offer additional factory methods for creating a a policy with only their required arguments, without using a builder, ex: `Timeout.of(Duration.ofSeconds(10))`. Optional arguments must be specified through a builder, ex: `Timeout.builder(duration).withInterrupt().build()`.
  - Policy configuration is now accessible via a `policy.getConfig()`.

#### RetryPolicy and CircuitBreaker

- In `RetryPolicyBuilder` and `CircuitBreakerBuilder`: 
  - `withDelay` has been renamed to `withDelayFn`.
  - `withDelayOn` has been renamed to `withDelayFnOn`.
  - `withDelayWhen` has been renamed to `withDelayFnWhen`.
  - The above method signatures have also been changed to accept a `ContextualSupplier` instead of a `DelayFunction`, since it provides access to the same information.

#### CircuitBreaker

- `onOpen`, `onClose`, and `onHalfOpen` methods now accept a `CircuitBreakerStateChangedEvent` argument.
- `allowsExecution()` was removed in favor of `acquirePermit()` and `tryAcquirePermit()`, which are meant for standalone CircuitBreaker usage.

#### Fallback

- The `Fallback` async factory methods have been removed in favor of a `FallbackBuilder.withAsync()` option.

#### Timeout

- `Timeout.withInterrupt(boolean)` is now `TimeoutBuilder.withInterrupt()`.

#### Execution and AsyncExecution

- The standalone `Execution` API, and the `AsyncExecution` API created via the `FailsafeExecutor.runAsyncExecution` and `getAsyncExecution` methods, have been unified to include:
  - `record(R, Throwable)`
  - `recordResult(R)`
  - `recordException(Throwable)`
  - `complete()`
- The previously supported `Execution` and `AsyncExecution` methods for recording a result have been removed. The methods for performing a retry have also been removed. For `Execution`, `isComplete` will indicate whether the execution is complete else if retries can be performed. For `AsyncExecution` retries will automatically be performed, if possible, immediately after a result or failure is recorded.
- The `Execution` constructor is no longer visible. `Execution` instances must now be constructed via `Execution.of(policies)`.
- `Execution.getWaitTime()` was renamed to `getDelay()`.

#### Failsafe class

- `Failsafe.with(P[] policies)` was removed in favor of `Failsafe.with(P, P...)`. This should only affect users who were explicitly passing an array to `Failsafe.with`.

### SPI Changes

The following changes effect the SPI classes, for users who are extending Failsafe with custom schedulers or policies:

- `Scheduler` and `DefauledScheduledFuture` were moved to the `spi` package.
- `Policy` and `PolicyExecutor` were moved to the `spi` package and some method signatures changed.
- `ExecutionResult` was moved to the `spi` package and made generic.
- Several new classes were added to the `spi` package to contain internal execution APIs including `ExecutionInternal`, `SyncExecutionInternal`, and `AsyncExecutionInternal`.
- `FailsafeFuture` was moved to the SPI package and some method signatures changed.

### Bug Fixes

- Improved the reliability of async executions, cancellations, and Timeouts.

### Improvements

- Issue #47 - All policies and policy config classes are now threadsafe. Policy builders are not threadsafe.
- Issue #201 - Thread safety is clearly documented in policy, policy config, and policy builder classes.
- Issue #292 - Created an extensible Policy SPI.
- Issue #254 - Added an explicit `compose` method to `FailsafeExecutor`.
- Issue #293 - Added `RetryPolicyBuilder.withBackoff(Duration, Duration)` and `.withDelay(Duration, Duration)`.
- Issue #221 - `Executor` instances configured via `FailsafeExecutor.with(Executor)` are now used on all executions, including sync executions, and can be used in conjunction with a separately configured `ExecutorService` or `Scheduler` for async executions.
- Added `FailsafeExecutor.getPolicies()`.
- Added `isFirstAttempt()` and `isRetry()` to `ExecutionAttempt`, which is available via a few event listeners.

# 2.4.4

### Bug Fixes

- Fixed #298 - `Fallback.onFailedAttempt` not being called correctly

### Improvements

- Fixed #296 - Add Automatic-Module-Name entry to the generated manifest file

### API Changes

- Added a generic result type `R` to `ExecutionContext`, `Execution`, `AsyncExecution`, and `AsyncRunnable`. This ensures that result types are unified across the API. It does mean that there are a few minor breaking changes to the API:
  - `ContextualSupplier` now has an additional result type parameter `R`. Normally this type is used as lambda parameters where the type is inferred, so most users should not be impacted. But any explicit generic declaration of this type will not compile until the new parameter is added.
  - `PolicyExecutor`, which is part of the SPI, now accepts an additional result type parameter `R`. This is only relevant for SPI users who are implementing their own Policies.
- Changed `FailsafeExecutor.getAsyncExecution` to accept `AsyncRunnable` instead of `AsyncSupplier`. This is a breaking change for any `getAsyncExecution` calls, but the fix is to simply remove any `return` statement. The reason for this change is that the provided object does not need to return a result since the result will already be passed asynchronously to one of the `AsyncExecution` `complete` or `retry` methods.

# 2.4.3

### Bug Fixes

- Fixed #289 - Binary imcompatibility with code that was compiled against previous Failsafe versions.
  
# 2.4.2

### Improvements

- Added `RetryPolicy.onRetryScheduled` event handler.
- Added `ExecutionEvent.getExecutionCount()` and `ExecutionContext.getExecutionCount()`, which distinguishes between attempts which may have been rejected and completed executions.
- Added `Failsafe.none` to create a no-op `FailsafeExecutor`.
- Improved support for outer Timeouts with retries.
- Fixed #221 - Added support for `FailsafeExecutor.with(Executor)`.
- Fixed #277 - Changed `Timeout` to use Failsafe's internal scheduler, so that user provided `ExecutorService` shutdowns do not interfere with timeouts.
- Fixed #266 - Propagate `Future` cancellation to supplied `CompletionStage` when using `getStageAsync`.

### Bug Fixes

- Fixed #267 - Allow null fallback values to be passed through when using nested fallbacks.

# 2.4.1

### Improvements

- Fixed #234 - An outer `Timeout` should cancel any inner retries.

### API Changes

- Deprecated `Timeout.withCancel(boolean)` and `Timeout.canCancel()`. Timeouts always cancel any executions and inner retries.
- Added `Timeout.withInterrupt(boolean)` to take the place of `withCancel`.
- Added `ExecutionEvent.getElapsedAttemptTime()`.

# 2.4.0

### Improvements

- Added time based thresholding support to `CircuitBreaker` via:
  - `withFailureThreshold(int failureThreshold, Duration failureThresholdingPeriod)`
  - `withFailureThreshold(int failureThreshold, int failureExecutionThreshold, Duration failureThresholdingPeriod)`
  - `withFailureRateThreshold(int failureRateThreshold, int failureExecutionThreshold, Duration failureThresholdingPeriod)`
- Added getters to `CircuitBreaker` for existing count based thresholding settings:
  - `getFailureThresholdingCapacity()`
  - `getSuccessThresholdingCapacity()`
- And added getters to `CircuitBreaker` for new time based thresholding settings:
  - `getFailureRateThreshold()`
  - `getFailureExecutionThreshold()`
  - `getFailureThresholdingPeriod()` 
- Added some new metrics to `CircuitBreaker`:
  - `getSuccessRate()`
  - `getFailureRate()`
  - `getExecutionCount()` 

### API Changes

- Changed the return type of `CircuitBreaker`'s `getFailureThreshold()` and `getSuccessThreshold()` from `Ratio` to `int`. `getFailureThresholdingCapacity`, `getFailureRateThreshold`, `getFailureExecutionThreshold`, and `getSuccessThresholdingCapacity` provide additional detail about thresholding configuration.
- Removed support for the previously deprecated `CircuitBreaker.withTimeout`. The `Timeout` policy should be used instead.

# 2.3.5

### Bug Fixes

- Fixed #242 - Delays not occurring between manually triggered async execution retries.

# 2.3.4

### Improvements

- Re-worked internal threading to only create async threads immediately prior to supplier execution. See #230.

### Bug Fixes

- Fixed #240 - `handleResult(null)` always triggering when an exception is thrown.

# 2.3.3

### Improvements

Added support for `CompletionStage` to the `Fallback` policy.

### Bug Fixes

- Fixed #224 - Allow combining random delay and jitter.

### API Changes

- `Fallback.apply` was made package private.
- `DelayablePolicy.computeDelay` was made package private.

# 2.3.2

### Improvements

- Added `CircuitBreaker.getRemainingDelay()`.
- Added support for `Fallback.VOID`.

### Bug Fixes

- Fixed #216 - Incorrect computation of randomDelay.

# 2.3.1

### Improvements

- Set `setRemoveOnCancelPolicy(true)` for the internal delay scheduler.
- Added `Scheduler.DEFAULT` to return the default scheduler Failsafe uses.

### Bug Fixes

- Fixed #206 - Problem with Fallback converting from failure to success.

# 2.3.0

### Behavior Changes

- `FailsafeExecutor.get` and `FailsafeExecutor.run` will no longer wrap `Error` instances in `FailsafeException` before throwing.

### Bug Fixes

- Fixed potential race between `Timeout` interrupts and execution completion.

# 2.2.0

### Improvements

- Added a new `Timeout` policy that fails with `TimeoutExceededException`.
- Added `ExecutionContext.isCancelled()`.
- Added `ExecutionContext.getElapsedAttemptTime()`.
- Made the internal delay scheduler more adaptive.

### API Changes

- Deprecated `CircuitBreaker.withTimeout` in favor of using a separate `Timeout` policy.

### Bug Fixes

- Reset interrupt flag when a synchronous execution is interrupted.
- Improved handling around externally completing a Failsafe `CompletableFuture`.

# 2.1.1

### Improvements

- Added support for `CircuitBreaker.withDelay(DelayFunction)`
- Added `Fallback.ofException` for returning custom exceptions.
- Added `ExecutionContext.getLastResult` and `.getLastFailure` to support retries that depend on previous executions
- Added `CircuitBreakerOpenException.getCircuitBreaker`

### API Changes

- `RetryPolicy.DelayedFunction` was moved to the `net.jodah.failsafe.function` package.
- Removed `RetryPolicy.canApplyDelayFn`

# 2.1.0

### Improvements

- Added support for `Failsafe.with(List<Policy<R>>)`.
- Allow `null` `Fallback` values.

### Behavior Changes

- A [standalone](https://github.com/jhalterman/failsafe#execution-tracking) or [async execution](https://github.com/jhalterman/failsafe#asynchronous-api-integration) will only be marked as complete when all policies are complete. `Execution.isComplete` reflects this. 

### Bug Fixes

- Issue #190 - Failure listener called on success for async executions.
- Issue #191 - Add missing listeners to RetryPolicy copy constructor.
- Issue #192 - Problem with detecting completion when performing async execution.

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
- RetryPoilicy
  - The `retryOn`, `retryIf`, and `retryWhen` methods have been replace with `handleOn`, etc.
- CircuitBreaker
  - The `failOn`, `failIf`, and `failWhen` methods have been replace with `handleOn`, etc.
- Fallbacks
  - Fallbacks must be wrapped in a `Fallback` instance via `Fallback.of`
- Failsafe APIs
  - `Supplier`s are now used instead of `Callable`s.
  - `java.util.function.Predicate` is used instead of Failsafe's internal Predicate.
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