# 0.4.0

### New Features

* Added better support for invocation tracking

### API Changes

* New Invocation and AsyncInvocation APIs

# 0.3.3

### New Features

* Add Scheduler API
* Make RetryPolicy copyable

### Behavior Changes

* Require ContextualCallable and ContextualRunnable to be manually retried
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