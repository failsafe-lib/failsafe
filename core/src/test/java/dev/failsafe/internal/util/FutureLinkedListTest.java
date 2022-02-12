/*
 * Copyright 2021 the original author or authors.
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
package dev.failsafe.internal.util;

import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
public class FutureLinkedListTest {
  public void testAdd() {
    // Given
    FutureLinkedList list = new FutureLinkedList();

    // When
    CompletableFuture<Void> f1 = list.add();
    CompletableFuture<Void> f2 = list.add();
    CompletableFuture<Void> f3 = list.add();

    // Then
    assertNull(list.head.previous);
    assertEquals(list.head.future, f1);
    assertEquals(list.tail.future, f3);
    assertEquals(list.head.next.future, f2);
    assertEquals(list.tail.previous.future, f2);
  }

  public void testPollFirst() {
    // Given
    FutureLinkedList list = new FutureLinkedList();

    // When / Then
    assertNull(list.pollFirst());

    // Given
    CompletableFuture<Void> f1 = list.add();
    CompletableFuture<Void> f2 = list.add();
    CompletableFuture<Void> f3 = list.add();

    // When / Then
    assertEquals(list.pollFirst(), f1);
    assertEquals(list.head.future, f2);
    assertNull(list.head.previous);
    assertEquals(list.pollFirst(), f2);
    assertEquals(list.head.future, f3);
    assertNull(list.head.previous);
    assertEquals(list.pollFirst(), f3);
    assertNull(list.head);
    assertNull(list.pollFirst());
  }

  public void testRemove() {
    // Given
    FutureLinkedList list = new FutureLinkedList();
    CompletableFuture<Void> f1 = list.add();
    CompletableFuture<Void> f2 = list.add();
    CompletableFuture<Void> f3 = list.add();

    // When / Then
    f1.complete(null);
    assertEquals(list.head.future, f2);
    assertNull(list.head.previous);
    assertEquals(list.tail.previous.future, f2);

    f2.complete(null);
    assertEquals(list.head.future, f3);
    assertEquals(list.tail.future, f3);
    assertNull(list.head.previous);
    assertNull(list.head.next);

    f3.complete(null);
    assertNull(list.head);
    assertNull(list.tail);
  }
}
