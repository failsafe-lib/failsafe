/*
 * Copyright 2022 the original author or authors.
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
package dev.failsafe.okhttp;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.failsafe.*;
import dev.failsafe.okhttp.testing.OkHttpTesting;
import okhttp3.Call;
import okhttp3.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class FailsafeCallTest extends OkHttpTesting {
  public static final String URL = "http://localhost:8080";

  WireMockServer server;
  OkHttpClient client = new OkHttpClient.Builder().build();

  @BeforeMethod
  protected void beforeMethod() {
    server = new WireMockServer();
    server.start();
  }

  @AfterMethod
  protected void afterMethod() {
    server.stop();
  }

  public void testSuccess() {
    // Given
    mockResponse(200, "foo");
    FailsafeExecutor<Response> failsafe = Failsafe.with(RetryPolicy.ofDefaults());
    Call call = callFor("/test");

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, 200, "foo");
    assertCalled("/test", 2);
  }

  public void testRetryPolicyOn400() {
    // Given
    mockResponse(400, "foo");
    RetryPolicy<Response> retryPolicy = RetryPolicy.<Response>builder().handleResultIf(r -> r.code() == 400).build();
    FailsafeExecutor<Response> failsafe = Failsafe.with(retryPolicy);
    Call call = callFor("/test");

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 400, "foo");
    assertCalled("/test", 6);
  }

  public void testRetryPolicyOnResult() {
    // Given
    mockResponse(200, "bad");
    RetryPolicy<Response> retryPolicy = RetryPolicy.<Response>builder()
      .handleResultIf(r -> "bad".equals(r.peekBody(Long.MAX_VALUE).string()))
      .build();
    FailsafeExecutor<Response> failsafe = Failsafe.with(retryPolicy);
    Call call = callFor("/test");

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 200, "bad");
    assertCalled("/test", 6);
  }

  public void testRetryPolicyFallback() {
    // Given
    mockResponse(400, "foo");
    Fallback<Response> fallback = Fallback.<Response>builder(r -> {
      Response response = r.getLastResult();
      ResponseBody body = ResponseBody.create("fallback", response.body().contentType());
      return response.newBuilder().code(200).body(body).build();
    }).handleResultIf(r -> r.code() == 400).build();
    RetryPolicy<Response> retryPolicy = RetryPolicy.<Response>builder().handleResultIf(r -> r.code() == 400).build();
    FailsafeExecutor<Response> failsafe = Failsafe.with(fallback, retryPolicy);
    Call call = callFor("/test");

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 200, "fallback");
    assertCalled("/test", 6);
  }

  /**
   * Asserts that an open circuit breaker prevents executions from occurring, even with outer retries.
   */
  public void testCircuitBreaker() {
    // Given
    mockResponse(200, "foo");
    CircuitBreaker<Response> breaker = CircuitBreaker.ofDefaults();
    FailsafeExecutor<Response> failsafe = Failsafe.with(RetryPolicy.ofDefaults(), breaker);
    Call call = callFor("/test");
    breaker.open();

    // When / Then
    testFailure(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 0);
    }, CircuitBreakerOpenException.class);
    assertCalled("/test", 0);
  }

  public void testTimeout() {
    // Given
    mockDelayedResponse(200, "foo", 1000);
    FailsafeExecutor<Response> failsafe = Failsafe.with(Timeout.of(Duration.ofMillis(100)));
    Call call = callFor("/test");

    // When / Then
    testFailure(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, TimeoutExceededException.class);
    assertCalled("/test", 2);
  }

  public void testIOException() {
    server.stop();
    FailsafeExecutor<Response> failsafe = Failsafe.none();
    Call call = callFor("/test");

    testFailure(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, java.net.ConnectException.class);
  }

  public void testCancel() {
    // Given
    mockDelayedResponse(200, "foo", 1000);
    FailsafeExecutor<Response> failsafe = Failsafe.none();
    Call call = callFor("/test");

    // When / Then Sync
    FailsafeCall failsafeCall = FailsafeCall.with(failsafe).compose(call);
    runInThread(() -> {
      sleep(150);
      failsafeCall.cancel();
    });
    assertThrows(failsafeCall::execute, IOException.class);
    assertTrue(call.isCanceled());
    assertTrue(failsafeCall.isCancelled());

    // When / Then Async
    Call call2 = call.clone();
    FailsafeCall failsafeCall2 = FailsafeCall.with(failsafe).compose(call2);
    runInThread(() -> {
      sleep(150);
      failsafeCall2.cancel();
    });
    assertThrows(() -> failsafeCall2.executeAsync().get(), CancellationException.class);
    assertTrue(call2.isCanceled());
    assertTrue(failsafeCall2.isCancelled());
    assertCalled("/test", 2);
  }

  public void testCancelViaFuture() {
    // Given
    mockDelayedResponse(200, "foo", 1000);
    FailsafeExecutor<Response> failsafe = Failsafe.none();
    Call call = callFor("/test");

    // When / Then Async
    FailsafeCall failsafeCall = FailsafeCall.with(failsafe).compose(call);
    Future<Response> future = failsafeCall.executeAsync();
    sleep(150);
    future.cancel(false);
    assertThrows(future::get, CancellationException.class);
    assertTrue(call.isCanceled());
    assertTrue(failsafeCall.isCancelled());
    assertCalled("/test", 1);
  }

  private Call callFor(String path) {
    return client.newCall(new Request.Builder().url(URL + path).build());
  }

  private void mockResponse(int responseCode, String body) {
    stubFor(get(urlPathEqualTo("/test")).willReturn(
      aResponse().withStatus(responseCode).withHeader("Content-Type", "text/plain").withBody(body)));
  }

  private void mockDelayedResponse(int responseCode, String body, int delayMillis) {
    stubFor(get(urlEqualTo("/test")).willReturn(
      aResponse().withStatus(responseCode).withFixedDelay(delayMillis).withBody(body)));
  }

  private void assertCalled(String url, int times) {
    verify(times, getRequestedFor(urlPathEqualTo(url)));
  }
}
