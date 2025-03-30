package dev.failsafe;

import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;

import java.util.List;
import java.util.Objects;

public final class FailurePredicates {
    private FailurePredicates() {}

    public static <R> CheckedBiPredicate<R, Throwable> resultPredicateFor(R result) {
        return (t, u) -> result == null ? t == null && u == null : Objects.equals(result, t);
    }

    @SuppressWarnings("unchecked")
    public static <R> CheckedBiPredicate<R, Throwable> failurePredicateFor(
            CheckedPredicate<? extends Throwable> failurePredicate) {
        return (t, u) -> u != null && ((CheckedPredicate<Throwable>) failurePredicate).test(u);
    }

    public static <R> CheckedBiPredicate<R, Throwable> resultPredicateFor(CheckedPredicate<R> resultPredicate) {
        return (t, u) -> {
            if (u == null) {
                return resultPredicate.test(t);
            } else {
                return false;
            }
        };
    }

    public static <R> CheckedBiPredicate<R, Throwable> failurePredicateFor(List<Class<? extends Throwable>> failures) {
        return (t, u) -> {
            if (u == null)
                return false;
            for (Class<? extends Throwable> failureType : failures)
                if (failureType.isAssignableFrom(u.getClass()))
                    return true;
            return false;
        };
    }
}
