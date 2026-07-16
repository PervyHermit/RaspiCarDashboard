package nl.roy.raspicardashboard;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class DimOverlayService extends Service {
    public static final String ACTION_UPDATE = "nl.roy.raspicardashboard.DIM_UPDATE";
    public static final String ACTION_STOP = "nl.roy.raspicardashboard.DIM_STOP";
    public static final String EXTRA_PERCENT = "percent";

    private WindowManager windowManager;
    private View overlay;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int percent = intent == null ? 35 : intent.getIntExtra(EXTRA_PERCENT, 35);
        percent = Math.max(0, Math.min(90, percent));
        showOrUpdate(percent / 100f);
        return START_STICKY;
    }

    private void showOrUpdate(float alpha) {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
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
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            windowManager.addView(view, params);
            overlay = view;
        } else {
            overlay.setAlpha(alpha);
        }
    }

    private void removeOverlay() {
        if (windowManager != null && overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (RuntimeException ignored) {
            }
        }
        overlay = null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
