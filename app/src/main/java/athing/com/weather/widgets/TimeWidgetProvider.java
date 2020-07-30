package athing.com.weather.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import athing.com.weather.AlarmReceiver;
import athing.com.weather.activities.MainActivity;
import athing.com.weather.models.Weather;
import athing.com.weather.BuildConfig;
import athing.com.weather.R;

public class TimeWidgetProvider extends AbstractWidgetProvider {

    private static final String TAG = "TimeWidgetProvider";

    private static final String ACTION_UPDATE_TIME = "lemon.com.weather.UPDATE_TIME";

    private static final long DURATION_MINUTE = TimeUnit.SECONDS.toMillis(30);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.time_widget);

            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.widgetButtonRefresh, pendingIntent);

            Intent intent2 = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent2 = PendingIntent.getActivity(context, 0, intent2, 0);
            remoteViews.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent2);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            Weather widgetWeather = new Weather();
            if(!sp.getString("lastToday", "").equals("")) {
                widgetWeather = parseWidgetJson(sp.getString("lastToday", ""), context);
            }
            else {
                try {
                    pendingIntent2.send();
                } catch (PendingIntent.CanceledException e) {
                    e.printStackTrace();
                }
                return;
            }

            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
            String defaultDateFormat = context.getResources().getStringArray(R.array.dateFormatsValues)[0];
            String dateFormat = sp.getString("dateFormat", defaultDateFormat);
            if ("custom".equals(dateFormat)) {
                dateFormat = sp.getString("dateFormatCustom", defaultDateFormat);
            }
            dateFormat = dateFormat.substring(0, dateFormat.indexOf("-")-1);
            String dateString;
            try {
                SimpleDateFormat resultFormat = new SimpleDateFormat(dateFormat);
                dateString = resultFormat.format(new Date());
            } catch (IllegalArgumentException e) {
                dateString = context.getResources().getString(R.string.error_dateFormat);
            }

            remoteViews.setTextViewText(R.id.time, timeFormat.format(new Date()));
            remoteViews.setTextViewText(R.id.date, dateString);
            remoteViews.setTextViewText(R.id.widgetCity, widgetWeather.getCity() + ", " + widgetWeather.getCountry());
            remoteViews.setTextViewText(R.id.widgetTemperature, widgetWeather.getTemperature());
            remoteViews.setTextViewText(R.id.widgetDescription, widgetWeather.getDescription());
//            remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.clear_icon_day);

            if (widgetWeather.getIcon().toString().equals("01d") ){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.clear_icon_day);
            }else if (widgetWeather.getIcon().equals("01n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.clear_icon_night);

            }else if (widgetWeather.getIcon().toString().equals("02d")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.clouds_icon_day);

            }else if (widgetWeather.getIcon().toString().equals("02n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.clouds_night_icon);

            }else if (widgetWeather.getIcon().toString().equals("03d") || widgetWeather.getIcon().toString().equals("03n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.scatter_clouds);

            }else if (widgetWeather.getIcon().toString().equals("04d")||widgetWeather.getIcon().toString().equals("04n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.broken_clouds);

            }else if (widgetWeather.getIcon().toString().equals("09d")|| widgetWeather.getIcon().toString().equals("09n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.shower_rain);


            }else if (widgetWeather.getIcon().toString().equals("10d")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.rain);


            }else if (widgetWeather.getIcon().toString().equals("10n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.rain_night);


            }else if (widgetWeather.getIcon().toString().equals("11d") || widgetWeather.getIcon().toString().equals("11n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.thunderstorm_icon);


            }else if (widgetWeather.getIcon().toString().equals("13d") || widgetWeather.getIcon().toString().equals("13n")){
                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.snow);


            }else if (widgetWeather.getIcon().toString().equals("50d") || widgetWeather.getIcon().toString().equals("50n")){

                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.mist);


            }
//            else {
//                remoteViews.setImageViewResource(R.id.widgetIcon, R.drawable.broken_clouds);
//
//            }

            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
        scheduleNextUpdate(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_UPDATE_TIME.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName provider = new ComponentName(context.getPackageName(), getClass().getName());
            int ids[] = appWidgetManager.getAppWidgetIds(provider);
            onUpdate(context, appWidgetManager, ids);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        Log.d(TAG, "Disable time widget updates");
        cancelUpdate(context);
    }

    private static void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = new Date().getTime();
        long nextUpdate = now + DURATION_MINUTE - now % DURATION_MINUTE;
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Next widget update: " +
                    android.text.format.DateFormat.getTimeFormat(context).format(new Date(nextUpdate)));
        }
        if (Build.VERSION.SDK_INT >= 19) {
            alarmManager.setExact(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        } else {
            alarmManager.set(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        }
    }

    private static void cancelUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getTimeIntent(context));
    }

    private static PendingIntent getTimeIntent(Context context) {
        Intent intent = new Intent(context, TimeWidgetProvider.class);
        intent.setAction(ACTION_UPDATE_TIME);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

}
