package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static net.minecraftforge.accesstransformers.gradle.AccessTransformersPlugin.LOGGER;

final class AccessTransformersContainerImpl implements AccessTransformersContainer {
    private final Project project;
    private final Attribute<Boolean> attribute;
    private final AccessTransformersContainerImpl.OptionsImpl options;

    @SuppressWarnings("rawtypes") // public-facing closure
    AccessTransformersContainerImpl(
        Project project,
        Attribute<Boolean> attribute,
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure options
    ) {
        this.attribute = Objects.requireNonNull(attribute);
        Closures.invoke(this.options = new AccessTransformersContainerImpl.OptionsImpl(this.project = project), options);

        project.afterEvaluate(this::finish);
    }

    private void finish(Project project) {
        project.dependencies(Closures.<DependencyHandler>consumer(this, dependencies -> {
            dependencies.attributesSchema(attributesSchema -> {
                if (attributesSchema.hasAttribute(this.attribute))
                    throw new IllegalStateException("Another project in this build is already using this attribute for AccessTransformers: " + this.attribute);

                attributesSchema.attribute(this.attribute);
            });

            dependencies.getArtifactTypes().named(
                ArtifactTypeDefinition.JAR_TYPE,
                type -> type.getAttributes().attribute(this.attribute, false)
            );

            dependencies.registerTransform(ArtifactAccessTransformer.class, spec -> {
                spec.parameters(parameters -> {
                    parameters.getClasspath().set(this.options.classpath);
                    parameters.getMainClass().set(this.options.mainClass);
                    parameters.getJavaLauncher().set(this.options.javaLauncher);
                    parameters.getArgs().set(this.options.args);
                    parameters.getConfig().set(this.options.config);

                    parameters.getCachesDir().convention(project.getLayout().getBuildDirectory().dir(Constants.CACHES_PATH_LOCAL).map(dir -> {
                        Path path = dir.getAsFile().toPath();
                        try {
                            Files.createDirectories(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to create directory: " + path.toAbsolutePath(), e);
                        }

                        return dir;
                    }));
                });

                this.setAttributes(spec.getFrom(), false);
                this.setAttributes(spec.getTo(), true);
            });
        }));
    }

    @SuppressWarnings("UnstableApiUsage") // ArtifactTypeDefinition#ARTIFACT_TYPE_ATTRIBUTE (stabilized in Gradle 8)
    private void setAttributes(AttributeContainer attributes, boolean value) {
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                  .attribute(this.attribute, value);
    }

    @Override
    public Attribute<Boolean> getAttribute() {
        return this.attribute;
    }

    @Override
    @SuppressWarnings("rawtypes") // public-facing closure
    public void options(
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure closure
    ) {
        Closures.invoke(this.options, closure);
    }

    @Override
    @SuppressWarnings("rawtypes") // public-facing closure
    public Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        Dependency dependency = project.getDependencies().create(dependencyNotation, closure);
        if (dependency instanceof HasConfigurableAttributes<?>) {
            ((HasConfigurableAttributes<?>) dependency).attributes(a -> a.attribute(this.attribute, true));
        } else {
            LOGGER.warn("Cannot apply access transformer attribute to dependency: {} ({})", dependency, dependency.getClass().getName());
        }
        return dependency;
    }

    private static final class OptionsImpl implements AccessTransformersContainer.Options {
        private final Project project;
        private final ProviderFactory providers;

        private @InputFiles FileCollection classpath;
        private final @Optional @Input Property<String> mainClass;
        private final @Input Property<String> javaLauncher;
        private final @Input ListProperty<String> args;
        private final @InputFile RegularFileProperty config;

        @Inject
        public OptionsImpl(Project project) {
            this.project = project;
            final ObjectFactory objects = project.getObjects();
            this.providers = project.getProviders();

            this.classpath = objects.fileCollection().from(DefaultTools.ACCESS_TRANSFORMERS);
            this.mainClass = objects.property(String.class);
            this.javaLauncher = objects.property(String.class).convention(this.defaultJavaLauncher());
            this.args = objects.listProperty(String.class).convention(Constants.AT_DEFAULT_ARGS);
            this.config = objects.fileProperty();
        }

        @Override
        public void setClasspath(FileCollection files) {
            this.classpath = files;
        }

        @Override
        public void setClasspath(
            @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.dsl.DependencyHandler")
            Closure<? extends Dependency> closure
        ) {
            this.classpath = this.project.getConfigurations().detachedConfiguration().withDependencies(
                dependencies -> dependencies.addLater(this.providers.provider(
                    () -> Closures.<Dependency>invoke(this.project.getDependencies(), closure).copy()
                ))
            );
        }

        @Override
        public void setMainClass(Provider<String> mainClass) {
            this.mainClass.set(mainClass);
        }

        @Override
        public void setMainClass(String mainClass) {
            this.mainClass.set(mainClass);
        }

        private Provider<String> defaultJavaLauncher() {
            return this.providers.provider(() -> {
                final JavaPluginExtension java = this.project.getExtensions().getByType(JavaPluginExtension.class);
                final JavaToolchainService javaToolchains = this.project.getExtensions().getByType(JavaToolchainService.class);
                final JavaLanguageVersion version = JavaLanguageVersion.of(Constants.AT_MIN_JAVA);

                JavaToolchainSpec currentToolchain = java.getToolchain();
                Provider<JavaLauncher> launcher = currentToolchain.getLanguageVersion().get().canCompileOrRun(version)
                    ? javaToolchains.launcherFor(currentToolchain)
                    : javaToolchains.launcherFor(spec -> spec.getLanguageVersion().set(version));

                return launcher.map(l -> l.getExecutablePath().toString()).get();
            });
        }

        @Override
        public void setJavaLauncher(Provider<? extends JavaLauncher> javaLauncher) {
            this.javaLauncher.set(javaLauncher.map(java -> java.getExecutablePath().toString()));
        }

        @Override
        public void setJavaLauncher(JavaLauncher javaLauncher) {
            this.javaLauncher.set(javaLauncher.getExecutablePath().toString());
        }

        @Override
        public void setArgs(Iterable<String> args) {
            this.args.set(args);
        }

        @Override
        public void setArgs(Provider<? extends Iterable<String>> args) {
            this.args.set(args);
        }

        @Override
        public void setConfig(Provider<?> configFile) {
            this.config.fileProvider(this.providers.provider(() -> {
                Object value = configFile.getOrNull();
                if (value == null)
                    return null;
                else if (value instanceof FileSystemLocation)
                    value = ((FileSystemLocation) value).getAsFile();

                // if Project#file becomes inaccessible, use ProjectLayout#files
                return this.project.file(value, PathValidation.FILE);
            }));
        }

        @Override
        public void setConfig(RegularFile configFile) {
            this.config.set(configFile);
        }

        @Override
        public void setConfig(File configFile) {
            this.config.set(configFile);
        }

        @Override
        public void setConfig(Object configFile) {
            this.config.fileProvider(this.providers.provider(
                // if Project#file becomes inaccessible, use ProjectLayout#files
                () -> this.project.file(configFile, PathValidation.FILE)
            ));
        }
    }
}
