/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.jetbrains.annotations.ApiStatus;

/// The extension interface for the AccessTransformers Gradle plugin.
///
/// Consumers must register at least one container using [#register(Attribute, Action)] (or using [#register(Action)] to
/// use the default attribute). This extension acts as a delegate to the last registered container, but consumers can
/// also use the return value of the registration to use multiple containers in a single project.
///
/// @apiNote This interface is effectively sealed and [must not be extended][ApiStatus.NonExtendable].
@ApiStatus.NonExtendable // Sealed types were not added until Java 15
public interface AccessTransformersExtension extends AccessTransformersContainer {
    /// The name for this extension when added to [projects][org.gradle.api.Project].
    String NAME = "accessTransformers";

    /// The default attribute used by [#register(Action)].
    ///
    /// It is recommended that consumers use their own attributes if they plan on registering more than one container
    /// per build.
    Attribute<Boolean> DEFAULT_ATTRIBUTE = Attribute.of("net.minecraftforge.accesstransformers.transformed", Boolean.class);

    /// Registers a new container, with the given options, using the [default attribute][#DEFAULT_ATTRIBUTE].
    ///
    /// @param options The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    default AccessTransformersContainer register(Action<? super AccessTransformersContainer.Options> options) {
        return this.register(AccessTransformersExtension.DEFAULT_ATTRIBUTE, options);
    }

    /// Registers a new container, with the given options, using the [default attribute][#DEFAULT_ATTRIBUTE].
    ///
    /// @param options The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    @SuppressWarnings("rawtypes") // public-facing closure
    default AccessTransformersContainer register(
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure options
    ) {
        return this.register(DEFAULT_ATTRIBUTE, options);
    }

    /// Registers a new container using the given attribute and options.
    ///
    /// @param attribute The boolean attribute to use for this container
    /// @param options   The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    default AccessTransformersContainer register(Attribute<Boolean> attribute, Action<? super AccessTransformersContainer.Options> options) {
        return register(attribute, Closures.action(this, options));
    }

    /// Registers a new container using the given attribute and options.
    ///
    /// @param attribute The boolean attribute to use for this container
    /// @param options   The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    @SuppressWarnings("rawtypes") // public-facing closure
    AccessTransformersContainer register(
        Attribute<Boolean> attribute,
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure options
    );
}
