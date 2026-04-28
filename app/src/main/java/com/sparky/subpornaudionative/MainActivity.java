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
    private TextView counterText;
    private TextView rawText;
    private ProgressBar levelBar;
    private TextView levelText;

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_MEDIA_PROJECTION = 1002;

    private final BroadcastReceiver levelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            int level = intent.getIntExtra("level", 0);
            int avg = intent.getIntExtra("avg", 0);
            int max = intent.getIntExtra("max", 0);
            String last = intent.getStringExtra("last");

            long totalReads = intent.getLongExtra("totalReads", 0);
            long audioReads = intent.getLongExtra("audioReads", 0);
            long silentReads = intent.getLongExtra("silentReads", 0);
            long zeroReads = intent.getLongExtra("zeroReads", 0);
            long errorReads = intent.getLongExtra("errorReads", 0);

            statusText.setText(status != null ? status : "Sin estado");
            detailText.setText("Nivel: " + level + "% | AVG: " + avg + " | MAX: " + max);
            levelText.setText(level + "%");
            levelBar.setProgress(level);

            counterText.setText(
                    "Lecturas totales: " + totalReads +
                    "\nCon audio: " + audioReads +
                    "\nSilencio: " + silentReads +
                    "\nSin muestras: " + zeroReads +
                    "\nErrores: " + errorReads
            );

            rawText.setText("Último dato: " + (last != null ? last : "sin dato"));
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

    private TextView makeText(String text, int size, int color, int top, int bottom, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, top, 0, bottom);
        if (bold) {
            tv.setTypeface(null, 1);
        }
        return tv;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(34, 34, 34, 34);
        root.setBackgroundColor(Color.rgb(5, 5, 5));

        TextView title = makeText("SubPorn Audio Native", 27, Color.WHITE, 0, 18, true);

        statusText = makeText(
                "Listo para diagnóstico profundo",
                20,
                Color.rgb(119, 224, 212),
                10,
                14,
                true
        );

        detailText = makeText(
                "Primero permisos, luego captura, luego prueba YouTube vs sitio objetivo.",
                15,
                Color.LTGRAY,
                0,
                16,
                false
        );

        levelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        levelBar.setProgress(0);
        levelBar.setPadding(0, 15, 0, 15);

        levelText = makeText("0%", 20, Color.WHITE, 0, 20, true);

        counterText = makeText(
                "Lecturas totales: 0\nCon audio: 0\nSilencio: 0\nSin muestras: 0\nErrores: 0",
                15,
                Color.rgb(220, 220, 220),
                4,
                18,
                false
        );

        rawText = makeText(
                "Último dato: sin dato",
                14,
                Color.rgb(180, 180, 180),
                0,
                20,
                false
        );

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
        root.addView(counterText);
        root.addView(rawText);
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
        detailText.setText("Nivel: 0% | AVG: 0 | MAX: 0");
        levelBar.setProgress(0);
        levelText.setText("0%");
        rawText.setText("Último dato: captura detenida");
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
