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
package dev.failsafe;

import dev.failsafe.function.CheckedBiPredicate;
import org.testng.annotations.Test;

import static dev.failsafe.testing.Testing.unwrapExceptions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

@Test
public class FailurePolicyBuilderTest {
  public void testResultPredicateOnlyHandlesResults() {
    CheckedBiPredicate<Object, Throwable> resultPredicate = FailurePolicyBuilder.resultPredicateFor(result -> true);
    assertEquals(unwrapExceptions(() -> resultPredicate.test("result", null)), Boolean.TRUE);
    assertNotEquals(unwrapExceptions(() -> resultPredicate.test(null, new RuntimeException())), Boolean.TRUE);
  }
}
