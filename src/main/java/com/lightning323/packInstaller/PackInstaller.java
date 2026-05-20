package com.lightning323.packInstaller;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.lightning323.packInstaller.fileTypes.FileEntry;
import com.lightning323.packInstaller.fileTypes.IndexFile;
import com.lightning323.packInstaller.fileTypes.PackConfig;
import com.lightning323.packInstaller.utils.FileDownloader;
import com.lightning323.packInstaller.utils.UIUtils;

import java.io.File;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lightning323.packInstaller.utils.IOUtils.fetchString;
import static com.lightning323.packInstaller.utils.IOUtils.getRelativeUrl;

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


    @Option(names = {"-u", "--url"}, description = "URL to the packwiz pack.toml file")
    public static URL PACK_TOML_URL;

    @Option(names = {"-s", "--save"}, description = "The output save directory (default: ./)", defaultValue = "./")
    public static File saveDir;

    @Option(names = {"-r", "--reset"}, description = "Do a full cleanup (reset all files)")
    public static boolean FULL_RESET = false;

    @Option(names = {"-sm", "--spare-added-mods"}, description = "If we should spare mods added by the user")
    public static boolean SPARE_ADDED_MODS = false;

    @Option(names = {"-sh", "--ship-hash-check"}, description = "If we should skip checking hashes")
    public static boolean SKIP_HASH_CHECK = false;

    @Option(
            names = {"--spare"},
            description = "Files/directories to prevent overwriting or deletion",
            split = ","
    )
    public static HashSet<Path> PATHS_TO_SPARE = new HashSet<>();
    public static FileCleanup fileCleanup;

    static {
        PATHS_TO_SPARE.add(Paths.get("options.txt"));
        PATHS_TO_SPARE.add(Paths.get("servers.dat"));
    }

    private static void fail(String message) {
        System.err.println("\nFAIL:\n" + message.toUpperCase());
        UIUtils.detachedAlert("Installation failed", message);
        System.exit(1);
    }

    private static void fail(String message, Throwable t) {
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
            AtomicBoolean popup = new AtomicBoolean(true);
            long startTime = System.currentTimeMillis();
            System.out.println("Fetching pack configuration...");
            String packContent = fetchString(PACK_TOML_URL);

            // Deserialize PackConfig
            PackConfig config = mapper.readValue(packContent, PackConfig.class);

            System.out.println("--- Pack Info ---");
            System.out.println("Name: " + config.name);
            if (config.versions != null) {
                System.out.println("Minecraft Version: " + config.versions.get("minecraft"));
            }


            if (config.index != null) {
                Executors.newScheduledThreadPool(1).schedule(() -> {
                    UIUtils.detachedAlert("Downloading Modpack...", "Your \"" + config.name + "\" pack is being installed...");
                }, 2000, TimeUnit.MILLISECONDS);

                System.out.println("\n--- Index ---");
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
                if (!saveDir.exists()) {
                    fail("Failed to create save directory in " + saveDir.getAbsolutePath());
                }

                fileCleanup = new FileCleanup(saveDir);
                boolean shouldUpdate = fileCleanup.calculateModsToSpare(indexURL, indexData, config.index.hashFormat, config.index.hash);
                if (!shouldUpdate) return;

                System.out.println("\n" +
                        "--- Downloading to " + saveDir.getAbsolutePath() + " ---");
                AtomicBoolean stop = new AtomicBoolean(false);

                for (FileEntry entry : indexData.files) {
                    if (!stop.get()) workerPool.submit(() -> {
                        try {
                            FileDownloader.checkAndDownloadFile(indexURL, saveDir, config.index.hashFormat, entry);
                        } catch (Exception e) {
                            fail("Failed to download " + entry.file(), e);

                        }
                    });
                }

                //Wait for all tasks to complete
                workerPool.shutdown();
                if (!workerPool.awaitTermination(10, TimeUnit.MINUTES)) {
                    workerPool.shutdownNow();
                }
                System.out.println("\n--- Download Complete ---");
                fileCleanup.deleteUnIncludedFiles(indexData);
                System.out.println("\n--- Cleanup Complete ---");
                System.out.println("Elapsed time: " + (System.currentTimeMillis() - startTime) / 1000 + "s");

                if (System.currentTimeMillis() - startTime > 2000) {
                    UIUtils.detachedAlert("Modpack download complete!", "Download complete for \"" + config.name + "\"");
                }
            } else {
                System.err.println("No index found!");
            }

        } catch (Exception e) {
            fail("Could not complete installation: ", e);

        }
    }


    static private final ExecutorService workerPool = Executors.newFixedThreadPool(8);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PackInstaller()).execute(args);
        System.exit(exitCode);
    }


}