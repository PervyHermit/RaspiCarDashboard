package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    static final String PREFS = "dashboard_prefs";
    static final String PREF_AUTO_WAZE = "auto_waze";
    static final String PREF_START_GPS = "start_gps_connector";
    static final String PREF_WEATHER = "weather_enabled";
    static final String PREF_DIM = "dim_enabled";
    static final String PREF_DIM_PERCENT = "dim_percent";

    private SharedPreferences prefs;
    private Switch autoWazeSwitch;
    private Switch startGpsSwitch;
    private Switch weatherSwitch;
    private Switch dimSwitch;
    private SeekBar dimSeek;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        hideSystemBars();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        autoWazeSwitch = findViewById(R.id.autoWazeSwitch);
        startGpsSwitch = findViewById(R.id.startGpsSwitch);
        weatherSwitch = findViewById(R.id.weatherSwitch);
        dimSwitch = findViewById(R.id.dimSwitch);
        dimSeek = findViewById(R.id.dimSeek);
        statusText = findViewById(R.id.statusText);

        autoWazeSwitch.setChecked(prefs.getBoolean(PREF_AUTO_WAZE, true));
        startGpsSwitch.setChecked(prefs.getBoolean(PREF_START_GPS, true));
        weatherSwitch.setChecked(prefs.getBoolean(PREF_WEATHER, true));
        dimSwitch.setChecked(prefs.getBoolean(PREF_DIM, false));
        dimSeek.setProgress(prefs.getInt(PREF_DIM_PERCENT, 35));

        autoWazeSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_AUTO_WAZE, checked).apply());
        startGpsSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_START_GPS, checked).apply());
        weatherSwitch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(PREF_WEATHER, checked).apply());

        dimSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(PREF_DIM, checked).apply();
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    button.setChecked(false);
                    openOverlayPermission();
                } else {
                    updateDimOverlay();
                }
            } else {
                stopDimOverlay();
            }
            refreshStatus();
        });

        dimSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt(PREF_DIM_PERCENT, progress).apply();
                if (dimSwitch.isChecked() && Settings.canDrawOverlays(SettingsActivity.this)) {
                    updateDimOverlay();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        findViewById(R.id.overlayPermissionButton).setOnClickListener(v -> openOverlayPermission());
        findViewById(R.id.mediaPermissionButton).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        findViewById(R.id.locationPermissionButton).setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 77));
        findViewById(R.id.homeSettingsButton).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        findViewById(R.id.androidSettingsButton).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.testSplitButton).setOnClickListener(v -> {
            Intent dashboard = new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(DashboardActivity.EXTRA_FORCE_SPLIT, true);
            startActivity(dashboard);
            finish();
        });

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        refreshStatus();
        if (prefs.getBoolean(PREF_DIM, false) && Settings.canDrawOverlays(this)) {
            dimSwitch.setChecked(true);
            updateDimOverlay();
        }
    }

    private void openOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void updateDimOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class)
                .setAction(DimOverlayService.ACTION_UPDATE)
                .putExtra(DimOverlayService.EXTRA_PERCENT, dimSeek.getProgress());
        startService(intent);
    }

    private void stopDimOverlay() {
        Intent intent = new Intent(this, DimOverlayService.class)
                .setAction(DimOverlayService.ACTION_STOP);
        startService(intent);
    }

    private void refreshStatus() {
        if (statusText == null) return;
        StringBuilder status = new StringBuilder();
        status.append("Waze: ").append(isInstalled("com.waze") ? "gevonden" : "niet gevonden").append('\n');
        status.append("GPS Connector: ").append(isInstalled("de.pilablu.gpsconnector") ? "gevonden" : "niet gevonden").append('\n');
        status.append("VLC: ").append(isInstalled("org.videolan.vlc") ? "gevonden" : "niet gevonden").append('\n');
        status.append("Spotify: ").append(isInstalled("com.spotify.music") ? "gevonden" : "niet gevonden").append('\n');
        status.append("Mediatoegang: ").append(hasNotificationAccess() ? "toegestaan" : "nog toestaan").append('\n');
        status.append("Locatie: ").append(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? "toegestaan" : "nog toestaan").append('\n');
        status.append("Dim-overlay: ").append(Settings.canDrawOverlays(this) ? "toegestaan" : "nog toestaan");
        statusText.setText(status.toString());
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, MediaNotificationListener.class);
        return enabled.contains(component.flattenToString()) || enabled.contains(getPackageName());
    }

    private boolean isInstalled(String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                getPackageManager().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                //noinspection deprecation
                getPackageManager().getPackageInfo(packageName, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
