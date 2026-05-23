package com.lightning323.packInstaller.installer.fileTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PackConfig {
    public String name = "";
    public String author = "";
    public String version = "";

    @JsonProperty("pack-format")
    public String packFormat = "";

    public IndexSection index;
    public Map<String, String> versions = new HashMap<>();

    public PackConfig() {} // Default constructor for Jackson
}

