package com.lightning323.packInstaller.installer.fileTypes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IndexSection {
    public String file = "";

    @JsonProperty("hash-format")
    public String hashFormat = "";

    public String hash = "";

    public IndexSection() {} // Default constructor for Jackson
}
