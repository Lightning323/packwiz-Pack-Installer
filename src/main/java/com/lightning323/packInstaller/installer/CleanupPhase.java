package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static com.lightning323.packInstaller.installer.PackInstaller.*;
import static com.lightning323.packInstaller.installer.utils.IOUtils.*;

public class CleanupPhase {

    public static void cleanup(Path savePath, List<InstallerEntry> indexFiles) {
        //Spare paths
        HashSet<Path> sparePaths = new HashSet<>();
        if (!FULL_RESET) {
            SPARE_CLEANUP.forEach((s) -> {
                sparePaths.add(savePath.resolve(s));
            });
        }

        //Add the files that should exist
        HashSet<Path> filesThatShouldExist = new HashSet<>();
        indexFiles.forEach((f) -> {
            filesThatShouldExist.add(savePath.relativize(f.path));
        });

        for (File file : savePath.toFile().listFiles()) {
            if (file.isDirectory())
                cleanDirectory(savePath, file.toPath(), filesThatShouldExist, sparePaths);
        }
    }

    static void cleanDirectory(Path baseDir, Path path, HashSet<Path> filesThatShouldExist, HashSet<Path> skipPaths) {
        //IMPORTANT SAFETY CHECK, make sure the path is inside the save directory
        if (!isInsideOrEqual(path, baseDir))
            throw new RuntimeException("Path " + path + " is not inside the save directory");
        if (!FULL_RESET) {
            for (Path spareDir : skipPaths) {
                if (isInsideOrEqual(path, spareDir)) {
                    System.out.println("Skipping directory: " + path);
                    return;
                }
            }
        }

        for (File file : path.toFile().listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(baseDir, file.toPath(), filesThatShouldExist, skipPaths);
            } else {
                Path fileRelativePath = baseDir.relativize(file.toPath());

                if (!fileRelativePath.equals(IOUtils.getJarPath())
                        && !filesThatShouldExist.contains(fileRelativePath)) {
                    //Spare jarfiles that dont have a toml file, because they were likely added manually
                    if (!FULL_RESET) {
                        if (skipPaths.contains(fileRelativePath) || PackInstaller.modsToSpare.contains(fileRelativePath)) {
                            System.out.println("Skipping: " + fileRelativePath);
                            continue;
                        }
                    }
                    System.out.println("Deleting: " + fileRelativePath);
                    file.delete();
                }
            }
        }
    }
}