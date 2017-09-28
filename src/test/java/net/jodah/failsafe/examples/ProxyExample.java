package net.jodah.failsafe.examples;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.util.concurrent.atomic.AtomicLong;

public class ProxyExample {

    public interface Printer {
        void print(String message);
    }

    private static class FailingSystemOutPrinter implements Printer {

        private final AtomicLong count = new AtomicLong(0);
        private long failUntil;

        public FailingSystemOutPrinter(long failUntil) {
            this.failUntil = failUntil;
        }

        @Override
        public void print(String message) {
            try {
                if (count.get() < failUntil) {
                    throw new RuntimeException(String.format("Failing %s/%s", count.get(), failUntil));
                }
                System.out.println(message);
            } finally {
                count.incrementAndGet();
            }
        }
    }

    public static void main(String[] args) {

        Printer printer = new FailingSystemOutPrinter(1);
        RetryPolicy retryPolicy = new RetryPolicy().withMaxRetries(2);

        Printer retryingPrinter = Failsafe.with(retryPolicy)
                .onFailedAttempt(t -> {
                    System.out.println(t.getCause());
                })
                .proxy(printer, Printer.class);
        retryingPrinter.print("Hello World");

    }

}
