package com.lightning323.packInstaller.installer;

import com.lightning323.packInstaller.installer.utils.HashUtils;
import com.lightning323.packInstaller.installer.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lightning323.packInstaller.installer.PackInstaller.PATHS_TO_SPARE;
import static com.lightning323.packInstaller.installer.utils.IOUtils.*;

public class DownloadPhase {

    public static void download(List<InstallerEntry> files) throws InterruptedException {
        ExecutorService workerPool = Executors.newFixedThreadPool(8);

        AtomicBoolean stop = new AtomicBoolean(false);

        for (InstallerEntry entry : files) {
            if (!stop.get()) workerPool.submit(() -> {
                try {
                    if (entry.modFile != null) {
                        ModDownloader.checkAndDownloadMod(entry.modFile, entry.path);
                    } else {
                        download(entry.downloadURL, entry.hashFormat, entry.hash, entry.path.toFile());
                    }
                } catch (Exception e) {
                    PackInstaller.fail("Failed to download " + entry.toString(), e);
                }
            });
        }

        //Wait for all tasks to complete
        workerPool.shutdown();
        if (!workerPool.awaitTermination(10, TimeUnit.MINUTES)) {
            workerPool.shutdownNow();
        }
        System.out.println("\n--- Download Complete ---");
    }

    public static void download(URL url, String hashFormat, String hash, File outFile) throws IOException, InterruptedException, URISyntaxException {
        if(outFile.exists()){ //Check if this file already exists
            byte[] bytes = Files.readAllBytes(outFile.toPath());
            String fileHash = HashUtils.getHash(hashFormat, bytes);
            if (fileHash.equals(hash)) {
                return; //Already downloaded and correct
            }
        }

        System.out.println("Downloading: " + outFile.getName() + " \t (" + url + ")..");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(url.toURI())
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
            IOUtils.writeFile(baos.toByteArray(), outFile, hashFormat, hash);
        } else {
            throw new RuntimeException("Failed to download file. HTTP Status: " + response.statusCode());
        }
    }
}
