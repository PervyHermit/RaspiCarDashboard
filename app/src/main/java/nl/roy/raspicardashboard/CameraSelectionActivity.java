package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Safe camera picker with a coalesced live preview for USB/external cameras. */
public final class CameraSelectionActivity extends Activity {
    private static final int CAMERA_REQUEST = 81;
    private static final long PREVIEW_DELAY_MS = 300L;

    private SharedPreferences prefs;
    private CameraPreviewController controller;
    private Spinner cameraSpinner;
    private Spinner rotationSpinner;
    private Switch mirrorSwitch;
    private TextView statusText;
    private final List<String> cameraIds = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean binding;
    private boolean resumed;
    private boolean previewScheduled;

    private final Runnable previewRunnable = () -> {
        previewScheduled = false;
        startPreviewSafely();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_selection);
        hideSystemBars();

        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        cameraSpinner = findViewById(R.id.cameraSpinner);
        rotationSpinner = findViewById(R.id.cameraRotationSpinner);
        mirrorSwitch = findViewById(R.id.cameraMirrorSwitch);
        statusText = findViewById(R.id.cameraSelectStatus);

        controller = new CameraPreviewController(this, findViewById(R.id.cameraSelectPreview),
                this::showStatus);

        binding = true;
        rotationSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Rotatie 0°", "Rotatie 90°", "Rotatie 180°", "Rotatie 270°"}));
        int rotation = prefs.getInt(SettingsActivity.PREF_CAMERA_ROTATION, 0);
        rotationSpinner.setSelection(Math.max(0, Math.min(3, rotation / 90)));
        mirrorSwitch.setChecked(prefs.getBoolean(SettingsActivity.PREF_CAMERA_MIRROR, false));
        loadCamerasSafely();
        binding = false;

        cameraSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (!binding) schedulePreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        mirrorSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) schedulePreview();
        });
        rotationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (!binding) schedulePreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        findViewById(R.id.saveCameraButton).setOnClickListener(v -> saveAndFinish());
        ThemeManager.apply(this);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showStatus("Geef cameratoegang om de preview te tonen", false);
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else {
            schedulePreview();
        }
    }

    private void loadCamerasSafely() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        List<String> labels = new ArrayList<>();
        cameraIds.clear();

        if (manager == null) {
            labels.add("Cameraservice niet beschikbaar");
            bindCameraAdapter(labels);
            return;
        }

        try {
            for (String id : manager.getCameraIdList()) {
                if (id == null) continue;
                cameraIds.add(id);
                String type = "camera";
                try {
                    Integer facing = manager.getCameraCharacteristics(id)
                            .get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                        type = "USB / external";
                    } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        type = "voorcamera";
                    } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        type = "achtercamera";
                    }
                } catch (CameraAccessException | RuntimeException ignored) {
                    type = "camera (details onbekend)";
                }
                labels.add("Camera " + id + " — " + type);
            }
        } catch (CameraAccessException e) {
            labels.add("Camera’s konden niet worden gelezen");
            showStatus("Android kon de cameralijst niet openen", true);
        } catch (RuntimeException e) {
            labels.add("Cameraservice gaf een fout");
            showStatus("De cameraservice reageerde onverwacht", true);
        }

        if (labels.isEmpty()) labels.add("Geen camera gevonden");
        bindCameraAdapter(labels);
    }

    private void bindCameraAdapter(List<String> labels) {
        cameraSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        cameraSpinner.setEnabled(!cameraIds.isEmpty());

        String selectedId = prefs.getString(SettingsActivity.PREF_CAMERA_ID, null);
        int selected = selectedId == null ? 0 : cameraIds.indexOf(selectedId);
        if (selected < 0) selected = 0;
        cameraSpinner.setSelection(selected, false);
    }

    private void schedulePreview() {
        if (!resumed || cameraIds.isEmpty()
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mainHandler.removeCallbacks(previewRunnable);
        previewScheduled = true;
        mainHandler.postDelayed(previewRunnable, PREVIEW_DELAY_MS);
    }

    private void startPreviewSafely() {
        if (!resumed || controller == null || cameraIds.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        int selectedPosition = cameraSpinner.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= cameraIds.size()) selectedPosition = 0;

        try {
            controller.start(cameraIds.get(selectedPosition), mirrorSwitch.isChecked(),
                    Math.max(0, rotationSpinner.getSelectedItemPosition()) * 90,
                    prefs.getString(SettingsActivity.PREF_CAMERA_SCALE,
                            CameraPreviewController.SCALE_FIT),
                    prefs.getString(SettingsActivity.PREF_CAMERA_ASPECT,
                            CameraPreviewController.ASPECT_AUTO));
        } catch (RuntimeException e) {
            showStatus("Camera-preview kon niet worden gestart", true);
        }
    }

    private void showStatus(String status, boolean error) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            statusText.setText(status == null ? "" : status);
            statusText.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
            statusText.setBackgroundResource(error
                    ? R.drawable.status_warning_bg : R.drawable.camera_overlay_bg);
        });
    }

    private void saveAndFinish() {
        if (cameraIds.isEmpty()) {
            Toast.makeText(this, "Geen camera gevonden", Toast.LENGTH_LONG).show();
            return;
        }
        int position = cameraSpinner.getSelectedItemPosition();
        if (position < 0 || position >= cameraIds.size()) position = 0;
        prefs.edit()
                .putString(SettingsActivity.PREF_CAMERA_ID, cameraIds.get(position))
                .putBoolean(SettingsActivity.PREF_CAMERA_MIRROR, mirrorSwitch.isChecked())
                .putInt(SettingsActivity.PREF_CAMERA_ROTATION,
                        Math.max(0, rotationSpinner.getSelectedItemPosition()) * 90)
                .apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        hideSystemBars();
        ThemeManager.apply(this);
        schedulePreview();
    }

    @Override protected void onPause() {
        resumed = false;
        if (previewScheduled) {
            mainHandler.removeCallbacks(previewRunnable);
            previewScheduled = false;
        }
        if (controller != null) controller.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (controller != null) controller.stop();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            schedulePreview();
        } else {
            showStatus("Cameratoestemming is geweigerd", true);
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
