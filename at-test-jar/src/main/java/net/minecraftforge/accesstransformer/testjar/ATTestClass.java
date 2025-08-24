/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformer.testjar;

@SuppressWarnings("unused")
public class ATTestClass {
    private final String finalPrivateField = "EMPTY";
    private String privateField = "EMPTY";

    private void privateMethod() {
    }

    public void otherMethod() {
        privateMethod();
    }
}
