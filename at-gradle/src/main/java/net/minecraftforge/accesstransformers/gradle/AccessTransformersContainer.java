/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jetbrains.annotations.ApiStatus;

/// Represents a container of dependencies that will be access transformed.
///
/// Containers are created with [AccessTransformersExtension#register(Action)]. Dependencies can be registered to it
/// inside their configuring closures using [#configure].
///
/// @apiNote This interface is effectively sealed and [must not be extended][ApiStatus.NonExtendable].
/// @see AccessTransformersExtension
public sealed interface AccessTransformersContainer permits AccessTransformersContainerInternal, AccessTransformersExtension {
    /// Gets the access transformer options.
    ///
    /// @return The options
    /// @see AccessTransformersContainer.Options
    AccessTransformersContainer.Options getOptions();

    /// Configures the access transformer options.
    ///
    /// @param action The configuring action
    /// @see AccessTransformersContainer.Options
    default void options(Action<? super AccessTransformersContainer.Options> action) {
        action.execute(this.getOptions());
    }

    /// Configures the given dependency to use this AccessTransformers container.
    ///
    /// @param dependency The dependency to configure AccessTransformers for
    default void configure(Object dependency) {
        this.configure(dependency, it -> { });
    }

    /// Configures the given dependency to use this AccessTransformers container.
    ///
    /// @param dependency The dependency to configure AccessTransformers for
    /// @param action A configuring action to modify dependency-level AccessTransformer options
    void configure(Object dependency, Action<? super AccessTransformersConfiguration> action);

    /// When initially registering an AccessTransformers container, the consumer should define key information regarding
    /// how AccessTransformers will be used. This interface is used to define that information.
    sealed interface Options extends AccessTransformersConfiguration permits AccessTransformersContainerInternal.Options {
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
