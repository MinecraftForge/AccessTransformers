/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformer;

public class IdentityNameHandler implements INameHandler {
    @Override
    public String translateClassName(final String className) {
        return className;
    }

    @Override
    public String translateFieldName(final String fieldName) {
        return fieldName;
    }

    @Override
    public String translateMethodName(final String methodName) {
        return methodName;
    }

    @Override
    public String toString() {
        return "Identity NameHandler";
    }
}
