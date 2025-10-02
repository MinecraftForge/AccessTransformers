/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

abstract class AccessTransformersExtensionImpl implements AccessTransformersExtensionInternal {
    private final Project project;

    private final AccessTransformersPlugin plugin;
    private final AccessTransformersProblems problems = this.getObjects().newInstance(AccessTransformersProblems.class);

    private final NamedDomainObjectProvider<DependencyScopeConfiguration> constraintsConfiguration;

    private @Nullable AccessTransformersContainer container;

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getLayout();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public AccessTransformersExtensionImpl(AccessTransformersPlugin plugin, Project project) {
        this.project = project;
        this.plugin = plugin;

        var configurations = project.getConfigurations();
        {
            NamedDomainObjectProvider<DependencyScopeConfiguration> c;
            try {
                c = configurations.dependencyScope("accessTransformersDependencyConstraints", configuration -> {
                    configuration.setDescription("Dependency constraints to enforce access transformer usage.");
                });
            } catch (InvalidUserDataException e) {
                c = configurations.named("accessTransformerDependencyConstraints", DependencyScopeConfiguration.class);
            }
            this.constraintsConfiguration = c;
        }

        // FUCK
        project.getGradle().projectsEvaluated(gradle -> this.finish(project));
    }

    @SuppressWarnings({"unchecked", "DataFlowIssue"})
    private void finish(Project project) {
        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().forEach(sourceSet ->
            Util.forEachClasspath(project.getConfigurations(), sourceSet, c -> c.extendsFrom(this.constraintsConfiguration.get()))
        );

        project.getConfigurations().forEach(c -> c.getDependencies().matching(it ->
            ((ExtensionAware) it).getExtensions().getExtraProperties().has("__accessTransformers_configs")
        ).forEach(dependency -> {
            Object dependencyNotation = dependency;
            if (!(dependencyNotation instanceof MinimalExternalModuleDependency)
                && !(dependencyNotation instanceof ProjectDependency)
                && dependencyNotation instanceof ModuleVersionSelector module) {
                dependencyNotation = module.getModule().toString();
            }

            var configs = (List<AccessTransformersConfigurationImpl>) ((ExtensionAware) dependency).getExtensions().getExtraProperties().get("__accessTransformers_configs");
            var attributes = new ArrayList<Attribute<Boolean>>(configs.size());
            for (var i = 0; i < configs.size(); i++) {
                var config = configs.get(i);
                attributes.add(this.register(i, dependency, config));
            }

            this.constraintsConfiguration.get().getDependencyConstraints().add(this.project.getDependencies().getConstraints().create(dependencyNotation, constraint ->
                constraint.attributes(a -> {
                    for (var attribute : attributes) a.attribute(attribute, true);
                })
            ));
        }));
    }

    private Attribute<Boolean> register(int index, Dependency dependency, AccessTransformersConfigurationImpl config) {
        this.validateATFile(dependency, config.getConfig());

        var attribute = Attribute.of("net.minecraftforge.accesstransformers.automatic." + index, Boolean.class);
        this.project.dependencies(Closures.<DependencyHandler>consumer(this, dependencies -> {
            dependencies.attributesSchema(attributesSchema -> {
                if (attributesSchema.hasAttribute(attribute))
                    throw new IllegalStateException("Another project in this build is already using this attribute for AccessTransformers: " + attribute);

                attributesSchema.attribute(attribute);
            });

            dependencies.getArtifactTypes().named(
                ArtifactTypeDefinition.JAR_TYPE,
                type -> type.getAttributes().attribute(attribute, false)
            );

            dependencies.registerTransform(ArtifactAccessTransformer.class, spec -> {
                spec.parameters(parameters -> {
                    parameters.getConfig().set(config.getConfig());
                    parameters.getLogLevel().set(config.options().getLogLevel());
                    parameters.getClasspath().setFrom(config.options().getClasspath());
                    parameters.getMainClass().set(config.options().getMainClass());
                    parameters.getJavaLauncher().set(config.options().getJavaLauncher().map(Util.LAUNCHER_EXECUTABLE));
                    parameters.getArgs().set(config.options().getArgs().map(Util::listToString));

                    parameters.getCachesDir().convention(this.plugin.localCaches());
                });

                setAttributes(spec.getFrom(), attribute, false);
                setAttributes(spec.getTo(), attribute, true);
            });
        }));

        return attribute;
    }

    private static void setAttributes(AttributeContainer attributes, Attribute<Boolean> attribute, boolean value) {
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                  .attribute(attribute, value);
    }

    private void validateATFile(Dependency dependency, RegularFileProperty atFileProperty) {
        // check that consumer has defined the config
        RegularFile atFileSource;
        try {
            atFileSource = atFileProperty.get();
        } catch (IllegalStateException e) {
            throw this.problems.accessTransformerConfigNotDefined(new RuntimeException("Failed to resolve config file property", e), dependency);
        }

        // check that the file exists
        var atFile = atFileSource.getAsFile();
        var atFilePath = this.getLayout().getProjectDirectory().getAsFile().toPath().relativize(atFile.toPath()).toString();
        if (!atFile.exists())
            throw this.problems.accessTransformerConfigMissing(new RuntimeException(new FileNotFoundException("Config file does not exist at " + atFilePath)), dependency, atFilePath);

        // check that the file can be read and isn't empty
        String atFileContents;
        try {
            atFileContents = this.getProviders().fileContents(atFileSource).getAsText().get();
        } catch (Throwable e) {
            throw this.problems.accessTransformerConfigUnreadable(new RuntimeException(new IOException("Failed to read config file at " + atFilePath, e)), dependency, atFilePath);
        }
        if (atFileContents.isBlank())
            throw this.problems.accessTransformerConfigEmpty(new IllegalStateException("Config file must not be blank at " + atFilePath), dependency, atFilePath);
    }

    @Override
    public AccessTransformersContainer register(Action<? super AccessTransformersContainer.Options> options) {
        return this.container = AccessTransformersContainer.register(this.project, options);
    }

    private AccessTransformersContainer getContainer() {
        return this.container == null ? this.container = AccessTransformersContainer.register(this.project, it -> { }) : this.container;
    }

    @Override
    public Options getOptions() {
        return this.getContainer().getOptions();
    }

    @Override
    public void options(Action<? super Options> action) {
        this.getContainer().options(action);
    }

    @Override
    public void configure(Dependency dependency, Action<? super AccessTransformersConfiguration> action) {
        this.getContainer().configure(dependency, action);
    }
}
