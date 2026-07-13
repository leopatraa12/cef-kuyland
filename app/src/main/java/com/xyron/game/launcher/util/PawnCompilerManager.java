package com.xyron.game.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PawnCompilerManager {
    private static final String COMPILER_LIBRARY_NAME = "libpawncc_arm.so";
    private static final String COMPILER_RUNTIME_LIBRARY_NAME = "libpawnc.so";
    private static final String ASSET_ROOT = "editor-runtime";
    private static final String ASSET_INCLUDE_DIR = "editor-runtime/include";
    private static final String ASSET_TEMPLATE_DIR = "editor-runtime/templates";
    private static final String STARTER_SCRIPT_NAME = "xyron_starter.pwn";
    private static final String DEFAULT_PROJECTS_RELATIVE_PATH = "editor/projects";
    private static final String DEFAULT_INCLUDE_RELATIVE_PATH = "editor/include";
    private static final String SERVER_GAMEMODES_RELATIVE_PATH = "server/gamemodes";
    private static final List<String> COMPILABLE_EXTENSIONS = Arrays.asList("pwn");

    private PawnCompilerManager() {
    }

    public static boolean prepareWorkspace(Context context) {
        if (context == null) {
            return false;
        }

        boolean sharedPrepared = LocalHostManager.prepareSharedWorkspace(context);
        File editorRoot = LocalHostManager.getSharedEditorRootDirectory();
        File projectsDir = LocalHostManager.getSharedEditorProjectsDirectory();
        File includeDir = LocalHostManager.getSharedEditorIncludeDirectory();
        if (!sharedPrepared || editorRoot == null || projectsDir == null || includeDir == null) {
            return false;
        }

        if (!ensureDir(editorRoot) || !ensureDir(projectsDir) || !ensureDir(includeDir)) {
            return false;
        }

        boolean includesCopied = copyAssetsIfMissing(context.getAssets(), ASSET_INCLUDE_DIR, includeDir);
        boolean templatesCopied = copyAssetsIfMissing(context.getAssets(), ASSET_TEMPLATE_DIR, projectsDir);
        return includesCopied && templatesCopied;
    }

    public static boolean hasCompiler(Context context) {
        File compiler = getCompilerBinary(context);
        File runtimeLibrary = getRuntimeLibrary(context);
        return compiler != null && compiler.exists() && compiler.isFile()
                && runtimeLibrary != null && runtimeLibrary.exists() && runtimeLibrary.isFile();
    }

    public static String getProjectsRelativePath() {
        return DEFAULT_PROJECTS_RELATIVE_PATH;
    }

    public static String getIncludeRelativePath() {
        return DEFAULT_INCLUDE_RELATIVE_PATH;
    }

    public static String getServerGamemodesRelativePath() {
        return SERVER_GAMEMODES_RELATIVE_PATH;
    }

    public static String buildCompilerSummary(Context context) {
        StringBuilder summary = new StringBuilder();
        summary.append("Compiler: ").append(hasCompiler(context) ? "pronto" : "ausente");
        summary.append("\nProjetos: /").append(getProjectsRelativePath());
        summary.append("\nIncludes: /").append(getIncludeRelativePath());
        summary.append("\nGM do host: /").append(getServerGamemodesRelativePath());
        if (context != null) {
            File compiler = getCompilerBinary(context);
            if (compiler != null) {
                summary.append("\nRuntime: ").append(compiler.getAbsolutePath());
            }
        }
        summary.append("\nTemplate inicial: ").append(STARTER_SCRIPT_NAME);
        return summary.toString();
    }

    public static boolean isCompilableFile(String relativePath) {
        File file = HostFileManager.resolveManagedPath(relativePath);
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        String extension = extensionOf(file.getName());
        return COMPILABLE_EXTENSIONS.contains(extension);
    }

    public static CompileResult compile(Context context, String relativePath) {
        if (context == null) {
            return CompileResult.error("Contexto do editor indisponivel.");
        }
        if (!prepareWorkspace(context)) {
            return CompileResult.error("Nao foi possivel preparar a workspace do editor.");
        }
        if (!hasCompiler(context)) {
            return CompileResult.error("O compilador Pawn nao foi empacotado nesta build.");
        }

        File sourceFile = HostFileManager.resolveManagedPath(relativePath);
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return CompileResult.error("Arquivo fonte nao encontrado.");
        }
        if (!isCompilableFile(relativePath)) {
            return CompileResult.error("Selecione um arquivo .pwn para compilar.");
        }

        File parentDir = sourceFile.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            return CompileResult.error("A pasta do script nao esta disponivel.");
        }

        File outputFile = new File(parentDir, stripExtension(sourceFile.getName()) + ".amx");
        File compilerBinary = getCompilerBinary(context);
        if (compilerBinary == null) {
            return CompileResult.error("Nao foi possivel localizar o binario do compilador.");
        }

        ArrayList<String> command = new ArrayList<>();
        command.add(compilerBinary.getAbsolutePath());
        command.add(sourceFile.getAbsolutePath());
        for (String includePath : buildIncludePaths(parentDir)) {
            command.add("-i" + includePath);
        }
        command.add("-o" + outputFile.getAbsolutePath());

        StringBuilder output = new StringBuilder();
        output.append("Compilando ").append(sourceFile.getName()).append("\n");
        output.append("Saida: ").append(outputFile.getAbsolutePath()).append("\n");

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(parentDir);
            processBuilder.redirectErrorStream(true);
            Map<String, String> environment = processBuilder.environment();
            String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
            if (!TextUtils.isEmpty(nativeLibraryDir)) {
                environment.put("LD_LIBRARY_PATH", nativeLibraryDir);
            }

            process = processBuilder.start();
            String compilerOutput = readProcessOutput(process);
            int exitCode = process.waitFor();

            if (!TextUtils.isEmpty(compilerOutput)) {
                output.append("\n").append(compilerOutput.trim());
            }

            if (exitCode != 0 || !outputFile.exists()) {
                output.append("\n\nFalha: o compilador retornou codigo ").append(exitCode).append(".");
                return CompileResult.error(output.toString().trim());
            }

            boolean activated = maybeActivateGamemode(outputFile);
            output.append("\n\nSucesso: ").append(outputFile.getName()).append(" gerado.");
            if (activated) {
                output.append("\nGamemode principal atualizada para este .amx.");
            }
            return CompileResult.success(output.toString().trim(), outputFile.getAbsolutePath(), activated);
        } catch (Exception e) {
            output.append("\n\nFalha ao executar o compilador: ").append(e.getMessage());
            return CompileResult.error(output.toString().trim());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static boolean maybeActivateGamemode(File outputFile) {
        if (outputFile == null || !outputFile.exists() || !outputFile.isFile()) {
            return false;
        }
        File gamemodesDir = LocalHostManager.getSharedGamemodesDirectory();
        if (!HostFileManager.isUnderDirectory(outputFile, gamemodesDir)) {
            return false;
        }
        return LocalHostManager.activateGamemode(outputFile.getName());
    }

    private static List<String> buildIncludePaths(File sourceParent) {
        ArrayList<String> includePaths = new ArrayList<>();
        addIncludePath(includePaths, sourceParent);
        addIncludePath(includePaths, LocalHostManager.getSharedEditorIncludeDirectory());
        addIncludePath(includePaths, LocalHostManager.getSharedScriptfilesDirectory());
        addIncludePath(includePaths, LocalHostManager.getSharedGamemodesDirectory());
        addIncludePath(includePaths, LocalHostManager.getSharedServerDirectory());
        return includePaths;
    }

    private static void addIncludePath(List<String> includePaths, File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        String absolutePath = dir.getAbsolutePath();
        if (!includePaths.contains(absolutePath)) {
            includePaths.add(absolutePath);
        }
    }

    private static File getCompilerBinary(Context context) {
        if (context == null || context.getApplicationInfo() == null) {
            return null;
        }
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        if (TextUtils.isEmpty(nativeLibraryDir)) {
            return null;
        }
        return new File(nativeLibraryDir, COMPILER_LIBRARY_NAME);
    }

    private static File getRuntimeLibrary(Context context) {
        if (context == null || context.getApplicationInfo() == null) {
            return null;
        }
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        if (TextUtils.isEmpty(nativeLibraryDir)) {
            return null;
        }
        return new File(nativeLibraryDir, COMPILER_RUNTIME_LIBRARY_NAME);
    }

    private static boolean copyAssetsIfMissing(AssetManager assetManager, String assetPath, File outputDir) {
        try {
            String[] children = assetManager.list(assetPath);
            if (children == null) {
                return false;
            }
            if (!ensureDir(outputDir)) {
                return false;
            }

            for (String child : children) {
                if (TextUtils.isEmpty(child)) {
                    continue;
                }
                String childAssetPath = assetPath + "/" + child;
                String[] nestedChildren = assetManager.list(childAssetPath);
                File outFile = new File(outputDir, child);
                if (nestedChildren != null && nestedChildren.length > 0) {
                    if (!copyAssetsIfMissing(assetManager, childAssetPath, outFile)) {
                        return false;
                    }
                    continue;
                }
                if (outFile.exists()) {
                    continue;
                }
                if (!copyAssetFile(assetManager, childAssetPath, outFile)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean copyAssetFile(AssetManager assetManager, String assetPath, File outFile) {
        ensureParent(outFile);
        try (InputStream inputStream = assetManager.open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(outFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private static boolean ensureDir(File dir) {
        return dir != null && (dir.exists() || dir.mkdirs());
    }

    private static void ensureParent(File file) {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static String extensionOf(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.US);
    }

    private static String stripExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "script";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    public static final class CompileResult {
        public final boolean success;
        public final String output;
        public final String outputPath;
        public final boolean activatedGamemode;

        private CompileResult(boolean success, String output, String outputPath, boolean activatedGamemode) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.outputPath = outputPath == null ? "" : outputPath;
            this.activatedGamemode = activatedGamemode;
        }

        public static CompileResult success(String output, String outputPath, boolean activatedGamemode) {
            return new CompileResult(true, output, outputPath, activatedGamemode);
        }

        public static CompileResult error(String output) {
            return new CompileResult(false, output, "", false);
        }
    }
}
