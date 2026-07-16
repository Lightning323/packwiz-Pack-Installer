package com.lightning323.packInstaller.installer.utils;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            if (isLfsPointer(contentSample)) {
                System.out.println("-> Detected Git LFS Pointer in " + relativePath + ". Fetching actual binary via Git LFS API...");
                fileData = resolveAndDownloadLfsBinary(url, contentSample);
            }

            IOUtils.writeFile(fileData, outFile, hashFormat, hash);
        }
    }

    private static byte[] downloadWebBytes(URI uri) throws IOException, InterruptedException {
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

    /**
     * Inspects the downloaded data to determine if it is a Git LFS pointer structure.
     */
    private static boolean isLfsPointer(String content) {
        return content.contains("version https://git-lfs") && content.contains("oid sha256:") && content.contains("size");
    }

    /**
     * Interacts with GitHub's LFS API to parse the pointers, issue a batch request,
     * retrieve the actual binary link, and download it.
     */
    private static byte[] resolveAndDownloadLfsBinary(URL originalUrl, String pointerContent)
            throws IOException, InterruptedException, URISyntaxException {

        // 1. Parse OID and Size with safer, fixed regex patterns
        // \s* matches any spaces, \S+ matches any non-whitespace characters (the hash)
        String oid = extractValue(pointerContent, "oid sha256:(\\S+)");
        long size = Long.parseLong(extractValue(pointerContent, "size (\\d+)"));

        // 2. Build LFS API Endpoint
        String lfsApiUrl = constructLfsApiUrl(originalUrl.toString());

        // 3. Craft JSON payload for the Git LFS Batch API
        String jsonPayload = String.format(
                "{\"operation\":\"download\",\"transfer\":[\"basic\"],\"objects\":[{\"oid\":\"%s\",\"size\":%d}]}",
                oid, size
        );

        HttpRequest lfsRequest = HttpRequest.newBuilder()
                .uri(new URI(lfsApiUrl))
                .header("Accept", "application/vnd.git-lfs+json")
                .header("Content-Type", "application/vnd.git-lfs+json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> lfsResponse = client.send(lfsRequest, HttpResponse.BodyHandlers.ofString());
        if (lfsResponse.statusCode() != 200) {
            throw new RuntimeException("LFS API call failed with status: " + lfsResponse.statusCode() + "\n" + lfsResponse.body());
        }

        // 4. Extract the AWS S3 download URL
        String realDownloadUrl = extractDownloadUrl(lfsResponse.body());

        System.out.println("-> Real LFS URL resolved successfully.");
        return downloadWebBytes(new URI(realDownloadUrl));
    }

    private static String extractValue(String input, String regex) {
        // Enable MULTILINE mode so ^ and $ match line boundaries in the pointer file
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new IllegalArgumentException("Failed to parse pattern matching " + regex + " from LFS pointer file.\nContent:\n" + input);
    }

    private static String constructLfsApiUrl(String rawUrl) {
        // Find owner and repo pattern in github-based URLs
        // e.g., https://github.com/owner/repo/... OR https://raw.githubusercontent.com/owner/repo/...
        Pattern pattern = Pattern.compile("(https?://(?:github\\.com|raw\\.githubusercontent\\.com)/[^/]+/[^/]+)");
        Matcher matcher = pattern.matcher(rawUrl);
        if (matcher.find()) {
            String repoBase = matcher.group(1);
            // Translate raw.githubusercontent.com URLs back to the main github.com path for LFS API endpoints
            repoBase = repoBase.replace("raw.githubusercontent.com", "github.com");
            return repoBase + ".git/info/lfs/objects/batch";
        }
        throw new IllegalArgumentException("Unsupported repository hosting URL layout for LFS detection: " + rawUrl);
    }

    private static String extractDownloadUrl(String lfsJsonResponse) {
        // Quick regex parser to grab the href inside the "download" object action
        Matcher matcher = Pattern.compile("\"download\"\\s*:\\s*\\{\\s*\"href\"\\s*:\\s*\"([^\"]+)\"").matcher(lfsJsonResponse);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("Could not extract LFS download 'href' from response. Response details: " + lfsJsonResponse);
    }
}
