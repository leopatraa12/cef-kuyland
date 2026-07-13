package com.xyron.game.launcher.fragments;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xyron.game.BuildConfig;
import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.UpdateActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.ServerConfigManager;

public class HomeFragment extends Fragment {
    private static final int NEXT_STEP_PLAY = 0;
    private static final int NEXT_STEP_UPDATE_DATA = 1;
    private static final int NEXT_STEP_ADD_SERVER = 2;
    private static final int NEXT_STEP_CHOOSE_SERVER = 3;

    private View playButton;
    private TextView selectServerButton;
    private TextView selectedServerName;
    private TextView selectedServerAddress;
    private TextView selectedServerHint;
    private TextView readinessTitle;
    private TextView readinessBody;
    private TextView nextStepButton;
    private int nextStepAction = NEXT_STEP_PLAY;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        playButton = root.findViewById(R.id.play_button);
        selectServerButton = root.findViewById(R.id.select_server_button);
        selectedServerName = root.findViewById(R.id.selected_server_name);
        selectedServerAddress = root.findViewById(R.id.selected_server_address);
        selectedServerHint = root.findViewById(R.id.selected_server_hint);
        readinessTitle = root.findViewById(R.id.home_readiness_title);
        readinessBody = root.findViewById(R.id.home_readiness_body);
        nextStepButton = root.findViewById(R.id.home_next_step_button);

        if (playButton != null) {
            playButton.setOnTouchListener(new ButtonAnimator(requireContext(), playButton));
            playButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).startGta();
                }
            });
        }

        if (selectServerButton != null) {
            selectServerButton.setOnTouchListener(new ButtonAnimator(requireContext(), selectServerButton));
            selectServerButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openTab(
                            ServerConfigManager.hasConfiguredServers(requireContext())
                                    ? MainActivity.TAB_SERVERS
                                    : MainActivity.TAB_SETTINGS
                    );
                }
            });
        }

        configureTabShortcut(root, R.id.home_quick_host, MainActivity.TAB_HOST);
        configureTabShortcut(root, R.id.home_quick_files, MainActivity.TAB_HOST_FILES);
        configureTabShortcut(root, R.id.home_quick_settings, MainActivity.TAB_SETTINGS);

        if (nextStepButton != null) {
            nextStepButton.setOnTouchListener(new ButtonAnimator(requireContext(), nextStepButton));
            nextStepButton.setOnClickListener(v -> performNextStep());
        }

        updateSelectedServer();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSelectedServer();
    }

    private void configureTabShortcut(View root, int viewId, int tabIndex) {
        View shortcut = root.findViewById(viewId);
        if (shortcut == null) {
            return;
        }

        if (getActivity() instanceof MainActivity
                && !((MainActivity) getActivity()).isTabAllowed(tabIndex)) {
            shortcut.setVisibility(View.GONE);
            return;
        }

        shortcut.setOnTouchListener(new ButtonAnimator(requireContext(), shortcut));
        shortcut.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTab(tabIndex);
            }
        });
    }

    private void updateSelectedServer() {
        Context context = getContext();
        if (context == null || selectedServerName == null || selectedServerAddress == null || selectedServerHint == null) {
            return;
        }

        boolean hasGameData = isLauncherRole() || GameDataVerifier.hasRequiredGameData(context);
        boolean hasServers = ServerConfigManager.hasConfiguredServers(context);

        if (!hasServers) {
            if (selectServerButton != null) {
                selectServerButton.setText("Adicionar servidor");
            }
            selectedServerName.setText("Sem servidor ativo");
            selectedServerAddress.setText("Toque em Adicionar servidor");
            selectedServerHint.setText("Depois de salvar um IP, ele fica pronto para usar aqui.");
            updateReadiness(hasGameData, false, false);
            return;
        }

        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(context);
        if (selectedServer == null || !selectedServer.isValid()) {
            if (selectServerButton != null) {
                selectServerButton.setText("Escolher servidor");
            }
            selectedServerName.setText("Sem servidor ativo");
            selectedServerAddress.setText("Escolha um IP salvo em Servidores");
            selectedServerHint.setText("O servidor escolhido fica aplicado no jogo automaticamente.");
            updateReadiness(hasGameData, true, false);
            return;
        }

        if (selectServerButton != null) {
            selectServerButton.setText("Trocar servidor");
        }
        selectedServerName.setText("Pronto para jogar");
        selectedServerAddress.setText(selectedServer.getAddress());
        selectedServerHint.setText(selectedServer.favorite
                ? "Servidor favoritado e pronto para abrir no jogo."
                : "Servidor pronto para abrir no jogo. Use Trocar servidor para mudar.");
        updateReadiness(hasGameData, true, true);
    }

    private void updateReadiness(boolean hasGameData, boolean hasServers, boolean hasSelectedServer) {
        if (readinessTitle == null || readinessBody == null || nextStepButton == null) {
            return;
        }

        if (!hasGameData) {
            nextStepAction = NEXT_STEP_UPDATE_DATA;
            readinessTitle.setText("Dados pendentes");
            readinessBody.setText("Baixe os arquivos do jogo");
            nextStepButton.setText("Baixar dados");
            return;
        }

        if (!hasServers) {
            nextStepAction = NEXT_STEP_ADD_SERVER;
            readinessTitle.setText("Falta servidor");
            readinessBody.setText("Adicione um IP:PORTA");
            nextStepButton.setText("Adicionar servidor");
            return;
        }

        if (!hasSelectedServer) {
            nextStepAction = NEXT_STEP_CHOOSE_SERVER;
            readinessTitle.setText("Escolha o servidor");
            readinessBody.setText("Defina o IP ativo");
            nextStepButton.setText("Escolher servidor");
            return;
        }

        nextStepAction = NEXT_STEP_PLAY;
        if (isLauncherRole()) {
            readinessTitle.setText("Servidor pronto");
            readinessBody.setText("Abre pelo APK Game");
            nextStepButton.setText("Abrir Game");
        } else {
            readinessTitle.setText("Tudo pronto");
            readinessBody.setText("Pode entrar no jogo");
            nextStepButton.setText("Jogar agora");
        }
    }

    private void performNextStep() {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }

        MainActivity activity = (MainActivity) getActivity();
        if (nextStepAction == NEXT_STEP_UPDATE_DATA) {
            Intent intent = new Intent(requireContext(), UpdateActivity.class);
            intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(intent);
            return;
        }

        if (nextStepAction == NEXT_STEP_ADD_SERVER) {
            activity.openTab(MainActivity.TAB_SETTINGS);
            return;
        }

        if (nextStepAction == NEXT_STEP_CHOOSE_SERVER) {
            activity.openTab(MainActivity.TAB_SERVERS);
            return;
        }

        activity.startGta();
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }
}
