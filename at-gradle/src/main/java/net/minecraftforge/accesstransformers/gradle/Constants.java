/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class Constants {
    private Constants() { }

    static final List<String> AT_DEFAULT_ARGS = Collections.unmodifiableList(Arrays.asList(
        "--inJar", "{inJar}",
        "--atFile", "{atFile}",
        "--outJar", "{outJar}",
        "--logFile", "{logFile}"
    ));
}
