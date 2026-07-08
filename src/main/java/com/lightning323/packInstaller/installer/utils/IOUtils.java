package com.lightning323.packInstaller.installer.utils;

import java.io.*;
import java.net.*;
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
    /**
     * Resolves a relative path against a base path string (handles both URLs and local file paths).
     */
    public static String getRelativePath(String basePath, String relativePath) throws Exception {
        URI baseUri;

        // Check if the base path is a web URL or a local file path
        if (basePath.startsWith("http://") || basePath.startsWith("https://") || basePath.startsWith("file://")) {
            baseUri = new URI(basePath);
        } else {
            // Treat it as a standard local disk path
            baseUri = new File(basePath).toURI();
        }

        // Resolve the relative path and return it as a string representation
        return baseUri.resolve(relativePath).toString();
    }

    /**
     * Fetch String content from a path string (Supports http://, https://, and file:// or raw disk paths)
     */
    public static String getStringFromPath(String path) throws Exception {
        URL url;

        // Convert the string path to a URL object
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file://")) {
            url = new URI(path).toURL();
        } else {
            // Convert raw local disk path (e.g., "./pack/pack.toml") to a file URL
            url = new File(path).toURI().toURL();
        }

        // Open the generic connection (works for both web and local files)
        URLConnection conn = url.openConnection();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
