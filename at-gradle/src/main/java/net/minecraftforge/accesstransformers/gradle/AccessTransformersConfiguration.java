/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.file.RegularFileProperty;

/// Configuration for individual dependencies to be run through AccessTransformers.
///
/// @apiNote This interface is not sealed since it is implemented by Forge Gradle for consumers to set the config file
/// for the Minecraft dependency. Implementing this interface yourself is still highly discouraged.
public interface AccessTransformersConfiguration {
    /// Gets the AccessTransformer configuration to use.
    ///
    /// @return The property for the configuration
    RegularFileProperty getConfig();
}
