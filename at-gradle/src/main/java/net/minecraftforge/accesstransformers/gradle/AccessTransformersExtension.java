/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.Action;
import org.jetbrains.annotations.ApiStatus;

/// The extension interface for the AccessTransformers Gradle plugin.
///
/// Consumers must register at least one container using [#register(Action)]. This extension acts as a delegate to the
/// last registered container, but consumers can also use the return value of the registration to use multiple
/// containers in a single project.
///
/// @apiNote This interface is effectively sealed and [must not be extended][ApiStatus.NonExtendable].
public sealed interface AccessTransformersExtension extends AccessTransformersContainer permits AccessTransformersExtensionInternal {
    /// The name for this extension when added to [projects][org.gradle.api.Project].
    String NAME = "accessTransformers";

    /// Registers a new container using the given attribute and options.
    ///
    /// @param options The options to apply
    /// @return The registered container
    /// @see AccessTransformersContainer.Options
    AccessTransformersContainer register(Action<? super AccessTransformersContainer.Options> options);
}
