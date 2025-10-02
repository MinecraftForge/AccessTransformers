/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.problems.Severity;

import javax.inject.Inject;
import java.io.File;
import java.io.Serial;
import java.nio.file.Path;

abstract class AccessTransformersProblems extends EnhancedProblems {
    private static final @Serial long serialVersionUID = -5334414678185075096L;

    @Inject
    public AccessTransformersProblems() {
        super(AccessTransformersPlugin.NAME, AccessTransformersPlugin.DISPLAY_NAME);
    }

    void reportIllegalTargetDependency(Dependency dependency) {
        var dependencyToString = Util.toString(dependency);
        this.getLogger().error("ERROR: Cannot access transform target dependency: {}", dependencyToString);
        this.report("access-transformer-illegal-dependency-target", "Cannot access transform target dependency", spec -> spec
            .details("""
                Cannot apply Access Transformers to a dependency that cannot be configured with attributes.
                Dependency: %s"""
                .formatted(dependencyToString))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Use a valid dependency type (i.e. an external module, or any dependency type that implements `HasConfigurableAttributes`.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerConfigNotDefined(Exception e, String dependency) {
        return this.throwing(e, "access-transformer-config-not-defined", "Access transformer config not defined", spec -> spec
            .details("""
                The access transformer cannot transform the input artifact without a configuration file.
                Dependency: %s""".formatted(dependency))
            .severity(Severity.ERROR)
            .solution("Ensure you have defined your configuration file.")
            .solution("Do not use AccessTransformers if you do not need to.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerConfigMissing(Exception e, String dependency, String atFilePath) {
        return this.throwing(e, "access-transformer-config-missing", "Access transformer config file not found", spec -> spec
            .details("""
                The access transformer cannot transform the input artifact because the configuration file could not be found.
                Dependency: %s
                AccessTransformer Config: %s""".formatted(dependency, atFilePath))
            .severity(Severity.ERROR)
            .solution("Ensure your AccessTransformers configuration file exists.")
            .solution("Do not use AccessTransformers if you do not need to.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerConfigUnreadable(Throwable e, String dependency, String atFilePath) {
        return this.throwing(e, "access-transformer-config-unreadable", "Access transformer config file not read", spec -> spec
            .details("""
                The access transformer cannot transform the input artifact because the configuration file could not be read.
                This may be due to insufficient file permissions or a corrupted file.
                Dependency: %s
                AccessTransformer Config: %s""".formatted(dependency, atFilePath))
            .severity(Severity.ERROR)
            .solution("Ensure you (and/or Gradle) have proper read/write permissions to the AccessTransformer config file.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerConfigEmpty(Exception e, String dependency, String atFilePath) {
        return this.throwing(e, "access-transformer-config-empty", "Access transformer config missing or empty", spec -> spec
            .details("""
                The access transformer cannot transform the input artifact because the configuration file is empty or blank.
                Dependency: %s
                AccessTransformer Config: %s""".formatted(dependency, atFilePath))
            .severity(Severity.ERROR)
            .solution("Ensure your AccessTransformers configuration file contains definitions to be used.")
            .solution("Do not use AccessTransformers if you do not need to.")
            .solution(HELP_MESSAGE));
    }

    void reportAccessTransformerCannotValidateOutput(Exception e, File inJar, File atFile, File outJar, File logFile) {
        this.getLogger().warn("WARNING: Access transformer completed, but failed to validate the output. Output jar: {}", outJar.getAbsolutePath());
        this.report("access-transformer-output-validation-failed", "Failed to validate the access transformed output", spec -> spec
            .details("""
                The access transformer completed the transformation, but failed to validate the output.
                While the output JAR may have been created successfully, it cannot be determined if any transformations were made.
                This could potentially be caused the lack of read/write permissions in the build folder.
                Input Jar: %s
                AccessTransformer Config: %s
                Output Jar: %s
                Execution Log: %s"""
                .formatted(inJar, atFile, outJar, logFile))
            .severity(Severity.WARNING)
            .withException(e)
            .fileLocation(atFile.getAbsolutePath())
            .solution("Ensure you (and/or Gradle) have proper read/write permissions to the build folder.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerCannotWriteOutput(Exception e, Path output) {
        return this.throwing(e, "access-transformer-output-write-failed", "Failed to write the access transformed output", spec -> spec
            .details("""
                The access transformer completed the transformation, but failed to write the output to Gradle.
                This could potentially be caused the lack of read/write permissions in the build folder or Gradle caches (`~/.gradle`).
                Transform action output: %s""".formatted(output.toAbsolutePath()))
            .severity(Severity.ERROR)
            .solution("Ensure you (and/or Gradle) have proper read/write permissions to the build folder.")
            .solution("Ensure you (and/or Gradle) have proper read/write permissions to the Gradle caches (`~/.gradle`).")
            .solution(HELP_MESSAGE));
    }

    void reportAccessTransformerOutputIdentical(File inJar, File atFile, File outJar, File logFile) {
        this.getLogger().warn("WARNING: Access transformer completed, but the output bytes are the same as the input. Output jar: {}", outJar.getAbsolutePath());
        this.report("access-transformer-output-identical", "Access transformer output identical to input", spec -> spec
            .details("""
                The access transformer completed the transformation, but the output bytes are the same as the input.
                This could potentially be caused by an empty or invalid access transformer configuration.
                Input Jar: %s
                AccessTransformer Config: %s
                Output Jar: %s
                Execution Log: %s"""
                .formatted(inJar, atFile, outJar, logFile))
            .severity(Severity.WARNING)
            .fileLocation(atFile.getAbsolutePath())
            .solution("Check your access transformer configuration file and ensure the correct members are stated.")
            .solution(HELP_MESSAGE));
    }

    RuntimeException accessTransformerFailed(RuntimeException e, File inJar, File atFile, File logFile) {
        return this.throwing(e, "access-transformer-failed", "Access transformer failed", spec -> spec
            .details("""
                The access transformer failed to apply the transformations.
                This could potentially be caused by an invalid access transformer configuration.
                Input Jar: %s
                AccessTransformer Config: %s
                Execution Log: %s"""
                .formatted(inJar, atFile, logFile))
            .severity(Severity.ERROR)
            .fileLocation(atFile.getAbsolutePath())
            .solution("Check your access transformer configuration file and ensure it is valid.")
            .solution(HELP_MESSAGE)
        );
    }
}
