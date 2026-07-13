package com.xyron.game.launcher.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.HostShellEngine;
import com.xyron.game.launcher.util.LocalHostManager;
import com.xyron.game.launcher.util.PinggyTunnelManager;
import com.xyron.game.launcher.util.TermuxHostBridge;

public class HostFragment extends Fragment {
    private static final long AUTO_REFRESH_INTERVAL_MS = 1200L;
    private static final String ACCESS_LOCAL = "local";
    private static final String ACCESS_LAN = "lan";
    private static final String ACCESS_REMOTE = "remote";
    private TextView statusBadge;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView workspaceValue;
    private TextView loopbackValue;
    private TextView hostActionNote;
    private TextView hostJoinInfo;
    private TextView localAddressValue;
    private TextView lanAddressValue;
    private TextView remoteAccessValue;
    private TextView bootButton;
    private TextView stopButton;
    private TextView remoteTunnelButton;
    private View rootView;
    private volatile boolean hostActionInFlight;
    private volatile boolean remoteTunnelActionInFlight;
    private String selectedAccessMode = ACCESS_LOCAL;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || rootView == null) {
                return;
            }
            refreshState();
            rootView.removeCallbacks(this);
            rootView.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_host, container, false);
        rootView = root;

        statusBadge = root.findViewById(R.id.host_status_badge);
        statusTitle = root.findViewById(R.id.host_status_title);
        statusBody = root.findViewById(R.id.host_status_body);
        workspaceValue = root.findViewById(R.id.host_workspace_value);
        loopbackValue = root.findViewById(R.id.host_loopback_value);
        hostActionNote = root.findViewById(R.id.host_action_note);
        hostJoinInfo = root.findViewById(R.id.host_join_info);
        localAddressValue = root.findViewById(R.id.host_local_address_value);
        lanAddressValue = root.findViewById(R.id.host_lan_address_value);
        remoteAccessValue = root.findViewById(R.id.host_remote_access_value);
        bootButton = root.findViewById(R.id.button_boot_host);
        stopButton = root.findViewById(R.id.button_stop_local_host);
        remoteTunnelButton = root.findViewById(R.id.button_host_pinggy);

        bindAction(bootButton, this::bootLocalHostRuntime);
        bindAction(stopButton, this::stopLocalHostRuntime);
        bindAction(root.findViewById(R.id.host_local_access_option), this::useSameDeviceAccess);
        bindAction(root.findViewById(R.id.host_lan_access_option), this::useLanAccess);
        bindAction(root.findViewById(R.id.host_remote_access_option), this::startRemoteTunnel);
        bindAction(root.findViewById(R.id.button_host_copy_lan), this::useSameDeviceAccess);
        bindAction(root.findViewById(R.id.button_host_refresh_join), this::useLanAccess);
        bindAction(remoteTunnelButton, this::startRemoteTunnel);
        bindAction(root.findViewById(R.id.button_open_server_files), this::openFilesTab);

        refreshState();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        stopAutoRefresh();
        super.onPause();
    }

    private void bindAction(View button, Runnable action) {
        if (button == null || getContext() == null) {
            return;
        }
        button.setOnTouchListener(new ButtonAnimator(getContext(), button));
        button.setOnClickListener(v -> {
            if (!button.isEnabled()) {
                return;
            }
            action.run();
        });
    }

    private void bootLocalHostRuntime() {
        runHostAction(true);
    }

    private void stopLocalHostRuntime() {
        runHostAction(false);
    }

    private void runHostAction(boolean start) {
        if (getContext() == null) {
            return;
        }
        if (hostActionInFlight) {
            return;
        }

        boolean hostRunning = HostShellEngine.isHostRunning(requireContext());
        boolean hostStarting = HostShellEngine.isHostStarting(requireContext());
        if (start && (hostRunning || hostStarting)) {
            Toast.makeText(requireContext(), "O host ja esta ligado ou iniciando.", Toast.LENGTH_SHORT).show();
            refreshState();
            return;
        }
        if (!start && !hostRunning && !hostStarting) {
            Toast.makeText(requireContext(), "O host ja esta desligado.", Toast.LENGTH_SHORT).show();
            refreshState();
            return;
        }

        hostActionInFlight = true;
        refreshButtons(hostRunning, HostShellEngine.isHostReady(requireContext()), hostStarting);
        final Context appContext = requireContext().getApplicationContext();

        new Thread(() -> {
            HostShellEngine.CommandResult result = start
                    ? HostShellEngine.bootHost(appContext)
                    : HostShellEngine.execute(appContext, "host stop");
            if (!start) {
                PinggyTunnelManager.stopTunnel(appContext);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hostActionInFlight = false;
                    Toast.makeText(requireContext(), extractToastMessage(result), Toast.LENGTH_LONG).show();
                    refreshState();
                });
            }
        }, start ? "xyron-host-boot" : "xyron-host-stop").start();
    }

    private String extractToastMessage(HostShellEngine.CommandResult result) {
        if (result == null || TextUtils.isEmpty(result.output)) {
            return "Operacao do host concluida.";
        }

        String output = result.output.trim();
        String[] lines = output.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || "Fluxo rapido do host".equalsIgnoreCase(line)) {
                continue;
            }
            String normalized = line.toLowerCase(java.util.Locale.US);
            if (normalized.contains("falha")
                    || normalized.contains("erro")
                    || normalized.contains("processo saiu")
                    || normalized.contains("nao foi possivel")
                    || normalized.contains("servidor pronto")) {
                return line;
            }
        }
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.isEmpty() && !"Fluxo rapido do host".equalsIgnoreCase(line)) {
                return line;
            }
        }
        return output;
    }

    private void useSameDeviceAccess() {
        runAccessHostAction(ACCESS_LOCAL, false);
    }

    private void useLanAccess() {
        runAccessHostAction(ACCESS_LAN, true);
    }

    private void runAccessHostAction(String accessMode, boolean copyLanAfterStart) {
        if (getContext() == null) {
            return;
        }
        if (hostActionInFlight || remoteTunnelActionInFlight) {
            Toast.makeText(getContext(), "Aguarde a acao atual terminar.", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedAccessMode = accessMode;
        hostActionInFlight = true;
        refreshButtons(
                HostShellEngine.isHostRunning(requireContext()),
                HostShellEngine.isHostReady(requireContext()),
                HostShellEngine.isHostStarting(requireContext())
        );

        final Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            PinggyTunnelManager.stopTunnel(appContext);

            boolean hostAlreadyAvailable = HostShellEngine.isHostReady(appContext)
                    || HostShellEngine.isHostRunning(appContext)
                    || HostShellEngine.isHostStarting(appContext);
            HostShellEngine.CommandResult result = hostAlreadyAvailable
                    ? HostShellEngine.CommandResult.success("Host local ja esta ligado.", false, true)
                    : HostShellEngine.bootHost(appContext);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hostActionInFlight = false;
                    if (result != null && result.success) {
                        if (copyLanAfterStart) {
                            copyLanAddress();
                        } else {
                            Toast.makeText(
                                    requireContext(),
                                    "Mesmo aparelho selecionado: " + LocalHostManager.getLoopbackAddress(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), extractToastMessage(result), Toast.LENGTH_LONG).show();
                    }
                    refreshState();
                });
            }
        }, ACCESS_LAN.equals(accessMode) ? "xyron-host-lan-mode" : "xyron-host-local-mode").start();
    }

    private void refreshJoinInfo() {
        if (getContext() == null) {
            return;
        }
        LocalHostManager.prepareSharedWorkspace(requireContext());
        String loopback = LocalHostManager.getLoopbackAddress();
        String lanIp = LocalHostManager.getBestLanAddress();
        String lanAddress = TextUtils.isEmpty(lanIp)
                ? "Conecte em uma Wi-Fi ou hotspot"
                : lanIp + ":" + LocalHostManager.LOCAL_PORT;
        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        boolean tunnelRunning = PinggyTunnelManager.isTunnelRunning(requireContext());
        boolean internalTunnelSupported = PinggyTunnelManager.isInternalTunnelSupported();
        boolean termuxReady = TermuxHostBridge.isTermuxInstalled(requireContext());
        String remoteMode;
        if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
            remoteMode = tunnelState.publicUrl;
        } else if (tunnelRunning || tunnelState.isStarting()) {
            remoteMode = "Tunel interno abrindo pelo APK";
        } else if (tunnelState.isError()) {
            remoteMode = "Tunel interno falhou. Toque para tentar de novo";
        } else if (internalTunnelSupported) {
            remoteMode = "Pronto no APK, sem Termux";
        } else if (termuxReady) {
            remoteMode = "Fallback via Termux disponivel";
        } else {
            remoteMode = "Use roteador ou Termux em aparelho sem ARM64";
        }

        if (localAddressValue != null) {
            localAddressValue.setText(loopback);
        }
        if (lanAddressValue != null) {
            lanAddressValue.setText(lanAddress);
        }
        if (remoteAccessValue != null) {
            remoteAccessValue.setText(remoteMode);
        }
        if (hostJoinInfo != null) {
            StringBuilder helper = new StringBuilder();
            String effectiveMode = tunnelRunning ? ACCESS_REMOTE : selectedAccessMode;
            if (ACCESS_LAN.equals(effectiveMode)) {
                helper.append("Opcao selecionada: mesma rede\n");
                helper.append("Compartilhe: ").append(lanAddress).append("\n");
                helper.append("Sem tunel remoto. O host continua sendo um so, na porta 7777.");
            } else if (ACCESS_REMOTE.equals(effectiveMode)) {
                helper.append("Opcao selecionada: acesso remoto\n");
                if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
                    helper.append("Compartilhe: ").append(tunnelState.publicUrl).append("\n");
                    helper.append("Formato pronto para SA-MP: IP numerico + porta.");
                } else if (internalTunnelSupported) {
                    helper.append("Toque em Acesso remoto para abrir o Pinggy UDP pelo APK.");
                } else {
                    helper.append("Abra UDP 7777 no roteador ou use o fallback via Termux.");
                }
            } else {
                helper.append("Opcao selecionada: mesmo aparelho\n");
                helper.append("Use no seu celular: ").append(loopback).append("\n");
                helper.append("Sem tunel remoto. Ideal para testar sozinho.");
            }
            hostJoinInfo.setText(helper.toString());
        }
    }

    private void copyLanAddress() {
        if (getContext() == null) {
            return;
        }

        String lanIp = LocalHostManager.getBestLanAddress();
        if (TextUtils.isEmpty(lanIp)) {
            Toast.makeText(getContext(), "Conecte o aparelho na Wi-Fi ou hotspot para gerar um IP LAN.", Toast.LENGTH_SHORT).show();
            return;
        }

        String address = lanIp + ":" + LocalHostManager.LOCAL_PORT;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("xyron-host-lan", address));
            Toast.makeText(getContext(), "Endereco LAN copiado: " + address, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRemoteTunnel() {
        if (getContext() == null) {
            return;
        }

        selectedAccessMode = ACCESS_REMOTE;
        if (remoteTunnelActionInFlight) {
            Toast.makeText(getContext(), "O tunel remoto ja esta abrindo.", Toast.LENGTH_SHORT).show();
            return;
        }

        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        if (PinggyTunnelManager.isTunnelRunning(requireContext()) && !TextUtils.isEmpty(tunnelState.publicUrl)) {
            copyRemoteTunnelAddress();
            showRemoteAccessDialog(true);
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        remoteTunnelActionInFlight = true;
        refreshButtons(
                HostShellEngine.isHostRunning(requireContext()),
                HostShellEngine.isHostReady(requireContext()),
                HostShellEngine.isHostStarting(requireContext())
        );

        new Thread(() -> {
            PinggyTunnelManager.LaunchStatus tunnelStatus;
            if (!HostShellEngine.isHostReady(appContext)
                    && !HostShellEngine.isHostRunning(appContext)
                    && !HostShellEngine.isHostStarting(appContext)) {
                HostShellEngine.CommandResult bootResult = HostShellEngine.bootHost(appContext);
                if (bootResult == null || !bootResult.success) {
                    String message = extractToastMessage(bootResult);
                    tunnelStatus = PinggyTunnelManager.LaunchStatus.failure(
                            "Antes do tunel, o host precisa ligar. " + message
                    );
                } else {
                    tunnelStatus = PinggyTunnelManager.startTunnel(appContext);
                }
            } else {
                tunnelStatus = PinggyTunnelManager.startTunnel(appContext);
            }

            if (!tunnelStatus.success
                    && !PinggyTunnelManager.isInternalTunnelSupported()
                    && TermuxHostBridge.isTermuxInstalled(appContext)) {
                TermuxHostBridge.LaunchStatus termuxStatus =
                        TermuxHostBridge.prepareAndStartPinggyUdp(appContext);
                tunnelStatus = termuxStatus.success
                        ? PinggyTunnelManager.LaunchStatus.success(termuxStatus.message, "")
                        : PinggyTunnelManager.LaunchStatus.failure(termuxStatus.message);
            }

            PinggyTunnelManager.LaunchStatus finalStatus = tunnelStatus;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    remoteTunnelActionInFlight = false;
                    Toast.makeText(requireContext(), finalStatus.message, Toast.LENGTH_LONG).show();
                    refreshState();
                    showRemoteAccessDialog(finalStatus.success);
                });
            }
        }, "xyron-remote-tunnel").start();
    }

    private void copyRemoteTunnelAddress() {
        if (getContext() == null) {
            return;
        }

        String publicUrl = PinggyTunnelManager.getPublicUrl(requireContext());
        if (TextUtils.isEmpty(publicUrl)) {
            Toast.makeText(getContext(), "O endereco publico ainda nao apareceu.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("xyron-host-tunnel", publicUrl));
            Toast.makeText(getContext(), "Tunel copiado: " + publicUrl, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRemoteAccessDialog(boolean tunnelStarted) {
        if (getContext() == null) {
            return;
        }

        PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
        boolean internalTunnelSupported = PinggyTunnelManager.isInternalTunnelSupported();
        boolean termuxReady = TermuxHostBridge.isTermuxInstalled(requireContext());
        StringBuilder message = new StringBuilder();
        message.append(LocalHostManager.buildJoinInfo());
        message.append("\n\n");
        if (!TextUtils.isEmpty(tunnelState.publicUrl)) {
            message.append("Tunel remoto online pelo APK:\n");
            message.append(tunnelState.publicUrl);
            message.append("\n\nCompartilhe esse endereco com quem vai entrar pela internet.");
        } else if (PinggyTunnelManager.isTunnelRunning(requireContext()) || tunnelState.isStarting()) {
            message.append("O motor interno do APK esta abrindo o Pinggy UDP. Assim que o endereco publico aparecer, ele fica neste painel.");
        } else if (tunnelState.isError()) {
            message.append(tunnelState.note);
            message.append("\n\nToque em Abrir tunel remoto para tentar de novo.");
        } else if (internalTunnelSupported) {
            message.append(tunnelStarted
                    ? "O launcher tentou abrir o tunel remoto pelo APK. Se a rede do chip oscilar, toque de novo."
                    : "Este APK ja tem motor interno para abrir Pinggy UDP sem Termux.");
        } else if (termuxReady) {
            message.append("Este aparelho nao liberou ARM64 para o motor interno. O fallback via Termux ainda pode abrir o Pinggy UDP.");
        } else {
            message.append("Este aparelho nao liberou ARM64 para o motor interno. Para internet, abra UDP 7777 no roteador ou use Termux como fallback.");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("Acesso remoto")
                .setMessage(message.toString())
                .setNegativeButton("Fechar", null)
                .setNeutralButton("Copiar IP LAN", (dialog, which) -> copyLanAddress());

        if (!TextUtils.isEmpty(tunnelState.publicUrl)) {
            builder.setPositiveButton("Copiar tunel", (dialog, which) -> copyRemoteTunnelAddress());
        } else if (termuxReady || internalTunnelSupported) {
            builder.setPositiveButton("Abrir arquivos", (dialog, which) -> openFilesTab());
        }

        builder.show();
    }

    private void openFilesTab() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openTab(MainActivity.TAB_HOST_FILES);
        }
    }

    private void refreshState() {
        if (getContext() == null) {
            return;
        }

        LocalHostManager.HostState state = LocalHostManager.getState(requireContext());
        boolean hostRunning = HostShellEngine.isHostRunning(requireContext());
        boolean hostReady = HostShellEngine.isHostReady(requireContext());
        boolean hostStarting = HostShellEngine.isHostStarting(requireContext());
        boolean hostErrored = HostShellEngine.isHostErrored(requireContext());
        String runtimeMessage = HostShellEngine.getHostStatusMessage(requireContext());
        if (workspaceValue != null) {
            String sharedPath = LocalHostManager.getSharedWorkspacePath();
            workspaceValue.setText(state.workspacePrepared
                    ? state.workspacePath + "\nDownloads: " + sharedPath
                    : "A base local ainda nao foi preparada.");
        }

        if (loopbackValue != null) {
            String loopbackLabel;
            if (state.loopbackSelected) {
                loopbackLabel = state.loopbackAddress + " ativo no launcher";
            } else if (state.loopbackSaved) {
                loopbackLabel = state.loopbackAddress + " salvo, mas nao ativo";
            } else {
                loopbackLabel = state.loopbackAddress + " ainda nao foi salvo";
            }
            loopbackValue.setText(loopbackLabel);
        }

        if (statusTitle != null) {
            if (hostReady) {
                statusTitle.setText("Servidor local online");
            } else if (hostRunning || hostStarting) {
                statusTitle.setText("Runtime local em execucao");
            } else if (hostErrored) {
                statusTitle.setText("Falha ao ligar host");
            } else if (!state.workspacePrepared) {
                statusTitle.setText("Host local em preparacao");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusTitle.setText("Runtime local encontrado");
            } else {
                statusTitle.setText("Base local pronta");
            }
        }

        if (statusBadge != null) {
            if (hostReady) {
                statusBadge.setText("ONLINE");
            } else if (hostRunning || hostStarting) {
                statusBadge.setText("INICIANDO");
            } else if (hostErrored) {
                statusBadge.setText("ERRO");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusBadge.setText("PRONTO");
            } else {
                statusBadge.setText("BASE");
            }
        }

        if (statusBody != null) {
            if (hostReady) {
                statusBody.setText("O host subiu na porta 7777 e ja pode receber jogadores locais. Se quiser chamar gente de fora da rede, abra o tunel remoto.");
            } else if (hostRunning || hostStarting) {
                if (TextUtils.isEmpty(runtimeMessage)) {
                    statusBody.setText("O runtime foi iniciado e esta fechando a subida do servidor local agora.");
                } else {
                    statusBody.setText(runtimeMessage);
                }
            } else if (hostErrored) {
                statusBody.setText(TextUtils.isEmpty(runtimeMessage)
                        ? "O host nao conseguiu iniciar. Toque em Ligar host para tentar preparar a base de novo."
                        : runtimeMessage);
            } else if (!state.workspacePrepared) {
                statusBody.setText("Toque em Ligar host para preparar a base, ativar o loopback e subir o servidor local automaticamente.");
            } else if (HostShellEngine.hasRuntimeCandidate(requireContext())) {
                statusBody.setText("A base ja foi criada e o runtime ARM esta no pacote. O launcher ja consegue subir tudo em um toque.");
            } else {
                statusBody.setText("A estrutura local ja existe, mas esta build ainda nao trouxe um runtime ARM compativel para executar o host.");
            }
        }

        if (hostActionNote != null) {
            if (hostActionInFlight) {
                hostActionNote.setText("Aguarde a acao atual terminar antes de tocar de novo.");
            } else if (hostReady) {
                hostActionNote.setText("Host online. O botao de ligar fica travado ate voce desligar o servidor.");
            } else if (hostRunning || hostStarting) {
                hostActionNote.setText("O servidor ja recebeu o comando de start. Espere a tela virar para o estado online.");
            } else if (hostErrored) {
                hostActionNote.setText("A ultima tentativa falhou. Toque em Ligar host para refazer os links internos e tentar de novo.");
            } else {
                hostActionNote.setText("Fluxo rapido: ligue o host, copie o LAN para a mesma rede e use o tunel remoto para testar pela internet.");
            }
        }

        refreshButtons(hostRunning, hostReady, hostStarting);
        refreshJoinInfo();
    }

    private void startAutoRefresh() {
        if (rootView == null) {
            return;
        }
        rootView.removeCallbacks(autoRefreshRunnable);
        rootView.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (rootView != null) {
            rootView.removeCallbacks(autoRefreshRunnable);
        }
    }

    private void refreshButtons(boolean hostRunning, boolean hostReady, boolean hostStarting) {
        boolean hostBusyOrOnline = hostRunning || hostReady || hostStarting;
        boolean canStart = !hostActionInFlight && !hostBusyOrOnline;
        boolean canStop = !hostActionInFlight && hostBusyOrOnline;

        if (bootButton != null) {
            bootButton.setEnabled(canStart);
            bootButton.setAlpha(canStart ? 1f : 0.48f);
            if (hostActionInFlight && !hostBusyOrOnline) {
                bootButton.setText("Ligando host...");
            } else if (hostReady) {
                bootButton.setText("Host online");
            } else if (hostStarting || hostRunning) {
                bootButton.setText("Ligando host...");
            } else {
                bootButton.setText("Ligar host");
            }
        }

        if (stopButton != null) {
            stopButton.setEnabled(canStop);
            stopButton.setAlpha(canStop ? 1f : 0.48f);
            if (hostActionInFlight && hostBusyOrOnline) {
                stopButton.setText("Desligando...");
            } else {
                stopButton.setText("Desligar host");
            }
        }

        if (remoteTunnelButton != null && getContext() != null) {
            PinggyTunnelManager.TunnelState tunnelState = PinggyTunnelManager.getState(requireContext());
            boolean tunnelRunning = PinggyTunnelManager.isTunnelRunning(requireContext());
            remoteTunnelButton.setEnabled(!remoteTunnelActionInFlight);
            remoteTunnelButton.setAlpha(remoteTunnelActionInFlight ? 0.56f : 1f);
            if (remoteTunnelActionInFlight) {
                remoteTunnelButton.setText("Abrindo tunel...");
            } else if (tunnelRunning && !TextUtils.isEmpty(tunnelState.publicUrl)) {
                remoteTunnelButton.setText("Copiar remoto");
            } else if (tunnelRunning || tunnelState.isStarting()) {
                remoteTunnelButton.setText("Abrindo...");
            } else {
                remoteTunnelButton.setText("Acesso remoto");
            }
        }
    }
}
