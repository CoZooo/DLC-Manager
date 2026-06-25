package com.cozooo.dlc_manager.tools.externalsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

public final class ExternalSyncCli {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ExternalSyncCli() {
    }

    public static void main(String[] args) {
        try {
            CliInput input = CliInput.parse(args);
            NativeSyncEngine engine = new NativeSyncEngine();
            switch (input.command) {
                case "preview" -> printPreview(engine.preview(input.request), input.json);
                case "apply" -> printResult(engine.apply(input.request), input.json);
                case "test-server" -> printServer(engine.testServer(requireServer(input.request)), input.json);
                case "help" -> printHelp();
                default -> throw new IllegalArgumentException("unknown command: " + input.command);
            }
        } catch (Exception ex) {
            System.err.println("error: " + ex.getMessage());
            System.err.println("run with `help` for usage");
            System.exit(1);
        }
    }

    private static SyncModels.ServerConfig requireServer(SyncModels.SyncRequest request) {
        if (request.server == null) {
            throw new IllegalArgumentException("test-server requires --host");
        }
        return request.server;
    }

    private static void printPreview(SyncModels.NativeSyncPreview preview, boolean json) {
        if (json) {
            System.out.println(GSON.toJson(preview));
            return;
        }
        System.out.println("DLC external sync preview");
        System.out.println("gameDir: " + preview.gameDir);
        System.out.println("modsDir: " + preview.modsDir);
        System.out.println("dlcRoot: " + preview.dlcRoot);
        System.out.println("remote entries: " + preview.remoteEntries.size());
        System.out.println("server files: " + preview.serverFiles.size());
        printIssues(preview.issues);
    }

    private static void printResult(SyncModels.NativeSyncResult result, boolean json) {
        if (json) {
            System.out.println(GSON.toJson(result));
            return;
        }
        System.out.println("DLC external sync result");
        System.out.println("downloaded: " + result.downloaded.size());
        result.downloaded.forEach(path -> System.out.println("  + " + path));
        System.out.println("uploaded: " + result.uploaded.size());
        result.uploaded.forEach(path -> System.out.println("  ^ " + path));
        System.out.println("deleted: " + result.deleted.size());
        result.deleted.forEach(path -> System.out.println("  - " + path));
        System.out.println("applied: " + result.applied + ", requiresRestart: " + result.requiresRestart);
        if (!result.messages.isEmpty()) {
            System.out.println("messages:");
            result.messages.forEach(message -> System.out.println("  ! " + message));
        }
        printIssues(result.preview.issues);
    }

    private static void printServer(SyncModels.TestServerResult result, boolean json) {
        if (json) {
            System.out.println(GSON.toJson(result));
            return;
        }
        System.out.println(result.ok ? "server ok" : "server failed");
        System.out.println(result.message);
        if (result.info != null) {
            System.out.println("base: " + result.info.baseVersion + ", dlc: " + result.info.dlcVersion);
            System.out.println("items: " + result.info.items.size());
        }
    }

    private static void printIssues(List<SyncModels.SyncIssue> issues) {
        System.out.println("issues: " + issues.size());
        for (SyncModels.SyncIssue issue : issues) {
            String fileName = issue.fileName == null ? "" : " " + issue.fileName;
            System.out.println("  [" + issue.severity + "] " + issue.kind + fileName + " - " + issue.message);
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  preview --game-dir <path> [--host <host> --port <port> --password <password>] [--json]");
        System.out.println("  apply --game-dir <path> [--host <host> --port <port> --password <password>] [--delete mods/file.jar] [--json]");
        System.out.println("  test-server --host <host> [--port <port>] [--password <password>] [--json]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --mods-dir <path>       Override gameDir/mods");
        System.out.println("  --dlc-root <path>       Override gameDir/DLC");
        System.out.println("  --timeout-ms <ms>       TCP/API/download timeout, default 10000");
        System.out.println("  --mc-version <version>  Optional API filter for version lookups");
        System.out.println("  --loader <loader>       Optional API filter, e.g. fabric/forge/neoforge");
        System.out.println("  --no-download           Do not download missing remote entries");
        System.out.println("  --no-upload             Do not upload local/server mismatches");
        System.out.println("  --no-apply              Do not call set/apply on the server");
    }

    private static final class CliInput {
        String command = "help";
        boolean json;
        SyncModels.SyncRequest request = new SyncModels.SyncRequest();

        static CliInput parse(String[] args) {
            CliInput input = new CliInput();
            List<String> arguments = args == null ? List.of() : List.of(args);
            int index = 0;
            if (!arguments.isEmpty() && !arguments.get(0).startsWith("--")) {
                input.command = arguments.get(0).trim().toLowerCase();
                index = 1;
            }
            List<String> deletes = new ArrayList<>();
            while (index < arguments.size()) {
                String option = arguments.get(index++);
                switch (option) {
                    case "--json" -> input.json = true;
                    case "--game-dir" -> input.request.gameDir = value(arguments, index++, option);
                    case "--mods-dir" -> input.request.modsDir = value(arguments, index++, option);
                    case "--dlc-root" -> input.request.dlcRoot = value(arguments, index++, option);
                    case "--host" -> server(input).host = value(arguments, index++, option);
                    case "--port" -> server(input).port = parseInt(value(arguments, index++, option), "port");
                    case "--password" -> server(input).password = value(arguments, index++, option);
                    case "--timeout-ms" -> server(input).timeoutMs = parseInt(value(arguments, index++, option), "timeout-ms");
                    case "--player" -> server(input).player = value(arguments, index++, option);
                    case "--uuid" -> server(input).uuid = value(arguments, index++, option);
                    case "--mc-version" -> input.request.minecraftVersion = value(arguments, index++, option);
                    case "--loader" -> input.request.loader = value(arguments, index++, option);
                    case "--delete" -> deletes.add(value(arguments, index++, option));
                    case "--no-download" -> input.request.downloadMissing = false;
                    case "--no-upload" -> input.request.uploadExtra = false;
                    case "--no-apply" -> input.request.applyChanges = false;
                    default -> throw new IllegalArgumentException("unknown option: " + option);
                }
            }
            input.request.deleteConfirmed = deletes;
            if (("preview".equals(input.command) || "apply".equals(input.command)) && (input.request.gameDir == null || input.request.gameDir.isBlank())) {
                throw new IllegalArgumentException(input.command + " requires --game-dir");
            }
            return input;
        }

        private static SyncModels.ServerConfig server(CliInput input) {
            if (input.request.server == null) {
                input.request.server = new SyncModels.ServerConfig();
            }
            return input.request.server;
        }

        private static String value(List<String> arguments, int index, String option) {
            if (index >= arguments.size()) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return arguments.get(index);
        }

        private static int parseInt(String value, String label) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("invalid " + label + ": " + value);
            }
        }
    }
}
