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

import com.google.inject.internal.Errors;
import com.google.inject.spi.Element;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.InjectionRequest;
import com.google.inject.spi.InterceptorBinding;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.StaticInjectionRequest;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.ConstructorInterceptorBinding;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base class for creating an injector from module elements.
 *
 * <p>Extending classes must return {@code true} from any overridden
 * {@code visit*()} methods, in order for the element processor to remove the
 * handled element.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
abstract class AbstractProcessor implements ElementVisitor<Boolean> {

  protected Errors errors;

  protected AbstractProcessor(Errors errors) {
    this.errors = errors;
  }

  public void processCommands(List<Element> elements) {
    Errors errorsAnyElement = this.errors;
    try {
      for (Iterator<Element> i = elements.iterator(); i.hasNext(); ) {
        Element element = i.next();
        this.errors = errorsAnyElement.withSource(element.getSource());
        Boolean allDone = element.acceptVisitor(this);
        if (allDone) {
          i.remove();
        }
      }
    } finally {
      this.errors = errorsAnyElement;
    }
  }

  public Boolean visitMessage(Message message) {
    return false;
  }

  public Boolean visitInterceptorBinding(InterceptorBinding interceptorBinding) {
    return false;
  }

  public Boolean visitConstructorInterceptorBinding(ConstructorInterceptorBinding constructorInterceptorBinding) {
    return false;
  }

  public Boolean visitScopeBinding(ScopeBinding scopeBinding) {
    return false;
  }

  public Boolean visitInjectionRequest(InjectionRequest injectionRequest) {
    return false;
  }

  public Boolean visitStaticInjectionRequest(StaticInjectionRequest staticInjectionRequest) {
    return false;
  }

  public Boolean visitTypeConverterBinding(TypeConverterBinding typeConverterBinding) {
    return false;
  }

  public <T> Boolean visitBinding(Binding<T> binding) {
    return false;
  }

  public <T> Boolean visitProviderLookup(ProviderLookup<T> providerLookup) {
    return false;
  }
}
