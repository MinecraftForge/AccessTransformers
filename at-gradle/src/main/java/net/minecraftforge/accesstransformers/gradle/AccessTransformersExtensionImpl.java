/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

final class AccessTransformersExtensionImpl implements AccessTransformersExtension {
    private final Project project;

    private @Nullable AccessTransformersContainer container;

    AccessTransformersExtensionImpl(Project project) {
        this.project = project;
    }

    @Override
    @SuppressWarnings("rawtypes") // public-facing closure
    public AccessTransformersContainer register(
        Attribute<Boolean> attribute,
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure options
    ) {
        return this.container = AccessTransformersContainer.register(this.project, attribute, options);
    }

    private AccessTransformersContainer getContainer() {
        if (this.container == null)
            throw new IllegalStateException("Cannot configure options for AccessTransformers without having registered one! Use accessTransformers#register in your project.");

        return this.container;
    }

    @Override
    public Attribute<Boolean> getAttribute() {
        return this.getContainer().getAttribute();
    }

    @Override
    @SuppressWarnings("rawtypes") // public-facing closure
    public void options(
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
        Closure closure
    ) {
        this.getContainer().options(closure);
    }

    @Override
    @SuppressWarnings("rawtypes") // public-facing closure
    public Dependency dep(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        return this.getContainer().dep(dependencyNotation, closure);
    }
}
