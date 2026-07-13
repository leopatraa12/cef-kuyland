package com.xyron.game.launcher.fragments;

import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.HostFileManager;
import com.xyron.game.launcher.util.PawnCompilerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditorFragment extends Fragment {
    private static final int REQUEST_IMPORT_EDITOR_FILE = 6101;
    private static final String STATE_CURRENT_PATH = "editor_current_path";
    private static final String STATE_SELECTED_FILE = "editor_selected_file";

    private TextView compilerSummary;
    private TextView currentPath;
    private TextView currentFileName;
    private TextView currentFilePath;
    private TextView compileOutput;
    private TextView emptyState;
    private TextView buttonBack;
    private TextView buttonRoot;
    private TextView buttonSave;
    private TextView buttonCompile;
    private EditText editorInput;
    private LinearLayout filesContainer;

    private String currentRelativePath = PawnCompilerManager.getProjectsRelativePath();
    private String selectedFilePath = "";
    private volatile boolean compileInFlight;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentRelativePath = savedInstanceState.getString(STATE_CURRENT_PATH, PawnCompilerManager.getProjectsRelativePath());
            selectedFilePath = savedInstanceState.getString(STATE_SELECTED_FILE, "");
        }

        View root = inflater.inflate(R.layout.fragment_editor, container, false);
        compilerSummary = root.findViewById(R.id.editor_compiler_summary);
        currentPath = root.findViewById(R.id.editor_current_path);
        currentFileName = root.findViewById(R.id.editor_current_file_name);
        currentFilePath = root.findViewById(R.id.editor_current_file_path);
        compileOutput = root.findViewById(R.id.editor_compile_output);
        emptyState = root.findViewById(R.id.editor_empty_state);
        buttonBack = root.findViewById(R.id.button_editor_back);
        buttonRoot = root.findViewById(R.id.button_editor_root);
        buttonSave = root.findViewById(R.id.button_editor_save);
        buttonCompile = root.findViewById(R.id.button_editor_compile);
        editorInput = root.findViewById(R.id.editor_input);
        filesContainer = root.findViewById(R.id.editor_files_list_container);

        bindAction(root.findViewById(R.id.button_editor_projects), () -> openDirectory(PawnCompilerManager.getProjectsRelativePath()));
        bindAction(root.findViewById(R.id.button_editor_gamemodes), () -> openDirectory(PawnCompilerManager.getServerGamemodesRelativePath()));
        bindAction(root.findViewById(R.id.button_editor_includes), () -> openDirectory(PawnCompilerManager.getIncludeRelativePath()));
        bindAction(root.findViewById(R.id.button_editor_refresh), this::refreshAll);
        bindAction(root.findViewById(R.id.button_editor_import), this::launchImportPicker);
        bindAction(root.findViewById(R.id.button_editor_new_script), () -> showCreateSourceDialog(false));
        bindAction(root.findViewById(R.id.button_editor_new_include), () -> showCreateSourceDialog(true));
        bindAction(buttonBack, this::goToParentDirectory);
        bindAction(buttonRoot, () -> openDirectory(PawnCompilerManager.getProjectsRelativePath()));
        bindAction(buttonSave, this::saveCurrentFile);
        bindAction(buttonCompile, this::compileCurrentFile);

        refreshAll();
        if (!TextUtils.isEmpty(selectedFilePath)) {
            loadSelectedFile();
        }
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_CURRENT_PATH, currentRelativePath);
        outState.putString(STATE_SELECTED_FILE, selectedFilePath);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_EDITOR_FILE
                || resultCode != Activity.RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri fileUri = data.getData();
        new Thread(() -> importSelectedFile(fileUri), "xyron-editor-import").start();
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
        PawnCompilerManager.prepareWorkspace(requireContext());
        refreshSummary();
        refreshFileList();
        refreshEditorState();
    }

    private void refreshSummary() {
        if (compilerSummary == null || currentPath == null) {
            return;
        }
        compilerSummary.setText(PawnCompilerManager.buildCompilerSummary(requireContext()));
        currentPath.setText(HostFileManager.getDisplayPath(currentRelativePath));
    }

    private void refreshFileList() {
        if (filesContainer == null || emptyState == null) {
            return;
        }

        currentRelativePath = HostFileManager.normalizeRelativePath(currentRelativePath);
        List<HostFileManager.BrowserEntry> rawEntries = HostFileManager.listDirectory(currentRelativePath);
        List<HostFileManager.BrowserEntry> entries = filterEditorEntries(rawEntries);

        filesContainer.removeAllViews();
        boolean atProjectsRoot = PawnCompilerManager.getProjectsRelativePath().equals(currentRelativePath);
        if (buttonBack != null) {
            buttonBack.setEnabled(!atProjectsRoot);
            buttonBack.setAlpha(atProjectsRoot ? 0.45f : 1f);
        }
        if (buttonRoot != null) {
            buttonRoot.setEnabled(!atProjectsRoot);
            buttonRoot.setAlpha(atProjectsRoot ? 0.45f : 1f);
        }

        if (entries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }
        emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (HostFileManager.BrowserEntry entry : entries) {
            View itemView = inflater.inflate(R.layout.item_editor_file, filesContainer, false);
            bindEditorFileItem(itemView, entry);
            filesContainer.addView(itemView);
        }
    }

    private void bindEditorFileItem(View itemView, HostFileManager.BrowserEntry entry) {
        TextView typeBadge = itemView.findViewById(R.id.editor_file_type_badge);
        TextView nameView = itemView.findViewById(R.id.editor_file_name);
        TextView metaView = itemView.findViewById(R.id.editor_file_meta);
        TextView pathView = itemView.findViewById(R.id.editor_file_path);
        TextView openAction = itemView.findViewById(R.id.editor_file_open_action);
        TextView compileAction = itemView.findViewById(R.id.editor_file_compile_action);
        TextView renameAction = itemView.findViewById(R.id.editor_file_rename_action);
        TextView deleteAction = itemView.findViewById(R.id.editor_file_delete_action);
        View surface = itemView.findViewById(R.id.editor_file_item_surface);

        typeBadge.setText(buildTypeBadge(entry));
        nameView.setText(entry.displayName);
        metaView.setText(buildMeta(entry));
        pathView.setText(entry.relativePath);
        openAction.setText(entry.directory ? "Entrar" : "Abrir");
        compileAction.setVisibility(entry.directory || !PawnCompilerManager.isCompilableFile(entry.relativePath)
                ? View.GONE
                : View.VISIBLE);

        bindAction(surface, () -> openEntry(entry));
        bindAction(openAction, () -> openEntry(entry));
        bindAction(renameAction, () -> showRenameDialog(entry));
        bindAction(deleteAction, () -> confirmDelete(entry));
        if (compileAction.getVisibility() == View.VISIBLE) {
            bindAction(compileAction, () -> compileEntry(entry.relativePath));
        }
    }

    private void openEntry(HostFileManager.BrowserEntry entry) {
        if (entry == null) {
            return;
        }
        if (entry.directory) {
            openDirectory(entry.relativePath);
            return;
        }

        if (!HostFileManager.isEditableText(entry)) {
            Toast.makeText(requireContext(), "Esse arquivo nao e editavel como texto no editor.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String content = HostFileManager.readEditableText(entry.relativePath);
            selectedFilePath = entry.relativePath;
            editorInput.setText(content);
            editorInput.setSelection(editorInput.getText() == null ? 0 : editorInput.getText().length());
            compileOutput.setText("Arquivo aberto: " + entry.displayName);
            refreshEditorState();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Nao foi possivel abrir o arquivo.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSelectedFile() {
        if (TextUtils.isEmpty(selectedFilePath)) {
            refreshEditorState();
            return;
        }
        File selectedFile = HostFileManager.resolveManagedPath(selectedFilePath);
        if (selectedFile == null || !selectedFile.exists() || !selectedFile.isFile()) {
            selectedFilePath = "";
            refreshEditorState();
            return;
        }
        try {
            editorInput.setText(HostFileManager.readEditableText(selectedFilePath));
        } catch (Exception ignored) {
            selectedFilePath = "";
        }
        refreshEditorState();
    }

    private void openDirectory(String relativePath) {
        currentRelativePath = HostFileManager.normalizeRelativePath(relativePath);
        refreshSummary();
        refreshFileList();
    }

    private void goToParentDirectory() {
        String parent = HostFileManager.getParentRelativePath(currentRelativePath);
        if (TextUtils.isEmpty(parent)) {
            openDirectory(PawnCompilerManager.getProjectsRelativePath());
            return;
        }
        openDirectory(parent);
    }

    private void saveCurrentFile() {
        if (TextUtils.isEmpty(selectedFilePath)) {
            Toast.makeText(requireContext(), "Abra um arquivo para salvar.", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = editorInput.getText() == null ? "" : editorInput.getText().toString();
        boolean saved = HostFileManager.writeEditableText(selectedFilePath, content);
        if (!saved) {
            Toast.makeText(requireContext(), "Nao foi possivel salvar o arquivo.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Arquivo salvo.", Toast.LENGTH_SHORT).show();
        refreshEditorState();
        refreshFileList();
    }

    private void compileCurrentFile() {
        compileEntry(selectedFilePath);
    }

    private void compileEntry(String relativePath) {
        if (compileInFlight) {
            Toast.makeText(requireContext(), "Ja existe uma compilacao em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(relativePath)) {
            Toast.makeText(requireContext(), "Abra um .pwn antes de compilar.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PawnCompilerManager.isCompilableFile(relativePath)) {
            Toast.makeText(requireContext(), "Selecione um arquivo .pwn para compilar.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (relativePath.equals(selectedFilePath)) {
            saveCurrentFile();
        }

        compileInFlight = true;
        refreshEditorState();
        compileOutput.setText("Compilando...");

        new Thread(() -> {
            PawnCompilerManager.CompileResult result = PawnCompilerManager.compile(requireContext(), relativePath);
            if (getActivity() == null) {
                compileInFlight = false;
                return;
            }
            getActivity().runOnUiThread(() -> {
                compileInFlight = false;
                compileOutput.setText(result.output);
                refreshEditorState();
                refreshFileList();
                if (result.success) {
                    Toast.makeText(requireContext(), "Compilacao concluida.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "A compilacao retornou erro.", Toast.LENGTH_SHORT).show();
                }
            });
        }, "xyron-pawn-compile").start();
    }

    private void refreshEditorState() {
        if (editorInput == null || currentFileName == null || currentFilePath == null) {
            return;
        }

        boolean fileSelected = !TextUtils.isEmpty(selectedFilePath);
        File file = fileSelected ? HostFileManager.resolveManagedPath(selectedFilePath) : null;
        boolean validFile = file != null && file.exists() && file.isFile();
        if (!validFile) {
            selectedFilePath = "";
            fileSelected = false;
        }

        currentFileName.setText(fileSelected ? file.getName() : "Nenhum arquivo aberto");
        currentFilePath.setText(fileSelected ? selectedFilePath : "Abra um .pwn ou .inc para editar e compilar.");
        editorInput.setEnabled(fileSelected);
        editorInput.setAlpha(fileSelected ? 1f : 0.72f);
        if (!fileSelected && TextUtils.isEmpty(editorInput.getText())) {
            editorInput.setHint("Abra um script, include ou config para editar aqui.");
        }

        boolean canCompile = fileSelected && PawnCompilerManager.isCompilableFile(selectedFilePath) && !compileInFlight;
        buttonSave.setEnabled(fileSelected && !compileInFlight);
        buttonSave.setAlpha(fileSelected && !compileInFlight ? 1f : 0.45f);
        buttonCompile.setEnabled(canCompile);
        buttonCompile.setAlpha(canCompile ? 1f : 0.45f);
        buttonCompile.setText(compileInFlight ? "Compilando..." : "Compilar .pwn");
    }

    private void launchImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_EDITOR_FILE);
    }

    private void importSelectedFile(Uri fileUri) {
        if (fileUri == null || getContext() == null) {
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
            postToast("Arquivo importado em " + HostFileManager.getDisplayPath(currentRelativePath));
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    refreshFileList();
                    if (HostFileManager.isEditableText(targetFile)) {
                        selectedFilePath = HostFileManager.normalizeRelativePath(currentRelativePath + "/" + targetFile.getName());
                        loadSelectedFile();
                    }
                });
            }
        } catch (Exception e) {
            postToast("Falha ao importar o arquivo para o editor.");
        }
    }

    private void showCreateSourceDialog(boolean includeFile) {
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

        badgeView.setText(includeFile ? "Novo include" : "Novo script");
        titleView.setText(includeFile ? "Criar include nesta pasta" : "Criar script Pawn nesta pasta");
        pathView.setText(HostFileManager.getDisplayPath(currentRelativePath));
        inputView.setHint(includeFile ? "nome_do_include.inc" : "nome_do_script.pwn");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        bindAction(cancelButton, dialog::dismiss);
        bindAction(saveButton, () -> {
            String requestedName = inputView.getText() == null ? "" : inputView.getText().toString().trim();
            String fileName = appendRequiredExtension(requestedName, includeFile ? ".inc" : ".pwn");
            HostFileManager.FileActionResult result = HostFileManager.createEmptyFile(currentRelativePath, fileName);
            if (!result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
                return;
            }

            HostFileManager.writeEditableText(result.relativePath, includeFile
                    ? buildIncludeTemplate(fileName)
                    : buildPawnTemplate(fileName));
            dialog.dismiss();
            selectedFilePath = result.relativePath;
            currentRelativePath = HostFileManager.getParentRelativePath(result.relativePath);
            if (TextUtils.isEmpty(currentRelativePath)) {
                currentRelativePath = PawnCompilerManager.getProjectsRelativePath();
            }
            refreshAll();
            loadSelectedFile();
            Toast.makeText(requireContext(), "Arquivo criado no editor.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showRenameDialog(HostFileManager.BrowserEntry entry) {
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
            HostFileManager.RenameResult result = HostFileManager.renameEntry(
                    entry.relativePath,
                    input.getText() == null ? "" : input.getText().toString().trim()
            );
            if (!result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show();
                return;
            }

            if (entry.relativePath.equals(selectedFilePath)) {
                selectedFilePath = result.newRelativePath;
            }
            dialog.dismiss();
            refreshAll();
            loadSelectedFile();
            Toast.makeText(requireContext(), "Item renomeado.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void confirmDelete(HostFileManager.BrowserEntry entry) {
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

                    if (entry.relativePath.equals(selectedFilePath)) {
                        selectedFilePath = "";
                        editorInput.setText("");
                    }
                    refreshAll();
                    Toast.makeText(requireContext(), "Item removido.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private List<HostFileManager.BrowserEntry> filterEditorEntries(List<HostFileManager.BrowserEntry> rawEntries) {
        ArrayList<HostFileManager.BrowserEntry> filtered = new ArrayList<>();
        if (rawEntries == null) {
            return filtered;
        }

        for (HostFileManager.BrowserEntry entry : rawEntries) {
            if (entry == null) {
                continue;
            }
            if (entry.directory) {
                filtered.add(entry);
                continue;
            }

            String extension = extensionOf(entry.displayName);
            if (entry.editableText
                    || "amx".equals(extension)
                    || "log".equals(extension)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private String buildTypeBadge(HostFileManager.BrowserEntry entry) {
        if (entry.directory) {
            return "PASTA";
        }
        String extension = extensionOf(entry.displayName);
        if (TextUtils.isEmpty(extension)) {
            return "ARQUIVO";
        }
        return extension.toUpperCase(Locale.US);
    }

    private String buildMeta(HostFileManager.BrowserEntry entry) {
        if (entry.directory) {
            return entry.category + " | " + entry.childCount + " item(ns)";
        }
        String mode = PawnCompilerManager.isCompilableFile(entry.relativePath)
                ? "Compilavel"
                : (entry.editableText ? "Editavel" : "Leitura");
        return entry.category + " | " + HostFileManager.formatSize(entry.sizeBytes) + " | " + mode;
    }

    private String buildPawnTemplate(String fileName) {
        String baseName = fileName == null ? "meu_script" : fileName.replace(".pwn", "");
        return "#include <open.mp>\n\n"
                + "main() {}\n\n"
                + "public OnGameModeInit()\n"
                + "{\n"
                + "    print(\"" + baseName + " carregado\");\n"
                + "    return 1;\n"
                + "}\n";
    }

    private String buildIncludeTemplate(String fileName) {
        String guard = fileName == null ? "_NOVO_INCLUDE_" : fileName.toUpperCase(Locale.US).replace('.', '_');
        return "#if defined _" + guard + "\n"
                + "    #endinput\n"
                + "#endif\n"
                + "#define _" + guard + "\n\n";
    }

    private String appendRequiredExtension(String requestedName, String extension) {
        if (TextUtils.isEmpty(requestedName)) {
            return "";
        }
        String trimmed = requestedName.trim();
        if (trimmed.toLowerCase(Locale.US).endsWith(extension)) {
            return trimmed;
        }
        return trimmed + extension;
    }

    private String extensionOf(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.US);
    }

    private void copyUriToFile(Uri fileUri, File targetFile) throws Exception {
        if (targetFile == null) {
            throw new IllegalArgumentException("Destino invalido.");
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Nao foi possivel criar a pasta de destino.");
        }

        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(fileUri);
             FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Falha ao abrir o arquivo selecionado.");
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

    private void postToast(String message) {
        if (TextUtils.isEmpty(message) || getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show());
    }
}
