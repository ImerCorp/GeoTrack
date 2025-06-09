package fr.upjv.geotrack.fragments.home;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SearchView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import fr.upjv.geotrack.MainActivity;
import fr.upjv.geotrack.JourneyDetailActivity;
import fr.upjv.geotrack.R;
import fr.upjv.geotrack.adapters.JourneyAdapter;
import fr.upjv.geotrack.adapters.ImagePreviewAdapter;
import fr.upjv.geotrack.controllers.JourneyController;
import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.User;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.widget.ProgressBar;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ProfileFragment extends Fragment implements JourneyAdapter.OnJourneyActionListener {

    private static final String TAG = "ProfileFragment";
    private static final int MAX_IMAGES = 10;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    // Controllers
    private JourneyController journeyController;

    // UI Components
    private ImageView profileImage;
    private TextView displayName;
    private TextView emailAddress;
    private TextView memberSince;
    private TextView journeyCount;
    private ImageButton backButton;
    private Button addJourneyButton;
    private RecyclerView journeysRecyclerView;
    private LinearLayout emptyStateLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private ChipGroup filterChipGroup;

    // Journey Management
    private JourneyAdapter journeyAdapter;
    private List<Journey> journeyList;
    private List<Journey> filteredJourneyList;

    // Filter and Search
    private String currentSearchQuery = "";
    private String currentSortFilter = "newest"; // newest, oldest, name

    // Image Upload Components
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private List<Uri> selectedImageUris;
    private ImagePreviewAdapter imagePreviewAdapter;
    private RecyclerView imagePreviewRecyclerView;
    private Button selectImagesButton;
    private TextView selectedImagesCount;
    private Journey currentEditingJourney;
    private Button saveButton;

    private ProgressBar profileImageLoading;
    private ImageView cameraOverlay;
    private ActivityResultLauncher<Intent> profileImagePickerLauncher;
    private FirebaseStorage storage;
    private StorageReference storageRef;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Initialize controllers
        journeyController = new JourneyController();

        // Initialize lists
        journeyList = new ArrayList<>();
        filteredJourneyList = new ArrayList<>();
        selectedImageUris = new ArrayList<>();

        // Setup image picker launcher
        setupImagePickerLauncher();

        initializeFirebase();
        setupImagePickerLauncher();
        setupProfileImagePickerLauncher(); // Add this line
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(view);
        setupBackButton();
        setupProfileImageClick(); // Add this line
        loadUserProfile();
        loadUserProfileImage();
        setupJourneyManagement();
        setupSearchAndFilters();
        loadUserJourneys();

        return view;
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        selectedImageUris.clear();

                        // Handle multiple image selection
                        if (data.getClipData() != null) {
                            int count = Math.min(data.getClipData().getItemCount(), MAX_IMAGES);
                            for (int i = 0; i < count; i++) {
                                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                                selectedImageUris.add(imageUri);
                            }
                        } else if (data.getData() != null) {
                            selectedImageUris.add(data.getData());
                        }

                        updateImagePreview();
                    }
                }
        );
    }

    private void initializeViews(View view) {
        backButton = view.findViewById(R.id.back_button);
        // Profile views
        profileImage = view.findViewById(R.id.profile_image);
        displayName = view.findViewById(R.id.display_name);
        emailAddress = view.findViewById(R.id.email_address);
        memberSince = view.findViewById(R.id.member_since);

        // Optional enhanced views (check if they exist)
        journeyCount = view.findViewById(R.id.journey_count);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        searchView = view.findViewById(R.id.search_view);
        filterChipGroup = view.findViewById(R.id.filter_chip_group);

        // Journey management views
        addJourneyButton = view.findViewById(R.id.add_journey_button);
        journeysRecyclerView = view.findViewById(R.id.journeys_recycler_view);
        emptyStateLayout = view.findViewById(R.id.empty_state_layout);

        backButton = view.findViewById(R.id.back_button);
        profileImage = view.findViewById(R.id.profile_image);
        profileImageLoading = view.findViewById(R.id.profile_image_loading); // Add this line
        cameraOverlay = view.findViewById(R.id.camera_overlay); // Add this line
        displayName = view.findViewById(R.id.display_name);
        emailAddress = view.findViewById(R.id.email_address);
        memberSince = view.findViewById(R.id.member_since);
        journeyCount = view.findViewById(R.id.journey_count);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        searchView = view.findViewById(R.id.search_view);
        filterChipGroup = view.findViewById(R.id.filter_chip_group);
        addJourneyButton = view.findViewById(R.id.add_journey_button);
        journeysRecyclerView = view.findViewById(R.id.journeys_recycler_view);
        emptyStateLayout = view.findViewById(R.id.empty_state_layout);
    }

    private void loadUserProfile() {
        if (currentUser != null) {
            loadBasicUserInfo();
            loadUserStats();
        } else {
            redirectToLogin();
        }
    }

    private void setupProfileImagePickerLauncher() {
        profileImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        handleProfileImageResult(result.getData());
                    }
                }
        );
    }

    private void setupProfileImageClick() {
        View.OnClickListener profileImageClickListener = v -> openProfileImagePicker();

        if (profileImage != null) {
            profileImage.setOnClickListener(profileImageClickListener);
        }

        if (cameraOverlay != null) {
            cameraOverlay.setOnClickListener(profileImageClickListener);
        }
    }

    private void openProfileImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        profileImagePickerLauncher.launch(Intent.createChooser(intent, "Select Profile Picture"));
    }

    private void handleProfileImageResult(Intent data) {
        Uri imageUri = data.getData();
        if (imageUri != null) {
            uploadProfileImage(imageUri);
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        if (currentUser == null) {
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        showProfileImageLoading(true);

        try {
            // Compress and resize the image
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
            Bitmap resizedBitmap = resizeBitmap(bitmap, 400, 400);

            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageData = baos.toByteArray();

            // Create storage reference
            String imagePath = "users/" + currentUser.getUid() + "/profile.jpg";
            StorageReference imageRef = storageRef.child(imagePath);

            // Upload image
            UploadTask uploadTask = imageRef.putBytes(imageData);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                // Get download URL
                imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    updateUserProfilePicture(imagePath, downloadUri.toString());
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting download URL", e);
                    Toast.makeText(getContext(), "Failed to get image URL", Toast.LENGTH_SHORT).show();
                    showProfileImageLoading(false);
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error uploading profile image", e);
                Toast.makeText(getContext(), "Failed to upload image", Toast.LENGTH_SHORT).show();
                showProfileImageLoading(false);
            });

        } catch (IOException e) {
            Log.e(TAG, "Error processing image", e);
            Toast.makeText(getContext(), "Error processing image", Toast.LENGTH_SHORT).show();
            showProfileImageLoading(false);
        }
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap;
        }

        float aspectRatio = (float) width / height;

        if (width > height) {
            width = maxWidth;
            height = Math.round(width / aspectRatio);
        } else {
            height = maxHeight;
            width = Math.round(height * aspectRatio);
        }

        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private void updateUserProfilePicture(String imagePath, String imageUrl) {
        if (currentUser == null) return;

        // Update user document in Firestore
        db.collection("users").document(currentUser.getUid())
                .update("profilePicturePath", imagePath, "profilePictureUrl", imageUrl)
                .addOnSuccessListener(aVoid -> {
                    // Load the new image
                    loadProfileImage(imageUrl);
                    Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                    showProfileImageLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile", e);
                    Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show();
                    showProfileImageLoading(false);
                });
    }

    private void loadProfileImage(String imageUrl) {
        if (getContext() == null || profileImage == null) return;

        if (imageUrl != null && !imageUrl.isEmpty()) {
            RequestOptions requestOptions = new RequestOptions()
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_profile_logo)
                    .error(R.drawable.ic_profile_logo);

            Glide.with(getContext())
                    .load(imageUrl)
                    .apply(requestOptions)
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.ic_profile_logo);
        }
    }

    private void showProfileImageLoading(boolean show) {
        if (profileImageLoading != null) {
            profileImageLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (cameraOverlay != null) {
            cameraOverlay.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (profileImage != null) {
            profileImage.setEnabled(!show);
        }
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        currentUser = mAuth.getCurrentUser();
        journeyController = new JourneyController();
    }

    private void setupBackButton() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                // Check if we have a fragment manager and can go back
                FragmentManager fragmentManager = getParentFragmentManager();

                if (fragmentManager.getBackStackEntryCount() > 0) {
                    // Pop the fragment from back stack
                    fragmentManager.popBackStack();
                } else {
                    // Alternative: Navigate back to ThreadFragment explicitly
                    try {
                        FragmentTransaction transaction = fragmentManager.beginTransaction();
                        ThreadFragment threadFragment = new ThreadFragment();

                        // Replace with ThreadFragment
                        transaction.replace(R.id.fragment_container, threadFragment);
                        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                        transaction.commit();

                    } catch (Exception e) {
                        // Last resort: finish the activity
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    }
                }
            });
        }
    }

    private void loadBasicUserInfo() {
        displayName.setText(currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : "User");

        if (currentUser.getEmail() != null) {
            emailAddress.setText(currentUser.getEmail());
        }

        // Load profile image from Firestore user document
        loadUserProfileImage();

        Date creationDate = new Date(currentUser.getMetadata().getCreationTimestamp());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        memberSince.setText("Member since " + dateFormat.format(creationDate));
    }

    private void loadUserProfileImage() {
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String profilePictureUrl = documentSnapshot.getString("profilePictureUrl");
                        loadProfileImage(profilePictureUrl);
                    } else {
                        // Create user document if it doesn't exist
                        createUserDocument();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user profile image", e);
                    profileImage.setImageResource(R.drawable.ic_profile_logo);
                });
    }

    private void createUserDocument() {
        if (currentUser == null) return;

        User user = new User(currentUser.getUid(), currentUser.getEmail(),
                currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User");

        db.collection("users").document(currentUser.getUid())
                .set(user.toJson())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document created"))
                .addOnFailureListener(e -> Log.e(TAG, "Error creating user document", e));
    }

    private void loadUserStats() {
        if (currentUser == null || journeyCount == null) return;

        String userUUID = currentUser.getUid();

        // Get journey count
        db.collection("journey")
                .whereEqualTo("userUUID", userUUID)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    journeyCount.setText(count + " journeys");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting journey count", e);
                    journeyCount.setText("0 journeys");
                });
    }

    private void setupJourneyManagement() {
        // Setup RecyclerView
        journeyAdapter = new JourneyAdapter(filteredJourneyList, this);
        journeysRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        journeysRecyclerView.setAdapter(journeyAdapter);

        // Setup swipe refresh if available
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::refreshJourneys);
        }

        // Setup add journey button
        addJourneyButton.setOnClickListener(v -> showJourneyDialog(null));
    }

    private void setupSearchAndFilters() {
        // Setup search if available
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    currentSearchQuery = query;
                    filterJourneys();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    currentSearchQuery = newText;
                    filterJourneys();
                    return true;
                }
            });
        }

        // Setup filter chips if available
        if (filterChipGroup != null) {
            setupFilterChips();
        }
    }

    private void setupFilterChips() {
        // Clear existing chips
        filterChipGroup.removeAllViews();

        // Sort chips
        Chip newestChip = new Chip(getContext());
        newestChip.setText("Newest First");
        newestChip.setCheckable(true);
        newestChip.setChecked(true);
        newestChip.setOnClickListener(v -> {
            currentSortFilter = "newest";
            updateChipSelection(newestChip);
            sortAndFilterJourneys();
        });

        Chip oldestChip = new Chip(getContext());
        oldestChip.setText("Oldest First");
        oldestChip.setCheckable(true);
        oldestChip.setOnClickListener(v -> {
            currentSortFilter = "oldest";
            updateChipSelection(oldestChip);
            sortAndFilterJourneys();
        });

        Chip nameChip = new Chip(getContext());
        nameChip.setText("Name A-Z");
        nameChip.setCheckable(true);
        nameChip.setOnClickListener(v -> {
            currentSortFilter = "name";
            updateChipSelection(nameChip);
            sortAndFilterJourneys();
        });

        // Add chips to group
        filterChipGroup.addView(newestChip);
        filterChipGroup.addView(oldestChip);
        filterChipGroup.addView(nameChip);
    }

    private void updateChipSelection(Chip selectedChip) {
        if (filterChipGroup == null) return;

        for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) filterChipGroup.getChildAt(i);
            if (chip != selectedChip && chip.isChecked()) {
                chip.setChecked(false);
            }
        }
    }

    private void loadUserJourneys() {
        if (currentUser == null) {
            Log.e(TAG, "Current user is null - cannot load journeys");
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String userUUID = currentUser.getUid();
        Log.d(TAG, "Loading journeys for user: " + userUUID);

        // Show loading state
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            // You might want to show a progress indicator here
        }

        db.collection("journey")
                .whereEqualTo("userUUID", userUUID)
                .get()
                .addOnCompleteListener(task -> {
                    // Hide refresh indicator
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    if (task.isSuccessful()) {
                        journeyList.clear();
                        QuerySnapshot querySnapshot = task.getResult();

                        if (querySnapshot != null) {
                            Log.d(TAG, "Found " + querySnapshot.size() + " journey documents");

                            for (QueryDocumentSnapshot document : querySnapshot) {
                                try {
                                    Log.d(TAG, "Processing document: " + document.getId());

                                    // Debug: Print all fields in the document
                                    Log.d(TAG, "Document data: " + document.getData().toString());

                                    String id = document.getString("id");
                                    String userUUIDDoc = document.getString("userUUID");
                                    Date start = document.getDate("start");
                                    Date end = document.getDate("end");
                                    String name = document.getString("name");
                                    String description = document.getString("description");

                                    // Validate required fields
                                    if (id == null || id.isEmpty()) {
                                        Log.w(TAG, "Document missing 'id' field: " + document.getId());
                                        continue;
                                    }
                                    if (userUUIDDoc == null || userUUIDDoc.isEmpty()) {
                                        Log.w(TAG, "Document missing 'userUUID' field: " + document.getId());
                                        continue;
                                    }
                                    if (name == null || name.isEmpty()) {
                                        Log.w(TAG, "Document missing 'name' field: " + document.getId());
                                        continue;
                                    }
                                    if (start == null) {
                                        Log.w(TAG, "Document missing 'start' date: " + document.getId());
                                        continue;
                                    }
                                    if (end == null) {
                                        Log.w(TAG, "Document missing 'end' date: " + document.getId());
                                        continue;
                                    }

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

                                    String thumbnailPath = document.getString("thumbnailPath");
                                    Log.d(TAG, "Thumbnail path: " + thumbnailPath);

                                    // Create journey object
                                    Journey journey = new Journey(id, userUUIDDoc, start, end, name, description, imagePaths, thumbnailPath);

                                    // Validate journey before adding
                                    if (journey.isValid()) {
                                        journeyList.add(journey);
                                        Log.d(TAG, "Successfully added journey: " + name);
                                    } else {
                                        Log.w(TAG, "Journey validation failed: " + journey.toString());
                                    }

                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing journey document: " + document.getId(), e);
                                    // Continue processing other documents
                                }
                            }

                            Log.d(TAG, "Successfully loaded " + journeyList.size() + " journeys");

                            // Apply filters and sorting
                            sortAndFilterJourneys();

                            // Update stats
                            loadUserStats();

                        } else {
                            Log.w(TAG, "QuerySnapshot is null");
                            updateEmptyState();
                        }

                    } else {
                        // This is where your error is occurring
                        Exception exception = task.getException();
                        Log.w(TAG, "Error getting journeys", exception);

                        // Provide more specific error messages
                        String errorMessage = "Failed to load journeys";
                        if (exception != null) {
                            String exceptionMessage = exception.getMessage();
                            Log.e(TAG, "Detailed error: " + exceptionMessage);

                            // Check for common Firebase errors
                            if (exceptionMessage != null) {
                                if (exceptionMessage.contains("PERMISSION_DENIED")) {
                                    errorMessage = "Permission denied. Check Firestore security rules.";
                                } else if (exceptionMessage.contains("UNAUTHENTICATED")) {
                                    errorMessage = "User not authenticated. Please log in again.";
                                } else if (exceptionMessage.contains("UNAVAILABLE")) {
                                    errorMessage = "Service unavailable. Check your internet connection.";
                                } else if (exceptionMessage.contains("NOT_FOUND")) {
                                    errorMessage = "Collection not found. Database may not be properly initialized.";
                                } else {
                                    errorMessage = "Error: " + exceptionMessage;
                                }
                            }
                        }

                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                        updateEmptyState();
                    }
                })
                .addOnFailureListener(e -> {
                    // Additional failure handler for network issues
                    Log.e(TAG, "Network or other failure loading journeys", e);
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    Toast.makeText(getContext(), "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateEmptyState();
                });
    }

    private void sortAndFilterJourneys() {
        filteredJourneyList.clear();

        // Start with all journeys
        List<Journey> workingList = new ArrayList<>(journeyList);

        // Apply search filter
        if (!currentSearchQuery.isEmpty()) {
            workingList = searchJourneys(workingList, currentSearchQuery);
        }

        // Apply sorting
        workingList = sortJourneys(workingList, currentSortFilter);

        filteredJourneyList.addAll(workingList);

        // Update UI
        if (journeyAdapter != null) {
            journeyAdapter.updateJourneys(filteredJourneyList);
        }
        updateEmptyState();

        Log.d(TAG, "Filtered journeys: " + filteredJourneyList.size());
    }

    private void filterJourneys() {
        sortAndFilterJourneys();
    }

    private List<Journey> searchJourneys(List<Journey> journeys, String query) {
        String searchQuery = query.toLowerCase().trim();
        List<Journey> filtered = new ArrayList<>();

        for (Journey journey : journeys) {
            if (journey.getName().toLowerCase().contains(searchQuery) ||
                    (journey.getDescription() != null && journey.getDescription().toLowerCase().contains(searchQuery))) {
                filtered.add(journey);
            }
        }

        return filtered;
    }

    private List<Journey> sortJourneys(List<Journey> journeys, String sortType) {
        List<Journey> sorted = new ArrayList<>(journeys);

        switch (sortType) {
            case "oldest":
                Collections.sort(sorted, (j1, j2) -> j1.getStart().compareTo(j2.getStart()));
                break;
            case "name":
                Collections.sort(sorted, (j1, j2) -> j1.getName().compareToIgnoreCase(j2.getName()));
                break;
            case "newest":
            default:
                Collections.sort(sorted, (j1, j2) -> j2.getStart().compareTo(j1.getStart()));
                break;
        }

        return sorted;
    }

    private void refreshJourneys() {
        loadUserJourneys();
    }

    private void updateEmptyState() {
        if (filteredJourneyList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            journeysRecyclerView.setVisibility(View.GONE);

            // Update empty state message based on filters
            TextView emptyMessage = emptyStateLayout.findViewById(R.id.empty_message);
            if (emptyMessage != null) {
                if (!currentSearchQuery.isEmpty()) {
                    emptyMessage.setText("No journeys found for \"" + currentSearchQuery + "\"");
                } else {
                    emptyMessage.setText("No journeys yet");
                }
            }
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            journeysRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showJourneyDialog(Journey existingJourney) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_journey, null);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText nameInput = dialogView.findViewById(R.id.journey_name_input);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.journey_description_input);
        TextInputEditText startDateInput = dialogView.findViewById(R.id.start_date_input);
        TextInputEditText endDateInput = dialogView.findViewById(R.id.end_date_input);

        // Initialize image components
        selectImagesButton = dialogView.findViewById(R.id.select_images_button);
        selectedImagesCount = dialogView.findViewById(R.id.selected_images_count);
        imagePreviewRecyclerView = dialogView.findViewById(R.id.image_preview_recycler);

        // Initialize Calendar objects
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        // Set default end date to 7 days from start date
        endCalendar.add(Calendar.DAY_OF_MONTH, 7);

        // Setup for edit mode
        boolean isEditMode = existingJourney != null;
        currentEditingJourney = existingJourney;

        if (isEditMode) {
            dialogTitle.setText("Edit Journey");
            nameInput.setText(existingJourney.getName());
            descriptionInput.setText(existingJourney.getDescription());

            // Set calendar dates from existing journey
            startCalendar.setTime(existingJourney.getStart());
            endCalendar.setTime(existingJourney.getEnd());

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            startDateInput.setText(dateFormat.format(existingJourney.getStart()));
            endDateInput.setText(dateFormat.format(existingJourney.getEnd()));

            // Load existing images if any
            loadExistingImages(existingJourney);
        } else {
            dialogTitle.setText("Add New Journey");
            // For new journey, set default dates
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            startDateInput.setText(dateFormat.format(startCalendar.getTime()));
            endDateInput.setText(dateFormat.format(endCalendar.getTime()));

            selectedImageUris.clear();
            updateImagePreview();
        }

        // Setup date pickers
        setupDatePickers(startDateInput, endDateInput, startCalendar, endCalendar);

        // Setup image preview RecyclerView
        setupImagePreviewRecyclerView();

        // Setup image selection button
        if (selectImagesButton != null) {
            selectImagesButton.setOnClickListener(v -> {
                if (selectedImageUris.size() >= MAX_IMAGES) {
                    Toast.makeText(getContext(), "Maximum " + MAX_IMAGES + " images allowed", Toast.LENGTH_SHORT).show();
                    return;
                }
                openImagePicker();
            });
        }

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Find buttons from dialog view
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        saveButton = dialogView.findViewById(R.id.save_button);

        // Setup button listeners
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                selectedImageUris.clear();
                dialog.dismiss();
            });
        }

        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                String description = descriptionInput.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter a journey name", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (startCalendar.after(endCalendar)) {
                    Toast.makeText(getContext(), "Start date must be before end date", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Create or update journey with description
                String journeyId = isEditMode ? existingJourney.getId() : UUID.randomUUID().toString();
                Journey journey = new Journey(
                        journeyId,
                        currentUser.getUid(),
                        startCalendar.getTime(),
                        endCalendar.getTime(),
                        name,
                        description
                );

                // Save journey with images
                if (isEditMode) {
                    updateJourneyWithImages(journey, dialog);
                } else {
                    createJourneyWithImages(journey, dialog);
                }
            });
        }

        dialog.show();
    }

    private void setupImagePreviewRecyclerView() {
        if (imagePreviewRecyclerView != null) {
            imagePreviewAdapter = new ImagePreviewAdapter(selectedImageUris, uri -> {
                selectedImageUris.remove(uri);
                updateImagePreview();
            });

            imagePreviewRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
            imagePreviewRecyclerView.setAdapter(imagePreviewAdapter);
        }
    }

    private void setupDatePickers(TextInputEditText startDateInput, TextInputEditText endDateInput,
                                  Calendar startCalendar, Calendar endCalendar) {
        startDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        startDateInput.setText(dateFormat.format(startCalendar.getTime()));
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        endDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        endCalendar.set(year, month, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        endDateInput.setText(dateFormat.format(endCalendar.getTime()));
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Images"));
    }

    private void updateImagePreview() {
        if (imagePreviewAdapter != null) {
            imagePreviewAdapter.notifyDataSetChanged();
        }

        if (selectedImagesCount != null) {
            selectedImagesCount.setText(selectedImageUris.size() + " images selected");
            selectedImagesCount.setVisibility(selectedImageUris.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void loadExistingImages(Journey journey) {
        // For existing journeys, we'll show a count but not load the actual images in the dialog
        if (selectedImagesCount != null) {
            selectedImagesCount.setText(journey.getImageCount() + " existing images");
            selectedImagesCount.setVisibility(journey.hasImages() ? View.VISIBLE : View.GONE);
        }
    }

    private void createJourneyWithImages(Journey journey, AlertDialog dialog) {
        if (saveButton != null) {
            saveButton.setEnabled(false);
            saveButton.setText("Saving...");
        }

        if (selectedImageUris.isEmpty()) {
            createJourney(journey, dialog);
        } else {
            journeyController.uploadJourneyImages(journey, selectedImageUris)
                    .addOnSuccessListener(updatedJourney -> {
                        createJourney(updatedJourney, dialog);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading images", e);
                        Toast.makeText(getContext(), "Failed to upload images", Toast.LENGTH_SHORT).show();
                        resetSaveButton();
                    });
        }
    }

    private void updateJourneyWithImages(Journey journey, AlertDialog dialog) {
        if (saveButton != null) {
            saveButton.setEnabled(false);
            saveButton.setText("Updating...");
        }

        if (selectedImageUris.isEmpty()) {
            updateJourney(journey, dialog);
        } else {
            journeyController.uploadJourneyImages(journey, selectedImageUris)
                    .addOnSuccessListener(updatedJourney -> {
                        updateJourney(updatedJourney, dialog);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading images", e);
                        Toast.makeText(getContext(), "Failed to upload images", Toast.LENGTH_SHORT).show();
                        resetSaveButton();
                    });
        }
    }

    private void createJourney(Journey journey, AlertDialog dialog) {
        journeyController.createJourney(journey)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Journey created successfully");
                    Toast.makeText(getContext(), "Journey created successfully", Toast.LENGTH_SHORT).show();
                    selectedImageUris.clear();
                    loadUserJourneys();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating journey", e);
                    Toast.makeText(getContext(), "Failed to create journey", Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                });
    }

    private void updateJourney(Journey journey, AlertDialog dialog) {
        journeyController.updateJourney(journey)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Journey updated successfully");
                    Toast.makeText(getContext(), "Journey updated successfully", Toast.LENGTH_SHORT).show();
                    selectedImageUris.clear();
                    loadUserJourneys();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating journey", e);
                    Toast.makeText(getContext(), "Failed to update journey", Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                });
    }

    private void resetSaveButton() {
        if (saveButton != null) {
            saveButton.setEnabled(true);
            saveButton.setText("Save");
        }
    }

    private void deleteJourney(Journey journey) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Journey")
                .setMessage("Are you sure you want to delete \"" + journey.getName() + "\"? This will also delete all associated images and cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    journeyController.deleteJourneyWithImages(journey)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Journey and images deleted successfully");
                                Toast.makeText(getContext(), "Journey deleted successfully", Toast.LENGTH_SHORT).show();
                                loadUserJourneys();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting journey", e);
                                Toast.makeText(getContext(), "Failed to delete journey", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    // JourneyAdapter.OnJourneyActionListener implementation
    @Override
    public void onEditJourney(Journey journey) {
        showJourneyDialog(journey);
    }

    @Override
    public void onDeleteJourney(Journey journey) {
        deleteJourney(journey);
    }

    @Override
    public void onJourneyClick(Journey journey) {
        Log.d(TAG, "Journey clicked: " + journey.getName());
        // TODO: Implement navigation to journey details
        // Navigate to JourneyDetailActivity
        JourneyDetailActivity.startActivity(getContext(), journey);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentUser != null) {
            loadUserJourneys();
        }
    }
}