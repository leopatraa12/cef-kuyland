package com.xyron.game.launcher.util;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HostFileManager {
    private static final long MAX_EDITABLE_BYTES = 256L * 1024L;
    private static final Set<String> TEXT_EXTENSIONS = new LinkedHashSet<>(Arrays.asList(
            "cfg", "json", "txt", "log", "ini", "pwn", "inc", "md", "properties",
            "xml", "conf", "sh", "yml", "yaml", "csv"
    ));

    private HostFileManager() {
    }

    public static String normalizeRelativePath(String relativePath) {
        File resolved = resolveManagedPath(relativePath);
        File root = LocalHostManager.getSharedRootDirectory();
        if (resolved == null || root == null) {
            return "";
        }
        return toRelativePath(root, resolved);
    }

    public static String getDisplayPath(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        if (TextUtils.isEmpty(normalized)) {
            return "/";
        }
        return "/" + normalized;
    }

    public static String getParentRelativePath(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        if (TextUtils.isEmpty(normalized)) {
            return "";
        }

        int slash = normalized.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return normalized.substring(0, slash);
    }

    public static File resolveManagedPath(String relativePath) {
        File root = LocalHostManager.getSharedRootDirectory();
        if (root == null) {
            return null;
        }

        if (TextUtils.isEmpty(relativePath)) {
            return root;
        }

        File candidate = new File(root, relativePath.replace('/', File.separatorChar));
        return ensureWithinRoot(root, candidate);
    }

    public static File resolveChildPath(String parentRelativePath, String requestedName) {
        File parent = resolveManagedPath(parentRelativePath);
        if (parent == null || !parent.exists() || !parent.isDirectory()) {
            return null;
        }

        String sanitized = sanitizeName(requestedName);
        if (TextUtils.isEmpty(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            return null;
        }

        File target = new File(parent, sanitized);
        File safeTarget = ensureWithinRoot(LocalHostManager.getSharedRootDirectory(), target);
        if (safeTarget == null) {
            return null;
        }

        String safeParentPath = safePath(parent);
        String targetParentPath = safePath(safeTarget.getParentFile());
        if (TextUtils.isEmpty(safeParentPath) || !safeParentPath.equals(targetParentPath)) {
            return null;
        }
        return safeTarget;
    }

    public static List<BrowserEntry> listDirectory(String relativePath) {
        File directory = resolveManagedPath(relativePath);
        File root = LocalHostManager.getSharedRootDirectory();
        if (directory == null || root == null || !directory.exists() || !directory.isDirectory()) {
            return Collections.emptyList();
        }

        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return Collections.emptyList();
        }

        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() && !right.isDirectory()) {
                    return -1;
                }
                if (!left.isDirectory() && right.isDirectory()) {
                    return 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        ArrayList<BrowserEntry> entries = new ArrayList<>();
        for (File child : children) {
            if (child == null) {
                continue;
            }

            BrowserEntry entry = buildBrowserEntry(root, child);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public static String buildDirectoryOverview(String relativePath, List<BrowserEntry> entries) {
        int directoryCount = 0;
        int fileCount = 0;

        if (entries != null) {
            for (BrowserEntry entry : entries) {
                if (entry.directory) {
                    directoryCount++;
                } else {
                    fileCount++;
                }
            }
        }

        StringBuilder overview = new StringBuilder();
        overview.append("Pasta atual: ").append(getDisplayPath(relativePath)).append("\n");
        overview.append(directoryCount).append(" pasta(s) | ");
        overview.append(fileCount).append(" arquivo(s)");

        if (entries == null || entries.isEmpty()) {
            overview.append("\nEsta pasta ainda esta vazia.");
        }
        return overview.toString();
    }

    public static FileActionResult createDirectory(String parentRelativePath, String requestedName) {
        File target = resolveChildPath(parentRelativePath, requestedName);
        if (target == null) {
            return FileActionResult.error("Digite um nome de pasta valido.");
        }
        if (target.exists()) {
            return FileActionResult.error("Ja existe um item com esse nome.");
        }
        if (!target.mkdirs()) {
            return FileActionResult.error("Nao foi possivel criar a pasta.");
        }
        return FileActionResult.success("Pasta criada.", toRelativePath(LocalHostManager.getSharedRootDirectory(), target));
    }

    public static FileActionResult createEmptyFile(String parentRelativePath, String requestedName) {
        File target = resolveChildPath(parentRelativePath, requestedName);
        if (target == null) {
            return FileActionResult.error("Digite um nome de arquivo valido.");
        }
        if (target.exists()) {
            return FileActionResult.error("Ja existe um item com esse nome.");
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return FileActionResult.error("Nao foi possivel criar a pasta do arquivo.");
        }

        try (FileOutputStream ignored = new FileOutputStream(target)) {
            return FileActionResult.success("Arquivo criado.", toRelativePath(LocalHostManager.getSharedRootDirectory(), target));
        } catch (Exception ignored) {
            return FileActionResult.error("Nao foi possivel criar o arquivo.");
        }
    }

    public static RenameResult renameEntry(String relativePath, String requestedName) {
        File source = resolveManagedPath(relativePath);
        if (source == null || !source.exists()) {
            return RenameResult.error("Arquivo ou pasta nao encontrado.");
        }

        File root = LocalHostManager.getSharedRootDirectory();
        if (root == null || safePath(root).equals(safePath(source))) {
            return RenameResult.error("A raiz do host nao pode ser renomeada.");
        }

        String sanitized = sanitizeName(requestedName);
        if (TextUtils.isEmpty(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            return RenameResult.error("Digite um nome valido.");
        }

        File parent = source.getParentFile();
        if (parent == null || !parent.exists()) {
            return RenameResult.error("A pasta pai nao existe.");
        }

        File target = resolveChildPath(toRelativePath(root, parent), sanitized);
        if (target == null) {
            return RenameResult.error("Nao foi possivel gerar o novo caminho.");
        }
        if (target.exists()) {
            return RenameResult.error("Ja existe outro item com esse nome.");
        }

        String oldGamemode = source.isFile() ? extractGamemodeBaseName(source) : "";
        boolean renamed = source.renameTo(target);
        if (!renamed) {
            return RenameResult.error("Nao foi possivel renomear este item.");
        }

        String newGamemode = target.isFile() ? extractGamemodeBaseName(target) : "";
        if (!TextUtils.isEmpty(oldGamemode)
                && !TextUtils.isEmpty(newGamemode)
                && isActiveGamemode(oldGamemode)) {
            LocalHostManager.activateGamemode(target.getName());
        }

        return RenameResult.success(toRelativePath(root, target));
    }

    public static FileActionResult deleteEntry(String relativePath) {
        File file = resolveManagedPath(relativePath);
        File root = LocalHostManager.getSharedRootDirectory();
        if (file == null || !file.exists()) {
            return FileActionResult.error("Arquivo ou pasta nao encontrado.");
        }
        if (root == null || safePath(root).equals(safePath(file))) {
            return FileActionResult.error("A raiz do host nao pode ser removida.");
        }

        if (!deleteRecursively(file)) {
            return FileActionResult.error("Nao foi possivel remover este item.");
        }

        return FileActionResult.success("Item removido.", "");
    }

    public static String readEditableText(String relativePath) throws Exception {
        File file = resolveManagedPath(relativePath);
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Arquivo nao encontrado.");
        }
        if (!isEditableText(file)) {
            throw new IllegalArgumentException("Esse arquivo nao e editavel como texto dentro do launcher.");
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    content.append('\n');
                }
                content.append(line);
                first = false;
            }
        }
        return content.toString();
    }

    public static boolean writeEditableText(String relativePath, String content) {
        File file = resolveManagedPath(relativePath);
        if (file == null || !isEditableText(file)) {
            return false;
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, false),
                StandardCharsets.UTF_8
        )) {
            writer.write(content == null ? "" : content);
            writer.flush();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isEditableText(BrowserEntry entry) {
        return entry != null && !entry.directory && entry.editableText;
    }

    public static boolean isEditableText(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        if (file.length() > MAX_EDITABLE_BYTES) {
            return false;
        }

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.US) : "";
        return TEXT_EXTENSIONS.contains(extension);
    }

    public static boolean isUnderDirectory(File child, File directory) {
        if (child == null || directory == null) {
            return false;
        }

        String childPath = safePath(child);
        String directoryPath = safePath(directory);
        if (TextUtils.isEmpty(childPath) || TextUtils.isEmpty(directoryPath)) {
            return false;
        }
        return childPath.startsWith(directoryPath + File.separator) || childPath.equals(directoryPath);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    public static List<ManagedFile> listManagedFiles() {
        File root = LocalHostManager.getSharedRootDirectory();
        if (root == null || !root.exists()) {
            return Collections.emptyList();
        }

        ArrayList<ManagedFile> result = new ArrayList<>();
        appendFileIfPresent(result, root, LocalHostManager.getSharedServerCfgFile(), "Config");
        appendFileIfPresent(result, root, LocalHostManager.getSharedOmpConfigFile(), "Config");

        appendDirectory(result, root, LocalHostManager.getSharedGamemodesDirectory(), "Gamemodes");
        appendDirectory(result, root, LocalHostManager.getSharedScriptfilesDirectory(), "Scriptfiles");
        appendDirectory(result, root, LocalHostManager.getSharedPluginsDirectory(), "Plugins");
        appendDirectory(result, root, LocalHostManager.getSharedComponentsDirectory(), "Componentes");
        appendDirectory(result, root, LocalHostManager.getSharedBinDirectory(), "Binarios");
        appendDirectory(result, root, LocalHostManager.getSharedLogsDirectory(), "Logs");

        appendRootHelpers(result, root);

        Collections.sort(result, new Comparator<ManagedFile>() {
            @Override
            public int compare(ManagedFile left, ManagedFile right) {
                int category = Integer.compare(categoryRank(left.category), categoryRank(right.category));
                if (category != 0) {
                    return category;
                }
                return left.relativePath.compareToIgnoreCase(right.relativePath);
            }
        });
        return result;
    }

    public static String buildOverview(List<ManagedFile> files) {
        if (files == null || files.isEmpty()) {
            return "Nenhum arquivo do servidor foi encontrado ainda.";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ManagedFile file : files) {
            Integer current = counts.get(file.category);
            counts.put(file.category, current == null ? 1 : current + 1);
        }

        StringBuilder overview = new StringBuilder();
        overview.append(files.size()).append(" arquivo(s) carregado(s)");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            overview.append("\n")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue());
        }
        return overview.toString();
    }

    private static BrowserEntry buildBrowserEntry(File root, File file) {
        if (root == null || file == null || !file.exists()) {
            return null;
        }

        String relativePath = toRelativePath(root, file);
        if (relativePath == null) {
            return null;
        }

        boolean directory = file.isDirectory();
        String category = resolveCategory(relativePath, directory);
        long sizeBytes = directory ? 0L : file.length();
        boolean editable = !directory && isEditableText(file);
        int childCount = directory ? countImmediateChildren(file) : 0;

        return new BrowserEntry(
                file.getName(),
                relativePath,
                category,
                directory,
                sizeBytes,
                editable,
                childCount
        );
    }

    private static String resolveCategory(String relativePath, boolean directory) {
        if (TextUtils.isEmpty(relativePath)) {
            return directory ? "Raiz" : "Arquivo";
        }

        String normalized = relativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        String first = parts.length > 0 ? parts[0].toLowerCase(Locale.US) : "";
        String last = parts.length > 0 ? parts[parts.length - 1].toLowerCase(Locale.US) : "";

        if ("server.cfg".equals(last) || "config.json".equals(last)) {
            return "Config";
        }
        if ("server".equals(first)) {
            if (parts.length == 1) {
                return "Servidor";
            }
            String second = parts[1].toLowerCase(Locale.US);
            if ("gamemodes".equals(second)) {
                return "Gamemodes";
            }
            if ("scriptfiles".equals(second)) {
                return "Scriptfiles";
            }
            if ("plugins".equals(second)) {
                return "Plugins";
            }
            if ("components".equals(second)) {
                return "Componentes";
            }
            return "Servidor";
        }
        if ("bin".equals(first)) {
            return "Binarios";
        }
        if ("logs".equals(first)) {
            return "Logs";
        }
        return directory ? "Pasta" : "Pacote";
    }

    private static int countImmediateChildren(File directory) {
        File[] children = directory == null ? null : directory.listFiles();
        return children == null ? 0 : children.length;
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private static void appendRootHelpers(List<ManagedFile> out, File root) {
        if (root == null || out == null) {
            return;
        }

        File[] children = root.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child == null || !child.isFile()) {
                continue;
            }

            String name = child.getName();
            if ("server.cfg".equalsIgnoreCase(name) || "config.json".equalsIgnoreCase(name)) {
                continue;
            }
            if ("server".equalsIgnoreCase(name)) {
                continue;
            }
            appendFileIfPresent(out, root, child, "Pacote");
        }
    }

    private static void appendDirectory(List<ManagedFile> out, File root, File directory, String category) {
        if (directory == null || root == null || out == null || !directory.exists()) {
            return;
        }

        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        for (File child : children) {
            if (child == null) {
                continue;
            }

            if (child.isDirectory()) {
                appendNestedDirectory(out, root, child, category);
            } else if (child.isFile()) {
                appendFileIfPresent(out, root, child, category);
            }
        }
    }

    private static void appendNestedDirectory(List<ManagedFile> out, File root, File directory, String category) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        for (File child : children) {
            if (child == null) {
                continue;
            }
            if (child.isDirectory()) {
                appendNestedDirectory(out, root, child, category);
            } else if (child.isFile()) {
                appendFileIfPresent(out, root, child, category);
            }
        }
    }

    private static void appendFileIfPresent(List<ManagedFile> out, File root, File file, String category) {
        if (out == null || root == null || file == null || !file.exists() || !file.isFile()) {
            return;
        }

        String relativePath = toRelativePath(root, file);
        if (TextUtils.isEmpty(relativePath)) {
            return;
        }

        out.add(new ManagedFile(
                file.getName(),
                relativePath,
                category,
                file.length(),
                isEditableText(file)
        ));
    }

    private static int categoryRank(String category) {
        if ("Config".equals(category)) {
            return 0;
        }
        if ("Gamemodes".equals(category)) {
            return 1;
        }
        if ("Scriptfiles".equals(category)) {
            return 2;
        }
        if ("Plugins".equals(category)) {
            return 3;
        }
        if ("Componentes".equals(category)) {
            return 4;
        }
        if ("Binarios".equals(category)) {
            return 5;
        }
        if ("Logs".equals(category)) {
            return 6;
        }
        return 7;
    }

    private static String sanitizeName(String requestedName) {
        if (requestedName == null) {
            return "";
        }

        String trimmed = requestedName.trim();
        trimmed = trimmed.replace("\\", "");
        trimmed = trimmed.replace("/", "");
        trimmed = trimmed.replace(":", "");
        trimmed = trimmed.replace("*", "");
        trimmed = trimmed.replace("?", "");
        trimmed = trimmed.replace("\"", "");
        trimmed = trimmed.replace("<", "");
        trimmed = trimmed.replace(">", "");
        trimmed = trimmed.replace("|", "");
        return trimmed;
    }

    private static String extractGamemodeBaseName(File file) {
        if (file == null || file.getParentFile() == null) {
            return "";
        }

        File gamemodesDir = LocalHostManager.getSharedGamemodesDirectory();
        if (gamemodesDir == null) {
            return "";
        }

        String parentPath = safePath(file.getParentFile());
        String gamemodePath = safePath(gamemodesDir);
        if (TextUtils.isEmpty(parentPath) || TextUtils.isEmpty(gamemodePath) || !parentPath.startsWith(gamemodePath)) {
            return "";
        }

        String name = file.getName();
        if (!name.toLowerCase(Locale.US).endsWith(".amx")) {
            return "";
        }
        return name.substring(0, name.length() - 4);
    }

    private static boolean isActiveGamemode(String gameModeName) {
        if (TextUtils.isEmpty(gameModeName)) {
            return false;
        }

        File serverCfg = LocalHostManager.getSharedServerCfgFile();
        if (serverCfg == null || !serverCfg.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(serverCfg), StandardCharsets.UTF_8))) {
            String line;
            String prefix = "gamemode0 " + gameModeName.toLowerCase(Locale.US) + " ";
            while ((line = reader.readLine()) != null) {
                String normalized = line.trim().toLowerCase(Locale.US);
                if (normalized.startsWith(prefix)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String toRelativePath(File root, File file) {
        String rootPath = safePath(root);
        String filePath = safePath(file);
        if (TextUtils.isEmpty(rootPath) || TextUtils.isEmpty(filePath) || !filePath.startsWith(rootPath)) {
            return "";
        }

        String relative = filePath.substring(rootPath.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative.replace(File.separatorChar, '/');
    }

    private static File ensureWithinRoot(File root, File candidate) {
        try {
            String rootPath = root == null ? "" : root.getCanonicalPath();
            File canonicalFile = candidate == null ? null : candidate.getCanonicalFile();
            String filePath = canonicalFile == null ? "" : canonicalFile.getCanonicalPath();
            if (TextUtils.isEmpty(rootPath) || TextUtils.isEmpty(filePath)) {
                return null;
            }
            if (!filePath.startsWith(rootPath)) {
                return null;
            }
            return canonicalFile;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safePath(File file) {
        try {
            return file == null ? "" : file.getCanonicalPath();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final class BrowserEntry {
        public final String displayName;
        public final String relativePath;
        public final String category;
        public final boolean directory;
        public final long sizeBytes;
        public final boolean editableText;
        public final int childCount;

        BrowserEntry(String displayName,
                     String relativePath,
                     String category,
                     boolean directory,
                     long sizeBytes,
                     boolean editableText,
                     int childCount) {
            this.displayName = displayName == null ? "" : displayName;
            this.relativePath = relativePath == null ? "" : relativePath;
            this.category = category == null ? "Pacote" : category;
            this.directory = directory;
            this.sizeBytes = Math.max(0L, sizeBytes);
            this.editableText = editableText;
            this.childCount = Math.max(0, childCount);
        }
    }

    public static final class FileActionResult {
        public final boolean success;
        public final String message;
        public final String relativePath;

        private FileActionResult(boolean success, String message, String relativePath) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.relativePath = relativePath == null ? "" : relativePath;
        }

        public static FileActionResult success(String message, String relativePath) {
            return new FileActionResult(true, message, relativePath);
        }

        public static FileActionResult error(String message) {
            return new FileActionResult(false, message, "");
        }
    }

    public static final class ManagedFile {
        public final String displayName;
        public final String relativePath;
        public final String category;
        public final long sizeBytes;
        public final boolean editableText;

        ManagedFile(String displayName,
                    String relativePath,
                    String category,
                    long sizeBytes,
                    boolean editableText) {
            this.displayName = displayName == null ? "" : displayName;
            this.relativePath = relativePath == null ? "" : relativePath;
            this.category = category == null ? "Pacote" : category;
            this.sizeBytes = Math.max(0L, sizeBytes);
            this.editableText = editableText;
        }
    }

    public static final class RenameResult {
        public final boolean success;
        public final String message;
        public final String newRelativePath;

        private RenameResult(boolean success, String message, String newRelativePath) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.newRelativePath = newRelativePath == null ? "" : newRelativePath;
        }

        public static RenameResult success(String relativePath) {
            return new RenameResult(true, "Item renomeado.", relativePath);
        }

        public static RenameResult error(String message) {
            return new RenameResult(false, message, "");
        }
    }
}
