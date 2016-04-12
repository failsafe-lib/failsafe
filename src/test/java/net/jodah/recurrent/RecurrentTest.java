package net.jodah.recurrent;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.ScheduledExecutorService;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import net.jodah.recurrent.util.concurrent.Scheduler;

@Test
public class RecurrentTest {
  public void testWith() {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    Scheduler scheduler = Mockito.mock(Scheduler.class);

    assertTrue(Recurrent.with(new RetryPolicy()) instanceof SyncRecurrent);
    assertTrue(Recurrent.with(new RetryPolicy(), executor) instanceof AsyncRecurrent);
    assertTrue(Recurrent.with(new RetryPolicy(), scheduler) instanceof AsyncRecurrent);
  }
}
