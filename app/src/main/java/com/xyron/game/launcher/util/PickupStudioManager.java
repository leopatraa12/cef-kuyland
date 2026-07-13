package com.xyron.game.launcher.util;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PickupStudioManager {
    private static final String STUDIO_DIR_NAME = "pickup-studio";
    private static final String DATA_FILE_NAME = "xyron_pickups.json";
    private static final String EXPORT_FILE_NAME = "xyron_pickups_export.inc";
    private static final String README_FILE_NAME = "README.txt";

    private PickupStudioManager() {
    }

    public static synchronized boolean prepareWorkspace(Context context) {
        if (context == null) {
            return false;
        }
        if (!LocalHostManager.prepareSharedWorkspace(context)) {
            return false;
        }

        File studioDirectory = getStudioDirectory(context);
        File dataFile = getDataFile(context);
        File exportFile = getExportFile(context);
        if (studioDirectory == null || dataFile == null || exportFile == null) {
            return false;
        }
        if (!studioDirectory.exists() && !studioDirectory.mkdirs()) {
            return false;
        }

        writeTextIfMissing(new File(studioDirectory, README_FILE_NAME), buildReadme());
        if (!dataFile.exists()) {
            writeText(dataFile, "[]\n");
        }
        exportAll(context);
        return true;
    }

    public static synchronized List<PickupDefinition> loadPickups(Context context) {
        ArrayList<PickupDefinition> pickups = new ArrayList<>();
        File dataFile = getDataFile(context);
        if (dataFile == null || !dataFile.exists() || !dataFile.isFile()) {
            return pickups;
        }

        try {
            String raw = readText(dataFile);
            if (TextUtils.isEmpty(raw)) {
                return pickups;
            }
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i += 1) {
                JSONObject entry = array.optJSONObject(i);
                PickupDefinition pickup = PickupDefinition.fromJson(entry);
                if (pickup != null) {
                    pickups.add(pickup);
                }
            }
        } catch (Exception ignored) {
            pickups.clear();
        }
        return pickups;
    }

    public static synchronized SaveResult savePickup(Context context, PickupDefinition input) {
        if (context == null) {
            return SaveResult.error("Contexto indisponivel.");
        }
        if (input == null) {
            return SaveResult.error("Pickup invalido.");
        }
        if (!prepareWorkspace(context)) {
            return SaveResult.error("Nao foi possivel preparar a workspace do host.");
        }

        PickupDefinition sanitized = input.sanitized();
        List<PickupDefinition> pickups = loadPickups(context);
        boolean updated = false;
        long now = System.currentTimeMillis();

        if (TextUtils.isEmpty(sanitized.id)) {
            sanitized.id = "pickup_" + now;
            sanitized.createdAt = now;
        } else if (sanitized.createdAt <= 0L) {
            sanitized.createdAt = now;
        }
        sanitized.updatedAt = now;

        for (int i = 0; i < pickups.size(); i += 1) {
            PickupDefinition current = pickups.get(i);
            if (current != null && sanitized.id.equals(current.id)) {
                sanitized.createdAt = current.createdAt > 0L ? current.createdAt : sanitized.createdAt;
                pickups.set(i, sanitized);
                updated = true;
                break;
            }
        }

        if (!updated) {
            pickups.add(sanitized);
        }

        if (!writePickupList(context, pickups)) {
            return SaveResult.error("Nao foi possivel salvar este pickup.");
        }

        ExportResult exportResult = exportAll(context);
        return new SaveResult(
                true,
                updated ? "Pickup atualizado." : "Pickup criado.",
                sanitized,
                exportResult
        );
    }

    public static synchronized DeleteResult deletePickup(Context context, String pickupId) {
        if (context == null) {
            return DeleteResult.error("Contexto indisponivel.");
        }
        if (TextUtils.isEmpty(pickupId)) {
            return DeleteResult.error("Pickup invalido.");
        }

        List<PickupDefinition> pickups = loadPickups(context);
        boolean removed = false;
        for (int i = pickups.size() - 1; i >= 0; i -= 1) {
            PickupDefinition pickup = pickups.get(i);
            if (pickup != null && pickupId.equals(pickup.id)) {
                pickups.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            return DeleteResult.error("Pickup nao encontrado.");
        }
        if (!writePickupList(context, pickups)) {
            return DeleteResult.error("Nao foi possivel remover este pickup.");
        }
        ExportResult exportResult = exportAll(context);
        return new DeleteResult(true, "Pickup removido.", exportResult);
    }

    public static synchronized ExportResult exportAll(Context context) {
        if (context == null) {
            return ExportResult.error("Contexto indisponivel.");
        }
        if (!LocalHostManager.prepareSharedWorkspace(context)) {
            return ExportResult.error("Nao foi possivel preparar o host para exportar.");
        }

        List<PickupDefinition> pickups = loadPickups(context);
        File exportFile = getExportFile(context);
        if (exportFile == null) {
            return ExportResult.error("Arquivo de exportacao indisponivel.");
        }
        if (!writeText(exportFile, buildExportFileContent(pickups))) {
            return ExportResult.error("Nao foi possivel gerar o arquivo de exportacao.");
        }
        return new ExportResult(
                true,
                pickups.isEmpty()
                        ? "Arquivo de exportacao atualizado sem pickups salvos."
                        : pickups.size() + " pickup(s) exportado(s).",
                exportFile.getAbsolutePath(),
                pickups.size()
        );
    }

    public static String buildSnippet(PickupDefinition pickup) {
        if (pickup == null) {
            return "";
        }
        PickupDefinition value = pickup.sanitized();
        String label = TextUtils.isEmpty(value.label) ? "Pickup" : value.label.trim();
        String variable = sanitizePawnIdentifier(label);
        return "CreatePickup("
                + value.modelId + ", "
                + value.pickupType + ", "
                + formatFloat(value.x) + ", "
                + formatFloat(value.y) + ", "
                + formatFloat(value.z) + ", "
                + value.worldId + ");"
                + " // " + label
                + " | interior " + value.interiorId
                + " | valor " + value.amount
                + " | id " + variable;
    }

    public static String buildExportTargetLabel(Context context) {
        File exportFile = getExportFile(context);
        return exportFile == null ? "" : exportFile.getAbsolutePath();
    }

    public static File getStudioDirectory(Context context) {
        File scriptfilesDir = LocalHostManager.getSharedScriptfilesDirectory();
        if (scriptfilesDir == null) {
            return null;
        }
        return new File(scriptfilesDir, STUDIO_DIR_NAME);
    }

    public static File getDataFile(Context context) {
        File studioDirectory = getStudioDirectory(context);
        return studioDirectory == null ? null : new File(studioDirectory, DATA_FILE_NAME);
    }

    public static File getExportFile(Context context) {
        File scriptfilesDir = LocalHostManager.getSharedScriptfilesDirectory();
        return scriptfilesDir == null ? null : new File(scriptfilesDir, EXPORT_FILE_NAME);
    }

    private static boolean writePickupList(Context context, List<PickupDefinition> pickups) {
        File dataFile = getDataFile(context);
        if (dataFile == null) {
            return false;
        }

        JSONArray array = new JSONArray();
        if (pickups != null) {
            for (PickupDefinition pickup : pickups) {
                if (pickup != null) {
                    array.put(pickup.toJson());
                }
            }
        }
        try {
            return writeText(dataFile, array.toString(2) + "\n");
        } catch (Exception ignored) {
            return writeText(dataFile, array.toString() + "\n");
        }
    }

    private static String buildExportFileContent(List<PickupDefinition> pickups) {
        StringBuilder builder = new StringBuilder();
        builder.append("// Xyron Pickup Studio\n");
        builder.append("// Gerado automaticamente pelo launcher.\n");
        builder.append("// Arquivo base: server/scriptfiles/").append(STUDIO_DIR_NAME).append("/").append(DATA_FILE_NAME).append("\n\n");
        builder.append("stock Xyron_LoadStudioPickups()\n");
        builder.append("{\n");
        if (pickups == null || pickups.isEmpty()) {
            builder.append("    // Nenhum pickup salvo ainda.\n");
            builder.append("    return 1;\n");
            builder.append("}\n");
            return builder.toString();
        }

        for (PickupDefinition pickup : pickups) {
            builder.append("    ")
                    .append(buildSnippet(pickup))
                    .append("\n");
        }
        builder.append("    return 1;\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String buildReadme() {
        return "Xyron Pickup Studio\n"
                + "\n"
                + "- xyron_pickups.json guarda os pickups criados dentro do jogo.\n"
                + "- xyron_pickups_export.inc e regenerado automaticamente.\n"
                + "- Edite a lista pelo comando /criarpickup dentro do APK.\n";
    }

    private static String sanitizePawnIdentifier(String raw) {
        String normalized = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[^\\p{ASCII}]", "");
        normalized = normalized.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (TextUtils.isEmpty(normalized)) {
            return "pickup";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            return "pickup_" + normalized;
        }
        return normalized;
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static String readText(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        )) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    builder.append('\n');
                }
                builder.append(line);
                first = false;
            }
        }
        return builder.toString();
    }

    private static boolean writeText(File file, String content) {
        try {
            File parent = file == null ? null : file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file, false),
                    StandardCharsets.UTF_8
            )) {
                writer.write(content == null ? "" : content);
                writer.flush();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writeTextIfMissing(File file, String content) {
        if (file == null || file.exists()) {
            return;
        }
        writeText(file, content);
    }

    public static final class PickupDefinition {
        public String id = "";
        public String label = "";
        public int modelId = 1242;
        public int pickupType = 2;
        public int worldId = 0;
        public int interiorId = 0;
        public int amount = 1;
        public float x = 0f;
        public float y = 0f;
        public float z = 0f;
        public long createdAt = 0L;
        public long updatedAt = 0L;

        public PickupDefinition sanitized() {
            PickupDefinition value = new PickupDefinition();
            value.id = TextUtils.isEmpty(id) ? "" : id.trim();
            value.label = TextUtils.isEmpty(label) ? "Pickup" : label.trim();
            value.modelId = modelId <= 0 ? 1242 : modelId;
            value.pickupType = pickupType <= 0 ? 2 : pickupType;
            value.worldId = Math.max(worldId, 0);
            value.interiorId = Math.max(interiorId, 0);
            value.amount = amount <= 0 ? 1 : amount;
            value.x = x;
            value.y = y;
            value.z = z;
            value.createdAt = createdAt;
            value.updatedAt = updatedAt;
            return value;
        }

        JSONObject toJson() {
            JSONObject root = new JSONObject();
            try {
                root.put("id", id);
                root.put("label", label);
                root.put("modelId", modelId);
                root.put("pickupType", pickupType);
                root.put("worldId", worldId);
                root.put("interiorId", interiorId);
                root.put("amount", amount);
                root.put("x", x);
                root.put("y", y);
                root.put("z", z);
                root.put("createdAt", createdAt);
                root.put("updatedAt", updatedAt);
            } catch (Exception ignored) {
            }
            return root;
        }

        static PickupDefinition fromJson(JSONObject root) {
            if (root == null) {
                return null;
            }
            PickupDefinition pickup = new PickupDefinition();
            pickup.id = root.optString("id", "");
            pickup.label = root.optString("label", "Pickup");
            pickup.modelId = root.optInt("modelId", 1242);
            pickup.pickupType = root.optInt("pickupType", 2);
            pickup.worldId = root.optInt("worldId", 0);
            pickup.interiorId = root.optInt("interiorId", 0);
            pickup.amount = root.optInt("amount", 1);
            pickup.x = (float) root.optDouble("x", 0d);
            pickup.y = (float) root.optDouble("y", 0d);
            pickup.z = (float) root.optDouble("z", 0d);
            pickup.createdAt = root.optLong("createdAt", 0L);
            pickup.updatedAt = root.optLong("updatedAt", 0L);
            return pickup.sanitized();
        }
    }

    public static final class SaveResult {
        public final boolean success;
        public final String message;
        public final PickupDefinition pickup;
        public final ExportResult exportResult;

        SaveResult(boolean success, String message, PickupDefinition pickup, ExportResult exportResult) {
            this.success = success;
            this.message = message;
            this.pickup = pickup;
            this.exportResult = exportResult;
        }

        static SaveResult error(String message) {
            return new SaveResult(false, message, null, ExportResult.error(""));
        }
    }

    public static final class DeleteResult {
        public final boolean success;
        public final String message;
        public final ExportResult exportResult;

        DeleteResult(boolean success, String message, ExportResult exportResult) {
            this.success = success;
            this.message = message;
            this.exportResult = exportResult;
        }

        static DeleteResult error(String message) {
            return new DeleteResult(false, message, ExportResult.error(""));
        }
    }

    public static final class ExportResult {
        public final boolean success;
        public final String message;
        public final String absolutePath;
        public final int pickupCount;

        ExportResult(boolean success, String message, String absolutePath, int pickupCount) {
            this.success = success;
            this.message = message;
            this.absolutePath = absolutePath;
            this.pickupCount = pickupCount;
        }

        static ExportResult error(String message) {
            return new ExportResult(false, TextUtils.isEmpty(message) ? "Falha ao exportar." : message, "", 0);
        }
    }
}
