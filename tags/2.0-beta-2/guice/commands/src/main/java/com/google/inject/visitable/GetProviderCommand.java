/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.visitable;

import com.google.inject.Key;

/**
 * Immutable snapshot of a request for a provider.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class GetProviderCommand<T> implements Command {
  private final Key<T> key;
  private final EarlyRequestsProvider earlyRequestsProvider;

  GetProviderCommand(Key<T> key, EarlyRequestsProvider earlyRequestsProvider) {
    this.key = key;
    this.earlyRequestsProvider = earlyRequestsProvider;
  }

  public Key<T> getKey() {
    return key;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitGetProvider(this);
  }

  public EarlyRequestsProvider getEarlyRequestsProvider() {
    return earlyRequestsProvider;
  }
}
