package com.lightning323.packInstaller.crashReporter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.OutputStream;

public class ModpackCrashReporter {

    public static void main(String[] args) {
        System.out.println("[Post-Exit] Starting crash reporter...");
        String minecraftDirPath = System.getenv("INST_MC_DIR");
        File mcDir = minecraftDirPath != null ? new File(minecraftDirPath) : new File(".");

        ReporterConfig config = ReporterConfig.loadOrCreateConfig(mcDir);
        System.out.println("[Post-Exit] Config loaded: " + config.toString());

        String instanceName = System.getenv("INST_NAME");
        if (instanceName == null) instanceName = "Minecraft Instance";

        final int exitCode = -1;
        AtomicReference<String> logURL = new AtomicReference<>("No log file could be found or read.");
        AtomicReference<String> crashURL = new AtomicReference<>(null);
        AtomicReference<String> debugLogURL = new AtomicReference<>(null);
        AtomicReference<String> playerUsername = new AtomicReference<>(null);

        try {
            File latestLog = new File(mcDir, "logs/latest.log");
            if (latestLog.exists()) {
                Thread logUploadThread = new Thread(() -> {
                    logURL.set(uploadToMcLogs(latestLog));
                });

                Thread debugLogUploadThread = new Thread(() -> {
                    File debugLog = new File(mcDir, "logs/debug.log");
                    if (debugLog.exists()) debugLogURL.set(uploadToMcLogs(debugLog));
                });

                // Thread 2: Username Scraper
                Thread usernameThread = new Thread(() -> {
                    if (config.allowUsernames) {
                        playerUsername.set(parseUsernameFromLog(latestLog));
                    }
                });

                // Thread 3: Crash Report Finder & Uploader
                Thread crashUploadThread = new Thread(() -> {
                    File crashFile = findCrashReport(mcDir, latestLog);
                    if (crashFile != null && crashFile.exists()) {
                        System.out.println("[Post-Exit] Found crash file: " + crashFile.getName() + ". Uploading...");
                        crashURL.set(uploadToMcLogs(crashFile));
                    }
                });

                // Start all processes concurrently
                logUploadThread.start();
                usernameThread.start();
                crashUploadThread.start();

                try {
                    // Block main timeline until ALL background work has stabilized completely
                    logUploadThread.join();
                    usernameThread.join();
                    debugLogUploadThread.join();
                    crashUploadThread.join();
                } catch (InterruptedException e) {
                    System.err.println("[Main] Interrupted while waiting for uploads to finalize.");
                }
            }
        } finally {
            // 4. Dispatch final Discord notice payload securely
            System.out.println("[Main] Dispatching final Discord notice payload...");
            sendDiscordWebhook(config.webhookUrl, playerUsername.get(), instanceName, exitCode, logURL.get(), debugLogURL.get(), crashURL.get());
        }
    }


    private static String parseUsernameFromLog(File latestLog) {
        if (latestLog == null || !latestLog.exists() || !latestLog.canRead()) {
            return "Unknown Player";
        }
        Pattern[] userPatterns = new Pattern[]{
                Pattern.compile("Logged in as\\s+([^\\s]+)\\s+with\\s+uuid"),
                Pattern.compile("Setting user:\\s+([^\\s]+)"),
                Pattern.compile("--username\\s+([^\\s,]+)")
        };
        try (BufferedReader reader = new BufferedReader(new FileReader(latestLog, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Iterate through every regex pattern for the current log line
                for (Pattern pattern : userPatterns) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1).trim(); // Returns the dynamically matched gamertag
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Post-Exit] Error parsing gamertag from log: " + e.getMessage());
        }

        return "Unknown Player"; // Fallback if parsing fails or user is playing offline mode
    }

    private static File findCrashReport(File mcDir, File latestLog) {
        if (latestLog == null || !latestLog.exists() || !latestLog.canRead()) {
            return null;
        }

        Pattern crashPattern = Pattern.compile("Crash report saved to \\s*\"?([^\"]+\\.txt)\"?");

        try (BufferedReader reader = new BufferedReader(new FileReader(latestLog, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = crashPattern.matcher(line);
                if (matcher.find()) {
                    String extractedPath = matcher.group(1).trim();
                    File crashFile = new File(extractedPath);

                    if (crashFile.exists()) {
                        System.out.println("[Post-Exit] Parsed crash report path from log: " + crashFile.getName());
                        return crashFile;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Post-Exit] Error parsing latest.log for crash reports: " + e.getMessage());
        }

        //If we cant find it from the log, find it in the folders
        File crashDir = new File(mcDir, "crash-reports");
        if (crashDir.exists()) {
            File[] crashFiles = crashDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (crashFiles != null && crashFiles.length > 0) {
                //Get the latest crash report
                Arrays.sort(crashFiles, Comparator.comparingLong(File::lastModified).reversed());
                File crashLog = crashFiles[0];
                if (CrashLogParser.isCrashFromToday(crashLog)) return crashLog;
            }
        }

        return null;
    }


    private static String uploadToMcLogs(File logFile) {
        try {
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(logFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contentBuilder.append(line).append("\n");
                }
            }

            String rawContent = "content=" + java.net.URLEncoder.encode(contentBuilder.toString(), "UTF-8");

            URL url = new URL("https://api.mclo.gs/1/log");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(rawContent.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String response = br.readLine();
                    int urlIndex = response.indexOf("\"url\":\"");
                    if (urlIndex != -1) {
                        int start = urlIndex + 7;
                        int end = response.indexOf("\"", start);
                        return response.substring(start, end).replace("\\/", "/");
                    }
                }
            }
            return "Failed to upload log (API HTTP Code: " + conn.getResponseCode() + ")";
        } catch (Exception e) {
            return "Error uploading to mclo.gs: " + e.getMessage();
        }
    }


    private static final ObjectMapper mapper = new ObjectMapper();

    private static void sendDiscordWebhook(String webhookURL, String playerName, String instanceName, int exitCode,
                                           String mclogsUrl, String debugLogUrl, String crashLogUrl) {
        if (webhookURL == null || webhookURL.trim().isEmpty()) {
            System.err.println("[Post-Exit] Webhook URL is null or empty. Skipping execution.");
            return;
        }

        try {
            // 1. Color logic (Green for success/crashlogs, Red for clean exits without crashlogs?
            // Note: Check if your original ternary logic had the colors inverted)
            int color = (crashLogUrl != null || exitCode != 0) ? 65280 : 16711680;
            String timestamp = Instant.now().toString();

            // 2. Build the JSON structure safely using Jackson Nodes
            ObjectNode payload = mapper.createObjectNode();
            ArrayNode embeds = payload.putArray("embeds");
            ObjectNode embed = embeds.addObject();

            embed.put("title", "Instance Session Terminated");
            embed.put("color", color);
            embed.put("timestamp", timestamp);

            ArrayNode fields = embed.putArray("fields");

            // Conditionally add Username field
            if (playerName != null && !playerName.trim().isEmpty()) {
                fields.addObject()
                        .put("name", "Username")
                        .put("value", playerName)
                        .put("inline", true);
            }

            // Add Instance field
            fields.addObject()
                    .put("name", "Instance")
                    .put("value", instanceName != null ? instanceName : "Unknown")
                    .put("inline", true);

            // Conditionally add Exit Code field
            if (exitCode != -1) {
                fields.addObject()
                        .put("name", "Exit Code")
                        .put("value", "`" + exitCode + "`")
                        .put("inline", true);
            }

            // Add Latest Log field
            fields.addObject()
                    .put("name", "Latest log")
                    .put("value", mclogsUrl != null ? mclogsUrl : "N/A")
                    .put("inline", false);

            // Conditionally add Crash Log field
            if (crashLogUrl != null && !crashLogUrl.trim().isEmpty()) {
                fields.addObject()
                        .put("name", "Crash log")
                        .put("value", crashLogUrl)
                        .put("inline", false);
            }

            // Conditionally add Debug Log field
            if (debugLogUrl != null && !debugLogUrl.trim().isEmpty()) {
                fields.addObject()
                        .put("name", "Debug log")
                        .put("value", debugLogUrl)
                        .put("inline", false);
            }

            // Convert the structural node straight to a byte array
            byte[] jsonBytes = mapper.writeValueAsBytes(payload);

            // 3. HTTP Request Execution
            URL url = new URL(webhookURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Java-Prism-PostExit");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
            }

            // Check response to log actual API failures
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                System.err.println("[Post-Exit] Discord returned error HTTP status: " + responseCode);
            }

            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Post-Exit] Failed sending payload message to Discord webhook: " + e.getMessage());
        }
    }
}