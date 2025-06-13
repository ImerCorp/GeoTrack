package fr.upjv.geotrack.services;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Date;
import java.util.UUID;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.controllers.LocalisationController;

public class LocationService extends Service {
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final String TAG = "LocationService";
    private static final int DEFAULT_INTERVAL_MS = 30 * 60 * 1000; // Default 2 seconds

    // Binder for bound service
    private final IBinder binder = new LocationBinder();

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FusedLocationProviderClient fusedLocationClient;
    private Thread locationThread;
    private boolean isRunning = false;
    private boolean isBound = false;

    // Configurable interval for location updates
    private volatile long locationUpdateInterval = DEFAULT_INTERVAL_MS;

    // Binder class for clients to access the service
    public class LocationBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Start as foreground service with location type
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        initializeService();
        startLocationTracking();

        // Return START_STICKY to restart if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        isBound = true;

        // Initialize service if not already done
        if (!isRunning) {
            // Start as foreground service
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            initializeService();
            startLocationTracking();
        }

        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        isBound = false;

        // Stop the service when unbound (you can modify this behavior)
        // If you want the service to continue running even when unbound,
        // comment out the next line
        stopLocationService();

        return false; // Don't allow rebinding
    }

    private void initializeService() {
        // Initialize Firebase components
        this.mAuth = FirebaseAuth.getInstance();
        this.currentUser = mAuth.getCurrentUser();

        // Initialize location client
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }
    }

    private void startLocationTracking() {
        if (isRunning) return;

        isRunning = true;
        locationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    Log.d(TAG, "Location service alive - interval: " + locationUpdateInterval + "ms");
                    try {
                        saveCurrentLocation();
                        Thread.sleep(locationUpdateInterval); // Use configurable interval
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Location thread interrupted");
                        break;
                    }
                }
                Log.d(TAG, "Location tracking stopped");
            }
        });
        locationThread.start();
    }

    // Public method to stop location tracking (accessible from bound clients)
    public void stopLocationTracking() {
        Log.d(TAG, "Stopping location tracking");
        isRunning = false;
        if (locationThread != null) {
            locationThread.interrupt();
        }
    }

    // Public method to start location tracking (accessible from bound clients)
    public void resumeLocationTracking() {
        Log.d(TAG, "Resuming location tracking");
        if (!isRunning) {
            startLocationTracking();
        }
    }

    // Public method to check if service is tracking
    public boolean isTracking() {
        return isRunning;
    }

    // Public method to get current location status
    public String getLocationStatus() {
        if (isRunning) {
            return "Location tracking is active (interval: " + locationUpdateInterval + "ms)";
        } else {
            return "Location tracking is stopped";
        }
    }

    // NEW METHOD: Update location tracking interval
    public void setLocationUpdateInterval(long intervalMs) {
        if (intervalMs < 1000) { // Minimum 1 second
            Log.w(TAG, "Interval too short, setting to minimum 1000ms");
            intervalMs = 1000;
        }

        long oldInterval = this.locationUpdateInterval;
        this.locationUpdateInterval = intervalMs;

        Log.d(TAG, "Location update interval changed from " + oldInterval + "ms to " + intervalMs + "ms");

        // If tracking is currently active, restart it to apply the new interval
        if (isRunning) {
            Log.d(TAG, "Restarting location tracking with new interval");
            stopLocationTracking();
            // Give a small delay to ensure the thread stops
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startLocationTracking();
        }
    }

    // NEW METHOD: Get current location update interval
    public long getLocationUpdateInterval() {
        return locationUpdateInterval;
    }

    // Method to stop the entire service
    public void stopLocationService() {
        Log.d(TAG, "Stopping location service");
        stopLocationTracking();
        stopForeground(true);
        stopSelf();
    }

    private void saveCurrentLocation() {
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "Location found: " + location.getLatitude() + ", " + location.getLongitude());

                            Localisation localisation = new Localisation(
                                    UUID.randomUUID().toString(),
                                    currentUser.getUid(),
                                    new Date(),
                                    location.getLatitude(),
                                    location.getLongitude()
                            );

                            new LocalisationController().saveLocalisation(localisation, this.TAG);
                            new LocalisationController().updateUserCurrentLocation(localisation, this.TAG);
                        } else {
                            Log.w(TAG, "Location is null - GPS might be disabled");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get location", e);
                    });
        } else {
            Log.e(TAG, "Location permissions not granted");
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed");
        // Only restart if not bound (you can modify this behavior)
        if (!isBound) {
            Log.d(TAG, "Task removed - scheduling restart");
            Intent restartIntent = new Intent(getApplicationContext(), LocationService.class);
            PendingIntent restartPendingIntent = PendingIntent.getService(
                    getApplicationContext(), 1, restartIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmService = (AlarmManager) getApplicationContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (alarmService != null) {
                alarmService.set(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + 1000, restartPendingIntent);
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");

        // Stop location tracking
        isRunning = false;
        if (locationThread != null) {
            locationThread.interrupt();
        }

        // Only schedule restart if not bound
        if (!isBound) {
            scheduleRestart();
        }

        super.onDestroy();
    }

    private void scheduleRestart() {
        Intent restartIntent = new Intent(this, LocationServiceRestarter.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 2000, pendingIntent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks your location for GeoTrack app");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoTrack Location Service")
                .setContentText("Tracking your location in background")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }
}