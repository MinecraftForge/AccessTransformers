package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.FirstParam;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.Action;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.Consumer;
import java.util.function.Function;

// copied from FG7
final class Closures {
    /// Invokes a given closure with the given object as the delegate type and parameter.
    ///
    /// This is used to work around a Groovy DSL implementation detail that involves dynamic objects within
    /// buildscripts. By default, Gradle will attempt to locate the dynamic object that is being referenced within a
    /// closure and use handlers within the buildscript's class loader to work with it. This is unfortunately very
    /// unfriendly to trying to use closures on traditional objects. The solution is to both manually set the closure's
    /// [delegate][Closure#setDelegate(Object)], [resolve strategy][Closure#setResolveStrategy(int)], and temporarily
    /// swap out the [current thread's context class loader][Thread#setContextClassLoader(ClassLoader)] with that of the
    /// closure in order to force resolution of the groovy metaclass to the delegate object.
    ///
    /// I'm sorry.
    ///
    /// @see org.gradle.api.internal.AbstractTask.ClosureTaskAction#doExecute(org.gradle.api.Task)
    @SuppressWarnings({"rawtypes", "unchecked", "JavadocReference"})
    static <T> @UnknownNullability T invoke(Object object, @DelegatesTo(value = FirstParam.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        closure.setDelegate(object);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(closure.getClass().getClassLoader());
        try {
            Object ret = closure.getMaximumNumberOfParameters() == 0 ? closure.call() : closure.call(object);
            return ret != null ? (T) ret : null;
        } catch (InvokerInvocationException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /// Creates a closure backed by the given function
    ///
    /// @param owner    The owner of the closure
    /// @param function The function to apply
    /// @param <T>      The parameter type of the function
    /// @param <R>      The return type of the function
    static <T, R> Closure<R> function(Object owner, Function<? super T, ? extends R> function) {
        return new Functional<T, R>(owner, function);
    }

    /// Creates a closure backed by the given action.
    ///
    /// @apiNote For instance methods only.
    /// @param owner  The owner of the closure
    /// @param action The action to execute
    /// @param <T>    The type of the action
    /// @return The closure
    static <T> Closure<Void> action(Object owner, Action<? super T> action) {
        return consumer(owner, action::execute);
    }

    /// Creates a closure backed by the given consumer.
    ///
    /// @apiNote For instance methods only.
    /// @param owner    The owner of the closure
    /// @param consumer The consumer to execute
    /// @param <T>      The type of the action
    /// @return The closure
    static <T> Closure<Void> consumer(Object owner, Consumer<? super T> consumer) {
        return new Consuming<>(owner, consumer);
    }

    /// Creates an empty closure.
    ///
    /// @apiNote For instance methods only.
    /// @param owner The owner of the closure
    /// @return The empty closure
    static Closure<Void> empty(Object owner) {
        return new Empty(owner);
    }

    private static final class Functional<T, R> extends Closure<R> {
        private final Function<? super T, ? extends R> function;

        private Functional(Object owner, Function<? super T, ? extends R> function) {
            super(owner, owner);
            this.function = function;
        }

        @SuppressWarnings("unused") // invoked by Groovy
        public R doCall(T object) {
            return this.function.apply(object);
        }
    }

    private static final class Consuming<T> extends Closure<Void> {
        private final Consumer<? super T> consumer;

        private Consuming(Object owner, Consumer<? super T> consumer) {
            super(owner, owner);
            this.consumer = consumer;
        }

        @SuppressWarnings("unused") // invoked by Groovy
        public Void doCall(T object) {
            this.consumer.accept(object);
            return null;
        }
    }

    private static class Empty extends Closure<Void> {
        public Empty(Object owner) {
            super(owner, owner);
        }

        @SuppressWarnings("unused") // invoked by Groovy
        public Void doCall() {
            return null;
        }
    }
}
