/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Project;

import javax.inject.Inject;

abstract class AccessTransformersPlugin extends EnhancedPlugin<Project> {
    static final String NAME = "accesstransformers";
    static final String DISPLAY_NAME = "AccessTransformers Gradle";

    @Inject
    public AccessTransformersPlugin() {
        super(NAME, DISPLAY_NAME);
    }

    @Override
    public void setup(Project project) {
        project.getExtensions().create(AccessTransformersExtension.NAME, AccessTransformersExtensionImpl.class, this, project);
    }
}
