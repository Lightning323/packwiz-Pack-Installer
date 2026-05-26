package com.lightning323.packInstaller.installer.utils;

import com.lightning323.packInstaller.installer.PackInstaller;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class PathUtils {


    public static boolean isSameLevel(Path basePath, Path filePath) {
        Path relative = basePath.relativize(filePath);

        // File must have exactly one name element
        // Example: "file.txt"
        // Not allowed: "folder/file.txt"
        return relative.getNameCount() == 1;
    }

    public static boolean isInsideOrEqual(Path child, Path parent) {
        // 1. Normalize and get absolute paths to handle "." or ".." or relative vs absolute
        Path absChild = child.toAbsolutePath().normalize();
        Path absParent = parent.toAbsolutePath().normalize();

        // 2. startsWith handles both "inside" and "equal to"
        return absChild.startsWith(absParent);
    }

    public static Path getJarPath() {
        Path jarFull = null;
        try {
            jarFull = Path.of(
                    PackInstaller.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
        }
        return jarFull;
    }
}
