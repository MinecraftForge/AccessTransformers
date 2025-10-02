/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.gradleutils.shared.Closures;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Objects;

abstract class AccessTransformersContainerImpl implements AccessTransformersContainerInternal {
    private final AccessTransformersPlugin plugin;
    private final AccessTransformersProblems problems;

    private final Project project;
    private final Attribute<Boolean> attribute;
    private final OptionsImpl options;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProjectLayout getProjectLayout();
    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public AccessTransformersContainerImpl(
        Project project,
        Attribute<Boolean> attribute,
        Action<? super AccessTransformersContainer.Options> options
    ) {
        this.project = project;

        this.plugin = project.getPlugins().getPlugin(AccessTransformersPlugin.class);
        this.problems = this.getObjects().newInstance(AccessTransformersProblems.class);

        this.attribute = Objects.requireNonNull(attribute);
        options.execute(this.options = this.getObjects().newInstance(OptionsImpl.class, project));

        project.afterEvaluate(this::finish);
    }

    private void finish(Project project) {
        this.validateATFile(this.attribute, this.options.config);

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
                    parameters.getConfig().set(this.options.config);
                    parameters.getLogLevel().set(this.options.logLevel);
                    parameters.getClasspath().setFrom(this.options.classpath);
                    parameters.getMainClass().set(this.options.mainClass);
                    parameters.getJavaLauncher().set(this.options.javaLauncher.map(Util.LAUNCHER_EXECUTABLE));
                    parameters.getArgs().set(this.options.args.map(Util::listToString));

                    parameters.getCachesDir().convention(this.plugin.localCaches());
                });

                this.setAttributes(spec.getFrom(), false);
                this.setAttributes(spec.getTo(), true);
            });
        }));
    }

    private void validateATFile(Attribute<Boolean> attribute, RegularFileProperty atFileProperty) {
        // check that consumer has defined the config
        RegularFile atFileSource;
        try {
            atFileSource = atFileProperty.get();
        } catch (IllegalStateException e) {
            throw this.problems.accessTransformerConfigNotDefined(new RuntimeException("Failed to resolve config file property", e), attribute);
        }

        // check that the file exists
        var atFile = atFileSource.getAsFile();
        var atFilePath = this.getProjectLayout().getProjectDirectory().getAsFile().toPath().relativize(atFile.toPath()).toString();
        if (!atFile.exists())
            throw this.problems.accessTransformerConfigMissing(new RuntimeException(new FileNotFoundException("Config file does not exist at " + atFilePath)), attribute, atFilePath);

        // check that the file can be read and isn't empty
        String atFileContents;
        try {
            atFileContents = this.getProviders().fileContents(atFileSource).getAsText().get();
        } catch (Throwable e) {
            throw this.problems.accessTransformerConfigUnreadable(new RuntimeException(new IOException("Failed to read config file at " + atFilePath, e)), attribute, atFilePath);
        }
        if (atFileContents.isBlank())
            throw this.problems.accessTransformerConfigEmpty(new IllegalStateException("Config file must not be blank at " + atFilePath), attribute, atFilePath);
    }

    private void setAttributes(AttributeContainer attributes, boolean value) {
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                  .attribute(this.attribute, value);
    }

    @Override
    public Attribute<Boolean> getAttribute() {
        return this.attribute;
    }

    @Override
    public AccessTransformersContainer.Options getOptions() {
        return this.options;
    }

    @Override
    public Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
    ) {
        return this.project.getDependencies().create(dependencyNotation, closure.compose(Closures.<Dependency>unaryOperator(dependency -> {
            if (dependency instanceof HasConfigurableAttributes<?>) {
                var ext = ((ExtensionAware) dependency).getExtensions().getExtraProperties();
                ext.set("__at_attribute", this.attribute);
            } else {
                this.problems.reportIllegalTargetDependency(dependency);
            }

            return dependency;
        })));
    }

    @Override
    public Provider<?> dep(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
    ) {
        if (dependencyNotation.isPresent() && dependencyNotation.get() instanceof ExternalModuleDependencyBundle bundle) {
            // this provider MUST return ExternalModuleDependencyBundle
            // The only way to coerce the type of it is to use a property, since we can set the type manually on creation.
            // ProviderInternal#getType uses the generic argument to determine what type it is.
            // Provider#map and #flatMap do NOT preserve the resultant type, which fucks with adding bundles to configurations.
            return this.getObjects().property(ExternalModuleDependencyBundle.class).value(this.getProviders().provider(
                () -> DefaultGroovyMethods.collect(
                    bundle,
                    new MappedExternalModuleDependencyBundle(),
                    Closures.unaryOperator(d -> (MinimalExternalModuleDependency) this.dep(d, closure))
                )
            ));
        } else {
            // Property<Dependency> exposes the Dependency class through Gradle's internals
            return this.getObjects().property(Dependency.class).value(dependencyNotation.map(d -> this.dep(d, closure)));
        }
    }

    private static class MappedExternalModuleDependencyBundle extends ArrayList<MinimalExternalModuleDependency> implements ExternalModuleDependencyBundle {
        private static final @Serial long serialVersionUID = -5641567719847374245L;
    }

    static abstract class OptionsImpl implements AccessTransformersContainerInternal.Options {
        private final Property<LogLevel> logLevel = this.getObjects().property(LogLevel.class);
        private final RegularFileProperty config = this.getObjects().fileProperty();
        private final ConfigurableFileCollection classpath = this.getObjects().fileCollection();
        private final Property<String> mainClass = this.getObjects().property(String.class);
        private final Property<JavaLauncher> javaLauncher = this.getObjects().property(JavaLauncher.class);
        private final ListProperty<Object> args = this.getObjects().listProperty(Object.class);

        protected abstract @Inject ObjectFactory getObjects();

        @Inject
        public OptionsImpl(Project project) {
            var plugin = project.getPlugins().getPlugin(AccessTransformersPlugin.class);

            this.logLevel.convention(LogLevel.INFO);
            this.classpath.from(plugin.getTool(Tools.ACCESSTRANSFORMERS));
            //this.mainClass.convention(Tools.ACCESSTRANSFORMERS.getMainClass());
            this.javaLauncher.convention(Util.launcherFor(project, Tools.ACCESSTRANSFORMERS.getJavaVersion()));
            this.args.convention(Constants.ACCESSTRANSFORMERS_DEFAULT_ARGS);
        }

        @Override
        public RegularFileProperty getConfig() {
            return this.config;
        }

        @Override
        public Property<LogLevel> getLogLevel() {
            return this.logLevel;
        }

        @Override
        public ConfigurableFileCollection getClasspath() {
            return this.classpath;
        }

        @Override
        public Property<String> getMainClass() {
            return this.mainClass;
        }

        @Override
        public Property<JavaLauncher> getJavaLauncher() {
            return this.javaLauncher;
        }

        @Override
        public ListProperty<Object> getArgs() {
            return this.args;
        }
    }
}
