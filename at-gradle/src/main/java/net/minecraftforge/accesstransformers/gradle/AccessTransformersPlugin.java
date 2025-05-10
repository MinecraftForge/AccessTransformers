/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;

abstract class AccessTransformersPlugin implements Plugin<Project> {
    static final Logger LOGGER = Logging.getLogger(AccessTransformersPlugin.class);

    private final ObjectFactory objects;
    private final ProviderFactory providers;

    @Inject
    public AccessTransformersPlugin(ObjectFactory objects, ProviderFactory providers) {
        this.objects = objects;
        this.providers = providers;
    }

    @Override
    public void apply(Project project) {
        final DirectoryProperty globalCaches = this.objects.directoryProperty().fileProvider(this.providers.provider(() -> {
            File dir = new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Constants.CACHES_PATH_GLOBAL);
            Files.createDirectories(dir.toPath());
            return dir;
        }));

        // init default tools
        DefaultTools.start(globalCaches);

        // init accessTransformers extension
        project.getExtensions().add(
            AccessTransformersExtension.class,
            AccessTransformersExtension.NAME,
            new AccessTransformersExtensionImpl(project)
        );
    }
}
