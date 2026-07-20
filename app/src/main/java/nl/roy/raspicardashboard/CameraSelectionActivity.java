package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class CameraSelectionActivity extends Activity {
    private static final int CAMERA_REQUEST = 81;

    private SharedPreferences prefs;
    private CameraPreviewController controller;
    private Spinner cameraSpinner;
    private Spinner rotationSpinner;
    private Switch mirrorSwitch;
    private TextView statusText;
    private final List<String> cameraIds = new ArrayList<>();
    private boolean suppressSelection;

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

        controller = new CameraPreviewController(this, findViewById(R.id.cameraSelectPreview), (status, error) -> {
            statusText.setText(status);
            statusText.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
            statusText.setBackgroundResource(error ? R.drawable.status_warning_bg : R.drawable.camera_overlay_bg);
        });

        ArrayAdapter<String> rotationAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Rotatie 0°", "Rotatie 90°", "Rotatie 180°", "Rotatie 270°"});
        rotationSpinner.setAdapter(rotationAdapter);
        int rotation = prefs.getInt(SettingsActivity.PREF_CAMERA_ROTATION, 0);
        rotationSpinner.setSelection(Math.max(0, Math.min(3, rotation / 90)));
        mirrorSwitch.setChecked(prefs.getBoolean(SettingsActivity.PREF_CAMERA_MIRROR, false));

        cameraSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!suppressSelection) startPreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        mirrorSwitch.setOnCheckedChangeListener((button, checked) -> startPreview());
        rotationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!suppressSelection) startPreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        findViewById(R.id.saveCameraButton).setOnClickListener(v -> saveAndFinish());
        ThemeManager.apply(this);
        loadCameras();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else {
            startPreview();
        }
    }

    private void loadCameras() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        List<String> labels = new ArrayList<>();
        cameraIds.clear();
        try {
            for (String id : manager.getCameraIdList()) {
                cameraIds.add(id);
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                String type;
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_EXTERNAL) type = "USB / external";
                else if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) type = "voorcamera";
                else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) type = "achtercamera";
                else type = "camera";
                labels.add("Camera " + id + " — " + type);
            }
        } catch (CameraAccessException e) {
            labels.add("Camera’s konden niet worden gelezen");
        }
        suppressSelection = true;
        cameraSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        String selectedId = prefs.getString(SettingsActivity.PREF_CAMERA_ID, null);
        int selected = selectedId == null ? 0 : cameraIds.indexOf(selectedId);
        cameraSpinner.setSelection(Math.max(0, selected));
        suppressSelection = false;
    }

    private void startPreview() {
        if (controller == null || cameraIds.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        int position = Math.max(0, Math.min(cameraSpinner.getSelectedItemPosition(), cameraIds.size() - 1));
        controller.stop();
        controller.start(cameraIds.get(position), mirrorSwitch.isChecked(),
                rotationSpinner.getSelectedItemPosition() * 90,
                prefs.getString(SettingsActivity.PREF_CAMERA_SCALE, CameraPreviewController.SCALE_FIT),
                prefs.getString(SettingsActivity.PREF_CAMERA_ASPECT, CameraPreviewController.ASPECT_AUTO));
    }

    private void saveAndFinish() {
        if (cameraIds.isEmpty()) {
            Toast.makeText(this, "Geen camera gevonden", Toast.LENGTH_LONG).show();
            return;
        }
        int position = Math.max(0, Math.min(cameraSpinner.getSelectedItemPosition(), cameraIds.size() - 1));
        prefs.edit()
                .putString(SettingsActivity.PREF_CAMERA_ID, cameraIds.get(position))
                .putBoolean(SettingsActivity.PREF_CAMERA_MIRROR, mirrorSwitch.isChecked())
                .putInt(SettingsActivity.PREF_CAMERA_ROTATION, rotationSpinner.getSelectedItemPosition() * 90)
                .apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        ThemeManager.apply(this);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startPreview();
    }

    @Override protected void onStop() {
        if (controller != null) controller.stop();
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) startPreview();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
