package com.lightning323.packInstaller.crashReporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;

public class ReporterConfig {
    // Default values if a new file needs to be generated
    public String webhookUrl = "YOUR_DISCORD_WEBHOOK_URL_HERE";
    public boolean allowUsernames = false;

    public ReporterConfig() {
    }

    public static ReporterConfig loadOrCreateConfig(File mcDir) {
        File configFile = new File(mcDir, "wrapper_config.json");

        // Set up ObjectMapper with clean pretty-printing for generated configs
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // 1. If the config doesn't exist, create a default template
        if (!configFile.exists()) {
            System.out.println("[Wrapper] config.json not found. Generating template...");
            ReporterConfig defaultConfig = new ReporterConfig();

            try {
                mapper.writeValue(configFile, defaultConfig);
                System.out.println("[Wrapper] Default config.json created at: " + configFile.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[Wrapper] Failed to write default config.json: " + e.getMessage());
            }
            return defaultConfig;
        }

        // 2. If it does exist, map it directly to our configuration object
        try {
            ReporterConfig config = mapper.readValue(configFile, ReporterConfig.class);

            // Defensive check if the file was empty
            if (config == null) {
                return new ReporterConfig();
            }

            return config;
        } catch (Exception e) {
            System.err.println("[Wrapper] Error reading config.json with Jackson: " + e.getMessage());
            return new ReporterConfig(); // Safe fallback to defaults if users corrupt the JSON syntax
        }
    }
}