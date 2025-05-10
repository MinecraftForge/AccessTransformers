package net.minecraftforge.accesstransformers.gradle;

import net.minecraftforge.util.download.DownloadUtils;
import net.minecraftforge.util.hash.HashStore;
import org.gradle.api.file.DirectoryProperty;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static net.minecraftforge.accesstransformers.gradle.AccessTransformersPlugin.LOGGER;

enum DefaultTools implements Callable<File> {
    ACCESS_TRANSFORMERS("accesstransformers.jar", Constants.AT_DOWNLOAD_URL);

    private final String fileName;
    private final String downloadUrl;

    private static @UnknownNullability DirectoryProperty caches;
    private @Nullable Future<File> future;

    DefaultTools(String fileName, String downloadUrl) {
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
    }

    static void start(DirectoryProperty cachesDir) {
        if (caches != null) return;
        caches = cachesDir;

        for (DefaultTools tool : values())
            tool.future = CompletableFuture.supplyAsync(tool::download);
    }

    private File download() {
        // outputs
        File outFile = caches.file(this.fileName).get().getAsFile();
        String name = outFile.getName();

        // in-house caching
        HashStore cache = HashStore.fromFile(outFile)
            .add("tool", outFile)
            .add("url", this.downloadUrl);

        if (outFile.exists() && cache.isSame()) {
            LOGGER.info("Default tool already downloaded: {}", name);
        } else {
            LOGGER.info("Downloading default tool: {}", name);
            try {
                DownloadUtils.downloadFile(outFile, this.downloadUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to download default tool: " + name, e);
            }

            cache.add("tool", outFile).save();
        }

        return outFile;
    }

    @Override
    public File call() throws Exception {
        try {
            return Objects.requireNonNull(this.future, "Default tools are not initialized").get();
        } catch (Throwable e) {
            throw new IOException("Failed to download default tool: " + fileName, e);
        }
    }
}
