package net.jodah.failsafe.proxy;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Proxy class that retries operations and has a circuit breaker. */
public class FailsafeInvocationHandler implements InvocationHandler {

    private final Object underlying;
    private final SyncFailsafe<?> failsafe;

    private FailsafeInvocationHandler(Object underlying, SyncFailsafe<?> failsafe) {
        this.underlying = underlying;
        this.failsafe = failsafe;
    }

    /**
     * Create a Proxy around an underlying interface such that the method calls are wrapped with a
     * {@link RetryPolicy} and a {@link CircuitBreaker}
     */
    @SuppressWarnings("unchecked")
    public static <T> T retryingProxy(T instance, Class<T> clazz, SyncFailsafe<?> failsafe) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Only interfaces are supported with default JRE proxy.");
        }
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[] { clazz },
                new FailsafeInvocationHandler(instance, failsafe));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return invokeWithRetry(underlying, method, args);
    }

    private Object invokeWithRetry(Object target, Method method, Object[] args)
            throws Throwable {
        try {
            return failsafe.get(() -> method.invoke(target, args));
        } catch (FailsafeException ex) {
            throw unwrapException(ex); //unwrap FailsafeException and InvocationTargetException
        }
    }

    /**
     * Unwrap the exception that Failsafe throws.
     */
    private static Throwable unwrapException(FailsafeException ex) {
        // There are two possibly layers of indirection here.
        //  1) Fail-safe wraps exceptions with FailsafeException
        //  2) Proxy clients use reflection to invoke methods, which wraps
        //     exceptions in InvocationTargetExceptions.
        Throwable inner = ex.getCause();
        if (inner instanceof InvocationTargetException) {
            inner = inner.getCause();
        }
        return inner;
    }

}
