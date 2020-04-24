# Failsafe

[![Build Status](https://travis-ci.org/jhalterman/failsafe.svg)](https://travis-ci.org/jhalterman/failsafe)
[![Maven Central](https://img.shields.io/maven-central/v/net.jodah/failsafe.svg?maxAge=60&colorB=53C92E)](https://maven-badges.herokuapp.com/maven-central/net.jodah/failsafe)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![JavaDoc](https://img.shields.io/maven-central/v/net.jodah/failsafe.svg?maxAge=60&label=javadoc)](https://jhalterman.github.com/failsafe/javadoc)
[![Join the chat at https://gitter.im/jhalterman/failsafe](https://badges.gitter.im/jhalterman/failsafe.svg)](https://gitter.im/jhalterman/failsafe)

Failsafe is a lightweight, zero-dependency library for handling failures in Java 8+, with a concise API for handling everyday use cases and the flexibility to handle everything else. It works by wrapping executable logic with one or more resilience policies, which can be combined and composed as needed. Current policies include [Retry](https://jodah.net/failsafe/retry/), [Timeout](https://jodah.net/failsafe/timeout/), [Fallback](https://jodah.net/failsafe/fallback/), and [CircuitBreaker](https://jodah.net/failsafe/circuit-breaker/).

## Usage

Visit the [Failsafe website](https://jodah.net/failsafe).

## Contributing

Check out the [contributing guidelines](https://github.com/jhalterman/failsafe/blob/master/CONTRIBUTING.md).

## License

Copyright 2015-2019 Jonathan Halterman and friends. Released under the [Apache 2.0 license](https://github.com/jhalterman/failsafe/blob/master/LICENSE).