/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class AccessTransformersExtensionImpl implements AccessTransformersExtensionInternal {
    private final Project project;

    private final AccessTransformersPlugin plugin;
    private final AccessTransformersProblems problems = this.getObjects().newInstance(AccessTransformersProblems.class);

    private @Nullable AccessTransformersContainer container;

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getLayout();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public AccessTransformersExtensionImpl(AccessTransformersPlugin plugin, Project project) {
        this.project = project;
        this.plugin = plugin;

        var configurations = project.getConfigurations();
        configurations.configureEach(configuration -> configuration.withDependencies(dependencies -> {
            var hierarchy = configuration.getHierarchy();

            var constraints = hierarchy
                .stream()
                .flatMap(c -> c.getDependencyConstraints().matching(it ->
                    ((ExtensionAware) it).getExtensions().getExtraProperties().has("__accessTransformers_configs")
                ).stream())
                .collect(Collectors.toMap(ModuleVersionSelector::getModule, Function.identity()));

            for (var c : hierarchy) {
                for (var dependency : c.getDependencies().matching(it ->
                    ((ExtensionAware) it).getExtensions().getExtraProperties().has("__accessTransformers_configs")
                )) {
                    var configs = (List<AccessTransformersConfigurationImpl>) ((ExtensionAware) dependency).getExtensions().getExtraProperties().get("__accessTransformers_configs");
                    var attributes = new ArrayList<Attribute<Boolean>>(configs.size());
                    for (var config : configs) {
                        attributes.add(this.register(dependency, config));
                    }

                    if (dependency instanceof ModuleVersionSelector m) {
                        var constraint = constraints.remove(m.getModule());
                        if (constraint != null) {
                            var constraintConfigs = (List<AccessTransformersConfigurationImpl>) ((ExtensionAware) constraint).getExtensions().getExtraProperties().get("__accessTransformers_configs");
                            for (var config : constraintConfigs) {
                                attributes.add(this.register(constraint, config));
                            }
                        }
                    }

                    Function<DependencySubstitutions, ComponentSelector> componentSelector;
                    if (dependency instanceof ProjectDependency p) {
                        var path = p.getPath();
                        componentSelector = s -> s.project(path);
                    } else if (dependency instanceof ModuleVersionSelector m) {
                        var module = "%s%s".formatted(m.getModule().toString(), m.getVersion() != null ? ":" + m.getVersion() : "");
                        componentSelector = s -> s.module(module);
                    } else {
                        this.problems.reportIllegalTargetDependency(dependency);
                        return;
                    }

                    this.apply(configuration, componentSelector, attributes);
                }
            }

            for (var constraint : constraints.values()) {
                var configs = (List<AccessTransformersConfigurationImpl>) ((ExtensionAware) constraint).getExtensions().getExtraProperties().get("__accessTransformers_configs");
                var attributes = new ArrayList<Attribute<Boolean>>(configs.size());
                for (var config : configs) {
                    attributes.add(this.register(constraint, config));
                }

                var module = "%s%s".formatted(constraint.getModule().toString(), constraint.getVersion() != null ? ":" + constraint.getVersion() : "");
                this.apply(configuration, s -> s.module(module), attributes);
            }
        }));
    }

    private void apply(Configuration configuration, Function<DependencySubstitutions, ComponentSelector> componentSelector, Iterable<Attribute<Boolean>> attributes) {
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var component = componentSelector.apply(s);
            s.substitute(component).using(s.variant(component, variant -> {
                variant.attributes(a -> {
                    for (var attribute : attributes) {
                        a.attribute(attribute, true);
                    }
                });
            }));
        });
    }

    private Attribute<Boolean> register(Object dependency, AccessTransformersConfigurationImpl config) {
        this.validateATFile(dependency, config.getConfig());

        var ext = this.project.getGradle().getExtensions().getExtraProperties();
        int index = ext.has("__accessTransformers_automatic_index") ? (int) ext.get("__accessTransformers_automatic_index") + 1 : 0;
        this.project.getGradle().getExtensions().getExtraProperties().set("__accessTransformers_automatic_index", index);

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

    private void validateATFile(Object dependency, RegularFileProperty atFileProperty) {
        var dependencyToString = dependency instanceof Dependency d ? Util.toString(d) : dependency.toString();

        // check that consumer has defined the config
        RegularFile atFileSource;
        try {
            atFileSource = atFileProperty.get();
        } catch (IllegalStateException e) {
            throw this.problems.accessTransformerConfigNotDefined(new RuntimeException("Failed to resolve config file property", e), dependencyToString);
        }

        // check that the file exists
        var atFile = atFileSource.getAsFile();
        var atFilePath = this.getLayout().getProjectDirectory().getAsFile().toPath().relativize(atFile.toPath()).toString();
        if (!atFile.exists())
            throw this.problems.accessTransformerConfigMissing(new RuntimeException(new FileNotFoundException("Config file does not exist at " + atFilePath)), dependencyToString, atFilePath);

        // check that the file can be read and isn't empty
        String atFileContents;
        try {
            atFileContents = this.getProviders().fileContents(atFileSource).getAsText().get();
        } catch (Throwable e) {
            throw this.problems.accessTransformerConfigUnreadable(new RuntimeException(new IOException("Failed to read config file at " + atFilePath, e)), dependencyToString, atFilePath);
        }
        if (atFileContents.isBlank())
            throw this.problems.accessTransformerConfigEmpty(new IllegalStateException("Config file must not be blank at " + atFilePath), dependencyToString, atFilePath);
    }

    @Override
    public AccessTransformersContainer register(Action<? super AccessTransformersContainer.Options> options) {
        return this.container = AccessTransformersContainerInternal.register(this.project, options);
    }

    private AccessTransformersContainer getContainer() {
        return this.container == null ? this.container = AccessTransformersContainerInternal.register(this.project, it -> { }) : this.container;
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
    public void configure(Object dependency, Action<? super AccessTransformersConfiguration> action) {
        this.getContainer().configure(dependency, action);
    }
}
