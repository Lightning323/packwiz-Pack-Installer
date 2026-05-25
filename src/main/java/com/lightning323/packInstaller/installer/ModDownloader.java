package com.lightning323.packInstaller.installer;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.lightning323.packInstaller.installer.fileTypes.ModFile;
import com.lightning323.packInstaller.installer.utils.HashUtils;
import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModDownloader {

    private static final String CURSEFORGE_BASE_URL = "https://www.curseforge.com/api/v1/mods/%d/files/%d/download";
    private static final String MODRINTH_BASE_URL = "https://cdn.modrinth.com/data/%s/versions/%s/%s";
    public static final String MOD_TOML_FILE_EXT = ".pw.toml";

    private static final TomlMapper mapper = new TomlMapper();


    public static void downloadMod(InstallerEntry mod) throws IOException {
        System.out.println("Downloading: " + mod.path + "...");
        System.out.println("URL: " + mod.url);
        URL url = new URL(mod.modFile.download.url);

        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // 2. Check for success (200 OK)
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedInputStream in = new BufferedInputStream(httpConn.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(mod.path.toString())) {

                byte[] dataBuffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
        } else {
            System.err.println("Failed to download file. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    public static ModFile getFileEntry(File pwTomlFile) throws IOException {
        return mapper.readValue(pwTomlFile, ModFile.class);
    }

    public static ModFile getFileEntry(byte[] pwTomlFile) throws IOException {
        return mapper.readValue(pwTomlFile, ModFile.class);
    }

    public static void checkAndDownloadMod(ModFile modToml, File destinationDir) throws IOException, InterruptedException {
        Path jarOutputPath = new File(destinationDir, modToml.filename).toPath();

        if (Files.exists(jarOutputPath) //If the jar already exists and its hash matches
                && HashUtils.getHash(modToml.download.hashFormat, Files.readAllBytes(jarOutputPath)).equals(modToml.download.hash)) {
            return;
        }

        // 1. Construct the download URL using the IDs from the config
        String url = null;

        if (modToml.update.curseforge != null) {
            url = String.format(CURSEFORGE_BASE_URL,
                    modToml.update.curseforge.projectId,
                    modToml.update.curseforge.fileId);
        } else if (modToml.update.modrinth != null) {
            url = String.format(MODRINTH_BASE_URL,
                    modToml.update.modrinth.modId,
                    modToml.update.modrinth.version,
                    modToml.filename);
        } else {
            throw new RuntimeException("Invalid mod update URL");
        }
        System.out.println("Downloading mod: " + modToml.filename + " \t (" + url + ")..");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        // 2. Send the request
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = response.body()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
            }
            IOUtils.writeFile(modToml.download.hashFormat, baos.toByteArray(), jarOutputPath.toFile(), modToml.download.hash);
        } else {
            throw new RuntimeException("Failed to download mod. HTTP Status: " + response.statusCode());
        }

    }
}