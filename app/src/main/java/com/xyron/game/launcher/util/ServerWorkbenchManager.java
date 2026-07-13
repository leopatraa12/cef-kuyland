package com.xyron.game.launcher.util;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ServerWorkbenchManager {
    private static final String BACKUPS_DIR_NAME = "backups";
    private static final int LOG_SNIPPET_LINES = 18;

    private ServerWorkbenchManager() {
    }

    public static boolean prepare(Context context) {
        return LocalHostManager.prepareSharedWorkspace(context) && PawnCompilerManager.prepareWorkspace(context);
    }

    public static ServerSettings loadSettings(Context context) {
        prepare(context);

        ServerSettings settings = new ServerSettings();
        settings.hostname = "News RP Local Host";
        settings.website = "localhost";
        settings.language = "pt-BR";
        settings.maxPlayers = 32;
        settings.mainScript = "blank";
        settings.password = "";
        settings.mapName = "";
        settings.announce = false;
        settings.query = true;
        settings.lanMode = false;

        applyServerCfg(settings, LocalHostManager.getSharedServerCfgFile());
        applyOpenMpConfig(settings, LocalHostManager.getSharedOmpConfigFile());
        return settings;
    }

    public static ActionResult saveSettings(Context context, ServerSettings settings) {
        if (!prepare(context)) {
            return ActionResult.error("Nao foi possivel preparar a base do host.");
        }
        if (settings == null) {
            return ActionResult.error("Configuracao invalida.");
        }

        File serverCfg = LocalHostManager.getSharedServerCfgFile();
        File openMpConfig = LocalHostManager.getSharedOmpConfigFile();
        if (serverCfg == null || openMpConfig == null) {
            return ActionResult.error("Arquivos principais do host nao foram encontrados.");
        }

        String normalizedMainScript = normalizeScriptName(settings.mainScript, "blank");
        settings.mainScript = normalizedMainScript;
        settings.hostname = fallback(settings.hostname, "News RP Local Host");
        settings.website = fallback(settings.website, "localhost");
        settings.language = fallback(settings.language, "pt-BR");
        settings.maxPlayers = clampMaxPlayers(settings.maxPlayers);
        settings.password = settings.password == null ? "" : settings.password.trim();
        settings.mapName = settings.mapName == null ? "" : settings.mapName.trim();

        List<String> lines = readLines(serverCfg);
        if (lines.isEmpty()) {
            lines = new ArrayList<>(Arrays.asList(
                    "echo Executing Server Config...",
                    "port 7777",
                    "filterscripts",
                    "onfoot_rate 40",
                    "incar_rate 40",
                    "weapon_rate 40",
                    "stream_distance 200.0",
                    "stream_rate 1000"
            ));
        }

        upsertLine(lines, "lanmode", settings.lanMode ? "1" : "0");
        upsertLine(lines, "maxplayers", String.valueOf(settings.maxPlayers));
        upsertLine(lines, "port", String.valueOf(LocalHostManager.LOCAL_PORT));
        upsertLine(lines, "hostname", settings.hostname);
        upsertLine(lines, "gamemode0", settings.mainScript + " 1");
        upsertLine(lines, "announce", settings.announce ? "1" : "0");
        upsertLine(lines, "query", settings.query ? "1" : "0");
        upsertLine(lines, "weburl", settings.website);
        upsertLine(lines, "language", settings.language);
        upsertLine(lines, "rcon_password", "changeme-local");
        if (TextUtils.isEmpty(settings.mapName)) {
            removeLines(lines, "mapname");
        } else {
            upsertLine(lines, "mapname", settings.mapName);
        }
        if (TextUtils.isEmpty(settings.password)) {
            removeLines(lines, "password");
        } else {
            upsertLine(lines, "password", settings.password);
        }

        if (!writeLines(serverCfg, lines)) {
            return ActionResult.error("Nao foi possivel salvar o server.cfg.");
        }

        try {
            JSONObject root = readJson(openMpConfig);
            root.put("announce", settings.announce);
            root.put("enable_query", settings.query);
            root.put("language", settings.language);
            root.put("max_players", settings.maxPlayers);
            root.put("name", settings.hostname);
            root.put("password", settings.password);
            root.put("website", settings.website);

            JSONObject network = root.optJSONObject("network");
            if (network == null) {
                network = new JSONObject();
                root.put("network", network);
            }
            network.put("bind", "");
            network.put("port", LocalHostManager.LOCAL_PORT);
            network.put("use_lan_mode", settings.lanMode);

            JSONObject pawn = root.optJSONObject("pawn");
            if (pawn == null) {
                pawn = new JSONObject();
                root.put("pawn", pawn);
            }
            if (pawn.optJSONArray("legacy_plugins") == null) {
                pawn.put("legacy_plugins", new JSONArray());
            }
            if (pawn.optJSONArray("side_scripts") == null) {
                pawn.put("side_scripts", new JSONArray());
            }
            JSONArray mainScripts = new JSONArray();
            mainScripts.put(settings.mainScript);
            pawn.put("main_scripts", mainScripts);

            JSONObject rcon = root.optJSONObject("rcon");
            if (rcon == null) {
                rcon = new JSONObject();
                root.put("rcon", rcon);
            }
            rcon.put("enable", false);
            rcon.put("password", "changeme-local");

            if (!writeText(openMpConfig, root.toString(2) + "\n")) {
                return ActionResult.error("Nao foi possivel salvar o config.json.");
            }
        } catch (Exception e) {
            return ActionResult.error("Falha ao montar o config.json.");
        }

        return ActionResult.success("Configuracoes salvas no server.cfg e no config.json.");
    }

    public static ActionResult createResource(Context context, ResourceType type, String requestedName) {
        if (!prepare(context)) {
            return ActionResult.error("Nao foi possivel preparar a base do host.");
        }
        if (type == null) {
            return ActionResult.error("Tipo de recurso invalido.");
        }

        String baseName = sanitizeBaseName(requestedName);
        if (TextUtils.isEmpty(baseName)) {
            return ActionResult.error("Digite um nome valido.");
        }

        String relativeDir = type.relativeDirectory;
        String fileName = baseName + type.extension;
        HostFileManager.FileActionResult createResult = HostFileManager.createEmptyFile(relativeDir, fileName);
        if (!createResult.success) {
            return ActionResult.error(createResult.message);
        }

        String relativePath = createResult.relativePath;
        if (!HostFileManager.writeEditableText(relativePath, buildTemplate(type, baseName))) {
            return ActionResult.error("O arquivo foi criado, mas nao foi possivel escrever o template.");
        }

        return ActionResult.success(type.successLabel + ": /" + relativePath);
    }

    public static BackupSummary getBackupSummary() {
        File backupDir = getBackupsDirectory();
        if (backupDir == null || !backupDir.exists() || !backupDir.isDirectory()) {
            return new BackupSummary("Nenhum backup criado ainda.", "");
        }

        File[] files = backupDir.listFiles();
        if (files == null || files.length == 0) {
            return new BackupSummary("Nenhum backup criado ainda.", "");
        }

        List<File> zipFiles = new ArrayList<>();
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".zip")) {
                zipFiles.add(file);
            }
        }
        if (zipFiles.isEmpty()) {
            return new BackupSummary("Nenhum backup criado ainda.", "");
        }

        Collections.sort(zipFiles, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });

        File latest = zipFiles.get(0);
        String summary = zipFiles.size() + " backup(s) salvo(s)\n"
                + "Ultimo: " + latest.getName() + "\n"
                + "Tamanho: " + HostFileManager.formatSize(latest.length());
        return new BackupSummary(summary, latest.getAbsolutePath());
    }

    public static ActionResult createBackup(Context context, String requestedLabel) {
        if (!prepare(context)) {
            return ActionResult.error("Nao foi possivel preparar a base do host.");
        }

        File rootDir = LocalHostManager.getSharedRootDirectory();
        if (rootDir == null || !rootDir.exists()) {
            return ActionResult.error("A pasta compartilhada do host ainda nao existe.");
        }

        File backupDir = getBackupsDirectory();
        if (backupDir == null || (!backupDir.exists() && !backupDir.mkdirs())) {
            return ActionResult.error("Nao foi possivel criar a pasta de backups.");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String suffix = sanitizeBaseName(requestedLabel);
        String fileName = TextUtils.isEmpty(suffix)
                ? "xyron-host-backup-" + timestamp + ".zip"
                : "xyron-host-backup-" + timestamp + "-" + suffix + ".zip";
        File outFile = new File(backupDir, fileName);

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outFile, false))) {
            zipDirectory(rootDir, rootDir, zip, backupDir);
            zip.flush();
            return ActionResult.success("Backup criado em " + outFile.getName());
        } catch (Exception e) {
            return ActionResult.error("Nao foi possivel gerar o backup .zip.");
        }
    }

    public static LogSummary getLatestLogSummary() {
        File logsDir = LocalHostManager.getSharedLogsDirectory();
        if (logsDir == null || !logsDir.exists() || !logsDir.isDirectory()) {
            return new LogSummary("Nenhum log foi gerado ainda.", "");
        }

        File[] files = logsDir.listFiles();
        if (files == null || files.length == 0) {
            return new LogSummary("Nenhum log foi gerado ainda.", "");
        }

        File latest = null;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            if (latest == null || file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }

        if (latest == null) {
            return new LogSummary("Nenhum log foi gerado ainda.", "");
        }

        List<String> lines = readLines(latest);
        if (lines.isEmpty()) {
            return new LogSummary("Ultimo log: " + latest.getName() + "\nSem linhas para mostrar ainda.", latest.getAbsolutePath());
        }

        int start = Math.max(0, lines.size() - LOG_SNIPPET_LINES);
        StringBuilder snippet = new StringBuilder();
        snippet.append("Ultimo log: ").append(latest.getName()).append("\n\n");
        for (int i = start; i < lines.size(); i++) {
            snippet.append(lines.get(i)).append("\n");
        }
        return new LogSummary(snippet.toString().trim(), latest.getAbsolutePath());
    }

    private static void applyServerCfg(ServerSettings settings, File file) {
        List<String> lines = readLines(file);
        for (String line : lines) {
            if (TextUtils.isEmpty(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            String key = space >= 0 ? trimmed.substring(0, space).trim().toLowerCase(Locale.US) : trimmed.toLowerCase(Locale.US);
            String value = space >= 0 ? trimmed.substring(space + 1).trim() : "";

            switch (key) {
                case "hostname":
                    settings.hostname = value;
                    break;
                case "weburl":
                    settings.website = value;
                    break;
                case "language":
                    settings.language = value;
                    break;
                case "maxplayers":
                    settings.maxPlayers = parseInt(value, settings.maxPlayers);
                    break;
                case "gamemode0":
                    String[] parts = value.split("\\s+");
                    if (parts.length > 0 && !TextUtils.isEmpty(parts[0])) {
                        settings.mainScript = parts[0];
                    }
                    break;
                case "password":
                    settings.password = value;
                    break;
                case "mapname":
                    settings.mapName = value;
                    break;
                case "announce":
                    settings.announce = parseBoolean(value, settings.announce);
                    break;
                case "query":
                    settings.query = parseBoolean(value, settings.query);
                    break;
                case "lanmode":
                    settings.lanMode = parseBoolean(value, settings.lanMode);
                    break;
                default:
                    break;
            }
        }
    }

    private static void applyOpenMpConfig(ServerSettings settings, File file) {
        try {
            JSONObject root = readJson(file);
            settings.hostname = valueOrFallback(root.optString("name"), settings.hostname);
            settings.website = valueOrFallback(root.optString("website"), settings.website);
            settings.language = valueOrFallback(root.optString("language"), settings.language);
            settings.password = valueOrFallback(root.optString("password"), settings.password);
            settings.maxPlayers = root.optInt("max_players", settings.maxPlayers);
            settings.announce = root.optBoolean("announce", settings.announce);
            settings.query = root.optBoolean("enable_query", settings.query);

            JSONObject network = root.optJSONObject("network");
            if (network != null) {
                settings.lanMode = network.optBoolean("use_lan_mode", settings.lanMode);
            }

            JSONObject pawn = root.optJSONObject("pawn");
            if (pawn != null) {
                JSONArray mainScripts = pawn.optJSONArray("main_scripts");
                if (mainScripts != null && mainScripts.length() > 0) {
                    String mainScript = mainScripts.optString(0, "");
                    if (!TextUtils.isEmpty(mainScript)) {
                        settings.mainScript = mainScript;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONObject readJson(File file) throws Exception {
        String content = readText(file);
        if (TextUtils.isEmpty(content)) {
            return new JSONObject();
        }
        return new JSONObject(content);
    }

    private static String readText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    content.append('\n');
                }
                content.append(line);
                first = false;
            }
        } catch (Exception ignored) {
        }
        return content.toString();
    }

    private static List<String> readLines(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new ArrayList<>();
        }
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    private static boolean writeLines(File file, List<String> lines) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            content.append(lines.get(i));
            if (i < lines.size() - 1) {
                content.append('\n');
            }
        }
        content.append('\n');
        return writeText(file, content.toString());
    }

    private static boolean writeText(File file, String content) {
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, false),
                StandardCharsets.UTF_8
        )) {
            writer.write(content == null ? "" : content);
            writer.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void upsertLine(List<String> lines, String key, String value) {
        String normalizedKey = key.toLowerCase(Locale.US);
        for (int i = 0; i < lines.size(); i++) {
            String current = lines.get(i);
            if (current == null) {
                continue;
            }
            String trimmed = current.trim().toLowerCase(Locale.US);
            if (trimmed.startsWith(normalizedKey + " ") || trimmed.equals(normalizedKey)) {
                lines.set(i, key + " " + value);
                return;
            }
        }
        lines.add(key + " " + value);
    }

    private static void removeLines(List<String> lines, String key) {
        String normalizedKey = key.toLowerCase(Locale.US);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String current = lines.get(i);
            if (current == null) {
                continue;
            }
            String trimmed = current.trim().toLowerCase(Locale.US);
            if (trimmed.startsWith(normalizedKey + " ") || trimmed.equals(normalizedKey)) {
                lines.remove(i);
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private static int clampMaxPlayers(int maxPlayers) {
        if (maxPlayers < 1) {
            return 1;
        }
        return Math.min(maxPlayers, 1000);
    }

    private static String buildTemplate(ResourceType type, String baseName) {
        switch (type) {
            case GAMEMODE:
                return "#include <open.mp>\n\n"
                        + "main() {}\n\n"
                        + "public OnGameModeInit()\n"
                        + "{\n"
                        + "    SetGameModeText(\"" + friendlyLabel(baseName) + "\");\n"
                        + "    AddPlayerClass(0, 1958.3783, 1343.1572, 15.3746, 270.0, 0, 0, 0, 0, 0, 0);\n"
                        + "    return 1;\n"
                        + "}\n\n"
                        + "public OnPlayerConnect(playerid)\n"
                        + "{\n"
                        + "    SendClientMessage(playerid, -1, \"Bem-vindo ao " + friendlyLabel(baseName) + "!\");\n"
                        + "    return 1;\n"
                        + "}\n";
            case INCLUDE:
                String guard = baseName.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]+", "_") + "_INCLUDED";
                return "#if defined _" + guard + "\n"
                        + "    #endinput\n"
                        + "#endif\n"
                        + "#define _" + guard + "\n\n"
                        + "stock " + baseName + "_Init()\n"
                        + "{\n"
                        + "    return 1;\n"
                        + "}\n";
            case SCRIPTFILE:
                return "# Arquivo auxiliar do host\n"
                        + "nome=" + baseName + "\n"
                        + "valor=1\n";
            case CONFIG:
            default:
                return "# Configuracao criada pelo launcher\n"
                        + "# Ajuste os valores do seu servidor aqui\n"
                        + "nome=" + friendlyLabel(baseName) + "\n";
        }
    }

    private static String sanitizeBaseName(String requestedName) {
        if (requestedName == null) {
            return "";
        }
        String sanitized = requestedName.trim()
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('\"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_')
                .replace(' ', '_');
        while (sanitized.contains("__")) {
            sanitized = sanitized.replace("__", "_");
        }
        if (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        int dot = sanitized.lastIndexOf('.');
        if (dot > 0) {
            sanitized = sanitized.substring(0, dot);
        }
        return sanitized.trim();
    }

    private static String normalizeScriptName(String value, String fallback) {
        String script = sanitizeBaseName(value);
        return TextUtils.isEmpty(script) ? fallback : script;
    }

    private static String fallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private static String valueOrFallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static String friendlyLabel(String baseName) {
        if (TextUtils.isEmpty(baseName)) {
            return "News RP";
        }
        String label = baseName.replace('_', ' ').trim();
        if (TextUtils.isEmpty(label)) {
            return "News RP";
        }
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private static void zipDirectory(File rootDir, File current, ZipOutputStream zip, File skipDir) throws Exception {
        if (current == null || rootDir == null || zip == null) {
            return;
        }
        if (skipDir != null && current.equals(skipDir)) {
            return;
        }

        File[] children = current.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child == null) {
                continue;
            }
            if (skipDir != null && HostFileManager.isUnderDirectory(child, skipDir)) {
                continue;
            }
            if (child.isDirectory()) {
                zipDirectory(rootDir, child, zip, skipDir);
                continue;
            }

            String relativePath = child.getAbsolutePath()
                    .substring(rootDir.getAbsolutePath().length())
                    .replace(File.separatorChar, '/');
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            ZipEntry entry = new ZipEntry(relativePath);
            zip.putNextEntry(entry);
            try (FileInputStream inputStream = new FileInputStream(child)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
        }
    }

    private static File getBackupsDirectory() {
        File rootDir = LocalHostManager.getSharedRootDirectory();
        return rootDir == null ? null : new File(rootDir, BACKUPS_DIR_NAME);
    }

    public enum ResourceType {
        GAMEMODE("server/gamemodes", ".pwn", "Gamemode criada"),
        INCLUDE("editor/include", ".inc", "Include criada"),
        SCRIPTFILE("server/scriptfiles", ".txt", "Scriptfile criada"),
        CONFIG("server", ".cfg", "Config criada");

        public final String relativeDirectory;
        public final String extension;
        public final String successLabel;

        ResourceType(String relativeDirectory, String extension, String successLabel) {
            this.relativeDirectory = relativeDirectory;
            this.extension = extension;
            this.successLabel = successLabel;
        }
    }

    public static final class ServerSettings {
        public String hostname;
        public String website;
        public String language;
        public String password;
        public String mainScript;
        public String mapName;
        public int maxPlayers;
        public boolean announce;
        public boolean query;
        public boolean lanMode;
    }

    public static final class ActionResult {
        public final boolean success;
        public final String message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult error(String message) {
            return new ActionResult(false, message);
        }
    }

    public static final class BackupSummary {
        public final String summary;
        public final String latestPath;

        private BackupSummary(String summary, String latestPath) {
            this.summary = summary == null ? "" : summary;
            this.latestPath = latestPath == null ? "" : latestPath;
        }
    }

    public static final class LogSummary {
        public final String snippet;
        public final String filePath;

        private LogSummary(String snippet, String filePath) {
            this.snippet = snippet == null ? "" : snippet;
            this.filePath = filePath == null ? "" : filePath;
        }
    }
}
