package com.sparky.subpornaudionative;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView detailText;
    private ProgressBar levelBar;
    private TextView levelText;

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_MEDIA_PROJECTION = 1002;

    private final BroadcastReceiver levelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            int level = intent.getIntExtra("level", 0);

            statusText.setText(status != null ? status : "Sin estado");
            detailText.setText("Nivel de audio interno: " + level + "%");
            levelText.setText(level + "%");
            levelBar.setProgress(level);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        IntentFilter filter = new IntentFilter(AudioCaptureService.ACTION_LEVEL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(levelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(levelReceiver, filter);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.rgb(5, 5, 5));

        TextView title = new TextView(this);
        title.setText("SubPorn Audio Native");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);

        statusText = new TextView(this);
        statusText.setText("Listo para capturar audio interno");
        statusText.setTextColor(Color.rgb(119, 224, 212));
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 30, 0, 20);

        detailText = new TextView(this);
        detailText.setText("Pulsa permisos, luego inicia captura interna y reproduce un video.");
        detailText.setTextColor(Color.LTGRAY);
        detailText.setTextSize(16);
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, 0, 0, 20);

        levelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        levelBar.setProgress(0);
        levelBar.setPadding(0, 20, 0, 20);

        levelText = new TextView(this);
        levelText.setText("0%");
        levelText.setTextColor(Color.WHITE);
        levelText.setTextSize(18);
        levelText.setGravity(Gravity.CENTER);
        levelText.setPadding(0, 0, 0, 30);

        Button permissionButton = new Button(this);
        permissionButton.setText("1. Pedir permisos");
        permissionButton.setOnClickListener(v -> requestBasePermissions());

        Button startButton = new Button(this);
        startButton.setText("2. Iniciar captura interna");
        startButton.setOnClickListener(v -> requestMediaProjection());

        Button stopButton = new Button(this);
        stopButton.setText("3. Detener captura");
        stopButton.setOnClickListener(v -> stopCapture());

        root.addView(title);
        root.addView(statusText);
        root.addView(detailText);
        root.addView(levelBar);
        root.addView(levelText);
        root.addView(permissionButton);
        root.addView(startButton);
        root.addView(stopButton);

        setContentView(root);
    }

    private void requestBasePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    REQ_PERMISSIONS
            );
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQ_PERMISSIONS
            );
        }
    }

    private boolean hasRecordAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaProjection() {
        if (!hasRecordAudioPermission()) {
            statusText.setText("Falta RECORD_AUDIO");
            detailText.setText("Primero toca '1. Pedir permisos' y acepta micrófono.");
            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Intent intent = manager.createScreenCaptureIntent();
        startActivityForResult(intent, REQ_MEDIA_PROJECTION);
    }

    private void stopCapture() {
        Intent intent = new Intent(this, AudioCaptureService.class);
        stopService(intent);

        statusText.setText("Captura detenida");
        detailText.setText("Nivel de audio interno: 0%");
        levelBar.setProgress(0);
        levelText.setText("0%");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            if (hasRecordAudioPermission()) {
                statusText.setText("Permisos base concedidos");
                detailText.setText("Ahora toca '2. Iniciar captura interna'.");
            } else {
                statusText.setText("Permiso de micrófono denegado");
                detailText.setText("Sin RECORD_AUDIO no se puede capturar audio.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                statusText.setText("MediaProjection permitido");
                detailText.setText("Iniciando servicio de captura interna...");

                Intent serviceIntent = new Intent(this, AudioCaptureService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("resultData", data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                statusText.setText("MediaProjection denegado");
                detailText.setText("Sin este permiso no se puede capturar audio interno.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(levelReceiver);
        } catch (Exception ignored) {}

        super.onDestroy();
    }
}
