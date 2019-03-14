package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Tests https://github.com/jhalterman/failsafe/issues/182
 */
@Test
public class Issue182Test {
    private RetryPolicy<Object> retryPolicy = new RetryPolicy<>();
    // would recommend using http://joel-costigliola.github.io/assertj/ - then you can assertThatThrownBy(...)

    public void shouldNotWrapErrorInFailsafeException() {
        Throwable throwable = null;

        try {
            Failsafe.with(retryPolicy)
                    .get(() -> { throw new Error(); });
        } catch (Throwable t) {
            throwable = t;
        }

        assertTrue(throwable instanceof Error);
    }

    public void shouldThrowErrorsEvenIfFallbackProvided() {
        Throwable throwable = null;

        try {
            Failsafe.with(Fallback.of("default-result"), retryPolicy)
                    .get(() -> { throw new Error(); });
        } catch (Throwable t) {
            throwable = t;
        }

        assertTrue(throwable instanceof Error);
    }
}
