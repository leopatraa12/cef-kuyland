package com.xyron.game.main.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.PickupStudioManager;

import java.util.List;
import java.util.Locale;

public class PickupCreatorOverlay {
    public interface Listener {
        float[] captureCurrentPlacement();

        boolean previewPickup(PickupStudioManager.PickupDefinition pickup);
    }

    private final Activity activity;
    private final Listener listener;
    private final FrameLayout root;
    private final TextView statusView;
    private final TextView exportPathView;
    private final TextView librarySummaryView;
    private final TextView emptyStateView;
    private final TextView buttonClose;
    private final TextView buttonCapture;
    private final TextView buttonPreview;
    private final TextView buttonReset;
    private final TextView buttonSave;
    private final TextView buttonExportAll;
    private final EditText inputLabel;
    private final EditText inputModel;
    private final EditText inputType;
    private final EditText inputWorld;
    private final EditText inputInterior;
    private final EditText inputX;
    private final EditText inputY;
    private final EditText inputZ;
    private final EditText inputAmount;
    private final LinearLayout listContainer;

    private String editingPickupId = "";

    public PickupCreatorOverlay(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;

        FrameLayout layout = (FrameLayout) activity.getLayoutInflater().inflate(
                R.layout.pickup_creator_overlay,
                null
        );
        this.root = layout;
        activity.addContentView(layout, new FrameLayout.LayoutParams(-1, -1));

        statusView = layout.findViewById(R.id.pickup_studio_status);
        exportPathView = layout.findViewById(R.id.pickup_studio_export_path);
        librarySummaryView = layout.findViewById(R.id.pickup_studio_library_summary);
        emptyStateView = layout.findViewById(R.id.pickup_studio_empty_state);
        buttonClose = layout.findViewById(R.id.pickup_studio_button_close);
        buttonCapture = layout.findViewById(R.id.pickup_studio_button_capture);
        buttonPreview = layout.findViewById(R.id.pickup_studio_button_preview);
        buttonReset = layout.findViewById(R.id.pickup_studio_button_reset);
        buttonSave = layout.findViewById(R.id.pickup_studio_button_save);
        buttonExportAll = layout.findViewById(R.id.pickup_studio_button_export_all);
        inputLabel = layout.findViewById(R.id.pickup_studio_input_label);
        inputModel = layout.findViewById(R.id.pickup_studio_input_model);
        inputType = layout.findViewById(R.id.pickup_studio_input_type);
        inputWorld = layout.findViewById(R.id.pickup_studio_input_world);
        inputInterior = layout.findViewById(R.id.pickup_studio_input_interior);
        inputX = layout.findViewById(R.id.pickup_studio_input_x);
        inputY = layout.findViewById(R.id.pickup_studio_input_y);
        inputZ = layout.findViewById(R.id.pickup_studio_input_z);
        inputAmount = layout.findViewById(R.id.pickup_studio_input_amount);
        listContainer = layout.findViewById(R.id.pickup_studio_list_container);

        root.setOnClickListener(v -> {
        });

        bindAction(buttonClose, this::hide);
        bindAction(buttonCapture, this::captureCurrentPosition);
        bindAction(buttonPreview, this::previewCurrentPickup);
        bindAction(buttonReset, () -> resetForm(true));
        bindAction(buttonSave, this::saveCurrentPickup);
        bindAction(buttonExportAll, this::exportAllPickups);

        resetForm(false);
        refreshUi();
    }

    public boolean isVisible() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void show() {
        PickupStudioManager.prepareWorkspace(activity);
        root.setVisibility(View.VISIBLE);
        root.bringToFront();
        refreshUi();
        if (TextUtils.isEmpty(inputX.getText()) || TextUtils.isEmpty(inputY.getText()) || TextUtils.isEmpty(inputZ.getText())) {
            captureCurrentPosition();
        }
        hideSoftKeyboard();
    }

    public void hide() {
        root.setVisibility(View.GONE);
        clearFocusAndKeyboard();
    }

    public void refreshUi() {
        refreshStatus();
        refreshPickupList();
        updateSaveButtonLabel();
    }

    private void refreshStatus() {
        List<PickupStudioManager.PickupDefinition> pickups = PickupStudioManager.loadPickups(activity);
        String status = pickups.isEmpty()
                ? "Nenhum pickup salvo ainda. Use a captura de posicao e monte o primeiro item."
                : pickups.size() + " pickup(s) salvo(s). O export .inc e atualizado pelo launcher.";
        statusView.setText(status);
        exportPathView.setText(PickupStudioManager.buildExportTargetLabel(activity));
        librarySummaryView.setText(
                pickups.isEmpty()
                        ? "Biblioteca vazia"
                        : pickups.size() + " item(ns) pronto(s) para exportar"
        );
    }

    private void refreshPickupList() {
        List<PickupStudioManager.PickupDefinition> pickups = PickupStudioManager.loadPickups(activity);
        listContainer.removeAllViews();
        emptyStateView.setVisibility(pickups.isEmpty() ? View.VISIBLE : View.GONE);

        if (pickups.isEmpty()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (PickupStudioManager.PickupDefinition pickup : pickups) {
            View itemView = inflater.inflate(R.layout.item_pickup_studio, listContainer, false);
            bindPickupItem(itemView, pickup);
            listContainer.addView(itemView);
        }
    }

    private void bindPickupItem(View itemView, PickupStudioManager.PickupDefinition pickup) {
        TextView nameView = itemView.findViewById(R.id.pickup_item_name);
        TextView badgeView = itemView.findViewById(R.id.pickup_item_badge);
        TextView metaView = itemView.findViewById(R.id.pickup_item_meta);
        TextView coordsView = itemView.findViewById(R.id.pickup_item_coords);
        TextView loadButton = itemView.findViewById(R.id.pickup_item_button_load);
        TextView previewButton = itemView.findViewById(R.id.pickup_item_button_preview);
        TextView copyButton = itemView.findViewById(R.id.pickup_item_button_copy);
        TextView deleteButton = itemView.findViewById(R.id.pickup_item_button_delete);
        View surface = itemView.findViewById(R.id.pickup_studio_item_surface);

        nameView.setText(pickup.label);
        badgeView.setText("M" + pickup.modelId);
        metaView.setText(
                "Tipo " + pickup.pickupType
                        + " | World " + pickup.worldId
                        + " | Interior " + pickup.interiorId
                        + " | Valor " + pickup.amount
        );
        coordsView.setText(
                "X " + formatFloat(pickup.x)
                        + " | Y " + formatFloat(pickup.y)
                        + " | Z " + formatFloat(pickup.z)
        );

        bindAction(surface, () -> loadPickupIntoForm(pickup));
        bindAction(loadButton, () -> loadPickupIntoForm(pickup));
        bindAction(previewButton, () -> previewPickup(pickup));
        bindAction(copyButton, () -> exportSinglePickup(pickup));
        bindAction(deleteButton, () -> removePickup(pickup));
    }

    private void loadPickupIntoForm(PickupStudioManager.PickupDefinition pickup) {
        if (pickup == null) {
            return;
        }
        editingPickupId = pickup.id;
        inputLabel.setText(pickup.label);
        inputModel.setText(String.valueOf(pickup.modelId));
        inputType.setText(String.valueOf(pickup.pickupType));
        inputWorld.setText(String.valueOf(pickup.worldId));
        inputInterior.setText(String.valueOf(pickup.interiorId));
        inputX.setText(formatFloat(pickup.x));
        inputY.setText(formatFloat(pickup.y));
        inputZ.setText(formatFloat(pickup.z));
        inputAmount.setText(String.valueOf(pickup.amount));
        updateSaveButtonLabel();
        showToast("Pickup carregado no editor.");
    }

    private void resetForm(boolean capturePosition) {
        editingPickupId = "";
        inputLabel.setText("");
        inputModel.setText("1242");
        inputType.setText("2");
        inputWorld.setText("0");
        inputInterior.setText("0");
        inputX.setText("");
        inputY.setText("");
        inputZ.setText("");
        inputAmount.setText("1");
        updateSaveButtonLabel();
        if (capturePosition) {
            captureCurrentPosition();
        }
    }

    private void updateSaveButtonLabel() {
        buttonSave.setText(TextUtils.isEmpty(editingPickupId) ? "Salvar pickup" : "Atualizar pickup");
    }

    private void captureCurrentPosition() {
        if (listener == null) {
            showToast("Captura indisponivel agora.");
            return;
        }
        float[] snapshot = listener.captureCurrentPlacement();
        if (snapshot == null || snapshot.length < 4) {
            showToast("Nao foi possivel ler a posicao atual do player.");
            return;
        }
        inputX.setText(formatFloat(snapshot[0]));
        inputY.setText(formatFloat(snapshot[1]));
        inputZ.setText(formatFloat(snapshot[2]));
        inputInterior.setText(String.valueOf(Math.max(0, Math.round(snapshot[3]))));
        showToast("Posicao atual capturada.");
    }

    private void previewCurrentPickup() {
        PickupStudioManager.PickupDefinition pickup = buildPickupFromInputs(false);
        if (pickup == null) {
            return;
        }
        previewPickup(pickup);
    }

    private void previewPickup(PickupStudioManager.PickupDefinition pickup) {
        if (listener == null) {
            showToast("Preview indisponivel.");
            return;
        }
        boolean previewed = listener.previewPickup(pickup.sanitized());
        showToast(previewed
                ? "Pickup mostrado no jogo."
                : "Nao foi possivel criar o preview agora.");
    }

    private void saveCurrentPickup() {
        PickupStudioManager.PickupDefinition pickup = buildPickupFromInputs(true);
        if (pickup == null) {
            return;
        }

        PickupStudioManager.SaveResult result = PickupStudioManager.savePickup(activity, pickup);
        if (!result.success || result.pickup == null) {
            showToast(result.message);
            return;
        }

        loadPickupIntoForm(result.pickup);
        refreshUi();
        previewPickup(result.pickup);
        String exportMessage = result.exportResult != null ? result.exportResult.message : "";
        showToast(result.message + (TextUtils.isEmpty(exportMessage) ? "" : " " + exportMessage));
    }

    private void removePickup(PickupStudioManager.PickupDefinition pickup) {
        PickupStudioManager.DeleteResult result = PickupStudioManager.deletePickup(activity, pickup == null ? "" : pickup.id);
        showToast(result.message);
        if (result.success) {
            if (pickup != null && pickup.id.equals(editingPickupId)) {
                resetForm(false);
            }
            refreshUi();
        }
    }

    private void exportSinglePickup(PickupStudioManager.PickupDefinition pickup) {
        if (pickup == null) {
            return;
        }
        String snippet = PickupStudioManager.buildSnippet(pickup);
        copyToClipboard("pickup_snippet", snippet);
        showToast("Snippet do pickup copiado para a area de transferencia.");
    }

    private void exportAllPickups() {
        PickupStudioManager.ExportResult result = PickupStudioManager.exportAll(activity);
        showToast(result.message);
        refreshStatus();
    }

    private PickupStudioManager.PickupDefinition buildPickupFromInputs(boolean requireLabel) {
        String label = valueOf(inputLabel);
        if (requireLabel && TextUtils.isEmpty(label)) {
            showToast("Digite um nome para o pickup.");
            return null;
        }

        Integer modelId = parseInteger(inputModel, "modelo");
        Integer pickupType = parseInteger(inputType, "tipo");
        Integer worldId = parseInteger(inputWorld, "world");
        Integer interiorId = parseInteger(inputInterior, "interior");
        Integer amount = parseInteger(inputAmount, "valor");
        Float x = parseFloat(inputX, "posicao X");
        Float y = parseFloat(inputY, "posicao Y");
        Float z = parseFloat(inputZ, "posicao Z");

        if (modelId == null || pickupType == null || worldId == null || interiorId == null || amount == null
                || x == null || y == null || z == null) {
            return null;
        }

        PickupStudioManager.PickupDefinition pickup = new PickupStudioManager.PickupDefinition();
        pickup.id = editingPickupId;
        pickup.label = TextUtils.isEmpty(label) ? "Pickup" : label;
        pickup.modelId = modelId;
        pickup.pickupType = pickupType;
        pickup.worldId = worldId;
        pickup.interiorId = interiorId;
        pickup.amount = amount;
        pickup.x = x;
        pickup.y = y;
        pickup.z = z;
        return pickup;
    }

    private Integer parseInteger(EditText field, String label) {
        String value = valueOf(field);
        if (TextUtils.isEmpty(value)) {
            showToast("Preencha " + label + ".");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            showToast("Valor invalido em " + label + ".");
            return null;
        }
    }

    private Float parseFloat(EditText field, String label) {
        String value = valueOf(field);
        if (TextUtils.isEmpty(value)) {
            showToast("Preencha " + label + ".");
            return null;
        }
        try {
            return Float.parseFloat(value.replace(',', '.'));
        } catch (Exception ignored) {
            showToast("Valor invalido em " + label + ".");
            return null;
        }
    }

    private String valueOf(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private void bindAction(View target, Runnable action) {
        if (target == null || action == null) {
            return;
        }
        target.setOnTouchListener(new ButtonAnimator(activity, target));
        target.setOnClickListener(v -> action.run());
    }

    private void copyToClipboard(String label, String content) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content));
    }

    private void clearFocusAndKeyboard() {
        View focus = activity.getCurrentFocus();
        if (focus != null) {
            focus.clearFocus();
        }
        hideSoftKeyboard();
    }

    private void hideSoftKeyboard() {
        View focus = activity.getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void showToast(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
