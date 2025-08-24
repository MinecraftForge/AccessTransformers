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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Objects;

abstract class AccessTransformersExtensionImpl implements AccessTransformersExtensionInternal {
    private final Project project;

    private @Nullable AccessTransformersContainer container;

    @Inject
    public AccessTransformersExtensionImpl(Project project) {
        this.project = project;
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
    public void options(Action<? super Options> action) {
        this.getContainer().options(action);
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
