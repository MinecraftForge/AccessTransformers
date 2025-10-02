/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;
import java.util.LinkedList;
import java.util.List;

abstract class AccessTransformersContainerImpl implements AccessTransformersContainerInternal {
    private final AccessTransformersProblems problems;

    private final OptionsImpl options;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public AccessTransformersContainerImpl(
        Project project,
        Action<? super AccessTransformersContainer.Options> options
    ) {
        this.problems = this.getObjects().newInstance(AccessTransformersProblems.class);

        options.execute(this.options = this.getObjects().newInstance(OptionsImpl.class, project));
    }

    @Override
    public AccessTransformersContainer.Options getOptions() {
        return this.options;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Dependency dependency, Action<? super AccessTransformersConfiguration> action) {
        if (!(dependency instanceof HasConfigurableAttributes))
            this.problems.reportIllegalTargetDependency(dependency);

        var ext = ((ExtensionAware) dependency).getExtensions().getExtraProperties();
        var attributes = ext.has("__accessTransformers_configs")
            ? (List<AccessTransformersConfigurationImpl>) ext.get("__accessTransformers_configs")
            : null;
        if (attributes == null) {
            attributes = new LinkedList<>();
            ext.set("__accessTransformers_configs", attributes);
        }

        var config = new AccessTransformersConfigurationImpl(this.getObjects().fileProperty().convention(this.options.config), this.options);
        action.execute(config);
        attributes.add(config);
    }

    static abstract class OptionsImpl implements Options {
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
