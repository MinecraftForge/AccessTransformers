/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

non-sealed interface AccessTransformersConfigurationInternal extends AccessTransformersConfiguration, HasPublicType {
    String CONFIGS_EXT_PROPERTY = "__accessTransformers_configs";

    static boolean has(Object object) {
        try {
            return ((ExtensionAware) object).getExtensions().getExtraProperties().has(CONFIGS_EXT_PROPERTY);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static List<AccessTransformersConfigurationInternal> get(Object object) {
        ExtraPropertiesExtension ext;
        try {
            ext = ((ExtensionAware) object).getExtensions().getExtraProperties();
        } catch (Exception e) {
            // TODO [AccessTransformers][Gradle] Make problem for this
            throw new RuntimeException("Failed to get extension for " + object, e);
        }

        try {
            return (List<AccessTransformersConfigurationInternal>) Objects.requireNonNull(
                ext.get(CONFIGS_EXT_PROPERTY),
                "Configs ext property should never be null!"
            );
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            var ret = new LinkedList<AccessTransformersConfigurationInternal>();
            ext.set(CONFIGS_EXT_PROPERTY, ret);
            return ret;
        }
    }

    AccessTransformersContainerInternal.Options options();

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(AccessTransformersConfiguration.class);
    }
}
