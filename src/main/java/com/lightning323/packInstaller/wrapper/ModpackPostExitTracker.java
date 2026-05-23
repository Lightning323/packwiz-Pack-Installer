package com.lightning323.packInstaller.wrapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModpackPostExitTracker {

    public static void main(String[] args) {
        // 1. Capture variables from the system environment passed by Prism
        String minecraftDirPath = System.getenv("INST_MC_DIR");
        File mcDir = minecraftDirPath != null ? new File(minecraftDirPath) : new File(".");

        WrapperConfig CONFIG = WrapperConfig.loadOrCreateConfig(mcDir);

        String instanceName = System.getenv("INST_NAME");
        if (instanceName == null) instanceName = "Minecraft Instance";

        String playerUsername = System.getenv("INST_MC_USER");

        // 2. Extract the exit code from Prism's arguments
        // Post-exit commands append arguments manually or dynamically via tracking tokens
        int exitCode = -1;
        if (args.length > 0) {
            try {
                exitCode = Integer.parseInt(args[0]);
                System.out.println("[Post-Exit] Received Minecraft exit code: " + exitCode);
            } catch (NumberFormatException e) {
                System.err.println("[Post-Exit] Failed to parse exit code from argument: " + args[0]);
            }
        } else {
            System.err.println("[Post-Exit] Warning: No exit code parameter passed by launcher arguments.");
        }

        // 3. Process logs now that the game has completely stopped
        String logURL = "No log file could be found or read.";
        String crashURL = null;

        File logFile = getLatestLog(mcDir);

        if (logFile != null && logFile.exists()) {
            System.out.println("[Post-Exit] Found log file: " + logFile.getName() + ". Uploading to mclo.gs...");
            logURL = uploadToMcLogs(logFile);

            // Locate and upload the crash report if one was generated
            File crashFile = findCrashReportFromLog(logFile);
            if (crashFile != null && crashFile.exists()) {
                System.out.println("[Post-Exit] Found crash file: " + crashFile.getName() + ". Uploading to mclo.gs...");
                crashURL = uploadToMcLogs(crashFile);
            }
        }

        // 4. Dispatch final Discord notice payload
        sendDiscordWebhook(CONFIG.webhookUrl, playerUsername, instanceName, exitCode, logURL, crashURL);
    }

    private static File findCrashReportFromLog(File latestLog) {
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
        return null;
    }

    private static File getLatestLog(File mcDir) {
        File latestLog = new File(mcDir, "logs/latest.log");
        if (latestLog.exists()) {
            return latestLog;
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

    private static void sendDiscordWebhook(String webhookURL, String playerName, String instanceName, int exitCode, String mclogsUrl, String crashLogUrl) {
        try {
            int color = (exitCode == 0) ? 65280 : 16711680;
            String timestamp = Instant.now().toString();

            // Handle invalid/broken JSON syntax blocks dynamically if fields resolve to null
            String escapedUsernameLine = (playerName == null || playerName.trim().isEmpty())
                    ? ""
                    : "    { \"name\": \"Username\", \"value\": \"" + playerName + "\", \"inline\": true },";

            String jsonPayload = "{"
                    + "\"embeds\": [{"
                    + "  \"title\": \"🎮 Instance Session Terminated\","
                    + "  \"color\": " + color + ","
                    + "  \"fields\": ["
                    + escapedUsernameLine
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
            conn.setRequestProperty("User-Agent", "Java-Prism-PostExit");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Post-Exit] Failed sending payload message to Discord webhook: " + e.getMessage());
        }
    }
}