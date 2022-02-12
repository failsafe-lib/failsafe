/*
 * Copyright 2018 the original author or authors.
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
package dev.failsafe.testing;

import org.testng.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs test invocations.
 */
public class TestCaseLogger implements IInvokedMethodListener {
  private static final Map<ITestNGMethod, Long> START_TIMES = new ConcurrentHashMap<>();

  public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
    if (!method.isTestMethod())
      return;

    ITestNGMethod testMethod = method.getTestMethod();
    IClass clazz = testMethod.getTestClass();

    START_TIMES.put(testMethod, System.currentTimeMillis());
    System.out.printf("BEFORE %s#%s%n", clazz.getRealClass().getName(), testMethod.getMethodName());
  }

  public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
    if (!method.isTestMethod())
      return;

    ITestNGMethod testMethod = method.getTestMethod();
    IClass clazz = testMethod.getTestClass();
    double elapsed = (System.currentTimeMillis() - START_TIMES.remove(testMethod)) / 1000.0;

    if (elapsed > 1)
      System.out.printf("AFTER %s#%s Ran for %s seconds%n", clazz.getRealClass().getName(), testMethod.getMethodName(), elapsed);
  }
}