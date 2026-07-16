package com.lightning323.packInstaller.installer.utils;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

}
