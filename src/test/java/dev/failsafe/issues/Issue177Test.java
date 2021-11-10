package dev.failsafe.issues;

import dev.failsafe.Fallback;
import dev.failsafe.Failsafe;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;

@Test
public class Issue177Test {
  public void shouldSupportNullFallback() {
    Fallback<Boolean> fallback = Fallback.builder((Boolean) null).handleResult(false).build();
    assertNull(Failsafe.with(fallback).get(() -> false));
  }
}
