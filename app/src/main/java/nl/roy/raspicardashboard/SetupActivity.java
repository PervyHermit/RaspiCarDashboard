package nl.roy.raspicardashboard;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class SetupActivity extends Activity {
    private static final int STEP_COUNT = 7;
    private static final String WAZE = "com.waze";
    private static final String GPS = "de.pilablu.gpsconnector";
    private static final String SPOTIFY = "com.spotify.music";

    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView stepText;
    private TextView title;
    private TextView description;
    private Button backButton;
    private Button nextButton;
    private int step;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        hideSystemBars();
        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        SettingsActivity.migrateLegacyPreferences(prefs);
        content = findViewById(R.id.setupContent);
        stepText = findViewById(R.id.setupStepText);
        title = findViewById(R.id.setupTitle);
        description = findViewById(R.id.setupDescription);
        backButton = findViewById(R.id.setupBackButton);
        nextButton = findViewById(R.id.setupNextButton);
        backButton.setOnClickListener(v -> { if (step > 0) { step--; renderStep(); } });
        nextButton.setOnClickListener(v -> {
            if (step < STEP_COUNT - 1) { step++; renderStep(); }
            else finishSetup();
        });
        ThemeManager.apply(this);
        renderStep();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        ThemeManager.apply(this);
        renderStep();
        stopService(new Intent(this, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_STOP));
    }

    private void renderStep() {
        if (content == null) return;
        content.removeAllViews();
        stepText.setText("STAP " + (step + 1) + " VAN " + STEP_COUNT);
        backButton.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        nextButton.setText(step == STEP_COUNT - 1 ? "Dashboard starten" : "Volgende");
        switch (step) {
            case 0: renderWelcome(); break;
            case 1: renderApps(); break;
            case 2: renderPermissions(); break;
            case 3: renderCamera(); break;
            case 4: renderLayoutTheme(); break;
            case 5: renderWazeHome(); break;
            default: renderSummary(); break;
        }
        ThemeManager.apply(this);
    }

    private void renderWelcome() {
        title.setText("Welkom bij RaspiCar V4");
        description.setText("We lopen locatie, apps, toestemmingen, muziek, camera, layout en Waze stap voor stap langs.");
        addLargeText("RaspiCar gebruikt links een eigen dashboard en opent Waze rechts in split-screen. Kies Spotify, lokale muziek of automatisch; de USB-camera kan rechtstreeks in het dashboard worden getoond.");
        addInfo("Je kunt deze setup later opnieuw openen vanuit RaspiCar-instellingen.");
    }

    private void renderApps() {
        title.setText("Apps en locatiebron");
        description.setText("Waze is nodig voor navigatie. GPS Connector is alleen nodig bij een externe gps zoals de Garmin.");
        addLabel("Locatiebron");
        Spinner gpsSource = new Spinner(this);
        gpsSource.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Ingebouwde Android-gps", "Externe gps via GPS Connector"}));
        boolean useConnector = prefs.getBoolean(SettingsActivity.PREF_START_GPS, true);
        gpsSource.setSelection(useConnector ? 1 : 0);
        gpsSource.setOnItemSelectedListener(new SimpleSelectionListener(position -> {
            boolean selectedConnector = position == 1;
            boolean previous = prefs.getBoolean(SettingsActivity.PREF_START_GPS, true);
            prefs.edit().putBoolean(SettingsActivity.PREF_START_GPS, selectedConnector).apply();
            if (selectedConnector != previous) content.post(this::renderStep);
        }));
        content.addView(gpsSource, fullWidth(58));
        addAppRow("Waze", WAZE, true);
        if (useConnector) addAppRow("GPS Connector", GPS, true);
        addAppRow("Spotify", SPOTIFY, false);
        addLabel("Mediabron voor het dashboard");
        Spinner mediaSource = new Spinner(this);
        mediaSource.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Spotify", "Lokale muziek", "Automatisch"}));
        String source = prefs.getString(SettingsActivity.PREF_MEDIA_SOURCE, SettingsActivity.MEDIA_SOURCE_SPOTIFY);
        mediaSource.setSelection(SettingsActivity.MEDIA_SOURCE_LOCAL.equals(source) ? 1
                : SettingsActivity.MEDIA_SOURCE_AUTO.equals(source) ? 2 : 0);
        mediaSource.setOnItemSelectedListener(new SimpleSelectionListener(position ->
                prefs.edit().putString(SettingsActivity.PREF_MEDIA_SOURCE,
                        new String[]{SettingsActivity.MEDIA_SOURCE_SPOTIFY, SettingsActivity.MEDIA_SOURCE_LOCAL,
                                SettingsActivity.MEDIA_SOURCE_AUTO}[position]).apply()));
        content.addView(mediaSource, fullWidth(58));
        Button localLibrary = addButton("Lokale muziekmap kiezen");
        localLibrary.setOnClickListener(v -> startActivity(new Intent(this, LocalMediaActivity.class)));
        addInfo("Gebruik je lokale muziek, dan kun je Spotify overslaan. Bij Automatisch volgt RaspiCar de bron die daadwerkelijk speelt.");
    }

    private void renderPermissions() {
        title.setText("Toestemmingen");
        description.setText("Geef de rechten één voor één. RaspiCar controleert iedere stap opnieuw wanneer je terugkomt.");
        addPermissionRow("Locatie", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION), v ->
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 301));
        addPermissionRow("Camera", hasPermission(Manifest.permission.CAMERA), v ->
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 302));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            addPermissionRow("Lokale muzieknotificatie",
                    hasPermission(Manifest.permission.POST_NOTIFICATIONS), v ->
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 303));
        }
        addPermissionRow("Spotify-mediatoegang (optioneel)", hasNotificationAccess(), v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            if (!ExternalAppLauncher.launch(this, intent, "↩ Setup", true))
                ExternalAppLauncher.launch(this, new Intent(Settings.ACTION_SETTINGS), "↩ Setup", true);
        });
        addPermissionRow("Dimlaag / zwevende terugknop", Settings.canDrawOverlays(this), v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        addInfo("Is een speciale instelling grijs? Open Android → Apps → RaspiCar Dashboard → ⋮ → ‘Allow restricted settings’ en probeer opnieuw.");
    }

    private void renderCamera() {
        title.setText("USB-camera");
        description.setText("Kies de camera, spiegeling en rotatie met een live preview.");
        String cameraId = prefs.getString(SettingsActivity.PREF_CAMERA_ID, null);
        addLargeText(cameraId == null ? "Nog geen camera gekozen" : "Gekozen camera: " + cameraId);
        Button choose = addButton("Camera kiezen en testen");
        choose.setOnClickListener(v -> startActivity(new Intent(this, CameraSelectionActivity.class)));
        addInfo("De camera wordt alleen geopend wanneer je op Camera tikt. In Instellingen kies je volledig beeld of vullen, 4:3/16:9 en de camerabreedte.");
    }

    private void renderLayoutTheme() {
        title.setText("Layout en kleur");
        description.setText("Automatisch past zich aan het huidige appvenster aan. Je kunt dit altijd overschrijven.");
        addLabel("Layoutgrootte");
        Spinner layout = new Spinner(this);
        String[] layouts = {"Automatisch aanbevolen", "Compact", "Medium", "Large"};
        layout.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, layouts));
        String currentLayout = prefs.getString(SettingsActivity.PREF_LAYOUT_SIZE, "auto");
        layout.setSelection(layoutIndex(currentLayout));
        layout.setOnItemSelectedListener(new SimpleSelectionListener(position ->
                prefs.edit().putString(SettingsActivity.PREF_LAYOUT_SIZE,
                        new String[]{"auto", "compact", "medium", "large"}[position]).apply()));
        content.addView(layout, fullWidth(58));

        addLabel("Kleurschema");
        Spinner theme = new Spinner(this);
        String[] themes = {"Dark Blue", "Graphite", "Orange", "Green", "Red", "Purple"};
        String[] themeValues = {ThemeManager.THEME_BLUE, ThemeManager.THEME_GRAPHITE,
                ThemeManager.THEME_ORANGE, ThemeManager.THEME_GREEN,
                ThemeManager.THEME_RED, ThemeManager.THEME_PURPLE};
        theme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themes));
        theme.setSelection(themeIndex(prefs.getString(ThemeManager.PREF_THEME, ThemeManager.THEME_BLUE)));
        theme.setOnItemSelectedListener(new SimpleSelectionListener(position -> {
            prefs.edit().putString(ThemeManager.PREF_THEME, themeValues[position]).apply();
            ThemeManager.apply(this);
        }));
        content.addView(theme, fullWidth(58));
        addInfo("In de gewone instellingen kun je later ook een eigen accentkleur invoeren.");
    }

    private void renderWazeHome() {
        title.setText("Waze en Home");
        description.setText("Test de split-screencombinatie en stel RaspiCar pas daarna als Home-app in.");
        Button test = addButton("Test dashboard + Waze");
        test.setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)
                .putExtra(DashboardActivity.EXTRA_ALLOW_SETUP_PREVIEW, true)
                .putExtra(DashboardActivity.EXTRA_FORCE_SPLIT, true)));
        Button home = addButton("Als standaard Home-app kiezen");
        home.setOnClickListener(v -> {
            if (!ExternalAppLauncher.launch(this, new Intent(Settings.ACTION_HOME_SETTINGS), "↩ Setup", true))
                ExternalAppLauncher.launch(this, new Intent(Settings.ACTION_SETTINGS), "↩ Setup", true);
        });
        addInfo("Sluit je Waze handmatig, dan blijft hij gesloten. Gebruik daarna ‘Open Waze’ in het dashboard om hem terug te halen.");
    }

    private void renderSummary() {
        title.setText("Klaar voor gebruik");
        description.setText("Controleer de belangrijkste onderdelen. Ontbrekende optionele onderdelen blokkeren de setup niet.");
        addCheck("Waze", isInstalled(WAZE));
        if (prefs.getBoolean(SettingsActivity.PREF_START_GPS, true)) addCheck("GPS Connector", isInstalled(GPS));
        else addCheck("Ingebouwde Android-gps gekozen", true);
        addCheck("Spotify", isInstalled(SPOTIFY));
        addCheck("Lokale muziekmap", prefs.getString(SettingsActivity.PREF_LOCAL_MEDIA_TREE, null) != null);
        addCheck("Locatie", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        addCheck("Camera", hasPermission(Manifest.permission.CAMERA));
        addCheck("Mediatoegang", hasNotificationAccess());
        addCheck("Overlaytoegang", Settings.canDrawOverlays(this));
        addCheck("Camera geselecteerd", prefs.getString(SettingsActivity.PREF_CAMERA_ID, null) != null);
    }

    private void finishSetup() {
        prefs.edit().putBoolean(SettingsActivity.PREF_SETUP_COMPLETE, true).apply();
        startActivity(new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void addAppRow(String label, String packageName, boolean required) {
        LinearLayout row = new LinearLayout(this);
        row.setTag("slot");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
        TextView text = new TextView(this);
        boolean installed = isInstalled(packageName);
        text.setText(label + (required ? "  • vereist" : "  • optioneel") + "\n"
                + (installed ? "Geïnstalleerd" : "Niet geïnstalleerd"));
        text.setTextColor(ThemeManager.getPalette(this).textPrimary);
        text.setTextSize(16);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(64), 1f));
        Button button = new Button(this);
        button.setText(installed ? "Open" : "Installeren");
        button.setOnClickListener(v -> {
            if (installed) {
                ExternalAppLauncher.launchPackage(this, packageName);
            } else openStore(packageName);
        });
        row.addView(button, new LinearLayout.LayoutParams(dp(150), dp(52)));
        addWithMargin(row);
    }

    private void addPermissionRow(String label, boolean granted, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setTag("slot");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
        TextView text = new TextView(this);
        text.setText((granted ? "✓  " : "!  ") + label);
        text.setTextColor(granted ? 0xFF4ED58A : 0xFFF5F8FC);
        text.setTextSize(17);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(58), 1f));
        Button button = new Button(this);
        button.setText(granted ? "Gereed" : "Toestaan");
        button.setEnabled(!granted);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(dp(145), dp(50)));
        addWithMargin(row);
    }

    private void addCheck(String label, boolean okay) {
        TextView text = new TextView(this);
        text.setText((okay ? "✓" : "—") + "  " + label);
        text.setTextColor(okay ? 0xFF4ED58A : ThemeManager.getPalette(this).textSecondary);
        text.setTextSize(17);
        text.setPadding(dp(8), dp(7), dp(8), dp(7));
        content.addView(text, fullWidth(48));
    }

    private void addLargeText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(ThemeManager.getPalette(this).textPrimary);
        text.setTextSize(19);
        text.setPadding(dp(8), dp(10), dp(8), dp(10));
        content.addView(text, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addInfo(String value) {
        TextView text = new TextView(this);
        text.setTag("panelAlt");
        text.setText(value);
        text.setTextColor(ThemeManager.getPalette(this).textSecondary);
        text.setTextSize(14);
        text.setPadding(dp(14), dp(12), dp(14), dp(12));
        addWithMargin(text);
    }

    private void addLabel(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTypeface(null, Typeface.BOLD);
        text.setTextColor(ThemeManager.getPalette(this).accent);
        text.setTextSize(15);
        text.setPadding(dp(4), dp(12), dp(4), dp(4));
        content.addView(text, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private Button addButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        addWithMargin(button);
        return button;
    }

    private void addWithMargin(View view) {
        LinearLayout.LayoutParams params = fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        content.addView(view, params);
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height == ViewGroup.LayoutParams.WRAP_CONTENT
                ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(height));
    }

    private void openStore(String packageName) {
        boolean opened = ExternalAppLauncher.launch(this, new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageName)), "↩ Setup", true);
        if (!opened) {
            opened = ExternalAppLauncher.launch(this, new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)), "↩ Setup", true);
        }
        if (!opened) Toast.makeText(this, "Geen appwinkel of browser gevonden", Toast.LENGTH_LONG).show();
    }

    private boolean isInstalled(String packageName) {
        try { getPackageManager().getApplicationInfo(packageName, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        ComponentName component = new ComponentName(this, MediaNotificationListener.class);
        return enabled != null && (enabled.contains(component.flattenToString()) || enabled.contains(getPackageName()));
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
        return 0;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static final class SimpleSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void selected(int position); }
        private final Callback callback;
        SimpleSelectionListener(Callback callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            callback.selected(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
