package nl.roy.raspicardashboard;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.provider.Settings;
import android.widget.Toast;

/** Opens external activities and, when possible, shows the universal return-to-dashboard overlay. */
public final class ExternalAppLauncher {
    private ExternalAppLauncher() { }

    public static boolean launchPackage(Activity activity, String packageName) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return launch(activity, launch, "↩ Dashboard", true);
    }

    public static boolean launch(Activity activity, Intent intent, String label, boolean showReturnButton) {
        if (showReturnButton) showReturnOverlay(activity, label);
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            Rect full = activity.getWindowManager().getMaximumWindowMetrics().getBounds();
            int left = full.left + Math.round(full.width() * 0.35f);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(new Rect(left, full.top, full.right, full.bottom));
            activity.startActivity(intent, options.toBundle());
            return true;
        } catch (RuntimeException e) {
            try {
                activity.startActivity(intent);
                return true;
            } catch (RuntimeException fallback) {
                if (showReturnButton) {
                    activity.stopService(new Intent(activity, ExternalAppOverlayService.class)
                            .setAction(ExternalAppOverlayService.ACTION_STOP));
                }
                Toast.makeText(activity, "App of scherm kon niet worden geopend", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    public static void showReturnOverlay(Context context, String label) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(SettingsActivity.PREF_EXTERNAL_RETURN_OVERLAY, true)) return;
        if (!Settings.canDrawOverlays(context)) return;
        context.startService(new Intent(context, ExternalAppOverlayService.class)
                .setAction(ExternalAppOverlayService.ACTION_SHOW)
                .putExtra(ExternalAppOverlayService.EXTRA_LABEL,
                        label == null || label.trim().isEmpty() ? "↩ Dashboard" : label));
    }
}
