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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jetbrains.annotations.ApiStatus;

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
    Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
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
    Provider<?> dep(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
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
    default Provider<?> dep(
        ProviderConvertible<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
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
        /// Gets the AccessTransformer configuration to use.
        ///
        /// @return The property for the configuration
        RegularFileProperty getConfig();

        /// Gets the log level to pipe the output of AccessTransformers to.
        ///
        /// @return The property log level to use
        /// @apiNote This is [experimental][ApiStatus.Experimental] due to the fact that AccessTransformers treats both
        /// [System#out] and [System#err] equally, while preferring to use the latter. This will be addressed in a
        /// future version of AccessTransformers.
        @ApiStatus.Experimental
        Property<LogLevel> getLogLevel();

        /// Gets the classpath to use for AccessTransformers. By default, this contains the default AccessTransformers
        /// shadow JAR.
        ///
        /// @return The classpath
        /// @apiNote This is *not* the dependency's classpath. This is the classpath used in
        /// [org.gradle.process.JavaExecSpec#setClasspath(FileCollection)] to invoke AccessTransformers.
        ConfigurableFileCollection getClasspath();

        /// Gets the main class to invoke when running AccessTransformers.
        ///
        /// @return The property for the main class.
        /// @apiNote This is *not required* if the [classpath][#getClasspath()] is a single executable jar.
        Property<String> getMainClass();

        /// Gets the Java launcher used to run AccessTransformers.
        ///
        /// This can be easily acquired using [Java toolchains][org.gradle.jvm.toolchain.JavaToolchainService].
        ///
        /// @return The property for the Java launcher
        /// @see org.gradle.jvm.toolchain.JavaToolchainService#launcherFor(Action)
        Property<JavaLauncher> getJavaLauncher();

        /// Gets the list of arguments to use when running AccessTransformers.
        ///
        /// When processed by the transform action, these arguments will have specific tokens in them replaced.
        ///
        /// - `{inJar}` - The input jar
        /// - `{atFile}` - The AccessTransformers configuration file
        /// - `{outJar}` - The output jar
        /// - `{logFile}` - The log file
        ///
        /// @return The property for the arguments.
        ListProperty<Object> getArgs();
    }
}
