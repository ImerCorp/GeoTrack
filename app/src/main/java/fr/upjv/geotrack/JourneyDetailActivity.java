package fr.upjv.geotrack;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.adapters.PhotoSliderAdapter;
import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.User;

public class JourneyDetailActivity extends AppCompatActivity
        implements PhotoSliderAdapter.OnPhotoClickListener, PhotoSliderAdapter.OnPhotoChangeListener {

    private static final String TAG = "JourneyDetailActivity";
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

    // Enhanced photo navigation components
    private TextView photoCounter;
    private LinearLayout photoIndicators;
    private LinearLayout photoNavigation;
    private ImageButton btnPreviousPhoto;
    private ImageButton btnNextPhoto;
    private TextView photoPositionText;

    // Journey data
    private Journey journey;
    private UserController userController;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private PhotoSliderAdapter photoSliderAdapter;
    private List<String> photoUrls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey_detail);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Initialize controllers
        userController = new UserController(TAG, this);

        // Initialize photo data
        photoUrls = new ArrayList<>();

        // Initialize views
        initializeViews();

        // Get journey data from intent
        loadJourneyFromIntent();

        // Setup UI
        setupUI();

        // Load user information
        if (journey != null && journey.getUserUUID() != null) {
            loadUserInformation();
        }

        // Finalize photo setup and load photos
        finalizePhotoSetup();
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

        // Enhanced photo navigation views
        photoCounter = findViewById(R.id.photo_counter);
        photoIndicators = findViewById(R.id.photo_indicators);
        photoNavigation = findViewById(R.id.photo_navigation);
        btnPreviousPhoto = findViewById(R.id.btn_previous_photo);
        btnNextPhoto = findViewById(R.id.btn_next_photo);
        photoPositionText = findViewById(R.id.photo_position_text);

        // Setup photos RecyclerView with enhanced functionality
        setupPhotosRecyclerView();
        setupPhotoNavigation();
    }

    private void setupPhotosRecyclerView() {
        if (photosRecyclerView != null) {
            // Use LinearLayoutManager with horizontal orientation
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            photosRecyclerView.setLayoutManager(layoutManager);

            // Create adapter with enhanced functionality
            photoSliderAdapter = new PhotoSliderAdapter(photoUrls, this);
            photoSliderAdapter.setOnPhotoClickListener(this);
            photoSliderAdapter.setOnPhotoChangeListener(this); // New callback
            photosRecyclerView.setAdapter(photoSliderAdapter);

            // Add item decoration for spacing
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

    private void setupPhotoNavigation() {
        if (btnPreviousPhoto != null) {
            btnPreviousPhoto.setOnClickListener(v -> {
                if (photoSliderAdapter != null) {
                    photoSliderAdapter.goToPrevious();
                }
            });
        }

        if (btnNextPhoto != null) {
            btnNextPhoto.setOnClickListener(v -> {
                if (photoSliderAdapter != null) {
                    photoSliderAdapter.goToNext();
                }
            });
        }
    }

    private void loadJourneyFromIntent() {
        Intent intent = getIntent();

        if (intent != null) {
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
                Log.d(TAG, "Journey loaded: " + journey.getName());
            } else {
                Log.e(TAG, "Missing required journey data in intent");
                Toast.makeText(this, "Error loading journey details", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Log.e(TAG, "No intent data received");
            Toast.makeText(this, "Error loading journey details", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadJourneyPhotos() {
        if (journey == null || journey.getId() == null) {
            Log.e(TAG, "Journey or journey ID is null - cannot load photos");
            updatePhotosUI();
            return;
        }

        Log.d(TAG, "Loading photos for journey: " + journey.getId());

        // Query Firestore for the specific journey to get its photos
        db.collection("journey")
                .whereEqualTo("id", journey.getId())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();

                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            // Should only be one document since we're querying by ID
                            QueryDocumentSnapshot document = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);

                            try {
                                Log.d(TAG, "Processing journey document: " + document.getId());

                                // Handle image paths - check for both possible field types
                                List<String> imagePaths = null;
                                Object imagePathsObj = document.get("imagePaths");
                                if (imagePathsObj instanceof List) {
                                    try {
                                        imagePaths = (List<String>) imagePathsObj;
                                        Log.d(TAG, "Found " + (imagePaths != null ? imagePaths.size() : 0) + " image paths");
                                    } catch (ClassCastException e) {
                                        Log.w(TAG, "imagePaths field is not a List<String>: " + document.getId(), e);
                                        imagePaths = new ArrayList<>();
                                    }
                                } else {
                                    Log.d(TAG, "No imagePaths field or field is not a List: " + document.getId());
                                    imagePaths = new ArrayList<>();
                                }

                                // Update the journey object with the image paths
                                if (imagePaths != null && !imagePaths.isEmpty()) {
                                    journey.setImagePaths(imagePaths);
                                    Log.d(TAG, "Found " + imagePaths.size() + " image paths, converting to download URLs");

                                    // Convert Firebase Storage paths to download URLs
                                    convertStoragePathsToUrls(imagePaths);
                                } else {
                                    photoUrls.clear();
                                    Log.d(TAG, "No photos found for journey");
                                    updatePhotosUI();
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing journey document: " + document.getId(), e);
                                updatePhotosUI();
                            }
                        } else {
                            Log.w(TAG, "No journey document found with ID: " + journey.getId());
                            updatePhotosUI();
                        }
                    } else {
                        Exception exception = task.getException();
                        Log.w(TAG, "Error getting journey photos", exception);

                        String errorMessage = "Failed to load journey photos";
                        if (exception != null) {
                            String exceptionMessage = exception.getMessage();
                            Log.e(TAG, "Detailed error: " + exceptionMessage);

                            if (exceptionMessage != null) {
                                if (exceptionMessage.contains("PERMISSION_DENIED")) {
                                    errorMessage = "Permission denied accessing photos";
                                } else if (exceptionMessage.contains("UNAUTHENTICATED")) {
                                    errorMessage = "Authentication required";
                                } else if (exceptionMessage.contains("UNAVAILABLE")) {
                                    errorMessage = "Service unavailable";
                                }
                            }
                        }

                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                        updatePhotosUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Network or other failure loading journey photos", e);
                    Toast.makeText(this, "Network error loading photos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updatePhotosUI();
                });
    }

    private void convertStoragePathsToUrls(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            Log.d(TAG, "No image paths to convert");
            updatePhotosUI();
            return;
        }

        photoUrls.clear();
        final int totalPaths = imagePaths.size();
        final int[] completedCount = {0};

        Log.d(TAG, "Converting " + totalPaths + " storage paths to download URLs");

        for (String imagePath : imagePaths) {
            if (imagePath == null || imagePath.trim().isEmpty()) {
                Log.w(TAG, "Skipping null or empty image path");
                completedCount[0]++;
                if (completedCount[0] == totalPaths) {
                    Log.d(TAG, "All paths processed, updating UI with " + photoUrls.size() + " valid URLs");
                    runOnUiThread(this::updatePhotosUI);
                }
                continue;
            }

            // Remove leading slash if present
            String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;

            Log.d(TAG, "Converting path to URL: " + cleanPath);

            StorageReference imageRef = storage.getReference().child(cleanPath);

            imageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        Log.d(TAG, "Successfully got download URL for: " + cleanPath);

                        synchronized (photoUrls) {
                            photoUrls.add(downloadUrl);
                        }

                        completedCount[0]++;
                        if (completedCount[0] == totalPaths) {
                            Log.d(TAG, "All paths processed, updating UI with " + photoUrls.size() + " valid URLs");
                            runOnUiThread(this::updatePhotosUI);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Failed to get download URL for: " + cleanPath, exception);

                        completedCount[0]++;
                        if (completedCount[0] == totalPaths) {
                            Log.d(TAG, "All paths processed, updating UI with " + photoUrls.size() + " valid URLs");
                            runOnUiThread(this::updatePhotosUI);
                        }
                    });
        }
    }

    // Enhanced updatePhotosUI method
    private void updatePhotosUI() {
        if (photoSliderAdapter != null) {
            photoSliderAdapter.updatePhotos(photoUrls);
        }

        boolean hasPhotos = photoUrls != null && !photoUrls.isEmpty();

        // Show/hide main containers
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
            // Update photo counter
            updatePhotoCounter(1, photoUrls.size());

            // Show/hide navigation based on number of photos
            boolean showNavigation = photoUrls.size() > 1;
            if (photoNavigation != null) {
                photoNavigation.setVisibility(showNavigation ? View.VISIBLE : View.GONE);
            }

            // Create indicators
            createPhotoIndicators(photoUrls.size());

            Log.d(TAG, "Showing " + photoUrls.size() + " photos with navigation");
        } else {
            // Hide all photo navigation elements
            if (photoCounter != null) photoCounter.setVisibility(View.GONE);
            if (photoNavigation != null) photoNavigation.setVisibility(View.GONE);
            if (photoIndicators != null) photoIndicators.setVisibility(View.GONE);

            Log.d(TAG, "No photos to display");
        }
    }

    private void createPhotoIndicators(int count) {
        if (photoIndicators == null || count <= 1) {
            if (photoIndicators != null) photoIndicators.setVisibility(View.GONE);
            return;
        }

        photoIndicators.removeAllViews();

        // Only show indicators if there are multiple photos but not too many
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

        // Create drawable for dot
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

        if (photoPositionText != null && total > 0) {
            photoPositionText.setText(current + " of " + total);
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

                // Add subtle animation
                indicator.animate()
                        .scaleX(isActive ? 1.2f : 1.0f)
                        .scaleY(isActive ? 1.2f : 1.0f)
                        .setDuration(200)
                        .start();
            }
        }
    }

    // Implement the new callback interface
    @Override
    public void onPhotoChanged(int position, int total) {
        Log.d(TAG, "Photo changed to position: " + position + " of " + total);

        // Update counter and indicators
        updatePhotoCounter(position + 1, total);
        updateIndicators(position);

        // Update navigation buttons state
        updateNavigationButtons(position, total);
    }

    private void updateNavigationButtons(int position, int total) {
        if (btnPreviousPhoto != null && btnNextPhoto != null) {
            // For circular navigation, buttons are always enabled
            // For linear navigation, disable at ends:
            // btnPreviousPhoto.setEnabled(position > 0);
            // btnNextPhoto.setEnabled(position < total - 1);

            // Using circular navigation - always enabled
            btnPreviousPhoto.setEnabled(total > 1);
            btnNextPhoto.setEnabled(total > 1);

            // Optional: Add visual feedback for disabled state
            float alpha = total > 1 ? 1.0f : 0.5f;
            btnPreviousPhoto.setAlpha(alpha);
            btnNextPhoto.setAlpha(alpha);
        }
    }

    // Enhanced photo click handler
    @Override
    public void onPhotoClick(int position, String photoUrl) {
        Log.d(TAG, "Photo clicked at position: " + position + ", URL: " + photoUrl);

        // Create full-screen photo viewer intent
        Intent intent = new Intent(this, FullScreenPhotoActivity.class);
        intent.putStringArrayListExtra("photo_urls", new ArrayList<>(photoUrls));
        intent.putExtra("initial_position", position);
        intent.putExtra("journey_name", journey != null ? journey.getName() : "Journey Photos");

        // Add shared element transition if desired
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            View sharedView = photosRecyclerView.getLayoutManager().findViewByPosition(position);
            if (sharedView != null) {
                ImageView imageView = sharedView.findViewById(R.id.photo_image);
                if (imageView != null) {
                    android.app.ActivityOptions options = android.app.ActivityOptions
                            .makeSceneTransitionAnimation(this, imageView, "photo_transition");
                    startActivity(intent, options.toBundle());
                    return;
                }
            }
        }

        startActivity(intent);
    }

    // Add gesture detection for additional swipe functionality
    private void setupGestureDetection() {
        if (photosRecyclerView != null) {
            GestureDetector gestureDetector = new GestureDetector(this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            // Handle double tap - could zoom or open full screen
                            int position = photoSliderAdapter.getCurrentPosition();
                            String photoUrl = photoSliderAdapter.getPhotoUrl(position);
                            if (photoUrl != null) {
                                onPhotoClick(position, photoUrl);
                            }
                            return true;
                        }
                    }
            );

            photosRecyclerView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }
    }

    // Call this in your onCreate method after setupUI()
    private void finalizePhotoSetup() {
        setupGestureDetection();

        // Load photos after UI is set up
        if (journey != null) {
            loadJourneyPhotos();
        }
    }

    private void loadUserInformation() {
        Log.d(TAG, "Loading user information for UUID: " + journey.getUserUUID());

        userController.getUser(journey.getUserUUID(), new UserController.UserCallback() {
            @Override
            public void onSuccess(User user) {
                Log.d(TAG, "User information loaded successfully");
                displayUserInformation(user);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to load user information: " + error);
                displayDefaultUserInformation();
            }
        });
    }

    private void displayUserInformation(User user) {
        // Set user display name
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            userDisplayName.setText(user.getDisplayName());
        } else {
            userDisplayName.setText(user.getEmail());
        }

        // Load profile picture
        if (user.hasProfilePictureUrl()) {
            Glide.with(this)
                    .load(user.getProfilePictureUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_default_profile)
                    .error(R.drawable.ic_default_profile)
                    .into(userProfilePicture);
        } else {
            // Show default profile picture with user initials
            userProfilePicture.setImageResource(R.drawable.ic_default_profile);
            // You could also create a text-based avatar with initials here
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
        if (journey == null) {
            return;
        }

        // Setup back button
        backButton.setOnClickListener(v -> finish());

        // Set journey title
        journeyTitle.setText(journey.getName());

        // Set journey description
        if (journey.hasDescription()) {
            journeyDescription.setText(journey.getDescription());
            journeyDescription.setVisibility(View.VISIBLE);
        } else {
            journeyDescription.setVisibility(View.GONE);
        }

        // Format and set dates
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
        String startDateStr = dateFormat.format(journey.getStart());
        String endDateStr = dateFormat.format(journey.getEnd());

        journeyDates.setText("From " + startDateStr + " to " + endDateStr);

        // Calculate and set duration
        long durationMs = journey.getEnd().getTime() - journey.getStart().getTime();
        long durationDays = durationMs / (24 * 60 * 60 * 1000);

        if (durationDays == 0) {
            journeyDuration.setText("Same day");
        } else if (durationDays == 1) {
            journeyDuration.setText("1 day");
        } else {
            journeyDuration.setText(durationDays + " days");
        }

        // Set status
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