package com.xyron.game.launcher.fragments;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.ServerWorkbenchManager;

public class WorkbenchFragment extends Fragment {
    private TextView workbenchStatusSummary;
    private TextView workbenchBackupSummary;
    private TextView workbenchLogsOutput;
    private TextView workbenchResourceNote;
    private EditText hostnameInput;
    private EditText websiteInput;
    private EditText languageInput;
    private EditText maxPlayersInput;
    private EditText mainScriptInput;
    private EditText passwordInput;
    private EditText mapNameInput;
    private SwitchCompat announceSwitch;
    private SwitchCompat querySwitch;
    private SwitchCompat lanModeSwitch;

    private volatile boolean backgroundActionInFlight;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_workbench, container, false);

        workbenchStatusSummary = root.findViewById(R.id.workbench_status_summary);
        workbenchBackupSummary = root.findViewById(R.id.workbench_backup_summary);
        workbenchLogsOutput = root.findViewById(R.id.workbench_logs_output);
        workbenchResourceNote = root.findViewById(R.id.workbench_resource_note);
        hostnameInput = root.findViewById(R.id.workbench_hostname_input);
        websiteInput = root.findViewById(R.id.workbench_website_input);
        languageInput = root.findViewById(R.id.workbench_language_input);
        maxPlayersInput = root.findViewById(R.id.workbench_max_players_input);
        mainScriptInput = root.findViewById(R.id.workbench_main_script_input);
        passwordInput = root.findViewById(R.id.workbench_password_input);
        mapNameInput = root.findViewById(R.id.workbench_map_name_input);
        announceSwitch = root.findViewById(R.id.workbench_announce_switch);
        querySwitch = root.findViewById(R.id.workbench_query_switch);
        lanModeSwitch = root.findViewById(R.id.workbench_lan_mode_switch);

        bindAction(root.findViewById(R.id.button_workbench_save_settings), this::saveSettings);
        bindAction(root.findViewById(R.id.button_workbench_new_gamemode), () -> promptCreateResource(
                ServerWorkbenchManager.ResourceType.GAMEMODE,
                "Nova gamemode",
                "Cria um .pwn inicial em /server/gamemodes para voce continuar no editor.",
                "ex: brasil_roleplay"
        ));
        bindAction(root.findViewById(R.id.button_workbench_new_include), () -> promptCreateResource(
                ServerWorkbenchManager.ResourceType.INCLUDE,
                "Novo include",
                "Cria um .inc com guarda basica em /editor/include.",
                "ex: player_utils"
        ));
        bindAction(root.findViewById(R.id.button_workbench_new_scriptfile), () -> promptCreateResource(
                ServerWorkbenchManager.ResourceType.SCRIPTFILE,
                "Novo scriptfile",
                "Cria um arquivo auxiliar em /server/scriptfiles.",
                "ex: contas"
        ));
        bindAction(root.findViewById(R.id.button_workbench_new_config), () -> promptCreateResource(
                ServerWorkbenchManager.ResourceType.CONFIG,
                "Novo config",
                "Cria um .cfg base dentro da pasta /server.",
                "ex: eventos"
        ));
        bindAction(root.findViewById(R.id.button_workbench_open_editor), () -> openTab(MainActivity.TAB_EDITOR));
        bindAction(root.findViewById(R.id.button_workbench_open_files), () -> openTab(MainActivity.TAB_HOST_FILES));
        bindAction(root.findViewById(R.id.button_workbench_open_host), () -> openTab(MainActivity.TAB_HOST));
        bindAction(root.findViewById(R.id.button_workbench_refresh_logs), this::refreshAll);
        bindAction(root.findViewById(R.id.button_workbench_create_backup), this::promptCreateBackup);

        refreshAll();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    private void bindAction(View button, Runnable action) {
        if (button == null || getContext() == null) {
            return;
        }
        button.setOnTouchListener(new ButtonAnimator(getContext(), button));
        button.setOnClickListener(v -> action.run());
    }

    private void refreshAll() {
        if (getContext() == null) {
            return;
        }
        boolean prepared = ServerWorkbenchManager.prepare(requireContext());
        if (workbenchStatusSummary != null) {
            workbenchStatusSummary.setText(prepared
                    ? "Workbench pronto. O host compartilhado, o editor e os arquivos principais ja foram encontrados."
                    : "A base do host ainda nao foi preparada por completo.");
        }
        loadSettingsIntoForm();
        refreshBackupAndLogs();
    }

    private void loadSettingsIntoForm() {
        if (getContext() == null) {
            return;
        }
        ServerWorkbenchManager.ServerSettings settings = ServerWorkbenchManager.loadSettings(requireContext());
        hostnameInput.setText(settings.hostname);
        websiteInput.setText(settings.website);
        languageInput.setText(settings.language);
        maxPlayersInput.setText(String.valueOf(settings.maxPlayers));
        mainScriptInput.setText(settings.mainScript);
        passwordInput.setText(settings.password);
        mapNameInput.setText(settings.mapName);
        announceSwitch.setChecked(settings.announce);
        querySwitch.setChecked(settings.query);
        lanModeSwitch.setChecked(settings.lanMode);
    }

    private void refreshBackupAndLogs() {
        ServerWorkbenchManager.BackupSummary backupSummary = ServerWorkbenchManager.getBackupSummary();
        ServerWorkbenchManager.LogSummary logSummary = ServerWorkbenchManager.getLatestLogSummary();

        if (workbenchBackupSummary != null) {
            workbenchBackupSummary.setText(backupSummary.summary);
        }
        if (workbenchLogsOutput != null) {
            workbenchLogsOutput.setText(logSummary.snippet);
        }
    }

    private void saveSettings() {
        if (getContext() == null || backgroundActionInFlight) {
            return;
        }

        ServerWorkbenchManager.ServerSettings settings = new ServerWorkbenchManager.ServerSettings();
        settings.hostname = textOf(hostnameInput);
        settings.website = textOf(websiteInput);
        settings.language = textOf(languageInput);
        settings.maxPlayers = parseMaxPlayers(textOf(maxPlayersInput));
        settings.mainScript = textOf(mainScriptInput);
        settings.password = textOf(passwordInput);
        settings.mapName = textOf(mapNameInput);
        settings.announce = announceSwitch != null && announceSwitch.isChecked();
        settings.query = querySwitch != null && querySwitch.isChecked();
        settings.lanMode = lanModeSwitch != null && lanModeSwitch.isChecked();

        ServerWorkbenchManager.ActionResult result = ServerWorkbenchManager.saveSettings(requireContext(), settings);
        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
        if (result.success) {
            loadSettingsIntoForm();
        }
    }

    private void promptCreateResource(ServerWorkbenchManager.ResourceType type, String title, String note, String hint) {
        showPromptDialog(
                "Recurso",
                title,
                note,
                hint,
                "Criar",
                false,
                value -> runBackgroundAction(() -> {
                    ServerWorkbenchManager.ActionResult result =
                            ServerWorkbenchManager.createResource(requireContext(), type, value);
                    postActionResult(result, true);
                })
        );
    }

    private void promptCreateBackup() {
        showPromptDialog(
                "Backup",
                "Criar backup .zip",
                "Digite um rotulo curto opcional. O launcher vai gerar um .zip da pasta compartilhada do host.",
                "ex: versao_teste",
                "Gerar",
                true,
                value -> runBackgroundAction(() -> {
                    ServerWorkbenchManager.ActionResult result =
                            ServerWorkbenchManager.createBackup(requireContext(), value);
                    postActionResult(result, true);
                })
        );
    }

    private void runBackgroundAction(Runnable action) {
        if (backgroundActionInFlight || getContext() == null) {
            return;
        }
        backgroundActionInFlight = true;
        new Thread(() -> {
            try {
                action.run();
            } finally {
                backgroundActionInFlight = false;
            }
        }, "xyron-workbench-action").start();
    }

    private void postActionResult(ServerWorkbenchManager.ActionResult result, boolean refreshAfter) {
        if (getActivity() == null || getContext() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
            if (refreshAfter) {
                refreshAll();
                if (workbenchResourceNote != null && result.success) {
                    workbenchResourceNote.setText(result.message + "\nContinue no Editor ou em Arquivos para ajustar o conteudo.");
                }
            }
        });
    }

    private void showPromptDialog(
            String badge,
            String title,
            String note,
            String hint,
            String confirmLabel,
            boolean allowEmpty,
            PromptListener listener
    ) {
        if (getContext() == null || listener == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_workbench_prompt,
                null,
                false
        );

        TextView badgeView = dialogView.findViewById(R.id.dialog_workbench_badge);
        TextView titleView = dialogView.findViewById(R.id.dialog_workbench_title);
        TextView noteView = dialogView.findViewById(R.id.dialog_workbench_note);
        EditText input = dialogView.findViewById(R.id.dialog_workbench_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_workbench_cancel);
        TextView confirmButton = dialogView.findViewById(R.id.dialog_workbench_confirm);

        badgeView.setText(badge);
        titleView.setText(title);
        noteView.setText(note);
        input.setHint(hint);
        confirmButton.setText(confirmLabel);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelButton.setOnTouchListener(new ButtonAnimator(getContext(), cancelButton));
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnTouchListener(new ButtonAnimator(getContext(), confirmButton));
        confirmButton.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(value) && !allowEmpty) {
                Toast.makeText(getContext(), "Digite um valor para continuar.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            listener.onConfirm(value);
        });

        dialog.show();
        input.requestFocus();
    }

    private void openTab(int index) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openTab(index);
        }
    }

    private int parseMaxPlayers(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return 32;
        }
    }

    private String textOf(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private interface PromptListener {
        void onConfirm(String value);
    }
}
