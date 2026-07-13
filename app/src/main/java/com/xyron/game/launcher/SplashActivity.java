package com.xyron.game.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.ConfigValidator;
import com.xyron.game.launcher.util.DataVariantPreferences;
import com.xyron.game.launcher.util.GameDataVerifier;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SplashActivity extends SampActivity {

    private final String[] permissions = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.RECORD_AUDIO"
    };

    public int mGpuType;

    public IncomingHandler mInHandler;
    public Messenger mMessenger;
    public Messenger mService;
    private boolean isServiceBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            isServiceBound = true;
            checkUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        setContentView(R.layout.activity_splash);

        ((ProgressBar) findViewById(R.id.progressBar)).setIndeterminate(true);

        mInHandler = new IncomingHandler();
        mMessenger = new Messenger(mInHandler);

        boolean theme = mPref.getBoolean("theme", false);
        changeTheme(theme);

        GLSurfaceView.Renderer glRenderer = new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                UpdateActivity.eGPUType gpuType;
                String gpuName = gl10.glGetString(GL10.GL_EXTENSIONS);
                String extensions = gl10.glGetString(GL10.GL_EXTENSIONS);

                if (extensions.contains("GL_IMG_texture_compression_pvrtc")) {
                    gpuType = UpdateActivity.eGPUType.PVR;
                    mGpuType = 3;
                } else if (extensions.contains("GL_EXT_texture_compression_dxt1")
                        || extensions.contains("GL_EXT_texture_compression_s3tc")
                        || extensions.contains("GL_AMD_compressed_ATC_texture")) {
                    gpuType = UpdateActivity.eGPUType.DXT;
                    mGpuType = 1;
                } else {
                    gpuType = UpdateActivity.eGPUType.ETC;
                    mGpuType = 2;
                }

                Log.e("x1y2z", "GPU name: " + gpuName);
                Log.e("x1y2z", "GPU type: " + gpuType.name());

                if (isPermissionsGranted()) {
                    bindService(
                            new Intent(SplashActivity.this, UpdateService.class),
                            mConnection,
                            Context.BIND_AUTO_CREATE
                    );
                } else {
                    ActivityCompat.requestPermissions(SplashActivity.this, permissions, 1);
                }
            }

            @Override
            public void onSurfaceChanged(GL10 gl10, int width, int height) {
            }

            @Override
            public void onDrawFrame(GL10 gl10) {
            }
        };

        ConstraintLayout gpuLayout = findViewById(R.id.gpu);
        GLSurfaceView glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setRenderer(glRenderer);
        gpuLayout.addView(glSurfaceView);
    }

    public void changeTheme(boolean theme) {
        if (theme) {
            findViewById(R.id.main_splash_layout).setBackgroundResource(R.drawable.bg_blue);
            ((LinearProgressIndicator) findViewById(R.id.progressBarBlue)).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.main_splash_layout).setBackgroundResource(R.drawable.bg_red);
            ((LinearProgressIndicator) findViewById(R.id.progressBar)).setVisibility(View.VISIBLE);
        }
    }

    public class IncomingHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            if (msg.what == 4) {
                UpdateActivity.UpdateStatus status = UpdateActivity.UpdateStatus.valueOf(
                        msg.getData().getString("status", "")
                );

                if (status == UpdateActivity.UpdateStatus.Undefined) {
                    Message requestGameStatus = Message.obtain(null, 5);
                    requestGameStatus.replyTo = mMessenger;
                    try {
                        mService.send(requestGameStatus);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } else if (status == UpdateActivity.UpdateStatus.CheckUpdate) {
                    Message requestUpdateStatus = Message.obtain(null, 4);
                    requestUpdateStatus.replyTo = mMessenger;
                    try {
                        mService.send(requestUpdateStatus);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                return;
            }

            if (msg.what != 5) {
                return;
            }

            UpdateActivity.GameStatus gameStatus = UpdateActivity.GameStatus.valueOf(
                    msg.getData().getString("status", "")
            );
            Log.i("x1y2z", "gameStatus = " + gameStatus);

            if (gameStatus == UpdateActivity.GameStatus.UpdateRequired
                    || gameStatus == UpdateActivity.GameStatus.GameUpdateRequired) {
                startGameDataUpdate();
            } else if (gameStatus == UpdateActivity.GameStatus.Updated) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            } else if (hasRequiredGameData()) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            } else {
                showSourceUnavailableDialog();
            }
        }
    }

    public void checkUpdate() {
        Log.d("x1y2z", "checkUpdate");
        if (hasRequiredGameData()) {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            return;
        }

        Message obtain = Message.obtain(null, 0);
        obtain.getData().putInt("gputype", mGpuType);
        obtain.getData().putString("data_variant", DataVariantPreferences.DATA_VARIANT_LITE);
        obtain.replyTo = mMessenger;
        try {
            mService.send(obtain);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private boolean hasRequiredGameData() {
        ConfigValidator.validateConfigFiles(this);
        return GameDataVerifier.hasRequiredGameData(this);
    }

    public boolean isPermissionsGranted() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1) {
            return;
        }

        boolean granted = grantResults.length == this.permissions.length;
        for (int grantResult : grantResults) {
            granted &= grantResult == PackageManager.PERMISSION_GRANTED;
        }

        if (!granted) {
            Toast.makeText(this, "Permissoes nao concedidas.", Toast.LENGTH_LONG).show();
            return;
        }

        bindService(new Intent(this, UpdateService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    private void startGameDataUpdate() {
        Intent intent = new Intent(SplashActivity.this, UpdateActivity.class);
        intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
        startActivity(intent);
        finish();
    }

    private void showSourceUnavailableDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_update_prompt, null, false);
        TextView titleView = dialogView.findViewById(R.id.update_prompt_title);
        TextView bodyView = dialogView.findViewById(R.id.update_prompt_body);
        TextView primaryButton = dialogView.findViewById(R.id.update_prompt_primary);
        TextView secondaryButton = dialogView.findViewById(R.id.update_prompt_secondary);

        titleView.setText("Download indisponivel");
        bodyView.setText("Os arquivos do jogo ainda nao estao no aparelho e a fonte de download nao respondeu. Tente novamente ou configure o update_sources.json com sua URL do Hugging Face.");
        primaryButton.setText("Tentar de novo");
        secondaryButton.setText("Fechar");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        primaryButton.setOnTouchListener(new ButtonAnimator(this, primaryButton));
        secondaryButton.setOnTouchListener(new ButtonAnimator(this, secondaryButton));

        primaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            checkUpdate();
        });

        secondaryButton.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(mConnection);
            isServiceBound = false;
        }
    }
}
