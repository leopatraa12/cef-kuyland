package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.ini4j.Wini;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ServerConfigManager {
    private static final String TAG = "ServerConfigManager";
    private static final String PREFS_NAME = "xyron_server_manager";
    private static final String KEY_SAVED_SERVERS = "saved_servers";
    private static final String KEY_USER_ADDED = "userAdded";
    private static final int MAX_SAVED_SERVERS = 5;

    private static final String SETTINGS_PATH = "SAMP/settings.ini";
    private static final String CLIENT_SECTION = "client";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";

    private ServerConfigManager() {
    }

    public static List<ServerOption> getAvailableServers(Context context) {
        return loadStoredServers(context);
    }

    public static boolean hasConfiguredServers(Context context) {
        return !getAvailableServers(context).isEmpty();
    }

    public static int getMaxSavedServers() {
        return MAX_SAVED_SERVERS;
    }

    public static boolean isValidRawAddress(String rawAddress) {
        return parseServerAddress(rawAddress) != null;
    }

    public static void ensureSelectedServer(Context context) {
        File file = getSettingsFile(context);
        if (file == null) {
            return;
        }

        List<ServerOption> availableServers = getAvailableServers(context);
        ServerOption selectedServer = readServerFromSettings(context);

        if (availableServers.isEmpty()) {
            if (selectedServer != null && selectedServer.isValid()) {
                clearSelectedServer(context);
            }
            return;
        }

        if (selectedServer != null
                && selectedServer.isValid()
                && findMatchingServer(availableServers, selectedServer.host, selectedServer.port) != null) {
            saveSelectedServer(context, selectedServer.host, selectedServer.port);
            return;
        }

        saveSelectedServer(context, availableServers.get(0));
    }

    public static ServerOption getSelectedServer(Context context) {
        ensureSelectedServer(context);

        ServerOption selectedServer = readServerFromSettings(context);
        if (selectedServer != null && selectedServer.isValid()) {
            ServerOption matched = findMatchingServer(getAvailableServers(context), selectedServer.host, selectedServer.port);
            return matched != null ? matched : selectedServer;
        }

        List<ServerOption> availableServers = getAvailableServers(context);
        if (!availableServers.isEmpty()) {
            return availableServers.get(0);
        }

        return ServerOption.empty();
    }

    public static int getSelectedServerIndex(Context context) {
        ServerOption selectedServer = getSelectedServer(context);
        List<ServerOption> options = getAvailableServers(context);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).matches(selectedServer.host, selectedServer.port)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean saveSelectedServer(Context context, ServerOption option) {
        if (option == null || !option.isValid()) {
            return false;
        }
        upsertServer(context, option);
        return saveSelectedServer(context, option.host, option.port);
    }

    public static boolean saveSelectedServer(Context context, String host, int port) {
        if (context == null || TextUtils.isEmpty(sanitize(host)) || !isValidPort(port)) {
            return false;
        }

        File file = getSettingsFile(context);
        if (file == null) {
            return false;
        }

        try {
            prepareSettingsFile(file);
            Wini wini = new Wini(file);
            writeServer(wini, host, port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to persist selected server at " + file.getAbsolutePath(), e);
            return false;
        }
    }

    public static ServerOption addOrUpdateServer(Context context, String name, String host, int port, boolean favorite) {
        String sanitizedHost = sanitize(host);
        String sanitizedName = sanitizeDisplayName(name, sanitizedHost, port);
        if (context == null || TextUtils.isEmpty(sanitizedHost) || !isValidPort(port)) {
            return ServerOption.empty();
        }

        ArrayList<ServerOption> servers = loadStoredServers(context);
        boolean updated = false;

        for (int i = 0; i < servers.size(); i++) {
            ServerOption current = servers.get(i);
            if (current.matches(sanitizedHost, port)) {
                servers.set(i, new ServerOption(sanitizedName, sanitizedHost, port, favorite));
                updated = true;
                break;
            }
        }

        if (!updated && servers.size() >= MAX_SAVED_SERVERS) {
            return ServerOption.empty();
        }

        if (!updated) {
            servers.add(new ServerOption(sanitizedName, sanitizedHost, port, favorite));
        }

        persistStoredServers(context, servers);
        return findMatchingServer(servers, sanitizedHost, port);
    }

    public static ServerOption addOrUpdateServer(Context context, String rawAddress) {
        ParsedServerAddress parsed = parseServerAddress(rawAddress);
        if (parsed == null) {
            return ServerOption.empty();
        }
        return addOrUpdateServer(context, "", parsed.host, parsed.port, false);
    }

    public static boolean setFavorite(Context context, ServerOption option, boolean favorite) {
        if (context == null || option == null || !option.isValid()) {
            return false;
        }

        ArrayList<ServerOption> servers = loadStoredServers(context);
        boolean changed = false;

        for (int i = 0; i < servers.size(); i++) {
            ServerOption current = servers.get(i);
            if (current.matches(option.host, option.port)) {
                servers.set(i, new ServerOption(current.name, current.host, current.port, favorite));
                changed = true;
                break;
            }
        }

        if (!changed) {
            servers.add(new ServerOption(option.name, option.host, option.port, favorite));
            changed = true;
        }

        persistStoredServers(context, servers);
        return true;
    }

    public static boolean removeServer(Context context, ServerOption option) {
        if (context == null || option == null || !option.isValid()) {
            return false;
        }

        ArrayList<ServerOption> servers = loadStoredServers(context);
        boolean removed = false;
        for (int i = servers.size() - 1; i >= 0; i--) {
            if (servers.get(i).matches(option.host, option.port)) {
                servers.remove(i);
                removed = true;
            }
        }

        if (!removed) {
            return false;
        }

        persistStoredServers(context, servers);

        ServerOption selectedServer = readServerFromSettings(context);
        if (selectedServer != null && selectedServer.matches(option.host, option.port)) {
            if (!servers.isEmpty()) {
                saveSelectedServer(context, servers.get(0));
            } else {
                clearSelectedServer(context);
            }
        }

        return true;
    }

    private static void upsertServer(Context context, ServerOption option) {
        addOrUpdateServer(context, option.name, option.host, option.port, option.favorite);
    }

    private static ArrayList<ServerOption> loadStoredServers(Context context) {
        ArrayList<ServerOption> servers = new ArrayList<>();
        if (context == null) {
            return servers;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_SAVED_SERVERS, "[]");
        try {
            JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                String name = sanitizeDisplayName(
                        item.optString("name"),
                        item.optString("host"),
                        item.optInt("port")
                );
                String host = sanitize(item.optString("host"));
                int port = item.optInt("port");
                boolean favorite = item.optBoolean("favorite", false);
                boolean userAdded = item.optBoolean(KEY_USER_ADDED, false);

                if (!userAdded) {
                    continue;
                }

                if (!TextUtils.isEmpty(host) && isValidPort(port) && findMatchingServer(servers, host, port) == null) {
                    servers.add(new ServerOption(name, host, port, favorite));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse saved servers", e);
        }

        return servers;
    }

    private static void persistStoredServers(Context context, List<ServerOption> servers) {
        if (context == null) {
            return;
        }

        JSONArray jsonArray = new JSONArray();
        for (ServerOption option : servers) {
            if (option == null || !option.isValid()) {
                continue;
            }

            JSONObject item = new JSONObject();
            try {
                item.put("name", option.name);
                item.put("host", option.host);
                item.put("port", option.port);
                item.put("favorite", option.favorite);
                item.put(KEY_USER_ADDED, true);
                jsonArray.put(item);
            } catch (Exception ignored) {
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAVED_SERVERS, jsonArray.toString())
                .apply();
    }

    private static ServerOption readServerFromSettings(Context context) {
        File file = getSettingsFile(context);
        if (file == null || !file.exists()) {
            return null;
        }

        try {
            Wini wini = new Wini(file);
            String host = sanitize(wini.get(CLIENT_SECTION, HOST_KEY));
            int port = parsePort(wini.get(CLIENT_SECTION, PORT_KEY));
            if (TextUtils.isEmpty(host) || !isValidPort(port)) {
                return null;
            }
            return new ServerOption("Servidor atual", host, port, false);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read selected server", e);
            return null;
        }
    }

    private static void writeServer(Wini wini, String host, int port) throws IOException {
        wini.put(CLIENT_SECTION, HOST_KEY, host);
        wini.put(CLIENT_SECTION, PORT_KEY, port);
        wini.store();
    }

    private static void clearSelectedServer(Context context) {
        File file = getSettingsFile(context);
        if (file == null || !file.exists()) {
            return;
        }
        try {
            Wini wini = new Wini(file);
            wini.put(CLIENT_SECTION, HOST_KEY, "");
            wini.put(CLIENT_SECTION, PORT_KEY, "");
            wini.store();
        } catch (IOException e) {
            Log.e(TAG, "Failed to clear selected server at " + file.getAbsolutePath(), e);
        }
    }

    private static File getSettingsFile(Context context) {
        if (context == null || context.getExternalFilesDir(null) == null) {
            return null;
        }
        return new File(context.getExternalFilesDir(null), SETTINGS_PATH);
    }

    private static void prepareSettingsFile(File file) throws IOException {
        ensureParentDirectory(file);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    private static void ensureParentDirectory(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static int parsePort(Object rawValue) {
        String value = sanitize(rawValue);
        if (TextUtils.isEmpty(value)) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    private static String sanitize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String sanitizeDisplayName(String name, String host, int port) {
        String sanitizedName = sanitize(name);
        if (!sanitizedName.isEmpty()) {
            return sanitizedName;
        }
        if (!sanitize(host).isEmpty() && isValidPort(port)) {
            return host + ":" + port;
        }
        return "Servidor";
    }

    private static ParsedServerAddress parseServerAddress(String rawAddress) {
        String sanitized = sanitize(rawAddress);
        if (sanitized.isEmpty()) {
            return null;
        }

        String host = "";
        String portValue = "";

        if (sanitized.contains(":")) {
            int separator = sanitized.lastIndexOf(':');
            host = sanitize(sanitized.substring(0, separator));
            portValue = sanitize(sanitized.substring(separator + 1));
        } else {
            String[] parts = sanitized.split("\\s+");
            if (parts.length >= 2) {
                host = sanitize(parts[0]);
                portValue = sanitize(parts[1]);
            }
        }

        if (host.isEmpty() || portValue.isEmpty()) {
            return null;
        }

        try {
            int port = Integer.parseInt(portValue);
            if (!isValidPort(port)) {
                return null;
            }
            return new ParsedServerAddress(host, port);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ServerOption findMatchingServer(List<ServerOption> servers, String host, int port) {
        for (ServerOption option : servers) {
            if (option.matches(host, port)) {
                return option;
            }
        }
        return null;
    }

    public static final class ServerOption {
        public final String name;
        public final String host;
        public final int port;
        public final boolean favorite;

        public ServerOption(String name, String host, int port, boolean favorite) {
            this.name = sanitizeDisplayName(name, host, port);
            this.host = sanitize(host);
            this.port = port;
            this.favorite = favorite;
        }

        public static ServerOption empty() {
            return new ServerOption("Nenhum servidor", "", 0, false);
        }

        public String getAddress() {
            return isValid() ? host + ":" + port : "Adicione um servidor";
        }

        public boolean matches(String currentHost, int currentPort) {
            return host.equals(sanitize(currentHost)) && port == currentPort;
        }

        public boolean isValid() {
            return !TextUtils.isEmpty(host) && isValidPort(port);
        }
    }

    private static final class ParsedServerAddress {
        final String host;
        final int port;

        ParsedServerAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
