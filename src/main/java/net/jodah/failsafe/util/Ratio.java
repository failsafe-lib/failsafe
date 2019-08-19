/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.util;

import java.util.Objects;

/**
 * A ratio.
 *
 * @author Jonathan Halterman
 */
public class Ratio {
  private final int numerator;
  private final int denominator;
  private final double value;

  public Ratio(int numerator, int denominator) {
    this.numerator = numerator;
    this.denominator = denominator;
    value = denominator == 0 ? 0 : (double) numerator / (double) denominator;
  }

  /**
   * Returns the value of the ratio computed as the {@code numerator} / {@code denominator}.
   */
  public double getValue() {
    return value;
  }

  public int getDenominator() {
    return denominator;
  }

  public int getNumerator() {
    return numerator;
  }

  @Override
  public String toString() {
    return "Ratio[" + numerator + " / " + denominator + " = " + value + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Ratio ratio = (Ratio) o;
    return numerator == ratio.numerator && denominator == ratio.denominator && Double.compare(ratio.value, value) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(numerator, denominator, value);
  }
}
