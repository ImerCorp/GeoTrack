package fr.upjv.geotrack.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

import fr.upjv.geotrack.HomeActivity;
import fr.upjv.geotrack.R;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "location_service_channel";
    private static final int NOTIFICATION_ID = 123;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Create notification channel for Android O and above
        createNotificationChannel();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Create location request
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 seconds update interval
                .setMinUpdateIntervalMillis(5000) // 5 seconds minimum interval
                .setMaxUpdateDelayMillis(15000) // 15 seconds maximum delay
                .build();

        // Define location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    // Save location to Firestore
                    saveLocationToFirestore(location);
                    Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude());
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as a foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());

        // Request location updates
        requestLocationUpdates();

        // If the service is killed, restart it
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for location tracking service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GeoTrack is active")
                .setContentText("Tracking your location in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
        }
    }

    private void saveLocationToFirestore(Location location) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return;
        }

        String userId = currentUser.getUid();

        // Create a GeoPoint with the location coordinates
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

        // Create a location data map
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("location", geoPoint);
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("accuracy", location.getAccuracy());

        if (location.hasSpeed()) {
            locationData.put("speed", location.getSpeed());
        }

        if (location.hasAltitude()) {
            locationData.put("altitude", location.getAltitude());
        }

        // Save to Firestore
        firestore.collection("users")
                .document(userId)
                .collection("locations")
                .document() // Auto-generated ID
                .set(locationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location saved to Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving location", e));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove location updates when service is destroyed
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }
}