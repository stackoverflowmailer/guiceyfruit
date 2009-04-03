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


package com.google.inject;

import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.util.Providers;
import java.util.Set;

class InstanceBindingImpl<T> extends BindingImpl<T> {

  final T instance;
  final Provider<T> provider;
  final ImmutableSet<InjectionPoint> injectionPoints;

  InstanceBindingImpl(InjectorImpl injector, Key<T> key, Object source,
      InternalFactory<? extends T> internalFactory, Set<InjectionPoint> injectionPoints,
      T instance) {
    super(injector, key, source, internalFactory, Scopes.NO_SCOPE, LoadStrategy.EAGER);
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
    this.instance = instance;
    this.provider = Providers.of(instance);
  }

  @Override public Provider<T> getProvider() {
    return this.provider;
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visitInstance(instance, injectionPoints);
  }

  @Override public String toString() {
    return new ToStringBuilder(Binding.class)
        .add("key", key)
        .add("instance", instance)
        .add("source", source)
        .toString();
  }
}