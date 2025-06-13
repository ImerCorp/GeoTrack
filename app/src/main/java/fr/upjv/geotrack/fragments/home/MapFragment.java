package fr.upjv.geotrack.fragments.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.SettingsActivity;
import fr.upjv.geotrack.models.User;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.controllers.FollowController;
import fr.upjv.geotrack.controllers.CurrentLocationController;

public class MapFragment extends Fragment implements OnMapReadyCallback, CurrentLocationController.LocationUpdateCallback {

    private static final String TAG = "MapFragment";
    private static final float DEFAULT_ZOOM = 15f;
    private static final int LOCATION_FRESHNESS_MINUTES = 15; // Consider location stale after 15 minutes

    // Views
    private ImageView hamburgerMenu, appLogo, searchIcon, profileIcon;
    private MapView mapView;
    private FloatingActionButton fabMyLocation;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private StorageReference storageRef;

    // Map
    private GoogleMap googleMap;
    private Map<String, Marker> userMarkers = new HashMap<>();
    private List<User> followingUsers = new ArrayList<>();

    // Location Controller
    private CurrentLocationController locationController;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        initializeComponents(view);
        setupMapView(savedInstanceState);
        loadUserProfileImage();

        return view;
    }

    private void initializeComponents(View view) {
        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        // Initialize location controller
        locationController = new CurrentLocationController(getContext());
        locationController.setLocationUpdateCallback(this);

        // Views
        mapView = view.findViewById(R.id.map_view);
        fabMyLocation = view.findViewById(R.id.fab_my_location);
        hamburgerMenu = view.findViewById(R.id.hamburger_menu);
        appLogo = view.findViewById(R.id.app_logo);
        searchIcon = view.findViewById(R.id.search_icon);
        profileIcon = view.findViewById(R.id.profile_icon);

        // Click listeners
        fabMyLocation.setOnClickListener(v -> centerOnMyLocation());
        hamburgerMenu.setOnClickListener(v -> startActivity(new Intent(getContext(), SettingsActivity.class)));
        appLogo.setOnClickListener(v -> {
            refreshMap();
            Toast.makeText(getContext(), "Refreshing map...", Toast.LENGTH_SHORT).show();
        });
        searchIcon.setOnClickListener(v -> Toast.makeText(getContext(), "Search clicked", Toast.LENGTH_SHORT).show());
        profileIcon.setOnClickListener(v -> navigateToProfileFragment());
    }

    private void setupMapView(Bundle savedInstanceState) {
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // Enable location if permissions are granted
        try {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false); // We use our own FAB
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }

        // Configure map settings
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Set marker click listener
        googleMap.setOnMarkerClickListener(marker -> {
            String userId = findUserIdByMarker(marker);
            if (userId != null) {
                showUserLocationInfo(userId);
            }
            return false; // Return false to show info window
        });

        // Load and display following users
        loadFollowingUsers();
    }

    private void loadFollowingUsers() {
        new FollowController().getFollowingUserIds(new FollowController.FollowingUsersCallback() {
            @Override
            public void onSuccess(List<String> followingUserIds) {
                if (followingUserIds.isEmpty()) {
                    Log.d(TAG, "Not following anyone");
                    return;
                }

                // Load user details
                new UserController("FollowingUsers", getContext()).getMultipleUsersBatch(followingUserIds,
                        new UserController.MultipleUsersCallback() {
                            @Override
                            public void onSuccess(List<User> users) {
                                followingUsers.clear();
                                followingUsers.addAll(users);

                                // Start location tracking for all following users
                                locationController.startLocationListening(followingUserIds);
                                Log.d(TAG, "Started tracking " + users.size() + " users");
                            }

                            @Override
                            public void onFailure(String error) {
                                Log.e(TAG, "Error loading users: " + error);
                                Toast.makeText(getContext(), "Error loading users", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onProgress(int current, int total) {
                                Log.d(TAG, "Loading users: " + current + "/" + total);
                            }
                        });
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Error loading following list: " + error);
                Toast.makeText(getContext(), "Error loading following list", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Implementation of CurrentLocationController.LocationUpdateCallback
    @Override
    public void onLocationUpdated(String userId, Localisation location) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> updateUserMarker(userId, location));
        }
    }

    @Override
    public void onLocationRemoved(String userId) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> removeUserMarker(userId));
        }
    }

    @Override
    public void onError(String userId, String error) {
        Log.e(TAG, "Location error for user " + userId + ": " + error);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Location error for user", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void updateUserMarker(String userId, Localisation location) {
        if (googleMap == null) return;

        User user = findUserById(userId);
        if (user == null) return;

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

        // Remove existing marker if exists
        Marker existingMarker = userMarkers.get(userId);
        if (existingMarker != null) {
            existingMarker.remove();
        }

        // Determine marker color based on location freshness
        float markerColor = locationController.isLocationRecent(userId, LOCATION_FRESHNESS_MINUTES)
                ? BitmapDescriptorFactory.HUE_GREEN  // Fresh location
                : BitmapDescriptorFactory.HUE_ORANGE; // Stale location

        // Create new marker
        String title = user.getDisplayNameOrEmail();
        String snippet = createLocationSnippet(location, userId);

        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title(title)
                .snippet(snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

        Marker marker = googleMap.addMarker(markerOptions);
        userMarkers.put(userId, marker);

        Log.d(TAG, "Updated marker for " + title + " at " + position);
    }

    private String createLocationSnippet(Localisation location, String userId) {
        String timeAgo = getTimeAgo(location.getTimestamp());

        // Add distance from current user if available
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Localisation myLocation = locationController.getUserLocation(currentUser.getUid());
            if (myLocation != null) {
                double distance = locationController.getDistanceBetweenUsers(currentUser.getUid(), userId);
                if (distance >= 0) {
                    String distanceStr = distance < 1000
                            ? String.format("%.0fm away", distance)
                            : String.format("%.1fkm away", distance / 1000);
                    return timeAgo + " • " + distanceStr;
                }
            }
        }

        return timeAgo;
    }

    private void removeUserMarker(String userId) {
        Marker marker = userMarkers.get(userId);
        if (marker != null) {
            marker.remove();
            userMarkers.remove(userId);
            Log.d(TAG, "Removed marker for user " + userId);
        }
    }

    private void centerOnMyLocation() {
        if (googleMap == null) return;

        try {
            if (googleMap.getMyLocation() != null) {
                LatLng myLocation = new LatLng(
                        googleMap.getMyLocation().getLatitude(),
                        googleMap.getMyLocation().getLongitude()
                );
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, DEFAULT_ZOOM));
            } else {
                Toast.makeText(getContext(), "Current location not available", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
            Toast.makeText(getContext(), "Location permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private void centerOnAllUsers() {
        if (googleMap == null || userMarkers.isEmpty()) {
            centerOnMyLocation();
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasLocations = false;

        // Add all user markers to bounds
        for (Marker marker : userMarkers.values()) {
            builder.include(marker.getPosition());
            hasLocations = true;
        }

        // Add my location if available
        try {
            if (googleMap.getMyLocation() != null) {
                builder.include(new LatLng(
                        googleMap.getMyLocation().getLatitude(),
                        googleMap.getMyLocation().getLongitude()));
                hasLocations = true;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }

        if (hasLocations) {
            try {
                LatLngBounds bounds = builder.build();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (Exception e) {
                Log.e(TAG, "Error centering on all users", e);
            }
        }
    }

    private void refreshMap() {
        // Clear existing markers
        clearMarkers();
        // Reload following users and restart location tracking
        loadFollowingUsers();
    }

    private void clearMarkers() {
        // Remove all markers
        for (Marker marker : userMarkers.values()) {
            marker.remove();
        }
        userMarkers.clear();
    }

    private void showUserLocationInfo(String userId) {
        User user = findUserById(userId);
        if (user == null) return;

        Localisation location = locationController.getUserLocation(userId);
        if (location == null) {
            Toast.makeText(getContext(), "No location data for " + user.getDisplayNameOrEmail(), Toast.LENGTH_SHORT).show();
            return;
        }

        String message = String.format("%s\nLast seen: %s\nAccuracy: %s",
                user.getDisplayNameOrEmail(),
                getTimeAgo(location.getTimestamp()),
                locationController.isLocationRecent(userId, LOCATION_FRESHNESS_MINUTES) ? "Recent" : "Stale"
        );

        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private String findUserIdByMarker(Marker marker) {
        for (Map.Entry<String, Marker> entry : userMarkers.entrySet()) {
            if (entry.getValue().equals(marker)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private User findUserById(String userId) {
        return followingUsers.stream()
                .filter(u -> u.getUid().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private String getTimeAgo(Date timestamp) {
        if (timestamp == null) return "Unknown";

        long now = System.currentTimeMillis();
        long timestampMillis = timestamp.getTime();
        long diff = now - timestampMillis;

        if (diff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (diff < 3600000) { // Less than 1 hour
            return (diff / 60000) + "m ago";
        } else if (diff < 86400000) { // Less than 1 day
            return (diff / 3600000) + "h ago";
        } else {
            return (diff / 86400000) + "d ago";
        }
    }

    private void loadUserProfileImage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadProfileImage(currentUser.getUid(), profileIcon);
        } else {
            profileIcon.setImageResource(R.drawable.ic_profile_modern);
        }
    }

    private void loadProfileImage(String userId, ImageView imageView) {
        StorageReference profileRef = storageRef.child("users/" + userId + "/profile.jpg");

        profileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> loadImageWithGlide(uri.toString(), imageView))
                .addOnFailureListener(e -> {
                    storageRef.child("users/" + userId + "/profile.png").getDownloadUrl()
                            .addOnSuccessListener(uri -> loadImageWithGlide(uri.toString(), imageView))
                            .addOnFailureListener(ex -> imageView.setImageResource(R.drawable.ic_profile_modern));
                });
    }

    private void loadImageWithGlide(String url, ImageView imageView) {
        if (getContext() != null && isAdded()) {
            Glide.with(this)
                    .load(url)
                    .transform(new MultiTransformation<>(new CenterCrop(), new CircleCrop()))
                    .placeholder(R.drawable.ic_profile_modern)
                    .error(R.drawable.ic_profile_modern)
                    .into(imageView);
        }
    }

    private void navigateToProfileFragment() {
        try {
            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new ProfileFragment());
            transaction.addToBackStack("MapFragment");
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.commit();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Opening Profile", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // MapView lifecycle methods
    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        // Resume location tracking if we have users to track
        if (!followingUsers.isEmpty()) {
            List<String> userIds = new ArrayList<>();
            for (User user : followingUsers) {
                userIds.add(user.getUid());
            }
            locationController.startLocationListening(userIds);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Clean up location controller
        if (locationController != null) {
            locationController.cleanup();
        }

        // Clear markers
        clearMarkers();

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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    // Public methods for external access
    public List<User> getFollowingUsers() {
        return new ArrayList<>(followingUsers);
    }

    public void refreshFollowingUsers() {
        refreshMap();
    }

    public void addUserToTracking(String userId) {
        if (locationController != null) {
            locationController.addUserToLocationTracking(userId);
        }
    }

    public void centerMapOnAllUsers() {
        centerOnAllUsers();
    }

    // Get location statistics
    public String getLocationStats() {
        if (locationController == null) return "Location controller not initialized";

        return String.format("Tracking %d users, %d active listeners",
                locationController.getTrackedUserCount(),
                locationController.getActiveListenerCount());
    }
}