package com.lightning323.packInstaller.installer.utils;

import com.lightning323.packInstaller.installer.PackInstaller;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static com.lightning323.packInstaller.installer.PackInstaller.SKIP_HASH_CHECK;

public class IOUtils {

    public static boolean isSameLevel(Path basePath, Path filePath) {
        Path relative = basePath.relativize(filePath);

        // File must have exactly one name element
        // Example: "file.txt"
        // Not allowed: "folder/file.txt"
        return relative.getNameCount() == 1;
    }

    public static boolean isInsideOrEqual(Path child, Path parent) {
        // 1. Normalize and get absolute paths to handle "." or ".." or relative vs absolute
        Path absChild = child.toAbsolutePath().normalize();
        Path absParent = parent.toAbsolutePath().normalize();

        // 2. startsWith handles both "inside" and "equal to"
        return absChild.startsWith(absParent);
    }

    public static Path getJarPath() {
        Path jarFull = null;
        try {
            jarFull = Path.of(
                    PackInstaller.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
        }
        return jarFull;
    }

    public static void writeFile(byte[] bytes, File outFile, String hashFormat, String hash) throws IOException {
        //Assert the hash
        if (!SKIP_HASH_CHECK && !HashUtils.getHash(hashFormat, bytes).equals(hash)) {
            throw new IOException("Hash for \"" + outFile.toPath() + "\" does not match!");
        }
        outFile.getParentFile().mkdirs();
        Files.write(outFile.toPath(), bytes);
    }

    public static URL getRelativeUrl(URL baseUrl, String relativePath) throws URISyntaxException, MalformedURLException {
        URI resolvedUri = baseUrl.toURI().resolve(relativePath);
        return new URL(resolvedUri.toString());
    }

    /**
     * Helper to fetch String content from a URL
     */
    public static String fetchString(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
