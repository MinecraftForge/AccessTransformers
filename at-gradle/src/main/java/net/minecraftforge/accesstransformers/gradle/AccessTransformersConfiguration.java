/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.file.RegularFileProperty;

/// Configuration for individual dependencies to be run through AccessTransformers.
public sealed interface AccessTransformersConfiguration permits AccessTransformersContainer.Options, AccessTransformersConfigurationInternal {
    /// Gets the AccessTransformer configuration to use.
    ///
    /// @return The property for the configuration
    RegularFileProperty getConfig();
}
