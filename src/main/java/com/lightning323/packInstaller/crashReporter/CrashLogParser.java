package com.lightning323.packInstaller.crashReporter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrashLogParser {

    // Regex to match 4 digits, 2 digits, 2 digits separated by hyphens
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    public static boolean isCrashFromToday(File crashLog) {
        String fileName = crashLog.getName();
        Matcher matcher = DATE_PATTERN.matcher(fileName);

        if (matcher.find()) {
            // Extract the matched date string (e.g., "2026-05-27")
            String dateString = matcher.group(1);
            
            // Parse into a LocalDate object
            LocalDate logDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
            
            // Compare with today's date
            return logDate.equals(LocalDate.now());
        }

        // Return false if the filename doesn't contain a valid date pattern
        return false;
    }
}