package com.xyron.game.launcher.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.HostFileManager;
import com.xyron.game.launcher.util.HostShellEngine;
import com.xyron.game.launcher.util.LocalHostManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class HostFilesFragment extends Fragment {
    private static final int REQUEST_IMPORT_GENERIC = 5011;
    private static final String STATE_CURRENT_PATH = "state_current_path";

    private TextView filesSummary;
    private TextView currentPath;
    private TextView emptyState;
    private TextView backButton;
    private TextView rootButton;
    private LinearLayout filesContainer;
    private String currentRelativePath = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentRelativePath = savedInstanceState.getString(STATE_CURRENT_PATH, "");
        }

        View root = inflater.inflate(R.layout.fragment_host_files, container, false);

        filesSummary = root.findViewById(R.id.host_files_summary);
        currentPath = root.findViewById(R.id.host_files_current_path);
        emptyState = root.findViewById(R.id.host_files_empty_state);
        backButton = root.findViewById(R.id.button_host_files_back);
        rootButton = root.findViewById(R.id.button_host_files_root);
        filesContainer = root.findViewById(R.id.host_files_list_container);

        bindAction(backButton, this::goToParentDirectory);
        bindAction(rootButton, this::goToRootDirectory);
        bindAction(root.findViewById(R.id.button_host_files_import), this::launchImportPicker);
        bindAction(root.findViewById(R.id.button_host_files_new_folder), () -> showCreateDialog(true));
        bindAction(root.findViewById(R.id.button_host_files_new_file), () -> showCreateDialog(false));
        bindAction(root.findViewById(R.id.button_host_refresh_files), this::refreshFiles);

        refreshFiles();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFiles();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_PATH, currentRelativePath);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_GENERIC
                || resultCode != android.app.Activity.RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri fileUri = data.getData();
        new Thread(() -> importSelectedFile(fileUri), "xyron-host-files-import").start();
    }

    private void bindAction(View button, Runnable action) {
        if (button == null || getContext() == null) {
            return;
        }
        button.setOnTouchListener(new ButtonAnimator(getContext(), button));
        button.setOnClickListener(v -> action.run());
    }

    private void refreshFiles() {
        if (getContext() == null || filesSummary == null || currentPath == null || emptyState == null || filesContainer == null) {
            return;
        }

        LocalHostManager.prepareSharedWorkspace(requireContext());
        currentRelativePath = HostFileManager.normalizeRelativePath(currentRelativePath);

        List<HostFileManager.BrowserEntry> entries = HostFileManager.listDirectory(currentRelativePath);
        filesSummary.setText(HostFileManager.buildDirectoryOverview(currentRelativePath, entries));
        currentPath.setText(HostFileManager.getDisplayPath(currentRelativePath));
        filesContainer.removeAllViews();

        boolean atRoot = TextUtils.isEmpty(currentRelativePath);
        if (backButton != null) {
            backButton.setEnabled(!atRoot);
            backButton.setAlpha(atRoot ? 0.45f : 1f);
        }
        if (rootButton != null) {
            rootButton.setEnabled(!atRoot);
            rootButton.setAlpha(atRoot ? 0.45f : 1f);
        }

        if (entries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (HostFileManager.BrowserEntry entry : entries) {
            View itemView = inflater.inflate(R.layout.item_host_file, filesContainer, false);
            bindFileItem(itemView, entry);
            filesContainer.addView(itemView);
        }
    }

    private void bindFileItem(View itemView, HostFileManager.BrowserEntry entry) {
        ImageView iconView = itemView.findViewById(R.id.host_file_icon);
        View iconSurface = itemView.findViewById(R.id.host_file_icon_surface);
        TextView typeBadge = itemView.findViewById(R.id.host_file_type_badge);
        TextView nameView = itemView.findViewById(R.id.host_file_name);
        TextView metaView = itemView.findViewById(R.id.host_file_meta);
        TextView pathView = itemView.findViewById(R.id.host_file_path);
        TextView openAction = itemView.findViewById(R.id.host_file_open_action);
        TextView renameAction = itemView.findViewById(R.id.host_file_rename_action);
        TextView deleteAction = itemView.findViewById(R.id.host_file_delete_action);
        View surface = itemView.findViewById(R.id.host_file_item_surface);

        if (iconView != null) {
            iconView.setImageResource(entry.directory ? R.drawable.ic_browser_folder : R.drawable.ic_browser_file);
        }
        if (iconSurface != null) {
            iconSurface.setBackgroundResource(entry.directory
                    ? R.drawable.file_browser_icon_surface_folder
                    : R.drawable.file_browser_icon_surface_file);
        }

        typeBadge.setText(resolveTypeLabel(entry));
        typeBadge.setBackgroundResource(entry.directory
                ? R.drawable.file_browser_chip_folder
                : R.drawable.file_browser_chip_file);
        nameView.setText(entry.displayName);
        metaView.setText(buildMeta(entry));
        pathView.setText(entry.relativePath);
        openAction.setText(entry.directory ? "Abrir pasta" : (entry.editableText ? "Editar" : "Detalhes"));

        bindAction(surface, () -> openEntry(entry));
        bindAction(openAction, () -> openEntry(entry));
        bindAction(renameAction, () -> showRenameDialog(entry));
        bindAction(deleteAction, () -> confirmDelete(entry));
    }

    private String buildMeta(HostFileManager.BrowserEntry entry) {
        if (entry.directory) {
            return entry.category + " | " + entry.childCount + " item(ns) nesta pasta";
        }

        String editMode = entry.editableText ? "Editavel no launcher" : "Binario / leitura";
        return entry.category + " | " + HostFileManager.formatSize(entry.sizeBytes) + " | " + editMode;
    }

    private String resolveTypeLabel(HostFileManager.BrowserEntry entry) {
        if (entry == null) {
            return "ITEM";
        }
        if (entry.directory) {
            return "PASTA";
        }
        String name = entry.displayName == null ? "" : entry.displayName;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "ARQUIVO";
        }
        String extension = name.substring(dot + 1).toUpperCase(Locale.US);
        if (extension.length() > 8) {
            extension = extension.substring(0, 8);
        }
        return extension;
    }

    private void openEntry(HostFileManager.BrowserEntry entry) {
        if (entry == null) {
            return;
        }

        if (entry.directory) {
            currentRelativePath = entry.relativePath;
            refreshFiles();
            return;
        }

        if (!HostFileManager.isEditableText(entry)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(entry.displayName)
                    .setMessage("Categoria: " + entry.category
                            + "\nTamanho: " + HostFileManager.formatSize(entry.sizeBytes)
                            + "\nCaminho: " + entry.relativePath
                            + "\n\nEsse arquivo nao e editavel como texto dentro do launcher.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return;
        }

        try {
            String content = HostFileManager.readEditableText(entry.relativePath);
            showEditorDialog(entry, content);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Nao foi possivel abrir o arquivo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToParentDirectory() {
        currentRelativePath = HostFileManager.getParentRelativePath(currentRelativePath);
        refreshFiles();
    }

    private void goToRootDirectory() {
        currentRelativePath = "";
        refreshFiles();
    }

    private void launchImportPicker() {
        if (getContext() == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_GENERIC);
    }

    private void importSelectedFile(Uri fileUri) {
        if (getContext() == null || fileUri == null) {
            return;
        }

        boolean prepared = LocalHostManager.prepareSharedWorkspace(requireContext());
        if (!prepared) {
            postToast("Nao foi possivel preparar a pasta do host antes da importacao.");
            return;
        }

        String displayName = resolveDisplayName(fileUri);
        if (TextUtils.isEmpty(displayName)) {
            displayName = "arquivo-importado";
        }

        try {
            File targetFile = HostFileManager.resolveChildPath(currentRelativePath, displayName);
            if (targetFile == null) {
                postToast("Nao foi possivel importar para esta pasta.");
                return;
            }

            copyUriToFile(fileUri, targetFile);
            LocalHostManager.removePlaceholderFiles(targetFile.getParentFile());

            StringBuilder message = new StringBuilder();
            message.append("Arquivo importado em ").append(HostFileManager.getDisplayPath(currentRelativePath));

            if (isGamemodeImport(targetFile)) {
                boolean updated = LocalHostManager.activateGamemode(targetFile.getName());
                if (updated) {
                    message.append("\nGM principal atualizada.");
                }
            }

            if (HostShellEngine.isHostRunning()) {
                message.append("\nReinicie o host para aplicar.");
            }

            String finalMessage = message.toString();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), finalMessage, Toast.LENGTH_LONG).show();
                    refreshFiles();
                });
            }
        } catch (Exception e) {
            postToast("Falha ao importar o arquivo.");
        }
    }

    private boolean isGamemodeImport(File targetFile) {
        if (targetFile == null || !targetFile.getName().toLowerCase(Locale.US).endsWith(".amx")) {
            return false;
        }
        return HostFileManager.isUnderDirectory(targetFile, LocalHostManager.getSharedGamemodesDirectory());
    }

    private void showCreateDialog(boolean folder) {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_host_file_create,
                null,
                false
        );
        TextView badgeView = dialogView.findViewById(R.id.dialog_host_create_badge);
        TextView titleView = dialogView.findViewById(R.id.dialog_host_create_title);
        TextView pathView = dialogView.findViewById(R.id.dialog_host_create_path);
        EditText inputView = dialogView.findViewById(R.id.dialog_host_create_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_host_create_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_host_create_save);

        badgeView.setText(folder ? "Nova pasta" : "Novo arquivo");
        titleView.setText(folder ? "Criar pasta nesta pasta atual" : "Criar arquivo nesta pasta atual");
        pathView.setText(HostFileManager.getDisplayPath(currentRelativePath));
        inputView.setHint(folder ? "Nome da pasta" : "Nome do arquivo");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        bindAction(cancelButton, dialog::dismiss);
        bindAction(saveButton, () -> {
            String requestedName = inputView.getText() == null ? "" : inputView.getText().toString().trim();
            HostFileManager.FileActionResult result = folder
                    ? HostFileManager.createDirectory(currentRelativePath, requestedName)
                    : HostFileManager.createEmptyFile(currentRelativePath, requestedName);
            if (!result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(), buildApplyMessage(result.message), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshFiles();
        });

        dialog.show();
    }

    private void showEditorDialog(HostFileManager.BrowserEntry entry, String content) {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_host_file_editor,
                null,
                false
        );
        TextView titleView = dialogView.findViewById(R.id.dialog_host_file_title);
        TextView pathView = dialogView.findViewById(R.id.dialog_host_file_path);
        EditText editorView = dialogView.findViewById(R.id.dialog_host_file_editor);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_host_file_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_host_file_save);

        titleView.setText(entry.displayName);
        pathView.setText(entry.relativePath);
        editorView.setText(content);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        bindAction(cancelButton, dialog::dismiss);
        bindAction(saveButton, () -> {
            String updated = editorView.getText() == null ? "" : editorView.getText().toString();
            boolean saved = HostFileManager.writeEditableText(entry.relativePath, updated);
            if (!saved) {
                Toast.makeText(requireContext(), "Nao foi possivel salvar o arquivo.", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(), buildApplyMessage("Arquivo salvo."), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshFiles();
        });

        dialog.show();
    }

    private void showRenameDialog(HostFileManager.BrowserEntry entry) {
        if (getContext() == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_host_file_rename,
                null,
                false
        );
        TextView badgeView = dialogView.findViewById(R.id.dialog_host_rename_badge);
        TextView titleView = dialogView.findViewById(R.id.dialog_host_rename_title);
        TextView pathView = dialogView.findViewById(R.id.dialog_host_rename_path);
        EditText input = dialogView.findViewById(R.id.dialog_host_rename_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_host_rename_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_host_rename_save);

        badgeView.setText(entry.directory ? "Renomear pasta" : "Renomear arquivo");
        titleView.setText(entry.directory ? "Trocar nome da pasta" : "Trocar nome do arquivo");
        pathView.setText(entry.relativePath);
        input.setText(entry.displayName);
        input.setSelection(entry.displayName.length());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        bindAction(cancelButton, dialog::dismiss);
        bindAction(saveButton, () -> {
            String newName = input.getText() == null ? "" : input.getText().toString().trim();
            HostFileManager.RenameResult result = HostFileManager.renameEntry(entry.relativePath, newName);
            if (!result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(), buildApplyMessage(result.message), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshFiles();
        });

        dialog.show();
    }

    private void confirmDelete(HostFileManager.BrowserEntry entry) {
        if (getContext() == null) {
            return;
        }

        String label = entry.directory ? "pasta" : "arquivo";
        new AlertDialog.Builder(requireContext())
                .setTitle("Excluir " + label)
                .setMessage("Excluir " + entry.displayName + "?\n\n" + entry.relativePath)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (dialog, which) -> {
                    HostFileManager.FileActionResult result = HostFileManager.deleteEntry(entry.relativePath);
                    if (!result.success) {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(requireContext(), buildApplyMessage(result.message), Toast.LENGTH_SHORT).show();
                    refreshFiles();
                })
                .show();
    }

    private void copyUriToFile(Uri fileUri, File targetFile) throws Exception {
        if (targetFile == null || getContext() == null) {
            throw new IllegalArgumentException("Destino de importacao invalido.");
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Nao foi possivel criar a pasta de destino.");
        }

        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(fileUri);
             FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Nao foi possivel abrir o arquivo selecionado.");
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private String resolveDisplayName(Uri fileUri) {
        if (getContext() == null || fileUri == null) {
            return "";
        }

        Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(
                    fileUri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fileUri.getLastPathSegment() == null ? "" : fileUri.getLastPathSegment();
    }

    private String buildApplyMessage(String baseMessage) {
        if (HostShellEngine.isHostRunning()) {
            return baseMessage + " Reinicie o host para aplicar tudo.";
        }
        return baseMessage;
    }

    private void postToast(String message) {
        if (TextUtils.isEmpty(message) || getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show());
    }
}
