package fr.upjv.geotrack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;

import fr.upjv.geotrack.services.LocationService;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "GeoTrackPrefs";
    private static final String KEY_LOCATION_INTERVAL = "location_interval";
    private static final long DEFAULT_INTERVAL = 5000; // 5 seconds

    private LocationService locationService;
    private boolean isServiceBound = false;

    private Slider sliderInterval;
    private TextView tvIntervalDescription;
    private ChipGroup chipGroupIntervals;

    private SharedPreferences preferences;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocationBinder binder = (LocationService.LocationBinder) service;
            locationService = binder.getService();
            isServiceBound = true;

            // Load current interval from service
            long currentInterval = locationService.getLocationUpdateInterval();
            updateUIWithInterval(currentInterval);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            locationService = null;
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        setupIntervalControls();

        // Bind to LocationService
        Intent intent = new Intent(this, LocationService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Load saved interval if service is not bound yet
        if (!isServiceBound) {
            long savedInterval = preferences.getLong(KEY_LOCATION_INTERVAL, DEFAULT_INTERVAL);
            updateUIWithInterval(savedInterval);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void initializeViews() {
        sliderInterval = findViewById(R.id.slider_interval);
        tvIntervalDescription = findViewById(R.id.tv_interval_description);
        chipGroupIntervals = findViewById(R.id.chip_group_intervals);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupIntervalControls() {
        // Setup slider
        sliderInterval.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                long intervalMs = (long) (value * 1000);
                updateInterval(intervalMs);
                updateChipSelection(intervalMs);
                updateIntervalDescription(intervalMs);
            }
        });

        // Setup chips
        setupChipListeners();
    }

    private void setupChipListeners() {
        findViewById(R.id.chip_1sec).setOnClickListener(v -> setIntervalFromChip(1000));
        findViewById(R.id.chip_5sec).setOnClickListener(v -> setIntervalFromChip(5000));
        findViewById(R.id.chip_10sec).setOnClickListener(v -> setIntervalFromChip(10000));
        findViewById(R.id.chip_30sec).setOnClickListener(v -> setIntervalFromChip(30000));
        findViewById(R.id.chip_60sec).setOnClickListener(v -> setIntervalFromChip(60000));
    }

    private void setIntervalFromChip(long intervalMs) {
        updateInterval(intervalMs);
        sliderInterval.setValue(intervalMs / 1000f);
        updateIntervalDescription(intervalMs);
    }

    private void updateInterval(long intervalMs) {
        // Save to preferences
        preferences.edit()
                .putLong(KEY_LOCATION_INTERVAL, intervalMs)
                .apply();

        // Update service if bound
        if (isServiceBound && locationService != null) {
            locationService.setLocationUpdateInterval(intervalMs);
        }
    }

    private void updateUIWithInterval(long intervalMs) {
        // Update slider
        sliderInterval.setValue(intervalMs / 1000f);

        // Update chip selection
        updateChipSelection(intervalMs);

        // Update description
        updateIntervalDescription(intervalMs);
    }

    private void updateChipSelection(long intervalMs) {
        chipGroupIntervals.clearCheck();

        switch ((int) intervalMs) {
            case 1000:
                ((Chip) findViewById(R.id.chip_1sec)).setChecked(true);
                break;
            case 5000:
                ((Chip) findViewById(R.id.chip_5sec)).setChecked(true);
                break;
            case 10000:
                ((Chip) findViewById(R.id.chip_10sec)).setChecked(true);
                break;
            case 30000:
                ((Chip) findViewById(R.id.chip_30sec)).setChecked(true);
                break;
            case 60000:
                ((Chip) findViewById(R.id.chip_60sec)).setChecked(true);
                break;
        }
    }

    private void updateIntervalDescription(long intervalMs) {
        String description;
        if (intervalMs < 1000) {
            description = String.format("How often to update location (%d milliseconds)", intervalMs);
        } else if (intervalMs < 60000) {
            int seconds = (int) (intervalMs / 1000);
            description = String.format("How often to update location (%d second%s)",
                    seconds, seconds == 1 ? "" : "s");
        } else {
            int minutes = (int) (intervalMs / 60000);
            description = String.format("How often to update location (%d minute%s)",
                    minutes, minutes == 1 ? "" : "s");
        }

        tvIntervalDescription.setText(description);
    }

    // Public method to get current interval (can be called from other activities)
    public static long getSavedInterval(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LOCATION_INTERVAL, DEFAULT_INTERVAL);
    }
}