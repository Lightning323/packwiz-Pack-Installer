package com.lightning323.packInstaller.installer.utils.downloading;

import com.lightning323.packInstaller.installer.utils.HashUtils;
import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadUtils {

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void downloadFile(URL url, String hashFormat, String hash, Path baseDir, File outFile, boolean canOverwrite)
            throws IOException, InterruptedException, URISyntaxException {

        if (outFile.exists()) {
            if (!canOverwrite) return;
            byte[] bytes = Files.readAllBytes(outFile.toPath());
            String fileHash = HashUtils.getHash(hashFormat, bytes);
            if (fileHash.equals(hash)) {
                return; // Correct and already downloaded
            }
        }

        Path relativePath = baseDir.relativize(outFile.toPath());
        System.out.println("Downloading: " + relativePath + " \t (" + url + ")..");

        boolean isLocalFile = "file".equalsIgnoreCase(url.getProtocol()) || url.toString().startsWith("file:");

        if (isLocalFile) {
            // Ensure parental directories exist locally
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }

            // Convert URL to URI, then to a Path to correctly decode %20 and other characters
            java.nio.file.Path localPath = java.nio.file.Paths.get(url.toURI());

            try (InputStream is = Files.newInputStream(localPath)) {
                byte[] allBytes = is.readAllBytes();
                IOUtils.writeFile(allBytes, outFile, hashFormat, hash);
            }
        } else {
            // Get initial file bytes
            byte[] fileData = downloadWebBytes(url.toURI());

            // 1. Detect if this is a Git LFS pointer file
            String contentSample = new String(fileData, java.nio.charset.StandardCharsets.UTF_8);
            if (LFSDownloader.isLfsPointer(contentSample)) {
                System.out.println("-> Detected Git LFS Pointer in " + relativePath + ". Fetching actual binary via Git LFS API...");
                fileData = LFSDownloader.resolveAndDownloadLfsBinary(client, url, contentSample);
            }

            IOUtils.writeFile(fileData, outFile, hashFormat, hash);
        }
    }

    protected static byte[] downloadWebBytes(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download. HTTP Status: " + response.statusCode() + " for URI: " + uri);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = response.body()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
        }
        return baos.toByteArray();
    }


}
