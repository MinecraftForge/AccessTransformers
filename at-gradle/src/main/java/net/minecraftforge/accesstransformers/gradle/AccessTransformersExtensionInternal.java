/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface AccessTransformersExtensionInternal extends AccessTransformersExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(AccessTransformersExtension.class);
    }
}
