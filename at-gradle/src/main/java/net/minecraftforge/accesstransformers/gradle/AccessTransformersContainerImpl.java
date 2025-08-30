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
import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;
import java.io.File;
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
                    parameters.getClasspath().setFrom(this.options.classpath);
                    parameters.getMainClass().set(this.options.mainClass);
                    parameters.getJavaLauncher().set(this.options.javaLauncher);
                    parameters.getArgs().set(this.options.args);
                    parameters.getConfig().set(this.options.config);

                    parameters.getCachesDir().convention(this.plugin.localCaches());
                });

                this.setAttributes(spec.getFrom(), false);
                this.setAttributes(spec.getTo(), true);
            });
        }));
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
    public void options(Action<? super AccessTransformersContainer.Options> action) {
        action.execute(this.options);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        return this.project.getDependencies().create(dependencyNotation, closure.compose(Closures.<Dependency>unaryOperator(dependency -> {
            if (dependency instanceof HasConfigurableAttributes<?> d) {
                d.attributes(a -> a.attribute(this.attribute, true));
            } else {
                this.problems.reportIllegalTargetDependency(dependency);
            }

            return dependency;
        })));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Provider<?> dep(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
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
            // Rationale: single dependencies may have additional data that must be calculated at configuration time
            // The most obvious example being usage of DependencyHandler#variantOf.
            // If the dependency isn't created immediately, that information is lost when copying the base dependency (i don't know why)
            // It's a rather negligible performance hit and FG6 was already doing this anyway, so it's not a big deal.
            // Still going to return a Provider<?> though, since we also handle bundles in this method
            return this.getObjects().property(Dependency.class).value(dependencyNotation.map(d -> this.dep(d, closure)));
        }
    }

    private static class MappedExternalModuleDependencyBundle extends ArrayList<MinimalExternalModuleDependency> implements ExternalModuleDependencyBundle {
        private static final @Serial long serialVersionUID = -5641567719847374245L;
    }

    static abstract class OptionsImpl implements AccessTransformersContainerInternal.Options {
        private final Project project;

        private FileCollection classpath;
        private final Property<String> mainClass;
        private final Property<String> javaLauncher;
        private final ListProperty<String> args;
        private final RegularFileProperty config;

        protected abstract @Inject ObjectFactory getObjects();
        protected abstract @Inject ProviderFactory getProviders();

        @Inject
        public OptionsImpl(Project project) {
            this.project = project;

            var plugin = project.getPlugins().getPlugin(AccessTransformersPlugin.class);

            this.classpath = this.getObjects().fileCollection().from(plugin.getTool(Tools.ACCESSTRANSFORMERS));
            this.mainClass = this.getObjects().property(String.class)/*.convention(Tools.ACCESSTRANSFORMERS.getMainClass())*/;
            this.javaLauncher = this.getObjects().property(String.class).convention(Util.launcherFor(project, Tools.ACCESSTRANSFORMERS.getJavaVersion()).map(Util.LAUNCHER_EXECUTABLE));
            this.args = this.getObjects().listProperty(String.class).convention(Constants.AT_DEFAULT_ARGS);
            this.config = this.getObjects().fileProperty();
        }

        @Override
        public void setClasspath(FileCollection files) {
            this.classpath = files;
        }

        @Override
        public void setClasspath(
            @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.dsl.DependencyHandler")
            Closure<? extends Dependency> dependency
        ) {
            this.classpath = this.project.getConfigurations().detachedConfiguration().withDependencies(
                dependencies -> dependencies.addLater(this.getProviders().provider(
                    () -> Closures.<Dependency>invoke(dependency, this.project.getDependencies()).copy()
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

        @Override
        public void setJavaLauncher(Provider<? extends JavaLauncher> javaLauncher) {
            this.javaLauncher.set(javaLauncher.map(Util.LAUNCHER_EXECUTABLE));
        }

        @Override
        public void setJavaLauncher(JavaLauncher javaLauncher) {
            this.javaLauncher.set(this.getProviders().provider(() -> javaLauncher).map(Util.LAUNCHER_EXECUTABLE));
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
            this.config.fileProvider(this.getProviders().provider(() -> {
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
            this.config.fileProvider(this.getProviders().provider(
                // if Project#file becomes inaccessible, use ProjectLayout#files
                () -> this.project.file(configFile, PathValidation.FILE)
            ));
        }
    }
}
