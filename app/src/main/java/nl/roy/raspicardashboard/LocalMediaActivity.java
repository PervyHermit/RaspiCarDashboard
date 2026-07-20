package nl.roy.raspicardashboard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Folder chooser and simple library browser for the built-in local player. */
public final class LocalMediaActivity extends Activity {
    private static final int TREE_REQUEST = 91;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<LocalTrack> tracks = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView folderText;
    private TextView statusText;
    private ListView listView;
    private TrackAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_media);
        hideSystemBars();
        prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        folderText = findViewById(R.id.localFolderText);
        statusText = findViewById(R.id.localMediaStatus);
        listView = findViewById(R.id.localTrackList);
        adapter = new TrackAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> playTrack(tracks.get(position)));
        findViewById(R.id.chooseLocalFolderButton).setOnClickListener(v -> chooseFolder());
        findViewById(R.id.refreshLocalLibraryButton).setOnClickListener(v -> loadLibrary());
        findViewById(R.id.stopLocalPlaybackButton).setOnClickListener(v -> {
            startService(new Intent(this, LocalMediaPlaybackService.class)
                    .setAction(LocalMediaPlaybackService.ACTION_STOP));
        });
        ThemeManager.apply(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 92);
        }
        loadLibrary();
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, TREE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != TREE_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        prefs.edit().putString(SettingsActivity.PREF_LOCAL_MEDIA_TREE, tree.toString()).apply();
        loadLibrary();
    }

    private void loadLibrary() {
        String treeText = prefs.getString(SettingsActivity.PREF_LOCAL_MEDIA_TREE, null);
        if (treeText == null) {
            folderText.setText("Nog geen muziekmap gekozen");
            statusText.setText("Kies een map op interne opslag, SD-kaart of USB-stick.");
            tracks.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        Uri tree = Uri.parse(treeText);
        folderText.setText("Muziekmap: " + tree.getLastPathSegment());
        statusText.setText("Muziek zoeken…");
        executor.execute(() -> {
            List<LocalTrack> found = LocalMediaLibrary.scan(this, tree);
            runOnUiThread(() -> {
                tracks.clear();
                tracks.addAll(found);
                adapter.notifyDataSetChanged();
                statusText.setText(found.isEmpty()
                        ? "Geen ondersteunde audiobestanden gevonden"
                        : found.size() + " nummers gevonden • tik om af te spelen");
            });
        });
    }

    private void playTrack(LocalTrack track) {
        String tree = prefs.getString(SettingsActivity.PREF_LOCAL_MEDIA_TREE, null);
        Intent play = new Intent(this, LocalMediaPlaybackService.class)
                .setAction(LocalMediaPlaybackService.ACTION_PLAY_URI)
                .putExtra(LocalMediaPlaybackService.EXTRA_URI, track.uri.toString())
                .putExtra(LocalMediaPlaybackService.EXTRA_TREE_URI, tree);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(play);
            else startService(play);
            prefs.edit().putString(SettingsActivity.PREF_MEDIA_SOURCE, SettingsActivity.MEDIA_SOURCE_LOCAL).apply();
            Toast.makeText(this, "Afspelen: " + track.displayName, Toast.LENGTH_SHORT).show();
            finish();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Nummer kon niet worden gestart", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        ThemeManager.apply(this);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private final class TrackAdapter extends BaseAdapter {
        @Override public int getCount() { return tracks.size(); }
        @Override public Object getItem(int position) { return tracks.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView number;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                number = (TextView) row.getChildAt(0);
                title = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(LocalMediaActivity.this);
                row.setTag("slot");
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(6), dp(14), dp(6));
                number = new TextView(LocalMediaActivity.this);
                number.setGravity(Gravity.CENTER);
                number.setTypeface(null, Typeface.BOLD);
                number.setTextColor(ThemeManager.getPalette(LocalMediaActivity.this).accent);
                row.addView(number, new LinearLayout.LayoutParams(dp(50), dp(54)));
                title = new TextView(LocalMediaActivity.this);
                title.setTextColor(ThemeManager.getPalette(LocalMediaActivity.this).textPrimary);
                title.setTextSize(17);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1f));
            }
            number.setText(Integer.toString(position + 1));
            title.setText(tracks.get(position).displayName);
            ThemeManager.styleDynamicCard(LocalMediaActivity.this, row, "slot");
            return row;
        }
    }
}
