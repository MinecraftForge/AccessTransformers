/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import java.util.List;

final class Constants {
    private Constants() { }

    static final String ACCESSTRANSFORMERS_NAME = "accesstransformers";
    static final String ACCESSTRANSFORMERS_VERSION = "8.2.2";
    static final String ACCESSTRANSFORMERS_DOWNLOAD_URL = "https://maven.minecraftforge.net/net/minecraftforge/accesstransformers/" + ACCESSTRANSFORMERS_VERSION + "/accesstransformers-" + ACCESSTRANSFORMERS_VERSION + "-fatjar.jar";
    static final int ACCESSTRANSFORMERS_MIN_JAVA = 8;
    static final String ACCESSTRANSFORMERS_MAIN = "net.minecraftforge.accesstransformer.TransformerProcessor";

    static final List<String> ACCESSTRANSFORMERS_DEFAULT_ARGS = List.of(
        "--inJar", "{inJar}",
        "--atFile", "{atFile}",
        "--outJar", "{outJar}",
        "--logFile", "{logFile}"
    );
}
