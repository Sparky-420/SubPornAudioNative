package com.sparky.subpornaudionative;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

public class AudioCaptureService extends Service {

    public static final String ACTION_LEVEL = "com.sparky.subpornaudionative.LEVEL";
    public static final String ACTION_STOP = "com.sparky.subpornaudionative.STOP";

    private static final String CHANNEL_ID = "subporn_audio_capture";
    private static final int NOTIFICATION_ID = 420;

    private AudioRecord audioRecord;
    private MediaProjection mediaProjection;
    private Thread captureThread;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent resultData = intent.getParcelableExtra("resultData");

        if (resultCode == 0 || resultData == null) {
            broadcastStatus("Sin permiso MediaProjection", 0);
            stopSelf();
            return START_NOT_STICKY;
        }

        startInternalAudioCapture(resultCode, resultData);

        return START_STICKY;
    }

    private void startInternalAudioCapture(int resultCode, Intent resultData) {
        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            mediaProjection = manager.getMediaProjection(resultCode, resultData);

            AudioPlaybackCaptureConfiguration config =
                    new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            int minBufferSize = AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            int bufferSize = Math.max(minBufferSize * 2, 4096);

            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();

            audioRecord.startRecording();
            running = true;

            captureThread = new Thread(() -> readLoop(bufferSize), "SubPornAudioCaptureThread");
            captureThread.start();

            broadcastStatus("Captura interna iniciada", 0);

        } catch (Exception e) {
            broadcastStatus("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage(), 0);
            stopSelf();
        }
    }

    private void readLoop(int bufferSize) {
        short[] buffer = new short[bufferSize / 2];

        while (running && audioRecord != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);

            if (read > 0) {
                long sum = 0;

                for (int i = 0; i < read; i++) {
                    sum += Math.abs(buffer[i]);
                }

                int avg = (int) (sum / read);
                int level = Math.min(100, (avg * 100) / 12000);

                broadcastStatus("Recibiendo audio interno", level);
            } else {
                broadcastStatus("Sin muestras de audio", 0);
            }

            try {
                Thread.sleep(160);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void broadcastStatus(String status, int level) {
        Intent intent = new Intent(ACTION_LEVEL);
        intent.setPackage(getPackageName());
        intent.putExtra("status", status);
        intent.putExtra("level", level);
        sendBroadcast(intent);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, AudioCaptureService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("SubPorn Audio Native")
                .setContentText("Capturando audio interno")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SubPorn Audio Capture",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        running = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {}

            try {
                audioRecord.release();
            } catch (Exception ignored) {}

            audioRecord = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {}

            mediaProjection = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
