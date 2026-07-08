package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.fileTypes.FileEntry;
import com.lightning323.packInstaller.installer.fileTypes.IndexFile;
import com.lightning323.packInstaller.installer.fileTypes.ModFile;
import com.lightning323.packInstaller.installer.fileTypes.PackConfig;
import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.lightning323.packInstaller.installer.PackInstaller.*;
import static com.lightning323.packInstaller.installer.utils.IOUtils.getRelativePath;

public class IndexingPhase {

    /**
     * Downloads and returns the entire contents of a URL as a byte array.
     */
    private static ModFile getModFromPwToml(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try (ByteArrayOutputStream writer = new ByteArrayOutputStream();
             var inputStream = conn.getInputStream()) {
            writer.write(inputStream.readAllBytes());
            return ModDownloader.getFileEntry(writer.toByteArray());
        } finally {
            conn.disconnect();
        }
    }


    final HashSet<Path> cleanupBlacklist = new HashSet<>();
    final Set<Path> cleanupWhitelist = new HashSet();
    final List<InstallerEntry> allFiles = new ArrayList<>();
    private Object workerLock = new Object();


    public boolean index(Path savePath, PackConfig config, IndexFile indexData, URL indexURL) throws InterruptedException {
        //Read cache file
        CacheFile cacheFile = CacheFile.read(savePath);
        if (cacheFile != null) {
            System.out.println("\n\n--- Reading cache file ---");
            HashSet<Path> cachedMods = new HashSet<>();
            HashSet<Path> cachedFiles = new HashSet<>();
            for (String s : cacheFile.modFiles) {
                Path f = savePath.resolve(s);
                cachedMods.add(f);
                cleanupWhitelist.add(f);
            }
            for (String s : cacheFile.otherFiles) {
                Path f = savePath.resolve(s);
                cachedFiles.add(f);
                cleanupWhitelist.add(f);
            }
            //If the hashes are the same
            if (!FULL_RESET &&
                    cacheFile.indexHashFormat != null && cacheFile.indexHash != null
                    && cacheFile.indexHashFormat.equals(config.index.hashFormat)
                    && cacheFile.indexHash.equals(config.index.hash)) {
                System.out.println("\n\n--- Hashes match. Checking cached files ---");
                //Check to ensure all files are present
                boolean foundAllFilesThatShouldBeThere = true;
                for (Path f : cachedFiles) {
                    if (!f.toFile().exists()) {
                        foundAllFilesThatShouldBeThere = false;
                        break;
                    }
                }
                for (Path f : cachedMods) {
                    if (!f.toFile().exists()) {
                        foundAllFilesThatShouldBeThere = false;
                        break;
                    }
                }
                if (foundAllFilesThatShouldBeThere) {
                    System.out.println("Hashes are the same and all files are present, no need to update.");
                    return false;
                } else {
                    System.out.println("Hashes are the same, but some files are missing. Need to update.");
                }
            }

            if (!FULL_RESET && SPARE_ADDED_MODS) {
                System.out.println("\n\n--- Calculating mods to spare ---");
                HashSet<Path> existingMods = new HashSet<>();
                for (File f : savePath.resolve("mods").toFile().listFiles()) {
                    existingMods.add(f.toPath());
                }
                HashSet<Path> excluded = new HashSet<>();
                excluded.addAll(existingMods);
                excluded.removeAll(cachedMods);
                cleanupBlacklist.clear();
                for (Path p : excluded) {
                    cleanupBlacklist.add(savePath.relativize(p));
                }
            }
        }
        System.out.println("\n\n--- Gathering index files ---");
        cleanupBlacklist.add(savePath.resolve("logs"));
        cleanupBlacklist.add(savePath.resolve("saves"));
        cleanupBlacklist.add(savePath.resolve("worlds"));
        cleanupBlacklist.add(savePath.resolve("crash-reports"));
        System.out.println("Cleanup blacklist: " + cleanupBlacklist.toString());

        ExecutorService workerPool = Executors.newFixedThreadPool(4);
        for (FileEntry entry : indexData.files) {
            workerPool.submit(() -> {
                try {
                    Path path = savePath.resolve(entry.file());

                    if (entry.file().endsWith(PackInstaller.MOD_TOML_FILE_EXT)) {
                        ModFile modFile = getModFromPwToml(IOUtils.getRelativePath(indexURL, entry.file()));
                        path = path.getParent().resolve(modFile.filename); //We don't want the .toml file, we want the mod file
                        synchronized (workerLock) {
                            allFiles.add(new InstallerEntry(path, modFile));
                            cleanupWhitelist.add(path);
                        }
                    } else {
                        URL url = getRelativePath(indexURL, entry.file());
                        synchronized (workerLock) {
                            allFiles.add(new InstallerEntry(path, url, entry.hash(), config.index.hashFormat));
                            cleanupWhitelist.add(path);
                        }
                    }
                } catch (IOException | URISyntaxException e) {
                    System.err.println("Failed to index file: " + entry.file());
                }
            });
        }
        workerPool.shutdown();
        if (!workerPool.awaitTermination(10, TimeUnit.MINUTES)) {
            workerPool.shutdownNow();
        }
//        System.out.println(allFiles.toString());

        //Collect parent folders
        Set<Path> filtered = cleanupWhitelist.stream()
                .filter((p) -> savePath.relativize(p).getNameCount() > 1)//Skip files in the immediate directory
                .map(p -> p.subpath(0, 2))
                .collect(Collectors.toSet());
        cleanupWhitelist.clear();
        cleanupWhitelist.addAll(filtered);
        System.out.println("Cleanup whitelist: " + cleanupWhitelist.toString());
        return true;
    }
}
