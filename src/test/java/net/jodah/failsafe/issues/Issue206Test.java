package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

@Test
public class Issue206Test {
  public void test() {
    try {
      Failsafe.with(Fallback.of(e -> true).handleResultIf(r -> true))
        .onFailure(e -> fail("Unexpected execution failure"))
        .get(() -> true);
    } catch (RuntimeException e) {
      fail("Unexpected exception");
    }

    try {
      Failsafe.with(Fallback.of(() -> {
      })).onFailure(e -> fail("Unexpected execution failure")).run(() -> {
        throw new RuntimeException();
      });
    } catch (RuntimeException e) {
      fail("Unexpected exception");
    }
  }
}
