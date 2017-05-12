package blue.dave.tab;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Arrays;

import blue.dave.tab.util.WidgetValidator;

public abstract class TABBaseProvider extends AppWidgetProvider {

    private static final String TAG = TABBaseProvider.class.getName();

    private static final String PUNCH = "PUNCH";
    private static final String REFRESH = "REFRESH";

    public static final String PREF_CACHED_STATUS = "CACHED_STATUS";
    public static final String PREF_CACHED_HOURS = "CACHED_HOURS";
    public static final String PREF_CACHED_PUNCH = "CACHED_PUNCH";

    //Cache these values for widgets joining the party later.

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        init(context, getClass(), getLayout(), appWidgetIds);
    }

    public static void init(Context context, Class<?> provider, int layout, int... appWidgetIds) {

        Log.d(TAG, "Init request: " + provider);

        SharedPreferences preferences = context.getSharedPreferences(
                context.getString(R.string.app_preferences),
                Context.MODE_PRIVATE);


        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        ComponentName widget = new ComponentName(context, provider);

        //Hide punch & status
        views.setViewVisibility(R.id.tab_status, View.GONE);
        views.setViewVisibility(R.id.tab_punch, View.GONE);

        //hook punch action
        views.setOnClickPendingIntent(R.id.tab_punch, getPendingSelfIntent(context, provider, PUNCH));
        //hook refresh
        views.setOnClickPendingIntent(R.id.tab_status_container, getPendingSelfIntent(context, provider, REFRESH));

        //hook settings action
        Intent intent = new Intent(context, TABCredentialsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.tab_settings, pendingIntent);

        //check for cached hours & punched status
        String cachedStatus = preferences.getString(PREF_CACHED_STATUS, null);
        String cachedHours = preferences.getString(PREF_CACHED_HOURS, null);

        Log.d(TAG, String.format("Cached status : %s, Cached hours : %s, Working: %b", cachedStatus, cachedHours, TABTaskService.working.get()));
        if ((  (cachedStatus == null && cachedHours == null)
                || (cachedHours  != null && cachedHours.equals(context.getString(R.string.error_title)))
        ) && !TABTaskService.working.get()) {

            if(!WidgetValidator.configured(preferences, appWidgetIds)) {
                Log.d(TAG, "Waiting for configuration on widget(s): " + Arrays.toString(appWidgetIds));
                return;
            }

            //start update status task
            update(context, TABTask.STATUS);

        } else if(cachedStatus != null && cachedHours != null) {

            //use cached status
            views.setTextViewText(R.id.tab_hours, cachedHours);
            views.setTextViewText(R.id.tab_status, cachedStatus);
            views.setViewVisibility(R.id.tab_status, View.VISIBLE);

            //switch loading state to finish if there is no background service
            if(!TABTaskService.working.get()) {
                views.setViewVisibility(R.id.tab_loader, View.GONE);
                views.setViewVisibility(R.id.tab_punch, View.VISIBLE);
            }

        } //other wise, there is a background service and no cache avail: when service is complete, it will update this widget

        appWidgetManager.updateAppWidget(widget, views); //apply initial states
    }

    public static void update(Context context, TABTask task) {

        Log.d(TAG, "Performing ADP task");

        //only one task allowed at a time.
        if(TABTaskService.working.get()) {
            Log.e(TAG, "Can not perform task. Task is already occurring!");
            return;
        }

        Log.d(TAG, "Confirming credentials exist");

        //confirm user provided credentials (E.G. user clears app data via app manager)
        SharedPreferences preferences = context.getSharedPreferences(
                context.getString(R.string.app_preferences),
                Context.MODE_PRIVATE);

        TABGroupProviderUpdater updater = new TABGroupProviderUpdater(context);

        if(!preferences.contains(TABConfigureCredentialsFragment.PREF_USERNAME)
                || !preferences.contains(TABConfigureCredentialsFragment.PREF_PASSWORD)) {

            setMessage(updater,
                    context.getString(R.string.error_title),
                    context.getString(R.string.error_missing_credentials),
                    preferences
            );

            Log.e(TAG, "Credentials do not exist. Bailing");

            return;
        }

        if(preferences.contains(PREF_CACHED_STATUS)) {

            //contextual message update
            switch(task) {
                case STATUS:
                    updater.setTextViewText(R.id.tab_status, context.getString(R.string.status_pending_status));
                    break;
                case PUNCH:
                    String message;

                    if(!preferences.contains(PREF_CACHED_PUNCH)) {
                        message = context.getString(R.string.status_undetermined);
                    } else {
                        boolean punched = preferences.getBoolean(PREF_CACHED_PUNCH, false);
                        if(punched) {
                            message = context.getString(R.string.status_pending_clock_out);
                        } else {
                            message = context.getString(R.string.status_pending_clock_in);
                        }
                    }

                    updater.setTextViewText(R.id.tab_status, message);
                    break;
            }

            updater.setViewVisibility(R.id.tab_status, View.VISIBLE);
        }

        //set layout to a state of loading
        updater.setViewVisibility(R.id.tab_punch, View.GONE);
        updater.setViewVisibility(R.id.tab_loader, View.VISIBLE);
        updater.persist();

        TABTaskService.working.set(true);

        //information needed for all TAB server interactions
        Intent intent = new Intent(context, TABTaskService.class);
        intent.putExtra(TABTaskService.USERNAME, preferences.getString(TABTaskService.USERNAME, ""));
        intent.putExtra(TABTaskService.PASSWORD, preferences.getString(TABTaskService.PASSWORD, ""));
        intent.putExtra(TABTaskService.TASK, task);

        //start service
        context.startService(intent);
        Log.d(TAG, "Started");
    }

    public static void setMessage(TABGroupProviderUpdater updater, String title, String subtitle, SharedPreferences preferences) {

        SharedPreferences.Editor editor = preferences.edit();

        if(title != null) {
            updater.setTextViewText(R.id.tab_hours, title);
            editor.putString(PREF_CACHED_HOURS, title);
        }

        if(subtitle != null) {
            updater.setTextViewText(R.id.tab_status, subtitle);
            editor.putString(PREF_CACHED_STATUS, subtitle);
            updater.setViewVisibility(R.id.tab_status, View.VISIBLE);
        }

        updater.persist();
        editor.apply();

    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        Log.d(TAG, "Received broadcast");
        Log.d(TAG, "Intent: " + intent);
        Log.d(TAG, "Action: " + action);

        if(REFRESH.equals(action) || PUNCH.equals(action)) {

            // TABTask actions, confirm service is not already working!

            if (TABTaskService.working.get()) {
                Log.d(TAG, "TAB is working on task.");
                return;
            }

            if (REFRESH.equals(action)) {
                Log.d(TAG, "Requested Refresh");
                update(context, TABTask.STATUS);
            } else if (PUNCH.equals(action)) {
                Log.d(TAG, "Requested Punch");
                update(context, TABTask.PUNCH);
            }

        } else {

            if(AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {

                int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);

                if (appWidgetIds == null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    appWidgetIds = new int[]{appWidgetId};
                }

                if (appWidgetIds != null) {
                    SharedPreferences preferences = context.getSharedPreferences(
                            context.getString(R.string.app_preferences),
                            Context.MODE_PRIVATE);
                    WidgetValidator.finish(preferences, appWidgetIds);
                }

            }

            super.onReceive(context, intent);
        }

    }

    private static PendingIntent getPendingSelfIntent(Context context, Class<?> provider, String action) {
        Intent intent = new Intent(context, provider);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public abstract int getLayout();

}
