package fr.upjv.geotrack.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class LocationServiceRestarter extends BroadcastReceiver {
    private static final String TAG = "LocationServiceRestarter";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast - restarting LocationService");

        Intent serviceIntent = new Intent(context, LocationService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}