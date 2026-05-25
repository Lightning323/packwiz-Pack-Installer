package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.fileTypes.FileEntry;
import com.lightning323.packInstaller.installer.fileTypes.IndexFile;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.lightning323.packInstaller.installer.PackInstaller.*;
import static com.lightning323.packInstaller.installer.utils.IOUtils.*;

public class CleanupPhase {
    /**
     * Preventing added files from being deleted by the program
     * Each jar that is supposed to be here has a .pw.toml file
     * If a jar was added that does NOT have a .pw.toml file, it will be skipped
     */
    HashSet<Path> cleanDirectories = new HashSet<>();

    public static void cleanup(Path baseDir, List<InstallerEntry> modFiles, List<InstallerEntry> files) {
//        //Spare paths
//        List<Path> sparePaths = new ArrayList<>(PATHS_TO_SPARE);
//        sparePaths.addAll(PATHS_TO_SPARE_CLEANUP);
//
//        //Add the files that should exist
//        HashSet<Path> filesThatShouldExist = new HashSet<>();
//        modFiles.forEach((f) -> {
//            System.out.println(f);
//            filesThatShouldExist.add(baseDir.relativize(f.path));
//        });
//        files.forEach((f) -> {
//            System.out.println(f);
//            filesThatShouldExist.add(baseDir.relativize(f.path));
//        });
//
//        try {
//            Path jarFull = Path.of(PackInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI())
//                    .toAbsolutePath().normalize();
//            //Get the path to ourselves so we can skip deleting ourselves
//            Path ownJarfilePath = baseDir.relativize(jarFull);
//
//            //Now delete files within downloaded directories that arent on the list
//            getCleanDirectories().forEach(path -> {
//                //IMPORTANT SAFETY CHECK, make sure the path is inside the save directory
//                if (!isInsideOrEqual(path, baseDir))
//                    throw new RuntimeException("Path " + path + " is not inside the save directory");
//                if (!FULL_RESET) {
//                    for (Path spareDir : sparePaths) {
//                        if (isInsideOrEqual(path, spareDir)) {
//                            System.out.println("Skipping directory: " + path);
//                            return;
//                        }
//                    }
//                }
//                fileLoop:
//                for (File file : path.toFile().listFiles()) {
//                    if (file.exists() && !file.isDirectory()) {
//                        Path full = file.toPath().toAbsolutePath().normalize();
//                        Path fileRelativePath = baseDir.relativize(full);
//
//                        if (fileRelativePath.equals(ownJarfilePath)) { //Dont delete ourselves!
//                            System.out.println("Skipping own jarfile: " + ownJarfilePath);
//                            continue;
//                        }
//
//                        //Check if the file is in the index
//                        if (!filesThatShouldExist.contains(fileRelativePath)) {
//                            //Spare jarfiles that dont have a toml file, because they were likely added manually
//                            if (!FULL_RESET) {
//                                if (sparePaths.contains(fileRelativePath)) {
//                                    //We can spare files that don't have a toml file because they were likely added manually
//                                    System.out.println("Skipping: " + fileRelativePath);
//                                    continue;
//                                } else if (PackInstaller.modsToSpare.contains(fileRelativePath)) {
//                                    //We can spare files that don't have a toml file because they were likely added manually
//                                    System.out.println("Skipping mod: " + fileRelativePath);
//                                    continue;
//                                }
//                            }
//                            System.out.println("Deleting: " + fileRelativePath);
//                            file.delete();
//                        }
//                    }
//                }
//            });
//        } catch (URISyntaxException e) {
//            System.out.println("Failed to safely delete unincluded files " + e.getMessage());
//        }
    }
}