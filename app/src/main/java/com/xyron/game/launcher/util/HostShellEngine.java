package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class HostShellEngine {
    private static final Object PROCESS_LOCK = new Object();
    private static final String RUNTIME_PREFS_NAME = "xyron_host_runtime";
    private static final String PREF_STATUS = "status";
    private static final String PREF_PID = "pid";
    private static final String PREF_STARTED_AT = "startedAtMs";
    private static final String PREF_NOTE = "note";
    private static final String[] PACKAGED_RUNTIME_CANDIDATES = {
            "libxyron_host.so",
            "libomp_server_arm.so",
            "libsamp_server_arm.so",
            "libsamp03svr_arm.so"
    };
    private static final PackagedComponent[] PACKAGED_COMPONENTS = {
            new PackagedComponent("libomp_component_CAPI.so", "$CAPI.so"),
            new PackagedComponent("libomp_component_Actors.so", "Actors.so"),
            new PackagedComponent("libomp_component_Checkpoints.so", "Checkpoints.so"),
            new PackagedComponent("libomp_component_Classes.so", "Classes.so"),
            new PackagedComponent("libomp_component_Console.so", "Console.so"),
            new PackagedComponent("libomp_component_CustomModels.so", "CustomModels.so"),
            new PackagedComponent("libomp_component_Databases.so", "Databases.so"),
            new PackagedComponent("libomp_component_Dialogs.so", "Dialogs.so"),
            new PackagedComponent("libomp_component_GangZones.so", "GangZones.so"),
            new PackagedComponent("libomp_component_LegacyConfig.so", "LegacyConfig.so"),
            new PackagedComponent("libomp_component_LegacyNetwork.so", "LegacyNetwork.so"),
            new PackagedComponent("libomp_component_Menus.so", "Menus.so"),
            new PackagedComponent("libomp_component_NPCs.so", "NPCs.so"),
            new PackagedComponent("libomp_component_Objects.so", "Objects.so"),
            new PackagedComponent("libomp_component_Pawn.so", "Pawn.so"),
            new PackagedComponent("libomp_component_Pickups.so", "Pickups.so"),
            new PackagedComponent("libomp_component_Recordings.so", "Recordings.so"),
            new PackagedComponent("libomp_component_TextDraws.so", "TextDraws.so"),
            new PackagedComponent("libomp_component_TextLabels.so", "TextLabels.so"),
            new PackagedComponent("libomp_component_Timers.so", "Timers.so"),
            new PackagedComponent("libomp_component_Variables.so", "Variables.so"),
            new PackagedComponent("libomp_component_Vehicles.so", "Vehicles.so")
    };
    private static final String[] RUNTIME_CANDIDATES = {
            "xyron-host-arm",
            "omp-server-arm",
            "samp03svr-arm",
            "omp-server",
            "samp-server",
            "samp03svr"
    };
    private static final String[] OPTIONAL_RUNTIME_DIRS = {
            "scriptfiles",
            "gamemodes",
            "plugins"
    };
    private static final String ASSET_RUNTIME_DIR = "host-runtime";
    private static final String LOG_FILE_NAME = "host-runtime.log";
    private static final String EMBEDDED_RUNTIME_ROOT_NAME = "EmbeddedHostRuntime";
    private static final String EMBEDDED_SERVER_DIR_NAME = "server";
    private static final String COMPONENTS_DIR_NAME = "components";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String SERVER_CFG_FILE_NAME = "server.cfg";
    private static final String STATE_FILE_NAME = "host-runtime.state";
    private static final String STATE_STOPPED = "stopped";
    private static final String STATE_STARTING = "starting";
    private static final String STATE_ONLINE = "online";
    private static final String STATE_ERROR = "error";
    private static final int LOCAL_QUERY_TIMEOUT_MS = 180;
    private static final long START_READY_TIMEOUT_MS = 5000L;
    private static final long PIPE_CLOSE_PROBE_TIMEOUT_MS = 1800L;
    private static final long STARTING_STATE_GRACE_MS = 15000L;
    private static final long ONLINE_STATE_GRACE_MS = 30000L;
    private static final String BOOTSTRAP_GAMEMODE_NAME = "xyron_bootstrap";
    private static final String BOOTSTRAP_GAMEMODE_FILE_NAME = BOOTSTRAP_GAMEMODE_NAME + ".pwn";
    private static final String BOOTSTRAP_GAMEMODE_SOURCE =
            "#pragma rational Float\n"
                    + "\n"
                    + "#define NO_TEAM (255)\n"
                    + "#define PLAYER_MARKERS_MODE_OFF (0)\n"
                    + "#define WEAPON_FIST (0)\n"
                    + "\n"
                    + "native SetGameModeText(const string[]);\n"
                    + "native ShowPlayerMarkers(mode);\n"
                    + "native DisableInteriorEnterExits();\n"
                    + "native UsePlayerPedAnims();\n"
                    + "native print(const string[]);\n"
                    + "native AddPlayerClass(modelid, Float:spawn_x, Float:spawn_y, Float:spawn_z, Float:z_angle, weapon1, weapon1_ammo, weapon2, weapon2_ammo, weapon3, weapon3_ammo);\n"
                    + "native SendClientMessage(playerid, color, const message[]);\n"
                    + "native SetSpawnInfo(playerid, team, skin, Float:spawn_x, Float:spawn_y, Float:spawn_z, Float:z_angle, weapon1, weapon1_ammo, weapon2, weapon2_ammo, weapon3, weapon3_ammo);\n"
                    + "native SetPlayerPos(playerid, Float:x, Float:y, Float:z);\n"
                    + "native SetPlayerFacingAngle(playerid, Float:angle);\n"
                    + "native SetPlayerCameraPos(playerid, Float:x, Float:y, Float:z);\n"
                    + "native SetPlayerCameraLookAt(playerid, Float:x, Float:y, Float:z);\n"
                    + "native SetCameraBehindPlayer(playerid);\n"
                    + "\n"
                    + "#define XYRON_SPAWN_X (1958.3783)\n"
                    + "#define XYRON_SPAWN_Y (1343.1572)\n"
                    + "#define XYRON_SPAWN_Z (15.3746)\n"
                    + "#define XYRON_SPAWN_A (270.0)\n"
                    + "\n"
                    + "main() {}\n"
                    + "\n"
                    + "public OnGameModeInit()\n"
                    + "{\n"
                    + "    SetGameModeText(\"News RP Host\");\n"
                    + "    ShowPlayerMarkers(PLAYER_MARKERS_MODE_OFF);\n"
                    + "    DisableInteriorEnterExits();\n"
                    + "    UsePlayerPedAnims();\n"
                    + "    AddPlayerClass(0, XYRON_SPAWN_X, XYRON_SPAWN_Y, XYRON_SPAWN_Z, XYRON_SPAWN_A, WEAPON_FIST, 0, WEAPON_FIST, 0, WEAPON_FIST, 0);\n"
                    + "    print(\"Bootstrap SA-MP do host local carregado.\");\n"
                    + "    return 1;\n"
                    + "}\n"
                    + "\n"
                    + "public OnPlayerConnect(playerid)\n"
                    + "{\n"
                    + "    SendClientMessage(playerid, -1, \"Bem-vindo ao host SA-MP local do launcher News RP.\");\n"
                    + "    return 1;\n"
                    + "}\n"
                    + "\n"
                    + "public OnPlayerRequestClass(playerid, classid)\n"
                    + "{\n"
                    + "    SetSpawnInfo(playerid, NO_TEAM, 0, XYRON_SPAWN_X, XYRON_SPAWN_Y, XYRON_SPAWN_Z, XYRON_SPAWN_A, WEAPON_FIST, 0, WEAPON_FIST, 0, WEAPON_FIST, 0);\n"
                    + "    SetPlayerPos(playerid, XYRON_SPAWN_X, XYRON_SPAWN_Y, XYRON_SPAWN_Z);\n"
                    + "    SetPlayerFacingAngle(playerid, XYRON_SPAWN_A);\n"
                    + "    SetPlayerCameraPos(playerid, XYRON_SPAWN_X - 3.0, XYRON_SPAWN_Y, XYRON_SPAWN_Z + 1.0);\n"
                    + "    SetPlayerCameraLookAt(playerid, XYRON_SPAWN_X, XYRON_SPAWN_Y, XYRON_SPAWN_Z);\n"
                    + "    return 1;\n"
                    + "}\n"
                    + "\n"
                    + "public OnPlayerRequestSpawn(playerid)\n"
                    + "{\n"
                    + "    return 1;\n"
                    + "}\n"
                    + "\n"
                    + "public OnPlayerSpawn(playerid)\n"
                    + "{\n"
                    + "    SetPlayerPos(playerid, XYRON_SPAWN_X, XYRON_SPAWN_Y, XYRON_SPAWN_Z);\n"
                    + "    SetPlayerFacingAngle(playerid, XYRON_SPAWN_A);\n"
                    + "    SetCameraBehindPlayer(playerid);\n"
                    + "    return 1;\n"
                    + "}\n";

    private static Process hostProcess;
    private static long hostStartedAtMs;

    private HostShellEngine() {
    }

    public static CommandResult execute(Context context, String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (context == null) {
            return CommandResult.error("Contexto do launcher indisponivel.");
        }

        if (command.isEmpty()) {
            return CommandResult.error("Digite um comando. Exemplo: host help");
        }

        String normalized = command.toLowerCase(Locale.US);
        switch (normalized) {
            case "help":
            case "host help":
                return CommandResult.success(buildHelpText(), false, false);
            case "clear":
            case "host clear":
                return CommandResult.success("Console limpo.", true, false);
            case "host boot":
                return bootHost(context);
            case "host install":
                return installHost(context);
            case "host use-local":
                return useLoopback(context);
            case "host status":
                return status(context);
            case "host join":
            case "host network":
                return CommandResult.success(LocalHostManager.buildJoinInfo(), false, false);
            case "host pinggy":
            case "host tunnel":
                return CommandResult.success(buildPinggyHelp(), false, false);
            case "host files":
                return CommandResult.success(LocalHostManager.buildServerFilesSnapshot(), false, false);
            case "host start":
                return startHost(context);
            case "host stop":
                return stopHost(context);
            case "host logs":
                return logs(context);
            default:
                return CommandResult.error(
                        "Comando nao reconhecido: " + command + "\nUse host help para ver os comandos."
                );
        }
    }

    public static boolean isHostRunning() {
        synchronized (PROCESS_LOCK) {
            return hostProcess != null && hostProcess.isAlive();
        }
    }

    public static boolean isHostRunning(Context context) {
        if (isHostRunning()) {
            return true;
        }
        HostRuntimeState state = readRuntimeState(context);
        if (state != null) {
            if (state.isOnline()) {
                if (state.hasLivePid()) {
                    return true;
                }
                if (!isMainThread()) {
                    return isLoopbackResponding();
                }
                return state.startedRecently(ONLINE_STATE_GRACE_MS);
            }
            if (state.isStarting() && (state.hasLivePid() || state.startedRecently(STARTING_STATE_GRACE_MS))) {
                return true;
            }
        }
        if (!isMainThread() && state != null && state.hasLivePid()) {
            return true;
        }
        return !isMainThread() && isLoopbackResponding();
    }

    public static boolean isHostReady(Context context) {
        HostRuntimeState state = readRuntimeState(context);
        if (state != null && state.isOnline()) {
            if (state.hasLivePid()) {
                return true;
            }
            if (!isMainThread()) {
                return isLoopbackResponding();
            }
            return state.startedRecently(ONLINE_STATE_GRACE_MS);
        }
        if (isMainThread()) {
            return false;
        }
        if (isLoopbackResponding()) {
            return true;
        }
        if (state == null) {
            return false;
        }
        if (STATE_ONLINE.equals(state.status)) {
            return state.pid <= 0L || state.hasLivePid();
        }
        return false;
    }

    public static boolean isHostStarting(Context context) {
        HostRuntimeState state = readRuntimeState(context);
        if (state == null || !state.isStarting()) {
            return false;
        }
        return state.hasLivePid() || state.startedRecently(STARTING_STATE_GRACE_MS);
    }

    public static boolean isHostErrored(Context context) {
        HostRuntimeState state = readRuntimeState(context);
        return state != null && STATE_ERROR.equals(state.status) && !isStaleMissingRuntimeError(context, state);
    }

    public static String getHostStatusMessage(Context context) {
        HostRuntimeState state = readRuntimeState(context);
        if (state == null || TextUtils.isEmpty(state.note)) {
            return "";
        }
        if (isStaleMissingRuntimeError(context, state)) {
            return "";
        }
        return state.note;
    }

    private static boolean isStaleMissingRuntimeError(Context context, HostRuntimeState state) {
        if (state == null || !STATE_ERROR.equals(state.status) || TextUtils.isEmpty(state.note)) {
            return false;
        }
        return state.note.toLowerCase(Locale.US).contains("nenhum runtime arm")
                && resolveRuntimeBinary(context) != null;
    }

    public static boolean hasRuntimeCandidate(Context context) {
        return resolveRuntimeBinary(context) != null;
    }

    public static String getRuntimeHint(Context context) {
        File runtime = resolveRuntimeBinary(context);
        if (runtime != null) {
            if (isPackagedRuntime(context, runtime)) {
                return "Runtime empacotado encontrado em " + runtime.getAbsolutePath();
            }
            return "Runtime encontrado em " + runtime.getAbsolutePath();
        }
        File workspaceRoot = new File(LocalHostManager.getSharedWorkspacePath());
        return "Nenhum runtime ARM encontrado no pacote nem em "
                + new File(workspaceRoot, "server").getAbsolutePath()
                + " ou "
                + new File(workspaceRoot, "bin").getAbsolutePath();
    }

    private static CommandResult installHost(Context context) {
        boolean prepared = LocalHostManager.prepareSharedWorkspace(context);
        boolean selected = LocalHostManager.selectLoopbackServer(context);
        int copied = copyBundledRuntimePack(context);

        RuntimeWorkspace embeddedWorkspace = null;
        String embeddedMessage;
        try {
            embeddedWorkspace = preparePackagedRuntimeWorkspace(context);
            if (embeddedWorkspace != null) {
                embeddedMessage = "sim (" + embeddedWorkspace.linkedComponents + " componentes)";
            } else {
                embeddedMessage = "nao";
            }
        } catch (IOException e) {
            embeddedMessage = "falha (" + e.getMessage() + ")";
        }

        StringBuilder output = new StringBuilder();
        output.append("Pacote local preparado: ").append(prepared ? "sim" : "nao").append("\n");
        output.append("Loopback ativo: ").append(selected ? "sim" : "nao").append("\n");
        output.append("Workspace: ").append(LocalHostManager.getSharedWorkspacePath()).append("\n");
        output.append("Runtime embutido copiado: ").append(copied).append(" arquivo(s)\n");
        output.append("Runtime interno pronto: ").append(embeddedMessage).append("\n");
        if (embeddedWorkspace != null) {
            output.append("Runtime interno: ").append(embeddedWorkspace.serverDir.getAbsolutePath()).append("\n");
        }
        output.append("Aceitos no host:\n");
        output.append("- runtime empacotado na APK em nativeLibraryDir\n");
        output.append("- server/samp-server + server/components/*.so\n");
        output.append("- bin/samp03svr-arm\n");
        output.append("- bin/samp-server, bin/samp03svr");

        return CommandResult.success(output.toString(), false, true);
    }

    private static CommandResult useLoopback(Context context) {
        boolean selected = LocalHostManager.selectLoopbackServer(context);
        if (!selected) {
            return CommandResult.error("Nao foi possivel ativar o loopback " + LocalHostManager.getLoopbackAddress());
        }

        return CommandResult.success(
                "Loopback salvo como servidor ativo: " + LocalHostManager.getLoopbackAddress(),
                false,
                true
        );
    }

    private static CommandResult status(Context context) {
        LocalHostManager.HostState state = LocalHostManager.getState(context);
        File runtime = resolveRuntimeBinary(context);
        StringBuilder output = new StringBuilder();
        output.append("Host preparado: ").append(state.workspacePrepared ? "sim" : "nao").append("\n");
        output.append("Servidor local salvo: ").append(state.loopbackSaved ? "sim" : "nao").append("\n");
        output.append("Servidor local ativo: ").append(state.loopbackSelected ? "sim" : "nao").append("\n");
        output.append("Processo em execucao: ").append(isHostRunning() ? "sim" : "nao").append("\n");
        output.append("Loopback: ").append(state.loopbackAddress).append("\n");
        output.append("Workspace: ").append(LocalHostManager.getSharedWorkspacePath()).append("\n");
        output.append("Log: ").append(getLogFile(context).getAbsolutePath()).append("\n");
        output.append("Runtime: ").append(runtime == null ? "nao encontrado" : runtime.getAbsolutePath());

        if (runtime != null && isPackagedRuntime(context, runtime)) {
            File embeddedRuntimeDir = new File(new File(context.getFilesDir(), EMBEDDED_RUNTIME_ROOT_NAME), EMBEDDED_SERVER_DIR_NAME);
            output.append("\nRuntime interno: ").append(embeddedRuntimeDir.getAbsolutePath());
        }

        if (isHostRunning()) {
            long uptimeSeconds = Math.max(1L, (System.currentTimeMillis() - hostStartedAtMs) / 1000L);
            output.append("\nUptime: ").append(uptimeSeconds).append("s");
        }

        return CommandResult.success(output.toString(), false, true);
    }

    private static CommandResult startHost(Context context) {
        if (isHostRunning(context)) {
            return CommandResult.success("O host local ja esta rodando nesta sessao.", false, true);
        }

        boolean prepared = LocalHostManager.prepareSharedWorkspace(context);
        boolean selected = LocalHostManager.selectLoopbackServer(context);
        copyBundledRuntimePack(context);

        if (!prepared || !selected) {
            return CommandResult.error(
                    "Nao foi possivel preparar a base do host antes do start.\n"
                            + "Workspace preparado: " + (prepared ? "sim" : "nao") + "\n"
                            + "Loopback salvo: " + (selected ? "sim" : "nao") + "\n"
                            + "Workspace: " + LocalHostManager.getSharedWorkspacePath()
            );
        }

        BootstrapResult bootstrapResult = ensureBootstrapGamemode(context);
        if (!bootstrapResult.success) {
            return CommandResult.error(bootstrapResult.message);
        }

        File runtime = resolveRuntimeBinary(context);
        if (runtime == null) {
            return CommandResult.error(
                    "Nenhum runtime ARM foi encontrado no pacote desta build.\n"
                            + "Coloque um bundle compativel em "
                            + new File(LocalHostManager.getSharedWorkspacePath(), "server").getAbsolutePath()
                            + "\nou um binario solto em "
                            + new File(LocalHostManager.getSharedWorkspacePath(), "bin").getAbsolutePath()
                            + "\nDepois rode host start de novo."
            );
        }

        File serverDir = new File(LocalHostManager.getSharedWorkspacePath(), "server");
        if (isPackagedRuntime(context, runtime)) {
            try {
                RuntimeWorkspace workspace = preparePackagedRuntimeWorkspace(context);
                if (workspace == null) {
                    return CommandResult.error("O runtime empacotado existe, mas a pasta interna nao foi preparada.");
                }
                serverDir = workspace.serverDir;
            } catch (IOException e) {
                return CommandResult.error("Falha ao preparar o runtime interno.\nErro: " + e.getMessage());
            }
        }

        File logFile = getLogFile(context);
        ensureParent(logFile);
        if (!isPackagedRuntime(context, runtime)) {
            runtime.setExecutable(true, false);
        }

        try {
            HostRuntimeContextHolder.set(context);
            ProcessBuilder processBuilder = new ProcessBuilder(buildRuntimeCommand(runtime));
            processBuilder.directory(serverDir);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            long pid = getProcessPid(process);
            synchronized (PROCESS_LOCK) {
                hostProcess = process;
                hostStartedAtMs = System.currentTimeMillis();
            }
            writeRuntimeState(context, STATE_STARTING, pid, hostStartedAtMs, "Host local iniciando na porta 7777...");
            startLogPump(process, logFile);

            HostStartupCheck startupCheck = waitForStartupReadiness(context, process);

            boolean stillRunning = process.isAlive();
            StringBuilder output = new StringBuilder();
            output.append("Comando de start enviado.\n");
            output.append("Runtime: ").append(runtime.getAbsolutePath()).append("\n");
            output.append("Diretorio: ").append(serverDir.getAbsolutePath()).append("\n");
            output.append("Log: ").append(logFile.getAbsolutePath()).append("\n");
            if (!TextUtils.isEmpty(bootstrapResult.message)) {
                output.append(bootstrapResult.message).append("\n");
            }
            output.append("Processo vivo: ").append(stillRunning ? "sim" : "nao");
            if (startupCheck.online) {
                writeRuntimeState(context, STATE_ONLINE, pid, hostStartedAtMs, "Servidor local online na porta 7777.");
                output.append("\nServidor pronto em ").append(startupCheck.elapsedMs).append(" ms.");
            } else if (!stillRunning) {
                writeRuntimeState(context, STATE_ERROR, 0L, 0L, "O processo do host saiu logo depois do start.");
                output.append("\nO processo saiu rapido. Rode host logs para ver a saida.");
            } else {
                output.append("\nO host ainda esta aquecendo. Aguarde alguns segundos e atualize a tela.");
            }

            return startupCheck.online
                    ? CommandResult.success(output.toString(), false, true)
                    : stillRunning
                    ? CommandResult.success(output.toString(), false, true)
                    : CommandResult.error(output.toString());
        } catch (Exception e) {
            appendLogLine(logFile, timestamp() + " Falha ao iniciar runtime: " + e.getMessage());
            writeRuntimeState(context, STATE_ERROR, 0L, 0L, "Falha ao iniciar o runtime do host.");
            return CommandResult.error(
                    "Falha ao iniciar o runtime.\nVerifique host logs para detalhes.\nErro: " + e.getMessage()
            );
        }
    }

    private static CommandResult stopHost() {
        return stopHost(null);
    }

    private static CommandResult stopHost(Context context) {
        Process processToStop;
        synchronized (PROCESS_LOCK) {
            processToStop = hostProcess;
        }

        long persistedPid = 0L;
        if (context != null) {
            HostRuntimeState state = readRuntimeState(context);
            if (state != null) {
                persistedPid = state.pid;
            }
        }

        boolean stoppedSomething = false;
        if (processToStop != null && processToStop.isAlive()) {
            processToStop.destroy();
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (processToStop.isAlive()) {
                processToStop.destroyForcibly();
            }
            stoppedSomething = true;
        } else if (persistedPid > 0L && isPidAlive(persistedPid)) {
            stoppedSomething = killPid(persistedPid);
        }

        if (!stoppedSomething) {
            synchronized (PROCESS_LOCK) {
                hostProcess = null;
                hostStartedAtMs = 0L;
            }
            if (context != null) {
                writeRuntimeState(context, STATE_STOPPED, 0L, 0L, "Host local desligado.");
            }
            return CommandResult.error("Nenhum processo de host local esta rodando agora.");
        }

        synchronized (PROCESS_LOCK) {
            hostProcess = null;
            hostStartedAtMs = 0L;
        }
        if (context != null) {
            writeRuntimeState(context, STATE_STOPPED, 0L, 0L, "Host local desligado.");
        }

        return CommandResult.success("Host local finalizado.", false, true);
    }

    private static CommandResult logs(Context context) {
        File logFile = getLogFile(context);
        if (!logFile.exists()) {
            return CommandResult.error("Nenhum log do host foi gerado ainda.");
        }

        List<String> lines = readTail(logFile, 24);
        if (lines.isEmpty()) {
            return CommandResult.error("O log do host esta vazio.");
        }

        StringBuilder output = new StringBuilder();
        output.append("Ultimas linhas de ").append(logFile.getAbsolutePath()).append(":\n");
        for (String line : lines) {
            output.append(line).append("\n");
        }
        return CommandResult.success(output.toString().trim(), false, false);
    }

    private static String buildHelpText() {
        return "Host Shell\n"
                + "host boot    -> prepara a base, ativa o loopback e sobe o host\n"
                + "host install  -> prepara a base local, loopback e runtime interno\n"
                + "host use-local -> marca 127.0.0.1:7777 como servidor ativo\n"
                + "host status   -> mostra estado do host e do runtime\n"
                + "host join     -> mostra como entrar via local, Wi-Fi e internet\n"
                + "host pinggy   -> mostra o atalho e o script do tunel UDP\n"
                + "host files    -> mostra os arquivos editaveis do servidor\n"
                + "host start    -> sobe o runtime ARM empacotado ou externo\n"
                + "host stop     -> encerra o processo local\n"
                + "host logs     -> mostra as ultimas linhas do log\n"
                + "host clear    -> limpa o console\n"
                + "\n"
                + "Layouts aceitos:\n"
                + "- runtime SA-MP ARM empacotado na APK\n"
                + "- assets/host-runtime/server/samp-server\n"
                + "- assets/host-runtime/server/components/*.so\n"
                + "- assets/host-runtime/bin/samp03svr-arm\n"
                + "- jniLibs/<abi>/runtime do host SA-MP";
    }

    private static String buildPinggyHelp() {
        return "Tunel UDP via Pinggy\n"
                + "Botao do launcher: Host > Abrir tunel remoto\n"
                + "Motor interno: assets/tunnel/xyron-pinggy-udp-arm64\n"
                + "Log: " + PinggyTunnelManager.getLogFile(HostRuntimeContextHolder.peek()).getAbsolutePath() + "\n"
                + "\n"
                + "Em aparelhos ARM64, o APK:\n"
                + "- liga o host local se necessario\n"
                + "- resolve o servidor Pinggy pelo Android\n"
                + "- abre Pinggy UDP para localhost:" + LocalHostManager.LOCAL_PORT + "\n"
                + "- mostra o endereco publico no painel Host\n"
                + "\n"
                + "Fallback Termux: " + LocalHostManager.getSharedPinggyScriptPath() + "\n"
                + "O script:\n"
                + "- verifica Node.js e npm\n"
                + "- instala pinggy se faltar\n"
                + "- abre: pinggy --type udp -l " + LocalHostManager.LOCAL_PORT + "\n"
                + "\n"
                + "A porta publica muda a cada nova sessao na faixa gratis.";
    }

    public static CommandResult bootHost(Context context) {
        if (context == null) {
            return CommandResult.error("Contexto do launcher indisponivel.");
        }

        CommandResult installResult = installHost(context);
        if (!installResult.success) {
            writeRuntimeState(context, STATE_ERROR, 0L, 0L, firstUsefulLine(installResult.output));
            return installResult;
        }

        CommandResult startResult = startHost(context);
        StringBuilder output = new StringBuilder();
        output.append("Fluxo rapido do host\n\n");
        output.append(installResult.output);
        output.append("\n\n");
        output.append(startResult.output);

        if (!startResult.success) {
            writeRuntimeState(context, STATE_ERROR, 0L, 0L, firstUsefulLine(startResult.output));
            return CommandResult.error(output.toString());
        }

        return CommandResult.success(output.toString(), false, true);
    }

    private static File resolveRuntimeBinary(Context context) {
        File packagedRuntime = resolvePackagedRuntimeBinary(context);
        if (packagedRuntime != null) {
            return packagedRuntime;
        }

        File sharedRootDir = new File(LocalHostManager.getSharedWorkspacePath());
        File[] searchDirs = {
                new File(sharedRootDir, "server"),
                new File(sharedRootDir, "bin"),
                sharedRootDir
        };
        for (File searchDir : searchDirs) {
            for (String candidate : RUNTIME_CANDIDATES) {
                File file = new File(searchDir, candidate);
                if (file.exists() && file.isFile()) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File resolvePackagedRuntimeBinary(Context context) {
        if (context == null || context.getApplicationInfo() == null) {
            return null;
        }

        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        if (TextUtils.isEmpty(nativeLibraryDir)) {
            return null;
        }

        File nativeDir = new File(nativeLibraryDir);
        for (String candidate : PACKAGED_RUNTIME_CANDIDATES) {
            File file = new File(nativeDir, candidate);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }

        return null;
    }

    private static RuntimeWorkspace preparePackagedRuntimeWorkspace(Context context) throws IOException {
        File packagedRuntime = resolvePackagedRuntimeBinary(context);
        if (packagedRuntime == null || context == null || context.getFilesDir() == null) {
            return null;
        }

        File runtimeRoot = new File(context.getFilesDir(), EMBEDDED_RUNTIME_ROOT_NAME);
        File serverDir = new File(runtimeRoot, EMBEDDED_SERVER_DIR_NAME);
        File componentsDir = new File(serverDir, COMPONENTS_DIR_NAME);
        ensureDir(runtimeRoot);
        ensureDir(serverDir);
        ensureDir(componentsDir);

        for (String dirName : OPTIONAL_RUNTIME_DIRS) {
            ensureDir(new File(serverDir, dirName));
        }

        File sharedServerDir = new File(LocalHostManager.getSharedWorkspacePath(), EMBEDDED_SERVER_DIR_NAME);
        copyFileIfPresent(new File(sharedServerDir, CONFIG_FILE_NAME), new File(serverDir, CONFIG_FILE_NAME));
        copyFileIfPresent(new File(sharedServerDir, SERVER_CFG_FILE_NAME), new File(serverDir, SERVER_CFG_FILE_NAME));
        for (String dirName : OPTIONAL_RUNTIME_DIRS) {
            syncOptionalDirectory(new File(sharedServerDir, dirName), new File(serverDir, dirName));
        }

        clearDirectoryContents(componentsDir);

        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        int linkedComponents = 0;
        for (PackagedComponent component : PACKAGED_COMPONENTS) {
            File sourceLib = new File(nativeDir, component.packagedLibraryName);
            if (!sourceLib.exists() || !sourceLib.isFile()) {
                continue;
            }

            File targetLink = new File(componentsDir, component.runtimeFileName);
            if (createSymlinkOrCopy(sourceLib, targetLink)) {
                linkedComponents++;
            }
        }

        syncImportedComponents(new File(sharedServerDir, COMPONENTS_DIR_NAME), componentsDir);

        if (linkedComponents == 0) {
            throw new IOException("Nenhum componente empacotado foi encontrado em " + nativeDir.getAbsolutePath());
        }

        return new RuntimeWorkspace(serverDir, linkedComponents);
    }

    private static boolean isPackagedRuntime(Context context, File runtime) {
        if (context == null || runtime == null || context.getApplicationInfo() == null) {
            return false;
        }
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        if (TextUtils.isEmpty(nativeLibraryDir)) {
            return false;
        }
        File parent = runtime.getParentFile();
        return parent != null && nativeLibraryDir.equals(parent.getAbsolutePath());
    }

    private static List<String> buildRuntimeCommand(File runtime) {
        ArrayList<String> command = new ArrayList<>();
        command.add(runtime.getAbsolutePath());
        String runtimeName = runtime.getName().toLowerCase(Locale.US);
        if (runtimeName.contains("omp")) {
            command.add("--config-path");
            command.add(CONFIG_FILE_NAME);
        }
        return command;
    }

    private static boolean createSymlinkOrCopy(File sourceLib, File targetLink) throws IOException {
        deleteIfExists(targetLink);
        ensureParent(targetLink);
        try {
            Os.symlink(sourceLib.getAbsolutePath(), targetLink.getAbsolutePath());
            return true;
        } catch (ErrnoException e) {
            copyFile(sourceLib, targetLink);
            targetLink.setReadable(true, false);
            targetLink.setExecutable(true, false);
            return true;
        }
    }

    private static void syncOptionalDirectory(File sourceDir, File targetDir) throws IOException {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            ensureDir(targetDir);
            return;
        }

        ensureDir(targetDir);
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            File outFile = new File(targetDir, child.getName());
            if (child.isDirectory()) {
                syncOptionalDirectory(child, outFile);
            } else if (child.isFile()) {
                copyFile(child, outFile);
            }
        }
    }

    private static void syncImportedComponents(File sourceDir, File targetDir) throws IOException {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }

        ensureDir(targetDir);
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child == null || !child.isFile()) {
                continue;
            }

            String name = child.getName();
            if ("README.txt".equalsIgnoreCase(name) || "blank.README.txt".equalsIgnoreCase(name)) {
                continue;
            }

            File outFile = new File(targetDir, name);
            copyFile(child, outFile);
            outFile.setReadable(true, false);
            outFile.setExecutable(true, false);
        }
    }

    private static void clearDirectoryContents(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null) {
            return;
        }
        if (file.isDirectory() && !isSymlink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        deleteIfExists(file);
    }

    private static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            return false;
        }
        File canonical = file.getCanonicalFile();
        File absolute = file.getAbsoluteFile();
        return !canonical.equals(absolute);
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file == null) {
            return;
        }
        if (file.delete()) {
            return;
        }
        if (file.exists()) {
            throw new IOException("Nao foi possivel remover " + file.getAbsolutePath());
        }
    }

    private static String firstUsefulLine(String output) {
        if (TextUtils.isEmpty(output)) {
            return "Falha ao ligar o host local.";
        }
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()
                    || value.equalsIgnoreCase("Fluxo rapido do host")
                    || value.equalsIgnoreCase("Aceitos no host:")) {
                continue;
            }
            if (value.toLowerCase(Locale.US).contains("falha")
                    || value.toLowerCase(Locale.US).contains("erro")
                    || value.toLowerCase(Locale.US).contains("processo saiu")
                    || value.toLowerCase(Locale.US).contains("nao foi possivel")) {
                return value;
            }
        }
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (!value.isEmpty() && !value.equalsIgnoreCase("Fluxo rapido do host")) {
                return value;
            }
        }
        return "Falha ao ligar o host local.";
    }

    private static void copyFileIfPresent(File sourceFile, File targetFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return;
        }
        copyFile(sourceFile, targetFile);
    }

    private static void copyFile(File sourceFile, File targetFile) throws IOException {
        ensureParent(targetFile);
        try (FileInputStream inputStream = new FileInputStream(sourceFile);
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private static void ensureDir(File dir) throws IOException {
        if (dir == null) {
            throw new IOException("Diretorio do runtime invalido.");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Nao foi possivel criar " + dir.getAbsolutePath());
        }
    }

    private static int copyBundledRuntimePack(Context context) {
        if (context == null) {
            return 0;
        }

        File sharedRootDir = new File(LocalHostManager.getSharedWorkspacePath());
        if (!sharedRootDir.exists()) {
            sharedRootDir.mkdirs();
        }

        try {
            return copyAssetTree(context.getAssets(), sharedRootDir, "");
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int copyAssetTree(AssetManager assets, File sharedRootDir, String relativePath) throws IOException {
        String assetPath = TextUtils.isEmpty(relativePath)
                ? ASSET_RUNTIME_DIR
                : ASSET_RUNTIME_DIR + "/" + relativePath;
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            int copied = 0;
            for (String child : children) {
                if (TextUtils.isEmpty(child)) {
                    continue;
                }
                String nextRelativePath = TextUtils.isEmpty(relativePath)
                        ? child
                        : relativePath + "/" + child;
                copied += copyAssetTree(assets, sharedRootDir, nextRelativePath);
            }
            return copied;
        }

        if (TextUtils.isEmpty(relativePath)) {
            return 0;
        }

        File outFile = new File(sharedRootDir, relativePath);
        return copyAssetFile(assets, assetPath, outFile) ? 1 : 0;
    }

    private static boolean copyAssetFile(AssetManager assets, String assetPath, File outFile) {
        ensureParent(outFile);
        try (InputStream inputStream = assets.open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            outFile.setExecutable(true, false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void startLogPump(Process process, File logFile) {
        Thread thread = new Thread(() -> {
            Context context = HostRuntimeContextHolder.peek();
            boolean sawOnlineLine = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLogLine(logFile, timestamp() + " " + sanitizeHostLogLine(line));
                    if (context != null && line.contains("Legacy Network started on port")) {
                        sawOnlineLine = true;
                        writeRuntimeState(context, STATE_ONLINE, getProcessPid(process), hostStartedAtMs,
                                "Servidor local online na porta 7777.");
                    }
                }
            } catch (IOException e) {
                appendLogLine(logFile, timestamp() + " Falha ao ler saida do runtime: " + e.getMessage());
                if (context != null && isHostRunning()) {
                    writeRuntimeState(context, STATE_ERROR, 0L, 0L, "A leitura do runtime foi interrompida.");
                }
            } finally {
                boolean wasCurrentProcess = false;
                synchronized (PROCESS_LOCK) {
                    if (hostProcess == process) {
                        hostProcess = null;
                        wasCurrentProcess = true;
                    }
                    hostStartedAtMs = 0L;
                }
                if (context != null && wasCurrentProcess) {
                    boolean stillOnline = sawOnlineLine && probeLoopbackAfterPipeClose();
                    if (stillOnline) {
                        appendLogLine(logFile, timestamp() + " Runtime principal fechou o pipe, mas o host segue respondendo no loopback.");
                        writeRuntimeState(context, STATE_ONLINE, 0L, System.currentTimeMillis(),
                                "Servidor local online na porta 7777.");
                    } else {
                        appendLogLine(logFile, timestamp() + " Runtime do host finalizado ou sem resposta no loopback.");
                        writeRuntimeState(context, STATE_STOPPED, 0L, 0L, "Host local desligado.");
                    }
                }
            }
        }, "xyron-host-log-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private static String sanitizeHostLogLine(String line) {
        if (line == null) {
            return "";
        }
        if (line.startsWith("Starting open.mp server")) {
            return "Starting SA-MP compatible host runtime";
        }
        if (line.contains("Couldn't announce legacy network to open.mp list")) {
            return "[SA-MP] Anuncio externo ignorado; o host local continua online na porta 7777.";
        }
        return line;
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

    private static List<String> readTail(File file, int maxLines) {
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) {
                    lines.remove(0);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return lines;
    }

    private static File getLogFile(Context context) {
        File logsDir = new File(LocalHostManager.getSharedWorkspacePath(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return new File(logsDir, LOG_FILE_NAME);
    }

    private static File getStateFile(Context context) {
        File logsDir = new File(LocalHostManager.getSharedWorkspacePath(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return new File(logsDir, STATE_FILE_NAME);
    }

    private static BootstrapResult ensureBootstrapGamemode(Context context) {
        File gamemodesDir = LocalHostManager.getSharedGamemodesDirectory();
        if (context == null || gamemodesDir == null) {
            return BootstrapResult.error("A pasta de gamemodes do host nao esta disponivel.");
        }
        if (!gamemodesDir.exists()) {
            gamemodesDir.mkdirs();
        }

        File bootstrapSource = new File(gamemodesDir, BOOTSTRAP_GAMEMODE_FILE_NAME);
        File bootstrapBinary = new File(gamemodesDir, BOOTSTRAP_GAMEMODE_NAME + ".amx");
        boolean shouldRefreshBootstrap = bootstrapBinary.exists()
                && bootstrapSource.exists()
                && isGeneratedBootstrapSource(readTextFile(bootstrapSource))
                && !BOOTSTRAP_GAMEMODE_SOURCE.equals(readTextFile(bootstrapSource));
        if (shouldRefreshBootstrap) {
            bootstrapBinary.delete();
            bootstrapSource.delete();
        }

        File[] children = gamemodesDir.listFiles();
        boolean hasCompiledGamemode = false;
        if (children != null) {
            for (File child : children) {
                if (child != null && child.isFile() && child.getName().toLowerCase(Locale.US).endsWith(".amx")) {
                    hasCompiledGamemode = true;
                    break;
                }
            }
        }

        if (hasCompiledGamemode) {
            return BootstrapResult.success("");
        }

        if (!writeBootstrapSource(bootstrapSource)) {
            return BootstrapResult.error("Nao foi possivel criar o gamemode bootstrap do host.");
        }

        PawnCompilerManager.CompileResult compileResult = PawnCompilerManager.compile(
                context,
                PawnCompilerManager.getServerGamemodesRelativePath() + "/" + BOOTSTRAP_GAMEMODE_FILE_NAME
        );
        if (compileResult == null || !compileResult.success) {
            String compileOutput = compileResult == null ? "" : compileResult.output;
            return BootstrapResult.error(
                    "O host local nao conseguiu gerar o gamemode bootstrap.\n"
                            + (TextUtils.isEmpty(compileOutput) ? "" : compileOutput)
            );
        }

        return BootstrapResult.success("Gamemode bootstrap gerado: " + BOOTSTRAP_GAMEMODE_NAME + ".amx");
    }

    private static boolean isGeneratedBootstrapSource(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        return content.contains("News RP Host")
                || content.contains("Xyron Host")
                || content.contains("Bootstrap do host local")
                || content.contains("Bootstrap SA-MP do host local");
    }

    private static String readTextFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Exception ignored) {
            return "";
        }
        return builder.toString();
    }

    private static boolean writeBootstrapSource(File targetFile) {
        ensureParent(targetFile);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(targetFile, false),
                StandardCharsets.UTF_8
        )) {
            writer.write(BOOTSTRAP_GAMEMODE_SOURCE);
            writer.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private static void writeRuntimeState(Context context, String status, long pid, long startedAtMs, String note) {
        if (context == null) {
            return;
        }

        context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_STATUS, TextUtils.isEmpty(status) ? STATE_STOPPED : status)
                .putLong(PREF_PID, Math.max(0L, pid))
                .putLong(PREF_STARTED_AT, Math.max(0L, startedAtMs))
                .putString(PREF_NOTE, note == null ? "" : note)
                .apply();

        File stateFile = getStateFile(context);
        ensureParent(stateFile);
        Properties properties = new Properties();
        properties.setProperty("status", TextUtils.isEmpty(status) ? STATE_STOPPED : status);
        properties.setProperty("pid", Long.toString(Math.max(0L, pid)));
        properties.setProperty("startedAtMs", Long.toString(Math.max(0L, startedAtMs)));
        properties.setProperty("note", note == null ? "" : note);
        try (FileOutputStream outputStream = new FileOutputStream(stateFile, false)) {
            properties.store(outputStream, "xyron host runtime state");
        } catch (Exception ignored) {
        }
    }

    private static HostRuntimeState resetRuntimeState(Context context, String note) {
        String safeNote = TextUtils.isEmpty(note) ? "Host local desligado." : note;
        writeRuntimeState(context, STATE_STOPPED, 0L, 0L, safeNote);
        return new HostRuntimeState(STATE_STOPPED, 0L, 0L, safeNote);
    }

    private static HostRuntimeState readRuntimeState(Context context) {
        if (context == null) {
            return null;
        }

        android.content.SharedPreferences preferences = context.getSharedPreferences(
                RUNTIME_PREFS_NAME,
                Context.MODE_PRIVATE
        );
        String prefStatus = preferences.getString(PREF_STATUS, null);
        long prefPid = preferences.getLong(PREF_PID, 0L);
        long prefStartedAt = preferences.getLong(PREF_STARTED_AT, 0L);
        String prefNote = preferences.getString(PREF_NOTE, "");
        if (!TextUtils.isEmpty(prefStatus) || prefPid > 0L || prefStartedAt > 0L || !TextUtils.isEmpty(prefNote)) {
            HostRuntimeState prefState = new HostRuntimeState(prefStatus, prefPid, prefStartedAt, prefNote);
            if (prefState.pid > 0L && !isPidAlive(prefState.pid)) {
                return resetRuntimeState(context, "Host local desligado.");
            }
            if (prefState.pid <= 0L
                    && prefState.isOnline()
                    && !prefState.startedRecently(ONLINE_STATE_GRACE_MS)) {
                return resetRuntimeState(context, "Host local desligado.");
            }
            return prefState;
        }

        File stateFile = getStateFile(context);
        if (!stateFile.exists() || !stateFile.isFile()) {
            return new HostRuntimeState(STATE_STOPPED, 0L, 0L, "");
        }

        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(stateFile)) {
            properties.load(inputStream);
        } catch (Exception ignored) {
            return new HostRuntimeState(STATE_STOPPED, 0L, 0L, "");
        }

        String status = properties.getProperty("status", STATE_STOPPED);
        long pid = parseLong(properties.getProperty("pid"));
        long startedAtMs = parseLong(properties.getProperty("startedAtMs"));
        String note = properties.getProperty("note", "");

        if (pid > 0L && !isPidAlive(pid)) {
            return resetRuntimeState(context, "Host local desligado.");
        }
        HostRuntimeState fileState = new HostRuntimeState(status, pid, startedAtMs, note);
        if (fileState.pid <= 0L
                && fileState.isOnline()
                && !fileState.startedRecently(ONLINE_STATE_GRACE_MS)) {
            return resetRuntimeState(context, "Host local desligado.");
        }

        return fileState;
    }

    private static HostStartupCheck waitForStartupReadiness(Context context, Process process) {
        long startedAt = System.currentTimeMillis();
        while (System.currentTimeMillis() - startedAt < START_READY_TIMEOUT_MS) {
            if (!process.isAlive()) {
                return new HostStartupCheck(false, System.currentTimeMillis() - startedAt);
            }
            if (isLoopbackResponding()) {
                return new HostStartupCheck(true, System.currentTimeMillis() - startedAt);
            }
            try {
                Thread.sleep(220L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new HostStartupCheck(isLoopbackResponding(), System.currentTimeMillis() - startedAt);
    }

    private static boolean probeLoopbackAfterPipeClose() {
        long startedAt = System.currentTimeMillis();
        while (System.currentTimeMillis() - startedAt < PIPE_CLOSE_PROBE_TIMEOUT_MS) {
            if (isLoopbackResponding()) {
                return true;
            }
            try {
                Thread.sleep(180L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private static boolean isLoopbackResponding() {
        SampQueryApi queryApi = null;
        try {
            queryApi = new SampQueryApi(LocalHostManager.LOCAL_HOST, LocalHostManager.LOCAL_PORT, LOCAL_QUERY_TIMEOUT_MS);
            return queryApi.isOnline();
        } catch (Exception ignored) {
            return false;
        } finally {
            if (queryApi != null) {
                queryApi.close();
            }
        }
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean isPidAlive(long pid) {
        if (pid <= 0L) {
            return false;
        }
        try {
            Os.kill((int) pid, 0);
            return true;
        } catch (ErrnoException e) {
            return false;
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
                Thread.sleep(450L);
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
                return parseLong(description.substring(start, end));
            }
        }
        return 0L;
    }

    private static final class HostRuntimeContextHolder {
        private static Context applicationContext;

        private HostRuntimeContextHolder() {
        }

        static void set(Context context) {
            applicationContext = context == null ? null : context.getApplicationContext();
        }

        static Context peek() {
            return applicationContext;
        }
    }

    private static final class HostRuntimeState {
        final String status;
        final long pid;
        final long startedAtMs;
        final String note;

        HostRuntimeState(String status, long pid, long startedAtMs, String note) {
            this.status = TextUtils.isEmpty(status) ? STATE_STOPPED : status;
            this.pid = Math.max(0L, pid);
            this.startedAtMs = Math.max(0L, startedAtMs);
            this.note = note == null ? "" : note;
        }

        boolean hasLivePid() {
            return pid > 0L && isPidAlive(pid);
        }

        boolean isOnline() {
            return STATE_ONLINE.equals(status);
        }

        boolean isStarting() {
            return STATE_STARTING.equals(status);
        }

        boolean startedRecently(long maxAgeMs) {
            return startedAtMs > 0L && System.currentTimeMillis() - startedAtMs <= Math.max(1L, maxAgeMs);
        }
    }

    private static final class PackagedComponent {
        final String packagedLibraryName;
        final String runtimeFileName;

        PackagedComponent(String packagedLibraryName, String runtimeFileName) {
            this.packagedLibraryName = packagedLibraryName;
            this.runtimeFileName = runtimeFileName;
        }
    }

    private static final class RuntimeWorkspace {
        final File serverDir;
        final int linkedComponents;

        RuntimeWorkspace(File serverDir, int linkedComponents) {
            this.serverDir = serverDir;
            this.linkedComponents = linkedComponents;
        }
    }

    private static final class BootstrapResult {
        final boolean success;
        final String message;

        BootstrapResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        static BootstrapResult success(String message) {
            return new BootstrapResult(true, message);
        }

        static BootstrapResult error(String message) {
            return new BootstrapResult(false, message);
        }
    }

    private static final class HostStartupCheck {
        final boolean online;
        final long elapsedMs;

        HostStartupCheck(boolean online, long elapsedMs) {
            this.online = online;
            this.elapsedMs = Math.max(0L, elapsedMs);
        }
    }

    public static final class CommandResult {
        public final boolean success;
        public final String output;
        public final boolean clearConsole;
        public final boolean refreshState;

        private CommandResult(boolean success, String output, boolean clearConsole, boolean refreshState) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.clearConsole = clearConsole;
            this.refreshState = refreshState;
        }

        public static CommandResult success(String output, boolean clearConsole, boolean refreshState) {
            return new CommandResult(true, output, clearConsole, refreshState);
        }

        public static CommandResult error(String output) {
            return new CommandResult(false, output, false, false);
        }
    }
}
