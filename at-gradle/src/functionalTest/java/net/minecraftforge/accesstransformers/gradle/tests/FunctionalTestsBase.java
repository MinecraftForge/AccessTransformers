/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public abstract class FunctionalTestsBase {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    protected Path projectDir;

    @RegisterExtension
    AfterTestExecutionCallback afterTestExecutionCallback = this::after;
    private void after(ExtensionContext context) throws Exception {
        if (context.getExecutionException().isPresent())
            System.out.println(context.getDisplayName() + " Failed: " + projectDir);
    }

    protected final String gradleVersion;

    protected FunctionalTestsBase(String gradleVersion) {
        this.gradleVersion = gradleVersion;
    }

    protected void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    protected GradleRunner runner(String... args) {
        return GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withArguments(args)
            .withPluginClasspath();
    }

    protected BuildResult build(String... args) {
        return runner(args).build();
    }

    protected static void assertTaskSuccess(BuildResult result, String task) {
        assertTaskOutcome(result, task, TaskOutcome.SUCCESS);
    }

    protected static void assertTaskFailed(BuildResult result, String task) {
        assertTaskOutcome(result, task, TaskOutcome.FAILED);
    }

    protected static void assertTaskOutcome(BuildResult result, String task, TaskOutcome expected) {
        var info = result.task(task);
        assertNotNull(info, "Could not find task `" + task + "` in build results");
        assertEquals(expected, info.getOutcome());
    }

    protected static byte[] readJarEntry(Path path, String name) throws IOException {
        try (var fs = FileSystems.newFileSystem(path)) {
            var target = fs.getPath(name);
            assertTrue(Files.exists(target), "Archive " + path + " does not contain " + name);
            return Files.readAllBytes(target);
        }
    }

    protected static void assertFileMissing(Path path, String name) throws IOException {
        try (var fs = FileSystems.newFileSystem(path)) {
            var target = fs.getPath(name);
            assertFalse(Files.exists(target), "Archive " + path + " contains `" + name + "` when it should not");
        }
    }
}
