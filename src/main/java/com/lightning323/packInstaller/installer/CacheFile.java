package com.lightning323.packInstaller.installer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CacheFile {

    //Properties
    public String indexHashFormat;
    public String indexHash;
    public List<String> modFiles = new ArrayList<>();
    public List<String> otherFiles = new ArrayList<>();
    //--------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static File getCacheFile(Path basePath) {
        return basePath.resolve("cache.json").toFile();
    }

    public static CacheFile read(Path savePath) {
        if (!getCacheFile(savePath).exists()) {
            return null;
        }
        try {
            return MAPPER.readValue(getCacheFile(savePath), CacheFile.class);
        } catch (IOException e) {
            System.err.println("[Cache] Failed to read cache file: " + e.getMessage());
        }
        return null;
    }

    public static String normalizePackPath(Path basePath, String fullPath) {
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path normalizedFull = Paths.get(fullPath).toAbsolutePath().normalize();
        Path relative = normalizedBase.relativize(normalizedFull);
        return relative.toString();
    }

    public boolean write(Path savePath) {

        Path targetPath = getCacheFile(savePath).toPath();
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        modFiles = modFiles.stream()
                .map(path -> normalizePackPath(savePath, path))
                .collect(Collectors.toList());
        otherFiles = otherFiles.stream()
                .map(path -> normalizePackPath(savePath, path))
                .collect(Collectors.toList());

        try {
            // Write JSON to temp file
            MAPPER.writeValue(tempPath.toFile(), this);

            // Atomic replace
            Files.move(
                    tempPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            return true;

        } catch (IOException e) {
            System.err.println("[Cache] Failed writing cache atomically: " + e.getMessage());

            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }

            return false;
        }
    }
}