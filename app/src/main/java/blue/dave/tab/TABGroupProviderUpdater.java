package blue.dave.tab;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class TABGroupProviderUpdater {

    private static final String META_DATA_PROVIDER = "android.appwidget.provider";
    private static final String TAG = TABTaskService.class.getName();

    private List<TABProviderUpdater> updaters = new ArrayList<>();
    private AppWidgetManager manager;

    public TABGroupProviderUpdater(Context context) {

        try {
            android.os.Debug.waitForDebugger();
            // Fetching the information already provided for us in the AndroidManifest (receiver nodes)
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_RECEIVERS | PackageManager.GET_META_DATA);
            for(ActivityInfo activityInfo : packageInfo.receivers) {
                // @see meta-data tag
                int layoutResourceId = activityInfo.metaData.getInt(META_DATA_PROVIDER);
                // @see name attribute
                Class<?> providerClass = Class.forName(activityInfo.name);
                // Append widget provider updater to the group
                updaters.add(new TABProviderUpdater(context, providerClass, layoutResourceId));
            }
        } catch(Exception e) {
            Log.e(TAG, "Could not fetch receivers", e);
            throw new RuntimeException(e);
        }

        manager = AppWidgetManager.getInstance(context);
    }

    public void setTextViewText(int resource, String text) {
        for(TABProviderUpdater updater : updaters) {
            updater.getViews().setTextViewText(resource, text);
        }
    }

    public void setViewVisibility(int resource, int visibility) {
        for(TABProviderUpdater updater : updaters) {
            updater.getViews().setViewVisibility(resource, visibility);
        }
    }

    public void persist() {
        for(TABProviderUpdater updater : updaters) {
            int[] allWidgetIds = manager.getAppWidgetIds(updater.getName());
            manager.partiallyUpdateAppWidget(allWidgetIds, updater.getViews());
        }
    }

}
