package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipError;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests https://github.com/jhalterman/failsafe/issues/182
 */
@Test
public class Issue182Test {
  private RetryPolicy<Object> retryPolicy = new RetryPolicy<>();
  private Fallback<Object> fallback = Fallback.of("default-result");
  private final CheckedSupplier<Object> errorThrower = () -> {
    throw new Error();
  };

  // Would recommend using http://joel-costigliola.github.io/assertj/ - then you can assertThatThrownBy(...)
  public void shouldNotWrapErrorInFailsafeException() {
    Throwable throwable = null;

    try {
      Failsafe.with(retryPolicy).get(errorThrower);
    } catch (Throwable t) {
      throwable = t;
    }

    assertTrue(throwable instanceof Error);
  }

  public void shouldThrowErrorsEvenIfFallbackProvided() {
    Throwable throwable = null;

    try {
      Failsafe.with(fallback, retryPolicy).get(errorThrower);
    } catch (Throwable t) {
      throwable = t;
    }

    assertTrue(throwable instanceof Error);
  }

  public void shouldStillRetryOnErrorsIfExplicitlyHandled() {
    AtomicInteger counter = new AtomicInteger(1);

    // Not sure why one would ever want to do this, since `ZipError` is unrecoverable, but still giving the option
    retryPolicy = retryPolicy.handle(ZipError.class)
        .withMaxAttempts(10);

    String result = Failsafe.with(fallback, retryPolicy).get(() -> {
      if (counter.getAndIncrement() < 9) {
        throw new ZipError("");
      }
      return "result";
    });

    assertEquals(result, "result");
    assertEquals(counter.get(), 10);
  }

  public void shouldNotWrapErrorInFailsafeExceptionWhenAsync() throws InterruptedException {
    Throwable throwable = null;

    try {
      Failsafe.with(retryPolicy).getAsync(errorThrower).get();
    } catch (ExecutionException t) {
      throwable = t.getCause();
    }

    assertTrue(throwable instanceof Error);
  }

  public void shouldThrowErrorsEvenIfFallbackProvidedWhenAsync() throws InterruptedException {
    Throwable throwable = null;

    try {
      Failsafe.with(Fallback.of("default-result"), retryPolicy).getAsync(errorThrower).get();
    } catch (ExecutionException t) {
      throwable = t.getCause();
    }

    assertTrue(throwable instanceof Error);
  }
}
