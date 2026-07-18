package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardActivity extends Activity implements LocationListener {
    public static final String EXTRA_FORCE_SPLIT = "force_split";
    public static final String EXTRA_ALLOW_SETUP_PREVIEW = "allow_setup_preview";

    private static final int LOCATION_REQUEST = 41;
    private static final int CAMERA_REQUEST = 42;
    private static final int APP_PICKER_BASE = 200;
    private static final int SLOT_COUNT = 5;
    private static final String WAZE_PACKAGE = "com.waze";
    private static final String GPS_CONNECTOR_PACKAGE = "de.pilablu.gpsconnector";
    private static final String SPOTIFY_PACKAGE = "com.spotify.music";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs;
    private LinearLayout fixedAppsContainer;
    private LinearLayout customAppsContainer;
    private TextView trashTarget;
    private TextView timeText;
    private TextView dateText;
    private TextView weatherIcon;
    private TextView temperatureText;
    private TextView weatherDescription;
    private TextView speedText;
    private TextView modeTitle;
    private ImageView albumArt;
    private TextView trackTitle;
    private TextView artistText;
    private TextView playPauseButton;
    private ProgressBar mediaProgress;
    private LinearLayout mediaPanel;
    private FrameLayout cameraPanel;
    private TextureView cameraPreview;
    private TextView cameraStatus;
    private CameraPreviewController cameraController;
    private boolean cameraMode;
    private LayoutProfile layoutProfile;

    private LocationManager locationManager;
    private Location lastWeatherLocation;
    private long lastWeatherUpdate;

    private MediaSessionManager mediaSessionManager;
    private MediaController spotifyMediaController;
    private boolean mediaListenerRegistered;
    private boolean gpsPrimed;
    private boolean pendingInitialNavigation;
    private boolean initialNavigationScheduled;
    private long lastSplitRequest;

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            dateText.setText(new SimpleDateFormat("EEE, d MMM yyyy", new Locale("nl", "NL")).format(now));
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            updateMediaProgress();
            handler.postDelayed(this, 1_000L);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = this::selectSpotifyController;
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { updateMediaUi(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { updateMediaUi(); }
        @Override public void onSessionDestroyed() { refreshMediaSessions(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        SettingsActivity.migrateLegacyPreferences(prefs);
        boolean setupPreview = getIntent().getBooleanExtra(EXTRA_ALLOW_SETUP_PREVIEW, false);
        if (!prefs.getBoolean(SettingsActivity.PREF_SETUP_COMPLETE, false) && !setupPreview) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_dashboard);
        hideSystemBars();
        bindViews();
        layoutProfile = resolveLayoutProfile();
        applyLayoutProfile();
        configureDragTrash();
        buildFixedApps();
        rebuildCustomApps();
        configureMediaButtons();
        ThemeManager.apply(this);
        tintProgress();

        cameraController = new CameraPreviewController(this, cameraPreview, (status, error) -> {
            cameraStatus.setText(status);
            cameraStatus.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
            if (error) cameraStatus.setBackgroundResource(R.drawable.status_warning_bg);
            else cameraStatus.setBackgroundResource(R.drawable.camera_overlay_bg);
        });

        boolean locationAlreadyGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        configureLocation();
        handler.post(clockRunnable);
        handler.post(progressRunnable);

        boolean forceSplit = getIntent().getBooleanExtra(EXTRA_FORCE_SPLIT, false);
        boolean openAtStart = prefs.getBoolean(SettingsActivity.PREF_AUTO_WAZE, true);
        if (forceSplit || openAtStart) {
            if (locationAlreadyGranted) scheduleInitialNavigation(forceSplit ? 350 : 1_200);
            else pendingInitialNavigation = true;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_FORCE_SPLIT, false)) handler.postDelayed(this::openNavigation, 350);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) return;
        hideSystemBars();
        LayoutProfile newProfile = resolveLayoutProfile();
        if (newProfile != layoutProfile) {
            layoutProfile = newProfile;
            applyLayoutProfile();
            buildFixedApps();
            rebuildCustomApps();
        }
        ThemeManager.apply(this);
        tintProgress();
        rebuildCustomApps();
        refreshMediaSessions();
        resumeDimOverlayIfNeeded();
        stopService(new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_STOP));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (cameraMode) startSelectedCamera();
    }

    @Override
    protected void onStop() {
        if (cameraController != null) cameraController.stop();
        super.onStop();
    }

    private void scheduleInitialNavigation(long delayMs) {
        if (initialNavigationScheduled) return;
        initialNavigationScheduled = true;
        handler.postDelayed(this::openNavigation, delayMs);
    }

    private void bindViews() {
        fixedAppsContainer = findViewById(R.id.fixedAppsContainer);
        customAppsContainer = findViewById(R.id.customAppsContainer);
        trashTarget = findViewById(R.id.trashTarget);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        weatherIcon = findViewById(R.id.weatherIcon);
        temperatureText = findViewById(R.id.temperatureText);
        weatherDescription = findViewById(R.id.weatherDescription);
        speedText = findViewById(R.id.speedText);
        modeTitle = findViewById(R.id.modeTitle);
        albumArt = findViewById(R.id.albumArt);
        trackTitle = findViewById(R.id.trackTitle);
        artistText = findViewById(R.id.artistText);
        playPauseButton = findViewById(R.id.playPauseButton);
        mediaProgress = findViewById(R.id.mediaProgress);
        mediaPanel = findViewById(R.id.mediaPanel);
        cameraPanel = findViewById(R.id.cameraPanel);
        cameraPreview = findViewById(R.id.cameraPreview);
        cameraStatus = findViewById(R.id.cameraStatus);
        findViewById(R.id.openWazeButton).setOnClickListener(v -> openNavigation());
        findViewById(R.id.backToMediaButton).setOnClickListener(v -> showSpotifyPanel());
        findViewById(R.id.cameraMirrorButton).setOnClickListener(v -> toggleCameraMirror());
    }

    private void configureMediaButtons() {
        findViewById(R.id.previousButton).setOnClickListener(v -> {
            if (spotifyMediaController != null) spotifyMediaController.getTransportControls().skipToPrevious();
            else handleNoSpotifySession();
        });
        playPauseButton.setOnClickListener(v -> {
            if (spotifyMediaController == null) {
                handleNoSpotifySession();
                return;
            }
            PlaybackState state = spotifyMediaController.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                spotifyMediaController.getTransportControls().pause();
            } else {
                spotifyMediaController.getTransportControls().play();
            }
        });
        findViewById(R.id.nextButton).setOnClickListener(v -> {
            if (spotifyMediaController != null) spotifyMediaController.getTransportControls().skipToNext();
            else handleNoSpotifySession();
        });
        View.OnClickListener openSpotify = v -> launchSpotifyWithReturn();
        albumArt.setOnClickListener(openSpotify);
        trackTitle.setOnClickListener(openSpotify);
        artistText.setOnClickListener(openSpotify);
    }

    private void handleNoSpotifySession() {
        if (!hasNotificationAccess()) openMediaAccessSettings();
        else launchSpotifyWithReturn();
    }

    private void buildFixedApps() {
        fixedAppsContainer.removeAllViews();
        fixedAppsContainer.addView(createAppButton("GPS Connector", GPS_CONNECTOR_PACKAGE, 0,
                v -> launchPackage(GPS_CONNECTOR_PACKAGE)));
        fixedAppsContainer.addView(createAppButton("Camera", null, R.drawable.ic_camera,
                v -> toggleCameraPanel()));
        fixedAppsContainer.addView(createAppButton("Spotify", SPOTIFY_PACKAGE, 0,
                v -> launchSpotifyWithReturn()));
        fixedAppsContainer.addView(createAppButton("Instellingen", null, R.drawable.ic_settings,
                v -> startActivity(new Intent(this, SettingsActivity.class))));
        ThemeManager.apply(this);
    }

    private View createAppButton(String label, String iconPackage, int iconResource, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(this);
        button.setTag("slot");
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), dp(5), dp(4), dp(3));
        button.setOnClickListener(listener);
        ThemeManager.styleDynamicCard(this, button, "slot");

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (iconResource != 0) icon.setImageResource(iconResource);
        else icon.setImageDrawable(loadAppIcon(iconPackage));
        int iconSize = layoutProfile == LayoutProfile.COMPACT ? 34
                : layoutProfile == LayoutProfile.LARGE ? 60 : 52;
        button.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));

        TextView text = new TextView(this);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setText(label);
        text.setTextColor(ThemeManager.getPalette(this).textPrimary);
        float font = layoutProfile == LayoutProfile.COMPACT ? 7.5f : (label.length() > 10 ? 9f : 11f);
        text.setTextSize(font);
        button.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(layoutProfile == LayoutProfile.COMPACT ? 16 : 24)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void rebuildCustomApps() {
        if (customAppsContainer == null || prefs == null) return;
        customAppsContainer.removeAllViews();
        for (int slot = 0; slot < SLOT_COUNT; slot++) customAppsContainer.addView(createCustomSlot(slot));
        customAppsContainer.addView(createAppDrawerSlot());
        ThemeManager.apply(this);
    }

    private View createCustomSlot(int slot) {
        String packageName = prefs.getString("slot_" + slot, null);
        LinearLayout slotView = createBaseSlot();
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TextView label = createSlotLabel();

        if (packageName == null || !isInstalled(packageName)) {
            icon.setImageResource(R.drawable.ic_plus);
            label.setText("+");
            label.setTextSize(18);
            slotView.setOnClickListener(v -> openAppPicker(slot));
        } else {
            icon.setImageDrawable(loadAppIcon(packageName));
            label.setText(loadAppLabel(packageName));
            slotView.setOnClickListener(v -> launchPackage(packageName));
            slotView.setOnLongClickListener(v -> beginSlotDrag(v, slot));
        }
        int iconSize = layoutProfile == LayoutProfile.COMPACT ? 32 : layoutProfile == LayoutProfile.LARGE ? 54 : 48;
        slotView.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        slotView.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(layoutProfile == LayoutProfile.COMPACT ? 16 : 22)));
        return slotView;
    }

    private View createAppDrawerSlot() {
        LinearLayout slotView = createBaseSlot();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_apps);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TextView label = createSlotLabel();
        label.setText("Apps");
        int iconSize = layoutProfile == LayoutProfile.COMPACT ? 32 : layoutProfile == LayoutProfile.LARGE ? 54 : 48;
        slotView.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        slotView.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(layoutProfile == LayoutProfile.COMPACT ? 16 : 22)));
        slotView.setOnClickListener(v -> {
            Intent drawer = new Intent(this, AppPickerActivity.class)
                    .putExtra(AppPickerActivity.EXTRA_LAUNCH_MODE, true);
            startActivity(drawer);
        });
        return slotView;
    }

    private LinearLayout createBaseSlot() {
        LinearLayout slotView = new LinearLayout(this);
        slotView.setTag("slot");
        slotView.setOrientation(LinearLayout.VERTICAL);
        slotView.setGravity(Gravity.CENTER);
        slotView.setPadding(dp(2), dp(4), dp(2), dp(2));
        ThemeManager.styleDynamicCard(this, slotView, "slot");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        slotView.setLayoutParams(params);
        return slotView;
    }

    private TextView createSlotLabel() {
        TextView label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(ThemeManager.getPalette(this).textPrimary);
        label.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 7.5f : 9f);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return label;
    }

    private boolean beginSlotDrag(View view, int slot) {
        trashTarget.setVisibility(View.VISIBLE);
        ClipData data = ClipData.newPlainText("slot", Integer.toString(slot));
        view.startDragAndDrop(data, new View.DragShadowBuilder(view), slot, 0);
        return true;
    }

    private void configureDragTrash() {
        trashTarget.setOnDragListener((view, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_ENTERED:
                    view.setScaleX(1.07f); view.setScaleY(1.07f); return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    view.setScaleX(1f); view.setScaleY(1f); return true;
                case DragEvent.ACTION_DROP:
                    int slot = (Integer) event.getLocalState();
                    prefs.edit().remove("slot_" + slot).apply();
                    rebuildCustomApps();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    trashTarget.setVisibility(View.GONE);
                    view.setScaleX(1f); view.setScaleY(1f); return true;
                default:
                    return true;
            }
        });
    }

    private void openAppPicker(int slot) {
        startActivityForResult(new Intent(this, AppPickerActivity.class), APP_PICKER_BASE + slot);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode >= APP_PICKER_BASE && requestCode < APP_PICKER_BASE + SLOT_COUNT
                && resultCode == RESULT_OK && data != null) {
            int slot = requestCode - APP_PICKER_BASE;
            String packageName = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE);
            if (packageName != null) {
                prefs.edit().putString("slot_" + slot, packageName).apply();
                rebuildCustomApps();
            }
        }
    }

    private void toggleCameraPanel() {
        if (cameraMode) showSpotifyPanel();
        else showCameraPanel();
    }

    private void showCameraPanel() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        cameraMode = true;
        mediaPanel.setVisibility(View.GONE);
        cameraPanel.setVisibility(View.VISIBLE);
        modeTitle.setText("CAMERA");
        startSelectedCamera();
    }

    private void showSpotifyPanel() {
        cameraMode = false;
        if (cameraController != null) cameraController.stop();
        cameraPanel.setVisibility(View.GONE);
        mediaPanel.setVisibility(View.VISIBLE);
        modeTitle.setText("SPOTIFY");
    }

    private void startSelectedCamera() {
        if (!cameraMode || cameraController == null) return;
        String cameraId = prefs.getString(SettingsActivity.PREF_CAMERA_ID, null);
        boolean mirror = prefs.getBoolean(SettingsActivity.PREF_CAMERA_MIRROR, false);
        int rotation = prefs.getInt(SettingsActivity.PREF_CAMERA_ROTATION, 0);
        cameraController.start(cameraId, mirror, rotation);
    }

    private void toggleCameraMirror() {
        boolean mirror = !prefs.getBoolean(SettingsActivity.PREF_CAMERA_MIRROR, false);
        prefs.edit().putBoolean(SettingsActivity.PREF_CAMERA_MIRROR, mirror).apply();
        cameraPreview.setScaleX(mirror ? -1f : 1f);
        Toast.makeText(this, mirror ? "Camera gespiegeld" : "Spiegeling uit", Toast.LENGTH_SHORT).show();
    }

    public void openNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastSplitRequest < 2_500L) return;
        lastSplitRequest = now;
        if (prefs.getBoolean(SettingsActivity.PREF_START_GPS, true)
                && !gpsPrimed && isInstalled(GPS_CONNECTOR_PACKAGE)) {
            gpsPrimed = true;
            Intent gps = getPackageManager().getLaunchIntentForPackage(GPS_CONNECTOR_PACKAGE);
            if (gps != null) {
                gps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                try { startActivity(gps); } catch (RuntimeException ignored) { }
                handler.postDelayed(() -> {
                    Intent home = new Intent(this, DashboardActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(EXTRA_ALLOW_SETUP_PREVIEW, true);
                    startActivity(home);
                    handler.postDelayed(this::launchWazeAdjacent, 350);
                }, 500);
                return;
            }
        }
        launchWazeAdjacent();
    }

    private void launchWazeAdjacent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(WAZE_PACKAGE);
        if (intent == null) intent = new Intent(Intent.ACTION_VIEW, Uri.parse("waze://"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            WindowMetrics metrics = getWindowManager().getMaximumWindowMetrics();
            Rect full = metrics.getBounds();
            int left = full.left + Math.round(full.width() * 0.35f);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(new Rect(left, full.top, full.right, full.bottom));
            startActivity(intent, options.toBundle());
        } catch (RuntimeException e) {
            try { startActivity(intent); }
            catch (RuntimeException second) {
                Toast.makeText(this, "Waze is niet geïnstalleerd", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchSpotifyWithReturn() {
        if (!isInstalled(SPOTIFY_PACKAGE)) {
            Toast.makeText(this, "Spotify is niet geïnstalleerd", Toast.LENGTH_SHORT).show();
            return;
        }
        showExternalReturnOverlay("↩ Dashboard");
        launchPackage(SPOTIFY_PACKAGE);
    }

    private void showExternalReturnOverlay(String label) {
        if (!prefs.getBoolean(SettingsActivity.PREF_EXTERNAL_RETURN_OVERLAY, true)) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Geef overlaytoegang voor de zwevende terugknop.", Toast.LENGTH_LONG).show();
            return;
        }
        startService(new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_SHOW)
                .putExtra(ExternalAppOverlayService.EXTRA_LABEL, label));
    }

    private void launchPackage(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, loadAppLabel(packageName) + " is niet geïnstalleerd", Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(launch); }
        catch (RuntimeException e) { Toast.makeText(this, "App kon niet worden geopend", Toast.LENGTH_SHORT).show(); }
    }

    private Drawable loadAppIcon(String packageName) {
        if (packageName == null) return getDrawable(R.drawable.ic_launcher_legacy);
        try {
            if (packageName.equals(getPackageName())) return getDrawable(R.drawable.ic_launcher_legacy);
            return getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return getDrawable(R.drawable.ic_launcher_legacy);
        }
    }

    private String loadAppLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            if (WAZE_PACKAGE.equals(packageName)) return "Waze";
            if (GPS_CONNECTOR_PACKAGE.equals(packageName)) return "GPS Connector";
            if (SPOTIFY_PACKAGE.equals(packageName)) return "Spotify";
            return packageName;
        }
    }

    private boolean isInstalled(String packageName) {
        try { getPackageManager().getApplicationInfo(packageName, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void configureLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper()); }
        catch (RuntimeException ignored) { }
        try { locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper()); }
        catch (RuntimeException ignored) { }
        Location latest = null;
        try { latest = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (RuntimeException ignored) { }
        if (latest == null) {
            try { latest = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER); }
            catch (RuntimeException ignored) { }
        }
        if (latest != null) onLocationChanged(latest);
    }

    @Override
    public void onLocationChanged(Location location) {
        int kmh = location.hasSpeed() ? Math.max(0, Math.round(location.getSpeed() * 3.6f)) : 0;
        speedText.setText(getString(R.string.speed_format, kmh));
        prefs.edit()
                .putString(SettingsActivity.PREF_LAST_LATITUDE, Double.toString(location.getLatitude()))
                .putString(SettingsActivity.PREF_LAST_LONGITUDE, Double.toString(location.getLongitude()))
                .apply();
        maybeUpdateWeather(location);
        resumeDimOverlayIfNeeded();
    }

    private void maybeUpdateWeather(Location location) {
        if (!prefs.getBoolean(SettingsActivity.PREF_WEATHER, true)) {
            temperatureText.setText("--°C");
            weatherDescription.setText("Weer uitgeschakeld");
            return;
        }
        long now = System.currentTimeMillis();
        boolean stale = now - lastWeatherUpdate > 20 * 60_000L;
        boolean moved = lastWeatherLocation == null || lastWeatherLocation.distanceTo(location) > 5_000f;
        if (!stale && !moved) return;
        lastWeatherUpdate = now;
        lastWeatherLocation = new Location(location);
        fetchWeather(location.getLatitude(), location.getLongitude());
    }

    private void fetchWeather(double latitude, double longitude) {
        weatherDescription.setText("Weer laden…");
        networkExecutor.submit(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = "https://api.open-meteo.com/v1/forecast?latitude="
                        + URLEncoder.encode(Double.toString(latitude), "UTF-8")
                        + "&longitude=" + URLEncoder.encode(Double.toString(longitude), "UTF-8")
                        + "&current=temperature_2m,weather_code&timezone=auto";
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(7_000);
                connection.setReadTimeout(7_000);
                connection.setRequestProperty("User-Agent", "RaspiCarDashboard/3.0");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                    JSONObject current = new JSONObject(json.toString()).getJSONObject("current");
                    double temperature = current.getDouble("temperature_2m");
                    int code = current.getInt("weather_code");
                    WeatherDisplay display = mapWeather(code);
                    runOnUiThread(() -> {
                        temperatureText.setText(Math.round(temperature) + "°C");
                        weatherIcon.setText(display.icon);
                        weatherDescription.setText(display.description);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> weatherDescription.setText("Weer niet beschikbaar"));
                lastWeatherUpdate = 0;
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private WeatherDisplay mapWeather(int code) {
        if (code == 0) return new WeatherDisplay("☀", "Helder");
        if (code <= 3) return new WeatherDisplay("☁", code == 1 ? "Licht bewolkt" : "Bewolkt");
        if (code == 45 || code == 48) return new WeatherDisplay("≋", "Mist");
        if (code >= 51 && code <= 67) return new WeatherDisplay("☂", "Regen");
        if (code >= 71 && code <= 77) return new WeatherDisplay("❄", "Sneeuw");
        if (code >= 80 && code <= 82) return new WeatherDisplay("☂", "Buien");
        if (code >= 85 && code <= 86) return new WeatherDisplay("❄", "Sneeuwbuien");
        if (code >= 95) return new WeatherDisplay("ϟ", "Onweer");
        return new WeatherDisplay("☁", "Onbekend");
    }

    private void refreshMediaSessions() {
        if (!hasNotificationAccess()) {
            clearSpotifyController();
            trackTitle.setText(R.string.tap_for_media_access);
            artistText.setText("Instellingen → mediatoegang");
            playPauseButton.setText("▶");
            return;
        }
        if (mediaSessionManager == null) mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName listener = new ComponentName(this, MediaNotificationListener.class);
        try {
            if (!mediaListenerRegistered) {
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listener, handler);
                mediaListenerRegistered = true;
            }
            selectSpotifyController(mediaSessionManager.getActiveSessions(listener));
        } catch (SecurityException e) {
            clearSpotifyController();
            trackTitle.setText(R.string.tap_for_media_access);
        }
    }

    private void selectSpotifyController(List<MediaController> controllers) {
        MediaController selected = null;
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (!SPOTIFY_PACKAGE.equals(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    selected = controller; break;
                }
                if (selected == null) selected = controller;
            }
        }
        if (spotifyMediaController != null && spotifyMediaController != selected) {
            try { spotifyMediaController.unregisterCallback(mediaCallback); } catch (RuntimeException ignored) { }
        }
        spotifyMediaController = selected;
        if (spotifyMediaController != null) spotifyMediaController.registerCallback(mediaCallback, handler);
        updateMediaUi();
    }

    private void clearSpotifyController() {
        if (spotifyMediaController != null) {
            try { spotifyMediaController.unregisterCallback(mediaCallback); } catch (RuntimeException ignored) { }
        }
        spotifyMediaController = null;
    }

    private void updateMediaUi() {
        if (spotifyMediaController == null) {
            trackTitle.setText("Spotify niet actief");
            artistText.setText("Tik op Spotify om muziek te kiezen");
            albumArt.setImageDrawable(loadAppIcon(SPOTIFY_PACKAGE));
            playPauseButton.setText("▶");
            mediaProgress.setProgress(0);
            return;
        }
        MediaMetadata metadata = spotifyMediaController.getMetadata();
        CharSequence title = null;
        CharSequence artist = null;
        Bitmap art = null;
        if (metadata != null) {
            title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
            artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null || artist.length() == 0) artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        trackTitle.setText(title == null || title.length() == 0 ? "Spotify" : title);
        artistText.setText(artist == null || artist.length() == 0 ? "Spotify" : artist);
        if (art != null) albumArt.setImageBitmap(art);
        else albumArt.setImageDrawable(loadAppIcon(SPOTIFY_PACKAGE));
        PlaybackState state = spotifyMediaController.getPlaybackState();
        playPauseButton.setText(state != null && state.getState() == PlaybackState.STATE_PLAYING ? "Ⅱ" : "▶");
        updateMediaProgress();
    }

    private void updateMediaProgress() {
        if (spotifyMediaController == null) { mediaProgress.setProgress(0); return; }
        MediaMetadata metadata = spotifyMediaController.getMetadata();
        PlaybackState state = spotifyMediaController.getPlaybackState();
        if (metadata == null || state == null) { mediaProgress.setProgress(0); return; }
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += (long) (elapsed * state.getPlaybackSpeed());
        }
        mediaProgress.setProgress(duration > 0
                ? (int) Math.max(0, Math.min(1000, position * 1000L / duration)) : 0);
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void openMediaAccessSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (RuntimeException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void resumeDimOverlayIfNeeded() {
        String mode = prefs.getString(SettingsActivity.PREF_DIM_MODE, SettingsActivity.DIM_MODE_OFF);
        if (!SettingsActivity.DIM_MODE_OFF.equals(mode) && Settings.canDrawOverlays(this)) {
            startService(new Intent(this, DimOverlayService.class).setAction(DimOverlayService.ACTION_UPDATE));
        } else {
            startService(new Intent(this, DimOverlayService.class).setAction(DimOverlayService.ACTION_STOP));
        }
    }

    private LayoutProfile resolveLayoutProfile() {
        String setting = prefs.getString(SettingsActivity.PREF_LAYOUT_SIZE, "auto");
        if ("compact".equals(setting)) return LayoutProfile.COMPACT;
        if ("large".equals(setting)) return LayoutProfile.LARGE;
        if ("medium".equals(setting)) return LayoutProfile.MEDIUM;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPixels;
        int heightPixels;
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            widthPixels = bounds.width();
            heightPixels = bounds.height();
        } else {
            //noinspection deprecation
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            widthPixels = dm.widthPixels;
            heightPixels = dm.heightPixels;
        }
        float widthDp = widthPixels / dm.density;
        float heightDp = heightPixels / dm.density;
        if (widthDp < 390 || heightDp < 650) return LayoutProfile.COMPACT;
        if (widthDp > 580 && heightDp > 850) return LayoutProfile.LARGE;
        return LayoutProfile.MEDIUM;
    }

    private void applyLayoutProfile() {
        int rootPadding;
        int headerHeight;
        int appsHeight;
        int shortcutHeight;
        int footerHeight;
        int dynamicHeaderHeight;
        if (layoutProfile == LayoutProfile.COMPACT) {
            rootPadding = 4; headerHeight = 54; appsHeight = 74; shortcutHeight = 70;
            footerHeight = 0; dynamicHeaderHeight = 40;
        } else if (layoutProfile == LayoutProfile.LARGE) {
            rootPadding = 16; headerHeight = 108; appsHeight = 138; shortcutHeight = 130;
            footerHeight = 28; dynamicHeaderHeight = 64;
        } else {
            rootPadding = 12; headerHeight = 92; appsHeight = 122; shortcutHeight = 116;
            footerHeight = 24; dynamicHeaderHeight = 58;
        }

        View root = findViewById(R.id.root);
        root.setPadding(dp(rootPadding), dp(rootPadding), dp(rootPadding), dp(rootPadding));
        setViewHeight(R.id.headerCard, headerHeight);
        setViewHeight(R.id.fixedAppsCard, appsHeight);
        setViewHeight(R.id.shortcutsCard, shortcutHeight);
        setViewHeight(R.id.footerText, footerHeight);
        setViewHeight(R.id.dynamicHeader, dynamicHeaderHeight);

        View dynamicCard = findViewById(R.id.dynamicCard);
        View fixedCard = findViewById(R.id.fixedAppsCard);
        View shortcutsCard = findViewById(R.id.shortcutsCard);
        if (layoutProfile == LayoutProfile.COMPACT) {
            dynamicCard.setPadding(dp(6), dp(4), dp(6), dp(4));
            fixedCard.setPadding(dp(4), dp(3), dp(4), dp(3));
            shortcutsCard.setPadding(dp(4), dp(3), dp(4), dp(3));
            findViewById(R.id.fixedSectionTitle).setVisibility(View.GONE);
            findViewById(R.id.shortcutSectionTitle).setVisibility(View.GONE);
        } else {
            dynamicCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            fixedCard.setPadding(dp(9), dp(9), dp(9), dp(9));
            shortcutsCard.setPadding(dp(9), dp(9), dp(9), dp(9));
            findViewById(R.id.fixedSectionTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.shortcutSectionTitle).setVisibility(View.VISIBLE);
        }

        timeText.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 27 : layoutProfile == LayoutProfile.LARGE ? 46 : 40);
        dateText.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 9 : layoutProfile == LayoutProfile.LARGE ? 15 : 13);
        weatherIcon.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 24 : layoutProfile == LayoutProfile.LARGE ? 38 : 33);
        temperatureText.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 18 : layoutProfile == LayoutProfile.LARGE ? 29 : 25);
        weatherDescription.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 9 : layoutProfile == LayoutProfile.LARGE ? 14 : 12);
        modeTitle.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 11 : layoutProfile == LayoutProfile.LARGE ? 16 : 14);
        trackTitle.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 15 : layoutProfile == LayoutProfile.LARGE ? 23 : 20);
        artistText.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 10 : layoutProfile == LayoutProfile.LARGE ? 16 : 14);

        int artSize = layoutProfile == LayoutProfile.COMPACT ? 60 : layoutProfile == LayoutProfile.LARGE ? 122 : 105;
        ViewGroup.LayoutParams artParams = albumArt.getLayoutParams();
        artParams.width = dp(artSize);
        artParams.height = dp(artSize);
        albumArt.setLayoutParams(artParams);

        resizeSquare(R.id.speedText, layoutProfile == LayoutProfile.COMPACT ? 40 : layoutProfile == LayoutProfile.LARGE ? 66 : 58);
        speedText.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 10 : layoutProfile == LayoutProfile.LARGE ? 16 : 14);
        resizeSquare(R.id.previousButton, layoutProfile == LayoutProfile.COMPACT ? 30 : layoutProfile == LayoutProfile.LARGE ? 58 : 52);
        resizeSquare(R.id.nextButton, layoutProfile == LayoutProfile.COMPACT ? 30 : layoutProfile == LayoutProfile.LARGE ? 58 : 52);
        resizeSquare(R.id.playPauseButton, layoutProfile == LayoutProfile.COMPACT ? 36 : layoutProfile == LayoutProfile.LARGE ? 68 : 60);
        playPauseButton.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 16 : layoutProfile == LayoutProfile.LARGE ? 27 : 24);
        mediaProgress.setVisibility(layoutProfile == LayoutProfile.COMPACT ? View.GONE : View.VISIBLE);

        TextView openWaze = findViewById(R.id.openWazeButton);
        ViewGroup.LayoutParams wazeParams = openWaze.getLayoutParams();
        wazeParams.height = dp(layoutProfile == LayoutProfile.COMPACT ? 34 : 42);
        openWaze.setLayoutParams(wazeParams);
        openWaze.setMinWidth(dp(layoutProfile == LayoutProfile.COMPACT ? 76 : 96));
        openWaze.setTextSize(layoutProfile == LayoutProfile.COMPACT ? 10 : 13);
    }

    private void resizeSquare(int id, int sizeDp) {
        View view = findViewById(id);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = dp(sizeDp);
        params.height = dp(sizeDp);
        view.setLayoutParams(params);
    }

    private void setViewHeight(int id, int heightDp) {
        View view = findViewById(id);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = dp(heightDp);
        view.setLayoutParams(params);
    }

    private void tintProgress() {
        if (mediaProgress != null && mediaProgress.getProgressDrawable() != null) {
            mediaProgress.getProgressDrawable().setTint(ThemeManager.getPalette(this).accent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocationUpdates();
            if (pendingInitialNavigation) {
                pendingInitialNavigation = false;
                scheduleInitialNavigation(500);
            }
        } else if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showCameraPanel();
            else Toast.makeText(this, "Cameratoestemming is nodig voor live beeld", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (cameraController != null) cameraController.stop();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (RuntimeException ignored) { }
        }
        clearSpotifyController();
        if (mediaSessionManager != null && mediaListenerRegistered) {
            try { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener); }
            catch (RuntimeException ignored) { }
        }
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private enum LayoutProfile { COMPACT, MEDIUM, LARGE }

    private static final class WeatherDisplay {
        final String icon;
        final String description;
        WeatherDisplay(String icon, String description) { this.icon = icon; this.description = description; }
    }
}
