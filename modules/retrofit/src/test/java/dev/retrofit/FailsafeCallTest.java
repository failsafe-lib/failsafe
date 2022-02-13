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
package dev.retrofit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.failsafe.*;
import dev.failsafe.retrofit.FailsafeCall;
import dev.retrofit.TestService.User;
import dev.retrofit.testing.RetrofitTesting;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class FailsafeCallTest extends RetrofitTesting {
  public static final String URL = "http://localhost:8080";

  WireMockServer server;
  User fooUser = new User("foo");
  Gson gson = new GsonBuilder().create();
  TestService service = new Retrofit.Builder().baseUrl(URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(TestService.class);

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
    mockResponse(200, fooUser);
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(RetryPolicy.ofDefaults());
    Call<User> call = service.testUser();

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, 200, fooUser);
    assertCalled("/test", 2);
  }

  public void testRetryPolicyOn400() {
    // Given
    mockResponse(400, fooUser);
    RetryPolicy<Response<User>> retryPolicy = RetryPolicy.<Response<User>>builder()
      .handleResultIf(r -> r.code() == 400)
      .build();
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(retryPolicy);
    Call<User> call = service.testUser();

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 400, null); // Retrofit puts the response for a 400 in the error body
    assertCalled("/test", 6);
  }

  public void testRetryPolicyOnResult() {
    // Given
    User bad = new User("bad");
    mockResponse(200, bad);
    RetryPolicy<Response<User>> retryPolicy = RetryPolicy.<Response<User>>builder()
      .handleResultIf(r -> bad.equals(r.body()))
      .build();
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(retryPolicy);
    Call<User> call = service.testUser();

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 200, bad);
    assertCalled("/test", 6);
  }

  public void testRetryPolicyFallback() {
    // Given
    mockResponse(400, fooUser);
    User fallbackUser = new User("fallback");
    Fallback<Response<User>> fallback = Fallback.<Response<User>>builder(r -> {
      return Response.success(200, fallbackUser);
    }).handleResultIf(r -> r.code() == 400).build();
    RetryPolicy<Response<User>> retryPolicy = RetryPolicy.<Response<User>>builder()
      .handleResultIf(r -> r.code() == 400)
      .build();
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(fallback, retryPolicy);
    Call<User> call = service.testUser();

    // When / Then
    testRequest(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
    }, 200, fallbackUser);
    assertCalled("/test", 6);
  }

  /**
   * Asserts that an open circuit breaker prevents executions from occurring, even with outer retries.
   */
  public void testCircuitBreaker() {
    // Given
    mockResponse(200, fooUser);
    CircuitBreaker<Response<User>> breaker = CircuitBreaker.ofDefaults();
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(RetryPolicy.ofDefaults(), breaker);
    Call<User> call = service.testUser();
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
    FailsafeExecutor<Response<User>> failsafe = Failsafe.with(Timeout.of(Duration.ofMillis(100)));
    Call<User> call = service.testUser();

    // When / Then
    testFailure(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, TimeoutExceededException.class);
    assertCalled("/test", 2);
  }

  public void testIOException() {
    server.stop();
    FailsafeExecutor<Response<User>> failsafe = Failsafe.none();
    Call<User> call = service.testUser();

    testFailure(failsafe, call, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, java.net.ConnectException.class);
  }

  public void testCancel() {
    // Given
    mockDelayedResponse(200, "foo", 1000);
    FailsafeExecutor<Response<User>> failsafe = Failsafe.none();
    Call<User> call = service.testUser();

    // When / Then Sync
    FailsafeCall<User> failsafeCall = FailsafeCall.of(call, failsafe);
    runInThread(() -> {
      sleep(150);
      failsafeCall.cancel();
    });
    assertThrows(failsafeCall::execute, IOException.class);
    assertTrue(call.isCanceled());
    assertTrue(failsafeCall.isCancelled());

    // When / Then Async
    Call<User> call2 = call.clone();
    FailsafeCall<User> failsafeCall2 = FailsafeCall.of(call2, failsafe);
    runInThread(() -> {
      sleep(150);
      failsafeCall2.cancel();
    });
    assertThrows(() -> failsafeCall2.executeAsync().get(), CancellationException.class);
    assertTrue(call2.isCanceled());
    assertTrue(failsafeCall2.isCancelled());
    assertCalled("/test", 2);
  }

  private void mockResponse(int responseCode, User body) {
    stubFor(get(urlPathEqualTo("/test")).willReturn(
      aResponse().withStatus(responseCode).withHeader("Content-Type", "application/json").withBody(gson.toJson(body))));
  }

  private void mockDelayedResponse(int responseCode, String body, int delayMillis) {
    stubFor(get(urlEqualTo("/test")).willReturn(
      aResponse().withStatus(responseCode).withFixedDelay(delayMillis).withBody(body)));
  }

  private void assertCalled(String url, int times) {
    verify(times, getRequestedFor(urlPathEqualTo(url)));
  }
}
