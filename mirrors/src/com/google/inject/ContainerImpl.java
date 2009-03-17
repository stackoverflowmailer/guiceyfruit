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

package com.google.inject;

import com.google.inject.util.ReferenceCache;
import com.google.inject.util.Strings;

import net.sf.cglib.reflect.FastMethod;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Default {@link Container} implementation.
 *
 * @see ContainerBuilder
 * @author crazybob@google.com (Bob Lee)
 */
class ContainerImpl implements Container {

  /**
   * Maps between primitive types and their wrappers and vice versa.
   */
  private static final Map<Class<?>, Class<?>> PRIMITIVE_COUNTERPARTS;
  static {
    Map<Class<?>, Class<?>> primitiveToWrapper =
        new HashMap<Class<?>, Class<?>>() {{
          put(int.class, Integer.class);
          put(long.class, Long.class);
          put(boolean.class, Boolean.class);
          put(byte.class, Byte.class);
          put(short.class, Short.class);
          put(float.class, Float.class);
          put(double.class, Double.class);
          put(char.class, Character.class);
        }};

    Map<Class<?>, Class<?>> counterparts = new HashMap<Class<?>, Class<?>>();
    for (Map.Entry<Class<?>, Class<?>> entry : primitiveToWrapper.entrySet()) {
      Class<?> key = entry.getKey();
      Class<?> value = entry.getValue();
      counterparts.put(key, value);
      counterparts.put(value, key);
    }

    PRIMITIVE_COUNTERPARTS = Collections.unmodifiableMap(counterparts);
  }

  private static final Map<Class<?>, Converter<?>> PRIMITIVE_CONVERTERS =
      new PrimitiveConverters();

  final Map<Key<?>, InternalFactory<?>> factories;

  ErrorHandler errorHandler = new InvalidErrorHandler();

  ContainerImpl(Map<Key<?>, InternalFactory<?>> factories) {
    this.factories = factories;
  }

  void setErrorHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  ErrorHandler getErrorHandler() {
    return errorHandler;
  }

  @SuppressWarnings("unchecked")
  <T> InternalFactory<? extends T> getFactory(Member member, Key<T> key) {
    // Do we have a factory for the specified type and name?
    InternalFactory<T> internalFactory =
        (InternalFactory<T>) factories.get(key);
    if (internalFactory != null) {
      return internalFactory;
    }

    // Handle cases where T is a Factory<?>.
    if (key.getType().getRawType().equals(Factory.class)) {
      Type factoryType = key.getType().getType();
      if (!(factoryType instanceof ParameterizedType)) {
        return null;
      }
      Type entryType =
          ((ParameterizedType) factoryType).getActualTypeArguments()[0];

      try {
        final Factory<?> factory =
            getFactory(Key.get(entryType, key.getName()));
        return new InternalFactory<T>() {
          @SuppressWarnings({"unchecked"})
          public T get(InternalContext context) {
            return (T) factory;
          }
        };
      } catch (ConfigurationException e) {
        errorHandler.handle(ErrorMessage.MISSING_BINDING, member, key);
        return invalidFactory();
      }
    }

    // Auto[un]box primitives.
    Class<?> primitiveCounterpart =
        PRIMITIVE_COUNTERPARTS.get(key.getType().getRawType());
    if (primitiveCounterpart != null) {
      internalFactory = (InternalFactory<T>) factories.get(
          Key.get(primitiveCounterpart, key.getName()));
      if (internalFactory != null) {
        return internalFactory;
      }
    }

    // Do we have a constant String factory of the same name?
    InternalFactory<String> stringFactory =
        (InternalFactory<String>) factories.get(
            Key.get(String.class, key.getName()));
    if (stringFactory == null
        || !(stringFactory instanceof ConstantFactory)) {
      return null;
    }

    Class<?> type = key.getRawType();

    // We don't need do pass in an InternalContext because we know this is
    // a ConstantFactory which will not use it.
    String value = stringFactory.get(null);

    // TODO: Generalize everything below here and enable users to plug in
    // their own converters.

    // Do we need a primitive?
    Converter<T> converter = (Converter<T>) PRIMITIVE_CONVERTERS.get(type);
    if (converter != null) {
      try {
        T t = converter.convert(member, key, value);
        return new ConstantFactory<T>(t);
      } catch (ConstantConversionException e) {
        errorHandler.handle(e);
        return null;
      }
    }

    // Do we need an enum?
    if (Enum.class.isAssignableFrom(type)) {
      T t = null;
      try {
        t = (T) Enum.valueOf((Class) type, value);
      } catch (IllegalArgumentException e) {
        errorHandler.handle(
            ConstantConversionException.createMessage(
                value, key, member, e.toString()));
        return invalidFactory();
      }
      return new ConstantFactory<T>(t);
    }

    // Do we need a class?
    if (type == Class.class) {
      try {
        return new ConstantFactory<T>((T) Class.forName(value));
      } catch (ClassNotFoundException e) {
        errorHandler.handle(
            ConstantConversionException.createMessage(
                value, key, member, e.toString()));
        return invalidFactory();
      }
    }

    return null;
  }

  boolean isConstantType(Class<?> type) {
    return PRIMITIVE_CONVERTERS.containsKey(type)
        || Enum.class.isAssignableFrom(type)
        || type == Class.class;
  }

  /**
   * Field and method injectors.
   */
  final Map<Class<?>, List<Injector>> injectors =
      new ReferenceCache<Class<?>, List<Injector>>() {
        protected List<Injector> create(Class<?> key) {
          if (key.isInterface()) {
            errorHandler.handle(ErrorMessage.CANNOT_INJECT_INTERFACE, key);
            return Collections.emptyList();
          }

          List<Injector> injectors = new ArrayList<Injector>();
          addInjectors(key, injectors);
          return injectors;
        }
      };

  /**
   * Recursively adds injectors for fields and methods from the given class to
   * the given list. Injects parent classes before sub classes.
   */
  void addInjectors(Class clazz, List<Injector> injectors) {
    if (clazz == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectors(clazz.getSuperclass(), injectors);

    // TODO (crazybob): Filter out overridden members.
    addInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
    addInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
  }

  void injectStatics(List<Class<?>> staticInjections) {
    final List<Injector> injectors = new ArrayList<Injector>();

    for (Class<?> clazz : staticInjections) {
      addInjectorsForFields(clazz.getDeclaredFields(), true, injectors);
      addInjectorsForMethods(clazz.getDeclaredMethods(), true, injectors);
    }

    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        for (Injector injector : injectors) {
          injector.inject(context, null);
        }
        return null;
      }
    });
  }

  void addInjectorsForMethods(Method[] methods, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
        new InjectorFactory<Method>() {
          public Injector create(ContainerImpl container, Method method,
              String name) throws MissingDependencyException {
            return new MethodInjector(container, method, name);
          }
        });
  }

  void addInjectorsForFields(Field[] fields, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
        new InjectorFactory<Field>() {
          public Injector create(ContainerImpl container, Field field,
              String name) throws MissingDependencyException {
            return new FieldInjector(container, field, name);
          }
        });
  }

  <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      List<M> members, boolean statics, List<Injector> injectors,
      InjectorFactory<M> injectorFactory) {
    for (M member : members) {
      if (isStatic(member) == statics) {
        Inject inject = member.getAnnotation(Inject.class);
        if (inject != null) {
          try {
            injectors.add(injectorFactory.create(this, member, inject.value()));
          } catch (MissingDependencyException e) {
            if (inject.required()) {
              // TODO: Report errors for more than one parameter per member.
              e.handle(errorHandler);
            }
          }
        }
      }
    }
  }

  interface InjectorFactory<M extends Member & AnnotatedElement> {
    Injector create(ContainerImpl container, M member, String name)
        throws MissingDependencyException;
  }

  private boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  static class FieldInjector implements Injector {

    final Field field;
    final InternalFactory<?> factory;
    final ExternalContext<?> externalContext;

    public FieldInjector(ContainerImpl container, Field field, String name)
        throws MissingDependencyException {
      this.field = field;

      // Ewwwww...
      field.setAccessible(true);

      Key<?> key = Key.get(field.getGenericType(), name);
      factory = container.getFactory(field, key);
      if (factory == null) {
        throw new MissingDependencyException(key, field);
      }

      this.externalContext = ExternalContext.newInstance(field, key, container);
    }

    public void inject(InternalContext context, Object o) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        field.set(o, factory.get(context));
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      } finally {
        context.setExternalContext(previous);
      }
    }
  }

  /**
   * Gets parameter injectors.
   *
   * @param member to which the parameters belong
   * @param annotations on the parameters
   * @param parameterTypes parameter types
   * @return injections
   */
  <M extends AccessibleObject & Member> ParameterInjector<?>[]
      getParametersInjectors(M member,
      Annotation[][] annotations, Type[] parameterTypes, String defaultName)
      throws MissingDependencyException {
    boolean defaultNameOverridden = !defaultName.equals(Key.DEFAULT_NAME);

    // We only carry over the name from the member level annotation to the
    // parameters if there's only one parameter.
    if (parameterTypes.length != 1 && defaultNameOverridden) {
      errorHandler.handle(
          ErrorMessage.NAME_ON_MEMBER_WITH_MULTIPLE_PARAMS, member);
    }

    List<ParameterInjector<?>> parameterInjectors =
        new ArrayList<ParameterInjector<?>>();

    Iterator<Annotation[]> annotationsIterator =
        Arrays.asList(annotations).iterator();
    for (Type parameterType : parameterTypes) {
      Inject annotation = findInject(annotationsIterator.next());

      String name;
      if (defaultNameOverridden) {
        name = defaultName;
        if (annotation != null) {
          errorHandler.handle(
              ErrorMessage.NAME_ON_MEMBER_AND_PARAMETER, member);
        }
      } else {
        name = annotation == null ? defaultName : annotation.value();
      }

      Key<?> key = Key.get(parameterType, name);
      parameterInjectors.add(createParameterInjector(key, member));
    }

    return toArray(parameterInjectors);
  }

  <T> ParameterInjector<T> createParameterInjector(
      Key<T> key, Member member) throws MissingDependencyException {
    InternalFactory<? extends T> factory = getFactory(member, key);
    if (factory == null) {
      throw new MissingDependencyException(key, member);
    }

    ExternalContext<T> externalContext =
        ExternalContext.newInstance(member, key, this);
    return new ParameterInjector<T>(externalContext, factory);
  }

  @SuppressWarnings("unchecked")
  private ParameterInjector<?>[] toArray(
      List<ParameterInjector<?>> parameterInjections) {
    return parameterInjections.toArray(
        new ParameterInjector[parameterInjections.size()]);
  }

  /**
   * Finds the {@link Inject} annotation in an array of annotations.
   */
  Inject findInject(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == Inject.class) {
        return Inject.class.cast(annotation);
      }
    }
    return null;
  }

  static class MethodInjector implements Injector {

    final FastMethod fastMethod;
    final ParameterInjector<?>[] parameterInjectors;

    public MethodInjector(ContainerImpl container, Method method, String name)
        throws MissingDependencyException {
      this.fastMethod =
          FastClass.create(method.getDeclaringClass()).getMethod(method);
      Type[] parameterTypes = method.getGenericParameterTypes();
      parameterInjectors = parameterTypes.length > 0
          ? container.getParametersInjectors(
              method, method.getParameterAnnotations(), parameterTypes, name)
          : null;
    }

    public void inject(InternalContext context, Object o) {
      try {
        fastMethod.invoke(o, getParameters(context, parameterInjectors));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  Map<Class<?>, ConstructorInjector> constructors =
      new ReferenceCache<Class<?>, ConstructorInjector>() {
        @SuppressWarnings("unchecked")
        protected ConstructorInjector<?> create(Class<?> implementation) {
          if (implementation.isInterface()) {
            errorHandler.handle(ErrorMessage.CANNOT_INJECT_INTERFACE,
                implementation);
          }

          return new ConstructorInjector(ContainerImpl.this, implementation);
        }
      };

  class ConstructorInjector<T> implements Factory<T> {

    final Class<T> implementation;
    final List<Injector> injectors;
    final FastConstructor fastConstructor;
    final ParameterInjector<?>[] parameterInjectors;

    ConstructorInjector(ContainerImpl container, Class<T> implementation) {
      this.implementation = implementation;
      Constructor<T> constructor = findConstructorIn(implementation);
      parameterInjectors = createParameterInjector(container, constructor);
      injectors = container.injectors.get(implementation);
      fastConstructor = FastClass.create(constructor.getDeclaringClass())
          .getConstructor(constructor);
    }

    ParameterInjector<?>[] createParameterInjector(ContainerImpl container,
        Constructor<T> constructor) {
      try {
        Inject inject = constructor.getAnnotation(Inject.class);
        return inject == null
            ? null // default constructor.
            : container.getParametersInjectors(
                constructor,
                constructor.getParameterAnnotations(),
                constructor.getGenericParameterTypes(),
                inject.value()
            );
      } catch (MissingDependencyException e) {
        e.handle(errorHandler);
        return null;
      }
    }

    @SuppressWarnings({"unchecked"})
    private Constructor<T> findConstructorIn(Class<T> implementation) {
      Constructor<T> found = null;
      for (Constructor<T> constructor
          : implementation.getDeclaredConstructors()) {
        if (constructor.getAnnotation(Inject.class) != null) {
          if (found != null) {
            errorHandler.handle(
                ErrorMessage.TOO_MANY_CONSTRUCTORS, implementation);
            return invalidConstructor();
          }
          found = constructor;
        }
      }
      if (found != null) {
        return found;
      }

      // If no annotated constructor is found, look for a no-arg constructor
      // instead.
      try {
        return implementation.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        errorHandler.handle(ErrorMessage.MISSING_CONSTRUCTOR, implementation);
        return invalidConstructor();
      }
    }

    /**
     * Construct an instance. Returns {@code Object} instead of {@code T}
     * because it may return a proxy.
     */
    Object construct(InternalContext context, Class<? super T> expectedType) {
      ConstructionContext<T> constructionContext =
          context.getConstructionContext(this);

      // We have a circular reference between constructors. Return a proxy.
      if (constructionContext.isConstructing()) {
        // TODO (crazybob): if we can't proxy this object, can we proxy the
        // other object?
        return constructionContext.createProxy(expectedType);
      }

      // If we're re-entering this factory while injecting fields or methods,
      // return the same instance. This prevents infinite loops.
      T t = constructionContext.getCurrentReference();
      if (t != null) {
        return t;
      }

      try {
        // First time through...
        constructionContext.startConstruction();
        try {
          Object[] parameters = getParameters(context, parameterInjectors);
          t = newInstance(parameters);
          constructionContext.setProxyDelegates(t);
        } finally {
          constructionContext.finishConstruction();
        }

        // Store reference. If an injector re-enters this factory, they'll
        // get the same reference.
        constructionContext.setCurrentReference(t);

        // Inject fields and methods.
        if (!injectors.isEmpty()) {
          for (Injector injector : injectors) {
            injector.inject(context, t);
          }
        }

        return t;
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      } finally {
        constructionContext.removeCurrentReference();
      }
    }

    @SuppressWarnings({"unchecked"})
    private T newInstance(Object[] parameters)
        throws InvocationTargetException {
      return (T) fastConstructor.newInstance(parameters);
    }

    public T get() {
      try {
        return callInContext(new ContextualCallable<T>() {
          @SuppressWarnings({"unchecked"})
          public T call(InternalContext context) {
            return (T) construct(context, implementation);
          }
        });
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * A placeholder. This enables us to continue processing and gather more
   * errors but blows up if you actually try to use it.
   */
  static class InvalidConstructor {
    InvalidConstructor() {
      throw new AssertionError();
    }
  }

  @SuppressWarnings({"unchecked"})
  static <T> Constructor<T> invalidConstructor() {
    try {
      return (Constructor<T>) InvalidConstructor.class.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static class ParameterInjector<T> {

    final ExternalContext<T> externalContext;
    final InternalFactory<? extends T> factory;

    public ParameterInjector(ExternalContext<T> externalContext,
        InternalFactory<? extends T> factory) {
      this.externalContext = externalContext;
      this.factory = factory;
    }

    T inject(InternalContext context) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        return factory.get(context);
      } finally {
        context.setExternalContext(previous);
      }
    }
  }

  private static Object[] getParameters(InternalContext context,
      ParameterInjector[] parameterInjectors) {
    if (parameterInjectors == null) {
      return null;
    }

    Object[] parameters = new Object[parameterInjectors.length];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterInjectors[i].inject(context);
    }
    return parameters;
  }

  void injectMembers(Object o, InternalContext context) {
    List<Injector> injectors = this.injectors.get(o.getClass());
    for (Injector injector : injectors) {
      injector.inject(context, o);
    }
  }

  public <T> Factory<T> getCreator(final Class<T> implementation) {
    return getConstructor(implementation);
  }

  public boolean hasBindingFor(Key<?> key) {
    try {
      return getFactory(null, key) != null;
    } catch (ConfigurationException e) {
      return false;
    }
  }

  public void injectMembers(final Object o) {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        injectMembers(o, context);
        return null;
      }
    });
  }

  public <T> Factory<T> getFactory(final Key<T> key) {
    final InternalFactory<? extends T> factory = getFactory(null, key);

    if (factory == null) {
      throw new ConfigurationException("Missing binding for " + key + ".");
    }

    return new Factory<T>() {
      public T get() {
        return callInContext(new ContextualCallable<T>() {
          public T call(InternalContext context) {
            ExternalContext<?> previous = context.getExternalContext();
            context.setExternalContext(
                ExternalContext.newInstance(null, key, ContainerImpl.this));
            try {
              return factory.get(context);
            } finally {
              context.setExternalContext(previous);
            }
          }
        });
      }
    };
  }

  ThreadLocal<InternalContext[]> localContext =
      new ThreadLocal<InternalContext[]>() {
        protected InternalContext[] initialValue() {
          return new InternalContext[1];
        }
      };

  /**
   * Looks up thread local context. Creates (and removes) a new context if
   * necessary.
   */
  <T> T callInContext(ContextualCallable<T> callable) {
    InternalContext[] reference = localContext.get();
    if (reference[0] == null) {
      reference[0] = new InternalContext(this);
      try {
        return callable.call(reference[0]);
      } finally {
        // Only remove the context if this call created it.
        reference[0] = null;
      }
    } else {
      // Someone else will clean up this context.
      return callable.call(reference[0]);
    }
  }

  interface ContextualCallable<T> {
    T call(InternalContext context);
  }

  /**
   * Gets a constructor function for a given implementation class.
   */
  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(Class<T> implementation) {
    return constructors.get(implementation);
  }

  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(TypeLiteral<T> implementation) {
    return constructors.get(implementation.getRawType());
  }

  /**
   * Injects a field or method in a given object.
   */
  interface Injector {
    void inject(InternalContext context, Object o);
  }

  static class MissingDependencyException extends Exception {

    final Key<?> key;
    final Member member;

    MissingDependencyException(Key<?> key, Member member) {
      this.key = key;
      this.member = member;
    }

    void handle(ErrorHandler errorHandler) {
      errorHandler.handle(ErrorMessage.MISSING_BINDING, member, key);
    }
  }

  /**
   * Map of primitive type converters.
   */
  static class PrimitiveConverters extends HashMap<Class<?>, Converter<?>> {

    PrimitiveConverters() {
      putParser(int.class);
      putParser(long.class);
      putParser(boolean.class);
      putParser(byte.class);
      putParser(short.class);
      putParser(float.class);
      putParser(double.class);

      // Character doesn't follow the same pattern.
      Converter<Character> characterConverter = new Converter<Character>() {
        public Character convert(Member member, Key<Character> key,
            String value) throws ConstantConversionException {
          value = value.trim();
          if (value.length() != 1) {
            throw new ConstantConversionException(member, key, value,
                "Length != 1.");
          }
          return value.charAt(0);
        }
      };
      put(char.class, characterConverter);
      put(Character.class, characterConverter);
    }

    <T> void putParser(final Class<T> primitive) {
      try {
        Class<?> wrapper = PRIMITIVE_COUNTERPARTS.get(primitive);
        final Method parser = wrapper.getMethod("parse" +
            Strings.capitalize(primitive.getName()), String.class);
        Converter<T> converter = new Converter<T>() {
          @SuppressWarnings({"unchecked"})
          public T convert(Member member, Key<T> key, String value)
              throws ConstantConversionException {
            try {
              return (T) parser.invoke(null, value);
            } catch (IllegalAccessException e) {
              throw new AssertionError(e);
            } catch (InvocationTargetException e) {
              throw new ConstantConversionException(member, key, value,
                  e.getTargetException());
            }
          }
        };
        put(wrapper, converter);
        put(primitive, converter);
      } catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
    }
  }

  /**
   * Converts a {@code String} to another type.
   */
  interface Converter<T> {

    /**
     * Converts {@code String} value.
     */
    T convert(Member member, Key<T> key, String value)
        throws ConstantConversionException;
  }

  private static InternalFactory<?> INVALID_FACTORY =
      new InternalFactory<Object>() {
        public Object get(InternalContext context) {
          throw new AssertionError();
        }
      };

  @SuppressWarnings({"unchecked"})
  static <T> InternalFactory<T> invalidFactory() {
    return (InternalFactory<T>) INVALID_FACTORY;
  }

  private static class InvalidErrorHandler implements ErrorHandler {

    public void handle(String message, Object... arguments) {
      throw new AssertionError();
    }

    public void handle(String message) {
      throw new AssertionError();
    }

    public void handle(Throwable t) {
      throw new AssertionError();
    }
  }
}