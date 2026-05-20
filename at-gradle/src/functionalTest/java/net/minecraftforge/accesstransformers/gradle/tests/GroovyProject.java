/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle.tests;

import java.io.IOException;
import java.nio.file.Path;

public class GroovyProject extends GradleProject {
    protected GroovyProject(Path projectDir) {
        super(projectDir, "build.gradle", "settings.gradle");
    }

    @Override
    protected void transformDependency(String library, String config) throws IOException {
        settingsFile();
        buildFile(
            BASIC_BUILD +
            """
            dependencies {
                implementation('{library}') {
                    accessTransformers.configure(it) {
                        config = project.file('{config}')
                    }
                }
            }
            """
            .replace("{library}", library)
            .replace("{config}", config)
        );
    }

    @Override
    protected void transformConstraint(String library, String constraint, String config) throws IOException {
        settingsFile();
        buildFile(
            BASIC_BUILD +
            """
            dependencies {
                implementation('{library}')
                constraints {
                    implementation('{constraint}') {
                        accessTransformers.configure(it) {
                            config = project.file('{config}')
                        }
                    }
                }
            }
            """
            .replace("{library}", library)
            .replace("{constraint}", constraint)
            .replace("{config}", config)
        );
    }
}
