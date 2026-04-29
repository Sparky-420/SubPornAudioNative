package com.sparky.subpornaudionative;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView detailText;
    private TextView counterText;
    private TextView rawText;
    private TextView blockStatusText;
    private ProgressBar levelBar;
    private TextView levelText;
    private EditText urlInput;

    private TextView micStatusText;
    private TextView micDetailText;
    private TextView micCounterText;
    private ProgressBar micLevelBar;
    private TextView micLevelText;

    private AudioRecord micRecord;
    private Thread micThread;
    private volatile boolean micRunning = false;

    private long micTotalReads = 0;
    private long micAudioReads = 0;
    private long micSilentReads = 0;
    private long micZeroReads = 0;
    private long micErrorReads = 0;

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

            boolean blockedSource = totalReads >= 120
                    && audioReads == 0
                    && silentReads >= 120
                    && zeroReads == 0
                    && errorReads == 0
                    && avg == 0
                    && max == 0;

            if (blockedSource) {
                statusText.setText("Fuente bloqueada / silencio forzado");
                blockStatusText.setText("Diagnóstico: la fuente sí entrega buffers, pero Android los entrega en cero. Usa micrófono fallback.");
                blockStatusText.setTextColor(Color.rgb(255, 210, 64));
            } else {
                statusText.setText(status != null ? status : "Sin estado");
                blockStatusText.setText("Diagnóstico: esperando patrón suficiente para decidir si la fuente está bloqueada.");
                blockStatusText.setTextColor(Color.rgb(180, 180, 180));
            }

            detailText.setText("Nivel interno: " + level + "% | AVG: " + avg + " | MAX: " + max);
            levelText.setText(level + "%");
            levelBar.setProgress(level);

            counterText.setText(
                    "Lecturas totales: " + totalReads +
                    "\nCon audio: " + audioReads +
                    "\nSilencio: " + silentReads +
                    "\nSin muestras: " + zeroReads +
                    "\nErrores: " + errorReads
            );

            rawText.setText("Último dato interno: " + (last != null ? last : "sin dato"));
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

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
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
                "Primero permisos, luego captura, luego prueba YouTube vs WebView.",
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
                12,
                false
        );

        rawText = makeText(
                "Último dato interno: sin dato",
                14,
                Color.rgb(180, 180, 180),
                0,
                14,
                false
        );

        blockStatusText = makeText(
                "Diagnóstico: esperando captura interna.",
                14,
                Color.rgb(180, 180, 180),
                0,
                20,
                true
        );

        Button permissionButton = makeButton("1. Pedir permisos");
        permissionButton.setOnClickListener(v -> requestBasePermissions());

        Button startButton = makeButton("2. Iniciar captura interna");
        startButton.setOnClickListener(v -> requestMediaProjection());

        Button stopButton = makeButton("3. Detener captura interna");
        stopButton.setOnClickListener(v -> stopCapture());

        urlInput = new EditText(this);
        urlInput.setHint("URL para prueba WebView");
        urlInput.setSingleLine(true);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.GRAY);
        urlInput.setText("https://www.google.com");
        urlInput.setPadding(20, 16, 20, 16);

        Button webButton = makeButton("4. Abrir WebView de prueba");
        webButton.setOnClickListener(v -> openWebTest());

        TextView webLabel = makeText(
                "WebView de prueba",
                17,
                Color.WHITE,
                24,
                8,
                true
        );

        TextView webHelp = makeText(
                "Inicia captura interna antes de abrir la WebView. Luego reproduce un video y vuelve para revisar números.",
                13,
                Color.LTGRAY,
                0,
                12,
                false
        );

        TextView micLabel = makeText(
                "Micrófono fallback",
                17,
                Color.WHITE,
                28,
                8,
                true
        );

        TextView micHelp = makeText(
                "Úsalo cuando el audio interno diga fuente bloqueada o silencio forzado.",
                13,
                Color.LTGRAY,
                0,
                12,
                false
        );

        micStatusText = makeText(
                "Micrófono detenido",
                18,
                Color.rgb(119, 224, 212),
                8,
                8,
                true
        );

        micDetailText = makeText(
                "Nivel mic: 0% | AVG: 0 | MAX: 0",
                14,
                Color.LTGRAY,
                0,
                8,
                false
        );

        micLevelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        micLevelBar.setMax(100);
        micLevelBar.setProgress(0);
        micLevelBar.setPadding(0, 10, 0, 10);

        micLevelText = makeText("Mic: 0%", 18, Color.WHITE, 0, 10, true);

        micCounterText = makeText(
                "Mic lecturas: 0\nMic con audio: 0\nMic silencio: 0\nMic sin muestras: 0\nMic errores: 0",
                14,
                Color.rgb(220, 220, 220),
                0,
                12,
                false
        );

        Button micStartButton = makeButton("5. Probar micrófono fallback");
        micStartButton.setOnClickListener(v -> startMicFallback());

        Button micStopButton = makeButton("6. Detener micrófono fallback");
        micStopButton.setOnClickListener(v -> stopMicFallback());

        root.addView(title);
        root.addView(statusText);
        root.addView(detailText);
        root.addView(levelBar);
        root.addView(levelText);
        root.addView(counterText);
        root.addView(rawText);
        root.addView(blockStatusText);
        root.addView(permissionButton);
        root.addView(startButton);
        root.addView(stopButton);
        root.addView(webLabel);
        root.addView(webHelp);
        root.addView(urlInput);
        root.addView(webButton);
        root.addView(micLabel);
        root.addView(micHelp);
        root.addView(micStatusText);
        root.addView(micDetailText);
        root.addView(micLevelBar);
        root.addView(micLevelText);
        root.addView(micCounterText);
        root.addView(micStartButton);
        root.addView(micStopButton);

        scrollView.addView(root);
        setContentView(scrollView);
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
        detailText.setText("Nivel interno: 0% | AVG: 0 | MAX: 0");
        levelBar.setProgress(0);
        levelText.setText("0%");
        rawText.setText("Último dato interno: captura detenida");
    }

    private void openWebTest() {
        String url = urlInput != null ? urlInput.getText().toString().trim() : "";

        if (url.length() == 0) {
            url = "https://www.google.com";
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Intent intent = new Intent(this, WebTestActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    private void startMicFallback() {
        if (!hasRecordAudioPermission()) {
            micStatusText.setText("Falta RECORD_AUDIO");
            micDetailText.setText("Primero toca '1. Pedir permisos' y acepta micrófono.");
            return;
        }

        if (micRunning) {
            micStatusText.setText("Micrófono ya está activo");
            return;
        }

        try {
            int sampleRate = 44100;
            int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (minBufferSize <= 0) {
                micStatusText.setText("Error micrófono");
                micDetailText.setText("Buffer inválido: " + minBufferSize);
                return;
            }

            int bufferSize = Math.max(minBufferSize * 2, 8192);

            micRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            micRecord.startRecording();

            micTotalReads = 0;
            micAudioReads = 0;
            micSilentReads = 0;
            micZeroReads = 0;
            micErrorReads = 0;
            micRunning = true;

            micStatusText.setText("Micrófono fallback activo");
            micThread = new Thread(() -> micReadLoop(bufferSize), "SubPornMicFallbackThread");
            micThread.start();

        } catch (Exception e) {
            micStatusText.setText("Error iniciando micrófono");
            micDetailText.setText(e.getClass().getSimpleName() + ": " + e.getMessage());
            stopMicFallback();
        }
    }

    private void micReadLoop(int bufferSize) {
        short[] buffer = new short[bufferSize / 2];

        while (micRunning && micRecord != null) {
            int read = micRecord.read(buffer, 0, buffer.length);

            if (read > 0) {
                micTotalReads++;

                long sum = 0;
                int max = 0;

                for (int i = 0; i < read; i++) {
                    int abs = Math.abs(buffer[i]);
                    sum += abs;
                    if (abs > max) {
                        max = abs;
                    }
                }

                int avg = (int) (sum / read);
                int level = Math.min(100, (avg * 100) / 7000);

                if (avg > 12 || max > 100) {
                    micAudioReads++;
                } else {
                    micSilentReads++;
                }

                updateMicUi(level, avg, max, "read=" + read);

            } else if (read == 0) {
                micZeroReads++;
                updateMicUi(0, 0, 0, "read=0");

            } else {
                micErrorReads++;
                updateMicUi(0, 0, 0, "read=" + read);
            }

            try {
                Thread.sleep(180);
            } catch (InterruptedException ignored) {}
        }
    }

    private void updateMicUi(int level, int avg, int max, String last) {
        runOnUiThread(() -> {
            micStatusText.setText(level > 0 ? "Micrófono detectando audio" : "Micrófono capturando silencio");
            micDetailText.setText("Nivel mic: " + level + "% | AVG: " + avg + " | MAX: " + max + " | " + last);
            micLevelText.setText("Mic: " + level + "%");
            micLevelBar.setProgress(level);
            micCounterText.setText(
                    "Mic lecturas: " + micTotalReads +
                    "\nMic con audio: " + micAudioReads +
                    "\nMic silencio: " + micSilentReads +
                    "\nMic sin muestras: " + micZeroReads +
                    "\nMic errores: " + micErrorReads
            );
        });
    }

    private void stopMicFallback() {
        micRunning = false;

        if (micRecord != null) {
            try {
                micRecord.stop();
            } catch (Exception ignored) {}

            try {
                micRecord.release();
            } catch (Exception ignored) {}

            micRecord = null;
        }

        micStatusText.setText("Micrófono fallback detenido");
        micDetailText.setText("Nivel mic: 0% | AVG: 0 | MAX: 0");
        micLevelText.setText("Mic: 0%");
        micLevelBar.setProgress(0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            if (hasRecordAudioPermission()) {
                statusText.setText("Permisos base concedidos");
                detailText.setText("Ahora toca '2. Iniciar captura interna'.");
                micStatusText.setText("Micrófono listo para fallback");
            } else {
                statusText.setText("Permiso de micrófono denegado");
                detailText.setText("Sin RECORD_AUDIO no se puede capturar audio.");
                micStatusText.setText("Micrófono denegado");
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

        stopMicFallback();
        super.onDestroy();
    }
}
