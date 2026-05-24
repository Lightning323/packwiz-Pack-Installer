package com.lightning323.packInstaller.installer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class CacheFile {

    // Instance-bound list prevents multi-threading/re-run state pollution
    private File cacheFile;
    private final Path basePath;

    public String indexHashFormat;
    public String indexHash;
    public final List<Path> modFiles = new ArrayList<>();


    public File getCacheFile() {
        return cacheFile;
    }

    public CacheFile(Path savePath) {
        this.basePath = savePath;
        cacheFile = basePath.resolve("cache.txt").toFile();
    }

    public void read() {
        if (cacheFile == null || !cacheFile.exists()) {
            return;
        }

        // Clear previous state if re-reading an updated cache file
        modFiles.clear();

        System.out.println("\n--- Cache File ---");
        try (BufferedReader reader = Files.newBufferedReader(cacheFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineIndex = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                if (lineIndex == 0) {
                    indexHashFormat = line.trim();
                } else if (lineIndex == 1) {
                    indexHash = line.trim();
                } else {
                    modFiles.add(basePath.resolve(line.trim()));
                }
                lineIndex++;
            }

            System.out.println("Current Hash Format: " + indexHashFormat);
            System.out.println("Current Hash: " + indexHash);

        } catch (IOException e) {
            System.err.println("[Cache] Failed to read cache file: " + e.getMessage());
        }
    }

    public boolean write() {
        if (cacheFile == null) return false;
        Path targetPath = cacheFile.toPath();
        // Create a sibling temp file safely using the NIO.2 Framework
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

        try {
            // Use standard Java NIO files to write directly
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                writer.write(indexHashFormat);
                writer.newLine();
                writer.write(indexHash);
                writer.newLine();

                for (Path modFile : modFiles) {
                    // Store relative paths in the cache so the pack remains portable across machines
                    Path relativePath = basePath.relativize(modFile);
                    writer.write(relativePath.toString().replace("\\", "/"));
                    writer.newLine();
                }
            }

            // Atomic file system replacement
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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