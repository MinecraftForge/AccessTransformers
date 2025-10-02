/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Objects;

abstract class AccessTransformersExtensionImpl implements AccessTransformersExtensionInternal {
    private final Project project;

    private @Nullable AccessTransformersContainer container;

    @Inject
    public AccessTransformersExtensionImpl(Project project) {
        this.project = project;
        project.afterEvaluate(AccessTransformersExtensionImpl::finish);
    }

    @SuppressWarnings({"DataFlowIssue", "unchecked"})
    private static void finish(Project project) {
        var configurations = project.getConfigurations();
        NamedDomainObjectProvider<DependencyScopeConfiguration> accessTransformersDependencyConstraints;
        {
            NamedDomainObjectProvider<DependencyScopeConfiguration> c;
            try {
                c = configurations.dependencyScope("accessTransformersDependencyConstraints", configuration -> {
                    configuration.setDescription("Dependency constraints to enforce access transformer usage.");
                });
            } catch (InvalidUserDataException e) {
                c = configurations.named("accessTransformerDependencyConstraints", DependencyScopeConfiguration.class);
            }
            accessTransformersDependencyConstraints = c;
        }

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().forEach(sourceSet -> {
            var dependencies = Util.collect(configurations, sourceSet, true, dependency -> {
                var ext = ((ExtensionAware) dependency).getExtensions().getExtraProperties();
                return ext.has("__at_attribute");
            });

            dependencies.forEach(dependency -> {
                var ext = ((ExtensionAware) dependency).getExtensions().getExtraProperties();
                Object dependencyNotation = dependency;
                if (dependencyNotation instanceof ProviderConvertible<?>) {
                    dependencyNotation = ((ProviderConvertible<?>) dependencyNotation).asProvider();
                }
                if (dependencyNotation instanceof Provider<?>) {
                    dependencyNotation = ((Provider<?>) dependencyNotation).get();
                }
                if (!(dependencyNotation instanceof MinimalExternalModuleDependency) && !(dependencyNotation instanceof ProjectDependency) && dependencyNotation instanceof ModuleVersionSelector module) {
                    dependencyNotation = module.getModule().toString();
                }

                accessTransformersDependencyConstraints.get().getDependencyConstraints().add(project.getDependencies().getConstraints().create(dependencyNotation, constraint -> {
                    constraint.attributes(a -> a.attribute((Attribute<Boolean>) ext.get("__at_attribute"), true));
                }));
                Util.forClasspathConfigurations(configurations, sourceSet, c -> c.extendsFrom(accessTransformersDependencyConstraints.get()));
            });
        });
    }

    @Override
    public AccessTransformersContainer register(
        Attribute<Boolean> attribute,
        Action<? super AccessTransformersContainer.Options> options
    ) {
        return this.container = AccessTransformersContainer.register(this.project, attribute, options);
    }

    private AccessTransformersContainer getContainer() {
        try {
            return Objects.requireNonNull(this.container);
        } catch (NullPointerException e) {
            throw new IllegalStateException("Cannot configure options for AccessTransformers without having registered one! Use accessTransformers#register in your project.", e);
        }
    }

    @Override
    public Attribute<Boolean> getAttribute() {
        return this.getContainer().getAttribute();
    }

    @Override
    public Options getOptions() {
        return this.getContainer().getOptions();
    }

    @Override
    public Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
    ) {
        return this.getContainer().dep(dependencyNotation, closure);
    }

    @Override
    public Provider<?> dep(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> closure
    ) {
        return this.getContainer().dep(dependencyNotation, closure);
    }
}
