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

package com.google.inject.commands;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.TypeConverter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandRecorderTest extends TestCase {

  private CommandRecorder commandRecorder = new CommandRecorder();

  // Binder fidelity tests

  public void testAddMessageErrorCommand() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            addError("Message %s %d %s", "A", 5, "C");
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Message A 5 C", command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testAddThrowableErrorCommand() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            addError(new Exception("A"));
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("A", command.getMessage().getCause().getMessage());
            assertEquals(command.getMessage().getMessage(),
                "An exception was caught and reported. Message: A");
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testErrorsAddedWhenExceptionsAreThrown() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            install(new AbstractModule() {
              protected void configure() {
                throw new RuntimeException("Throwing RuntimeException in AbstractModule.configure().");
              }
            });

            addError("Code after the exception still gets executed");
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Throwing RuntimeException in AbstractModule.configure().",
                command.getMessage().getCause().getMessage());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Code after the exception still gets executed",
                command.getMessage().getMessage());
            return null;
          }
        }
    );
  }

  public void testBindConstantAnnotations() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(SampleAnnotation.class).to("A");
            bindConstant().annotatedWith(Names.named("Bee")).to("B");
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertEquals("A", command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("Bee")), command.getKey());
            assertEquals("B", command.getTarget().get());
            return null;
          }
        }
    );
  }

  public void testBindConstantTypes() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(Names.named("String")).to("A");
            bindConstant().annotatedWith(Names.named("int")).to(2);
            bindConstant().annotatedWith(Names.named("long")).to(3L);
            bindConstant().annotatedWith(Names.named("boolean")).to(false);
            bindConstant().annotatedWith(Names.named("double")).to(5.0d);
            bindConstant().annotatedWith(Names.named("float")).to(6.0f);
            bindConstant().annotatedWith(Names.named("short")).to((short) 7);
            bindConstant().annotatedWith(Names.named("char")).to('h');
            bindConstant().annotatedWith(Names.named("Class")).to(Iterator.class);
            bindConstant().annotatedWith(Names.named("Enum")).to(CoinSide.TAILS);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("String")), command.getKey());
            assertEquals("A", command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Integer.class, Names.named("int")), command.getKey());
            assertEquals(2, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Long.class, Names.named("long")), command.getKey());
            assertEquals(3L, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Boolean.class, Names.named("boolean")), command.getKey());
            assertEquals(false, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Double.class, Names.named("double")), command.getKey());
            assertEquals(5.0d, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Float.class, Names.named("float")), command.getKey());
            assertEquals(6.0f, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Short.class, Names.named("short")), command.getKey());
            assertEquals((short) 7, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Character.class, Names.named("char")), command.getKey());
            assertEquals('h', command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Class.class, Names.named("Class")), command.getKey());
            assertEquals(Iterator.class, command.getTarget().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(CoinSide.class, Names.named("Enum")), command.getKey());
            assertEquals(CoinSide.TAILS, command.getTarget().get());
            return null;
          }
        }
    );
  }

  public void testBindKeysNoAnnotations() {
    FailingVisitor keyChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(new TypeLiteral<String>() {
            }).toInstance("B");
            bind(Key.get(String.class)).toInstance("C");
          }
        },
        keyChecker,
        keyChecker,
        keyChecker
    );
  }

  public void testBindKeysWithAnnotationType() {
    FailingVisitor annotationChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(SampleAnnotation.class).toInstance("A");
            bind(new TypeLiteral<String>() {
            }).annotatedWith(SampleAnnotation.class).toInstance("B");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindKeysWithAnnotationInstance() {
    FailingVisitor annotationChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
        return null;
      }
    };


    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(Names.named("a")).toInstance("B");
            bind(new TypeLiteral<String>() {
            }).annotatedWith(Names.named("a")).toInstance("C");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindToProvider() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toProvider(new Provider<String>() {
              public String get() {
                return "A";
              }
            });
            bind(List.class).toProvider(ListProvider.class);
            bind(Collection.class).toProvider(Key.get(ListProvider.class));
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().getProvider().get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertNull(command.getTarget().get());
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Collection.class), command.getKey());
            assertNull(command.getTarget().get());
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey());
            return null;
          }
        }
    );
  }

  public void testBindToLinkedBinding() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(List.class).to(ArrayList.class);
            bind(Map.class).to(new TypeLiteral<HashMap<Integer, String>>() { });
            bind(Set.class).to(Key.get(TreeSet.class, SampleAnnotation.class));
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Key.get(ArrayList.class), command.getTarget().getKey());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Key.get(new TypeLiteral<HashMap<Integer, String>>() {}), command.getTarget().getKey());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertEquals(Key.get(TreeSet.class, SampleAnnotation.class), command.getTarget().getKey());
            return null;
          }
        }
    );
  }

  public void testBindToInstance() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().get());
            return null;
          }
        }
    );
  }

  public void testBindInScopes() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(List.class).to(ArrayList.class).in(Scopes.SINGLETON);
            bind(Map.class).to(HashMap.class).in(Singleton.class);
            bind(Set.class).to(TreeSet.class).asEagerSingleton();
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Scopes.SINGLETON, command.getScoping().getScope());
            assertNull(command.getScoping().getScopeAnnotation());
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Singleton.class, command.getScoping().getScopeAnnotation());
            assertNull(command.getScoping().getScope());
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertNull(command.getScoping().getScopeAnnotation());
            assertNull(command.getScoping().getScope());
            assertTrue(command.getScoping().isEagerSingleton());
            return null;
          }
        }
    );
  }

  public void testBindIntercepor() {
    final Matcher<Class> classMatcher = Matchers.subclassesOf(List.class);
    final Matcher<Object> methodMatcher = Matchers.any();
    final MethodInterceptor methodInterceptor = new MethodInterceptor() {
      public Object invoke(MethodInvocation methodInvocation) {
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindInterceptor(classMatcher, methodMatcher, methodInterceptor);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindInterceptor(BindInterceptorCommand command) {
            assertSame(classMatcher, command.getClassMatcher());
            assertSame(methodMatcher, command.getMethodMatcher());
            assertEquals(Arrays.asList(methodInterceptor), command.getInterceptors());
            return null;
          }
        }
    );
  }

  public void testBindScope() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindScope(SampleAnnotation.class, Scopes.NO_SCOPE);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindScope(BindScopeCommand command) {
            assertSame(SampleAnnotation.class, command.getAnnotationType());
            assertSame(Scopes.NO_SCOPE, command.getScope());
            return null;
          }
        }
    );
  }

  public void testConvertToTypes() {
    final TypeConverter typeConverter = new TypeConverter() {
      public Object convert(String value, TypeLiteral<?> toType) {
        return value;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            convertToTypes(Matchers.any(), typeConverter);
          }
        },

        new FailingVisitor() {
          @Override public Void visitConvertToTypes(ConvertToTypesCommand command) {
            assertSame(typeConverter, command.getTypeConverter());
            assertSame(Matchers.any(), command.getTypeMatcher());
            return null;
          }
        }
    );
  }

  public void testGetProvider() {
    commandRecorder = new CommandRecorder();

    checkModule(
        new AbstractModule() {
          protected void configure() {
            Provider<String> keyGetProvider = getProvider(Key.get(String.class, SampleAnnotation.class));
            try {
              keyGetProvider.get();
            } catch (IllegalStateException e) {
              assertEquals("This provider cannot be used until the Injector has been created.",
                  e.getMessage());
            }

            Provider<String> typeGetProvider = getProvider(String.class);
            try {
              typeGetProvider.get();
            } catch (IllegalStateException e) {
              assertEquals("This provider cannot be used until the Injector has been created.",
                  e.getMessage());
            }
          }
        },

        new FailingVisitor() {
          @Override public Void visitGetProvider(GetProviderCommand command) {
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertNull(command.getDelegate());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitGetProvider(GetProviderCommand command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertNull(command.getDelegate());
            return null;
          }
        }
    );
  }

  public void testRequestInjection() {
    final Object firstObject = new Object();
    final Object secondObject = new Object();

    checkModule(
        new AbstractModule() {
          protected void configure() {
            requestInjection(firstObject, secondObject);
          }
        },

        new FailingVisitor() {
          @Override public Void visitRequestInjection(RequestInjectionCommand command) {
            assertEquals(Arrays.asList(firstObject, secondObject), command.getInstances());
            return null;
          }
        }
    );
  }

  public void testRequestStaticInjection() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            requestStaticInjection(ArrayList.class);
          }
        },

        new FailingVisitor() {
          @Override public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
            assertEquals(Arrays.asList(ArrayList.class), command.getTypes());
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleAnnotationsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedBindingBuilder<String> abb = bind(String.class);
            abb.annotatedWith(SampleAnnotation.class);
            abb.annotatedWith(Names.named("A"));
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("More than one annotation is specified for this binding.",
                command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleTargetsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedBindingBuilder<String> abb = bind(String.class);
            abb.toInstance("A");
            abb.toInstance("B");
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Implementation is set more than once.",
                command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleScopesAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            ScopedBindingBuilder sbb = bind(List.class).to(ArrayList.class);
            sbb.in(Scopes.NO_SCOPE);
            sbb.asEagerSingleton();
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Scope is set more than once.", command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testBindConstantWithMultipleAnnotationsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedConstantBindingBuilder cbb = bindConstant();
            cbb.annotatedWith(SampleAnnotation.class).to("A");
            cbb.annotatedWith(Names.named("A"));
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("More than one annotation is specified for this binding.",
                command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  public void testBindConstantWithMultipleTargetsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            ConstantBindingBuilder cbb = bindConstant().annotatedWith(SampleAnnotation.class);
            cbb.to("A");
            cbb.to("B");
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessage(AddMessageCommand command) {
            assertEquals("Constant value is set more than once.",
                command.getMessage().getMessage());
            assertNull(command.getMessage().getCause());
            assertTrue(command.getMessage().getInjectionPoints().isEmpty());
            assertContains(command.getMessage().getSource(), "CommandRecorderTest.java");
            return null;
          }
        }
    );
  }

  // Business logic tests

  public void testModulesAreInstalledAtMostOnce() {
    final AtomicInteger aConfigureCount = new AtomicInteger(0);
    final Module a = new AbstractModule() {
      public void configure() {
        aConfigureCount.incrementAndGet();
      }
    };

    commandRecorder.recordCommands(a, a);
    assertEquals(1, aConfigureCount.get());

    aConfigureCount.set(0);
    Module b = new AbstractModule() {
      protected void configure() {
        install(a);
        install(a);
      }
    };

    commandRecorder.recordCommands(b);
    assertEquals(1, aConfigureCount.get());
  }


  /**
   * Ensures the module performs the commands consistent with {@code visitors}.
   */
  protected void checkModule(Module module, Command.Visitor<?>... visitors) {
    List<Command> commands = commandRecorder.recordCommands(module);

    assertEquals(commands.size(), visitors.length);

    for (int i = 0; i < visitors.length; i++) {
      Command.Visitor<?> visitor = visitors[i];
      Command command = commands.get(i);
      assertContains(command.getSource().toString(), "CommandRecorderTest.java");
      command.acceptVisitor(visitor);
    }
  }

  private static class ListProvider implements Provider<List> {
    public List get() {
      return new ArrayList();
    }
  }

  private static class FailingVisitor implements Command.Visitor<Void> {
    public Void visitAddMessage(AddMessageCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindInterceptor(BindInterceptorCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindScope(BindScopeCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitRequestInjection(RequestInjectionCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindConstant(BindConstantCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitConvertToTypes(ConvertToTypesCommand command) {
      throw new AssertionFailedError();
    }

    public <T> Void visitBind(BindCommand<T> command) {
      throw new AssertionFailedError();
    }

    public Void visitGetProvider(GetProviderCommand command) {
      throw new AssertionFailedError();
    }
  }

  @Retention(RUNTIME)
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  public @interface SampleAnnotation { }

  public enum CoinSide { HEADS, TAILS }
}