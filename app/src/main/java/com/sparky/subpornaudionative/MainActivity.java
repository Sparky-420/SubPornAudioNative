package com.sparky.subpornaudionative;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView detailText;
    private static final int REQ_RECORD_AUDIO = 1001;
    private static final int REQ_MEDIA_PROJECTION = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        statusText.setText("Prueba nativa lista");
        statusText.setTextColor(Color.rgb(119, 224, 212));
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 30, 0, 20);

        detailText = new TextView(this);
        detailText.setText("Esta APK probará permisos nativos antes de capturar audio interno.");
        detailText.setTextColor(Color.LTGRAY);
        detailText.setTextSize(16);
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, 0, 0, 30);

        Button micButton = new Button(this);
        micButton.setText("Pedir permiso de micrófono");
        micButton.setOnClickListener(v -> requestMicPermission());

        Button projectionButton = new Button(this);
        projectionButton.setText("Pedir captura interna / MediaProjection");
        projectionButton.setOnClickListener(v -> requestMediaProjection());

        root.addView(title);
        root.addView(statusText);
        root.addView(detailText);
        root.addView(micButton);
        root.addView(projectionButton);

        setContentView(root);
    }

    private void requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            statusText.setText("Micrófono permitido");
            detailText.setText("Permiso RECORD_AUDIO ya está concedido.");
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        }
    }

    private void requestMediaProjection() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = manager.createScreenCaptureIntent();
        startActivityForResult(intent, REQ_MEDIA_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.setText("Micrófono permitido");
                detailText.setText("Android concedió RECORD_AUDIO.");
            } else {
                statusText.setText("Micrófono denegado");
                detailText.setText("Sin micrófono no podremos procesar audio.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                statusText.setText("MediaProjection permitido");
                detailText.setText("El sistema permitió captura. Siguiente capa: AudioPlaybackCapture.");
            } else {
                statusText.setText("MediaProjection denegado");
                detailText.setText("Sin este permiso no se puede capturar audio interno.");
            }
        }
    }
}
