package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.utils.HashUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lightning323.packInstaller.installer.PackInstaller.PATHS_TO_SPARE;
import static com.lightning323.packInstaller.installer.utils.IOUtils.*;

public class DownloadPhase {

    public static void download(List<InstallerEntry> files) throws InterruptedException {
        ExecutorService workerPool = Executors.newFixedThreadPool(8);

        AtomicBoolean stop = new AtomicBoolean(false);

        for (InstallerEntry entry : files) {
            if (!stop.get()) workerPool.submit(() -> {
                try {
                    if (entry.isMod()) {
                        ModDownloader.downloadMod(entry);
                    } else downloadFile(entry);
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


    private static void downloadFile(InstallerEntry entry)
            throws IOException, SecurityException, URISyntaxException, InterruptedException {
        HttpURLConnection conn = (HttpURLConnection) entry.fileURL.openConnection();
        File saveDir = entry.path.toFile().getParentFile();
        saveDir.mkdirs();

        //Add the directory to the list of downloaded directories
        String outHash = entry.hash;

        if (entry.path.toFile().exists()) {
            //If a file already exist, check if they are the same
            byte[] existingFile = Files.readAllBytes(entry.path);
            String existingFileHash = HashUtils.getHash(entry.hashFormat, existingFile);
            if (existingFileHash.equals(outHash)) {
                return; //The files are the same
            }

            if (!PackInstaller.FULL_RESET) {
                if (PATHS_TO_SPARE.contains(entry.path)) {  //Check if the file is in the DONT_OVERWRITE list
                    System.out.println("Skipping: " + entry.path);
                    return;
                }
                for (Path path : PackInstaller.PATHS_TO_SPARE) { //Check if the file is in a directory in the DONT_OVERWRITE list
                    if (isInsideOrEqual(entry.path, path)) {
                        System.out.println("Skipping directory: " + path);
                        return;
                    }
                }
            }
        }
    }


}
