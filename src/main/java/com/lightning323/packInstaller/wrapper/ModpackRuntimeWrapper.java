package com.lightning323.packInstaller.wrapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModpackRuntimeWrapper {

    //TODO: As of right now, this wrapper only supports prism Launcher
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("[Wrapper] Error: No Java execution arguments passed by launcher.");
            System.exit(1);
        }

        // 1. Capture Prism environment variables
        String minecraftDirPath = System.getenv("INST_MC_DIR");
        File mcDir = minecraftDirPath != null ? new File(minecraftDirPath) : new File(".");
        WrapperConfig CONFIG = WrapperConfig.loadOrCreateConfig(mcDir);

        String instanceName = System.getenv("INST_NAME");
        if (instanceName == null) instanceName = "Minecraft Instance";
        int exitCode = -1;

        String playerUsername = System.getenv("INST_MC_USER");

        try {
            System.out.println("[Wrapper] Launching Minecraft process...");

            // 2. Start the Minecraft Java process using the arguments passed by Prism
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO(); // Routes game output directly to the Prism Console window
            Process process = pb.start();

            // Wait for the game to close and capture the exit code
            exitCode = process.waitFor();
            System.out.println("[Wrapper] Minecraft closed with exit code: " + exitCode);

        } catch (Exception e) {
            System.err.println("[Wrapper] Failed to execute or monitor Minecraft process.");
            e.printStackTrace();
            System.exit(1);
        }

        // 3. Look for logs or crash reports based on context
        String logURL = "No log file could be found or read.";
        String crashURL = null;

        File logFile = getLatestLog(mcDir, exitCode);

        if (logFile != null && logFile.exists()) {
            System.out.println("[Wrapper] Found log file: " + logFile.getName() + ". Uploading to mclo.gs...");
            logURL = uploadToMcLogs(logFile);

            // Find crash report
            File crashFile = findCrashReportFromLog(logFile);
            if (crashFile != null && crashFile.exists()) {
                System.out.println("[Wrapper] Found crash file: " + logFile.getName() + ". Uploading to mclo.gs...");
                crashURL = uploadToMcLogs(crashFile);
            }
        }

        // 4. Send the notification payload to Discord
        sendDiscordWebhook(CONFIG.webhookUrl, playerUsername, instanceName, exitCode, logURL, crashURL);

        // 5. Exit out with the game's actual status code so Prism tracks it correctly
        System.exit(exitCode);
    }

    /**
     * Parses the latest.log file to extract the absolute path of a saved crash report.
     * * @param latestLog The File object pointing to logs/latest.log
     *
     * @return A verified File object pointing to the crash report, or null if not found.
     */
    private static File findCrashReportFromLog(File latestLog) {
        if (latestLog == null || !latestLog.exists() || !latestLog.canRead()) {
            return null;
        }

        // Pattern to look for "Crash report saved to " followed by the absolute file path ending in .txt
        // Handles quotes if present and captures the raw path inside group 1
        Pattern crashPattern = Pattern.compile("Crash report saved to \\s*\"?([^\"]+\\.txt)\"?");

        try (BufferedReader reader = new BufferedReader(new FileReader(latestLog, StandardCharsets.UTF_8))) {
            String line;
            // Read through the log file line by line
            while ((line = reader.readLine()) != null) {
                Matcher matcher = crashPattern.matcher(line);
                if (matcher.find()) {
                    String extractedPath = matcher.group(1).trim();
                    File crashFile = new File(extractedPath);

                    // Double check that the file actually exists on the system disk
                    if (crashFile.exists()) {
                        System.out.println("[Wrapper] Successfully parsed crash report path from log: " + crashFile.getName());
                        return crashFile;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Wrapper] Error parsing latest.log for crash reports: " + e.getMessage());
        }
//        if (exitCode != 0) {
//            File crashFolder = new File(mcDir, "crash-reports");
//            if (crashFolder.exists() && crashFolder.isDirectory()) {
//                File[] crashFiles = crashFolder.listFiles((dir, name) -> name.startsWith("crash-") && name.endsWith(".txt"));
//                if (crashFiles != null && crashFiles.length > 0) {
//                    // Sort by newest modified time
//                    Arrays.sort(crashFiles, Comparator.comparingLong(File::lastModified).reversed());
//                    return crashFiles[0];
//                }
//            }
//        }
        return null;
    }

    private static File getLatestLog(File mcDir, int exitCode) {
        // Fall back to latest.log
        File latestLog = new File(mcDir, "logs/latest.log");
        if (latestLog.exists()) {
            return latestLog;
        }

        return null;
    }

    private static String uploadToMcLogs(File logFile) {
        try {
            // Read file contents into a String
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(logFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contentBuilder.append(line).append("\n");
                }
            }

            // Build payload format required by api.mclo.gs
            // URL-encoded format: content=url_encoded_text
            String rawContent = "content=" + java.net.URLEncoder.encode(contentBuilder.toString(), "UTF-8");

            URL url = new URL("https://api.mclo.gs/1/log");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(rawContent.getBytes(StandardCharsets.UTF_8));
            }

            // Parse response to find "url":"https://mclo.gs/XXXXXXX"
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

    private static void sendDiscordWebhook(String webhookURL, String playerName, String instanceName, int exitCode, String mclogsUrl, String crashLogUrl) {
        try {
            int color = (exitCode == 0) ? 65280 : 16711680; // Green for success, Red for non-zero exit strings
            String timestamp = Instant.now().toString();

            // Native JSON payload string mapping
            String jsonPayload = "{"
                    + "\"embeds\": [{"
                    + "  \"title\": \"🎮 Instance Session Terminated\","
                    + "  \"color\": " + color + ","
                    + "  \"fields\": ["
                    + (playerName == null ? "" : "    { \"name\": \"Username\", \"value\": \"" + playerName + "\", \"inline\": true },")
                    + "    { \"name\": \"Instance\", \"value\": \"" + instanceName + "\", \"inline\": true },"
                    + "    { \"name\": \"Exit Code\", \"value\": \"`" + exitCode + "`\", \"inline\": true },"
                    + "    { \"name\": \"Latest log\", \"value\": \"" + mclogsUrl + "\", \"inline\": false },"
                    + (crashLogUrl == null ? "" : "    { \"name\": \"Crash log\", \"value\": \"" + crashLogUrl + "\", \"inline\": false }")
                    + "  ],"
                    + "  \"timestamp\": \"" + timestamp + "\""
                    + "}]"
                    + "}";

            URL url = new URL(webhookURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Java-Prism-Wrapper");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            // Flush the buffer streams to ensure delivery
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Wrapper] Failed sending payload message to Discord webhook: " + e.getMessage());
        }
    }
}