package nl.roy.raspicardashboard;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Date;

/** Full-screen, touch-through dim layer with off/manual/sun-controlled modes. */
public final class DimOverlayService extends Service {
    public static final String ACTION_UPDATE = "nl.roy.raspicardashboard.DIM_UPDATE";
    public static final String ACTION_STOP = "nl.roy.raspicardashboard.DIM_STOP";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            applyPreferences();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            handler.removeCallbacks(autoRefresh);
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(autoRefresh);
        applyPreferences();
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        if (SettingsActivity.DIM_MODE_AUTO.equals(
                prefs.getString(SettingsActivity.PREF_DIM_MODE, SettingsActivity.DIM_MODE_OFF))) {
            handler.postDelayed(autoRefresh, 60_000L);
        }
        return START_STICKY;
    }

    private void applyPreferences() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        String mode = prefs.getString(SettingsActivity.PREF_DIM_MODE, SettingsActivity.DIM_MODE_OFF);
        if (SettingsActivity.DIM_MODE_OFF.equals(mode)) {
            removeOverlay();
            return;
        }

        int percent;
        if (SettingsActivity.DIM_MODE_MANUAL.equals(mode)) {
            percent = prefs.getInt(SettingsActivity.PREF_DIM_PERCENT, 35);
        } else {
            int dayPercent = prefs.getInt(SettingsActivity.PREF_DIM_DAY_PERCENT, 0);
            int nightPercent = prefs.getInt(SettingsActivity.PREF_DIM_NIGHT_PERCENT, 45);
            int offset = prefs.getInt(SettingsActivity.PREF_DIM_SUN_OFFSET, 0);
            boolean night = fallbackNightByClock();
            String latValue = prefs.getString(SettingsActivity.PREF_LAST_LATITUDE, null);
            String lonValue = prefs.getString(SettingsActivity.PREF_LAST_LONGITUDE, null);
            if (latValue != null && lonValue != null) {
                try {
                    night = SunCalculator.isNight(new Date(),
                            Double.parseDouble(latValue), Double.parseDouble(lonValue), offset);
                } catch (NumberFormatException ignored) { }
            }
            percent = night ? nightPercent : dayPercent;
        }
        showOrUpdate(Math.max(0, Math.min(90, percent)) / 100f);
    }

    private boolean fallbackNightByClock() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 7 || hour >= 19;
    }

    private void showOrUpdate(float alpha) {
        if (alpha <= 0.001f) {
            removeOverlay();
            return;
        }
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (overlay == null) {
            FrameLayout view = new FrameLayout(this);
            view.setBackgroundColor(0xFF000000);
            view.setAlpha(alpha);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            windowManager.addView(view, params);
            overlay = view;
        } else {
            overlay.setAlpha(alpha);
        }
    }

    private void removeOverlay() {
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (RuntimeException ignored) { }
        }
        overlay = null;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
