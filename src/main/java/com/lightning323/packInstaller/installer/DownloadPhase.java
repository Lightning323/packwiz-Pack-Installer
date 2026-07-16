package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.utils.DownloadUtils;
import com.lightning323.packInstaller.installer.utils.ModDownloader;
import com.lightning323.packInstaller.installer.utils.PathUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lightning323.packInstaller.installer.PackInstaller.*;

public class DownloadPhase {

    public static void downloadAllFiles(Path savePath, List<InstallerEntry> files) throws InterruptedException {
        //Dont overwrite specific files like options.txt or servers.dat
        HashSet<File> spareFromOverwrite = new HashSet<>();
        SPARE_OVERWRITE.forEach((s) -> {
            File f = savePath.resolve(s).toFile();
            if (f.exists() && !f.isDirectory()) spareFromOverwrite.add(f);
        });

        ExecutorService workerPool = Executors.newFixedThreadPool(8);
        AtomicBoolean stop = new AtomicBoolean(false);
        for (InstallerEntry entry : files) {
            if (!stop.get()) workerPool.submit(() -> {
                try {
                    if (entry.modFile != null) {
                        ModDownloader.checkAndDownloadMod(entry.modFile, savePath,  entry.path);
                    } else {
                        //If the file is NOT one of the spare files and is not in the same level as the save path, we can overwrite
                        boolean canOverwrite = !FULL_RESET && !spareFromOverwrite.contains(entry.path.toFile()) && !PathUtils.isSameLevel(entry.path, savePath);
                        DownloadUtils.downloadFile(entry.downloadURL, entry.hashFormat, entry.hash, savePath, entry.path.toFile(), canOverwrite);
                    }
                } catch (Exception e) {
                    PackInstaller.fail("Failed to download " + entry.toString(), e);
                }
            });
        }

        //Wait for all tasks to complete
        workerPool.shutdown();
        if (!workerPool.awaitTermination(10, TimeUnit.MINUTES)) {
            workerPool.shutdownNow();
        }
        System.out.println("\n--- Download Complete ---");
    }
}
