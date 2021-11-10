package dev.failsafe.issues;

import dev.failsafe.Fallback;
import dev.failsafe.Failsafe;
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
    Fallback<Object> fallback = Fallback.builderOfStage(e -> CompletableFuture.completedFuture("test")).withAsync().build();
    Object result = Failsafe.with(fallback).getAsync(() -> {
      throw new IllegalStateException();
    }).get();
    assertEquals(result, "test");
  }
}
