package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class Issue165Test {
  public void testOfStage() {
    Fallback<Object> fallback = Fallback.ofStage(e -> {
      assertTrue(e.getLastFailure() instanceof IllegalStateException);
      return CompletableFuture.supplyAsync(() -> "test");
    });
    Object result = Failsafe.with(fallback).get(() -> {
      throw new IllegalStateException();
    });
    assertEquals(result, "test");
  }

  public void testOfStageAsync() throws Throwable {
    Fallback<Object> fallback = Fallback.ofStageAsync(e -> CompletableFuture.completedFuture("test"));
    Object result = Failsafe.with(fallback).getAsync(() -> {
      throw new IllegalStateException();
    }).get();
    assertEquals(result, "test");
  }
}
