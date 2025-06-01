package fr.upjv.geotrack;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.datatypes.ExerciseRoute;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.UUID;

import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.services.LocationService;

public class HomeActivity extends AppCompatActivity {

    // Data bases - connections
    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;

    // Components
    private TextView textView;
    private Button buttonViewTest;

    // Permission request codes
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        DBFireStore = FirebaseFirestore.getInstance();

        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Initialize and set text to TextView
        textView = findViewById(R.id.textView);
        if (currentUser != null) {
            textView.setText("Welcome, " + currentUser.getEmail() + " + " + currentUser.getUid());

            // Check and request location permissions
            checkLocationPermission();
        } else {
            // If not signed in, redirect to AuthActivity
            startActivity(new Intent(HomeActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Init - test button
        this.buttonViewTest = findViewById(R.id.button_id_test);
        this.buttonViewTest.setOnClickListener(
                task -> {
                    // Initialize location client here (when context is available)
                    if (fusedLocationClient == null) {
                        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
                    }

                    // Check for location permissions
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                        Toast.makeText(this, "Location permissions not granted. Requesting permissions...", Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                        return;
                    }

                    Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();

                    // Get last known location
                    fusedLocationClient.getLastLocation()
                            .addOnSuccessListener(location -> {
                                if (location != null) {
                                    Toast.makeText(this, "Location found: " + location.getLatitude() + ", " + location.getLongitude(), Toast.LENGTH_LONG).show();
                                    Log.d("HomeActivity", "Location found: " + location.getLatitude() + ", " + location.getLongitude());

                                    // Create localisation object
                                    Localisation localisation = new Localisation(
                                            UUID.randomUUID().toString(),
                                            currentUser.getUid(),
                                            new Date(),
                                            location.getLatitude(),
                                            location.getLongitude()
                                    );

                                    // Debug: Log the data we're trying to save
                                    Log.d("HomeActivity", "Attempting to save: " + localisation.toJson().toString());

                                    // Check if user is authenticated
                                    if (currentUser == null) {
                                        Toast.makeText(this, "User not authenticated!", Toast.LENGTH_SHORT).show();
                                        Log.e("HomeActivity", "User is null!");
                                        return;
                                    }

                                    Log.d("HomeActivity", "User authenticated: " + currentUser.getUid());

                                    // Check if Firestore is initialized
                                    if (DBFireStore == null) {
                                        Toast.makeText(this, "Firestore not initialized!", Toast.LENGTH_SHORT).show();
                                        Log.e("HomeActivity", "DBFireStore is null!");
                                        return;
                                    }

                                    Log.d("HomeActivity", "Firestore initialized, attempting to save...");

                                    // Save to Firestore with detailed error logging
                                    this.DBFireStore
                                            .collection("localisation")
                                            .document("test")
                                            .set(localisation.toJson())
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(this, "Location saved to Firestore successfully!", Toast.LENGTH_SHORT).show();
                                                Log.d("HomeActivity", "Location saved successfully to Firestore");
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(this, "Failed to save to Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                Log.e("HomeActivity", "Failed to save location to Firestore", e);
                                                Log.e("HomeActivity", "Error details: " + e.getClass().getSimpleName() + " - " + e.getMessage());

                                                // Log specific error types
                                                if (e.getMessage() != null) {
                                                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                                                        Log.e("HomeActivity", "Permission denied - check Firestore security rules");
                                                        Toast.makeText(this, "Permission denied - check Firestore rules", Toast.LENGTH_LONG).show();
                                                    } else if (e.getMessage().contains("UNAVAILABLE")) {
                                                        Log.e("HomeActivity", "Firestore unavailable - check internet connection");
                                                        Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                                                    }
                                                }
                                            });
                                } else {
                                    Toast.makeText(this, "Location is null. Enable GPS and try again.", Toast.LENGTH_LONG).show();
                                    Log.w("HomeActivity", "Location is null - GPS might be disabled");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e("HomeActivity", "Failed to get location", e);
                            });
                }
        );
    }

    private void checkLocationPermission() {
        // Check if we have foreground location permissions
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Request foreground location permissions
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // We have foreground location permissions, now check for background location
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        // Background location permission is only needed on Android 10 (Q) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // Request background location permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                // All permissions granted, start location service
                startLocationService();
            }
        } else {
            // For pre-Android 10, we don't need separate background permission
            startLocationService();
        }
    }

    private void startLocationService() {
        if (!isLocationServiceRunning()) {
            try {
                Intent serviceIntent = new Intent(this, LocationService.class);
                startForegroundService(serviceIntent);
                Toast.makeText(this, "Location service started", Toast.LENGTH_SHORT).show();
                Log.d("HomeActivity", "Location service started successfully");
            } catch (Exception e) {
                Log.e("HomeActivity", "Failed to start location service", e);
                Toast.makeText(this, "Failed to start location service", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Location service already running", Toast.LENGTH_SHORT).show();
            Log.d("HomeActivity", "Location service already running");
        }
    }

    private boolean isLocationServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningServiceInfo service :
                    activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (LocationService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {

                // Foreground location permissions granted, now check background
                checkBackgroundLocationPermission();

            } else {
                // Permissions denied
                Toast.makeText(this,
                        "Location permissions denied. The app needs location access to function properly.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Background location permission granted
                startLocationService();
            } else {
                // Background permission denied
                Toast.makeText(this,
                        "Background location permission denied. Location will only work when the app is open.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Note: We DON'T stop the location service here since we want it to run permanently
    }
}