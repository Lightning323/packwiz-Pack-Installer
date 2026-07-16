package com.lightning323.packInstaller.installer.utils.downloading;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LFSDownloader {
    /**
     * Inspects the downloaded data to determine if it is a Git LFS pointer structure.
     */
    protected static boolean isLfsPointer(String content) {
        return content.contains("version https://git-lfs") && content.contains("oid sha256:") && content.contains("size");
    }

    /**
     * Interacts with GitHub's LFS API to parse the pointers, issue a batch request,
     * retrieve the actual binary link, and download it.
     */
    protected static byte[] resolveAndDownloadLfsBinary(HttpClient client, URL originalUrl, String pointerContent)
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
        return DownloadUtils.downloadWebBytes(new URI(realDownloadUrl));
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
