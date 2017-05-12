package blue.dave.tab;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import blue.dave.tab.util.WidgetValidator;

public class TABConfigureCredentialsFragment extends Fragment {

    private static final String TAG = TABConfigureCredentialsFragment.class.getName();

    public static final String PREF_USERNAME = "USERNAME";
    public static final String PREF_PASSWORD = "PASSWORD";
    public static final String PREF_LOCATION = "USE_LOCATION";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.tab_credentials, container, false);

        Button continueButton = (Button) rootView.findViewById(R.id.configure_continue);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCredentials();
            }
        });

        if (savedInstanceState == null) {
            getCredentials(rootView);
        }

        return rootView;
    }

    private void setCredentials() {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.app_preferences),
                Context.MODE_PRIVATE);

        View view = getView();
        assert view != null;

        EditText usernameField = (EditText) view.findViewById(R.id.configure_username);
        EditText passwordField = (EditText) view.findViewById(R.id.configure_password);
        Switch useLocationField = (Switch)  view.findViewById(R.id.configure_location);

        String username = usernameField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        boolean fail = false;

        //username is missing
        if(username.isEmpty()) {
            usernameField.setError(getActivity().getString(R.string.error_auth_username));
            fail = true;
        }

        //password is missing
        if(password.isEmpty()) {
            passwordField.setError(getActivity().getString(R.string.error_auth_password));
            fail = true;
        }

        //failed to setup
        if(fail) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_USERNAME, username);
        editor.putString(PREF_PASSWORD, password);
        editor.putBoolean(PREF_LOCATION, useLocationField.isChecked());
        editor.apply();

        int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

        Intent activityIntent = getActivity().getIntent();
        Bundle activityExtras = activityIntent.getExtras();

        if (activityExtras != null) {
            widgetId = activityExtras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {

            // Finalize activity
            Intent intent = new Intent();
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            getActivity().setResult(Activity.RESULT_OK, intent);

            if(activityIntent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)) {

                try {

                    // Mark as configured
                    WidgetValidator.onConfigured(preferences, widgetId);

                    // Determine provider. We can assume this exists because this is a configure action
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getActivity().getApplicationContext());
                    AppWidgetProviderInfo appWidgetProviderInfo = appWidgetManager.getAppWidgetInfo(widgetId);
                    Class<?> provider = Class.forName(appWidgetProviderInfo.provider.getClassName());
                    TABBaseProvider baseProvider = (TABBaseProvider) provider.newInstance();

                    // Fire init
                    TABBaseProvider.init(getContext(), provider, baseProvider.getLayout(), widgetId);

                } catch(Exception e) {
                    Log.e(TAG, "Could not update widget after configuration", e);
                }
            }

        } else {
            // This is OK as this activity can be called without an attached widget
            // @see TABConfigureActivity
            getActivity().setResult(Activity.RESULT_OK);

        }
        getActivity().finish();

    }

    private void getCredentials(View view) {

        SharedPreferences preferences = getContext().getSharedPreferences(
                getString(R.string.app_preferences),
                Context.MODE_PRIVATE);

        if(preferences.contains(PREF_USERNAME) && preferences.contains(PREF_PASSWORD)) {

            EditText usernameField = (EditText) view.findViewById(R.id.configure_username);
            EditText passwordField = (EditText) view.findViewById(R.id.configure_password);
            Switch useLocationField = (Switch)  view.findViewById(R.id.configure_location);

            usernameField.setText(preferences.getString(PREF_USERNAME, ""));
            passwordField.setText(preferences.getString(PREF_PASSWORD, ""));
            useLocationField.setChecked(preferences.getBoolean(PREF_LOCATION, false));

        }

    }

}
