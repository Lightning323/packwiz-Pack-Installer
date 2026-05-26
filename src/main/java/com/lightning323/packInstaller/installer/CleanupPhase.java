package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.utils.PathUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.lightning323.packInstaller.installer.PackInstaller.*;

public class CleanupPhase {

    public static void cleanup(Path savePath, List<InstallerEntry> indexFiles,
                               Set<Path> pathWhitelist, Set<Path> pathBlacklist) {
        //Spare paths
        if (!FULL_RESET) {
            SPARE_CLEANUP.forEach((s) -> {
                pathBlacklist.add(savePath.resolve(s));
            });
        }

        //These are an ABSOLUTE MUST NOT be deleted
        pathBlacklist.add(savePath.resolve("logs"));
        pathBlacklist.add(savePath.resolve("saves"));
        pathBlacklist.add(savePath.resolve("worlds"));
        pathBlacklist.add(savePath.resolve("crash-reports"));
        pathWhitelist.removeAll(pathBlacklist);

        //Add the files that should exist
        HashSet<Path> filesThatShouldExist = new HashSet<>();
        indexFiles.forEach((f) -> {
            filesThatShouldExist.add(savePath.relativize(f.path));
        });

        for (File file : savePath.toFile().listFiles()) {
            if (file.isDirectory())
                cleanDirectory(savePath, file.toPath(), filesThatShouldExist, pathWhitelist, pathBlacklist);
        }
    }

    static void cleanDirectory(final Path baseDir, Path path, HashSet<Path> filesThatShouldExist,
                               Set<Path> pathWhitelist, Set<Path> pathBlacklist) {
        //Check our whitelist and blacklist
        boolean isAllowed = false;
        for (Path p : pathWhitelist) {
            if (PathUtils.isInsideOrEqual(path, p)) {
                isAllowed = true;
                break;
            }
        }
        for (Path p : pathBlacklist) {
            if (PathUtils.isInsideOrEqual(path, p)) {
                isAllowed = false;
                break;
            }
        }
        if (!isAllowed) {
            System.out.println("Skipping directory: " + path);
            return;
        }

        for (File file : path.toFile().listFiles()) {
            if (file.isDirectory()) {
                cleanDirectory(baseDir, file.toPath(), filesThatShouldExist, pathWhitelist, pathBlacklist);
            } else {
                Path fileRelativePath = baseDir.relativize(file.toPath());

                if (!fileRelativePath.equals(PathUtils.getJarPath())
                        && !filesThatShouldExist.contains(fileRelativePath)) {
                    //Spare jarfiles that dont have a toml file, because they were likely added manually
                    if (!FULL_RESET) {
                        if (pathBlacklist.contains(fileRelativePath)) {
                            System.out.println("Skipping: " + fileRelativePath);
                            continue;
                        }
                    }

                    //--------------------------------------------
                    if (!PathUtils.isInsideOrEqual(path, baseDir)) //Very important safety check
                        throw new RuntimeException("Path " + path + " is not inside the save directory!");
                    else {
                        System.out.println("Deleting: " + fileRelativePath);
                        file.delete();
                    }
                    //--------------------------------------------

                }
            }
        }
    }
}