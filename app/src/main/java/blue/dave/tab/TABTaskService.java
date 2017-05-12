package blue.dave.tab;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.config.CookieSpecs;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.utils.URIBuilder;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

public class TABTaskService extends IntentService {

    private static final String TAG = TABTaskService.class.getName();
    private static NumberFormat LOCATION_FORMATTER = new DecimalFormat("#0.000000");

    public static AtomicBoolean working = new AtomicBoolean(false);

    public static final String TASK = "TASK";
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";



    public TABTaskService() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        Log.d(TAG, "Starting TAB task");

        TABGroupProviderUpdater updater = new TABGroupProviderUpdater(this);
        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.app_preferences),
                Context.MODE_PRIVATE);

        TABTask task = (TABTask) intent.getSerializableExtra(TASK);
        String username = intent.getStringExtra(USERNAME);
        String password = intent.getStringExtra(PASSWORD);

        RequestConfig config = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BEST_MATCH)
                .setCircularRedirectsAllowed(true)
                .build();

        CloseableHttpClient client = HttpClientBuilder.create()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultRequestConfig(config)
                .build();

        CloseableHttpResponse response = null;

        try {

            //Login. STEP 1: POST TO LOGIN to establish if this is mobile1 or mobile2
            URI uri = new URIBuilder()
                    .setScheme("https")
                    .setHost("mobile.adp.com")
                    .setPath("/public/login")
                    .build();

            //Login URL
            HttpPost httpPost = new HttpPost(uri);

            List<NameValuePair> nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("username", username));
            nameValuePairs.add(new BasicNameValuePair("g", "1"));
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            //Launch LOGIN request
            Log.d(TAG, "Sending LOGIN Inquiry request");
            response = client.execute(httpPost);
            Log.d(TAG, "Received LOGIN Inquiry response, parsing.");

            //Parse the response as a JSOUP document
            Document responseDocument = asDocument(response);

            boolean mobile2 =  responseDocument.select("form").attr("action").contains("mobile2");

            //Apply the appropriate domain to connect to depending on task
            String action;

            if(task.equals(TABTask.PUNCH)) {
                action = mobile2 ? "https://mobile2.adp.com/timeclock" : "https://mobile.adp.com/timeclock";
            } else {
                action = mobile2 ?  "https://mobile2.adp.com/timesheet" : "https://mobile.adp.com/timesheet";
            }

            uri = new URIBuilder()
                    .setScheme("https")
                    .setHost("mobile.adp.com")
                    .setPath("/siteminderagent/forms/login.fcc")
                    .addParameter("USER", username)
                    .addParameter("password", password)
                    .addParameter("TARGET", action)
                    .build();

            //Login URL
            httpPost = new HttpPost(uri);

            //Launch LOGIN request
            Log.d(TAG, "Sending LOGIN request");
            response = client.execute(httpPost);
            Log.d(TAG, "Received LOGIN response, parsing.");

            //Parse the response as a JSOUP document
            responseDocument = asDocument(response);

            //Check for errors
            Element loginMessage = responseDocument.select(".message h2").first();
            if(loginMessage != null) {

                //Potential error!
                String asText = loginMessage.text().toUpperCase();
                String error = null;

                if(asText.contains("INCORRECT")) {
                    error = getString(R.string.error_invalid_credentials);
                } else if (asText.contains("LOCKED")) {
                    error = getString(R.string.error_account_locked);
                } else {
                    Log.e(TAG, "Potential error inbound, message: " + asText);
                }

                //Let user know
                if(error != null) {
                    String title = getString(R.string.error_title);
                    finishTask(updater, title, error, preferences);
                    return;
                }

            }

            if (task.equals(TABTask.DEBUG)) {
                return;
            }

            if (task.equals(TABTask.STATUS)) {

                //Get TIME from the page
                String time = responseDocument
                                .select(".period_amount")
                                .first()
                                .textNodes()
                                .get(0)
                                .getWholeText();

                //Append the time suffix, e.g 12 Hours
                String title = time + getString(R.string.status_hours_suffix);

                //Default to clocked out
                String message = getString(R.string.status_clocked_out);
                boolean punched = false;

                //Gather time sheet entries
                Elements elements = responseDocument.select(".timesheet-line");

                for (Element element : elements) {

                    Element left = element.select(".tl-left a").first();
                    //this is a manual item, ignore.
                    if (left != null && left.text().equalsIgnoreCase("PUN")) {
                        continue;
                    }

                    //There is only one entry on this line, no clock out was achieved
                    punched = element.select(".tl-right a span").size() == 1;
                }

                //Change message to clocked in
                if (punched) {
                    message = getString(R.string.status_clocked_in);
                }

                //Set cached punch status
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(TABBaseProvider.PREF_CACHED_PUNCH, punched);
                editor.apply();

                //Inform user of status
                finishTask(updater, title, message, preferences);

                return;
            }

            //STEP 2: Build punch action. 'CCT' are ADP's auth tokens that change per action
            String cct = responseDocument.select("[name=cct]").val();

            //Location if allowed & location available
            Location location = null;
            if(preferences.getBoolean(TABConfigureCredentialsFragment.PREF_LOCATION, false)) {
                location = getLocation();
            }

            //Target the TIMECLOCK action
            httpPost = new HttpPost(mobile2 ? "https://mobile2.adp.com/timeclock/action" : "https://mobile.adp.com/timeclock/action");

            //Unlike AUTH, instead of parameters content is form data. Use NameValuePairs instead
           nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("cct", cct));
            nameValuePairs.add(new BasicNameValuePair("punchAction", "Punch"));

            if(location == null) {
                //READER is required to be passed even if no location is passed
                nameValuePairs.add(new BasicNameValuePair("reader", ""));
                nameValuePairs.add(new BasicNameValuePair("lat", ""));
                nameValuePairs.add(new BasicNameValuePair("long", ""));
            } else {
                //Reader=HTML tells ADP that we used the browser's navigator element
                nameValuePairs.add(new BasicNameValuePair("reader", "html5"));
                nameValuePairs.add(new BasicNameValuePair("lat", LOCATION_FORMATTER.format(location.getLatitude())));
                nameValuePairs.add(new BasicNameValuePair("long", LOCATION_FORMATTER.format(location.getLongitude())));
            }

            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            //Launch PUNCH request
            Log.d(TAG, "Sending PUNCH request");
            response = client.execute(httpPost);
            Log.d(TAG, "Received PUNCH response, parsing.");

            //JSOUP request
            responseDocument = asDocument(response);

            //STEP 3 (Obtains SM Directive) : Build and send confirm action
            Elements form = responseDocument.select("form[action*=timeclock]");
            cct = form.select("[name=cct]").val();

            //Target the TIMECLOCK action again, however this time against an action
            httpPost = new HttpPost(mobile2 ?  ("https://mobile2.adp.com" + form.attr("action")) : ("https://mobile.adp.com" + form.attr("action")));

            nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("cct", cct));
            nameValuePairs.add(new BasicNameValuePair("clockSubmit", "Confirm"));
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            //Launch CONFIRM request
            Log.d(TAG, "Sending CONFIRM request");
            response = client.execute(httpPost);
            Log.d(TAG, "Received CONFIRM response.");

            //Its important that we anticipate a variety of different available data
            //It's possible any previous request may have failed in an unpredictable manner

            String cachedHours = preferences.getString(TABBaseProvider.PREF_CACHED_HOURS, null);

            boolean cachedPunchAvailable = preferences.contains(TABBaseProvider.PREF_CACHED_PUNCH);
            boolean cachedPunched = preferences.getBoolean(TABBaseProvider.PREF_CACHED_PUNCH, false);

            Log.d(TAG, String.format(
                    "Cached hours: %s, Cached available: %b, Cached punch: %b",
                    cachedHours,
                    cachedPunchAvailable,
                    cachedPunched)
            );

            //Initialize with undetermined status
            String title = getString(R.string.status_undetermined);
            String message = getString(R.string.status_undetermined_subtitle);

            if(cachedPunchAvailable) {

                boolean punch = !cachedPunched;

                //Only cache the punch if a previous punch was available.
                //Cached punch only controls the state post punch

                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(TABBaseProvider.PREF_CACHED_PUNCH, punch);
                editor.apply();

                if(punch) { //Clock in
                    message = getString(R.string.status_clocked_in);
                } else {
                    message = getString(R.string.status_clocked_out);
                }

            }

            if(cachedHours != null) {
                //Restore the state of the hour count, if this is available
                title = cachedHours;
            }

            //Let user know status
            finishTask(updater, title, message, preferences);

        } catch (Exception e) {

            Log.e(TAG, "TABTaskService FATAL error", e);
            finishTask(updater, getString(R.string.error_title), getString(R.string.error_internal_error_message), preferences);

        } finally {

            //close response, if got up to this point
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    Log.e(TAG, "TABTaskService NON-FATAL error", e);
                }
            }

            //close client
            try {
                client.close();
            } catch (Exception e) {
                Log.e(TAG, "TABTaskService NON-FATAL error", e);
            }

            Log.d(TAG, "Finished TAB task");
        }
    }

    private static Document asDocument(CloseableHttpResponse response) throws Exception {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
            content.append(String.format("%n"));
        }
        return Jsoup.parse(content.toString());
    }

    public Location getLocation() {

        Log.d(TAG, "Requesting device location");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //no means of obtaining location
            return null;
        }

        try {

            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            //Establish the best provider (power requirement -> accuracy -> bearing -> speed -> altitude)
            Log.d(TAG, "Establishing best provider");
            String bestProvider = locationManager.getBestProvider(new Criteria(), false);
            Log.d(TAG, String.format("Best Provider: %s", bestProvider));

            //No providers
            if(bestProvider == null) {
                return null;
            }

            //Fetch last known location
            Location location = locationManager.getLastKnownLocation(bestProvider);
            Log.d(TAG, String.format("Location: %s", location));
            return location;

        } catch (Exception e) {

            Log.e(TAG, "TABTaskService.getLocation NON Fatal Error", e);

            return null;
        }


    }

    private void finishTask(TABGroupProviderUpdater updater, String title, String subtitle, SharedPreferences preferences) {

        //Set message if applicable
        TABBaseProvider.setMessage(updater, title, subtitle, preferences);

        //Restore state to actionable
        updater.setViewVisibility(R.id.tab_loader, View.GONE);
        updater.setViewVisibility(R.id.tab_punch, View.VISIBLE);
        updater.persist();

        //Allow requests again
        working.set(false);

    }

}
