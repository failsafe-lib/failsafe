package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.testng.annotations.Test;

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

    public void shouldNotWrapErrorInFailsafeExceptionWhenAsync() {
        Throwable throwable = null;

        try {
            Failsafe.with(retryPolicy).getAsync(errorThrower).get();
        } catch (Throwable t) {
            throwable = t;
        }

        assertTrue(throwable instanceof Error);
    }

    public void shouldThrowErrorsEvenIfFallbackProvidedWhenAsync() {
        Throwable throwable = null;

        try {
            Failsafe.with(Fallback.of("default-result"), retryPolicy).getAsync(errorThrower).get();
        } catch (Throwable t) {
            throwable = t;
        }

        assertTrue(throwable instanceof Error);
    }
}
