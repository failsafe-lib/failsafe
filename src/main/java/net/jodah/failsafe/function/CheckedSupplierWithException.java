package net.jodah.failsafe.function;

@FunctionalInterface
public interface CheckedSupplierWithException<T, E extends Throwable> {
  T get() throws E;
}