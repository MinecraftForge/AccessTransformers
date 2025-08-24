/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.Tool;

final class Tools {
    private Tools() { }

    private static final String ACCESSTRANSFORMERS_NAME = "accesstransformers";
    private static final String ACCESSTRANSFORMERS_VERSION = "8.2.2";
    private static final String ACCESSTRANSFORMERS_DOWNLOAD_URL = "https://maven.minecraftforge.net/net/minecraftforge/accesstransformers/" + ACCESSTRANSFORMERS_VERSION + "/accesstransformers-" + ACCESSTRANSFORMERS_VERSION + "-fatjar.jar";
    private static final int ACCESSTRANSFORMERS_MIN_JAVA = 8;
    private static final String ACCESSTRANSFORMESR_MAIN = "net.minecraftforge.accesstransformer.TransformerProcessor";
    static final Tool ACCESSTRANSFORMERS = Tool.of(ACCESSTRANSFORMERS_NAME, ACCESSTRANSFORMERS_VERSION, ACCESSTRANSFORMERS_DOWNLOAD_URL, ACCESSTRANSFORMERS_MIN_JAVA, ACCESSTRANSFORMESR_MAIN);
}
