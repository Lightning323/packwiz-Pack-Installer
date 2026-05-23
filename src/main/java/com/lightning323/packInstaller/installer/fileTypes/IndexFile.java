package com.lightning323.packInstaller.installer.fileTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexFile {
    @JsonProperty("hash-format")
    public String hashFormat = "";
    
    public List<FileEntry> files = new ArrayList<>();

    public IndexFile() {} // Default constructor for Jackson
}

