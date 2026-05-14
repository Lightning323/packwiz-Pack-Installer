package com.lightning323.packInstaller;

import com.lightning323.packInstaller.fileTypes.FileEntry;
import com.lightning323.packInstaller.fileTypes.IndexFile;
import com.lightning323.packInstaller.fileTypes.ModFile;
import com.lightning323.packInstaller.utils.ModDownloader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.util.HashSet;

import static com.lightning323.packInstaller.PackInstaller.*;
import static com.lightning323.packInstaller.utils.IOUtils.isInsideOrEqual;
import static com.lightning323.packInstaller.utils.ModDownloader.MOD_TOML_FILE_EXT;

public class FileCleanup {
    /**
     * Preventing added files from being deleted by the program
     * Each jar that is supposed to be here has a .pw.toml file
     * If a jar was added that does NOT have a .pw.toml file, it will be skipped
     */
    static HashSet<Path> cleanDirectories = new HashSet<>();
    static HashSet<Path> modFiles = new HashSet<>();

    public static void deleteUnIncludedFiles(File saveDir, IndexFile indexData) throws IOException {
        System.out.println("\n--- Deleting Unincluded Files ---");

        HashSet<Path> filesThatShouldExist = new HashSet<>();
        HashSet<Path> modsToSkip = new HashSet<>();

        Path baseDir = saveDir.toPath().toAbsolutePath().normalize();

        //Add the files that should exist so know what files to delete
        for (FileEntry fe : indexData.files) {
            filesThatShouldExist.add(Path.of(fe.file()));
        }
        modFiles.forEach( (f)->{
            Path full = f.toAbsolutePath().normalize();
            Path fileRelativePath = baseDir.relativize(full);
            filesThatShouldExist.add(fileRelativePath);
        });

        try {
            Path jarFull = Path.of(PackInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            //Get the path to ourselves so we can skip deleting ourselves
            Path ownJarfilePath = baseDir.relativize(jarFull);

            //Now delete files within downloaded directories that arent on the list
            FileCleanup.getCleanDirectories().forEach(path -> {
                //IMPORTANT SAFETY CHECK, make sure the path is inside the save directory
                if (!isInsideOrEqual(path, saveDir.toPath()))
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
                                } else if(modsToSkip.contains(fileRelativePath)){
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


    public static HashSet<Path> getCleanDirectories() {
        return cleanDirectories;
    }

    public static synchronized void add(File saveDir, Path newPath) {
        Path savePath = saveDir.toPath().toAbsolutePath().normalize();
        Path normalizedNew = newPath.toAbsolutePath().normalize();
        //Dont add base directory as a cleanup directory
        if (!savePath.startsWith(normalizedNew)) {
            cleanDirectories.add(newPath);
        }
    }
}