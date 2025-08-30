/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/// Represents a container of dependencies that will be access transformed.
///
/// Containers are created with [AccessTransformersExtension#register(Attribute, Action)]. Dependencies can be added
/// using [#dep(Object, Closure)].
///
/// @apiNote This interface is effectively sealed and [must not be extended][ApiStatus.NonExtendable].
/// @see AccessTransformersExtension
public sealed interface AccessTransformersContainer permits AccessTransformersContainerInternal, AccessTransformersExtension {
    /// Registers a new container using the given attribute and options.
    ///
    /// @param project   The project to make the container for
    /// @param attribute The boolean attribute to use for this container
    /// @param options   The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    static AccessTransformersContainer register(Project project, Attribute<Boolean> attribute, Action<? super AccessTransformersContainer.Options> options) {
        return AccessTransformersContainerInternal.register(project, attribute, options);
    }

    /// Gets the attribute used by the [transformer][ArtifactAccessTransformer]. It must be unique to this container.
    ///
    /// @return The attribute
    /// @see <a href="https://docs.gradle.org/current/userguide/artifact_transforms.html">Artifact Transforms</a>
    Attribute<Boolean> getAttribute();

    /// Configures the access transformer options.
    ///
    /// @param action The configuring action
    /// @see AccessTransformersContainer.Options
    void options(Action<? super AccessTransformersContainer.Options> action);

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param closure            A configuring closure for the dependency
    /// @return The dependency to be transformed
    @SuppressWarnings("rawtypes") // public-facing closure
    Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    );

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param action             A configuring action for the dependency
    /// @return The dependency to be transformed
    default Dependency dep(Object dependencyNotation, Action<? super Dependency> action) {
        return this.dep(dependencyNotation, Closures.action(this, action));
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @return The dependency to be transformed
    default Dependency dep(Object dependencyNotation) {
        return this.dep(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param closure            A configuring closure for the dependency
    /// @return The dependency to be transformed
    @SuppressWarnings("rawtypes") // public-facing closure
    Provider<?> dep(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    );

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param action             A configuring action for the dependency
    /// @return The dependency to be transformed
    default Provider<?> dep(Provider<?> dependencyNotation, Action<? super Dependency> action) {
        return this.dep(dependencyNotation, Closures.action(this, action));
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @return The dependency to be transformed
    default Provider<?> dep(Provider<?> dependencyNotation) {
        return this.dep(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param closure            A configuring closure for the dependency
    /// @return The dependency to be transformed
    @SuppressWarnings("rawtypes") // public-facing closure
    default Provider<?> dep(
        ProviderConvertible<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        return this.dep(dependencyNotation.asProvider(), closure);
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @param action             A configuring action for the dependency
    /// @return The dependency to be transformed
    default Provider<?> dep(ProviderConvertible<?> dependencyNotation, Action<? super Dependency> action) {
        return this.dep(dependencyNotation, Closures.action(this, action));
    }

    /// Queues the given dependency to be transformed by AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation)
    /// @return The dependency to be transformed
    default Provider<?> dep(ProviderConvertible<?> dependencyNotation) {
        return this.dep(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
    }

    /// When initially registering an AccessTransformers container, the consumer must define key information regarding
    /// how AccessTransformers will be used. This interface is used to define that information.
    sealed interface Options permits AccessTransformersContainerInternal.Options {
        /// Sets the configuration to use as the classpath for AccessTransformers.
        ///
        /// @param files The file collection to use
        void setClasspath(FileCollection files);

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependency The dependency to use
        void setClasspath(
            @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.dsl.DependencyHandler")
            Closure<? extends Dependency> dependency
        );

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependency The dependency to use
        default void setClasspath(Function<? super DependencyHandler, ? extends Dependency> dependency) {
            this.setClasspath(Closures.function(this, dependency));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param closure            A configuring closure for the dependency
        @SuppressWarnings("rawtypes")
        default void setClasspath(
            Object dependencyNotation,
            @DelegatesTo(Dependency.class)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
            Closure closure
        ) {
            this.setClasspath(dependencies -> dependencies.create(dependencyNotation, closure));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param action             A configuring action for the dependency
        default void setClasspath(
            Object dependencyNotation,
            Action<? super Dependency> action
        ) {
            this.setClasspath(dependencyNotation, Closures.action(this, action));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        default void setClasspath(Object dependencyNotation) {
            this.setClasspath(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param closure            A configuring closure for the dependency
        @SuppressWarnings("rawtypes")
        default void setClasspath(
            Provider<?> dependencyNotation,
            @DelegatesTo(Dependency.class)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
            Closure closure
        ) {
            this.setClasspath(dependencyNotation.get(), closure);
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param action             A configuring action for the dependency
        default void setClasspath(
            Provider<?> dependencyNotation,
            Action<? super Dependency> action
        ) {
            this.setClasspath(dependencyNotation, Closures.action(this, action));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        default void setClasspath(Provider<?> dependencyNotation) {
            this.setClasspath(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param closure            A configuring closure for the dependency
        @SuppressWarnings("rawtypes")
        default void setClasspath(
            ProviderConvertible<?> dependencyNotation,
            @DelegatesTo(Dependency.class)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
            Closure closure
        ) {
            this.setClasspath(dependencyNotation.asProvider(), closure);
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        /// @param action             A configuring action for the dependency
        default void setClasspath(
            ProviderConvertible<?> dependencyNotation,
            Action<? super Dependency> action
        ) {
            this.setClasspath(dependencyNotation, Closures.action(this, action));
        }

        /// Sets the dependency to use as the classpath for AccessTransformers.
        ///
        /// @param dependencyNotation The dependency (notation) to use
        default void setClasspath(ProviderConvertible<?> dependencyNotation) {
            this.setClasspath(dependencyNotation, Closures.<Dependency>unaryOperator(this, UnaryOperator.identity()));
        }

        /// Sets the main class to invoke when running AccessTransformers.
        ///
        /// @param mainClass The main class to use
        /// @apiNote This is *not required* if the given [classpath][#setClasspath(FileCollection)] is a single
        /// executable jar.
        void setMainClass(Provider<String> mainClass);

        /// Sets the main class to invoke when running AccessTransformers.
        ///
        /// @param mainClass The main class to use
        /// @apiNote This is *not required* if the given [classpath][#setClasspath(FileCollection)] is a single
        /// executable jar.
        void setMainClass(String mainClass);

        /// Sets the Java launcher to use to run AccessTransformers.
        ///
        /// This can be easily acquired using [Java toolchains][org.gradle.jvm.toolchain.JavaToolchainService].
        ///
        /// @param javaLauncher The Java launcher to use
        /// @see org.gradle.jvm.toolchain.JavaToolchainService#launcherFor(Action)
        void setJavaLauncher(Provider<? extends JavaLauncher> javaLauncher);

        /// Sets the Java launcher to use to run AccessTransformers.
        ///
        /// This can be easily acquired using [Java toolchains][org.gradle.jvm.toolchain.JavaToolchainService].
        ///
        /// @param javaLauncher The Java launcher to use
        /// @apiNote This method exists in case consumers have an eagerly processed `JavaLauncher` object. It is
        /// recommended to use [#setJavaLauncher(Provider)] instead.
        /// @see org.gradle.jvm.toolchain.JavaToolchainService#launcherFor(Action)
        void setJavaLauncher(JavaLauncher javaLauncher);

        /// Sets the arguments to use when running AccessTransformers.
        ///
        /// When processed by the transform action, these arguments will have specific tokens in them replaced.
        ///
        /// - `{inJar}` - The input jar
        /// - `{atFile}` - The AccessTransformers configuration file
        /// - `{outJar}` - The output jar
        /// - `{logFile}` - The log file
        ///
        /// @param args The arguments to use
        void setArgs(Provider<? extends Iterable<String>> args);

        /// Sets the arguments to use when running AccessTransformers.
        ///
        /// When processed by the transform action, these arguments will have specific tokens in them replaced.
        ///
        /// - `{inJar}` - The input jar
        /// - `{atFile}` - The AccessTransformers configuration file
        /// - `{outJar}` - The output jar
        /// - `{logFile}` - The log file
        ///
        /// @param args The arguments to use
        void setArgs(Iterable<String> args);

        /// Sets the arguments to use when running AccessTransformers.
        ///
        /// When processed by the transform action, these arguments will have specific tokens in them replaced.
        ///
        /// - `{inJar}` - The input jar
        /// - `{atFile}` - The AccessTransformers configuration file
        /// - `{outJar}` - The output jar
        /// - `{logFile}` - The log file
        ///
        /// @param args The arguments to use
        default void setArgs(String... args) {
            this.setArgs(Arrays.asList(args));
        }

        /// Sets the AccessTransformer configuration to use.
        ///
        /// If the given provider does not provide a [file][File] or [regular file][RegularFile], the result will be
        /// resolved using [org.gradle.api.Project#file(Object)], similarly to [#setConfig(Object)].
        ///
        /// @param configFile The configuration file to use
        void setConfig(Provider<?> configFile);

        /// Sets the AccessTransformer configuration to use.
        ///
        /// @param configFile The configuration file to use
        void setConfig(RegularFile configFile);

        /// Sets the AccessTransformer configuration to use.
        ///
        /// @param configFile The configuration file to use
        void setConfig(File configFile);

        /// Sets the AccessTransformer configuration to use.
        ///
        /// The given object is resolved using [org.gradle.api.Project#file(Object)].
        ///
        /// @param configFile The configuration file to use
        void setConfig(Object configFile);
    }
}
