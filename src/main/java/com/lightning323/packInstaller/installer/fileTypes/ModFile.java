package com.lightning323.packInstaller.installer.fileTypes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModFile {
    public String name;
    public String filename;
    public String side;

    public Download download;
    public Update update;

    public static class Download {
        @JsonProperty("hash-format")
        public String hashFormat;
        @JsonProperty("hash")
        public String hash;
        @JsonProperty("mode")
        public String mode;
        @JsonProperty("url")
        public String url;
    }

    public static class Update {
        public CurseForge curseforge;
        public Modrinth modrinth;

        public static class CurseForge {
            @JsonProperty("file-id")
            public long fileId;
            @JsonProperty("project-id")
            public long projectId;
        }

        public static class Modrinth {
            @JsonProperty("mod-id")
            public String modId;
            @JsonProperty("version")
            public String version;
        }
    }
}