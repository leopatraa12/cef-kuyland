package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UpdateSourceResolver {
    private static final String CONFIG_ASSET_PATH = "update_sources.json";
    private static final String DEFAULT_DATA_VARIANT_ID = "lite";
    private static final String DEFAULT_CLIENT_CONFIG_URL =
            "https://rp.goldcityrolrplay.space/dowloandoficial/client_config.json";
    private static final String DEFAULT_FILES_BASE_URL =
            "https://rp.goldcityrolrplay.space/dowloandoficial/files";

    private UpdateSourceResolver() {
    }

    public static UpdateSourceConfig resolve(Context context) {
        return resolve(context, DEFAULT_DATA_VARIANT_ID);
    }

    public static UpdateSourceConfig resolve(Context context, String dataVariantId) {
        LinkedHashSet<String> clientConfigUrls = new LinkedHashSet<>();
        LinkedHashSet<String> fallbackFileBaseUrls = new LinkedHashSet<>();
        String huggingFaceTreeApiUrl = "";
        String huggingFaceResolveBaseUrl = "";
        String huggingFaceFilesPathPrefix = "";
        boolean hasCustomSource = false;
        boolean selectedExplicitVariant = false;
        String normalizedVariantId = normalizeVariantId(dataVariantId);

        JSONObject config = loadJsonAsset(context.getAssets(), CONFIG_ASSET_PATH);
        if (config != null) {
            JSONObject selectedConfig = findDataVariantConfig(config, normalizedVariantId);
            if (selectedConfig != null) {
                selectedExplicitVariant = true;
            } else {
                selectedConfig = config;
            }

            ParsedSourceConfig parsedConfig = parseSourceConfig(selectedConfig);
            clientConfigUrls.addAll(parsedConfig.clientConfigUrls);
            fallbackFileBaseUrls.addAll(parsedConfig.fallbackFileBaseUrls);
            hasCustomSource = parsedConfig.hasCustomSource;
            huggingFaceTreeApiUrl = parsedConfig.huggingFaceTreeApiUrl;
            huggingFaceResolveBaseUrl = parsedConfig.huggingFaceResolveBaseUrl;
            huggingFaceFilesPathPrefix = parsedConfig.huggingFaceFilesPathPrefix;
        }

        boolean allowDefaultFallback = !selectedExplicitVariant
                || DEFAULT_DATA_VARIANT_ID.equals(normalizedVariantId);
        if (!hasCustomSource && allowDefaultFallback) {
            clientConfigUrls.add(DEFAULT_CLIENT_CONFIG_URL);
            fallbackFileBaseUrls.add(DEFAULT_FILES_BASE_URL);
        }

        return new UpdateSourceConfig(
                new ArrayList<>(clientConfigUrls),
                new ArrayList<>(fallbackFileBaseUrls),
                huggingFaceTreeApiUrl,
                huggingFaceResolveBaseUrl,
                huggingFaceFilesPathPrefix
        );
    }

    private static ParsedSourceConfig parseSourceConfig(JSONObject config) {
        ParsedSourceConfig parsedConfig = new ParsedSourceConfig();
        if (config == null) {
            return parsedConfig;
        }

        JSONObject huggingFace = config.optJSONObject("huggingface");
        if (huggingFace != null) {
            String repoId = sanitize(huggingFace.optString("repo_id"));
            if (isValidRepoId(repoId)) {
                String repoType = sanitize(huggingFace.optString("repo_type"));
                String revision = sanitize(huggingFace.optString("revision"));
                String clientConfigPath = sanitize(huggingFace.optString("client_config_path"));
                String filesPathPrefix = huggingFace.has("files_path_prefix")
                        ? sanitize(huggingFace.optString("files_path_prefix"))
                        : "files";

                if (revision.isEmpty()) {
                    revision = "main";
                }
                if (clientConfigPath.isEmpty()) {
                    clientConfigPath = "client_config.json";
                }

                parsedConfig.hasCustomSource = true;
                parsedConfig.huggingFaceFilesPathPrefix = trimSlashes(filesPathPrefix);
                parsedConfig.huggingFaceTreeApiUrl = buildHuggingFaceTreeApiUrl(repoId, repoType, revision);
                parsedConfig.huggingFaceResolveBaseUrl = buildHuggingFaceResolveBaseUrl(repoId, repoType, revision);
                parsedConfig.clientConfigUrls.add(buildHuggingFaceUrl(repoId, repoType, revision, clientConfigPath));
                parsedConfig.fallbackFileBaseUrls.add(buildHuggingFaceUrl(repoId, repoType, revision, filesPathPrefix));
            }
        }

        addAll(parsedConfig.clientConfigUrls, config.optJSONArray("client_config_urls"));
        addAll(parsedConfig.fallbackFileBaseUrls, config.optJSONArray("fallback_file_base_urls"));
        parsedConfig.hasCustomSource = parsedConfig.hasCustomSource
                || hasValues(config.optJSONArray("client_config_urls"))
                || hasValues(config.optJSONArray("fallback_file_base_urls"));

        return parsedConfig;
    }

    private static JSONObject findDataVariantConfig(JSONObject config, String dataVariantId) {
        JSONArray variants = config.optJSONArray("data_variants");
        if (variants == null) {
            return null;
        }

        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant != null && normalizeVariantId(variant.optString("id")).equals(dataVariantId)) {
                return variant;
            }
        }

        return null;
    }

    private static JSONObject loadJsonAsset(AssetManager assetManager, String assetPath) {
        try (InputStream inputStream = assetManager.open(assetPath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            String raw = outputStream.toString(StandardCharsets.UTF_8.name());
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addAll(Set<String> target, JSONArray values) {
        if (values == null) {
            return;
        }

        for (int i = 0; i < values.length(); i++) {
            String value = sanitize(values.optString(i));
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private static boolean hasValues(JSONArray values) {
        return values != null && values.length() > 0;
    }

    private static String buildHuggingFaceUrl(
            String repoId,
            String repoType,
            String revision,
            String path
    ) {
        StringBuilder builder = new StringBuilder(buildHuggingFaceResolveBaseUrl(repoId, repoType, revision));
        String normalizedPath = trimLeadingSlashes(path);
        if (!normalizedPath.isEmpty()) {
            if (!builder.toString().endsWith("/")) {
                builder.append("/");
            }
            builder.append(normalizedPath);
        }

        return builder.toString();
    }

    private static String buildHuggingFaceResolveBaseUrl(
            String repoId,
            String repoType,
            String revision
    ) {
        StringBuilder builder = new StringBuilder("https://huggingface.co/");
        builder.append(resolveRepoTypePathSegment(repoType))
                .append(trimSlashes(repoId))
                .append("/resolve/")
                .append(trimSlashes(revision));
        return builder.toString();
    }

    private static String buildHuggingFaceTreeApiUrl(
            String repoId,
            String repoType,
            String revision
    ) {
        StringBuilder builder = new StringBuilder("https://huggingface.co/api/");
        builder.append(resolveRepoTypePathSegment(repoType))
                .append(trimSlashes(repoId))
                .append("/tree/")
                .append(trimSlashes(revision))
                .append("?recursive=true&expand=true&limit=50");
        return builder.toString();
    }

    private static String resolveRepoTypePathSegment(String repoType) {
        if ("dataset".equalsIgnoreCase(repoType) || "datasets".equalsIgnoreCase(repoType)) {
            return "datasets/";
        }
        if ("space".equalsIgnoreCase(repoType) || "spaces".equalsIgnoreCase(repoType)) {
            return "spaces/";
        }
        return "models/";
    }

    private static boolean isValidRepoId(String repoId) {
        return !repoId.isEmpty()
                && !repoId.contains("SEU_USUARIO")
                && !repoId.contains("SEU_REPO")
                && !repoId.contains("<")
                && !repoId.contains(">");
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeVariantId(String value) {
        String sanitized = sanitize(value);
        return sanitized.isEmpty() ? DEFAULT_DATA_VARIANT_ID : sanitized.toLowerCase();
    }

    private static String trimSlashes(String value) {
        String sanitized = sanitize(value);
        while (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith("/")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    private static String trimLeadingSlashes(String value) {
        String sanitized = sanitize(value);
        while (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }

    public static final class UpdateSourceConfig {
        public final List<String> clientConfigUrls;
        public final List<String> fallbackFileBaseUrls;
        public final String huggingFaceTreeApiUrl;
        public final String huggingFaceResolveBaseUrl;
        public final String huggingFaceFilesPathPrefix;

        public UpdateSourceConfig(
                List<String> clientConfigUrls,
                List<String> fallbackFileBaseUrls,
                String huggingFaceTreeApiUrl,
                String huggingFaceResolveBaseUrl,
                String huggingFaceFilesPathPrefix
        ) {
            this.clientConfigUrls = clientConfigUrls;
            this.fallbackFileBaseUrls = fallbackFileBaseUrls;
            this.huggingFaceTreeApiUrl = huggingFaceTreeApiUrl;
            this.huggingFaceResolveBaseUrl = huggingFaceResolveBaseUrl;
            this.huggingFaceFilesPathPrefix = huggingFaceFilesPathPrefix;
        }
    }

    private static final class ParsedSourceConfig {
        final LinkedHashSet<String> clientConfigUrls = new LinkedHashSet<>();
        final LinkedHashSet<String> fallbackFileBaseUrls = new LinkedHashSet<>();
        String huggingFaceTreeApiUrl = "";
        String huggingFaceResolveBaseUrl = "";
        String huggingFaceFilesPathPrefix = "";
        boolean hasCustomSource = false;
    }
}
