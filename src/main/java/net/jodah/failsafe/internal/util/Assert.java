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
package net.jodah.failsafe.internal.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Assertion utilities.
 *
 * @author Jonathan Halterman
 */
public final class Assert {
  private Assert() {
  }

  public static void isTrue(boolean expression, String errorMessageFormat, Object... args) {
    if (!expression)
      throw new IllegalArgumentException(String.format(errorMessageFormat, args));
  }

  public static <T> T notNull(T reference, String parameterName) {
    if (reference == null)
      throw new NullPointerException(parameterName + " cannot be null");
    return reference;
  }

  public static void state(boolean expression, String errorMessageFormat, Object... args) {
    if (!expression)
      throw new IllegalStateException(String.format(errorMessageFormat, args));
  }

  static volatile long lastTimestamp;

  public static void log(Object object, String msg, Object... args) {
    Class<?> clazz = object instanceof Class ? (Class<?>) object : object.getClass();
    log(clazz.getSimpleName() + " " + String.format(msg, args));
  }

  public static void log(Class<?> clazz, String msg) {
    log(clazz.getSimpleName() + " " + msg);
  }

  public static void log(String msg) {
    long currentTimestamp = System.currentTimeMillis();
    if (lastTimestamp + 80 < currentTimestamp)
      System.out.printf("%n%n");
    lastTimestamp = currentTimestamp;
    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss.SSS"));
    StringBuilder threadName = new StringBuilder(Thread.currentThread().getName());
    for (int i = threadName.length(); i < 35; i++)
      threadName.append(" ");
    System.out.println("[" + time + "] " + "[" + threadName + "] " + msg);
  }
}