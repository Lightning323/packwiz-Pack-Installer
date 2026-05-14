package com.lightning323.packInstaller.utils;

import com.lightning323.packInstaller.FileCleanup;
import com.lightning323.packInstaller.PackInstaller;
import com.lightning323.packInstaller.fileTypes.FileEntry;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.lightning323.packInstaller.PackInstaller.PATHS_TO_SPARE;
import static com.lightning323.packInstaller.PackInstaller.SKIP_HASH_CHECK;

public class IOUtils {

    public static boolean isInsideOrEqual(Path child, Path parent) {
        // 1. Normalize and get absolute paths to handle "." or ".." or relative vs absolute
        Path absChild = child.toAbsolutePath().normalize();
        Path absParent = parent.toAbsolutePath().normalize();

        // 2. startsWith handles both "inside" and "equal to"
        return absChild.startsWith(absParent);
    }

    public static void writeFile(String hashFormat, byte[] bytes, File outFile, String hash) throws IOException {
        //Assert the hash
        if (!SKIP_HASH_CHECK && !HashUtils.getHash(hashFormat, bytes).equals(hash)) {
            throw new IOException("Hash for \"" + outFile.toPath() + "\" does not match!");
        }
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
