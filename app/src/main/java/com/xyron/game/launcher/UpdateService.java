package com.xyron.game.launcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.OnProgressListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.Progress;
import com.xyron.game.launcher.data.FilesData;
import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.UpdateSourceResolver;
import com.xyron.game.launcher.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class UpdateService extends Service {
    private static final String DEFAULT_DATA_VARIANT_ID = "lite";

    Messenger mMessenger;
    Messenger mActivityMessenger;
    IncomingHandler mInHandler;

    public UpdateActivity.GameStatus mGameStatus = UpdateActivity.GameStatus.Unknown;
    public UpdateActivity.UpdateStatus mUpdateStatus = UpdateActivity.UpdateStatus.Undefined;

    public boolean mDownloadingStatus = false;

    public long mUpdateGameDataSize = 0;
    public long mUpdateGameDataSizeUpdated = 0;
    public String mUpdateGameURL = "";
    public int mUpdateVersion;

    public ArrayList<String> mUpdateFiles;
    public ArrayList<String> mUpdateFilesName;
    public ArrayList<Long> mUpdateFilesSize;
    public ArrayList<String> mUpdateFilesUrl;
    public ArrayList<String> mClientConfigUrls;
    public ArrayList<String> mFallbackFileBaseUrls;
    public String mHuggingFaceTreeApiUrl = "";
    public String mHuggingFaceResolveBaseUrl = "";
    public String mHuggingFaceFilesPathPrefix = "";
    public String mDataVariantId = DEFAULT_DATA_VARIANT_ID;

    public int mGpuType = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread handlerThread = new HandlerThread("ServiceStartArguments", 10);
        handlerThread.start();
        PRDownloader.initialize(getApplicationContext(), PRDownloaderConfig.newBuilder().setDatabaseEnabled(true).setReadTimeout(30000).setConnectTimeout(30000).build());
        mInHandler = new IncomingHandler(handlerThread.getLooper());
        mMessenger = new Messenger(mInHandler);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    class IncomingHandler extends Handler {
        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            mActivityMessenger = msg.replyTo;
            Message obtain = null;
            Messenger messenger = null;
            if (msg.what == 0) {
                mGpuType = msg.getData().getInt("gputype");
                if(mGpuType == 0)
                {
                    Log.e("x1y2z", "GPU not found");
                    return;
                }
                updateDataVariantFromMessage(msg);
                startUpdating();
            } else if (msg.what == 1) {
                startGameUpdateChecking();
            } else if(msg.what == 2) {
                updateGame();
            } else if (msg.what == 4) {
                obtain = Message.obtain(mInHandler, 4);
                obtain.getData().putString("status", mUpdateStatus.name());
                UpdateService updateService = UpdateService.this;
                obtain.replyTo = updateService.mMessenger;
                messenger = updateService.mActivityMessenger;
                try {
                    messenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }
            } else if (msg.what == 5) {
                obtain = Message.obtain(mInHandler, 5);
                obtain.getData().putString("status", mGameStatus.name());
                obtain.replyTo = mMessenger;
                try {
                    mActivityMessenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }

            } else if (msg.what == 7) {
                mGpuType = msg.getData().getInt("gputype");
                if(mGpuType == 0)
                {
                    Log.e("x1y2z", "GPU not found");
                    return;
                }
                updateDataVariantFromMessage(msg);
                startUpdating();
            }

        }
    }

    void startUpdating()
    {
        resetUpdateState();
        loadUpdateSources();
        setUpdateStatus(UpdateActivity.UpdateStatus.CheckUpdate);
        requestClientConfig(0);
    }

    private void loadUpdateSources() {
        UpdateSourceResolver.UpdateSourceConfig sourceConfig = UpdateSourceResolver.resolve(this, mDataVariantId);
        mClientConfigUrls = new ArrayList<>(sourceConfig.clientConfigUrls);
        mFallbackFileBaseUrls = new ArrayList<>(sourceConfig.fallbackFileBaseUrls);
        mHuggingFaceTreeApiUrl = sourceConfig.huggingFaceTreeApiUrl;
        mHuggingFaceResolveBaseUrl = sourceConfig.huggingFaceResolveBaseUrl;
        mHuggingFaceFilesPathPrefix = sourceConfig.huggingFaceFilesPathPrefix;
    }

    private void updateDataVariantFromMessage(Message msg) {
        String requestedVariant = msg.getData().getString("data_variant");
        if (requestedVariant == null || requestedVariant.trim().isEmpty()) {
            mDataVariantId = DEFAULT_DATA_VARIANT_ID;
            return;
        }
        mDataVariantId = requestedVariant.trim().toLowerCase();
    }

    private void requestClientConfig(int index) {
        if (mClientConfigUrls == null || index >= mClientConfigUrls.size()) {
            handleClientConfigFailure();
            return;
        }

        String clientConfigUrl = mClientConfigUrls.get(index);
        Volley.newRequestQueue(getApplicationContext()).add(new StringRequest(clientConfigUrl, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jSONObject = new JSONObject(response).getJSONObject("client_config");
                    mUpdateVersion = jSONObject.getInt("version_code");
                    mUpdateGameURL = resolvePossiblyRelativeUrl(
                            clientConfigUrl,
                            jSONObject.optString("url_launcher")
                    );

                    String string = resolvePossiblyRelativeUrl(
                            clientConfigUrl,
                            jSONObject.optString("url_cache_files")
                    );
                    if (string.isEmpty()) {
                        requestClientConfig(index + 1);
                        return;
                    }

                    getFilesInfo(string);

                    if (!isGameUpdateExists()) {
                        if (mUpdateFiles.isEmpty()) {
                            mGameStatus = UpdateActivity.GameStatus.Updated;
                        }
                        else {
                            mGameStatus = UpdateActivity.GameStatus.UpdateRequired;
                        }
                    }
                    else {
                        mGameStatus = UpdateActivity.GameStatus.GameUpdateRequired;
                    }

                    setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);

                } catch (Exception e) {
                    e.printStackTrace();
                    requestClientConfig(index + 1);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("x1y2z", "error " + error.toString());
                requestClientConfig(index + 1);
            }
        }));
    }

    private void handleClientConfigFailure() {
        if (canUseHuggingFaceTreeFallback()) {
            requestHuggingFaceTreeManifest();
            return;
        }
        handleSourceUnavailable();
    }

    private void handleSourceUnavailable() {
        mGameStatus = GameDataVerifier.hasRequiredGameData(this)
                ? UpdateActivity.GameStatus.Updated
                : UpdateActivity.GameStatus.Unknown;
        setUpdateStatus(UpdateActivity.UpdateStatus.SourceUnavailable);
        notifyGameStatus();
    }

    private boolean canUseHuggingFaceTreeFallback() {
        return !sanitize(mHuggingFaceTreeApiUrl).isEmpty()
                && !sanitize(mHuggingFaceResolveBaseUrl).isEmpty();
    }

    private void requestHuggingFaceTreeManifest() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean loaded = getFilesInfoFromHuggingFaceTree();
                    if (!loaded) {
                        handleSourceUnavailable();
                        return;
                    }

                    mUpdateVersion = getInstalledVersionCode();
                    mUpdateGameURL = "";
                    finalizeManifestLoad();
                } catch (Exception e) {
                    Log.e("x1y2z", "hugging face tree fallback failed", e);
                    handleSourceUnavailable();
                }
            }
        }).start();
    }

    private void finalizeManifestLoad() {
        if (!isGameUpdateExists()) {
            if (mUpdateFiles.isEmpty()) {
                mGameStatus = UpdateActivity.GameStatus.Updated;
            } else {
                mGameStatus = UpdateActivity.GameStatus.UpdateRequired;
            }
        } else {
            mGameStatus = UpdateActivity.GameStatus.GameUpdateRequired;
        }

        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
        notifyGameStatus();
    }

    private void notifyGameStatus() {
        Message obtain = Message.obtain(mInHandler, 5);
        obtain.getData().putString("status", mGameStatus.name());
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }
    }

    public void getFilesInfo(String response) throws IOException, JSONException {
        Util.responseFiles = "";
        Util.responseFilesInt = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                BufferedReader reader = null;

                try {
                    URL url = new URL(response);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();


                    InputStream stream = connection.getInputStream();

                    reader = new BufferedReader(new InputStreamReader(stream));

                    StringBuffer buffer = new StringBuffer();
                    String line = "";

                    while ((line = reader.readLine()) != null) {
                        buffer.append(line+"\n");

                    }

                    Util.responseFiles = buffer.toString();
                    Util.responseFilesInt = 1;


                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    Util.responseFilesInt = 2;
                } catch (IOException e) {
                    e.printStackTrace();
                    Util.responseFilesInt = 2;
                }
            }
        }).start();
        int i5;
        while (true) {
            i5 = Util.responseFilesInt;
            if (i5 != 0) {
                break;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(i5 == 2) {
            throw new IOException("Unable to fetch files manifest: " + response);
        }

        Log.d("x1y2z", "Info: " + Util.responseFiles);
        JSONObject jsonObject = new JSONObject(Util.responseFiles);
        JSONArray jsonArray = jsonObject.getJSONArray("files");
        Log.d("x1y2z", "Length: " + jsonArray.length());
        for(int i = 0; i<jsonArray.length(); i++) {
            JSONObject fileObject = jsonArray.getJSONObject(i);
            FilesData fileData = new FilesData(
                    fileObject.getString("name"),
                    fileObject.getLong("size"),
                    fileObject.getString("path"),
                    fileObject.optString("url")
            );
            enqueueMissingFile(fileData, response);
        }
    }

    private boolean getFilesInfoFromHuggingFaceTree() throws IOException, JSONException {
        String nextUrl = mHuggingFaceTreeApiUrl;
        ArrayList<JSONObject> fileEntries = new ArrayList<>();
        Set<String> remotePaths = new HashSet<>();

        while (!sanitize(nextUrl).isEmpty()) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                connection = (HttpURLConnection) new URL(nextUrl).openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Unexpected Hugging Face response code: " + responseCode);
                }

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder buffer = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }

                JSONArray entries = new JSONArray(buffer.toString());
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null || !"file".equalsIgnoreCase(item.optString("type"))) {
                        continue;
                    }

                    String remotePath = sanitize(item.optString("path"));
                    if (remotePath.isEmpty()) {
                        continue;
                    }

                    fileEntries.add(item);
                    remotePaths.add(remotePath);
                }

                nextUrl = parseNextLink(connection.getHeaderField("Link"));
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        if (fileEntries.isEmpty()) {
            return false;
        }

        boolean hasUsableRemoteFile = false;
        for (JSONObject entry : fileEntries) {
            FilesData fileData = buildHuggingFaceFileData(entry, remotePaths);
            if (fileData != null) {
                hasUsableRemoteFile = true;
                enqueueMissingFile(fileData, mHuggingFaceResolveBaseUrl);
            }
        }

        return hasUsableRemoteFile;
    }

    private FilesData buildHuggingFaceFileData(JSONObject entry, Set<String> remotePaths) {
        String remotePath = sanitize(entry.optString("path"));
        if (remotePath.isEmpty() || remotePath.startsWith(".")) {
            return null;
        }

        String localPath = resolveHuggingFaceLocalPath(remotePath, remotePaths);
        if (localPath.isEmpty()) {
            return null;
        }

        String fileName = new File(localPath).getName();
        long size = Math.max(0L, entry.optLong("size", 0L));
        String downloadUrl = joinUrl(mHuggingFaceResolveBaseUrl, remotePath);
        return new FilesData(fileName, size, localPath, downloadUrl);
    }

    private String resolveHuggingFaceLocalPath(String remotePath, Set<String> remotePaths) {
        String normalizedPrefix = sanitize(mHuggingFaceFilesPathPrefix);
        if (!normalizedPrefix.isEmpty()) {
            if (remotePath.equals(normalizedPrefix)) {
                return "";
            }

            String expectedPrefix = normalizedPrefix + "/";
            if (!remotePath.startsWith(expectedPrefix)) {
                return "";
            }

            return remotePath.substring(expectedPrefix.length());
        }

        if (remotePath.startsWith("files/")) {
            String strippedPath = remotePath.substring("files/".length());
            if (remotePaths.contains(strippedPath)) {
                return "";
            }
            return strippedPath;
        }

        return remotePath;
    }

    private String parseNextLink(String linkHeader) {
        String sanitizedHeader = sanitize(linkHeader);
        if (sanitizedHeader.isEmpty()) {
            return "";
        }

        String[] parts = sanitizedHeader.split(",");
        for (String part : parts) {
            String trimmed = sanitize(part);
            if (!trimmed.contains("rel=\"next\"")) {
                continue;
            }

            int start = trimmed.indexOf('<');
            int end = trimmed.indexOf('>');
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end);
            }
        }

        return "";
    }

    private void enqueueMissingFile(FilesData fileData, String manifestBaseUrl) {
        if (fileData == null || shouldIgnoreFile(fileData)) {
            return;
        }

        String rootPath = getExternalFilesDir(null) + "/";
        File file = new File(rootPath + fileData.getPath());
        if (isCompleteFile(file, fileData.getSize())) {
            return;
        }

        if (!shouldDownloadForCurrentGpu(fileData.getPath())) {
            return;
        }

        mUpdateFiles.add(fileData.getPath());
        Log.d("x1y2z", "File name: " + fileData.getName());
        mUpdateFilesName.add(fileData.getName());
        Log.d("x1y2z", "File path: " + fileData.getPath());
        mUpdateFilesSize.add(fileData.getSize());
        mUpdateFilesUrl.add(resolveDownloadUrl(manifestBaseUrl, fileData));
        mUpdateGameDataSize = mUpdateGameDataSize + fileData.getSize();
        Log.d("x1y2z", "File size: " + fileData.getSize());
        Log.d("x1y2z", "Data size: " + mUpdateGameDataSize);
    }

    private boolean isCompleteFile(File file, long expectedSize) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        return expectedSize <= 0L || file.length() == expectedSize;
    }

    private boolean shouldIgnoreFile(FilesData fileData) {
        return fileData.getName().equals("samp_log.txt")
                || fileData.getName().equals("svlog.txt")
                || fileData.getName().equals("gtasatelem.set")
                || fileData.getName().equals("GTASAMP10.b")
                || fileData.getName().equals("gta_sa.set")
                || fileData.getName().equals("settings.ini");
    }

    private boolean shouldDownloadForCurrentGpu(String filePath) {
        if (filePath.contains("player")
                || filePath.contains("playerhi")
                || filePath.contains("menu")
                || filePath.contains("samp")) {
            return true;
        }

        if (filePath.contains(".dxt.") && mGpuType != 1) {
            return false;
        }
        if (filePath.contains(".etc.") && mGpuType != 2) {
            return false;
        }
        return !filePath.contains(".pvr.") || mGpuType == 3;
    }

    private String resolveDownloadUrl(String filesManifestUrl, FilesData fileData) {
        String explicitUrl = sanitize(fileData.getUrl());
        if (!explicitUrl.isEmpty()) {
            return resolvePossiblyRelativeUrl(filesManifestUrl, explicitUrl);
        }

        String relativePath = trimLeadingSlash(fileData.getPath());
        for (String baseUrl : mFallbackFileBaseUrls) {
            String resolved = joinUrl(baseUrl, relativePath);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        return "";
    }

    private String joinUrl(String baseUrl, String relativePath) {
        String sanitizedBaseUrl = sanitize(baseUrl);
        if (sanitizedBaseUrl.isEmpty()) {
            return "";
        }
        if (!sanitizedBaseUrl.endsWith("/")) {
            sanitizedBaseUrl = sanitizedBaseUrl + "/";
        }
        return resolvePossiblyRelativeUrl(sanitizedBaseUrl, relativePath);
    }

    private String resolvePossiblyRelativeUrl(String baseUrl, String candidate) {
        String sanitizedCandidate = sanitize(candidate);
        if (sanitizedCandidate.isEmpty()) {
            return "";
        }
        if (sanitizedCandidate.startsWith("http://") || sanitizedCandidate.startsWith("https://")) {
            return sanitizedCandidate;
        }
        String sanitizedBaseUrl = sanitize(baseUrl);
        if (sanitizedBaseUrl.isEmpty()) {
            return sanitizedCandidate;
        }
        try {
            return new URL(new URL(sanitizedBaseUrl), sanitizedCandidate).toString();
        } catch (MalformedURLException ignored) {
            return sanitizedCandidate;
        }
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private int getInstalledVersionCode() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
            return packageInfo.versionCode;
        } catch (Exception e) {
            Log.e("x1y2z", "unable to read installed version", e);
            return 1;
        }
    }

    private String trimLeadingSlash(String value) {
        String sanitizedValue = sanitize(value);
        while (sanitizedValue.startsWith("/")) {
            sanitizedValue = sanitizedValue.substring(1);
        }
        return sanitizedValue;
    }

    public void updateGame() {
        if (isGameUpdateExists()) {
            Log.d("UpdateService", "updateGame exists");
            setUpdateStatus(UpdateActivity.UpdateStatus.DownloadGame);
            downloadGame();
            return;
        }
        Log.d("UpdateService", "updateGame done");
        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        Message obtain = Message.obtain(mInHandler, 2);
        obtain.getData().putBoolean("status", true);
        obtain.getData().putString("apkPath", file.getAbsolutePath());
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }
        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
    }


    public void startGameUpdateChecking()
    {
        if (!mUpdateFiles.isEmpty()) {
            setUpdateStatus(UpdateActivity.UpdateStatus.DownloadGameData);
            startDataUpdating();
            return;
        }

        Log.d("UpdateService", "updateGameData()");
        Message obtain = Message.obtain(this.mInHandler, 1);
        obtain.getData().putBoolean("status", true);
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }

    }

    public void setUpdateStatus(UpdateActivity.UpdateStatus updateStatus) {
        if (updateStatus.name().length() != 0 && mUpdateStatus != updateStatus) {
            mUpdateStatus = updateStatus;
            Message obtain = Message.obtain(mInHandler, 4);
            obtain.getData().putString("status", mUpdateStatus.name());
            obtain.replyTo = mMessenger;
            Messenger messenger = mActivityMessenger;
            if (messenger != null) {
                try {
                    messenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }
            }
        }
    }


    public void startDataUpdating()
    {
        ArrayList<String> arrayList = new ArrayList<>(mUpdateFiles);
        ArrayList<String> arrayList1 = new ArrayList<>(mUpdateFilesName);
        ArrayList<Long> arrayList2 = new ArrayList<>(mUpdateFilesSize);
        ArrayList<String> arrayList3 = new ArrayList<>(mUpdateFilesUrl);
        mUpdateFiles.clear();
        mUpdateFilesName.clear();
        mUpdateFilesSize.clear();
        mUpdateFilesUrl.clear();
        Ref.IntRef intRef = new Ref.IntRef();
        intRef.element = 0;
        Ref.LongRef longRef1 = new Ref.LongRef();
        longRef1.element = 0;
        while(intRef.element<arrayList.size()) {
            mDownloadingStatus = true;

            String string = getExternalFilesDir(null) + "/" + arrayList.get(intRef.element);

            //Log.d("x1y2z", "Update file path: " + string.replace((CharSequence) arrayList1.get(intRef.element), "") + ", Name:" + arrayList1.get(intRef.element));

            File file = new File(string);
            File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            if (file.exists()) {
                file.delete();
            }

            Ref.LongRef longRef = new Ref.LongRef();
            longRef.element = System.currentTimeMillis();

            Log.d("x1y2z", "startDataUpdating " + mUpdateGameDataSize + " " + mUpdateGameDataSizeUpdated);

            String downloadUrl = arrayList3.get(intRef.element);
            if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                mDownloadingStatus = false;
                mUpdateFiles.add(arrayList.get(intRef.element));
                mUpdateFilesName.add(arrayList1.get(intRef.element));
                mUpdateFilesSize.add(arrayList2.get(intRef.element));
                mUpdateFilesUrl.add("");
                intRef.element++;
                continue;
            }

            mDownloadingStatus = true;
            PRDownloader.download(downloadUrl, string.replace(arrayList1.get(intRef.element), ""), arrayList1.get(intRef.element)).build().setOnStartOrResumeListener(null).setOnPauseListener(null).setOnCancelListener(null).setOnProgressListener(new OnProgressListener() {
                @Override
                public void onProgress(Progress progress) {
                    mDownloadingStatus = true;
                    if(System.currentTimeMillis() - longRef.element > 100) {
                        longRef.element = System.currentTimeMillis();
                        Message obtain = Message.obtain(mInHandler, 4);
                        obtain.getData().putString("status", UpdateActivity.UpdateStatus.DownloadGameData.name());
                        obtain.getData().putBoolean("withProgress", true);
                        obtain.getData().putLong("current", longRef1.element+progress.currentBytes);
                        obtain.getData().putLong("total", mUpdateGameDataSize);
                        obtain.getData().putString("filename", (String)arrayList1.get(intRef.element));
                        obtain.getData().putLong("totalfiles", arrayList.size());
                        obtain.getData().putLong("currentfile", intRef.element);
                        if (mActivityMessenger != null) {
                            try {
                                mActivityMessenger.send(obtain);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }).start(new OnDownloadListener() {
                @Override
                public void onDownloadComplete() {
                    mDownloadingStatus = false;
                    longRef1.element += arrayList2.get(intRef.element);
                    Log.d("x1y2z", "completed");
                }

                @Override
                public void onError(Error error) {
                    mDownloadingStatus = false;
                    mUpdateFiles.add(arrayList.get(intRef.element));
                    mUpdateFilesName.add(arrayList1.get(intRef.element));
                    mUpdateFilesSize.add(arrayList2.get(intRef.element));
                    mUpdateFilesUrl.add(arrayList3.get(intRef.element));
                    Log.d("x1y2z", "error downloadgamedata: " + error);
                }
            });

            do {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (mDownloadingStatus);


            intRef.element++;

        }

        mDownloadingStatus = false;
        if (!mUpdateFiles.isEmpty()) {
            Log.d("x1y2z", "download incomplete, pending files: " + mUpdateFiles.size());
            setUpdateStatus(UpdateActivity.UpdateStatus.SourceUnavailable);
            return;
        }
        updateGame();
    }

    public void downloadGame()
    {
        Log.d("UpdateService", "downloadGame");
        if (mUpdateGameURL == null || mUpdateGameURL.trim().isEmpty()) {
            mDownloadingStatus = false;
            setUpdateStatus(UpdateActivity.UpdateStatus.SourceUnavailable);
            return;
        }
        mDownloadingStatus = true;

        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        file.getParentFile().mkdirs();
        if (file.exists()) {
            file.delete();
        }

        Ref.LongRef longRef = new Ref.LongRef();
        longRef.element = System.currentTimeMillis();

        mDownloadingStatus = true;
        PRDownloader.download(mUpdateGameURL, getExternalFilesDir(null) + "/download", "update.apk").build().setOnStartOrResumeListener(null).setOnPauseListener(null).setOnCancelListener(null).setOnProgressListener(new OnProgressListener() {
            @Override
            public void onProgress(Progress progress) {
                mDownloadingStatus = true;
                if(System.currentTimeMillis() - longRef.element > 100) {
                    longRef.element = System.currentTimeMillis();
                    Message obtain = Message.obtain(mInHandler, 4);
                    obtain.getData().putString("status", UpdateActivity.UpdateStatus.DownloadGame.name());
                    obtain.getData().putBoolean("withProgress", true);
                    obtain.getData().putLong("current", progress.currentBytes);
                    obtain.getData().putLong("total", progress.totalBytes);
                    obtain.getData().putString("filename", "update.apk");
                    obtain.getData().putLong("totalfiles", 1);
                    obtain.getData().putLong("currentfile", 1);
                    if (mActivityMessenger != null) {
                        try {
                            mActivityMessenger.send(obtain);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }).start(new OnDownloadListener() {
            @Override
            public void onDownloadComplete() {
                Message obtain = Message.obtain(UpdateService.this.mInHandler, 2);
                obtain.getData().putBoolean("status", true);
                obtain.getData().putString("apkPath", file.getAbsolutePath());
                obtain.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                }
                setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                mDownloadingStatus = false;
                Log.d("x1y2z", "completed");
            }

            @Override
            public void onError(Error error) {
                mDownloadingStatus = false;
                setUpdateStatus(UpdateActivity.UpdateStatus.SourceUnavailable);
                Log.d("x1y2z", "error downloadgame");
            }
        });

        do {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (mDownloadingStatus);

        mDownloadingStatus = false;
    }

    public boolean isGameUpdateExists() {
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo("com.xyron.game", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo == null) {
            return true;
        }
        Log.d("x1y2z", "isGameUpdateExists -> currentVersion " + packageInfo.versionCode + " | mUpdateVersion " + this.mUpdateVersion);
        return packageInfo.versionCode == this.mUpdateVersion ? false:true;
    }

    private void sendLoadingScreen(boolean unpacking, String filename, long current, long total) {
        new Thread(new Runnable() {
            public void run() {
                Message obtain = Message.obtain(UpdateService.this.mInHandler, 4);
                obtain.getData().putString("status", UpdateActivity.UpdateStatus.CheckUpdate.name());
                obtain.getData().putBoolean("withProgress", true);
                obtain.getData().putString("filename", filename);
                obtain.getData().putBoolean("unpacking", unpacking);
                obtain.getData().putLong("current", current);
                obtain.getData().putLong("total", total);
                obtain.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void resetUpdateState() {
        mDownloadingStatus = false;
        mUpdateGameDataSize = 0;
        mUpdateGameDataSizeUpdated = 0;
        mUpdateGameURL = "";
        mUpdateVersion = 0;
        mUpdateFiles = new ArrayList<>();
        mUpdateFilesName = new ArrayList<>();
        mUpdateFilesSize = new ArrayList<>();
        mUpdateFilesUrl = new ArrayList<>();
        Util.responseFiles = "";
        Util.responseFilesInt = 0;
    }


}
