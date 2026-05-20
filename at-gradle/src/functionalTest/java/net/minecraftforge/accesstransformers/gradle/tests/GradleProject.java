/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class GradleProject {
    protected final Path projectDir;
    protected final String buildFile;
    protected final String settingsFile;

    protected GradleProject(Path projectDir, String buildFile, String settingsFile) {
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.settingsFile = settingsFile;
    }

    protected static final String BASIC_SETTINGS = """
        plugins {
          id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        }

        rootProject.name = "test"
        """;

    protected void settingsFile() throws IOException {
        settingsFile(BASIC_SETTINGS);
    }

    protected void settingsFile(String content) throws IOException {
        writeFile(settingsFile, content);
    }

    protected static final String BASIC_BUILD = """
        plugins {
          id("java")
          id("net.minecraftforge.accesstransformers")
        }

        repositories {
          mavenCentral();
        }
        """;

    protected void buildFile() throws IOException {
        buildFile(BASIC_BUILD);
    }

    protected void buildFile(int javaVersion) throws IOException {
        buildFile(BASIC_BUILD + javaVersion(javaVersion));
    }

    protected String javaVersion(int javaVersion) {
        return "java.toolchain.languageVersion = JavaLanguageVersion.of(" + javaVersion + ")\n";
    }

    protected void buildFile(String content) throws IOException {
        writeFile(buildFile, content);
    }

    public void writeFile(String name, String content) throws IOException {
        writeFile(projectDir.resolve(name), content);
    }

    protected void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // Simple dependency transformation
    // The test will write the config file, a java class which access the transformed field/method,
    // Then attempt to run build.
    protected abstract void transformDependency(String library, String config) throws IOException;

    // Transform a transitive dependency using Gradle's constraints function.
    // This allows you to transform the a dependency if it exists, but not have a hard reference
    // to it in your metadata
    //    - I have no idea why you would do that - Lex
    //
    // The test will write the config file, a java class which access the transformed field/method,
    // Then attempt to run ':build'.
    protected abstract void transformConstraint(String library, String constraint, String config) throws IOException;
}
