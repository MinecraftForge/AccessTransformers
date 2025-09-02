/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.gradleutils.shared.SharedUtil;

import java.util.List;

final class Util extends SharedUtil {
    private Util() { }

    static <T> List<String> listToString(List<T> list) {
        return list.stream().map(Object::toString).toList();
    }
}
