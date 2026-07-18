package nl.roy.raspicardashboard;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Small draggable return button shown above Spotify so the dashboard can be restored.
 */
public final class ExternalAppOverlayService extends Service {
    public static final String ACTION_SHOW = "nl.roy.raspicardashboard.EXTERNAL_OVERLAY_SHOW";
    public static final String ACTION_STOP = "nl.roy.raspicardashboard.EXTERNAL_OVERLAY_STOP";
    public static final String EXTRA_LABEL = "label";

    private WindowManager windowManager;
    private TextView button;
    private WindowManager.LayoutParams layoutParams;

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

        String label = intent == null ? null : intent.getStringExtra(EXTRA_LABEL);
        showOverlay(label == null || label.isBlank() ? "↩ Dashboard" : label);
        return START_NOT_STICKY;
    }

    private void showOverlay(String label) {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (button != null) {
            button.setText(label);
            return;
        }

        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(0xFFF5F8FC);
        view.setTextSize(16f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackgroundResource(R.drawable.floating_return_bg);
        view.setElevation(dp(10));
        view.setOnClickListener(v -> returnToDashboard());
        view.setOnTouchListener(new DragTouchListener());

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = dp(22);
        layoutParams.y = dp(70);

        windowManager.addView(view, layoutParams);
        button = view;
    }

    private void returnToDashboard() {
        Intent dashboard = new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(dashboard);
        removeOverlay();
        stopSelf();
    }

    private void removeOverlay() {
        if (windowManager != null && button != null) {
            try {
                windowManager.removeView(button);
            } catch (RuntimeException ignored) {
            }
        }
        button = null;
        layoutParams = null;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (layoutParams == null || windowManager == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = layoutParams.x;
                    startY = layoutParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - touchX);
                    int dy = Math.round(event.getRawY() - touchY);
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                    layoutParams.x = Math.max(0, startX + dx);
                    layoutParams.y = Math.max(0, startY + dy);
                    try {
                        windowManager.updateViewLayout(view, layoutParams);
                    } catch (RuntimeException ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        }
    }
}
