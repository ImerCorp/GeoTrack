package fr.upjv.geotrack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
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

    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "GeoTrackPrefs";
    private static final String KEY_LOCATION_INTERVAL = "location_interval";
    private static final long DEFAULT_INTERVAL = 5000; // 5 seconds

    private LocationService locationService;
    private boolean isServiceBound = false;
    private boolean isDestroyed = false;

    private Slider sliderInterval;
    private TextView tvIntervalDescription;
    private ChipGroup chipGroupIntervals;

    private SharedPreferences preferences;

    // ServiceConnection must be declared before onCreate
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (isDestroyed) {
                Log.d(TAG, "Activity destroyed, ignoring service connection");
                return;
            }

            try {
                LocationService.LocationBinder binder = (LocationService.LocationBinder) service;
                locationService = binder.getService();
                isServiceBound = true;
                Log.d(TAG, "Service connected successfully");

                // Load current interval from service
                if (locationService != null) {
                    long currentInterval = locationService.getLocationUpdateInterval();
                    updateUIWithInterval(currentInterval);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onServiceConnected", e);
                isServiceBound = false;
                locationService = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            locationService = null;
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
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

            // Load saved interval first (in case service binding fails)
            long savedInterval = preferences.getLong(KEY_LOCATION_INTERVAL, DEFAULT_INTERVAL);
            updateUIWithInterval(savedInterval);

            // Try to bind to LocationService
            bindToLocationService();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            finish();
        }
    }

    private void bindToLocationService() {
        if (isDestroyed) {
            return;
        }

        try {
            Intent intent = new Intent(this, LocationService.class);
            boolean bindResult = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Service bind attempt result: " + bindResult);

            if (!bindResult) {
                Log.w(TAG, "Failed to bind to LocationService");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while binding to service", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save settings when pausing (safer than waiting for back press)
        saveCurrentSettings();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        isDestroyed = true;

        // Save settings one more time
        saveCurrentSettings();

        // Unbind service safely
        unbindLocationService();

        super.onDestroy();
    }

    private void unbindLocationService() {
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
                Log.d(TAG, "Service unbound successfully");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service was not bound or already unbound", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during service unbind", e);
            } finally {
                isServiceBound = false;
                locationService = null;
            }
        }
    }

    private void initializeViews() {
        try {
            sliderInterval = findViewById(R.id.slider_interval);
            tvIntervalDescription = findViewById(R.id.tv_interval_description);
            chipGroupIntervals = findViewById(R.id.chip_group_intervals);

            if (sliderInterval == null) {
                throw new RuntimeException("slider_interval not found in layout");
            }
            if (tvIntervalDescription == null) {
                throw new RuntimeException("tv_interval_description not found in layout");
            }

            // Set slider range: 1 second to 3600 seconds (1 hour)
            sliderInterval.setValueFrom(1.0f);
            sliderInterval.setValueTo(3600.0f);
            sliderInterval.setStepSize(1.0f);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e; // Re-throw to handle in onCreate
        }
    }

    private void setupToolbar() {
        try {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar == null) {
                Log.w(TAG, "Toolbar not found, skipping toolbar setup");
                return;
            }

            setSupportActionBar(toolbar);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            toolbar.setNavigationOnClickListener(v -> {
                // Save settings before going back
                saveCurrentSettings();
                finishSafely();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar", e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            // Save settings before going back
            saveCurrentSettings();
            finishSafely();
        } catch (Exception e) {
            Log.e(TAG, "Error in onBackPressed", e);
            super.onBackPressed(); // Fallback to default behavior
        }
    }

    private void finishSafely() {
        try {
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error finishing activity", e);
            // Force finish using system back
            super.onBackPressed();
        }
    }

    private void saveCurrentSettings() {
        if (isDestroyed || preferences == null) {
            return;
        }

        try {
            if (sliderInterval != null) {
                long currentInterval = (long) (sliderInterval.getValue() * 1000);
                preferences.edit()
                        .putLong(KEY_LOCATION_INTERVAL, currentInterval)
                        .apply();
                Log.d(TAG, "Settings saved: interval = " + currentInterval + "ms");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving settings", e);
        }
    }

    private void setupIntervalControls() {
        if (isDestroyed) {
            return;
        }

        try {
            // Setup slider
            if (sliderInterval != null) {
                sliderInterval.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser && !isDestroyed) {
                        long intervalMs = (long) (value * 1000);
                        updateInterval(intervalMs);
                        updateChipSelection(intervalMs);
                        updateIntervalDescription(intervalMs);
                    }
                });
            }

            // Setup chips
            setupChipListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up interval controls", e);
        }
    }

    private void setupChipListeners() {
        try {
            setChipListener(R.id.chip_1sec, 1000);
            setChipListener(R.id.chip_5sec, 5000);
            setChipListener(R.id.chip_10sec, 10000);
            setChipListener(R.id.chip_30sec, 30000);
            setChipListener(R.id.chip_60sec, 60000);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up chip listeners", e);
        }
    }

    private void setChipListener(int chipId, long intervalMs) {
        try {
            Chip chip = findViewById(chipId);
            if (chip != null) {
                chip.setOnClickListener(v -> {
                    if (!isDestroyed) {
                        setIntervalFromChip(intervalMs);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting chip listener for interval: " + intervalMs, e);
        }
    }

    private void setIntervalFromChip(long intervalMs) {
        if (isDestroyed) {
            return;
        }

        try {
            updateInterval(intervalMs);
            if (sliderInterval != null) {
                sliderInterval.setValue(intervalMs / 1000f);
            }
            updateIntervalDescription(intervalMs);
        } catch (Exception e) {
            Log.e(TAG, "Error setting interval from chip", e);
        }
    }

    private void updateInterval(long intervalMs) {
        if (isDestroyed || preferences == null) {
            return;
        }

        try {
            // Save to preferences
            preferences.edit()
                    .putLong(KEY_LOCATION_INTERVAL, intervalMs)
                    .apply();

            // Update service if bound and available
            if (isServiceBound && locationService != null) {
                try {
                    locationService.setLocationUpdateInterval(intervalMs);
                    Log.d(TAG, "Updated service interval to: " + intervalMs + "ms");
                } catch (Exception e) {
                    Log.e(TAG, "Error updating service interval", e);
                }
            } else {
                Log.d(TAG, "Service not bound, interval saved to preferences only: " + intervalMs + "ms");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating interval", e);
        }
    }

    private void updateUIWithInterval(long intervalMs) {
        if (isDestroyed) {
            return;
        }

        try {
            // Update slider (ensure value is within bounds)
            if (sliderInterval != null) {
                float sliderValue = Math.max(1.0f, Math.min(3600.0f, intervalMs / 1000f));
                sliderInterval.setValue(sliderValue);
            }

            // Update chip selection
            updateChipSelection(intervalMs);

            // Update description
            updateIntervalDescription(intervalMs);
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI with interval: " + intervalMs, e);
        }
    }

    private void updateChipSelection(long intervalMs) {
        if (isDestroyed) {
            return;
        }

        try {
            if (chipGroupIntervals != null) {
                chipGroupIntervals.clearCheck();
            }

            Chip targetChip = null;
            switch ((int) intervalMs) {
                case 1000:
                    targetChip = findViewById(R.id.chip_1sec);
                    break;
                case 5000:
                    targetChip = findViewById(R.id.chip_5sec);
                    break;
                case 10000:
                    targetChip = findViewById(R.id.chip_10sec);
                    break;
                case 30000:
                    targetChip = findViewById(R.id.chip_30sec);
                    break;
                case 60000:
                    targetChip = findViewById(R.id.chip_60sec);
                    break;
                default:
                    // No chip selected for custom values
                    break;
            }

            if (targetChip != null) {
                targetChip.setChecked(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating chip selection", e);
        }
    }

    private void updateIntervalDescription(long intervalMs) {
        if (isDestroyed || tvIntervalDescription == null) {
            return;
        }

        try {
            String description;
            if (intervalMs < 1000) {
                description = String.format("Update location every %d milliseconds", intervalMs);
            } else if (intervalMs < 60000) {
                int seconds = (int) (intervalMs / 1000);
                description = String.format("Update location every %d second%s",
                        seconds, seconds == 1 ? "" : "s");
            } else if (intervalMs < 3600000) {
                int minutes = (int) (intervalMs / 60000);
                description = String.format("Update location every %d minute%s",
                        minutes, minutes == 1 ? "" : "s");
            } else {
                int hours = (int) (intervalMs / 3600000);
                int remainingMinutes = (int) ((intervalMs % 3600000) / 60000);
                if (remainingMinutes == 0) {
                    description = String.format("Update location every %d hour%s",
                            hours, hours == 1 ? "" : "s");
                } else {
                    description = String.format("Update location every %d hour%s and %d minute%s",
                            hours, hours == 1 ? "" : "s",
                            remainingMinutes, remainingMinutes == 1 ? "" : "s");
                }
            }

            tvIntervalDescription.setText(description);
        } catch (Exception e) {
            Log.e(TAG, "Error updating interval description", e);
            try {
                if (tvIntervalDescription != null) {
                    tvIntervalDescription.setText("Update location interval");
                }
            } catch (Exception ignored) {
                // Ignore if we can't even set fallback text
            }
        }
    }

    // Public method to get current interval (can be called from other activities)
    public static long getSavedInterval(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getLong(KEY_LOCATION_INTERVAL, DEFAULT_INTERVAL);
        } catch (Exception e) {
            Log.e("SettingsActivity", "Error getting saved interval", e);
            return DEFAULT_INTERVAL;
        }
    }
}