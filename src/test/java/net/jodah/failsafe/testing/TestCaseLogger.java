package net.jodah.failsafe.testing;

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