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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
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

    private static final int LOCATION_REQUEST = 41;
    private static final int APP_PICKER_BASE = 200;
    private static final int SLOT_COUNT = 6;
    private static final String WAZE_PACKAGE = "com.waze";
    private static final String GPS_CONNECTOR_PACKAGE = "de.pilablu.gpsconnector";
    private static final String VLC_PACKAGE = "org.videolan.vlc";
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
    private ImageView albumArt;
    private TextView trackTitle;
    private TextView artistText;
    private TextView playPauseButton;
    private ProgressBar mediaProgress;

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
        @Override
        public void run() {
            Date now = new Date();
            timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            dateText.setText(new SimpleDateFormat("EEE, d MMM yyyy", new Locale("nl", "NL")).format(now));
            handler.postDelayed(this, 30_000);
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateMediaProgress();
            handler.postDelayed(this, 1_000);
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
        setContentView(R.layout.activity_dashboard);
        hideSystemBars();
        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);

        bindViews();
        configureDragTrash();
        buildFixedApps();
        rebuildCustomApps();
        configureMediaButtons();

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
        if (intent.getBooleanExtra(EXTRA_FORCE_SPLIT, false)) {
            handler.postDelayed(this::openNavigation, 350);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        rebuildCustomApps();
        refreshMediaSessions();
        resumeDimOverlayIfNeeded();
        stopService(new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_STOP));
        // V2 intentionally does not reopen Waze here. If the user closes Waze,
        // the visible Open Waze button leaves the decision with the user.
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
        albumArt = findViewById(R.id.albumArt);
        trackTitle = findViewById(R.id.trackTitle);
        artistText = findViewById(R.id.artistText);
        playPauseButton = findViewById(R.id.playPauseButton);
        mediaProgress = findViewById(R.id.mediaProgress);
        findViewById(R.id.openWazeButton).setOnClickListener(v -> openNavigation());
    }

    private void configureMediaButtons() {
        findViewById(R.id.previousButton).setOnClickListener(v -> {
            if (spotifyMediaController != null) {
                spotifyMediaController.getTransportControls().skipToPrevious();
            } else {
                handleNoSpotifySession();
            }
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
            if (spotifyMediaController != null) {
                spotifyMediaController.getTransportControls().skipToNext();
            } else {
                handleNoSpotifySession();
            }
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
        fixedAppsContainer.addView(createAppButton("GPS", WAZE_PACKAGE, v -> openNavigation()));
        fixedAppsContainer.addView(createAppButton("VLC", VLC_PACKAGE, v -> launchVlcWithFloatingAssist()));
        fixedAppsContainer.addView(createAppButton("Spotify", SPOTIFY_PACKAGE, v -> launchSpotifyWithReturn()));
        fixedAppsContainer.addView(createAppButton("Instellingen", null, v ->
                startActivity(new Intent(this, SettingsActivity.class))));
    }

    private View createAppButton(String label, String iconPackage, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.slot_bg);
        button.setPadding(dp(4), dp(5), dp(4), dp(3));
        button.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (iconPackage == null) icon.setImageResource(R.drawable.ic_settings);
        else icon.setImageDrawable(loadAppIcon(iconPackage));
        button.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView text = new TextView(this);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setText(label);
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(label.length() > 9 ? 9f : 11f);
        button.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void rebuildCustomApps() {
        if (customAppsContainer == null) return;
        customAppsContainer.removeAllViews();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            customAppsContainer.addView(createCustomSlot(slot));
        }
    }

    private View createCustomSlot(int slot) {
        String packageName = prefs.getString("slot_" + slot, null);
        LinearLayout slotView = new LinearLayout(this);
        slotView.setOrientation(LinearLayout.VERTICAL);
        slotView.setGravity(Gravity.CENTER);
        slotView.setBackgroundResource(R.drawable.slot_bg);
        slotView.setPadding(dp(2), dp(4), dp(2), dp(2));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TextView label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(9);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);

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

        slotView.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        slotView.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        slotView.setLayoutParams(params);
        return slotView;
    }

    private boolean beginSlotDrag(View view, int slot) {
        trashTarget.setVisibility(View.VISIBLE);
        ClipData data = ClipData.newPlainText("slot", Integer.toString(slot));
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
        view.startDragAndDrop(data, shadow, slot, 0);
        return true;
    }

    private void configureDragTrash() {
        trashTarget.setOnDragListener((view, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_ENTERED:
                    view.setScaleX(1.07f);
                    view.setScaleY(1.07f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    int slot = (Integer) event.getLocalState();
                    prefs.edit().remove("slot_" + slot).apply();
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    rebuildCustomApps();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    trashTarget.setVisibility(View.GONE);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void openAppPicker(int slot) {
        Intent picker = new Intent(this, AppPickerActivity.class);
        startActivityForResult(picker, APP_PICKER_BASE + slot);
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

    public void openNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastSplitRequest < 2_500) return;
        lastSplitRequest = now;

        if (prefs.getBoolean(SettingsActivity.PREF_START_GPS, true)
                && !gpsPrimed
                && isInstalled(GPS_CONNECTOR_PACKAGE)) {
            gpsPrimed = true;
            Intent gps = getPackageManager().getLaunchIntentForPackage(GPS_CONNECTOR_PACKAGE);
            if (gps != null) {
                gps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                try {
                    startActivity(gps);
                } catch (RuntimeException ignored) {
                }
                handler.postDelayed(() -> {
                    Intent home = new Intent(this, DashboardActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            WindowMetrics metrics = getWindowManager().getMaximumWindowMetrics();
            Rect full = metrics.getBounds();
            int left = full.left + Math.round(full.width() * 0.35f);
            Rect wazeBounds = new Rect(left, full.top, full.right, full.bottom);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(wazeBounds);
            startActivity(intent, options.toBundle());
        } catch (RuntimeException e) {
            try {
                startActivity(intent);
            } catch (RuntimeException second) {
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

    private void launchVlcWithFloatingAssist() {
        if (!isInstalled(VLC_PACKAGE)) {
            Toast.makeText(this, "VLC is niet geïnstalleerd", Toast.LENGTH_SHORT).show();
            return;
        }
        showExternalReturnOverlay("↩ VLC zwevend");
        launchPackage(VLC_PACKAGE);
        Toast.makeText(this,
                "Start een video en tik op ‘VLC zwevend’. VLC gebruikt PiP als dit in VLC is ingeschakeld.",
                Toast.LENGTH_LONG).show();
    }

    private void showExternalReturnOverlay(String label) {
        if (!prefs.getBoolean(SettingsActivity.PREF_EXTERNAL_RETURN_OVERLAY, true)) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Geef overlaytoegang in RaspiCar-instellingen voor de zwevende terugknop.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent overlay = new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_SHOW)
                .putExtra(ExternalAppOverlayService.EXTRA_LABEL, label);
        startService(overlay);
    }

    private void launchPackage(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, loadAppLabel(packageName) + " is niet geïnstalleerd", Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(launch);
        } catch (RuntimeException e) {
            Toast.makeText(this, "App kon niet worden geopend", Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable loadAppIcon(String packageName) {
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
            if (VLC_PACKAGE.equals(packageName)) return "VLC";
            if (SPOTIFY_PACKAGE.equals(packageName)) return "Spotify";
            return packageName;
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void configureLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
            return;
        }
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper());
        } catch (RuntimeException ignored) {
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper());
        } catch (RuntimeException ignored) {
        }
        Location latest = null;
        try {
            latest = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (RuntimeException ignored) {
        }
        if (latest == null) {
            try {
                latest = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            } catch (RuntimeException ignored) {
            }
        }
        if (latest != null) onLocationChanged(latest);
    }

    @Override
    public void onLocationChanged(Location location) {
        int kmh = location.hasSpeed() ? Math.max(0, Math.round(location.getSpeed() * 3.6f)) : 0;
        speedText.setText(getString(R.string.speed_format, kmh));
        maybeUpdateWeather(location);
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
                connection.setRequestProperty("User-Agent", "RaspiCarDashboard/2.0");
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
        if (mediaSessionManager == null) {
            mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        }
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
                    selected = controller;
                    break;
                }
                if (selected == null) selected = controller;
            }
        }

        if (spotifyMediaController != null && spotifyMediaController != selected) {
            try {
                spotifyMediaController.unregisterCallback(mediaCallback);
            } catch (RuntimeException ignored) {
            }
        }
        spotifyMediaController = selected;
        if (spotifyMediaController != null) {
            spotifyMediaController.registerCallback(mediaCallback, handler);
        }
        updateMediaUi();
    }

    private void clearSpotifyController() {
        if (spotifyMediaController != null) {
            try {
                spotifyMediaController.unregisterCallback(mediaCallback);
            } catch (RuntimeException ignored) {
            }
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
            if (artist == null || artist.length() == 0) {
                artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        trackTitle.setText(title == null || title.length() == 0 ? "Spotify" : title);
        artistText.setText(artist == null || artist.length() == 0 ? "Spotify" : artist);
        if (art != null) albumArt.setImageBitmap(art);
        else albumArt.setImageDrawable(loadAppIcon(SPOTIFY_PACKAGE));

        PlaybackState state = spotifyMediaController.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        playPauseButton.setText(playing ? "Ⅱ" : "▶");
        updateMediaProgress();
    }

    private void updateMediaProgress() {
        if (spotifyMediaController == null) {
            mediaProgress.setProgress(0);
            return;
        }
        MediaMetadata metadata = spotifyMediaController.getMetadata();
        PlaybackState state = spotifyMediaController.getPlaybackState();
        if (metadata == null || state == null) {
            mediaProgress.setProgress(0);
            return;
        }
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += (long) (elapsed * state.getPlaybackSpeed());
        }
        int progress = duration > 0
                ? (int) Math.max(0, Math.min(1000, position * 1000L / duration))
                : 0;
        mediaProgress.setProgress(progress);
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void openMediaAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void resumeDimOverlayIfNeeded() {
        if (prefs.getBoolean(SettingsActivity.PREF_DIM, false) && Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, DimOverlayService.class)
                    .setAction(DimOverlayService.ACTION_UPDATE)
                    .putExtra(DimOverlayService.EXTRA_PERCENT,
                            prefs.getInt(SettingsActivity.PREF_DIM_PERCENT, 35));
            startService(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
            if (pendingInitialNavigation) {
                pendingInitialNavigation = false;
                scheduleInitialNavigation(500);
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (RuntimeException ignored) {
            }
        }
        clearSpotifyController();
        if (mediaSessionManager != null && mediaListenerRegistered) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            } catch (RuntimeException ignored) {
            }
        }
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    private static final class WeatherDisplay {
        final String icon;
        final String description;

        WeatherDisplay(String icon, String description) {
            this.icon = icon;
            this.description = description;
        }
    }
}
