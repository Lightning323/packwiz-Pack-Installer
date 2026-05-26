package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.fileTypes.ModFile;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

public class InstallerEntry {
    public InstallerEntry(Path path, URL fileURL, String hash, String hashFormat) {
        this.downloadURL = Objects.requireNonNull(fileURL);
        this.path = Objects.requireNonNull(path);
        this.hash = Objects.requireNonNull(hash);
        this.hashFormat = Objects.requireNonNull(hashFormat);
    }

    public InstallerEntry(Path path, ModFile mod) {
        this.path = Objects.requireNonNull(path);
        this.modFile = Objects.requireNonNull(mod);
        this.hash = Objects.requireNonNull(mod.download.hash);
        this.hashFormat = Objects.requireNonNull(mod.download.hashFormat);
    }

    public URL downloadURL;
    public Path path;
    public ModFile modFile;
    public String hash;
    public String hashFormat;

    public boolean isMod() {
        return modFile != null || path.getFileName().endsWith(".jar");
    }

    @Override
    public String toString() {
        if (modFile == null) {
            return "InstallerEntry{" + path + ",\t hash='" + (hash.length() > 5 ? hash.substring(0, 5) + "..." : hash) + " (" + hashFormat + "), fileURL='" + downloadURL + "'}";
        } else {
            return "InstallerEntry{" + path + ",\t hash='" + (hash.length() > 5 ? hash.substring(0, 5) + "..." : hash) + " (" + hashFormat + "), mod=" + modFile.toString() + "}";
        }
    }
}
