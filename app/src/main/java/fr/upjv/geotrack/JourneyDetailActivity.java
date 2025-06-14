package fr.upjv.geotrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment; // CHANGEMENT ICI
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.adapters.PhotoSliderAdapter;
import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.controllers.LocalisationController;
import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.User;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.utils.GPXExporter;

public class JourneyDetailActivity extends AppCompatActivity implements PhotoSliderAdapter.OnPhotoClickListener, PhotoSliderAdapter.OnPhotoChangeListener, OnMapReadyCallback {

    private static final String TAG = "JourneyDetailActivity";

    // Intent extras
    public static final String EXTRA_JOURNEY_ID = "journey_id";
    public static final String EXTRA_JOURNEY_NAME = "journey_name";
    public static final String EXTRA_JOURNEY_DESCRIPTION = "journey_description";
    public static final String EXTRA_JOURNEY_START_DATE = "journey_start_date";
    public static final String EXTRA_JOURNEY_END_DATE = "journey_end_date";
    public static final String EXTRA_JOURNEY_USER_UUID = "journey_user_uuid";

    // UI Components
    private ImageButton backButton;
    private ImageView userProfilePicture;
    private TextView userDisplayName;
    private TextView journeyTitle;
    private TextView journeyDescription;
    private TextView journeyDates;
    private TextView journeyStatus;
    private TextView journeyDuration;
    private RecyclerView photosRecyclerView;
    private View photosContainer;
    private TextView noPhotosText;
    private TextView photoCounter;
    private LinearLayout photoIndicators;
    private TextView localisationCount;
    private TextView localisationRange;

    // Map Components - CHANGEMENT ICI
    private SupportMapFragment mapFragment;
    private GoogleMap googleMap;
    private View mapLoadingOverlay;
    private View mapNoDataOverlay;
    private LinearLayout mapControls;
    private ImageButton btnCenterMap;
    private ImageButton btnFullscreenMap;
    private ImageButton btnExportJourney;
    private ImageButton btnShareJourney;

    // Data
    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 1001;
    private Journey journey;
    private UserController userController;
    private LocalisationController localisationController;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private PhotoSliderAdapter photoSliderAdapter;
    private List<String> photoUrls;
    private List<Localisation> journeyLocalisations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey_detail);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        userController = new UserController(TAG, this);
        localisationController = new LocalisationController();
        photoUrls = new ArrayList<>();
        journeyLocalisations = new ArrayList<>();

        initializeViews();
        initializeMap(); // CHANGEMENT ICI - Suppression du paramètre savedInstanceState
        loadJourneyFromIntent();
        setupUI();

        if (journey != null && journey.getUserUUID() != null) {
            loadUserInformation();
            loadJourneyPhotos();
            loadJourneyLocalisations();
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        userProfilePicture = findViewById(R.id.user_profile_picture);
        userDisplayName = findViewById(R.id.user_display_name);
        journeyTitle = findViewById(R.id.journey_title);
        journeyDescription = findViewById(R.id.journey_description);
        journeyDates = findViewById(R.id.journey_dates);
        journeyStatus = findViewById(R.id.journey_status);
        journeyDuration = findViewById(R.id.journey_duration);
        photosRecyclerView = findViewById(R.id.photos_recycler_view);
        photosContainer = findViewById(R.id.photos_container);
        noPhotosText = findViewById(R.id.no_photos_text);
        photoCounter = findViewById(R.id.photo_counter);
        photoIndicators = findViewById(R.id.photo_indicators);
        localisationCount = findViewById(R.id.localisation_count);
        localisationRange = findViewById(R.id.localisation_range);

        // CHANGEMENT ICI - Suppression des références directes au MapView
        mapLoadingOverlay = findViewById(R.id.map_loading_overlay);
        mapNoDataOverlay = findViewById(R.id.map_no_data_overlay);
        mapControls = findViewById(R.id.map_controls);
        btnCenterMap = findViewById(R.id.btn_center_map);
        btnFullscreenMap = findViewById(R.id.btn_fullscreen_map);

        btnExportJourney = findViewById(R.id.btn_export_journey);
        btnShareJourney = findViewById(R.id.btn_share_journey);

        setupPhotosRecyclerView();
        setupMapControls();
    }

    // CHANGEMENT PRINCIPAL ICI
    private void initializeMap() {
        // Obtenir le fragment de la carte
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
            Log.d(TAG, "Map fragment initialized, requesting map...");
        } else {
            Log.e(TAG, "Map fragment not found! Check your layout file.");
            // Afficher l'overlay d'erreur si le fragment n'est pas trouvé
            if (mapNoDataOverlay != null) {
                mapNoDataOverlay.setVisibility(View.VISIBLE);
            }
            if (mapLoadingOverlay != null) {
                mapLoadingOverlay.setVisibility(View.GONE);
            }
        }
    }

    private void setupMapControls() {
        if (btnCenterMap != null) {
            btnCenterMap.setOnClickListener(v -> centerMapOnRoute());
        }
        if (btnFullscreenMap != null) {
            btnFullscreenMap.setOnClickListener(v -> openFullscreenMap());
        }
    }

    private void centerMapOnRoute() {
        if (googleMap != null && !journeyLocalisations.isEmpty()) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            for (Localisation location : journeyLocalisations) {
                boundsBuilder.include(new LatLng(location.getLatitude(), location.getLongitude()));
            }

            try {
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100;
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (Exception e) {
                Log.e(TAG, "Error centering map", e);
            }
        }
    }

    private void openFullscreenMap() {
        Toast.makeText(this, "Fullscreen map coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        Log.d(TAG, "Map is ready");

        configureMap();

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(49.8941, 2.2956), 10));

        if (!journeyLocalisations.isEmpty()) {
            updateMapWithLocalisations();
        } else {
            updateMapUI();
        }
    }

    private void configureMap() {
        if (googleMap == null) return;

        try {
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMapToolbarEnabled(false);
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

            Log.d(TAG, "Map configured successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring map", e);
        }
    }

    private void updateMapWithLocalisations() {
        if (googleMap == null || journeyLocalisations.isEmpty()) {
            updateMapUI();
            return;
        }

        try {
            googleMap.clear();

            List<Localisation> sortedLocalisations = new ArrayList<>(journeyLocalisations);
            Collections.sort(sortedLocalisations, new Comparator<Localisation>() {
                @Override
                public int compare(Localisation l1, Localisation l2) {
                    return l1.getTimestamp().compareTo(l2.getTimestamp());
                }
            });

            PolylineOptions polylineOptions = new PolylineOptions()
                    .color(Color.parseColor("#6C5CE7"))
                    .width(8f)
                    .geodesic(true);

            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

            for (int i = 0; i < sortedLocalisations.size(); i++) {
                Localisation location = sortedLocalisations.get(i);
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                polylineOptions.add(latLng);
                boundsBuilder.include(latLng);

                if (i == 0) {
                    googleMap.addMarker(new MarkerOptions()
                            .position(latLng)
                            .title("Journey Start")
                            .snippet(formatTimestamp(location.getTimestamp()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                } else if (i == sortedLocalisations.size() - 1) {
                    googleMap.addMarker(new MarkerOptions()
                            .position(latLng)
                            .title("Journey End")
                            .snippet(formatTimestamp(location.getTimestamp()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                }
            }

            googleMap.addPolyline(polylineOptions);

            LatLngBounds bounds = boundsBuilder.build();
            int padding = 100;
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

            Log.d(TAG, "Map updated with " + sortedLocalisations.size() + " locations");

        } catch (Exception e) {
            Log.e(TAG, "Error updating map with locations", e);
        }

        updateMapUI();
    }

    private void updateMapUI() {
        boolean hasLocalisations = !journeyLocalisations.isEmpty();

        if (mapLoadingOverlay != null) {
            mapLoadingOverlay.setVisibility(View.GONE);
        }
        if (mapNoDataOverlay != null) {
            mapNoDataOverlay.setVisibility(hasLocalisations ? View.GONE : View.VISIBLE);
        }
        if (mapControls != null) {
            mapControls.setVisibility(hasLocalisations ? View.VISIBLE : View.GONE);
        }

        Log.d(TAG, "Map UI updated - hasLocalisations: " + hasLocalisations);
    }

    private String formatTimestamp(Date timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        return formatter.format(timestamp);
    }

    private void setupPhotosRecyclerView() {
        if (photosRecyclerView != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            photosRecyclerView.setLayoutManager(layoutManager);

            photoSliderAdapter = new PhotoSliderAdapter(photoUrls, this);
            photoSliderAdapter.setOnPhotoClickListener(this);
            photoSliderAdapter.setOnPhotoChangeListener(this);
            photosRecyclerView.setAdapter(photoSliderAdapter);

            photosRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.left = 8;
                    outRect.right = 8;
                }
            });
        }
    }

    private void loadJourneyFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            showErrorAndFinish("Error loading journey details");
            return;
        }

        String id = intent.getStringExtra(EXTRA_JOURNEY_ID);
        String name = intent.getStringExtra(EXTRA_JOURNEY_NAME);
        String description = intent.getStringExtra(EXTRA_JOURNEY_DESCRIPTION);
        String userUUID = intent.getStringExtra(EXTRA_JOURNEY_USER_UUID);
        long startDateMs = intent.getLongExtra(EXTRA_JOURNEY_START_DATE, 0);
        long endDateMs = intent.getLongExtra(EXTRA_JOURNEY_END_DATE, 0);

        if (id != null && name != null && startDateMs != 0 && endDateMs != 0) {
            Date startDate = new Date(startDateMs);
            Date endDate = new Date(endDateMs);
            journey = new Journey(id, userUUID, startDate, endDate, name, description, null, null);
        } else {
            showErrorAndFinish("Missing required journey data");
        }
    }

    private void loadJourneyLocalisations() {
        if (journey == null) {
            updateLocalisationUI();
            updateMapUI();
            return;
        }

        Log.d(TAG, "Loading localisations for journey: " + journey.getId());

        localisationController.getLocalisationsForJourney(journey)
                .addOnSuccessListener(localisations -> {
                    Log.d(TAG, "Localisations loaded successfully: " + localisations.size());
                    journeyLocalisations.clear();
                    journeyLocalisations.addAll(localisations);
                    updateLocalisationUI();

                    if (googleMap != null) {
                        updateMapWithLocalisations();
                    } else {
                        Log.d(TAG, "Google map not ready yet, localisations will be displayed when map is ready");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load localizations", e);
                    Toast.makeText(this, "Failed to load journey locations", Toast.LENGTH_SHORT).show();
                    updateLocalisationUI();
                    updateMapUI();
                });
    }

    private void updateLocalisationUI() {
        if (localisationCount != null) {
            int count = journeyLocalisations.size();
            String countText = count == 0 ? "No locations recorded" :
                    count == 1 ? "1 location recorded" :
                            count + " locations recorded";
            localisationCount.setText(countText);
        }

        if (localisationRange != null && !journeyLocalisations.isEmpty()) {
            double minLat = Double.MAX_VALUE;
            double maxLat = Double.MIN_VALUE;
            double minLng = Double.MAX_VALUE;
            double maxLng = Double.MIN_VALUE;

            for (Localisation loc : journeyLocalisations) {
                minLat = Math.min(minLat, loc.getLatitude());
                maxLat = Math.max(maxLat, loc.getLatitude());
                minLng = Math.min(minLng, loc.getLongitude());
                maxLng = Math.max(maxLng, loc.getLongitude());
            }

            double latRange = maxLat - minLat;
            double lngRange = maxLng - minLng;
            double approximateDistance = Math.sqrt(latRange * latRange + lngRange * lngRange) * 111;

            String rangeText = String.format(Locale.getDefault(), "Coverage: %.1f km range", approximateDistance);
            localisationRange.setText(rangeText);
            localisationRange.setVisibility(View.VISIBLE);
        } else if (localisationRange != null) {
            localisationRange.setVisibility(View.GONE);
        }
    }

    public List<Localisation> getJourneyLocalisations() {
        return new ArrayList<>(journeyLocalisations);
    }

    public boolean hasLocalisations() {
        return !journeyLocalisations.isEmpty();
    }

    private void loadJourneyPhotos() {
        if (journey == null || journey.getId() == null) {
            updatePhotosUI();
            return;
        }

        db.collection("journey")
                .whereEqualTo("id", journey.getId())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        QueryDocumentSnapshot document = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                        Object imagePathsObj = document.get("imagePaths");
                        List<String> imagePaths = new ArrayList<>();

                        if (imagePathsObj instanceof List) {
                            try {
                                imagePaths = (List<String>) imagePathsObj;
                            } catch (ClassCastException e) {
                                Log.w(TAG, "imagePaths field is not a List<String>", e);
                            }
                        }

                        if (!imagePaths.isEmpty()) {
                            journey.setImagePaths(imagePaths);
                            convertStoragePathsToUrls(imagePaths);
                        } else {
                            photoUrls.clear();
                            updatePhotosUI();
                        }
                    } else {
                        updatePhotosUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Network error loading photos", Toast.LENGTH_SHORT).show();
                    updatePhotosUI();
                });
    }

    private void convertStoragePathsToUrls(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            updatePhotosUI();
            return;
        }

        photoUrls.clear();
        final int totalPaths = imagePaths.size();
        final int[] completedCount = {0};

        for (String imagePath : imagePaths) {
            if (imagePath == null || imagePath.trim().isEmpty()) {
                completedCount[0]++;
                if (completedCount[0] == totalPaths) {
                    runOnUiThread(this::updatePhotosUI);
                }
                continue;
            }

            String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
            StorageReference imageRef = storage.getReference().child(cleanPath);

            imageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        synchronized (photoUrls) {
                            photoUrls.add(uri.toString());
                        }
                        completedCount[0]++;
                        if (completedCount[0] == totalPaths) {
                            runOnUiThread(this::updatePhotosUI);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        completedCount[0]++;
                        if (completedCount[0] == totalPaths) {
                            runOnUiThread(this::updatePhotosUI);
                        }
                    });
        }
    }

    private void updatePhotosUI() {
        if (photoSliderAdapter != null) {
            photoSliderAdapter.updatePhotos(photoUrls);
        }

        boolean hasPhotos = photoUrls != null && !photoUrls.isEmpty();

        if (photosContainer != null) {
            photosContainer.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        }
        if (findViewById(R.id.no_photos_card) != null) {
            findViewById(R.id.no_photos_card).setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
        }
        if (noPhotosText != null) {
            noPhotosText.setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
        }
        if (photosRecyclerView != null) {
            photosRecyclerView.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        }

        if (hasPhotos) {
            updatePhotoCounter(1, photoUrls.size());
            createPhotoIndicators(photoUrls.size());
        } else {
            if (photoCounter != null) photoCounter.setVisibility(View.GONE);
            if (photoIndicators != null) photoIndicators.setVisibility(View.GONE);
        }
    }

    private void createPhotoIndicators(int count) {
        if (photoIndicators == null || count <= 1) {
            if (photoIndicators != null) photoIndicators.setVisibility(View.GONE);
            return;
        }

        photoIndicators.removeAllViews();

        if (count > 1 && count <= 10) {
            photoIndicators.setVisibility(View.VISIBLE);
            for (int i = 0; i < count; i++) {
                View indicator = createIndicatorDot(i == 0);
                photoIndicators.addView(indicator);
            }
        } else {
            photoIndicators.setVisibility(View.GONE);
        }
    }

    private View createIndicatorDot(boolean isActive) {
        View dot = new View(this);
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);
        dot.setLayoutParams(params);

        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(isActive ? getColor(R.color.colorPrimary) : getColor(R.color.gray_light));
        dot.setBackground(drawable);

        return dot;
    }

    private void updatePhotoCounter(int current, int total) {
        if (photoCounter != null && total > 0) {
            photoCounter.setText(current + " / " + total);
            photoCounter.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateIndicators(int activePosition) {
        if (photoIndicators != null && photoIndicators.getChildCount() > 0) {
            for (int i = 0; i < photoIndicators.getChildCount(); i++) {
                View indicator = photoIndicators.getChildAt(i);
                boolean isActive = i == activePosition;

                android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
                drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                drawable.setColor(isActive ? getColor(R.color.colorPrimary) : getColor(R.color.gray_light));
                indicator.setBackground(drawable);
            }
        }
    }

    @Override
    public void onPhotoChanged(int position, int total) {
        updatePhotoCounter(position + 1, total);
        updateIndicators(position);
    }

    @Override
    public void onPhotoClick(int position, String photoUrl) {
        Intent intent = new Intent(this, FullScreenPhotoActivity.class);
        intent.putStringArrayListExtra("photo_urls", new ArrayList<>(photoUrls));
        intent.putExtra("initial_position", position);
        intent.putExtra("journey_name", journey != null ? journey.getName() : "Journey Photos");
        startActivity(intent);
    }

    private void loadUserInformation() {
        userController.getUser(journey.getUserUUID(), new UserController.UserCallback() {
            @Override
            public void onSuccess(User user) {
                displayUserInformation(user);
            }

            @Override
            public void onFailure(String error) {
                displayDefaultUserInformation();
            }
        });
    }

    private void displayUserInformation(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            userDisplayName.setText(user.getDisplayName());
        } else {
            userDisplayName.setText(user.getEmail());
        }

        if (user.hasProfilePictureUrl()) {
            Glide.with(this)
                    .load(user.getProfilePictureUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_default_profile)
                    .error(R.drawable.ic_default_profile)
                    .into(userProfilePicture);
        } else {
            userProfilePicture.setImageResource(R.drawable.ic_default_profile);
        }

        userProfilePicture.setVisibility(View.VISIBLE);
        userDisplayName.setVisibility(View.VISIBLE);
    }

    private void displayDefaultUserInformation() {
        userDisplayName.setText("Unknown User");
        userProfilePicture.setImageResource(R.drawable.ic_default_profile);
        userProfilePicture.setVisibility(View.VISIBLE);
        userDisplayName.setVisibility(View.VISIBLE);
    }

    private void setupUI() {
        if (journey == null) return;

        backButton.setOnClickListener(v -> finish());
        journeyTitle.setText(journey.getName());

        if (journey.hasDescription()) {
            journeyDescription.setText(journey.getDescription());
            journeyDescription.setVisibility(View.VISIBLE);
        } else {
            journeyDescription.setVisibility(View.GONE);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
        String startDateStr = dateFormat.format(journey.getStart());
        String endDateStr = dateFormat.format(journey.getEnd());
        journeyDates.setText("From " + startDateStr + " to " + endDateStr);

        long durationMs = journey.getEnd().getTime() - journey.getStart().getTime();
        long durationDays = durationMs / (24 * 60 * 60 * 1000);

        if (durationDays == 0) {
            journeyDuration.setText("Same day");
        } else if (durationDays == 1) {
            journeyDuration.setText("1 day");
        } else {
            journeyDuration.setText(durationDays + " days");
        }
        setupExportAndShareButtons();
        setJourneyStatus();
    }

    private void setJourneyStatus() {
        Date now = new Date();

        if (journey.getEnd().before(now)) {
            journeyStatus.setText("COMPLETED");
            journeyStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            journeyStatus.setBackgroundResource(R.drawable.status_completed_background);
        } else if (journey.getStart().before(now) && journey.getEnd().after(now)) {
            journeyStatus.setText("IN PROGRESS");
            journeyStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
            journeyStatus.setBackgroundResource(R.drawable.status_in_progress_background);
        } else {
            journeyStatus.setText("UPCOMING");
            journeyStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
            journeyStatus.setBackgroundResource(R.drawable.gradient_header_background);
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    // SUPPRESSION DES MÉTHODES LIÉES AU MAPVIEW
    // Les méthodes onResume, onPause, onDestroy, onLowMemory, onSaveInstanceState
    // ne sont plus nécessaires car nous utilisons un SupportMapFragment

    public static void startActivity(android.content.Context context, Journey journey) {
        Intent intent = new Intent(context, JourneyDetailActivity.class);
        intent.putExtra(EXTRA_JOURNEY_ID, journey.getId());
        intent.putExtra(EXTRA_JOURNEY_NAME, journey.getName());
        intent.putExtra(EXTRA_JOURNEY_DESCRIPTION, journey.getDescription());
        intent.putExtra(EXTRA_JOURNEY_START_DATE, journey.getStart().getTime());
        intent.putExtra(EXTRA_JOURNEY_END_DATE, journey.getEnd().getTime());
        intent.putExtra(EXTRA_JOURNEY_USER_UUID, journey.getUserUUID());
        context.startActivity(intent);
    }

    private void setupExportAndShareButtons() {
        if (btnExportJourney != null) {
            btnExportJourney.setOnClickListener(v -> showExportDialog());
        }

        if (btnShareJourney != null) {
            btnShareJourney.setOnClickListener(v -> showShareDialog());
        }
    }

    private void showExportDialog() {
        if (journeyLocalisations.isEmpty()) {
            Toast.makeText(this, "No location data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Export Journey")
                .setMessage("Choose the export format for your journey:")
                .setPositiveButton("GPX Format", (dialog, which) -> checkPermissionAndExport("gpx"))
                .setNegativeButton("KML Format", (dialog, which) -> checkPermissionAndExport("kml"))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void checkPermissionAndExport(String format) {
        // For Android 10 (API 29) and above, we don't need WRITE_EXTERNAL_STORAGE permission
        // for writing to the Downloads directory
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            performExport(format);
        } else {
            // For older versions, check permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Store the format for later use
                pendingExportFormat = format;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_STORAGE);
            } else {
                performExport(format);
            }
        }
    }

    // Add this field to store the pending export format
    private String pendingExportFormat;

    private void performExport(String format) {
        if (journey == null || journeyLocalisations.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Exporting Journey")
                .setMessage("Please wait while we export your journey...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        // Perform export in background thread
        new Thread(() -> {
            GPXExporter.ExportResult result;

            if ("gpx".equals(format)) {
                result = GPXExporter.exportToGPX(this, journey, journeyLocalisations);
            } else {
                result = GPXExporter.exportToKML(this, journey, journeyLocalisations);
            }

            // Return to main thread to show result
            runOnUiThread(() -> {
                progressDialog.dismiss();
                showExportResult(result, format.toUpperCase());
            });
        }).start();
    }

    private void showExportResult(GPXExporter.ExportResult result, String format) {
        if (result.success) {
            new AlertDialog.Builder(this)
                    .setTitle("Export Successful")
                    .setMessage("Your journey has been exported as " + format + " format.\n\nSaved to: Downloads/" +
                            new java.io.File(result.filePath).getName())
                    .setPositiveButton("OK", null)
                    .show();

            Toast.makeText(this, format + " file saved to Downloads", Toast.LENGTH_LONG).show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Export Failed")
                    .setMessage("Failed to export journey: " + result.errorMessage)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingExportFormat != null) {
                    performExport(pendingExportFormat);
                    pendingExportFormat = null;
                }
            } else {
                Toast.makeText(this, "Storage permission is required to export files", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showShareDialog() {
        if (journeyLocalisations.isEmpty()) {
            Toast.makeText(this, "No location data to share", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Share Journey")
                .setMessage("Choose the format to share your journey:")
                .setPositiveButton("Share GPX", (dialog, which) -> shareJourney("gpx"))
                .setNegativeButton("Share KML", (dialog, which) -> shareJourney("kml"))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void shareJourney(String format) {
        if (journey == null || journeyLocalisations.isEmpty()) {
            Toast.makeText(this, "No data to share", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Preparing to Share")
                .setMessage("Please wait while we prepare your journey for sharing...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        // Perform export in background thread
        new Thread(() -> {
            GPXExporter.ExportResult result;

            if ("gpx".equals(format)) {
                result = GPXExporter.exportToGPX(this, journey, journeyLocalisations);
            } else {
                result = GPXExporter.exportToKML(this, journey, journeyLocalisations);
            }

            // Return to main thread to share result
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (result.success) {
                    shareFile(result.filePath, format.toUpperCase());
                } else {
                    Toast.makeText(this, "Failed to prepare file for sharing: " + result.errorMessage,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void shareFile(String filePath, String format) {
        try {
            java.io.File file = new java.io.File(filePath);

            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create content URI using FileProvider
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Journey: " + (journey != null ? journey.getName() : "My Journey"));

            String emailBody = createEmailBody(format);
            shareIntent.putExtra(Intent.EXTRA_TEXT, emailBody);

            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Create chooser
            Intent chooser = Intent.createChooser(shareIntent, "Share Journey (" + format + ")");

            // Verify that the intent will resolve to an activity
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "No apps available to share files", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String createEmailBody(String format) {
        StringBuilder body = new StringBuilder();

        if (journey != null) {
            body.append("Hi there!\n\n");
            body.append("I'm sharing my journey '").append(journey.getName()).append("' with you.\n\n");

            if (journey.hasDescription()) {
                body.append("Description: ").append(journey.getDescription()).append("\n\n");
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            body.append("Journey dates: ")
                    .append(dateFormat.format(journey.getStart()))
                    .append(" to ")
                    .append(dateFormat.format(journey.getEnd()))
                    .append("\n\n");
        }

        body.append("Journey details:\n");
        body.append("• ").append(journeyLocalisations.size()).append(" location points recorded\n");

        if (!journeyLocalisations.isEmpty()) {
            double minLat = Double.MAX_VALUE;
            double maxLat = Double.MIN_VALUE;
            double minLng = Double.MAX_VALUE;
            double maxLng = Double.MIN_VALUE;

            for (Localisation loc : journeyLocalisations) {
                minLat = Math.min(minLat, loc.getLatitude());
                maxLat = Math.max(maxLat, loc.getLatitude());
                minLng = Math.min(minLng, loc.getLongitude());
                maxLng = Math.max(maxLng, loc.getLongitude());
            }

            double approximateDistance = Math.sqrt(
                    Math.pow(maxLat - minLat, 2) + Math.pow(maxLng - minLng, 2)
            ) * 111;

            body.append("• Approximate coverage: ").append(String.format(Locale.getDefault(), "%.1f", approximateDistance)).append(" km\n");
        }

        body.append("• File format: ").append(format).append("\n\n");
        body.append("You can open this file with GPS apps, mapping software, or import it into your preferred navigation app.\n\n");
        body.append("Enjoy exploring!\n");
        body.append("Shared from GeoTrack");

        return body.toString();
    }
}