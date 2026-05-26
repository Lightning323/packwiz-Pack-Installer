package com.lightning323.packInstaller.installer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.lightning323.packInstaller.installer.fileTypes.FileEntry;
import com.lightning323.packInstaller.installer.fileTypes.IndexFile;
import com.lightning323.packInstaller.installer.fileTypes.ModFile;
import com.lightning323.packInstaller.installer.fileTypes.PackConfig;
import com.lightning323.packInstaller.installer.utils.IOUtils;
import com.lightning323.packInstaller.installer.utils.UIUtils;

import java.io.*;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.lightning323.packInstaller.installer.utils.IOUtils.fetchString;
import static com.lightning323.packInstaller.installer.utils.IOUtils.getRelativeUrl;

@Command(
        name = "packwiz pack installer",
        mixinStandardHelpOptions = true,
        version = "1.0.3",
        headerHeading = "%n", // Adds a newline before the header
        header = {
                "@|fg(cyan)  _       _       ___       __ ___            _  _  |@",
                "@|fg(cyan) |_) /\\  /  |/     |  |\\ | (_   |  /\\  |  |  |_ |_) |@",
                "@|fg(cyan) |  /--\\ \\_ |\\    _|_ | \\| __)  | /--\\ |_ |_ |_ | \\ |@",
                "",
                "@|bold,white PACKWIZ PACK-INSTALLER CLI TOOL|@",
                "@|faint,black ---------------------------------------------------|@" // Changed gray to faint black
        },
        description = "A package downloading utility."
)
public class PackInstaller implements Runnable {

    //Parameters
    @Option(names = {"-u", "--url"}, description = "URL to the packwiz pack.toml file")
    public static URL PACK_TOML_URL;

    @Option(names = {"-s", "--save"}, description = "The output save directory (default: ./)", defaultValue = "./")
    public static File saveDir;

    @Option(names = {"-r", "--reset"}, description = "Do a full cleanup (reset all files)")
    public static boolean FULL_RESET = false;

    @Option(names = {"-sm", "--spare-added-mods"}, description = "If we should spare mods added by the user")
    public static boolean SPARE_ADDED_MODS = false;

    @Option(names = {"-shc", "--ship-hash-check"}, description = "If we should skip checking hashes")
    public static boolean SKIP_HASH_CHECK = false;

    @Option(
            names = {"--spare-cleanup", "--sc"},
            description = "Files/directories to prevent deletion",
            split = ","
    )
    public static HashSet<String> SPARE_CLEANUP = new HashSet<>();
    static {
        SPARE_CLEANUP.add("config");
    }

    @Option(
            names = {"--spare-overwrite", "--so"},
            description = "Files to prevent deletion or overwriting",
            split = ","
    )
    public static HashSet<String> SPARE_OVERWRITE = new HashSet<>();
    static {
        SPARE_OVERWRITE.add("options.txt");
        SPARE_OVERWRITE.add("servers.dat");
    }

    //-----------------------------------------------------------------------------------------------------
    //-----------------------------------------------------------------------------------------------------
    //-----------------------------------------------------------------------------------------------------

    public static final String MOD_TOML_FILE_EXT = ".pw.toml";

    private static void fail(String message) {
        System.err.println("\nFAIL:\n" + message.toUpperCase());
        UIUtils.detachedAlert("Installation failed", message);
        System.exit(1);
    }

    public static void fail(String message, Throwable t) {
        System.err.println("\nFAIL:\n" + message.toUpperCase());
        UIUtils.detachedAlert("Installation failed", message);
        if (t != null && t.getMessage() != null) {
            System.err.println(t.getMessage());
            t.printStackTrace();
        }
        System.exit(1);
    }

    // Setup Mapper
    static TomlMapper mapper = new TomlMapper();

    static {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }


    public final static HashSet<Path> modsToSpare = new HashSet<>();

    /**
     * Downloads and returns the entire contents of a URL as a byte array.
     */
    private static ModFile getModFromPwToml(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try (ByteArrayOutputStream writer = new ByteArrayOutputStream();
             var inputStream = conn.getInputStream()) {
            writer.write(inputStream.readAllBytes());
            return ModDownloader.getFileEntry(writer.toByteArray());
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public void run() {
        if (SKIP_HASH_CHECK) {
            System.out.println("WARNING: Skipping hash check is not recommended for security reasons. use at your own risk!");
        }
        if (PACK_TOML_URL == null) {
            System.err.println("Pack TOML URL is required");
            CommandLine.usage(this, System.out);
            System.exit(1);
        }

        try {
            long startTime = System.currentTimeMillis();
            System.out.println("Fetching pack configuration...");
            String packContent = fetchString(PACK_TOML_URL);

            // Deserialize PackConfig
            PackConfig config = mapper.readValue(packContent, PackConfig.class);

            System.out.println("--- Reading Pack TOML ---");
            System.out.println("Name: " + config.name);
            if (config.versions != null) {
                System.out.println("Minecraft Version: " + config.versions.get("minecraft"));
            }

            //Index
            if (config.index != null) {
                Executors.newScheduledThreadPool(1).schedule(() -> {
                    UIUtils.detachedAlert("Downloading Modpack...", "Your \"" + config.name + "\" pack is being installed...");
                }, 2000, TimeUnit.MILLISECONDS);

                System.out.println("\n--- Reading Index ---");
                if (config.index.file == null) throw new IllegalArgumentException("Index file cannot be null");
                System.out.println("Index File Path: " + config.index.file);
                if (config.index.hashFormat == null) throw new IllegalArgumentException("Hash type cannot be null");
                System.out.println("Hash Format: " + config.index.hashFormat);
                if (config.index.hash == null) throw new IllegalArgumentException("Hash cannot be null");
                System.out.println("Hash: " + config.index.hash);

                //Get the index.toml
                URL indexURL = getRelativeUrl(PACK_TOML_URL, config.index.file);
                String indexContent = fetchString(indexURL);
                IndexFile indexData = mapper.readValue(indexContent, IndexFile.class);
                saveDir.mkdirs();
                Path savePath = saveDir.toPath();
                if (!saveDir.exists()) {
                    fail("Failed to create save directory in " + saveDir.getAbsolutePath());
                }
                //Read cache file
                CacheFile cacheFile = CacheFile.read(savePath);
                if (cacheFile != null) {
                    HashSet<Path> cachedMods = new HashSet<>();
                    for (String s : cacheFile.modFiles) {
                        Path f = savePath.resolve(s);
                        cachedMods.add(f);
                    }

                    //If the hashes are the same
                    if (!FULL_RESET &&
                            cacheFile.indexHashFormat != null && cacheFile.indexHash != null
                            && cacheFile.indexHashFormat.equals(config.index.hashFormat)
                            && cacheFile.indexHash.equals(config.index.hash)) {
                        System.out.println("\n\n--- Hashes match. Checking cached files ---");
                        //Check to ensure all files are present
                        boolean foundAllFilesThatShouldBeThere = true;
                        for (String s : cacheFile.otherFiles) {
                            Path f = savePath.resolve(s);
                            if (!f.toFile().exists()) {
                                foundAllFilesThatShouldBeThere = false;
                                break;
                            }
                        }
                        for (Path f : cachedMods) {
                            if (!f.toFile().exists()) {
                                foundAllFilesThatShouldBeThere = false;
                                break;
                            }
                        }
                        if (foundAllFilesThatShouldBeThere) {
                            System.out.println("Hashes are the same and all files are present, no need to update.");
                            return;
                        } else {
                            System.out.println("Hashes are the same, but some files are missing. Need to update.");
                        }
                    }


                    if (!FULL_RESET && SPARE_ADDED_MODS) {
                        System.out.println("\n\n--- Calculating mods to spare ---");
                        HashSet<Path> existingMods = new HashSet<>();
                        for (File f : savePath.resolve("mods").toFile().listFiles()) {
                            existingMods.add(f.toPath());
                        }
                        HashSet<Path> excluded = new HashSet<>();
                        excluded.addAll(existingMods);
                        excluded.removeAll(cachedMods);
                        modsToSpare.clear();
                        for (Path p : excluded) {
                            modsToSpare.add(savePath.relativize(p));
                        }
                    }
                }
                if (SPARE_ADDED_MODS) System.out.println("Mods to spare: " + modsToSpare.toString());

                List<InstallerEntry> allFiles = new ArrayList<>();

                //Index mods from index.toml
                System.out.println("\n\n--- Gathering index files ---");
                for (FileEntry entry : indexData.files) {
                    File dir = savePath.resolve(entry.file()).toFile().getParentFile();
                    if (entry.file().endsWith(MOD_TOML_FILE_EXT)) {
                        try {
                            ModFile modFile = getModFromPwToml(IOUtils.getRelativeUrl(indexURL, entry.file()));
                            allFiles.add(new InstallerEntry(Path.of(dir.getPath(), modFile.filename), modFile));
                        } catch (IOException | URISyntaxException e) {
                            System.out.println("Failed to get mod from pw toml: " + entry.file());
                        }
                    } else {
                        Path path = Path.of(dir.getPath(), entry.file());
                        URL url = getRelativeUrl(indexURL, entry.file());
                        allFiles.add(new InstallerEntry(path, url, entry.hash(), config.index.hashFormat));
                    }
                }
                System.out.println("\n--- Downloading to " + saveDir.getAbsolutePath() + " ---");
                DownloadPhase.download(savePath, allFiles);

                System.out.println("\n--- Cleanup ---");
                CleanupPhase.cleanup(savePath, allFiles);
                System.out.println("\n--- Cleanup Complete ---");

                System.out.println("Elapsed time: " + (System.currentTimeMillis() - startTime) / 1000 + "s");

                if (System.currentTimeMillis() - startTime > 2000) {
                    UIUtils.detachedAlert("Modpack download complete!", "Download complete for \"" + config.name + "\"");
                }


                //Write cache file
                CacheFile w = new CacheFile();
                for (InstallerEntry entry : allFiles) {
                    if (entry.isMod()) w.modFiles.add(entry.path.toString());
                    else w.otherFiles.add(entry.path.toString());
                }
                w.indexHashFormat = config.index.hashFormat;
                w.indexHash = config.index.hash;
                w.write(savePath);
            } else {
                System.err.println("No index found!");
            }

        } catch (Exception e) {
            fail("Could not complete installation: ", e);
        }
    }


    public static void main(String[] args) {
        int exitCode = new CommandLine(new PackInstaller()).execute(args);
        System.exit(exitCode);
    }


}




