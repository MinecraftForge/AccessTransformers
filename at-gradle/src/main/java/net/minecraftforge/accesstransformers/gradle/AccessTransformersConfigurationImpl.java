/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.file.RegularFileProperty;

record AccessTransformersConfigurationImpl(
    RegularFileProperty getConfig,
    AccessTransformersContainerImpl.OptionsImpl options
) implements AccessTransformersConfigurationInternal { }
