package com.lightning323.packInstaller.utils;

import com.lightning323.packInstaller.PackInstaller;
import com.lightning323.packInstaller.fileTypes.FileEntry;
import com.lightning323.packInstaller.fileTypes.ModFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static com.lightning323.packInstaller.PackInstaller.PATHS_TO_SPARE;
import static com.lightning323.packInstaller.utils.IOUtils.*;
import static com.lightning323.packInstaller.utils.ModDownloader.MOD_TOML_FILE_EXT;

public class FileDownloader {

    /**
     * Downloads and returns the entire contents of a URL as a byte array.
     */
    public static ModFile getModFromPwToml(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try (ByteArrayOutputStream writer = new ByteArrayOutputStream();
             var inputStream = conn.getInputStream()) {
            writer.write(inputStream.readAllBytes());
            return ModDownloader.getFileEntry(writer.toByteArray());
        } finally {
            conn.disconnect();
        }
    }

    public static void checkAndDownloadFile(URL baseUrl, File baseSaveDir, String hashFormat,
                                            FileEntry entry)
            throws IOException, SecurityException, URISyntaxException, InterruptedException {

        URL fileURL = getRelativeUrl(baseUrl, entry.file());

        HttpURLConnection conn = (HttpURLConnection) fileURL.openConnection();
        File outFile = new File(baseSaveDir, entry.file());

        File saveDir = outFile.getParentFile();
        saveDir.mkdirs();

        //Add the directory to the list of downloaded directories
        PackInstaller.fileCleanup.add(baseSaveDir, saveDir.toPath());
        String outHash = entry.hash();

        ModFile modFile = null;
        if (entry.file().endsWith(MOD_TOML_FILE_EXT)) { //Download the pw toml file
            ByteArrayOutputStream writer = new ByteArrayOutputStream();
            try (var inputStream = conn.getInputStream()) {
                writer.write(inputStream.readAllBytes());
            }
            modFile = ModDownloader.getFileEntry(writer.toByteArray());
            //We change the output file and hash to the mod itself
            outFile = new File(saveDir, modFile.filename); //We need to compare the mod file itself not the pw.toml file
            outHash = modFile.download.hash;
            hashFormat = modFile.download.hashFormat;
            PackInstaller.fileCleanup.modFiles.add(outFile.toPath());
        }

        if (outFile.exists()) {
            //If a file already exist, check if they are the same
            byte[] existingFile = Files.readAllBytes(outFile.toPath());
            String existingFileHash = HashUtils.getHash(hashFormat, existingFile);
            if (existingFileHash.equals(outHash)) {
                return; //The files are the same
            }

            if (!PackInstaller.FULL_RESET) {
                Path filePath = Paths.get(entry.file());
                if (PATHS_TO_SPARE.contains(filePath)) {  //Check if the file is in the DONT_OVERWRITE list
                    System.out.println("Skipping: " + entry.file());
                    return;
                }
                for (Path path : PackInstaller.PATHS_TO_SPARE) { //Check if the file is in a directory in the DONT_OVERWRITE list
                    if (isInsideOrEqual(filePath, path)) {
                        System.out.println("Skipping directory: " + path);
                        return;
                    }
                }
            }
        }

        if (modFile != null) { //Download a mod
            ModDownloader.checkAndDownloadMod(modFile, saveDir);
        } else {
            //Overwrite/write the file
            ByteArrayOutputStream writer = new ByteArrayOutputStream();
            System.out.println("Downloading: " + entry.file());
            try (var inputStream = conn.getInputStream()) {
                writer.write(inputStream.readAllBytes());
            }
            writeFile(hashFormat, writer.toByteArray(), outFile, entry.hash()); //Write the file if its not a PW toml file
        }
    }
}
