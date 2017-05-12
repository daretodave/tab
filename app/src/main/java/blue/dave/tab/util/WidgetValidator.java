package blue.dave.tab.util;

import android.content.SharedPreferences;

import java.util.Locale;

public class WidgetValidator {

    private static final String VALIDATOR_PREFIX = "WIDGET_VALID_";

    private static String getPreferenceKey(int widgetId) {
        return String.format(Locale.ROOT, "%s%d", VALIDATOR_PREFIX, widgetId);
    }

    public static boolean configured(SharedPreferences preferences, int... appWidgetIds) {
        for(int appWidgetId : appWidgetIds) {
            if(!preferences.contains(getPreferenceKey(appWidgetId))) {
                return false;
            }
        }
        return true;
    }

    public static void onConfigured(SharedPreferences preferences, int... appWidgetIds) {
        SharedPreferences.Editor editor = preferences.edit();
        for(int appWidgetId : appWidgetIds) {
            editor.putBoolean(getPreferenceKey(appWidgetId), true);
        }
        editor.apply();
    }

    public static void finish(SharedPreferences preferences, int... appWidgetIds) {
        SharedPreferences.Editor editor = preferences.edit();
        for(int appWidgetId : appWidgetIds) {
            editor.remove(getPreferenceKey(appWidgetId));
        }
        editor.apply();
    }


}
