package com.lightning323.packInstaller;

import com.lightning323.packInstaller.fileTypes.FileEntry;
import com.lightning323.packInstaller.fileTypes.IndexFile;
import com.lightning323.packInstaller.fileTypes.ModFile;
import com.lightning323.packInstaller.utils.FileDownloader;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;

import static com.lightning323.packInstaller.PackInstaller.*;
import static com.lightning323.packInstaller.utils.IOUtils.*;
import static com.lightning323.packInstaller.utils.ModDownloader.MOD_TOML_FILE_EXT;

public class FileCleanup {
    /**
     * Preventing added files from being deleted by the program
     * Each jar that is supposed to be here has a .pw.toml file
     * If a jar was added that does NOT have a .pw.toml file, it will be skipped
     */
    HashSet<Path> cleanDirectories = new HashSet<>();
    public HashSet<Path> modFiles = new HashSet<>();
    HashSet<Path> filesThatShouldExist = new HashSet<>();
    final HashSet<Path> modsToSpare = new HashSet<>();
    Path baseDir;
    File modsCacheFile;

    public FileCleanup(File saveDir) {
        baseDir = saveDir.toPath().toAbsolutePath().normalize();
        modsCacheFile = baseDir.resolve("cache.txt").toFile();
    }

    private Path relativize(Path f) {
        Path full = f.toAbsolutePath().normalize();
        Path fileRelativePath = baseDir.relativize(full);
        return fileRelativePath;
    }

    /**
     *
     * @param baseUrl
     * @param indexData
     * @param hashFormat
     * @param hash
     * @return if we need to update the pack
     * @throws Exception
     */
    public boolean calculateModsToSpare(URL baseUrl, IndexFile indexData, String hashFormat, String hash) throws Exception {
        //Calculate mods to spare
        if (modsCacheFile.exists()) {
            HashSet<Path> originalMods = new HashSet<>();
            String originalHashFormat = null;
            String originalHash = null;

            try (BufferedReader reader = new BufferedReader(new FileReader(modsCacheFile))) {
                int lineIndex = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    //The first 2 lines of the cache file are declarations of the hash format and hash
                    if (lineIndex == 0) {
                        originalHashFormat = line;
                    } else if (lineIndex == 1) {
                        originalHash = line;
                    } else {
                        if (SPARE_ADDED_MODS) originalMods.add(baseDir.resolve(line));
                        else break;
                    }
                    lineIndex++;
                }
            }
            System.out.println("Current Hash Format: " + originalHashFormat);
            System.out.println("Current Hash: " + originalHash);


            if (originalHashFormat != null && originalHash != null
                    && originalHashFormat.equals(hashFormat)
                    && originalHash.equals(hash)) {
                System.out.println("Hashes are the same, no need to update.");
                return false;
            }

            if (SPARE_ADDED_MODS) {
                System.out.println("\n\n--- Calculating mods to spare ---");
                HashSet<Path> existingMods = new HashSet<>();
                for (File f : baseDir.resolve("mods").toFile().listFiles()) {
                    existingMods.add(f.toPath());
                }
//            System.out.println(originalMods);
                //The mods to spare = existing mods - original mods
                HashSet<Path> excluded = new HashSet<>();
                excluded.addAll(existingMods);
                excluded.removeAll(originalMods);
                modsToSpare.clear();
                for (Path p : excluded) {
                    modsToSpare.add(relativize(p));
                }
                System.out.println("Mods to spare: " + modsToSpare.toString());
            }
        }

        //Write the cache file of original mods from the index.toml we downloaded
        writeCacheFile(baseUrl, indexData, hashFormat, hash);
        return true;
    }

    public boolean writeCacheFile(URL baseUrl, IndexFile indexData, String hashFormat, String hash) {
        // 1. Define paths for the actual target and a sibling temp file
        Path targetPath = modsCacheFile.toPath();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        // 2. Open the writer on the TEMPORARY file
        try (BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
            // Write the hash of the index file
            writer.write(hashFormat);
            writer.newLine();
            writer.write(hash);
            writer.newLine();

            // Write the existing mods
            for (FileEntry entry : indexData.files) {
                if (entry.file().endsWith(MOD_TOML_FILE_EXT)) {
                    File dir = relativize(baseDir.resolve(entry.file())).toFile().getParentFile();
                    ModFile modFile = FileDownloader.getModFromPwToml(getRelativeUrl(baseUrl, entry.file()));
                    writer.write(Path.of(dir.getPath(), modFile.filename).toString());
                    writer.newLine();
                } else if (entry.file().endsWith(".jar")) {
                    File dir = relativize(baseDir.resolve(entry.file())).toFile().getParentFile();
                    writer.write(Path.of(dir.getPath(), entry.file()).toString());
                    writer.newLine();
                }
            }

            // Close the writer explicitly before moving (handled automatically by try-with-resources)
        } catch (IOException | URISyntaxException e) {
            // If writing failed, clean up the temp file so we don't leave garbage behind
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            return false;
        }

        // 3. Move the temp file to the target location atomically
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            // If the atomic move failed, try to clean up the temp file
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    public void deleteUnIncludedFiles(IndexFile indexData) throws IOException {
        System.out.println("\n--- Deleting Unincluded Files ---");

        //Add the files that should exist so know what files to delete
        for (FileEntry fe : indexData.files) {
            filesThatShouldExist.add(Path.of(fe.file()));
        }

        //Write the original mods for future reference and add them to the files that should exist
        modFiles.forEach((f) -> {
            filesThatShouldExist.add(relativize(f));
        });

        try {
            Path jarFull = Path.of(PackInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            //Get the path to ourselves so we can skip deleting ourselves
            Path ownJarfilePath = baseDir.relativize(jarFull);

            //Now delete files within downloaded directories that arent on the list
            getCleanDirectories().forEach(path -> {
                //IMPORTANT SAFETY CHECK, make sure the path is inside the save directory
                if (!isInsideOrEqual(path, baseDir))
                    throw new RuntimeException("Path " + path + " is not inside the save directory");
                if (!FULL_RESET) {
                    for (Path spareDir : PATHS_TO_SPARE) {
                        if (isInsideOrEqual(path, spareDir)) {
                            System.out.println("Skipping directory: " + path);
                            return;
                        }
                    }
                }
                fileLoop:
                for (File file : path.toFile().listFiles()) {
                    if (file.exists() && !file.isDirectory()) {
                        Path full = file.toPath().toAbsolutePath().normalize();
                        Path fileRelativePath = baseDir.relativize(full);

                        if (fileRelativePath.equals(ownJarfilePath)) { //Dont delete ourselves!
                            System.out.println("Skipping own jarfile: " + ownJarfilePath);
                            continue;
                        }

                        //Check if the file is in the index
                        if (!filesThatShouldExist.contains(fileRelativePath)) {
                            //Spare jarfiles that dont have a toml file, because they were likely added manually
                            if (!FULL_RESET) {
                                if (PATHS_TO_SPARE.contains(fileRelativePath)) {
                                    //We can spare files that don't have a toml file because they were likely added manually
                                    System.out.println("Skipping: " + fileRelativePath);
                                    continue;
                                } else if (modsToSpare.contains(fileRelativePath)) {
                                    //We can spare files that don't have a toml file because they were likely added manually
                                    System.out.println("Skipping mod: " + fileRelativePath);
                                    continue;
                                }
                            }
                            System.out.println("Deleting: " + fileRelativePath);
                            file.delete();
                        }
                    }
                }
            });
        } catch (URISyntaxException e) {
            System.out.println("Failed to safely delete unincluded files " + e.getMessage());
        }
    }


    public HashSet<Path> getCleanDirectories() {
        return cleanDirectories;
    }

    public synchronized void add(File saveDir, Path newPath) {
        Path savePath = saveDir.toPath().toAbsolutePath().normalize();
        Path normalizedNew = newPath.toAbsolutePath().normalize();
        //Dont add base directory as a cleanup directory
        if (!savePath.startsWith(normalizedNew)) {
            cleanDirectories.add(newPath);
        }
    }
}