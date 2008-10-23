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

import com.google.inject.*;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.TypeConverter;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Records commands executed by a module so they can be inspected or
 * {@link CommandReplayer replayed}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class CommandRecorder {
  private final Stage stage = Stage.DEVELOPMENT;
  private final EarlyRequestsProvider earlyRequestsProvider;

  /**
   * @param earlyRequestsProvider satisfies requests to
   *     {@link Binder#getProvider} at module execution time. For modules that
   *     will be used to create an injector, use {@link FutureInjector}.
   */
  public CommandRecorder(EarlyRequestsProvider earlyRequestsProvider) {
    this.earlyRequestsProvider = earlyRequestsProvider;
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public List<Command> recordCommands(Module... modules) {
    return recordCommands(Arrays.asList(modules));
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public List<Command> recordCommands(Iterable<Module> modules) {
    RecordingBinder binder = new RecordingBinder();
    for (Module module : modules) {
      module.configure(binder);
    }
    return Collections.unmodifiableList(binder.commands);
  }

  private class RecordingBinder implements Binder {
    private final List<Command> commands = new ArrayList<Command>();

    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      commands.add(new BindInterceptorCommand(classMatcher, methodMatcher, interceptors));
    }

    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      commands.add(new BindScopeCommand(annotationType, scope));
    }

    public void requestStaticInjection(Class<?>... types) {
      commands.add(new RequestStaticInjectionCommand(types));
    }

    public void install(Module module) {
      module.configure(this);
    }

    public Stage currentStage() {
      return stage;
    }

    public void addError(String message, Object... arguments) {
      commands.add(new AddMessageErrorCommand(message, arguments));
    }

    public void addError(Throwable t) {
      commands.add(new AddThrowableErrorCommand(t));
    }

    public <T> BindCommand<T>.BindingBuilder bind(Key<T> key) {
      BindCommand<T> bindCommand = new BindCommand<T>(key);
      commands.add(bindCommand);
      return bindCommand.bindingBuilder();
    }

    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    public AnnotatedConstantBindingBuilder bindConstant() {
      BindConstantCommand bindConstantCommand = new BindConstantCommand();
      commands.add(bindConstantCommand);
      return bindConstantCommand.bindingBuilder();
    }

    public <T> Provider<T> getProvider(final Key<T> key) {
      commands.add(new GetProviderCommand<T>(key, earlyRequestsProvider));
      return new Provider<T>() {
        public T get() {
          return earlyRequestsProvider.get(key);
        }
      };
    }

    public <T> Provider<T> getProvider(Class<T> type) {
      return getProvider(Key.get(type));
    }

    public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
                               TypeConverter converter) {
      commands.add(new ConvertToTypesCommand(typeMatcher, converter));
    }
  }
}
