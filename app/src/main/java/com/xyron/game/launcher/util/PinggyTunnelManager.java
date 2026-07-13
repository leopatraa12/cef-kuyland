package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PinggyTunnelManager {
    private static final Object PROCESS_LOCK = new Object();
    private static final String PREFS_NAME = "xyron_pinggy_tunnel";
    private static final String PREF_STATUS = "status";
    private static final String PREF_PID = "pid";
    private static final String PREF_STARTED_AT = "startedAtMs";
    private static final String PREF_PUBLIC_URL = "publicUrl";
    private static final String PREF_NOTE = "note";
    private static final String PREF_BINARY_VERSION = "binaryVersion";
    private static final String PREF_SERVER_ENDPOINT = "serverEndpoint";
    private static final String STATUS_STOPPED = "stopped";
    private static final String STATUS_STARTING = "starting";
    private static final String STATUS_ONLINE = "online";
    private static final String STATUS_ERROR = "error";
    private static final String ASSET_BINARY_PATH = "tunnel/xyron-pinggy-udp-arm64";
    private static final String BINARY_FILE_NAME = "xyron-pinggy-udp-arm64";
    private static final String RUNTIME_DIR_NAME = "RemoteTunnel";
    private static final String LOG_FILE_NAME = "remote-tunnel.log";
    private static final String PINGGY_HOST = "a.pinggy.io";
    private static final String FALLBACK_PINGGY_IPV4 = "172.233.17.91";
    private static final int PINGGY_PORT = 443;
    private static final int BINARY_VERSION = 2;
    private static final long START_TIMEOUT_MS = 18000L;

    private static Process tunnelProcess;
    private static long tunnelStartedAtMs;

    private PinggyTunnelManager() {
    }

    public static LaunchStatus startTunnel(Context context) {
        if (context == null) {
            return LaunchStatus.failure("Contexto do launcher indisponivel.");
        }

        Context appContext = context.getApplicationContext();
        if (!isInternalTunnelSupported()) {
            return LaunchStatus.failure("Este aparelho nao anunciou suporte ARM64 para o motor interno do tunel.");
        }

        TunnelState existingState = getState(appContext);
        if (isTunnelRunning(appContext)) {
            if (!TextUtils.isEmpty(existingState.publicUrl)) {
                return LaunchStatus.success("Tunel remoto ja esta ligado: " + existingState.publicUrl, existingState.publicUrl);
            }
            return LaunchStatus.success("Tunel remoto ja esta iniciando. Aguarde o endereco publico aparecer.", "");
        }

        if (!LocalHostManager.prepareSharedWorkspace(appContext)) {
            return LaunchStatus.failure("Nao foi possivel preparar a pasta do host antes do tunel.");
        }

        File binary;
        try {
            binary = ensureTunnelBinary(appContext);
        } catch (IOException e) {
            return LaunchStatus.failure("Nao consegui preparar o motor interno do tunel: " + e.getMessage());
        }

        String serverEndpoint = resolvePinggyEndpoint(appContext);
        if (TextUtils.isEmpty(serverEndpoint)) {
            return LaunchStatus.failure("Nao consegui resolver o servidor do Pinggy nesta rede.");
        }

        File runtimeDir = getRuntimeDir(appContext);
        if (!runtimeDir.exists() && !runtimeDir.mkdirs()) {
            return LaunchStatus.failure("Nao foi possivel criar a pasta interna do tunel.");
        }

        File logFile = getLogFile(appContext);
        appendLogLine(logFile, timestamp() + " Iniciando tunel interno para "
                + LocalHostManager.getLoopbackAddress() + " via " + serverEndpoint);

        ArrayList<String> command = new ArrayList<>();
        command.add(binary.getAbsolutePath());
        command.add("--host");
        command.add(LocalHostManager.LOCAL_HOST);
        command.add("--port");
        command.add(Integer.toString(LocalHostManager.LOCAL_PORT));
        command.add("--server");
        command.add(serverEndpoint);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(runtimeDir);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("HOME", runtimeDir.getAbsolutePath());

            Process process = processBuilder.start();
            long pid = getProcessPid(process);
            synchronized (PROCESS_LOCK) {
                tunnelProcess = process;
                tunnelStartedAtMs = System.currentTimeMillis();
            }
            writeState(appContext, STATUS_STARTING, pid, tunnelStartedAtMs, "",
                    "Tunel remoto iniciando pelo APK...");
            startLogPump(appContext, process, logFile);

            TunnelStartup startup = waitForStartup(appContext, process);
            if (!TextUtils.isEmpty(startup.publicUrl)) {
                return LaunchStatus.success("Tunel remoto ligado: " + startup.publicUrl, startup.publicUrl);
            }
            if (!TextUtils.isEmpty(startup.error)) {
                return LaunchStatus.failure(startup.error);
            }
            if (process.isAlive()) {
                return LaunchStatus.success("Tunel remoto iniciado. Aguarde o endereco publico aparecer.", "");
            }
            return LaunchStatus.failure("O motor interno do tunel fechou antes de gerar o endereco publico.");
        } catch (IOException e) {
            writeState(appContext, STATUS_ERROR, 0L, 0L, "", "Falha ao abrir tunel: " + e.getMessage());
            appendLogLine(logFile, timestamp() + " Falha ao iniciar processo: " + e.getMessage());
            return LaunchStatus.failure("Falha ao abrir o tunel interno: " + e.getMessage());
        }
    }

    public static boolean stopTunnel(Context context) {
        if (context == null) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        Process processToStop;
        synchronized (PROCESS_LOCK) {
            processToStop = tunnelProcess;
            tunnelProcess = null;
            tunnelStartedAtMs = 0L;
        }

        boolean stopped = false;
        if (processToStop != null) {
            processToStop.destroy();
            try {
                Thread.sleep(350L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (processToStop.isAlive()) {
                processToStop.destroyForcibly();
            }
            stopped = true;
        }

        TunnelState state = getState(appContext);
        if (state.pid > 0L) {
            stopped = killPid(state.pid) || stopped;
        }
        writeState(appContext, STATUS_STOPPED, 0L, 0L, "", "Tunel remoto desligado.");
        return stopped;
    }

    public static boolean isInternalTunnelSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        String[] abis = Build.SUPPORTED_64_BIT_ABIS;
        if (abis == null) {
            return false;
        }
        for (String abi : abis) {
            if ("arm64-v8a".equalsIgnoreCase(abi)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTunnelRunning(Context context) {
        synchronized (PROCESS_LOCK) {
            if (tunnelProcess != null && tunnelProcess.isAlive()) {
                return true;
            }
        }
        TunnelState state = getState(context);
        return (state.isOnline() || state.isStarting()) && state.hasLivePid();
    }

    public static boolean isTunnelStarting(Context context) {
        TunnelState state = getState(context);
        return state.isStarting() && state.hasLivePid();
    }

    public static boolean isTunnelErrored(Context context) {
        TunnelState state = getState(context);
        return state.isError();
    }

    public static String getPublicUrl(Context context) {
        return getState(context).publicUrl;
    }

    public static String getStatusMessage(Context context) {
        return getState(context).note;
    }

    public static File getLogFile(Context context) {
        File logsDir = LocalHostManager.getSharedLogsDirectory();
        if (logsDir == null && context != null) {
            logsDir = new File(context.getFilesDir(), "logs");
        }
        if (logsDir == null) {
            logsDir = new File(".", "logs");
        }
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return new File(logsDir, LOG_FILE_NAME);
    }

    public static TunnelState getState(Context context) {
        if (context == null) {
            return new TunnelState(STATUS_STOPPED, 0L, 0L, "", "");
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String status = preferences.getString(PREF_STATUS, STATUS_STOPPED);
        long pid = preferences.getLong(PREF_PID, 0L);
        long startedAtMs = preferences.getLong(PREF_STARTED_AT, 0L);
        String publicUrl = preferences.getString(PREF_PUBLIC_URL, "");
        String note = preferences.getString(PREF_NOTE, "");

        TunnelState state = new TunnelState(status, pid, startedAtMs, publicUrl, note);
        if ((state.isOnline() || state.isStarting()) && pid > 0L && !isPidAlive(pid)) {
            writeState(context, STATUS_STOPPED, 0L, 0L, "", "Tunel remoto desligado.");
            return new TunnelState(STATUS_STOPPED, 0L, 0L, "", "Tunel remoto desligado.");
        }
        return state;
    }

    private static File ensureTunnelBinary(Context context) throws IOException {
        File runtimeDir = getRuntimeDir(context);
        if (!runtimeDir.exists() && !runtimeDir.mkdirs()) {
            throw new IOException("pasta interna indisponivel");
        }

        File binary = new File(runtimeDir, BINARY_FILE_NAME);
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentVersion = preferences.getInt(PREF_BINARY_VERSION, 0);
        if (!binary.exists() || binary.length() <= 0L || currentVersion != BINARY_VERSION) {
            copyAsset(context, ASSET_BINARY_PATH, binary);
            preferences.edit().putInt(PREF_BINARY_VERSION, BINARY_VERSION).apply();
        }

        binary.setReadable(true, false);
        binary.setExecutable(true, false);
        return binary;
    }

    private static void copyAsset(Context context, String assetPath, File outFile) throws IOException {
        ensureParent(outFile);
        try (InputStream inputStream = context.getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(outFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private static String resolvePinggyEndpoint(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            InetAddress[] addresses = InetAddress.getAllByName(PINGGY_HOST);
            InetAddress selected = null;
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address) {
                    selected = address;
                    break;
                }
            }
            if (selected == null && addresses != null && addresses.length > 0) {
                selected = addresses[0];
            }
            if (selected != null && !TextUtils.isEmpty(selected.getHostAddress())) {
                String endpoint = selected.getHostAddress() + ":" + PINGGY_PORT;
                preferences.edit().putString(PREF_SERVER_ENDPOINT, endpoint).apply();
                return endpoint;
            }
        } catch (Exception ignored) {
        }

        String cachedEndpoint = preferences.getString(PREF_SERVER_ENDPOINT, "");
        if (!TextUtils.isEmpty(cachedEndpoint)) {
            return cachedEndpoint;
        }
        return FALLBACK_PINGGY_IPV4 + ":" + PINGGY_PORT;
    }

    private static File getRuntimeDir(Context context) {
        return new File(context.getFilesDir(), RUNTIME_DIR_NAME);
    }

    private static TunnelStartup waitForStartup(Context context, Process process) {
        long startedAt = System.currentTimeMillis();
        while (System.currentTimeMillis() - startedAt < START_TIMEOUT_MS) {
            TunnelState state = getState(context);
            if (!TextUtils.isEmpty(state.publicUrl)) {
                return TunnelStartup.success(state.publicUrl);
            }
            if (state.isError()) {
                return TunnelStartup.error(state.note);
            }
            if (!process.isAlive()) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                state = getState(context);
                if (state.isError()) {
                    return TunnelStartup.error(state.note);
                }
                return TunnelStartup.error("O motor interno do tunel foi encerrado.");
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return TunnelStartup.pending();
    }

    private static void startLogPump(Context context, Process process, File logFile) {
        Thread thread = new Thread(() -> {
            Context appContext = context.getApplicationContext();
            long pid = getProcessPid(process);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLogLine(logFile, timestamp() + " " + line);
                    parseTunnelLine(appContext, pid, line);
                }
            } catch (IOException e) {
                appendLogLine(logFile, timestamp() + " Falha ao ler tunel: " + e.getMessage());
            } finally {
                boolean wasCurrentProcess = false;
                synchronized (PROCESS_LOCK) {
                    if (tunnelProcess == process) {
                        tunnelProcess = null;
                        tunnelStartedAtMs = 0L;
                        wasCurrentProcess = true;
                    }
                }
                if (wasCurrentProcess) {
                    TunnelState state = getState(appContext);
                    if (!state.isError()) {
                        writeState(appContext, STATUS_STOPPED, 0L, 0L, "",
                                "Tunel remoto desligado.");
                    }
                }
            }
        }, "xyron-pinggy-tunnel");
        thread.setDaemon(true);
        thread.start();
    }

    private static void parseTunnelLine(Context context, long pid, String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.startsWith("XYRON_TUNNEL_URL ")) {
            String rawPublicUrl = line.substring("XYRON_TUNNEL_URL ".length()).trim();
            String publicAddress = resolveNumericPublicAddress(rawPublicUrl);
            writeState(context, STATUS_ONLINE, pid, getStartedAtMs(), publicAddress,
                    "Tunel remoto online: " + publicAddress);
        } else if (line.startsWith("XYRON_TUNNEL_ERROR ")) {
            String message = line.substring("XYRON_TUNNEL_ERROR ".length()).trim();
            writeState(context, STATUS_ERROR, 0L, 0L, "", "Falha no tunel remoto: " + message);
        } else if ("XYRON_TUNNEL_READY".equals(line)) {
            TunnelState state = getState(context);
            if (TextUtils.isEmpty(state.publicUrl)) {
                writeState(context, STATUS_STARTING, pid, getStartedAtMs(), "",
                        "Tunel conectado, aguardando endereco publico...");
            }
        }
    }

    private static String resolveNumericPublicAddress(String rawPublicUrl) {
        String value = rawPublicUrl == null ? "" : rawPublicUrl.trim();
        if (TextUtils.isEmpty(value)) {
            return "";
        }

        String host = "";
        int port = -1;
        try {
            URI uri = new URI(value);
            host = uri.getHost();
            port = uri.getPort();
        } catch (Exception ignored) {
        }

        if (TextUtils.isEmpty(host)) {
            String withoutScheme = value;
            int schemeIndex = withoutScheme.indexOf("://");
            if (schemeIndex >= 0) {
                withoutScheme = withoutScheme.substring(schemeIndex + 3);
            }
            int lastColon = withoutScheme.lastIndexOf(':');
            if (lastColon > 0 && lastColon < withoutScheme.length() - 1) {
                host = withoutScheme.substring(0, lastColon);
                try {
                    port = Integer.parseInt(withoutScheme.substring(lastColon + 1));
                } catch (Exception ignored) {
                    port = -1;
                }
            }
        }

        if (TextUtils.isEmpty(host) || port <= 0) {
            return value.replace("udp://", "");
        }

        String numericHost = host;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address && !TextUtils.isEmpty(address.getHostAddress())) {
                    numericHost = address.getHostAddress();
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return numericHost + ":" + port;
    }

    private static long getStartedAtMs() {
        synchronized (PROCESS_LOCK) {
            return tunnelStartedAtMs > 0L ? tunnelStartedAtMs : System.currentTimeMillis();
        }
    }

    private static void writeState(Context context, String status, long pid, long startedAtMs,
                                   String publicUrl, String note) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_STATUS, TextUtils.isEmpty(status) ? STATUS_STOPPED : status)
                .putLong(PREF_PID, Math.max(0L, pid))
                .putLong(PREF_STARTED_AT, Math.max(0L, startedAtMs))
                .putString(PREF_PUBLIC_URL, publicUrl == null ? "" : publicUrl)
                .putString(PREF_NOTE, note == null ? "" : note)
                .apply();
    }

    private static void appendLogLine(File logFile, String line) {
        ensureParent(logFile);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(logFile, true), StandardCharsets.UTF_8)) {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (Exception ignored) {
        }
    }

    private static void ensureParent(File file) {
        File parent = file == null ? null : file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private static boolean isPidAlive(long pid) {
        if (pid <= 0L) {
            return false;
        }
        try {
            Os.kill((int) pid, 0);
            return true;
        } catch (ErrnoException e) {
            return e.errno != android.system.OsConstants.ESRCH;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean killPid(long pid) {
        if (pid <= 0L) {
            return false;
        }
        try {
            Os.kill((int) pid, android.system.OsConstants.SIGTERM);
            try {
                Thread.sleep(350L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (isPidAlive(pid)) {
                Os.kill((int) pid, android.system.OsConstants.SIGKILL);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long getProcessPid(Process process) {
        if (process == null) {
            return 0L;
        }
        try {
            Object value = Process.class.getMethod("pid").invoke(process);
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Integer) {
                return ((Integer) value).longValue();
            }
        } catch (Exception ignored) {
        }
        String description = process.toString();
        int marker = description == null ? -1 : description.indexOf("pid=");
        if (marker >= 0) {
            int start = marker + 4;
            int end = start;
            while (end < description.length() && Character.isDigit(description.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    return Long.parseLong(description.substring(start, end));
                } catch (Exception ignored) {
                }
            }
        }
        return 0L;
    }

    private static final class TunnelStartup {
        final String publicUrl;
        final String error;

        private TunnelStartup(String publicUrl, String error) {
            this.publicUrl = publicUrl == null ? "" : publicUrl;
            this.error = error == null ? "" : error;
        }

        static TunnelStartup success(String publicUrl) {
            return new TunnelStartup(publicUrl, "");
        }

        static TunnelStartup error(String error) {
            return new TunnelStartup("", error);
        }

        static TunnelStartup pending() {
            return new TunnelStartup("", "");
        }
    }

    public static final class TunnelState {
        public final String status;
        public final long pid;
        public final long startedAtMs;
        public final String publicUrl;
        public final String note;

        TunnelState(String status, long pid, long startedAtMs, String publicUrl, String note) {
            this.status = TextUtils.isEmpty(status) ? STATUS_STOPPED : status;
            this.pid = Math.max(0L, pid);
            this.startedAtMs = Math.max(0L, startedAtMs);
            this.publicUrl = publicUrl == null ? "" : publicUrl;
            this.note = note == null ? "" : note;
        }

        public boolean isOnline() {
            return STATUS_ONLINE.equals(status);
        }

        public boolean isStarting() {
            return STATUS_STARTING.equals(status);
        }

        public boolean isError() {
            return STATUS_ERROR.equals(status);
        }

        boolean hasLivePid() {
            return pid > 0L && isPidAlive(pid);
        }
    }

    public static final class LaunchStatus {
        public final boolean success;
        public final String message;
        public final String publicUrl;

        private LaunchStatus(boolean success, String message, String publicUrl) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.publicUrl = publicUrl == null ? "" : publicUrl;
        }

        public static LaunchStatus success(String message, String publicUrl) {
            return new LaunchStatus(true, message, publicUrl);
        }

        public static LaunchStatus failure(String message) {
            return new LaunchStatus(false, message, "");
        }
    }
}
