package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    static final String PREFS = "dashboard_prefs";
    static final String PREF_SETUP_COMPLETE = "setup_complete";
    static final String PREF_AUTO_WAZE = "auto_waze";
    static final String PREF_START_GPS = "start_gps_connector";
    static final String PREF_WEATHER = "weather_enabled";
    static final String PREF_EXTERNAL_RETURN_OVERLAY = "external_return_overlay";
    static final String PREF_MEDIA_SOURCE = "media_source";
    static final String PREF_LOCAL_MEDIA_TREE = "local_media_tree";
    static final String MEDIA_SOURCE_SPOTIFY = "spotify";
    static final String MEDIA_SOURCE_LOCAL = "local";
    static final String MEDIA_SOURCE_AUTO = "auto";
    static final String PREF_LAYOUT_SIZE = "layout_size";
    static final String PREF_CAMERA_ID = "camera_id";
    static final String PREF_CAMERA_MIRROR = "camera_mirror";
    static final String PREF_CAMERA_ROTATION = "camera_rotation";
    static final String PREF_CAMERA_SCALE = "camera_scale";
    static final String PREF_CAMERA_ASPECT = "camera_aspect";
    static final String PREF_CAMERA_WIDTH_PERCENT = "camera_width_percent";
    static final String PREF_HEADER_HEIGHT_DP = "header_height_dp";
    static final String PREF_APPS_HEIGHT_DP = "apps_height_dp";
    static final String PREF_DIM_MODE = "dim_mode";
    static final String PREF_DIM_PERCENT = "dim_percent";
    static final String PREF_DIM_DAY_PERCENT = "dim_day_percent";
    static final String PREF_DIM_NIGHT_PERCENT = "dim_night_percent";
    static final String PREF_DIM_SUN_OFFSET = "dim_sun_offset";
    static final String PREF_LAST_LATITUDE = "last_latitude";
    static final String PREF_LAST_LONGITUDE = "last_longitude";

    static final String DIM_MODE_OFF = "off";
    static final String DIM_MODE_MANUAL = "manual";
    static final String DIM_MODE_AUTO = "auto";

    private SharedPreferences prefs;
    private Switch autoWazeSwitch;
    private Switch startGpsSwitch;
    private Switch weatherSwitch;
    private Switch externalReturnSwitch;
    private Spinner mediaSourceSpinner;
    private Spinner cameraScaleSpinner;
    private Spinner cameraAspectSpinner;
    private SeekBar cameraWidthSeek;
    private TextView cameraWidthLabel;
    private Spinner layoutSpinner;
    private SeekBar headerHeightSeek;
    private SeekBar appsHeightSeek;
    private TextView headerHeightLabel;
    private TextView appsHeightLabel;
    private Spinner themeSpinner;
    private Spinner dimModeSpinner;
    private SeekBar manualDimSeek;
    private SeekBar dayDimSeek;
    private SeekBar nightDimSeek;
    private SeekBar sunOffsetSeek;
    private TextView manualDimLabel;
    private TextView dayDimLabel;
    private TextView nightDimLabel;
    private TextView sunOffsetLabel;
    private TextView cameraSelectionText;
    private TextView statusText;
    private EditText customAccentInput;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        hideSystemBars();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        migrateLegacyPreferences(prefs);
        bindViews();
        configureSpinners();
        loadValues();
        configureListeners();
        ThemeManager.apply(this);
        refreshLabels();
        refreshStatus();
    }


    static void migrateLegacyPreferences(SharedPreferences prefs) {
        if (prefs.contains(PREF_DIM_MODE)) return;
        boolean legacyEnabled = prefs.getBoolean("dim_enabled", false);
        prefs.edit().putString(PREF_DIM_MODE, legacyEnabled ? DIM_MODE_MANUAL : DIM_MODE_OFF).apply();
    }

    private void bindViews() {
        autoWazeSwitch = findViewById(R.id.autoWazeSwitch);
        startGpsSwitch = findViewById(R.id.startGpsSwitch);
        weatherSwitch = findViewById(R.id.weatherSwitch);
        externalReturnSwitch = findViewById(R.id.externalReturnSwitch);
        mediaSourceSpinner = findViewById(R.id.mediaSourceSpinner);
        cameraScaleSpinner = findViewById(R.id.cameraScaleSpinner);
        cameraAspectSpinner = findViewById(R.id.cameraAspectSpinner);
        cameraWidthSeek = findViewById(R.id.cameraWidthSeek);
        cameraWidthLabel = findViewById(R.id.cameraWidthLabel);
        layoutSpinner = findViewById(R.id.layoutSpinner);
        headerHeightSeek = findViewById(R.id.headerHeightSeek);
        appsHeightSeek = findViewById(R.id.appsHeightSeek);
        headerHeightLabel = findViewById(R.id.headerHeightLabel);
        appsHeightLabel = findViewById(R.id.appsHeightLabel);
        themeSpinner = findViewById(R.id.themeSpinner);
        dimModeSpinner = findViewById(R.id.dimModeSpinner);
        manualDimSeek = findViewById(R.id.manualDimSeek);
        dayDimSeek = findViewById(R.id.dayDimSeek);
        nightDimSeek = findViewById(R.id.nightDimSeek);
        sunOffsetSeek = findViewById(R.id.sunOffsetSeek);
        manualDimLabel = findViewById(R.id.manualDimLabel);
        dayDimLabel = findViewById(R.id.dayDimLabel);
        nightDimLabel = findViewById(R.id.nightDimLabel);
        sunOffsetLabel = findViewById(R.id.sunOffsetLabel);
        cameraSelectionText = findViewById(R.id.cameraSelectionText);
        statusText = findViewById(R.id.statusText);
        customAccentInput = findViewById(R.id.customAccentInput);
    }

    private void configureSpinners() {
        mediaSourceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Mediabron: Spotify", "Mediabron: lokale muziek", "Mediabron: automatisch"}));
        cameraScaleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Camera: volledig beeld (geen vervorming)", "Camera: paneel vullen (bijsnijden)"}));
        cameraAspectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Cameraformaat: automatisch", "Cameraformaat: 4:3", "Cameraformaat: 16:9"}));
        layoutSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Layout: automatisch", "Layout: compact", "Layout: medium", "Layout: large"}));
        themeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Thema: Dark Blue", "Thema: Graphite", "Thema: Orange", "Thema: Green",
                        "Thema: Red", "Thema: Purple", "Thema: eigen accent"}));
        dimModeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Dimmen: uit", "Dimmen: handmatig", "Dimmen: automatisch op zon"}));
    }

    private void loadValues() {
        binding = true;
        autoWazeSwitch.setChecked(prefs.getBoolean(PREF_AUTO_WAZE, true));
        startGpsSwitch.setChecked(prefs.getBoolean(PREF_START_GPS, true));
        weatherSwitch.setChecked(prefs.getBoolean(PREF_WEATHER, true));
        externalReturnSwitch.setChecked(prefs.getBoolean(PREF_EXTERNAL_RETURN_OVERLAY, true));
        mediaSourceSpinner.setSelection(mediaSourceIndex(prefs.getString(PREF_MEDIA_SOURCE, MEDIA_SOURCE_SPOTIFY)));
        cameraScaleSpinner.setSelection(CameraPreviewController.SCALE_FILL.equals(
                prefs.getString(PREF_CAMERA_SCALE, CameraPreviewController.SCALE_FIT)) ? 1 : 0);
        String cameraAspect = prefs.getString(PREF_CAMERA_ASPECT, CameraPreviewController.ASPECT_AUTO);
        cameraAspectSpinner.setSelection(CameraPreviewController.ASPECT_4_3.equals(cameraAspect) ? 1
                : CameraPreviewController.ASPECT_16_9.equals(cameraAspect) ? 2 : 0);
        cameraWidthSeek.setProgress(Math.max(45, prefs.getInt(PREF_CAMERA_WIDTH_PERCENT, 100)) - 45);
        layoutSpinner.setSelection(layoutIndex(prefs.getString(PREF_LAYOUT_SIZE, "auto")));
        headerHeightSeek.setProgress(Math.max(60, Math.min(120,
                prefs.getInt(PREF_HEADER_HEIGHT_DP, 82))) - 60);
        appsHeightSeek.setProgress(Math.max(110, Math.min(240,
                prefs.getInt(PREF_APPS_HEIGHT_DP, 164))) - 110);
        themeSpinner.setSelection(themeIndex(prefs.getString(ThemeManager.PREF_THEME, ThemeManager.THEME_BLUE)));
        dimModeSpinner.setSelection(dimModeIndex(prefs.getString(PREF_DIM_MODE, DIM_MODE_OFF)));
        manualDimSeek.setProgress(prefs.getInt(PREF_DIM_PERCENT, 35));
        dayDimSeek.setProgress(prefs.getInt(PREF_DIM_DAY_PERCENT, 0));
        nightDimSeek.setProgress(prefs.getInt(PREF_DIM_NIGHT_PERCENT, 45));
        sunOffsetSeek.setProgress(prefs.getInt(PREF_DIM_SUN_OFFSET, 0) + 120);
        customAccentInput.setText(prefs.getString(ThemeManager.PREF_CUSTOM_ACCENT, "#2A92FF"));
        binding = false;
    }

    private void configureListeners() {
        autoWazeSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_AUTO_WAZE, checked).apply());
        startGpsSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_START_GPS, checked).apply());
        weatherSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_WEATHER, checked).apply());
        externalReturnSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked && !Settings.canDrawOverlays(this)) {
                button.setChecked(false);
                openOverlayPermission();
                return;
            }
            prefs.edit().putBoolean(PREF_EXTERNAL_RETURN_OVERLAY, checked).apply();
            if (!checked) stopService(new Intent(this, ExternalAppOverlayService.class)
                    .setAction(ExternalAppOverlayService.ACTION_STOP));
            refreshStatus();
        });

        mediaSourceSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            prefs.edit().putString(PREF_MEDIA_SOURCE,
                    new String[]{MEDIA_SOURCE_SPOTIFY, MEDIA_SOURCE_LOCAL, MEDIA_SOURCE_AUTO}[position]).apply();
        }));
        cameraScaleSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            prefs.edit().putString(PREF_CAMERA_SCALE, position == 1
                    ? CameraPreviewController.SCALE_FILL : CameraPreviewController.SCALE_FIT).apply();
        }));
        cameraAspectSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            prefs.edit().putString(PREF_CAMERA_ASPECT,
                    new String[]{CameraPreviewController.ASPECT_AUTO, CameraPreviewController.ASPECT_4_3,
                            CameraPreviewController.ASPECT_16_9}[position]).apply();
        }));
        cameraWidthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(PREF_CAMERA_WIDTH_PERCENT, progress + 45).apply();
                refreshLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        layoutSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            prefs.edit().putString(PREF_LAYOUT_SIZE,
                    new String[]{"auto", "compact", "medium", "large"}[position]).apply();
        }));
        headerHeightSeek.setOnSeekBarChangeListener(sizeSeekListener(PREF_HEADER_HEIGHT_DP, 60));
        appsHeightSeek.setOnSeekBarChangeListener(sizeSeekListener(PREF_APPS_HEIGHT_DP, 110));
        themeSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            String[] themes = {ThemeManager.THEME_BLUE, ThemeManager.THEME_GRAPHITE,
                    ThemeManager.THEME_ORANGE, ThemeManager.THEME_GREEN, ThemeManager.THEME_RED,
                    ThemeManager.THEME_PURPLE, ThemeManager.THEME_CUSTOM};
            prefs.edit().putString(ThemeManager.PREF_THEME, themes[position]).apply();
            ThemeManager.apply(this);
        }));
        dimModeSpinner.setOnItemSelectedListener(new SelectionListener(position -> {
            if (binding) return;
            prefs.edit().putString(PREF_DIM_MODE,
                    new String[]{DIM_MODE_OFF, DIM_MODE_MANUAL, DIM_MODE_AUTO}[position]).apply();
            updateDimOverlay();
            refreshLabels();
        }));

        manualDimSeek.setOnSeekBarChangeListener(seekListener(PREF_DIM_PERCENT));
        dayDimSeek.setOnSeekBarChangeListener(seekListener(PREF_DIM_DAY_PERCENT));
        nightDimSeek.setOnSeekBarChangeListener(seekListener(PREF_DIM_NIGHT_PERCENT));
        sunOffsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(PREF_DIM_SUN_OFFSET, progress - 120).apply();
                refreshLabels();
                updateDimOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        findViewById(R.id.applyAccentButton).setOnClickListener(v -> applyCustomAccent());
        findViewById(R.id.cameraSettingsButton).setOnClickListener(v ->
                startActivity(new Intent(this, CameraSelectionActivity.class)));
        findViewById(R.id.localMediaButton).setOnClickListener(v ->
                startActivity(new Intent(this, LocalMediaActivity.class)));
        findViewById(R.id.overlayPermissionButton).setOnClickListener(v -> openOverlayPermission());
        findViewById(R.id.mediaPermissionButton).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            if (!ExternalAppLauncher.launch(this, intent, "↩ RaspiCar", true))
                ExternalAppLauncher.launch(this, new Intent(Settings.ACTION_SETTINGS), "↩ RaspiCar", true);
        });
        findViewById(R.id.locationPermissionButton).setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 77));
        findViewById(R.id.cameraPermissionButton).setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.CAMERA}, 78));
        findViewById(R.id.homeSettingsButton).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            ExternalAppLauncher.launch(this, intent, "↩ RaspiCar", true);
        });
        findViewById(R.id.androidSettingsButton).setOnClickListener(v ->
                ExternalAppLauncher.launch(this, new Intent(Settings.ACTION_SETTINGS), "↩ RaspiCar", true));
        findViewById(R.id.testSplitButton).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(DashboardActivity.EXTRA_FORCE_SPLIT, true));
            finish();
        });
        findViewById(R.id.rerunSetupButton).setOnClickListener(v -> {
            prefs.edit().putBoolean(PREF_SETUP_COMPLETE, false).apply();
            startActivity(new Intent(this, SetupActivity.class));
            finish();
        });
    }

    private SeekBar.OnSeekBarChangeListener seekListener(String preference) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(preference, progress).apply();
                refreshLabels();
                updateDimOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private SeekBar.OnSeekBarChangeListener sizeSeekListener(String preference, int minimum) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(preference, minimum + progress).apply();
                refreshLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void applyCustomAccent() {
        String value = customAccentInput.getText().toString().trim();
        int parsed = ThemeManager.parseAccent(value);
        String normalized = ThemeManager.colorToHex(parsed);
        customAccentInput.setText(normalized);
        prefs.edit()
                .putString(ThemeManager.PREF_CUSTOM_ACCENT, normalized)
                .putString(ThemeManager.PREF_THEME, ThemeManager.THEME_CUSTOM)
                .apply();
        binding = true;
        themeSpinner.setSelection(6);
        binding = false;
        ThemeManager.apply(this);
        Toast.makeText(this, "Accentkleur toegepast", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        ThemeManager.apply(this);
        refreshLabels();
        refreshStatus();
        binding = true;
        externalReturnSwitch.setChecked(prefs.getBoolean(PREF_EXTERNAL_RETURN_OVERLAY, true)
                && Settings.canDrawOverlays(this));
        binding = false;
        updateDimOverlay();
        stopService(new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_STOP));
    }

    private void openOverlayPermission() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void updateDimOverlay() {
        String mode = prefs.getString(PREF_DIM_MODE, DIM_MODE_OFF);
        if (DIM_MODE_OFF.equals(mode)) {
            startService(new Intent(this, DimOverlayService.class).setAction(DimOverlayService.ACTION_STOP));
            return;
        }
        if (!Settings.canDrawOverlays(this)) return;
        startService(new Intent(this, DimOverlayService.class).setAction(DimOverlayService.ACTION_UPDATE));
    }

    private void refreshLabels() {
        manualDimLabel.setText("Handmatig dimniveau: " + manualDimSeek.getProgress() + "%");
        dayDimLabel.setText("Automatisch overdag: " + dayDimSeek.getProgress() + "%");
        nightDimLabel.setText("Automatisch ’s nachts: " + nightDimSeek.getProgress() + "%");
        int offset = sunOffsetSeek.getProgress() - 120;
        sunOffsetLabel.setText("Zonmoment verschuiven: " + (offset > 0 ? "+" : "") + offset + " minuten");
        cameraWidthLabel.setText("Camerabreedte in paneel: " + (cameraWidthSeek.getProgress() + 45) + "%");
        headerHeightLabel.setText("Hoogte tijd/weer/km-u: " + (headerHeightSeek.getProgress() + 60) + " dp");
        appsHeightLabel.setText("Hoogte gecombineerde appbalk: " + (appsHeightSeek.getProgress() + 110) + " dp");
        String id = prefs.getString(PREF_CAMERA_ID, null);
        cameraSelectionText.setText(id == null ? "Nog geen camera gekozen" : "Gekozen camera: " + id
                + (prefs.getBoolean(PREF_CAMERA_MIRROR, false) ? " • gespiegeld" : "")
                + " • " + prefs.getInt(PREF_CAMERA_ROTATION, 0) + "°");
    }

    private void refreshStatus() {
        if (statusText == null) return;
        StringBuilder status = new StringBuilder();
        status.append("Waze: ").append(isInstalled("com.waze") ? "gevonden" : "niet gevonden").append('\n');
        boolean useGpsConnector = prefs.getBoolean(PREF_START_GPS, true);
        status.append("Locatiebron: ").append(useGpsConnector ? "GPS Connector" : "ingebouwde Android-gps").append('\n');
        if (useGpsConnector) status.append("GPS Connector: ").append(isInstalled("de.pilablu.gpsconnector") ? "gevonden" : "niet gevonden").append('\n');
        status.append("Spotify: ").append(isInstalled("com.spotify.music") ? "gevonden" : "niet gevonden").append('\n');
        status.append("Lokale muziekmap: ").append(prefs.getString(PREF_LOCAL_MEDIA_TREE, null) != null ? "gekozen" : "niet gekozen").append('\n');
        status.append("Camera gekozen: ").append(prefs.getString(PREF_CAMERA_ID, null) != null ? "ja" : "nog kiezen").append('\n');
        status.append("Mediatoegang: ").append(hasNotificationAccess() ? "toegestaan" : "nog toestaan").append('\n');
        status.append("Locatie: ").append(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ? "toegestaan" : "nog toestaan").append('\n');
        status.append("Camera: ").append(checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED ? "toegestaan" : "nog toestaan").append('\n');
        status.append("Overlay: ").append(Settings.canDrawOverlays(this) ? "toegestaan" : "nog toestaan");
        statusText.setText(status.toString());
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, MediaNotificationListener.class);
        return enabled.contains(component.flattenToString()) || enabled.contains(getPackageName());
    }

    private boolean isInstalled(String packageName) {
        try { getPackageManager().getApplicationInfo(packageName, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private int mediaSourceIndex(String value) {
        if (MEDIA_SOURCE_LOCAL.equals(value)) return 1;
        if (MEDIA_SOURCE_AUTO.equals(value)) return 2;
        return 0;
    }

    private int layoutIndex(String value) {
        if ("compact".equals(value)) return 1;
        if ("medium".equals(value)) return 2;
        if ("large".equals(value)) return 3;
        return 0;
    }

    private int themeIndex(String value) {
        if (ThemeManager.THEME_GRAPHITE.equals(value)) return 1;
        if (ThemeManager.THEME_ORANGE.equals(value)) return 2;
        if (ThemeManager.THEME_GREEN.equals(value)) return 3;
        if (ThemeManager.THEME_RED.equals(value)) return 4;
        if (ThemeManager.THEME_PURPLE.equals(value)) return 5;
        if (ThemeManager.THEME_CUSTOM.equals(value)) return 6;
        return 0;
    }

    private int dimModeIndex(String value) {
        if (DIM_MODE_MANUAL.equals(value)) return 1;
        if (DIM_MODE_AUTO.equals(value)) return 2;
        return 0;
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static final class SelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void selected(int position); }
        private final Callback callback;
        SelectionListener(Callback callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            callback.selected(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
