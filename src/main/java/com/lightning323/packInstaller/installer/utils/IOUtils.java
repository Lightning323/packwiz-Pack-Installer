package com.lightning323.packInstaller.installer.utils;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

import static com.lightning323.packInstaller.installer.PackInstaller.SKIP_HASH_CHECK;

public class IOUtils {

    public static void writeFile(byte[] bytes, File outFile, String hashFormat, String hash) throws IOException {
        //Assert the hash
        if (!SKIP_HASH_CHECK && !HashUtils.getHash(hashFormat, bytes).equals(hash)) {
            throw new IOException("Hash for \"" + outFile.toPath() + "\" does not match!");
        }
        outFile.getParentFile().mkdirs();
        Files.write(outFile.toPath(), bytes);
    }

    public static URL getRelativeUrl(URL baseUrl, String relativePath) throws URISyntaxException, MalformedURLException {
        // 1. Sanitize relative path: remove any accidental leading slash
        // because URI.resolve() treats "/file" as a root-level path pivot.
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        // 2. Safely parse the relative path into a URI escaping spaces to %20
        URI relativeUri = java.nio.file.Paths.get(relativePath).toUri();

        // 3. Strip the file schema wrapper for an unescaped relative path string
        // This allows us to use the multi-argument constructor safely
        String cleanRelativePath = relativePath.replace("\\", "/");
        URI cleanRelativeUri = new URI(null, null, cleanRelativePath, null);

        // 4. Resolve against base context
        URI resolvedUri = baseUrl.toURI().resolve(cleanRelativeUri);

        return resolvedUri.toURL();
    }

    /**
     * Helper to fetch String content from a URL
     */
    public static String getFileAsString(URL url) throws Exception {
        // url.openStream() automatically handles both local files and web HTTP requests
        InputStreamReader inputStreamReader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);

        try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public static byte[] getFileAsBytes(URL url) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    public static void downloadFile(URL url, String hashFormat, String hash, File outFile, boolean canOverwrite)
            throws IOException, InterruptedException, URISyntaxException {
        if (outFile.exists()) { // Check if this file already exists
            if (!canOverwrite) return; // If we can't overwrite don't write to file
            byte[] bytes = Files.readAllBytes(outFile.toPath());
            String fileHash = HashUtils.getHash(hashFormat, bytes);
            if (fileHash.equals(hash)) {
                return; // Already downloaded and correct
            }
        }
        System.out.println("Downloading: " + outFile.getName() + " \t (" + url + ")..");

        boolean isLocalFile = "file".equalsIgnoreCase(url.getProtocol()) || url.toString().startsWith("file:");

        if (isLocalFile) { // --- Local File handling ---
            // Ensure parental directories exist locally
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }

            // Open stream directly from the file URL and write it out
            try (InputStream is = url.openStream()) {
                byte[] allBytes = is.readAllBytes();
                IOUtils.writeFile(allBytes, outFile, hashFormat, hash);
            }
        } else {   // --- Web handling (HTTP/HTTPS) ---
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .GET()
                    .build();

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
                IOUtils.writeFile(baos.toByteArray(), outFile, hashFormat, hash);
            } else {
                throw new RuntimeException("Failed to download file. HTTP Status: " + response.statusCode());
            }
        }
    }
}
