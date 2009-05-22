/**
 * Copyright (C) 2006 Google Inc.
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
 * limitations under the License.
 */

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import com.google.inject.internal.Errors;
import com.google.inject.internal.SourceProvider;
import java.io.Serializable;
import java.util.List;

/**
 * An error message and the context in which it occured. Messages are usually created internally by
 * Guice and its extensions. Messages can be created explicitly in a module using {@link
 * com.google.inject.Binder#addError(Throwable) addError()} statements:
 * <pre>
 *     try {
 *       bindPropertiesFromFile();
 *     } catch (IOException e) {
 *       addError(e);
 *     }</pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class Message implements Serializable, Element {
  private final String source;
  private final String message;
  private final List<InjectionPoint> injectionPoints;
  private final Throwable cause;

  public Message(Object source, String message, List<InjectionPoint> injectionPoints,
      Throwable cause) {
    this.source = Errors.sourceToString(source);
    this.message = checkNotNull(message, "message");
    this.injectionPoints = ImmutableList.copyOf(injectionPoints);
    this.cause = cause;
  }

  public Message(Object source, Throwable throwable) {
    this(source, "An exception was caught and reported. Message: " + throwable.getMessage(),
        ImmutableList.<InjectionPoint>of(), throwable);
  }

  public Message(Object source, String message) {
    this(source, message, ImmutableList.<InjectionPoint>of(), null);
  }

  public Message(String message) {
    this(SourceProvider.UNKNOWN_SOURCE, message, ImmutableList.<InjectionPoint>of(), null);
  }

  public String getSource() {
    return source;
  }

  /**
   * Gets the error message text.
   */
  public String getMessage() {
    return message;
  }

  public List<InjectionPoint> getInjectionPoints() {
    return injectionPoints;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visitMessage(this);
  }

  /**
   * Returns the throwable that caused this message, or {@code null} if this
   * message was not caused by a throwable.
   */
  public Throwable getCause() {
    return cause;
  }

  @Override public String toString() {
    return source + " " + message;
  }

  @Override public int hashCode() {
    return source.hashCode() * 31 + message.hashCode();
  }

  @Override public boolean equals(Object o) {
    if (!(o instanceof Message)) {
      return false;
    }
    Message e = (Message) o;
    return source.equals(e.source) && message.equals(e.message);
  }
}