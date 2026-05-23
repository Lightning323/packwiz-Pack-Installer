package com.lightning323.packInstaller.installer.fileTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // <--- Add this line!
public record FileEntry(
        String file,
        String hash
) {
}