package nl.roy.raspicardashboard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/** Applies one safe dark dashboard palette with a selectable accent colour. */
public final class ThemeManager {
    public static final String PREF_THEME = "theme";
    public static final String PREF_CUSTOM_ACCENT = "custom_accent";

    public static final String THEME_BLUE = "blue";
    public static final String THEME_GRAPHITE = "graphite";
    public static final String THEME_ORANGE = "orange";
    public static final String THEME_GREEN = "green";
    public static final String THEME_RED = "red";
    public static final String THEME_PURPLE = "purple";
    public static final String THEME_CUSTOM = "custom";

    private ThemeManager() { }

    public static Palette getPalette(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE);
        String theme = prefs.getString(PREF_THEME, THEME_BLUE);
        int accent;
        int bg = Color.rgb(5, 10, 16);
        int panel = Color.rgb(11, 22, 32);
        int panelAlt = Color.rgb(14, 27, 39);
        switch (theme == null ? THEME_BLUE : theme) {
            case THEME_GRAPHITE:
                accent = Color.rgb(172, 184, 196);
                bg = Color.rgb(8, 10, 13);
                panel = Color.rgb(20, 24, 29);
                panelAlt = Color.rgb(27, 32, 38);
                break;
            case THEME_ORANGE:
                accent = Color.rgb(255, 145, 48);
                break;
            case THEME_GREEN:
                accent = Color.rgb(48, 203, 118);
                break;
            case THEME_RED:
                accent = Color.rgb(242, 78, 86);
                break;
            case THEME_PURPLE:
                accent = Color.rgb(157, 105, 255);
                break;
            case THEME_CUSTOM:
                accent = parseAccent(prefs.getString(PREF_CUSTOM_ACCENT, "#2A92FF"));
                break;
            case THEME_BLUE:
            default:
                accent = Color.rgb(42, 146, 255);
                break;
        }
        return new Palette(bg, panel, panelAlt, accent,
                Color.rgb(245, 248, 252), Color.rgb(168, 180, 194));
    }

    public static void apply(Activity activity) {
        Palette palette = getPalette(activity);
        activity.getWindow().setStatusBarColor(palette.bg);
        activity.getWindow().setNavigationBarColor(palette.bg);
        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyRecursive(root, palette, activity);
    }

    public static void styleDynamicCard(Context context, View view, String role) {
        Palette palette = getPalette(context);
        applyRole(view, role, palette, context);
    }

    private static void applyRecursive(View view, Palette palette, Context context) {
        Object rawTag = view.getTag();
        if (rawTag instanceof String) applyRole(view, (String) rawTag, palette, context);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), palette, context);
            }
        }
    }

    private static void applyRole(View view, String role, Palette palette, Context context) {
        switch (role) {
            case "root":
                view.setBackgroundColor(palette.bg);
                break;
            case "panel":
                view.setBackground(rounded(palette.panel, dp(context, 16), 0, 0));
                break;
            case "panelAlt":
                view.setBackground(rounded(palette.panelAlt, dp(context, 12), 0, 0));
                break;
            case "slot":
                view.setBackground(rounded(palette.panelAlt, dp(context, 13), palette.accent, dp(context, 1)));
                break;
            case "action":
                view.setBackground(rounded(palette.panelAlt, dp(context, 13), palette.accent, dp(context, 1)));
                break;
            case "accentBg":
                view.setBackground(rounded(palette.accent, dp(context, 30), 0, 0));
                break;
            case "accentCircle":
                view.setBackground(oval(palette.accent));
                break;
            case "accentText":
                if (view instanceof TextView) ((TextView) view).setTextColor(palette.accent);
                break;
            case "primaryText":
                if (view instanceof TextView) ((TextView) view).setTextColor(palette.textPrimary);
                break;
            case "secondaryText":
                if (view instanceof TextView) ((TextView) view).setTextColor(palette.textSecondary);
                break;
            default:
                break;
        }
    }

    public static int parseAccent(String input) {
        if (input == null) return Color.rgb(42, 146, 255);
        String value = input.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith("#")) value = "#" + value;
        try {
            int parsed = Color.parseColor(value);
            // Prevent almost-black accents that disappear on the dashboard.
            float[] hsv = new float[3];
            Color.colorToHSV(parsed, hsv);
            if (hsv[2] < 0.45f) hsv[2] = 0.65f;
            if (hsv[1] < 0.25f) hsv[1] = 0.45f;
            return Color.HSVToColor(hsv);
        } catch (IllegalArgumentException ignored) {
            return Color.rgb(42, 146, 255);
        }
    }

    public static String colorToHex(int color) {
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF & color);
    }

    private static GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static final class Palette {
        public final int bg;
        public final int panel;
        public final int panelAlt;
        public final int accent;
        public final int textPrimary;
        public final int textSecondary;

        Palette(int bg, int panel, int panelAlt, int accent, int textPrimary, int textSecondary) {
            this.bg = bg;
            this.panel = panel;
            this.panelAlt = panelAlt;
            this.accent = accent;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
        }
    }
}
