package nl.roy.raspicardashboard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

public final class AppPickerActivity extends Activity {
    public static final String EXTRA_PACKAGE = "package";

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);
        hideSystemBars();

        ListView listView = findViewById(R.id.appList);
        EditText search = findViewById(R.id.searchInput);
        loadApps();
        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = visibleApps.get(position);
            Intent result = new Intent().putExtra(EXTRA_PACKAGE, entry.packageName);
            setResult(RESULT_OK, result);
            finish();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results;
        if (Build.VERSION.SDK_INT >= 33) {
            results = pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0));
        } else {
            //noinspection deprecation
            results = pm.queryIntentActivities(query, 0);
        }
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo info : results) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || !seenPackages.add(packageName)) continue;
            CharSequence labelText = info.loadLabel(pm);
            String label = labelText == null ? packageName : labelText.toString();
            Drawable icon = info.loadIcon(pm);
            allApps.add(new AppEntry(label, packageName, icon));
        }
        Collator collator = Collator.getInstance(new Locale("nl", "NL"));
        allApps.sort(Comparator.comparing(entry -> entry.label, collator));
        visibleApps.addAll(allApps);
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (AppEntry entry : allApps) {
            if (needle.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(needle)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                visibleApps.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleApps.size(); }
        @Override public Object getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView text;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                icon = (ImageView) row.getChildAt(0);
                text = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(AppPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                icon = new ImageView(AppPickerActivity.this);
                row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
                text = new TextView(AppPickerActivity.this);
                text.setTextColor(getColor(R.color.text_primary));
                text.setTextSize(18);
                text.setPadding(dp(16), 0, 0, 0);
                row.addView(text, new LinearLayout.LayoutParams(0, dp(62), 1f));
            }
            AppEntry entry = visibleApps.get(position);
            icon.setImageDrawable(entry.icon);
            text.setText(entry.label);
            return row;
        }
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

    private static final class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
