package fr.upjv.geotrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.view.View;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.models.User;
import fr.upjv.geotrack.services.LocationService;
import fr.upjv.geotrack.fragments.home.ThreadFragment;
import fr.upjv.geotrack.fragments.home.ProfileFragment;


public class HomeActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 1000;

    // Firebase
    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;
    FirebaseUser currentUser;

    // Permission request codes
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private FusedLocationProviderClient fusedLocationClient;

    // Location
    private FusedLocationProviderClient fusedClient;

    // UI Components - Updated for new navigation structure
    private LinearLayout tabThread, tabProfile;
    private ImageView btnThread, btnProfile;
    private TextView textThread, textProfile;
    private FragmentManager fragmentManager;
    private boolean isThreadSelected = true;

    // Colors for selected/unselected states
    private int colorSelected;
    private int colorUnselected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // 0) Hide the navigation bar
        hideNavigationBarOnly();

        // 1) Init Firebase Auth & Firestore
        mAuth = FirebaseAuth.getInstance();
        DBFireStore = FirebaseFirestore.getInstance();

        // 2) Initialize colors
        colorSelected = ContextCompat.getColor(this, android.R.color.white);
        colorUnselected = ContextCompat.getColor(this, android.R.color.darker_gray);

        // 3) Vérifier si l'utilisateur est bien connecté
        this.currentUser = mAuth.getCurrentUser();
        new UserController("HomeActivity")
                .saveUser(new User(
                        currentUser.getUid(),
                        currentUser.getEmail(),
                        currentUser.getDisplayName()
                ));
        if (currentUser != null) {
            // Check and request location permissions
            checkLocationPermission();
        } else {
            // Rediriger vers l'écran d'authentification si non connecté
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // 4) Init du client de localisation (FusedLocationProvider)
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // 5) Setup fragments and navigation
        setupFragments(savedInstanceState);
        setupBottomNavigation();
    }

    private void setupFragments(Bundle savedInstanceState) {
        fragmentManager = getSupportFragmentManager();

        // Load the default fragment (ThreadFragment)
        if (savedInstanceState == null) {
            loadFragment(new ThreadFragment());
        }
    }

    private void setupBottomNavigation() {
        // Initialize tab containers
        tabThread = findViewById(R.id.tab_thread);
        tabProfile = findViewById(R.id.tab_profile);

        // Initialize icons and text
        btnThread = findViewById(R.id.btn_thread);
        btnProfile = findViewById(R.id.btn_profile);
        textThread = findViewById(R.id.text_thread);
        textProfile = findViewById(R.id.text_profile);

        // Set initial state
        updateTabStates(true);

        // Set click listeners on tab containers
        tabThread.setOnClickListener(v -> {
            if (!isThreadSelected) {
                switchToThread();
            }
        });

        tabProfile.setOnClickListener(v -> {
            if (isThreadSelected) {
                switchToProfile();
            }
        });
    }

    private void switchToThread() {
        isThreadSelected = true;
        loadFragment(new ThreadFragment());
        updateTabStates(true);
    }

    private void switchToProfile() {
        isThreadSelected = false;
        loadFragment(new ProfileFragment());
        updateTabStates(false);
    }

    private void updateTabStates(boolean threadSelected) {
        if (threadSelected) {
            // Thread tab selected
            btnThread.setColorFilter(colorSelected);
            textThread.setTextColor(colorSelected);

            // Profile tab unselected
            btnProfile.setColorFilter(colorUnselected);
            textProfile.setTextColor(colorUnselected);
        } else {
            // Thread tab unselected
            btnThread.setColorFilter(colorUnselected);
            textThread.setTextColor(colorUnselected);

            // Profile tab selected
            btnProfile.setColorFilter(colorSelected);
            textProfile.setTextColor(colorSelected);
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
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

    private void hideNavigationBarOnly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Note: We DON'T stop the location service here since we want it to run permanently
    }
}