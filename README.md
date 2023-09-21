# Failsafe

[![Build Status](https://github.com/failsafe-lib/failsafe/workflows/build/badge.svg)](https://github.com/failsafe-lib/failsafe/actions)
[![Maven Central](https://img.shields.io/maven-central/v/dev.failsafe/failsafe.svg?maxAge=60&colorB=53C92E)](https://maven-badges.herokuapp.com/maven-central/dev.failsafe/failsafe)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Slack](https://img.shields.io/badge/slack-failsafe-brightgreen.svg?logo=slack)](https://failsafe-lib.slack.com)
[![JavaDoc](https://img.shields.io/maven-central/v/dev.failsafe/failsafe.svg?maxAge=60&label=javadoc)](https://failsafe.dev/javadoc/core)

Failsafe is a lightweight, zero-dependency library for handling failures in Java 8+, with a concise API for handling everyday use cases and the flexibility to handle everything else. It works by wrapping executable logic with one or more resilience policies, which can be combined and composed as needed. 

Policies include [Retry](https://failsafe.dev/retry/), [CircuitBreaker](https://failsafe.dev/circuit-breaker/), [RateLimiter](https://failsafe.dev/rate-limiter/), [Timeout](https://failsafe.dev/timeout/), [Bulkhead](https://failsafe.dev/bulkhead/), and [Fallback](https://failsafe.dev/fallback/). Additional modules include [OkHttp](https://failsafe.dev/okhttp/) and [Retrofit](https://failsafe.dev/retrofit/).

## Usage

Visit [failsafe.dev](https://failsafe.dev) for usage info, docs, and additional resources.

## Contributing

Check out the [contributing guidelines](https://github.com/failsafe-lib/failsafe/blob/master/CONTRIBUTING.md).

## License

Copyright Jonathan Halterman and friends. Released under the [Apache 2.0 license](https://github.com/failsafe-lib/failsafe/blob/master/LICENSE).