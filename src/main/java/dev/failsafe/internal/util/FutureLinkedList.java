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
package dev.failsafe.internal.util;

import java.util.concurrent.CompletableFuture;

/**
 * A LinkedList of CompletableFutures that removes a future from the list when it's completed.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @author Jonathan Halterman
 */
public final class FutureLinkedList {
  Node head;
  Node tail;

  static class Node {
    Node previous;
    Node next;
    CompletableFuture<Void> future;
  }

  /**
   * Adds a new CompletableFuture to the list and returns it. The returned future will be removed from the list when
   * it's completed.
   */
  public synchronized CompletableFuture<Void> add() {
    Node node = new Node();
    node.future = new CompletableFuture<>();
    node.future.whenComplete((result, error) -> remove(node));

    if (head == null)
      head = tail = node;
    else {
      tail.next = node;
      node.previous = tail;
      tail = node;
    }
    return node.future;
  }

  /**
   * Returns and removes the first future in the list, else returns {@code null} if the list is empty.
   */
  public synchronized CompletableFuture<Void> pollFirst() {
    Node previousHead = head;
    if (head != null) {
      head = head.next;
      if (head != null)
        head.previous = null;
    }
    return previousHead == null ? null : previousHead.future;
  }

  private synchronized void remove(Node node) {
    if (node.previous != null)
      node.previous.next = node.next;
    if (node.next != null)
      node.next.previous = node.previous;
    if (head == node)
      head = node.next;
    if (tail == node)
      tail = node.previous;
  }
}
