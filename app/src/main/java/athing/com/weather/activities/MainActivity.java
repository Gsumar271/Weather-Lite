package athing.com.weather.activities;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import athing.com.weather.AlarmReceiver;
import athing.com.weather.Constants;
import athing.com.weather.adapters.ViewPagerAdapter;
import athing.com.weather.adapters.WeatherRecyclerAdapter;
import athing.com.weather.fragments.RecyclerViewFragment;
import athing.com.weather.tasks.ParseResult;
import athing.com.weather.tasks.TaskOutput;
import athing.com.weather.utils.UnitConvertor;
import athing.com.weather.widgets.DashClockWeatherExtension;
import athing.com.weather.R;
import athing.com.weather.models.Weather;
import athing.com.weather.tasks.GenericRequestTask;
import athing.com.weather.widgets.AbstractWidgetProvider;

public class MainActivity extends AppCompatActivity implements LocationListener {
    protected static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;

    // Time in milliseconds; only reload weather if last update is longer ago than this value
    private static final int NO_UPDATE_REQUIRED_THRESHOLD = 300000;

    private static Map<String, Integer> speedUnits = new HashMap<>(3);
    private static Map<String, Integer> pressUnits = new HashMap<>(3);
    private static boolean mappingsInitialised = false;

    Typeface weatherFont;
    Weather todayWeather = new Weather();

    TextView todayTemperature;
    TextView todayDescription;
    TextView todayWind;
    TextView todayPressure;
    TextView todayHumidity;
    TextView todaySunrise;
    TextView todaySunset;
    TextView lastUpdate;
    ImageView todayIcon;
    ViewPager viewPager;
    TabLayout tabLayout;
    boolean showAd;

    View appView;

    LocationManager locationManager;
    ProgressDialog progressDialog;

    int theme;
    boolean destroyed = false;

    CoordinatorLayout mainBackground;

    private List<Weather> longTermWeather = new ArrayList<>();
    private List<Weather> longTermTodayWeather = new ArrayList<>();
    private List<Weather> longTermTomorrowWeather = new ArrayList<>();

    public String recentCity = "";
    boolean apprun;
    AppBarLayout appBarLayout;
    EditText input;

    InterstitialAd mInterstitialAd;
    AdRequest adRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize the associated SharedPreferences file with default values
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setTheme(theme = getTheme(prefs.getString("theme", "fresh")));
        boolean darkTheme = theme == R.style.AppTheme_NoActionBar_Dark ||
                theme == R.style.AppTheme_NoActionBar_Classic_Dark;

        // Initiate activity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        showAd = true;
        LocationDialog();
        //intitialize MobileAds
        MobileAds.initialize(this, getString(R.string.admob_appid));

        mInterstitialAd = new InterstitialAd(MainActivity.this);
//        // set the ad unit ID
        mInterstitialAd.setAdUnitId(getString(R.string.admob_idinterst));
        Handler mHandler=  new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adRequest = new AdRequest.Builder().build();
               // adRequest = new AdRequest.Builder().addTestDevice("F4AAD2F439EAD42C27D50B321FCFD141").build();
                // Load ads into Interstitial Ads
                mInterstitialAd.loadAd(adRequest);
                mInterstitialAd.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        super.onAdLoaded();
                        mInterstitialAd.show();

                    }

                    @Override
                    public void onAdFailedToLoad(int errorCode) {
                        // Code to be executed when an ad request fails.
                        super.onAdFailedToLoad(errorCode);
                    }

                });
            }
        }, 5000);
        appBarLayout = (AppBarLayout)findViewById(R.id.appbar);
        if (theme == getTheme("fresh")){
            appBarLayout.setBackgroundColor(getResources().getColor(R.color.colorPrimarytwo));
        }else if (theme == getTheme("classic")){
            appBarLayout.setBackgroundColor(getResources().getColor(R.color.classic_colorPrimaryTrans));
        }else if (theme == getTheme("classicdark")){
            appBarLayout.setBackgroundColor(getResources().getColor(R.color.classic_darkTheme_colorPrimaryTrans));
        }else if (theme == getTheme("dark")){
            appBarLayout.setBackgroundColor(getResources().getColor(R.color.darkTheme_colorPrimaryTrans));
        }
        appView = findViewById(R.id.viewApp);

        progressDialog = new ProgressDialog(MainActivity.this);

        // Load toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (darkTheme) {
            toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay_Dark);
        }

        // Initialize textboxes
        todayTemperature = (TextView) findViewById(R.id.todayTemperature);
        todayDescription = (TextView) findViewById(R.id.todayDescription);
        todayWind = (TextView) findViewById(R.id.todayWind);
        todayPressure = (TextView) findViewById(R.id.todayPressure);
        todayHumidity = (TextView) findViewById(R.id.todayHumidity);
        todaySunrise = (TextView) findViewById(R.id.todaySunrise);
        todaySunset = (TextView) findViewById(R.id.todaySunset);
        lastUpdate = (TextView) findViewById(R.id.lastUpdate);
        todayIcon = (ImageView) findViewById(R.id.todayIcon);
        weatherFont = Typeface.createFromAsset(this.getAssets(), "fonts/weather.ttf");
//        todayIcon.setTypeface(weatherFont);

        mainBackground = (CoordinatorLayout)findViewById(R.id.viewApp);

        // Initialize viewPager
        viewPager = (ViewPager) findViewById(R.id.viewPager);
        tabLayout = (TabLayout) findViewById(R.id.tabs);

        destroyed = false;

        initMappings();

        // Preload data from cache
        preloadWeather();
        updateLastUpdateTime();

        // Set autoupdater
        AlarmReceiver.setRecurringAlarm(this);
    }

    public void LocationDialog(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        apprun = preferences.getBoolean("run", false);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (apprun == false){
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)|| locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                getCityByLocation();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("run", true);
                editor.commit();
            }else{
                AlertDialog.Builder alertdialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Show Weather By")
                        .setCancelable(false)
                        .setMessage("Please Select An Option To Show Weather By")
                        .setPositiveButton("Get By Loaction", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                getCityByLocation();
                            }
                        })
                        .setNegativeButton("Search City", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                searchCities();
                            }
                        });
                alertdialog.create().show();
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("run", true);
            editor.commit();

        }
    }

    public WeatherRecyclerAdapter getAdapter(int id) {
        WeatherRecyclerAdapter weatherRecyclerAdapter;
        if (id == 0) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTodayWeather);
        } else if (id == 1) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTomorrowWeather);
        } else {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermWeather);
        }
        return weatherRecyclerAdapter;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AdRequest adRequest = new AdRequest.Builder().build();
        // Load ads into Interstitial Ads
        mInterstitialAd.loadAd(adRequest);
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                mInterstitialAd.show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mInterstitialAd = new InterstitialAd(MainActivity.this);
//        // set the ad unit ID
        mInterstitialAd.setAdUnitId(getString(R.string.admob_idinterst));

        adRequest = new AdRequest.Builder().build();

        if (getTheme(PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "fresh")) != theme) {
            // Restart activity to apply theme
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
        } else if (shouldUpdate() && isNetworkAvailable()) {
            getTodayWeather();
            getLongTermWeather();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(MainActivity.this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    private void preloadWeather() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        String lastToday = sp.getString("lastToday", "");
        if (!lastToday.isEmpty()) {
            new TodayWeatherTask(this, this, progressDialog).execute("cachedResponse", lastToday);
        }
        String lastLongterm = sp.getString("lastLongterm", "");
        if (!lastLongterm.isEmpty()) {
            new LongTermWeatherTask(this, this, progressDialog).execute("cachedResponse", lastLongterm);
        }
    }

    private void getTodayWeather() {
        new TodayWeatherTask(this, this, progressDialog).execute();
    }

    private void getLongTermWeather() {
        new LongTermWeatherTask(this, this, progressDialog).execute();
//        CreateNotification();
    }

    private void searchCities() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(this.getString(R.string.search_title));
        input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setMaxLines(1);
        input.setSingleLine(true);
        alert.setView(input, 32, 0, 32, 0);
        alert.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String result = input.getText().toString();
                if (!result.isEmpty()) {
                    saveLocation(result);
                }
            }
        });
        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Cancelled
            }
        });
        alert.show();
    }

    private void saveLocation(String result) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        recentCity = preferences.getString("city", Constants.DEFAULT_CITY);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("city", result);
        editor.commit();

        if (!recentCity.equals(result)) {
            // New location, update weather
            getTodayWeather();
            getLongTermWeather();
        }
    }

    private void aboutDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Forecastie");
        final WebView webView = new WebView(this);
        String about = "<p>1.4.1</p>" +
                "<p>A lightweight, opensource weather app.</p>" +
                "<p>Developed by <a href='mailto:t.martykan@gmail.com'>Tomas Martykan</a></p>" +
                "<p>Data provided by <a href='http://openweathermap.org/'>OpenWeatherMap</a>, under the <a href='http://creativecommons.org/licenses/by-sa/2.0/'>Creative Commons license</a>" +
                "<p>Icons are <a href='https://erikflowers.github.io/weather-icons/'>Weather Icons</a>, by <a href='http://www.twitter.com/artill'>Lukas Bischoff</a> and <a href='http://www.twitter.com/Erik_UX'>Erik Flowers</a>, under the <a href='http://scripts.sil.org/OFL'>SIL OFL 1.1</a> licence.";
        TypedArray ta = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary, R.attr.colorAccent});
        String textColor = String.format("#%06X", (0xFFFFFF & ta.getColor(0, Color.BLACK)));
        String accentColor = String.format("#%06X", (0xFFFFFF & ta.getColor(1, Color.BLUE)));
        ta.recycle();
        about = "<style media=\"screen\" type=\"text/css\">" +
                "body {\n" +
                "    color:" + textColor + ";\n" +
                "}\n" +
                "a:link {color:" + accentColor + "}\n" +
                "</style>" +
                about;
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.loadData(about, "text/html", "UTF-8");
        alert.setView(webView, 32, 0, 32, 0);
        alert.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        });
        alert.show();
    }

    private String setWeatherIcon(int actualId, int hourOfDay) {
        int id = actualId / 100;
        String icon = "";
        if (actualId == 800) {
            if (hourOfDay >= 7 && hourOfDay < 20) {
                icon = this.getString(R.string.weather_sunny);
            } else {
                icon = this.getString(R.string.weather_clear_night);
            }
        } else {
            switch (id) {
                case 2:
                    icon = this.getString(R.string.weather_thunder);
                    break;
                case 3:
                    icon = this.getString(R.string.weather_drizzle);
                    break;
                case 7:
                    icon = this.getString(R.string.weather_foggy);
                    break;
                case 8:
                    icon = this.getString(R.string.weather_cloudy);
                    break;
                case 6:
                    icon = this.getString(R.string.weather_snowy);
                    break;
                case 5:
                    icon = this.getString(R.string.weather_rainy);
                    break;
            }
        }
        return icon;
    }

    public static String getRainString(JSONObject rainObj) {
        String rain = "0";
        if (rainObj != null) {
            rain = rainObj.optString("3h", "fail");
            if ("fail".equals(rain)) {
                rain = rainObj.optString("1h", "0");
            }
        }
        return rain;
    }

    private ParseResult parseTodayJson(String result) {
        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                return ParseResult.CITY_NOT_FOUND;
            }

            String city = reader.getString("name");
            String country = "";
            JSONObject countryObj = reader.optJSONObject("sys");
            if (countryObj != null) {
                country = countryObj.getString("country");
                todayWeather.setSunrise(countryObj.getString("sunrise"));
                todayWeather.setSunset(countryObj.getString("sunset"));
            }
            todayWeather.setCity(city);
            todayWeather.setCountry(country);

            JSONObject coordinates = reader.getJSONObject("coord");
            if (coordinates != null) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                sp.edit().putFloat("latitude", (float) coordinates.getDouble("lon")).putFloat("longitude", (float) coordinates.getDouble("lat")).commit();
            }

            JSONObject main = reader.getJSONObject("main");

            todayWeather.setTemperature(main.getString("temp"));
            todayWeather.setDescription(reader.getJSONArray("weather").getJSONObject(0).getString("description"));
            JSONObject windObj = reader.getJSONObject("wind");
            todayWeather.setWind(windObj.getString("speed"));
            if (windObj.has("deg")) {
                todayWeather.setWindDirectionDegree(windObj.getDouble("deg"));
            } else {
                Log.e("parseTodayJson", "No wind direction available");
                todayWeather.setWindDirectionDegree(null);
            }
            todayWeather.setPressure(main.getString("pressure"));
            todayWeather.setHumidity(main.getString("humidity"));

            JSONObject rainObj = reader.optJSONObject("rain");
            String rain;
            if (rainObj != null) {
                rain = getRainString(rainObj);
            } else {
                JSONObject snowObj = reader.optJSONObject("snow");
                if (snowObj != null) {
                    rain = getRainString(snowObj);
                } else {
                    rain = "0";
                }
            }
            todayWeather.setRain(rain);

            final String idString = reader.getJSONArray("weather").getJSONObject(0).getString("id");
            todayWeather.setId(idString);
            final String iconString = reader.getJSONArray("weather").getJSONObject(0).getString("icon").toString();
            todayWeather.setIcon(iconString);
//            todayWeather.setIcon(setWeatherIcon(Integer.parseInt(idString), Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastToday", result);
            editor.commit();

        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }

        return ParseResult.OK;
    }

    private void updateTodayWeatherUI() {
        try {
            if (todayWeather.getCountry().isEmpty()) {
                preloadWeather();
                return;
            }
        } catch (Exception e) {
            preloadWeather();
            return;
        }
        String city = todayWeather.getCity();
        String country = todayWeather.getCountry();
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
        getSupportActionBar().setTitle(city + (country.isEmpty() ? "" : ", " + country));

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        // Temperature
        float temperature = UnitConvertor.convertTemperature(Float.parseFloat(todayWeather.getTemperature()), sp);
        if (sp.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature);
        }

        // Rain
        double rain = Double.parseDouble(todayWeather.getRain());
        String rainString = UnitConvertor.getRainString(rain, sp);

        // Wind
        double wind;
        try {
            wind = Double.parseDouble(todayWeather.getWind());
        } catch (Exception e) {
            e.printStackTrace();
            wind = 0;
        }
        wind = UnitConvertor.convertWind(wind, sp);

        // Pressure
        double pressure = UnitConvertor.convertPressure((float) Double.parseDouble(todayWeather.getPressure()), sp);

        todayTemperature.setText(new DecimalFormat("#.#").format(temperature) + " " + sp.getString("unit", "°K"));
        todayDescription.setText(todayWeather.getDescription().substring(0, 1).toUpperCase() +
                todayWeather.getDescription().substring(1) + rainString);
        if (sp.getString("speedUnit", "m/s").equals("bft")) {
            todayWind.setText(getString(R.string.wind) + ": " +
                    UnitConvertor.getBeaufortName((int) wind) +
                    (todayWeather.isWindDirectionAvailable() ? " " + getWindDirectionString(sp, this, todayWeather) : ""));
        } else {
            todayWind.setText(getString(R.string.wind) + ": " + new DecimalFormat("#.0").format(wind) + " " +
                    localize(sp, "speedUnit", "m/s") +
                    (todayWeather.isWindDirectionAvailable() ? " " + getWindDirectionString(sp, this, todayWeather) : ""));
        }
        todayPressure.setText(getString(R.string.pressure) + ": " + new DecimalFormat("#.0").format(pressure) + " " +
                localize(sp, "pressureUnit", "hPa"));
        todayHumidity.setText(getString(R.string.humidity) + ": " + todayWeather.getHumidity() + " %");
        todaySunrise.setText(getString(R.string.sunrise) + ": " + timeFormat.format(todayWeather.getSunrise()));
        todaySunset.setText(getString(R.string.sunset) + ": " + timeFormat.format(todayWeather.getSunset()));

        if (todayWeather.getIcon().equals("01d") ){
            mainBackground.setBackgroundResource(R.drawable.clear_sky);
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.clear_icon_day);
        }else if (todayWeather.getIcon().equals("01n")){
            mainBackground.setBackgroundResource(R.drawable.clear_sky_night);
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.clear_icon_night);

        }else if (todayWeather.getIcon().equals("02d")){
            mainBackground.setBackgroundResource(R.drawable.few_clouds);
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.clouds_icon_day);

        }else if (todayWeather.getIcon().equals("02n")){
            mainBackground.setBackgroundResource(R.drawable.few_clouds_night);
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.clouds_night_icon);

        }else if (todayWeather.getIcon().equals("03d") || todayWeather.getIcon().equals("03n")){

            todayIcon.setImageResource(android.R.color.transparent);
            if (todayWeather.getIcon().equals("03d")){
                mainBackground.setBackgroundResource(R.drawable.scattered_clouds);
                todayIcon.setImageResource(R.drawable.scatter_clouds);
            }else if (todayWeather.getIcon().equals("03n")){
                todayIcon.setImageResource(R.drawable.scatter_clouds);
                mainBackground.setBackgroundResource(R.drawable.scattered_clouds_night);
            }

        }else if (todayWeather.getIcon().equals("04d")||todayWeather.getIcon().equals("04n")){

            todayIcon.setImageResource(android.R.color.transparent);
            if (todayWeather.getIcon().equals("04d")){
                todayIcon.setImageResource(R.drawable.broken_clouds);
                mainBackground.setBackgroundResource(R.drawable.brokennn_clouds);
            }else if (todayWeather.getIcon().equals("04n")){
                todayIcon.setImageResource(R.drawable.broken_clouds);
                mainBackground.setBackgroundResource(R.drawable.scattered_clouds_night);
            }

        }else if (todayWeather.getIcon().equals("09d")|| todayWeather.getIcon().equals("09n")){

            todayIcon.setImageResource(android.R.color.transparent);
            if (todayWeather.getIcon().equals("09d")){
                todayIcon.setImageResource(R.drawable.shower_rain);
                mainBackground.setBackgroundResource(R.drawable.showerr_rain);
            }else if (todayWeather.getIcon().equals("09n")){
                todayIcon.setImageResource(R.drawable.shower_rain);
                mainBackground.setBackgroundResource(R.drawable.shower_rain_night);
            }

        }else if (todayWeather.getIcon().equals("10d")){
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.rain);
            mainBackground.setBackgroundResource(R.drawable.rain_back);

        }else if (todayWeather.getIcon().equals("10n")){
            todayIcon.setImageResource(android.R.color.transparent);
            todayIcon.setImageResource(R.drawable.rain_night);
            mainBackground.setBackgroundResource(R.drawable.rain_night_back);

        }else if (todayWeather.getIcon().equals("11d") || todayWeather.getIcon().equals("11n")){

            todayIcon.setImageResource(R.drawable.thunderstorm_icon);
            mainBackground.setBackgroundResource(R.drawable.thunderstorm);

        }else if (todayWeather.getIcon().equals("13d") || todayWeather.getIcon().equals("13n")){

            todayIcon.setImageResource(android.R.color.transparent);
            if (todayWeather.getIcon().equals("13d")){
                todayIcon.setImageResource(R.drawable.snow);
                mainBackground.setBackgroundResource(R.drawable.snow_min);
            }else if (todayWeather.getIcon().equals("013n")){
                todayIcon.setImageResource(R.drawable.snow);
                mainBackground.setBackgroundResource(R.drawable.snow_night);
            }

        }else if (todayWeather.getIcon().equals("50d") || todayWeather.getIcon().equals("50n")){

            todayIcon.setImageResource(android.R.color.transparent);
            if (todayWeather.getIcon().equals("50d")){
                todayIcon.setImageResource(R.drawable.mist);
                mainBackground.setBackgroundResource(R.drawable.mist_back);
            }else if (todayWeather.getIcon().equals("50n")){
                todayIcon.setImageResource(R.drawable.mist);
                mainBackground.setBackgroundResource(R.drawable.mist_night);
            }

        }else {
            todayIcon.setImageResource(R.drawable.clear_icon_day);

        }
    }

    public ParseResult parseLongTermJson(String result) {
        int i;
        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                if (longTermWeather == null) {
                    longTermWeather = new ArrayList<>();
                    longTermTodayWeather = new ArrayList<>();
                    longTermTomorrowWeather = new ArrayList<>();
                }
                return ParseResult.CITY_NOT_FOUND;
            }

            longTermWeather = new ArrayList<>();
            longTermTodayWeather = new ArrayList<>();
            longTermTomorrowWeather = new ArrayList<>();

            JSONArray list = reader.getJSONArray("list");
            for (i = 0; i < list.length(); i++) {
                Weather weather = new Weather();

                JSONObject listItem = list.getJSONObject(i);
                JSONObject main = listItem.getJSONObject("main");

                weather.setDate(listItem.getString("dt"));
                weather.setTemperature(main.getString("temp"));
                weather.setDescription(listItem.optJSONArray("weather").getJSONObject(0).getString("description"));
                JSONObject windObj = listItem.optJSONObject("wind");
                if (windObj != null) {
                    weather.setWind(windObj.getString("speed"));
                    weather.setWindDirectionDegree(windObj.getDouble("deg"));
                }
                weather.setPressure(main.getString("pressure"));
                weather.setHumidity(main.getString("humidity"));

                JSONObject rainObj = listItem.optJSONObject("rain");
                String rain = "";
                if (rainObj != null) {
                    rain = getRainString(rainObj);
                } else {
                    JSONObject snowObj = listItem.optJSONObject("snow");
                    if (snowObj != null) {
                        rain = getRainString(snowObj);
                    } else {
                        rain = "0";
                    }
                }
                weather.setRain(rain);

                final String idString = listItem.optJSONArray("weather").getJSONObject(0).getString("id");
                weather.setId(idString);
                final String iconString = listItem.optJSONArray("weather").getJSONObject(0).getString("icon").toString();


                final String dateMsString = listItem.getString("dt") + "000";
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(dateMsString));
//                weather.setIcon(setWeatherIcon(Integer.parseInt(idString), cal.get(Calendar.HOUR_OF_DAY)));
                weather.setIcon(iconString);
                Calendar today = Calendar.getInstance();
                if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                    longTermTodayWeather.add(weather);
                } else if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1) {
                    longTermTomorrowWeather.add(weather);
                } else {
                    longTermWeather.add(weather);
                }
            }
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastLongterm", result);
            editor.commit();
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }

        return ParseResult.OK;
    }

    private void updateLongTermWeatherUI() {
        if (destroyed) {
            return;
        }

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        Bundle bundleToday = new Bundle();
        bundleToday.putInt("day", 0);
        RecyclerViewFragment recyclerViewFragmentToday = new RecyclerViewFragment();
        recyclerViewFragmentToday.setArguments(bundleToday);
        viewPagerAdapter.addFragment(recyclerViewFragmentToday, getString(R.string.today));

        Bundle bundleTomorrow = new Bundle();
        bundleTomorrow.putInt("day", 1);
        RecyclerViewFragment recyclerViewFragmentTomorrow = new RecyclerViewFragment();
        recyclerViewFragmentTomorrow.setArguments(bundleTomorrow);
        viewPagerAdapter.addFragment(recyclerViewFragmentTomorrow, getString(R.string.tomorrow));

        Bundle bundle = new Bundle();
        bundle.putInt("day", 2);
        RecyclerViewFragment recyclerViewFragment = new RecyclerViewFragment();
        recyclerViewFragment.setArguments(bundle);
        viewPagerAdapter.addFragment(recyclerViewFragment, getString(R.string.later));

        int currentPage = viewPager.getCurrentItem();

        viewPagerAdapter.notifyDataSetChanged();
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

        if (currentPage == 0 && longTermTodayWeather.isEmpty()) {
            currentPage = 1;
        }


            viewPager.setCurrentItem(currentPage, false);

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean shouldUpdate() {
        String unit = PreferenceManager.getDefaultSharedPreferences(this).getString("unit", "°K");
        long lastUpdate = PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1);
        boolean cityChanged = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("cityChanged", false);
        boolean unitChanged = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("unitChanged", false);


        // Update if never checked or last update is longer ago than specified threshold
        return unitChanged || cityChanged || lastUpdate < 0 || (Calendar.getInstance().getTimeInMillis() - lastUpdate) > NO_UPDATE_REQUIRED_THRESHOLD;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            if (isNetworkAvailable()) {
                getTodayWeather();
                getLongTermWeather();
            } else {
                Snackbar.make(appView, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
            }
            return true;
        }
        if (id == R.id.action_map) {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_graphs) {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_search) {
            searchCities();

            return true;
        }
        if (id == R.id.action_location) {
            getCityByLocation();
            return true;
        }
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_about) {
            Intent rate = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=lemon.com.weather"));
            startActivity(rate);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void initMappings() {
        if (mappingsInitialised)
            return;
        mappingsInitialised = true;
        speedUnits.put("m/s", R.string.speed_unit_mps);
        speedUnits.put("kph", R.string.speed_unit_kph);
        speedUnits.put("mph", R.string.speed_unit_mph);
        speedUnits.put("kn", R.string.speed_unit_kn);

        pressUnits.put("hPa", R.string.pressure_unit_hpa);
        pressUnits.put("kPa", R.string.pressure_unit_kpa);
        pressUnits.put("mm Hg", R.string.pressure_unit_mmhg);
    }

    private String localize(SharedPreferences sp, String preferenceKey, String defaultValueKey) {
        return localize(sp, this, preferenceKey, defaultValueKey);
    }

    public static String localize(SharedPreferences sp, Context context, String preferenceKey, String defaultValueKey) {
        String preferenceValue = sp.getString(preferenceKey, defaultValueKey);
        String result = preferenceValue;
        if ("speedUnit".equals(preferenceKey)) {
            if (speedUnits.containsKey(preferenceValue)) {
                result = context.getString(speedUnits.get(preferenceValue));
            }
        } else if ("pressureUnit".equals(preferenceKey)) {
            if (pressUnits.containsKey(preferenceValue)) {
                result = context.getString(pressUnits.get(preferenceValue));
            }
        }
        return result;
    }

    public static String getWindDirectionString(SharedPreferences sp, Context context, Weather weather) {
        try {
            if (Double.parseDouble(weather.getWind()) != 0) {
                String pref = sp.getString("windDirectionFormat", null);
                if ("arrow".equals(pref)) {
                    return weather.getWindDirection(8).getArrow(context);
                } else if ("abbr".equals(pref)) {
                    return weather.getWindDirection().getLocalizedString(context);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    void getCityByLocation() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Explanation not needed, since user requests this themmself

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_ACCESS_FINE_LOCATION);
            }

        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getString(R.string.getting_location));
            progressDialog.setCancelable(false);
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        locationManager.removeUpdates(MainActivity.this);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }
            });
            progressDialog.show();
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            }
        } else {
            showLocationSettingsDialog();
        }
    }

    private void showLocationSettingsDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.location_settings);
        alertDialog.setMessage(R.string.location_settings_message);
        alertDialog.setPositiveButton(R.string.location_settings_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        alertDialog.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        alertDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCityByLocation();
                }
                return;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        progressDialog.hide();
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            Log.e("LocationManager", "Error while trying to stop listening for location updates. This is probably a permissions issue", e);
        }
        Log.i("LOCATION (" + location.getProvider().toUpperCase() + ")", location.getLatitude() + ", " + location.getLongitude());
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        new ProvideCityNameTask(this, this, progressDialog).execute("coords", Double.toString(latitude), Double.toString(longitude));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }


    class TodayWeatherTask extends GenericRequestTask {
        public TodayWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPreExecute() {
            loading = 0;
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(TaskOutput output) {
            super.onPostExecute(output);
            // Update widgets
            AbstractWidgetProvider.updateWidgets(MainActivity.this);
            DashClockWeatherExtension.updateDashClock(MainActivity.this);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseTodayJson(response);
        }

        @Override
        protected String getAPIName() {
            return "weather";
        }

        @Override
        protected void updateMainUI() {
            updateTodayWeatherUI();
            updateLastUpdateTime();
        }
    }

    class LongTermWeatherTask extends GenericRequestTask {
        public LongTermWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseLongTermJson(response);
        }

        @Override
        protected String getAPIName() {
            return "forecast";
        }

        @Override
        protected void updateMainUI() {
            updateLongTermWeatherUI();
        }
    }

    class ProvideCityNameTask extends GenericRequestTask {

        public ProvideCityNameTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPreExecute() { /*Nothing*/ }

        @Override
        protected String getAPIName() {
            return "weather";
        }

        @Override
        protected ParseResult parseResponse(String response) {
            Log.i("RESULT", response.toString());
            try {
                JSONObject reader = new JSONObject(response);

                final String code = reader.optString("cod");
                if ("404".equals(code)) {
                    Log.e("Geolocation", "No city found");
                    return ParseResult.CITY_NOT_FOUND;
                }

                String city = reader.getString("name");
                String country = "";
                JSONObject countryObj = reader.optJSONObject("sys");
                if (countryObj != null) {
                    country = ", " + countryObj.getString("country");
                }

                saveLocation(city + country);

            } catch (JSONException e) {
                Log.e("JSONException Data", response);
                e.printStackTrace();
                return ParseResult.JSON_EXCEPTION;
            }

            return ParseResult.OK;
        }

        @Override
        protected void onPostExecute(TaskOutput output) {
            /* Handle possible errors only */
            handleTaskOutput(output);
        }
    }

    public static long saveLastUpdateTime(SharedPreferences sp) {
        Calendar now = Calendar.getInstance();
        sp.edit().putLong("lastUpdate", now.getTimeInMillis()).apply();
        return now.getTimeInMillis();
    }

    private void updateLastUpdateTime() {
        updateLastUpdateTime(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1)
        );
    }

    private void updateLastUpdateTime(long timeInMillis) {
        if (timeInMillis < 0) {
            // No time
            lastUpdate.setText("");
        } else {
            lastUpdate.setText(getString(R.string.last_update, formatTimeWithDayIfNotToday(this, timeInMillis)));
        }
    }

    public static String formatTimeWithDayIfNotToday(Context context, long timeInMillis) {
        Calendar now = Calendar.getInstance();
        Calendar lastCheckedCal = new GregorianCalendar();
        lastCheckedCal.setTimeInMillis(timeInMillis);
        Date lastCheckedDate = new Date(timeInMillis);
        String timeFormat = android.text.format.DateFormat.getTimeFormat(context).format(lastCheckedDate);
        if (now.get(Calendar.YEAR) == lastCheckedCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == lastCheckedCal.get(Calendar.DAY_OF_YEAR)) {
            // Same day, only show time
            return timeFormat;
        } else {
            return android.text.format.DateFormat.getDateFormat(context).format(lastCheckedDate) + " " + timeFormat;
        }
    }

    private int getTheme(String themePref) {
        switch (themePref) {
            case "dark":
                return R.style.AppTheme_NoActionBar_Dark;
            case "classic":
                return R.style.AppTheme_NoActionBar_Classic;
            case "classicdark":
                return R.style.AppTheme_NoActionBar_Classic_Dark;
            default:
                return R.style.AppTheme_NoActionBar;
        }
    }
    public void CreateNotification(){
        long when = System.currentTimeMillis();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this);

        NotificationManager mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        notification.setOngoing(true);
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_layout);
        if (todayWeather.getIcon().toString().equals("01d") ){
            contentView.setImageViewResource(R.id.image, R.drawable.clear_icon_day);
        }else if (todayWeather.getIcon().equals("01n")){
            contentView.setImageViewResource(R.id.image, R.drawable.clear_icon_night);

        }else if (todayWeather.getIcon().toString().equals("02d")){
            contentView.setImageViewResource(R.id.image, R.drawable.clouds_icon_day);

        }else if (todayWeather.getIcon().toString().equals("02n")){
            contentView.setImageViewResource(R.id.image, R.drawable.clouds_night_icon);

        }else if (todayWeather.getIcon().toString().equals("03d") || todayWeather.getIcon().toString().equals("03n")){
            contentView.setImageViewResource(R.id.image, R.drawable.scatter_clouds);

        }else if (todayWeather.getIcon().toString().equals("04d")||todayWeather.getIcon().toString().equals("04n")){
            contentView.setImageViewResource(R.id.image, R.drawable.broken_clouds);

        }else if (todayWeather.getIcon().toString().equals("09d")|| todayWeather.getIcon().toString().equals("09n")){
            contentView.setImageViewResource(R.id.image, R.drawable.shower_rain);


        }else if (todayWeather.getIcon().toString().equals("10d")){
            contentView.setImageViewResource(R.id.image, R.drawable.rain);


        }else if (todayWeather.getIcon().toString().equals("10n")){
            contentView.setImageViewResource(R.id.image, R.drawable.rain_night);


        }else if (todayWeather.getIcon().toString().equals("11d") || todayWeather.getIcon().toString().equals("11n")){
            contentView.setImageViewResource(R.id.image, R.drawable.thunderstorm_icon);


        }else if (todayWeather.getIcon().toString().equals("13d") || todayWeather.getIcon().toString().equals("13n")){
            contentView.setImageViewResource(R.id.image, R.drawable.snow);


        }else if (todayWeather.getIcon().toString().equals("50d") || todayWeather.getIcon().toString().equals("50n")){

            contentView.setImageViewResource(R.id.image, R.drawable.mist);

        }
        contentView.setTextViewText(R.id.title, todayWeather.getDescription());
        contentView.setTextViewText(R.id.text, todayWeather.getTemperature());
        notification.setContent(contentView);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setContentIntent(contentIntent);


        mNotificationManager.notify(1, notification.build());
    }
}
