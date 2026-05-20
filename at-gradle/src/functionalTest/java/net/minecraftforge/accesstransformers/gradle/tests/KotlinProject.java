/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle.tests;

import java.io.IOException;
import java.nio.file.Path;

public class KotlinProject extends GradleProject {
    protected KotlinProject(Path projectDir) {
        super(projectDir, "build.gradle.kts", "settings.gradle.kts");
    }

    @Override
    protected void transformDependency(String library, String config) throws IOException {
        settingsFile();
        buildFile(
            BASIC_BUILD +
            """
            dependencies {
                implementation("{library}") {
                    accessTransformers.configure(this) {
                        config = project.file("{config}")
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
                constraints {
                    implementation("{constraint}") {
                        accessTransformers.configure(this) {
                            config = project.file("{config}")
                        }
                    }
                }
                implementation("{library}")
            }
            """
            .replace("{library}", library)
            .replace("{constraint}", constraint)
            .replace("{config}", config)
        );
    }
}
