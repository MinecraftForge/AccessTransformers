/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.util.hash.HashStore;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.process.ExecOperations;
import org.gradle.process.ProcessExecutionException;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Transforms artifacts using AccessTransformers.
///
/// This can be applied to any artifact as long as it is a
/// [jar][org.gradle.api.artifacts.type.ArtifactTypeDefinition#JAR_TYPE] file.
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
        /// The AccessTransformer configuration to use.
        ///
        /// This is usually named `accesstransformer.cfg`. In Forge, it is required in production that this file exists
        /// in the mod jar's `META-INF` directory.
        ///
        /// @return A property for the AccessTransformer configuration
        @InputFile RegularFileProperty getConfig();

        /// The log level to pipe the output of AccessTransformers to.
        ///
        /// @return The log level
        @Console Property<LogLevel> getLogLevel();

        /// The dependency configuration to resolve where AccessTransformers will be found in.
        ///
        /// @return A property for the AccessTransformers dependency
        @InputFiles @Classpath ConfigurableFileCollection getClasspath();

        /// The main class for AccessTransformers.
        ///
        /// @return A property for the main class to use
        /// @apiNote This is only required if you are providing a custom AccessTransformers which is not a single jar.
        @Input @Optional Property<String> getMainClass();

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

        /// The caches directory to use.
        ///
        /// This will include non-[transform output][TransformOutputs] files, such as in-house caching for the
        /// transformation process.
        ///
        /// @return A property for the caches directory
        @InputDirectory DirectoryProperty getCachesDir();

        /// Returns an action that sets the default values for the parameters of the artifact access transformer.
        ///
        /// @param project The project using AccessTransformers
        /// @apiNote Using this in [org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)]
        /// after the project has evaluated **will not** populate some crucial defaults, as the project is inaccessible
        /// after configuration. If you use this, make sure you do before the project has finished evaluation so that
        /// the property values can be finalized into the cache.
        static Action<? super Parameters> defaults(Project project) {
            return defaults(project, it -> { });
        }

        /// Returns an action that sets the default values for the parameters of the artifact access transformer.
        ///
        /// @param project The project using AccessTransformers
        /// @param action  An action to run after the defaults are set
        /// @apiNote Using this in [org.gradle.api.artifacts.dsl.DependencyHandler#registerTransform(Class, Action)]
        /// after the project has evaluated **will not** populate some crucial defaults, as the project is inaccessible
        /// after configuration. If you use this, make sure you do before the project has finished evaluation so that
        /// the property values can be finalized into the cache.
        static Action<? super Parameters> defaults(Project project, Action<? super Parameters> action) {
            var plugin = project.getPlugins().getPlugin(AccessTransformersPlugin.class);
            return parameters -> {
                parameters.getClasspath().from(plugin.getTool(Tools.ACCESSTRANSFORMERS));
                parameters.getMainClass().value(Tools.ACCESSTRANSFORMERS.getMainClass());
                parameters.getJavaLauncher().value(Util.launcherFor(project, Tools.ACCESSTRANSFORMERS.getJavaVersion()).map(Util.LAUNCHER_EXECUTABLE));
                parameters.getArgs().value(Constants.ACCESSTRANSFORMERS_DEFAULT_ARGS);
                parameters.getCachesDir().value(plugin.localCaches());
                action.execute(parameters);
            };
        }
    }

    private final AccessTransformersProblems problems;

    /// The object factory provided by Gradle services.
    ///
    /// @return The object factory
    /// @see <a href="https://docs.gradle.org/current/userguide/service_injection.html#objectfactory">ObjectFactory
    /// Service Injection</a>
    protected abstract @Inject ObjectFactory getObjects();

    /// The exec operations provided by Gradle services.
    ///
    /// @return The exec operations
    /// @see <a href="https://docs.gradle.org/current/userguide/service_injection.html#execoperations">ExecOperations
    /// Service Injection</a>
    protected abstract @Inject ExecOperations getExecOperations();

    /// The default constructor that is invoked by Gradle to instantiate this transform action.
    public ArtifactAccessTransformer() {
        this.problems = this.getObjects().newInstance(AccessTransformersProblems.class);
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
    /// - If the [AccessTransformers configuration][Parameters#getConfig()] is unspecified or does not exist, this
    /// transformation is skipped entirely.
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
        var parameters = this.getParameters();

        // inputs
        var inJar = this.getInputArtifact().get().getAsFile();
        var atFile = parameters.getConfig().map(RegularFile::getAsFile).get();

        // outputs
        var outJarName = inJar.getName().replace(".jar", "-at.jar");
        var outJar = this.file(outJarName);
        var logFile = this.file(outJar.getName() + ".log");

        // caches
        var cachesDir = parameters.getCachesDir();

        // aforementioned in-house caching
        var cache = new HashStore(cachesDir.get().getAsFile())
            .load(cachesDir.file(outJarName + ".cache").get().getAsFile())
            .add("atFile", atFile)
            .add("inJar", inJar)
            .add("outJar", outJar);

        if (outJar.exists() && cache.isSame()) {
            LOGGER.info("Access transformer output up-to-date, skipping transformation for {}", inJar.getName());
        } else {
            LOGGER.info("Access transformer started. Input jar: {}", inJar.getAbsolutePath());
            var result = this.getExecOperations().javaexec(exec -> {
                var logLevel = parameters.getLogLevel().get();
                var stream = Util.toLog(s -> LOGGER.log(logLevel, s));
                exec.setStandardOutput(stream);
                exec.setErrorOutput(stream);

                exec.setExecutable(parameters.getJavaLauncher().get());
                exec.setClasspath(parameters.getClasspath());
                Util.setOptional(exec.getMainClass(), parameters.getMainClass());

                var substitutions = Map.of(
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
                result.rethrowFailure().assertNormalExitValue();
            } catch (ProcessExecutionException e) {
                throw this.problems.accessTransformerFailed(e, inJar, atFile, logFile);
            }

            // TODO [AccessTransformers] If no changes were made, do not create a "modified" output jar
            /*
            try {
                if (Arrays.equals(ResourceGroovyMethods.getBytes(inJar), ResourceGroovyMethods.getBytes(outJar))) {
                    this.problems.reportAccessTransformerOutputIdentical(inJar, atFile, outJar, logFile);
                } else {
                    LOGGER.info("Access transformer completed. Output jar: {}", outJar.getAbsolutePath());
                }
            } catch (IOException e) {
                this.problems.reportAccessTransformerCannotValidateOutput(e, inJar, atFile, outJar, logFile);
            }
             */
            LOGGER.info("Access transformer completed. Output jar: {}", outJar.getAbsolutePath());

            cache.add("outJar", outJar).save();
        }

        // transform output
        var output = outputs.file(outJarName).toPath();

        try {
            Files.copy(
                outJar.toPath(),
                output,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw this.problems.accessTransformerCannotWriteOutput(e, output);
        }
    }

    /// Validates the given AccessTransformer configuration file to ensure it is ready for use by the artifact
    /// transformer.
    ///
    /// @param project    The project using AccessTransformers
    /// @param dependency The dependency to be transformed
    /// @param config     The AccessTransformer configuration file
    /// @throws RuntimeException If validation of the configuration file failed
    public static void validateConfig(Project project, Object dependency, RegularFileProperty config) {
        validateConfig(project.getObjects(), project.getProviders(), dependency, config);
    }

    /// Validates the given AccessTransformer configuration file to ensure it is ready for use by the artifact
    /// transformer.
    ///
    /// @param objects    The object factory for debugging
    /// @param providers  The provider factory for debugging
    /// @param dependency The dependency to be transformed
    /// @param config     The AccessTransformer configuration file
    /// @throws RuntimeException If validation of the configuration file failed
    public static void validateConfig(ObjectFactory objects, ProviderFactory providers, Object dependency, RegularFileProperty config) {
        validateConfig(objects.newInstance(AccessTransformersProblems.class), providers, dependency, config);
    }

    static void validateConfig(AccessTransformersProblems problems, ProviderFactory providers, Object dependency, RegularFileProperty atFileProperty) {
        var dependencyToString = dependency instanceof Dependency d ? Util.toString(d) : dependency.toString();

        // check that consumer has defined the config
        RegularFile atFileSource;
        try {
            atFileSource = atFileProperty.get();
        } catch (IllegalStateException e) {
            throw problems.accessTransformerConfigNotDefined(new RuntimeException("Failed to resolve config file property", e), dependencyToString);
        }

        // check that the file exists
        var atFile = atFileSource.getAsFile();
        var atFilePath = atFile.getPath();
        if (!atFile.exists())
            throw problems.accessTransformerConfigMissing(new RuntimeException(new FileNotFoundException("Config file does not exist at " + atFilePath)), dependencyToString, atFilePath);

        // check that the file can be read and isn't empty
        String atFileContents;
        try {
            atFileContents = providers.fileContents(atFileSource).getAsText().get();
        } catch (Throwable e) {
            throw problems.accessTransformerConfigUnreadable(new RuntimeException(new IOException("Failed to read config file at " + atFilePath, e)), dependencyToString, atFilePath);
        }
        if (atFileContents.isBlank())
            throw problems.accessTransformerConfigEmpty(new IllegalStateException("Config file must not be blank at " + atFilePath), dependencyToString, atFilePath);
    }

    /// Uses [String#replace(CharSequence, CharSequence)] but with pre-compiled patterns provided in the given map.
    ///
    /// @param s             The string to replace
    /// @param substitutions The map of substitutions to use
    /// @see String#replace(CharSequence, CharSequence)
    private static String replace(String s, Map<Pattern, String> substitutions) {
        for (var entry : substitutions.entrySet()) {
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
        return this.getParameters().getCachesDir().file(path).map(this.problems.ensureFileLocation()).get().getAsFile();
    }
}
