/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface AccessTransformersContainerInternal extends AccessTransformersContainer, HasPublicType {
    static AccessTransformersContainer register(Project project, Attribute<Boolean> attribute, Action<? super Options> options) {
        return project.getObjects().newInstance(AccessTransformersContainerImpl.class, attribute, options);
    }

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(AccessTransformersContainer.class);
    }

    non-sealed interface Options extends AccessTransformersContainer.Options, HasPublicType {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(AccessTransformersContainer.Options.class);
        }
    }
}
