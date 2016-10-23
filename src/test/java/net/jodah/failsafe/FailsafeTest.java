/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.ScheduledExecutorService;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import net.jodah.failsafe.util.concurrent.Scheduler;

@Test
public class FailsafeTest {
  public void testWithExecutor() {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    Scheduler scheduler = Mockito.mock(Scheduler.class);

    assertTrue(Failsafe.with(new RetryPolicy()) instanceof SyncFailsafe);
    assertTrue(Failsafe.with(new RetryPolicy()).with(executor) instanceof AsyncFailsafe);
    assertTrue(Failsafe.with(new RetryPolicy()).with(scheduler) instanceof AsyncFailsafe);
  }
}
