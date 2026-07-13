package com.xyron.game.launcher.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.xyron.game.BuildConfig;
import com.xyron.game.R;
import com.xyron.game.launcher.EntryActivity;
import com.xyron.game.launcher.SplashActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.ServerConfigManager;
import com.xyron.game.launcher.util.SharedPreferenceCore;
import com.xyron.game.launcher.util.Util;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class SettingsFragment extends Fragment {

    private Wini mWini;
    private SwitchCompat mKeyboardSwitch;
    private SwitchCompat mVoipSwitch;
    private TextView nicknameValue;

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View view = layoutInflater.inflate(R.layout.fragment_settings, viewGroup, false);

        mKeyboardSwitch = view.findViewById(R.id.keyboard_switch);
        mVoipSwitch = view.findViewById(R.id.voip_switch);
        nicknameValue = view.findViewById(R.id.player_name_value);
        TextView editProfileButton = view.findViewById(R.id.button_edit_profile);
        TextView addServerButton = view.findViewById(R.id.button_add_server_shortcut);
        TextView reinstallButton = view.findViewById(R.id.button_reinstall);

        openSettingsFile();
        configureChatSpinner(view);
        loadNickname();

        if (editProfileButton != null) {
            editProfileButton.setOnTouchListener(new ButtonAnimator(getContext(), editProfileButton));
            editProfileButton.setOnClickListener(v -> showNicknameDialog());
        }

        if (addServerButton != null) {
            addServerButton.setOnTouchListener(new ButtonAnimator(getContext(), addServerButton));
            addServerButton.setOnClickListener(v -> showAddServerDialog());
        }

        if (reinstallButton != null) {
            if (isLauncherRole()) {
                reinstallButton.setText("Abrir download do Game");
            } else if (isToolsRole()) {
                reinstallButton.setText("Download fica no Game");
            }
            reinstallButton.setOnTouchListener(new ButtonAnimator(getContext(), reinstallButton));
            reinstallButton.setOnClickListener(v -> handleDataAction());
        }

        if (mKeyboardSwitch != null) {
            mKeyboardSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    new SharedPreferenceCore().setBoolean(
                            requireContext().getApplicationContext(),
                            "ANDROID_KEYBOARD",
                            checked
                    );
                    saveIniValue("gui", "androidkeyboard23123", checked);
                }
            });
        }

        if (mVoipSwitch != null) {
            mVoipSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    new SharedPreferenceCore().setBoolean(
                            requireContext().getApplicationContext(),
                            "VOICE_CHAT_ENABLE",
                            checked
                    );
                    saveIniValue("gui", "VoiceChatEnable", checked);
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mKeyboardSwitch != null) {
            mKeyboardSwitch.setChecked(new SharedPreferenceCore().getBoolean(
                    requireContext().getApplicationContext(),
                    "ANDROID_KEYBOARD"
            ));
        }
        if (mVoipSwitch != null) {
            mVoipSwitch.setChecked(parseBooleanSetting(
                    readIniValue("gui", "VoiceChatEnable", "true"),
                    true
            ));
        }
        loadNickname();
    }

    private void openSettingsFile() {
        if (getActivity() == null || getActivity().getExternalFilesDir(null) == null) {
            return;
        }

        File file = new File(getActivity().getExternalFilesDir(null), "SAMP/settings.ini");
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            }
            mWini = new Wini(file);
        } catch (IOException e) {
            mWini = null;
            e.printStackTrace();
        }
    }

    private void configureChatSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.chat_count_select);
        if (spinner == null) {
            return;
        }

        String[] sort = {"5", "10", "15", "20"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(), R.layout.spinner_item, sort);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View selectedView, int index, long id) {
                int chatCount = 5;
                switch (index) {
                    case 1:
                        chatCount = 10;
                        break;
                    case 2:
                        chatCount = 15;
                        break;
                    case 3:
                        chatCount = 20;
                        break;
                    default:
                        chatCount = 5;
                        break;
                }

                Log.d("x1y2z", "chatCount " + chatCount);
                saveIniValue("gui", "ChatMaxMessages", chatCount);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        String message = readIniValue("gui", "ChatMaxMessages", "5");
        switch (message) {
            case "10":
                spinner.setSelection(1);
                break;
            case "15":
                spinner.setSelection(2);
                break;
            case "20":
                spinner.setSelection(3);
                break;
            default:
                spinner.setSelection(0);
                break;
        }
    }

    private void loadNickname() {
        if (nicknameValue == null) {
            return;
        }

        String nickname = readIniValue("client", "name", "");
        nicknameValue.setText(nickname.isEmpty() ? "Nenhum nome definido" : nickname);
    }

    private void saveNickname(String nickname) {
        if (nickname.isEmpty()) {
            Toast.makeText(getContext(), "Digite um nome para salvar no jogo.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!saveIniValue("client", "name", nickname)) {
            Toast.makeText(getContext(), "Nao foi possivel salvar o nome.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "Nome salvo no launcher e no arquivo do jogo.", Toast.LENGTH_SHORT).show();
        loadNickname();
    }

    private void handleDataAction() {
        if (getActivity() == null) {
            return;
        }

        if (isLauncherRole()) {
            launchGameDataUpdate();
            return;
        }

        if (isToolsRole()) {
            Toast.makeText(getContext(), "Baixe ou reinstale a data pelo APK News RP.", Toast.LENGTH_LONG).show();
            return;
        }

        File dir = new File(getActivity().getExternalFilesDir(null) + "/");
        Util.delete(dir);
        Intent intent = new Intent(getActivity(), EntryActivity.class);
        intent.putExtra(EntryActivity.EXTRA_FORCE_UPDATE_DATA, true);
        startActivity(intent);
        getActivity().finish();
    }

    private void launchGameDataUpdate() {
        Intent intent = new Intent();
        intent.setClassName(BuildConfig.XYRON_GAME_PACKAGE, "com.xyron.game.launcher.EntryActivity");
        intent.putExtra(EntryActivity.EXTRA_FORCE_UPDATE_DATA, true);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), "Instale o APK News RP para baixar a data.", Toast.LENGTH_LONG).show();
        }
    }

    private String readIniValue(String section, String key, String fallback) {
        if (mWini == null) {
            return fallback;
        }

        String value = mWini.get(section, key);
        return value == null ? fallback : value.trim();
    }

    private boolean saveIniValue(String section, String key, Object value) {
        if (mWini == null) {
            return false;
        }

        try {
            mWini.put(section, key, value);
            mWini.store();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean parseBooleanSetting(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }

    private boolean isToolsRole() {
        return "tools".equals(BuildConfig.XYRON_APK_ROLE);
    }

    private void showAddServerDialog() {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_add_server,
                null,
                false
        );
        EditText input = dialogView.findViewById(R.id.dialog_server_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_server_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_server_save);
        TextView limitText = dialogView.findViewById(R.id.dialog_server_limit);

        if (limitText != null) {
            limitText.setText("Voce pode salvar ate "
                    + ServerConfigManager.getMaxSavedServers()
                    + " servidores.");
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (cancelButton != null) {
            cancelButton.setOnTouchListener(new ButtonAnimator(getContext(), cancelButton));
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (saveButton != null) {
            saveButton.setOnTouchListener(new ButtonAnimator(getContext(), saveButton));
            saveButton.setOnClickListener(v -> {
                String rawAddress = input != null && input.getText() != null
                        ? input.getText().toString().trim()
                        : "";

                if (!ServerConfigManager.isValidRawAddress(rawAddress)) {
                    Toast.makeText(
                            getContext(),
                            "Use o formato IP:PORTA, por exemplo 127.0.0.1:7777.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                ServerConfigManager.ServerOption option =
                        ServerConfigManager.addOrUpdateServer(requireContext(), rawAddress);
                if (!option.isValid()) {
                    Toast.makeText(
                            getContext(),
                            "Voce pode salvar no maximo "
                                    + ServerConfigManager.getMaxSavedServers()
                                    + " servidores.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                Toast.makeText(
                        getContext(),
                        "Servidor adicionado. Ele ja aparece na aba Servidores.",
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
            });
        }

        dialog.show();
        if (input != null) {
            input.requestFocus();
        }
    }

    private void showNicknameDialog() {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_edit_name,
                null,
                false
        );
        EditText input = dialogView.findViewById(R.id.dialog_name_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_name_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_name_save);

        if (input != null) {
            String currentNickname = readIniValue("client", "name", "");
            input.setText(currentNickname);
            input.setSelection(input.getText() != null ? input.getText().length() : 0);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (cancelButton != null) {
            cancelButton.setOnTouchListener(new ButtonAnimator(getContext(), cancelButton));
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (saveButton != null) {
            saveButton.setOnTouchListener(new ButtonAnimator(getContext(), saveButton));
            saveButton.setOnClickListener(v -> {
                String nickname = input != null && input.getText() != null
                        ? input.getText().toString().trim()
                        : "";
                if (nickname.isEmpty()) {
                    Toast.makeText(getContext(), "Digite um nome para salvar no jogo.", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveNickname(nickname);
                dialog.dismiss();
            });
        }

        dialog.show();
        if (input != null) {
            input.requestFocus();
        }
    }
}
