/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.util.hash.HashStore;
import org.apache.groovy.util.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Transforms artifacts using AccessTransformers.
///
/// In ForgeGradle, this is only applied to the Minecraft classes artifact. It can, however, be apoplied to any artifact
/// as long as it is a [jar][org.gradle.api.artifacts.type.ArtifactTypeDefinition#JAR_TYPE] file.
public abstract class ArtifactAccessTransformer implements TransformAction<ArtifactAccessTransformer.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(ArtifactAccessTransformer.class);

    // If your IDE is showing an error, ignore it. Pattern#LITERAL ignores RegEx entirely.
    // We are using these to avoid compiling the pattern each time.
    private static final Pattern ARG_INJAR = Pattern.compile("{inJar}", Pattern.LITERAL);
    private static final Pattern ARG_ATFILE = Pattern.compile("{atFile}", Pattern.LITERAL);
    private static final Pattern ARG_OUTJAR = Pattern.compile("{outJar}", Pattern.LITERAL);
    private static final Pattern ARG_LOGFILE = Pattern.compile("{logFile}", Pattern.LITERAL);

    /// The parameters for the transform action.
    public interface Parameters extends TransformParameters {
        /// The dependency configuration to resolve where AccessTransformers will be found in.
        ///
        /// @return A property for the AccessTransformers dependency
        @InputFiles Property<FileCollection> getClasspath();

        /// The main class for AccessTransformers.
        ///
        /// @return A property for the main class to use
        /// @apiNote This is only required if you are providing a custom AccessTransformers which is not a single jar.
        @Optional @Input Property<String> getMainClass();

        /// The **executable path** of the Java launcher to use to run AccessTransformers.
        ///
        /// This can be acquired by mapping (a [provider][Provider] of) a [org.gradle.jvm.toolchain.JavaLauncher] to
        /// [org.gradle.jvm.toolchain.JavaLauncher#getExecutablePath()] -> [org.gradle.api.file.RegularFile#getAsFile()]
        /// -> [File#getAbsolutePath()].
        ///
        /// @return A property for the path of the Java launcher
        @Input Property<String> getJavaLauncher();

        /// The arguments to pass into AccessTransformers.
        ///
        /// These arguments accept tokens that will be replaced when the transform action is run.
        ///
        /// - `inJar` - The input jar
        /// - `atFile` - The AccessTransformers configuration file
        /// - `outJar` - The output jar
        /// - `logFile` - The log file
        ///
        /// @return A property for the arguments
        @Input ListProperty<String> getArgs();

        /// The AccessTransformer configuration to use.
        ///
        /// This is usually named `accesstransformer.cfg`. In Forge, it is required in production that this file exists
        /// in the mod jar's `META-INF` directory.
        ///
        /// @return A property for the AccessTransformer configuration
        @InputFile RegularFileProperty getConfig();

        /// The caches directory to use.
        ///
        /// This will include non-[transform output][TransformOutputs] files, such as in-house caching for the
        /// transformation process.
        ///
        /// @return A property for the caches directory
        @InputDirectory DirectoryProperty getCachesDir();
    }

    private final ExecOperations execOperations;

    /// The default constructor that is invoked by Gradle to instantiate this transform action.
    ///
    /// @param execOperations The exec operations used by this transform action
    @Inject
    public ArtifactAccessTransformer(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    /// The artifact to transform with AccessTransformers.
    ///
    /// @return A property for the input artifact
    protected abstract @InputArtifact Provider<FileSystemLocation> getInputArtifact();

    /// Runs the transform action on the input artifact, queuing it for transformation with AccessTransformers.
    ///
    /// There are a few key distinctions that set this transform action apart from more traditional actions in Gradle
    /// and other plugins:
    ///
    /// - If the [AccessTransformers configuration][Parameters#getConfig()] is unspecified or does not
    /// exist, this transformation is skipped entirely.
    ///   - Gradle detects changes to this file, so if it is changed, this transform action will be invoked by Gradle.
    /// - This transformer uses in-house caching to detect if the input artifact actually needs to be transformed in the
    /// first place.
    ///   - If caches are hit and an existing output is found, the transformation process is skipped entirely.
    ///   - Due to Gradle using transient caching for its artifact transforms, this is required to avoid unnecessarily
    /// jar transformations, lengthening the build/sync time.
    ///
    /// @param outputs The outputs for this transform action
    @Override
    public void transform(TransformOutputs outputs) {
        Parameters parameters = this.getParameters();

        // inputs
        File inJar = this.getInputArtifact().get().getAsFile();
        File atFile = parameters.getConfig().map(RegularFile::getAsFile).getOrNull();
        if (atFile == null) {
            LOGGER.warn("WARNING: Access transformer configuration missing or not provided, skipping transformation for {}", inJar.getName());
            outputs.file(inJar);
            return;
        }

        // outputs
        String outJarName = inJar.getName().replace(".jar", "-at.jar");
        File outJar = this.file(outJarName);
        File logFile = this.file(outJar.getName() + ".log");

        // caches
        DirectoryProperty cachesDir = parameters.getCachesDir();

        // aforementioned in-house caching
        HashStore cache = new HashStore(cachesDir.get().getAsFile())
            .load(cachesDir.file(outJarName + ".cache").get().getAsFile())
            .add("atFile", atFile)
            .add("inJar", inJar)
            .add("outJar", outJar);

        if (outJar.exists() && cache.isSame()) {
            LOGGER.info("Access transformer output up-to-date, skipping transformation for {}", inJar.getName());
        } else {
            LOGGER.info("Access transformer started. Input jar: {}", inJar.getAbsolutePath());
            ExecResult result = this.execOperations.javaexec(exec -> {
                OutputStream stream = toLog(LOGGER::info);
                exec.setStandardOutput(stream);
                exec.setErrorOutput(stream);

                exec.setExecutable(parameters.getJavaLauncher().get());
                exec.setClasspath(parameters.getClasspath().get());
                setOptional(exec.getMainClass(), parameters.getMainClass());

                Map<Pattern, String> substitutions = Maps.of(
                    ARG_INJAR, inJar.getAbsolutePath(),
                    ARG_ATFILE, atFile.getAbsolutePath(),
                    ARG_OUTJAR, outJar.getAbsolutePath(),
                    ARG_LOGFILE, logFile.getAbsolutePath()
                );
                exec.setArgs(parameters
                    .getArgs().get()
                    .stream()
                    .map(arg -> replace(arg, substitutions))
                    .collect(Collectors.toList())
                );
            });

            try {
                result.rethrowFailure();
                result.assertNormalExitValue();
            } catch (Exception e) {
                /*
                this.getReporter().throwing(e, id("access-transformer-failed", "Access transformer failed"), spec -> spec
                    .details("""
                        The access transformer failed to apply the transformations.
                        This could potentially be caused by an invalid access transformer configuration.
                        Input Jar: %s
                        AccessTransformer Config: %s"""
                        .formatted(inJar, atFile))
                    .severity(Severity.ERROR)
                    .stackLocation()
                    .fileLocation(atFile.getAbsolutePath())
                    .solution("Check your access transformer configuration file and ensure it is valid.")
                    .solution(HELP_MESSAGE)
                );
                 */
                LOGGER.error(
                    "The access transformer failed to apply the transformations.\n" +
                        "This could potentially be caused by an invalid access transformer configuration.\n" +
                        "Input Jar: {}\n" +
                        "AccessTransformer Config: {}\n" +
                        '\n' +
                        "Check your access transformer configuration file and ensure it is valid.",
                    inJar, atFile);
                throw new RuntimeException("Failed to apply access transformers to " + inJar.getName(), e);
            }

            LOGGER.info("Access transformer completed. Output jar: {}", outJar.getAbsolutePath());
            cache.add("outJar", outJar).save();
        }

        // transform output
        Path output = outputs.file(outJarName).toPath();

        try {
            Files.copy(
                outJar.toPath(),
                output,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy access transformer output to destination: " + output.toAbsolutePath(), e);
        }
    }

    /// Conditionally set the given provider's value to the given property's value if the property is
    /// [present][Provider#isPresent()].
    ///
    /// @param from The provider value to apply
    /// @param to   The property to apply the new value to
    /// @param <T>  The type of property
    private static <T> void setOptional(Property<T> to, Provider<? extends T> from) {
        if (from.isPresent()) to.set(from);
    }

    /// Uses [String#replace(CharSequence, CharSequence)] but with pre-compiled patterns provided in the given map.
    ///
    /// @param s             The string to replace
    /// @param substitutions The map of substitutions to use
    /// @see String#replace(CharSequence, CharSequence)
    private static String replace(String s, Map<Pattern, String> substitutions) {
        for (Map.Entry<Pattern, String> entry : substitutions.entrySet()) {
            s = entry.getKey().matcher(s).replaceAll(Matcher.quoteReplacement(entry.getValue()));
        }

        return s;
    }

    /// Creates a file in the caches directory.
    ///
    /// This uses a [RegularFileProperty] to ensure that Gradle is aware of this file's usage by this transform action.
    ///
    /// @param path The path, from the caches directory, to the file
    /// @return The file
    private File file(String path) {
        return this.getParameters().getCachesDir().file(path).map(file -> {
            Path p = file.getAsFile().toPath();
            try {
                Files.createDirectories(p.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create parent directrory for " + p.toAbsolutePath(), e);
            }

            return file;
        }).get().getAsFile();
    }

    /// Creates an output stream that logs to the given action.
    ///
    /// @param logger The logger to log to
    /// @return The output stream
    private static OutputStream toLog(Action<? super String> logger) {
        return new OutputStream() {
            private StringBuffer buffer = new StringBuffer(512);

            @Override
            public void write(int b) {
                if (b == '\r' || b == '\n') {
                    if (this.buffer.length() != 0) {
                        logger.execute(this.buffer.toString());
                        this.buffer = new StringBuffer(512);
                    }
                } else {
                    this.buffer.append(b);
                }
            }
        };
    }
}
