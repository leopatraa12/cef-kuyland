package com.xyron.game.launcher.util;

import android.content.Context;
import android.os.Environment;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public final class LocalHostManager {
    public static final String LOCAL_HOST = "127.0.0.1";
    public static final int LOCAL_PORT = 7777;

    private static final String ROOT_DIR_NAME = "LocalHost";
    private static final String SHARED_ROOT_DIR_NAME = "XyronHost";
    private static final String SERVER_DIR_NAME = "server";
    private static final String BIN_DIR_NAME = "bin";
    private static final String COMPONENTS_DIR_NAME = "components";
    private static final String SCRIPTFILES_DIR_NAME = "scriptfiles";
    private static final String GAMEMODES_DIR_NAME = "gamemodes";
    private static final String PLUGINS_DIR_NAME = "plugins";
    private static final String LOGS_DIR_NAME = "logs";
    private static final String EDITOR_DIR_NAME = "editor";
    private static final String EDITOR_PROJECTS_DIR_NAME = "projects";
    private static final String EDITOR_INCLUDE_DIR_NAME = "include";
    private static final String EDITOR_README_FILE_NAME = "EDITOR-README.txt";
    private static final String README_FILE_NAME = "README.txt";
    private static final String TERMUX_README_FILE_NAME = "TERMUX-SETUP.txt";
    private static final String REMOTE_ACCESS_FILE_NAME = "REMOTE-ACCESS.txt";
    private static final String PINGGY_SCRIPT_FILE_NAME = "start-pinggy-udp.sh";
    private static final String SERVER_CFG_FILE_NAME = "server.cfg";
    private static final String OMP_CONFIG_FILE_NAME = "config.json";
    private static final String START_SCRIPT_FILE_NAME = "start-host.sh";
    private static final String PLACEHOLDER_FILE_NAME = "blank.README.txt";
    private static File resolvedSharedRootDir;

    private LocalHostManager() {
    }

    public static HostState getState(Context context) {
        File rootDir = getRootDir(context);
        File serverDir = getServerDir(context);
        File sharedRootDir = getSharedRootDir(context);
        boolean localPrepared = rootDir != null
                && serverDir != null
                && new File(rootDir, README_FILE_NAME).exists()
                && new File(serverDir, SERVER_CFG_FILE_NAME).exists();
        boolean sharedPrepared = sharedRootDir != null
                && new File(sharedRootDir, README_FILE_NAME).exists()
                && new File(sharedRootDir, START_SCRIPT_FILE_NAME).exists()
                && new File(new File(sharedRootDir, SERVER_DIR_NAME), SERVER_CFG_FILE_NAME).exists();
        boolean prepared = localPrepared && sharedPrepared;

        boolean loopbackSaved = false;
        boolean loopbackSelected = false;

        if (context != null) {
            List<ServerConfigManager.ServerOption> options = ServerConfigManager.getAvailableServers(context);
            for (ServerConfigManager.ServerOption option : options) {
                if (option.matches(LOCAL_HOST, LOCAL_PORT)) {
                    loopbackSaved = true;
                    break;
                }
            }

            ServerConfigManager.ServerOption selected = ServerConfigManager.getSelectedServer(context);
            loopbackSelected = selected != null && selected.matches(LOCAL_HOST, LOCAL_PORT);
        }

        return new HostState(
                prepared,
                false,
                loopbackSaved,
                loopbackSelected,
                rootDir == null ? "" : rootDir.getAbsolutePath(),
                getLoopbackAddress()
        );
    }

    public static boolean prepareWorkspace(Context context) {
        File rootDir = getRootDir(context);
        File serverDir = getServerDir(context);
        if (rootDir == null || serverDir == null) {
            return false;
        }

        File binDir = new File(rootDir, BIN_DIR_NAME);
        File componentsDir = new File(serverDir, COMPONENTS_DIR_NAME);
        File scriptfilesDir = new File(serverDir, SCRIPTFILES_DIR_NAME);
        File gamemodesDir = new File(serverDir, GAMEMODES_DIR_NAME);
        File pluginsDir = new File(serverDir, PLUGINS_DIR_NAME);
        File logsDir = new File(serverDir, LOGS_DIR_NAME);

        if (!ensureDir(rootDir)
                || !ensureDir(serverDir)
                || !ensureDir(binDir)
                || !ensureDir(componentsDir)
                || !ensureDir(scriptfilesDir)
                || !ensureDir(gamemodesDir)
                || !ensureDir(pluginsDir)
                || !ensureDir(logsDir)) {
            return false;
        }

        boolean readmeSaved = writeTextIfMissing(new File(rootDir, README_FILE_NAME), buildReadme(rootDir));
        boolean cfgSaved = writeTextIfMissing(new File(serverDir, SERVER_CFG_FILE_NAME), buildServerCfg());
        boolean componentsSaved = writeTextIfMissing(new File(componentsDir, PLACEHOLDER_FILE_NAME), buildComponentsPlaceholder());
        boolean gmSaved = writeTextIfMissing(new File(gamemodesDir, PLACEHOLDER_FILE_NAME), buildGamemodePlaceholder());
        boolean pluginsSaved = writeTextIfMissing(new File(pluginsDir, PLACEHOLDER_FILE_NAME), buildPluginPlaceholder());
        migrateGeneratedServerCfg(new File(serverDir, SERVER_CFG_FILE_NAME));

        return readmeSaved && cfgSaved && componentsSaved && gmSaved && pluginsSaved;
    }

    public static ServerConfigManager.ServerOption addLoopbackServer(Context context) {
        return ServerConfigManager.addOrUpdateServer(context, "Servidor local", LOCAL_HOST, LOCAL_PORT, false);
    }

    public static boolean selectLoopbackServer(Context context) {
        ServerConfigManager.ServerOption option = addLoopbackServer(context);
        return option.isValid() && ServerConfigManager.saveSelectedServer(context, option);
    }

    public static boolean prepareSharedWorkspace(Context context) {
        File rootDir = getSharedRootDir(context);
        if (rootDir == null) {
            return false;
        }

        File serverDir = new File(rootDir, SERVER_DIR_NAME);
        File binDir = new File(rootDir, BIN_DIR_NAME);
        File logsDir = new File(rootDir, LOGS_DIR_NAME);
        File editorDir = new File(rootDir, EDITOR_DIR_NAME);
        File editorProjectsDir = new File(editorDir, EDITOR_PROJECTS_DIR_NAME);
        File editorIncludeDir = new File(editorDir, EDITOR_INCLUDE_DIR_NAME);
        File componentsDir = new File(serverDir, COMPONENTS_DIR_NAME);
        File scriptfilesDir = new File(serverDir, SCRIPTFILES_DIR_NAME);
        File gamemodesDir = new File(serverDir, GAMEMODES_DIR_NAME);
        File pluginsDir = new File(serverDir, PLUGINS_DIR_NAME);

        if (!ensureDir(rootDir)
                || !ensureDir(serverDir)
                || !ensureDir(binDir)
                || !ensureDir(logsDir)
                || !ensureDir(editorDir)
                || !ensureDir(editorProjectsDir)
                || !ensureDir(editorIncludeDir)
                || !ensureDir(componentsDir)
                || !ensureDir(scriptfilesDir)
                || !ensureDir(gamemodesDir)
                || !ensureDir(pluginsDir)) {
            return false;
        }

        boolean localPrepared = prepareWorkspace(context);
        boolean readmeSaved = writeTextIfMissing(new File(rootDir, README_FILE_NAME), buildSharedReadme(rootDir));
        boolean termuxSaved = writeTextIfMissing(new File(rootDir, TERMUX_README_FILE_NAME), buildTermuxSetup(rootDir));
        boolean remoteSaved = writeText(new File(rootDir, REMOTE_ACCESS_FILE_NAME), buildRemoteAccessGuide(rootDir));
        boolean pinggyScriptSaved = writeText(new File(rootDir, PINGGY_SCRIPT_FILE_NAME), buildPinggyUdpScript());
        boolean editorReadmeSaved = writeTextIfMissing(new File(editorDir, EDITOR_README_FILE_NAME), buildEditorReadme(rootDir));
        boolean cfgSaved = writeTextIfMissing(new File(serverDir, SERVER_CFG_FILE_NAME), buildServerCfg());
        boolean ompSaved = writeTextIfMissing(new File(serverDir, OMP_CONFIG_FILE_NAME), buildOmpConfig());
        boolean startSaved = writeTextIfMissing(new File(rootDir, START_SCRIPT_FILE_NAME), buildTermuxStartScript());
        boolean componentsSaved = writeTextIfMissing(new File(componentsDir, PLACEHOLDER_FILE_NAME), buildComponentsPlaceholder());
        boolean gmSaved = writeTextIfMissing(new File(gamemodesDir, PLACEHOLDER_FILE_NAME), buildGamemodePlaceholder());
        boolean pluginSaved = writeTextIfMissing(new File(pluginsDir, PLACEHOLDER_FILE_NAME), buildPluginPlaceholder());
        boolean binSaved = writeTextIfMissing(new File(binDir, PLACEHOLDER_FILE_NAME), buildBinaryPlaceholder());
        migrateGeneratedServerCfg(new File(serverDir, SERVER_CFG_FILE_NAME));
        migrateGeneratedOmpConfig(new File(serverDir, OMP_CONFIG_FILE_NAME));

        return localPrepared && readmeSaved && termuxSaved && remoteSaved && pinggyScriptSaved && editorReadmeSaved && cfgSaved && ompSaved
                && startSaved && componentsSaved && gmSaved && pluginSaved && binSaved;
    }

    public static File getSharedRootDirectory() {
        return getSharedRootDir();
    }

    public static File getSharedServerDirectory() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? null : new File(rootDir, SERVER_DIR_NAME);
    }

    public static File getSharedBinDirectory() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? null : new File(rootDir, BIN_DIR_NAME);
    }

    public static File getSharedLogsDirectory() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? null : new File(rootDir, LOGS_DIR_NAME);
    }

    public static File getSharedEditorRootDirectory() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? null : new File(rootDir, EDITOR_DIR_NAME);
    }

    public static File getSharedEditorProjectsDirectory() {
        File editorRoot = getSharedEditorRootDirectory();
        return editorRoot == null ? null : new File(editorRoot, EDITOR_PROJECTS_DIR_NAME);
    }

    public static File getSharedEditorIncludeDirectory() {
        File editorRoot = getSharedEditorRootDirectory();
        return editorRoot == null ? null : new File(editorRoot, EDITOR_INCLUDE_DIR_NAME);
    }

    public static File getSharedRemoteAccessFile() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? null : new File(rootDir, REMOTE_ACCESS_FILE_NAME);
    }

    public static File getSharedComponentsDirectory() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, COMPONENTS_DIR_NAME);
    }

    public static File getSharedScriptfilesDirectory() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, SCRIPTFILES_DIR_NAME);
    }

    public static File getSharedGamemodesDirectory() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, GAMEMODES_DIR_NAME);
    }

    public static File getSharedPluginsDirectory() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, PLUGINS_DIR_NAME);
    }

    public static File getSharedServerCfgFile() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, SERVER_CFG_FILE_NAME);
    }

    public static File getSharedOmpConfigFile() {
        File serverDir = getSharedServerDirectory();
        return serverDir == null ? null : new File(serverDir, OMP_CONFIG_FILE_NAME);
    }

    public static String getSharedWorkspacePath() {
        File rootDir = getSharedRootDir();
        return rootDir == null ? "" : rootDir.getAbsolutePath();
    }

    public static String getSharedTermuxScriptPath() {
        String base = "$HOME/storage/downloads/" + SHARED_ROOT_DIR_NAME;
        return base + "/" + START_SCRIPT_FILE_NAME;
    }

    public static String getSharedPinggyScriptPath() {
        String base = "$HOME/storage/downloads/" + SHARED_ROOT_DIR_NAME;
        return base + "/" + PINGGY_SCRIPT_FILE_NAME;
    }

    public static String getLoopbackAddress() {
        return LOCAL_HOST + ":" + LOCAL_PORT;
    }

    public static boolean removePlaceholderFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }

        boolean removedAny = false;
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }

        for (File child : children) {
            if (child != null && child.isFile() && isPlaceholderFile(child) && child.delete()) {
                removedAny = true;
            }
        }
        return removedAny;
    }

    public static String buildServerFilesSnapshot() {
        File rootDir = getSharedRootDir();
        File serverDir = getSharedServerDirectory();
        if (rootDir == null || serverDir == null) {
            return "A pasta compartilhada do host ainda nao esta disponivel.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Pasta editavel:\n");
        summary.append(serverDir.getAbsolutePath()).append("\n\n");

        appendSingleFileSummary(summary, "server.cfg", getSharedServerCfgFile());
        appendSingleFileSummary(summary, "config.json", getSharedOmpConfigFile());
        summary.append("\n");

        appendDirectorySummary(summary, "gamemodes", getSharedGamemodesDirectory());
        appendDirectorySummary(summary, "scriptfiles", getSharedScriptfilesDirectory());
        appendDirectorySummary(summary, "plugins", getSharedPluginsDirectory());
        appendDirectorySummary(summary, "components extras", getSharedComponentsDirectory());
        appendDirectorySummary(summary, "bin", getSharedBinDirectory());
        return summary.toString().trim();
    }

    public static boolean activateGamemode(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return false;
        }

        String trimmedName = fileName.trim();
        String gameModeName = trimmedName.toLowerCase(Locale.US).endsWith(".amx")
                ? trimmedName.substring(0, trimmedName.length() - 4)
                : trimmedName;
        if (TextUtils.isEmpty(gameModeName)) {
            return false;
        }

        boolean serverCfgSaved = updateServerCfgGamemode(gameModeName);
        boolean configSaved = updateOmpConfigGamemode(gameModeName);
        return serverCfgSaved && configSaved;
    }

    public static String getBestLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "";
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }

                    String hostAddress = address.getHostAddress();
                    if (!TextUtils.isEmpty(hostAddress)) {
                        return hostAddress;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String buildJoinInfo() {
        String lanIp = getBestLanAddress();
        StringBuilder info = new StringBuilder();
        info.append("Servidor local: ").append(getLoopbackAddress()).append("\n");
        if (TextUtils.isEmpty(lanIp)) {
            info.append("Rede local: conecte o celular na Wi-Fi ou hotspot para gerar um IP compartilhavel.\n");
        } else {
            info.append("Mesma Wi-Fi/hotspot: ").append(lanIp).append(":").append(LOCAL_PORT).append("\n");
        }
        info.append("Internet sem login fixo: use Abrir tunel remoto no Host. Em ARM64 o APK abre Pinggy UDP sem Termux.\n");
        info.append("Fallback: Termux/CLI ou roteador quando o motor interno nao estiver disponivel.\n");
        info.append("Internet padrao: abra UDP ").append(LOCAL_PORT).append(" no roteador e use seu IP publico.");
        return info.toString();
    }

    private static File getRootDir(Context context) {
        if (context == null || context.getExternalFilesDir(null) == null) {
            return null;
        }
        return new File(context.getExternalFilesDir(null), ROOT_DIR_NAME);
    }

    private static File getServerDir(Context context) {
        File rootDir = getRootDir(context);
        return rootDir == null ? null : new File(rootDir, SERVER_DIR_NAME);
    }

    private static synchronized File getSharedRootDir(Context context) {
        File publicRootDir = getPublicSharedRootDir();
        if (isWritableSharedRoot(publicRootDir)) {
            resolvedSharedRootDir = publicRootDir;
            return resolvedSharedRootDir;
        }

        File appRootDir = getAppSharedRootDir(context);
        if (appRootDir != null) {
            resolvedSharedRootDir = appRootDir;
            return resolvedSharedRootDir;
        }

        resolvedSharedRootDir = publicRootDir;
        return resolvedSharedRootDir;
    }

    private static synchronized File getSharedRootDir() {
        if (resolvedSharedRootDir != null) {
            return resolvedSharedRootDir;
        }
        return getPublicSharedRootDir();
    }

    private static File getPublicSharedRootDir() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) {
            return null;
        }
        return new File(downloadsDir, SHARED_ROOT_DIR_NAME);
    }

    private static File getAppSharedRootDir(Context context) {
        if (context == null || context.getExternalFilesDir(null) == null) {
            return null;
        }
        return new File(context.getExternalFilesDir(null), SHARED_ROOT_DIR_NAME);
    }

    private static boolean isWritableSharedRoot(File rootDir) {
        if (rootDir == null) {
            return false;
        }
        if (!rootDir.exists()) {
            File parent = rootDir.getParentFile();
            return parent != null && parent.exists() && parent.canWrite();
        }
        if (!rootDir.isDirectory() || !rootDir.canWrite()) {
            return false;
        }

        File probe = new File(rootDir, ".xyron-write-probe");
        try {
            if (probe.exists() && !probe.delete()) {
                return false;
            }
            try (FileOutputStream outputStream = new FileOutputStream(probe, false)) {
                outputStream.write(1);
                outputStream.flush();
            }
            return probe.delete() || !probe.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean ensureDir(File dir) {
        return dir.exists() || dir.mkdirs();
    }

    private static boolean writeTextIfMissing(File file, String content) {
        if (file == null) {
            return false;
        }
        if (file.exists()) {
            return true;
        }
        return writeText(file, content);
    }

    private static boolean writeText(File file, String content) {
        if (file == null || TextUtils.isEmpty(content)) {
            return false;
        }

        File parent = file.getParentFile();
        if (parent != null && !ensureDir(parent)) {
            return false;
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write(content);
            writer.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String buildReadme(File rootDir) {
        String basePath = rootDir == null ? "Android/data/.../files/LocalHost" : rootDir.getAbsolutePath();
        return "Host local experimental do launcher News RP.\n"
                + "\n"
                + "O launcher prepara a base do servidor em:\n"
                + basePath + "\n"
                + "\n"
                + "O loopback usado pelo app e " + getLoopbackAddress() + ".\n"
                + "\n"
                + "A estrutura aceita runtime SA-MP compativel com Android em:\n"
                + basePath + "/" + SERVER_DIR_NAME + "\n"
                + "com components em " + basePath + "/" + SERVER_DIR_NAME + "/" + COMPONENTS_DIR_NAME + ".\n";
    }

    private static String buildServerCfg() {
        return "echo Executing Server Config...\n"
                + "lanmode 0\n"
                + "rcon_password changeme-local\n"
                + "maxplayers 32\n"
                + "port 7777\n"
                + "hostname News RP Local Host\n"
                + "gamemode0 blank 1\n"
                + "filterscripts\n"
                + "announce 0\n"
                + "query 1\n"
                + "weburl localhost\n"
                + "onfoot_rate 40\n"
                + "incar_rate 40\n"
                + "weapon_rate 40\n"
                + "stream_distance 200.0\n"
                + "stream_rate 1000\n";
    }

    private static String buildGamemodePlaceholder() {
        return "Coloque aqui o seu gamemode real quando a integracao do host local tiver motor nativo.\n";
    }

    private static String buildPluginPlaceholder() {
        return "Coloque aqui os plugins/componentes do servidor quando essa fase avancar.\n";
    }

    private static String buildComponentsPlaceholder() {
        return "Coloque aqui os componentes .so do runtime local, por exemplo server/components/Pawn.so.\n";
    }

    private static String buildBinaryPlaceholder() {
        return "Fallback para binarios soltos do servidor.\n"
                + "O layout preferido agora e um bundle completo em server/.\n"
                + "\n"
                + "Bundle aceito:\n"
                + "- server/samp-server\n"
                + "- server/samp03svr\n"
                + "- server/components/*.so\n"
                + "\n"
                + "Fallbacks aceitos em bin/:\n"
                + "- samp03svr-arm\n"
                + "- samp-server (Linux x86, exige box86)\n"
                + "- samp03svr (Linux x86, exige box86)\n";
    }

    private static String buildSharedReadme(File rootDir) {
        String basePath = rootDir == null ? "/sdcard/Download/" + SHARED_ROOT_DIR_NAME : rootDir.getAbsolutePath();
        return "Pacote de host local para Termux.\n"
                + "\n"
                + "Diretorio exportado pelo launcher:\n"
                + basePath + "\n"
                + "\n"
                + "Loopback usado pelo launcher: " + getLoopbackAddress() + "\n"
                + "\n"
                + "Arquivos principais:\n"
                + "- server/server.cfg\n"
                + "- server/config.json\n"
                + "- server/samp-server\n"
                + "- server/components/\n"
                + "- start-host.sh\n"
                + "- bin/\n";
    }

    private static String buildEditorReadme(File rootDir) {
        String basePath = rootDir == null ? "/sdcard/Download/" + SHARED_ROOT_DIR_NAME : rootDir.getAbsolutePath();
        return "Editor Pawn dentro do launcher.\n"
                + "\n"
                + "Pasta de projetos:\n"
                + basePath + "/" + EDITOR_DIR_NAME + "/" + EDITOR_PROJECTS_DIR_NAME + "\n"
                + "\n"
                + "Includes oficiais:\n"
                + basePath + "/" + EDITOR_DIR_NAME + "/" + EDITOR_INCLUDE_DIR_NAME + "\n"
                + "\n"
                + "Se voce compilar um .pwn dentro de server/gamemodes, o launcher pode ativar o .amx gerado no host.\n";
    }

    private static String buildTermuxSetup(File rootDir) {
        String basePath = rootDir == null ? "/sdcard/Download/" + SHARED_ROOT_DIR_NAME : rootDir.getAbsolutePath();
        return "Setup via Termux\n"
                + "\n"
                + "1. Instale o Termux oficial.\n"
                + "2. Em Termux, execute termux-setup-storage.\n"
                + "3. Em ~/.termux/termux.properties, deixe allow-external-apps=true.\n"
                + "4. Nas configuracoes do Android, conceda ao launcher a permissao extra\n"
                + "   \"Run commands in Termux environment\".\n"
                + "5. Coloque o bundle do servidor em:\n"
                + "   " + basePath + "/server\n"
                + "   com components em " + basePath + "/server/components\n"
                + "   ou use fallback em " + basePath + "/bin\n"
                + "6. O launcher pode pedir ao Termux para abrir:\n"
                + "   " + getSharedTermuxScriptPath() + "\n";
    }

    private static String buildOmpConfig() {
        return "{\n"
                + "  \"announce\": false,\n"
                + "  \"enable_query\": true,\n"
                + "  \"language\": \"pt-BR\",\n"
                + "  \"max_players\": 32,\n"
                + "  \"name\": \"News RP Local Host\",\n"
                + "  \"network\": {\n"
                + "    \"bind\": \"\",\n"
                + "    \"port\": 7777,\n"
                + "    \"use_lan_mode\": false\n"
                + "  },\n"
                + "  \"password\": \"\",\n"
                + "  \"pawn\": {\n"
                + "    \"legacy_plugins\": [],\n"
                + "    \"main_scripts\": [],\n"
                + "    \"side_scripts\": []\n"
                + "  },\n"
                + "  \"rcon\": {\n"
                + "    \"enable\": false,\n"
                + "    \"password\": \"changeme-local\"\n"
                + "  },\n"
                + "  \"website\": \"localhost\"\n"
                + "}\n";
    }

    private static String buildTermuxStartScript() {
        return "#!/data/data/com.termux/files/usr/bin/bash\n"
                + "set -u\n"
                + "HOST_ROOT=\"$HOME/storage/downloads/" + SHARED_ROOT_DIR_NAME + "\"\n"
                + "SERVER_DIR=\"$HOST_ROOT/" + SERVER_DIR_NAME + "\"\n"
                + "BIN_DIR=\"$HOST_ROOT/" + BIN_DIR_NAME + "\"\n"
                + "LOG_DIR=\"$HOST_ROOT/" + LOGS_DIR_NAME + "\"\n"
                + "mkdir -p \"$LOG_DIR\"\n"
                + "cd \"$SERVER_DIR\" || exit 1\n"
                + "chmod +x \"$SERVER_DIR\"/samp* \"$SERVER_DIR/" + COMPONENTS_DIR_NAME + "\"/*.so 2>/dev/null || true\n"
                + "chmod +x \"$BIN_DIR\"/* 2>/dev/null || true\n"
                + "echo \"News RP Host: iniciando ambiente local em " + getLoopbackAddress() + "\"\n"
                + "if [ -x \"$SERVER_DIR/samp-server\" ]; then\n"
                + "  exec \"$SERVER_DIR/samp-server\"\n"
                + "fi\n"
                + "if [ -x \"$BIN_DIR/samp-server\" ]; then\n"
                + "  if command -v box86 >/dev/null 2>&1; then\n"
                + "    exec box86 \"$BIN_DIR/samp-server\"\n"
                + "  fi\n"
                + "  echo \"Encontrado samp-server x86, mas box86 nao esta instalado no Termux.\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "if [ -x \"$BIN_DIR/samp03svr-arm\" ]; then\n"
                + "  exec \"$BIN_DIR/samp03svr-arm\"\n"
                + "fi\n"
                + "if [ -x \"$BIN_DIR/samp03svr\" ]; then\n"
                + "  if command -v box86 >/dev/null 2>&1; then\n"
                + "    exec box86 \"$BIN_DIR/samp03svr\"\n"
                + "  fi\n"
                + "  echo \"Encontrado samp03svr x86, mas box86 nao esta instalado no Termux.\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "echo \"Nenhum binario de servidor foi encontrado em $SERVER_DIR ou $BIN_DIR.\"\n"
                + "echo \"Coloque um binario compativel e tente de novo.\"\n"
                + "echo \"Aceitos: server/samp-server, bin/samp03svr-arm, bin/samp-server, bin/samp03svr\"\n"
                + "exit 1\n";
    }

    private static String buildRemoteAccessGuide(File rootDir) {
        String basePath = rootDir == null ? "/sdcard/Download/" + SHARED_ROOT_DIR_NAME : rootDir.getAbsolutePath();
        String lanIp = getBestLanAddress();
        String lanAddress = TextUtils.isEmpty(lanIp)
                ? "(conecte o aparelho na Wi-Fi ou hotspot para gerar um IP local)"
                : lanIp + ":" + LOCAL_PORT;

        return "Como deixar outra pessoa entrar no host local\n"
                + "\n"
                + "1. Mesmo aparelho:\n"
                + "   " + getLoopbackAddress() + "\n"
                + "\n"
                + "2. Mesma Wi-Fi ou hotspot:\n"
                + "   " + lanAddress + "\n"
                + "   Quem estiver na mesma rede pode testar esse IP direto.\n"
                + "\n"
                + "3. Internet sem login fixo:\n"
                + "   No launcher, abra Host > Abrir tunel remoto.\n"
                + "   Em aparelho ARM64, o APK abre Pinggy UDP internamente sem Termux.\n"
                + "   A porta publica muda a cada nova sessao na faixa gratis.\n"
                + "   Fallback via Termux, se precisar:\n"
                + "   pkg update\n"
                + "   pkg install nodejs-lts\n"
                + "   npm install -g pinggy\n"
                + "   pinggy --type udp -l " + LOCAL_PORT + "\n"
                + "   Script gerado pelo launcher:\n"
                + "   " + getSharedPinggyScriptPath() + "\n"
                + "\n"
                + "4. Internet pelo seu roteador:\n"
                + "   Abra/encaminhe UDP " + LOCAL_PORT + " para o IP local do aparelho e compartilhe o IP publico.\n"
                + "\n"
                + "Pasta do host:\n"
                + basePath + "\n";
    }

    private static String buildPinggyUdpScript() {
        return "#!/data/data/com.termux/files/usr/bin/bash\n"
                + "set -u\n"
                + "PORT=\"" + LOCAL_PORT + "\"\n"
                + "if ! command -v node >/dev/null 2>&1; then\n"
                + "  echo \"Node.js nao encontrado. Rode: pkg update && pkg install nodejs-lts\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "if ! command -v npm >/dev/null 2>&1; then\n"
                + "  echo \"npm nao encontrado. Rode: pkg install nodejs-lts\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "if ! command -v pinggy >/dev/null 2>&1; then\n"
                + "  echo \"Pinggy CLI nao encontrado. Instalando via npm...\"\n"
                + "  npm install -g pinggy || exit 1\n"
                + "fi\n"
                + "echo \"Abrindo tunel UDP para localhost:$PORT\"\n"
                + "exec pinggy --type udp -l \"$PORT\"\n";
    }

    private static void migrateGeneratedServerCfg(File serverCfg) {
        if (serverCfg == null || !serverCfg.exists() || !serverCfg.isFile()) {
            return;
        }

        String content = readText(serverCfg);
        if (TextUtils.isEmpty(content)) {
            return;
        }

        if (content.contains("hostname Xyron Local Host")) {
            String migrated = content
                    .replace("hostname Xyron Local Host", "hostname News RP Local Host")
                    .replace("lanmode 1", "lanmode 0")
                    .replace("announce 1", "announce 0");
            if (!migrated.contains("announce 0")) {
                migrated = migrated.trim() + "\nannounce 0\n";
            }
            if (!migrated.equals(content)) {
                writeText(serverCfg, migrated);
            }
        }
    }

    private static void migrateGeneratedOmpConfig(File configFile) {
        if (configFile == null || !configFile.exists() || !configFile.isFile()) {
            return;
        }

        try {
            JSONObject root = parseExistingConfig(configFile);
            String name = root.optString("name");
            if (!"Xyron Local Host".equals(name) && !"News RP Local Host".equals(name)) {
                return;
            }

            root.put("name", "News RP Local Host");
            root.put("announce", false);
            root.put("enable_query", true);

            JSONObject network = root.optJSONObject("network");
            if (network == null) {
                network = new JSONObject();
                root.put("network", network);
            }

            network.put("port", LOCAL_PORT);
            network.put("use_lan_mode", false);
            writeText(configFile, root.toString(2) + "\n");
        } catch (Exception ignored) {
        }
    }

    private static void appendSingleFileSummary(StringBuilder summary, String label, File file) {
        summary.append(label).append(": ");
        if (file == null || !file.exists() || !file.isFile()) {
            summary.append("ausente\n");
            return;
        }
        summary.append(formatSize(file.length())).append("  |  ");
        summary.append(file.getName()).append("\n");
    }

    private static void appendDirectorySummary(StringBuilder summary, String label, File dir) {
        List<File> files = listVisibleFiles(dir);
        summary.append(label).append(" (").append(files.size()).append(")\n");
        if (files.isEmpty()) {
            summary.append("- vazio\n\n");
            return;
        }

        int visibleCount = Math.min(files.size(), 6);
        for (int i = 0; i < visibleCount; i++) {
            File file = files.get(i);
            summary.append("- ").append(file.getName());
            if (file.isFile()) {
                summary.append("  |  ").append(formatSize(file.length()));
            }
            summary.append("\n");
        }
        if (files.size() > visibleCount) {
            summary.append("- +").append(files.size() - visibleCount).append(" arquivo(s)\n");
        }
        summary.append("\n");
    }

    private static List<File> listVisibleFiles(File dir) {
        ArrayList<File> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return result;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }

        for (File child : children) {
            if (child == null || isPlaceholderFile(child)) {
                continue;
            }
            result.add(child);
        }
        Collections.sort(result, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean isPlaceholderFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        return README_FILE_NAME.equalsIgnoreCase(name)
                || PLACEHOLDER_FILE_NAME.equalsIgnoreCase(name)
                || TERMUX_README_FILE_NAME.equalsIgnoreCase(name);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private static boolean updateServerCfgGamemode(String gameModeName) {
        File serverCfg = getSharedServerCfgFile();
        if (serverCfg == null) {
            return false;
        }

        ArrayList<String> lines = readLines(serverCfg);
        if (lines.isEmpty()) {
            lines.add("echo Executing Server Config...");
        }

        boolean replaced = false;
        boolean announceSet = false;
        boolean lanModeSet = false;
        boolean querySet = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String normalized = line == null ? "" : line.trim().toLowerCase(Locale.US);
            if (normalized.startsWith("gamemode0 ")) {
                lines.set(i, "gamemode0 " + gameModeName + " 1");
                replaced = true;
            } else if (normalized.startsWith("announce ")) {
                lines.set(i, "announce 0");
                announceSet = true;
            } else if (normalized.startsWith("lanmode ")) {
                lines.set(i, "lanmode 0");
                lanModeSet = true;
            } else if (normalized.startsWith("query ")) {
                lines.set(i, "query 1");
                querySet = true;
            }
        }
        if (!replaced) {
            lines.add("gamemode0 " + gameModeName + " 1");
        }
        if (!announceSet) {
            lines.add("announce 0");
        }
        if (!lanModeSet) {
            lines.add("lanmode 0");
        }
        if (!querySet) {
            lines.add("query 1");
        }

        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line == null ? "" : line).append("\n");
        }
        return writeText(serverCfg, content.toString());
    }

    private static boolean updateOmpConfigGamemode(String gameModeName) {
        File configFile = getSharedOmpConfigFile();
        if (configFile == null) {
            return false;
        }

        try {
            JSONObject root = parseExistingConfig(configFile);
            root.put("announce", false);
            root.put("enable_query", true);
            root.put("max_players", 32);
            root.put("name", "News RP Local Host");

            JSONObject network = root.optJSONObject("network");
            if (network == null) {
                network = new JSONObject();
                root.put("network", network);
            }
            network.put("bind", "");
            network.put("port", LOCAL_PORT);
            network.put("use_lan_mode", false);

            JSONObject pawn = root.optJSONObject("pawn");
            if (pawn == null) {
                pawn = new JSONObject();
                root.put("pawn", pawn);
            }

            JSONArray mainScripts = new JSONArray();
            mainScripts.put(gameModeName);
            pawn.put("main_scripts", mainScripts);

            if (!pawn.has("side_scripts")) {
                pawn.put("side_scripts", new JSONArray());
            }
            if (!pawn.has("legacy_plugins")) {
                pawn.put("legacy_plugins", new JSONArray());
            }

            return writeText(configFile, root.toString(2) + "\n");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static JSONObject parseExistingConfig(File configFile) {
        String current = readText(configFile);
        if (TextUtils.isEmpty(current)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(current);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static ArrayList<String> readLines(File file) {
        ArrayList<String> lines = new ArrayList<>();
        if (file == null || !file.exists() || !file.isFile()) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception ignored) {
            lines.clear();
        }
        return lines;
    }

    private static String readText(File file) {
        StringBuilder content = new StringBuilder();
        List<String> lines = readLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                content.append('\n');
            }
            content.append(lines.get(i));
        }
        return content.toString();
    }

    public static final class HostState {
        public final boolean workspacePrepared;
        public final boolean embeddedEngineAvailable;
        public final boolean loopbackSaved;
        public final boolean loopbackSelected;
        public final String workspacePath;
        public final String loopbackAddress;

        HostState(boolean workspacePrepared,
                  boolean embeddedEngineAvailable,
                  boolean loopbackSaved,
                  boolean loopbackSelected,
                  String workspacePath,
                  String loopbackAddress) {
            this.workspacePrepared = workspacePrepared;
            this.embeddedEngineAvailable = embeddedEngineAvailable;
            this.loopbackSaved = loopbackSaved;
            this.loopbackSelected = loopbackSelected;
            this.workspacePath = workspacePath == null ? "" : workspacePath;
            this.loopbackAddress = loopbackAddress == null ? "" : loopbackAddress;
        }
    }
}
