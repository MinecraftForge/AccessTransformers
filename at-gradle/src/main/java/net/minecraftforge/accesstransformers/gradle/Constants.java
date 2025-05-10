package net.minecraftforge.accesstransformers.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface Constants {
    String CACHES_PATH_GLOBAL = /* gradleUserHomeDir + */ "minecraftforge/accesstransformers";
    String CACHES_PATH_LOCAL = /* projectLayout.buildDirectory + */ "accesstransformers";

    String AT_VERSION = "8.2.2";
    String AT_DOWNLOAD_URL = "https://maven.minecraftforge.net/net/minecraftforge/accesstransformers/" + AT_VERSION + "/accesstransformers-" + AT_VERSION + "-fatjar.jar";
    int AT_MIN_JAVA = 8;
    List<String> AT_DEFAULT_ARGS = Collections.unmodifiableList(Arrays.asList(
        "--inJar", "{inJar}",
        "--atFile", "{atFile}",
        "--outJar", "{outJar}",
        "--logFile", "{logFile}"
    ));
}
