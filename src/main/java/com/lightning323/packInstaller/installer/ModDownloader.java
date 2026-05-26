package com.lightning323.packInstaller.installer;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.lightning323.packInstaller.installer.fileTypes.ModFile;
import com.lightning323.packInstaller.installer.utils.HashUtils;
import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModDownloader {

    private static final String CURSEFORGE_BASE_URL = "https://www.curseforge.com/api/v1/mods/%d/files/%d/download";
    private static final String MODRINTH_BASE_URL = "https://cdn.modrinth.com/data/%s/versions/%s/%s";


    private static final TomlMapper mapper = new TomlMapper();


    public static ModFile getFileEntry(File pwTomlFile) throws IOException {
        return mapper.readValue(pwTomlFile, ModFile.class);
    }

    public static ModFile getFileEntry(byte[] pwTomlFile) throws IOException {
        return mapper.readValue(pwTomlFile, ModFile.class);
    }

    public static void checkAndDownloadMod(ModFile modToml, Path jarOutputPath)
            throws IOException, InterruptedException, URISyntaxException {

        if (Files.exists(jarOutputPath) //If the jar already exists and its hash matches
                && HashUtils.getHash(modToml.download.hashFormat, Files.readAllBytes(jarOutputPath)).equals(modToml.download.hash)) {
            return;
        }

        // 1. Construct the download URL using the IDs from the config
        URL url = null;

        if (modToml.update.curseforge != null) {
            url = new URL(String.format(CURSEFORGE_BASE_URL,
                    modToml.update.curseforge.projectId,
                    modToml.update.curseforge.fileId));
        } else if (modToml.update.modrinth != null) {
            url = new URL(String.format(MODRINTH_BASE_URL,
                    modToml.update.modrinth.modId,
                    modToml.update.modrinth.version,
                    modToml.filename));
        } else {
            throw new RuntimeException("Invalid mod update URL");
        }

        DownloadPhase.download(url, modToml.download.hashFormat, modToml.download.hash, jarOutputPath.toFile());
    }
}