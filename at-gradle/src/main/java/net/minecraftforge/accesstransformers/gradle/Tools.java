/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.Tool;

final class Tools {
    private Tools() { }

    static final Tool ACCESSTRANSFORMERS = Tool.of(Constants.ACCESSTRANSFORMERS_NAME, Constants.ACCESSTRANSFORMERS_VERSION, Constants.ACCESSTRANSFORMERS_DOWNLOAD_URL, Constants.ACCESSTRANSFORMERS_MIN_JAVA, Constants.ACCESSTRANSFORMERS_MAIN);
}
