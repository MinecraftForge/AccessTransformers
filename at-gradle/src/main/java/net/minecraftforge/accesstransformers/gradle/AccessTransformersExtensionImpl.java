/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.Closures;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

abstract class AccessTransformersExtensionImpl implements AccessTransformersExtensionInternal {
    private static final Logger LOGGER = Logging.getLogger(AccessTransformersExtension.class);

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

        project.getConfigurations().configureEach(c -> c.withDependencies(d -> this.apply(c)));
    }

    private void apply(Configuration configuration) {
        var hierarchy = configuration.getHierarchy();

        // AccessTransformers can be configured for constraints to not pollute the dependency tree.
        // We need to factor in these constraints, especially since it's possible for constraints to exist alongside dependencies.
        // The stream here shouldn't be too slow since they are static.
        var dependencyConstraints = hierarchy
            .stream()
            .flatMap(c -> c.getDependencyConstraints().matching(AccessTransformersConfigurationInternal::has).stream())
            .collect(Collectors.toSet());

        Collection<ProjectDependency> projectDependencies = new LinkedList<>();
        Map<ModuleVersionSelector, Dependency> moduleDependencies = new LinkedHashMap<>();

        hierarchy
            .stream()
            .flatMap(c -> c.getDependencies().matching(AccessTransformersConfigurationInternal::has).stream())
            .forEach(dependency -> {
                if (dependency instanceof ProjectDependency p) {
                    projectDependencies.add(p);
                } else if (dependency instanceof ModuleVersionSelector m) {
                    moduleDependencies.put(m, dependency);
                } else {
                    this.problems.reportIllegalTargetDependency(dependency);
                }
            });

        for (var dependency : projectDependencies) {
            var configs = AccessTransformersConfigurationInternal.get(dependency);
            var attributes = new ArrayList<Attribute<Boolean>>(configs.size());
            for (var config : configs) {
                attributes.add(this.register(dependency, config));
            }

            this.applyToProject(configuration, dependency.getPath(), attributes);
        }

        for (var entry : moduleDependencies.entrySet()) {
            var dependency = entry.getValue();

            var module = entry.getKey();
            var moduleSelector = "%s%s".formatted(module.getModule().toString(), module.getVersion() != null ? ":" + module.getVersion() : "");

            var configs = AccessTransformersConfigurationInternal.get(dependency);
            var attributes = new ArrayList<Attribute<Boolean>>(configs.size());
            for (var config : configs) {
                attributes.add(this.register(dependency, config));
            }

            // Check if we have a constraint that matches the dependency, so we can combine AT attributes.
            // It's a tad paranoid, but is better than risking having multiple dependency substitutions for the same module.
            {
                var itor = dependencyConstraints.iterator();
                for (var constraint = itor.next(); itor.hasNext(); constraint = itor.next()) {
                    if (!(Objects.equals(constraint.getModule(), module.getModule()) && Objects.equals(constraint.getVersion(), module.getVersion())))
                        continue;

                    itor.remove();
                    var constraintConfigs = AccessTransformersConfigurationInternal.get(constraint);
                    for (var config : constraintConfigs) {
                        attributes.add(this.register(constraint, config));
                    }
                }
            }

            this.applyToDependency(configuration, dependency, moduleSelector, attributes);
        }

        dependencyConstraints.stream().collect(Collectors.groupingBy(ModuleVersionSelector::getModule)).forEach((module, allConstraints) -> {
            String defaultVersion;
            {
                String version = "unspecified";
                for (var constraint : allConstraints) {
                    if (constraint.getVersion() != null) continue;

                    version = configuration
                        .getAllDependencies()
                        .stream()
                        .filter(dependency -> dependency.getVersion() != null
                            && Objects.equals(dependency.getGroup(), module.getGroup())
                            && Objects.equals(dependency.getName(), module.getName()))
                        .map(Dependency::getVersion)
                        .max(Comparator.naturalOrder())
                        .orElse("unspecified");
                    break;
                }

                defaultVersion = version;
            }

            allConstraints.stream().collect(Collectors.groupingBy(constraint -> {
                var version = constraint.getVersion();
                return version != null ? version : defaultVersion;
            })).forEach((version, constraints) -> {
                if ("unspecified".equals(version))
                    version = null;

                var attributes = constraints
                    .stream()
                    .flatMap(constraint -> AccessTransformersConfigurationInternal
                        .get(constraint)
                        .stream()
                        .map(config -> this.register(constraint, config)))
                    .toList();

                var moduleSelector = module + (version != null ? ":" + version : "");
                this.applyToConstraint(configuration, module, moduleSelector, attributes);
            });
        });
    }

    private void applyToProject(Configuration configuration, String path, Iterable<Attribute<Boolean>> attributes) {
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var component = s.project(path);
            var substitute = s.substitute(component);
            var variant = s.variant(component, v -> v.attributes(a -> {
                for (var attribute : attributes) {
                    a.attribute(attribute, true);
                }
            }));
            substitute.using(variant);
        });
    }

    private void applyToDependency(Configuration configuration, Dependency dependency, String moduleSelector, Iterable<Attribute<Boolean>> attributes) {
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var component = s.module(moduleSelector);
            var substitute = s.substitute(component);
            var variant = s.variant(component, v -> v.attributes(a -> {
                for (var attribute : attributes) {
                    a.attribute(attribute, true);
                }
            }));
            try {
                substitute.using(variant);
            } catch (InvalidUserDataException e) {
                var message = e.getMessage();
                if (message != null && StringGroovyMethods.containsIgnoreCase(message, "must specify version")) {
                    this.problems.reportDependencyWithoutVersion(e, dependency);
                }

                throw e;
            }
        });
    }

    private void applyToConstraint(Configuration configuration, ModuleIdentifier module, String moduleSelector, Iterable<Attribute<Boolean>> attributes) {
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var component = s.module(moduleSelector);
            var substitute = s.substitute(component);
            var variant = s.variant(component, v -> v.attributes(a -> {
                for (var attribute : attributes) {
                    a.attribute(attribute, true);
                }
            }));
            try {
                substitute.using(variant);
            } catch (InvalidUserDataException e) {
                var message = e.getMessage();
                if (message != null && StringGroovyMethods.containsIgnoreCase(message, "must specify version")) {
                    this.problems.reportDependencyConstraintWithoutVersion(e, module, configuration);
                }

                throw e;
            }
        });
    }

    private Attribute<Boolean> register(Object dependency, AccessTransformersConfigurationInternal config) {
        this.validateConfigFile(dependency, config.getConfig());

        int index = getIndex();
        var attribute = Attribute.of("net.minecraftforge.accesstransformers.automatic." + index, Boolean.class);
        this.project.dependencies(Closures.<DependencyHandler>consumer(this, dependencies -> {
            dependencies.attributesSchema(attributesSchema -> attributesSchema.attribute(attribute));

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

    private void setAttributes(AttributeContainer attributes, Attribute<Boolean> attribute, boolean value) {
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                  .attribute(Category.CATEGORY_ATTRIBUTE, this.getObjects().named(Category.class, Category.LIBRARY))
                  .attribute(attribute, value);
    }

    private int getIndex() {
        var gradle = this.project.getGradle();
        var ext = gradle.getExtensions().getExtraProperties();
        int index = ext.has(INDEX_EXT_PROPERTY) ?
            (int) Objects.requireNonNull(ext.get(INDEX_EXT_PROPERTY), "Index ext property should never be null!") + 1
            : 0;
        gradle.getExtensions().getExtraProperties().set(INDEX_EXT_PROPERTY, index);
        return index;
    }

    private void validateConfigFile(Object dependency, RegularFileProperty atFileProperty) {
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
    public void configure(Object dependency) {
        this.getContainer().configure(dependency);
    }

    @Override
    public void configure(Object dependency, Action<? super AccessTransformersConfiguration> action) {
        this.getContainer().configure(dependency, action);
    }
}
