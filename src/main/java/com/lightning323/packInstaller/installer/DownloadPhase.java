package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.gui.InstallerGui;
import com.lightning323.packInstaller.installer.utils.downloading.DownloadUtils;
import com.lightning323.packInstaller.installer.utils.downloading.ModDownloader;
import com.lightning323.packInstaller.installer.utils.PathUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        int total = files.size();
        AtomicInteger completed = new AtomicInteger(0);
        InstallerGui.setTaskProgress(0, total, "Downloading files");
        for (InstallerEntry entry : files) {
            if (stop.get() || InstallerGui.isCancelled()) break;
            if (!stop.get()) workerPool.submit(() -> {
                try {
                    InstallerGui.checkCancelled();
                    if (entry.modFile != null) {
                        ModDownloader.checkAndDownloadMod(entry.modFile, savePath,  entry.path);
                    } else {
                        //If the file is NOT one of the spare files and is not in the same level as the save path, we can overwrite
                        boolean canOverwrite = !FULL_RESET && !spareFromOverwrite.contains(entry.path.toFile()) && !PathUtils.isSameLevel(entry.path, savePath);
                        DownloadUtils.downloadFile(entry.downloadURL, entry.hashFormat, entry.hash, savePath, entry.path.toFile(), canOverwrite);
                    }
                } catch (CancellationException e) {
                    stop.set(true); // User cancelled, stop quietly
                } catch (Exception e) {
                    PackInstaller.fail("Failed to download " + entry.toString(), e);
                } finally {
                    InstallerGui.setTaskProgress(completed.incrementAndGet(), total, "Downloading files");
                }
            });
        }

        //Wait for all tasks to complete
        workerPool.shutdown();
        if (!workerPool.awaitTermination(10, TimeUnit.MINUTES)) {
            workerPool.shutdownNow();
        }
        if (InstallerGui.isCancelled()) {
            return;
        }
        InstallerGui.setTaskProgress(total, total, "Downloading files");
        System.out.println("\n--- Download Complete ---");
    }
}
