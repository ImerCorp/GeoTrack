package fr.upjv.geotrack;

import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
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

    // Map Components
    private MapView mapView;
    private GoogleMap googleMap;
    private View mapLoadingOverlay;
    private View mapNoDataOverlay;
    private LinearLayout mapControls;
    private ImageButton btnCenterMap;
    private ImageButton btnFullscreenMap;

    // Data
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
        initializeMap(savedInstanceState);
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

        mapView = findViewById(R.id.map_view);
        mapLoadingOverlay = findViewById(R.id.map_loading_overlay);
        mapNoDataOverlay = findViewById(R.id.map_no_data_overlay);
        mapControls = findViewById(R.id.map_controls);
        btnCenterMap = findViewById(R.id.btn_center_map);
        btnFullscreenMap = findViewById(R.id.btn_fullscreen_map);

        setupPhotosRecyclerView();
        setupMapControls();
    }

    private void initializeMap(Bundle savedInstanceState) {
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
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

        localisationController.getLocalisationsForJourney(journey)
                .addOnSuccessListener(localisations -> {
                    journeyLocalisations.clear();
                    journeyLocalisations.addAll(localisations);
                    updateLocalisationUI();

                    if (googleMap != null) {
                        updateMapWithLocalisations();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

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
}