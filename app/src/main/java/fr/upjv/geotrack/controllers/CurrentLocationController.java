package fr.upjv.geotrack.controllers;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.models.User;

public class CurrentLocationController {
    private static final String TAG = "CurrentLocationController";
    private static final String COLLECTION_NAME = "updateUserCurrentLocation";

    private FirebaseFirestore firestore;
    private Context context;

    private Map<String, Localisation> userLocations;
    private List<ListenerRegistration> locationListeners;
    private LocationUpdateCallback locationUpdateCallback;

    // Interface for location update callbacks
    public interface LocationUpdateCallback {
        void onLocationUpdated(String userId, Localisation location);
        void onLocationRemoved(String userId);
        void onError(String userId, String error);
        void onInitialLocationsLoaded(int loadedCount, int totalCount); // New callback for initial load
    }

    // Interface for bulk operations
    public interface LocationBatchCallback {
        void onSuccess(Map<String, Localisation> locations);
        void onError(String error);
    }

    public CurrentLocationController(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.userLocations = new HashMap<>();
        this.locationListeners = new ArrayList<>();

        Log.d(TAG, "CurrentLocationController initialized");
    }

    /**
     * Set callback for location updates
     */
    public void setLocationUpdateCallback(LocationUpdateCallback callback) {
        this.locationUpdateCallback = callback;
        Log.d(TAG, "LocationUpdateCallback set: " + (callback != null ? "Yes" : "No"));
    }

    /**
     * Start listening to real-time location updates for multiple users
     * This method now first loads current positions, then sets up real-time listeners
     */
    public void startLocationListening(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            Log.d(TAG, "No user IDs provided for location listening");
            return;
        }

        // Clear existing listeners first
        stopLocationListening();

        Log.d(TAG, "=== STARTING LOCATION LISTENING WITH INITIAL LOAD ===");
        Log.d(TAG, "User IDs to track: " + userIds.toString());
        Log.d(TAG, "Total users: " + userIds.size());

        // First, load current/last positions for all users
        loadInitialLocations(userIds, () -> {
            Log.d(TAG, "Initial locations loaded, now setting up real-time listeners");

            // After loading initial positions, set up real-time listeners
            for (String userId : userIds) {
                Log.d(TAG, "Setting up real-time listener for user ID: '" + userId + "'");
                startLocationListeningForUser(userId);
            }

            Log.d(TAG, "Started " + locationListeners.size() + " location listeners");
            Log.d(TAG, "=== LOCATION LISTENING SETUP COMPLETE ===");
        });
    }

    /**
     * Load initial/current locations for all users before setting up real-time listeners
     */
    private void loadInitialLocations(List<String> userIds, Runnable onComplete) {
        Log.d(TAG, "=== LOADING INITIAL LOCATIONS ===");
        Log.d(TAG, "Loading initial locations for " + userIds.size() + " users");

        AtomicInteger completedRequests = new AtomicInteger(0);
        AtomicInteger successfulLoads = new AtomicInteger(0);
        final int totalUsers = userIds.size();

        for (String userId : userIds) {
            Log.d(TAG, "Loading initial location for user: " + userId);

            firestore.collection(COLLECTION_NAME)
                    .document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Log.d(TAG, "Initial location fetch completed for user: " + userId);

                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "✅ Initial document exists for user: " + userId);

                            try {
                                Localisation location = parseLocationFromDocument(documentSnapshot, userId);
                                if (location != null) {
                                    Log.d(TAG, "✅ Initial location parsed successfully for user: " + userId);
                                    Log.d(TAG, "Initial location: " + location.getLatitude() + ", " + location.getLongitude());

                                    // Store the location
                                    userLocations.put(userId, location);
                                    successfulLoads.incrementAndGet();

                                    // Notify callback immediately for initial display
                                    if (locationUpdateCallback != null) {
                                        Log.d(TAG, "Notifying callback of initial location for user: " + userId);
                                        locationUpdateCallback.onLocationUpdated(userId, location);
                                    }
                                } else {
                                    Log.w(TAG, "❌ Failed to parse initial location for user: " + userId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "❌ Error parsing initial location data for user " + userId, e);
                                if (locationUpdateCallback != null) {
                                    locationUpdateCallback.onError(userId, "Error parsing initial location: " + e.getMessage());
                                }
                            }
                        } else {
                            Log.d(TAG, "❌ No initial document found for user: " + userId);
                        }

                        // Check if all requests completed
                        int completed = completedRequests.incrementAndGet();
                        Log.d(TAG, "Initial location requests completed: " + completed + "/" + totalUsers);

                        if (completed == totalUsers) {
                            int successful = successfulLoads.get();
                            Log.d(TAG, "=== INITIAL LOCATION LOADING COMPLETE ===");
                            Log.d(TAG, "Successfully loaded " + successful + " out of " + totalUsers + " initial locations");

                            // Notify callback about initial load completion
                            if (locationUpdateCallback != null) {
                                locationUpdateCallback.onInitialLocationsLoaded(successful, totalUsers);
                            }

                            // Continue with real-time listener setup
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to load initial location for user " + userId, e);

                        if (locationUpdateCallback != null) {
                            locationUpdateCallback.onError(userId, "Failed to load initial location: " + e.getMessage());
                        }

                        // Still count as completed to avoid hanging
                        int completed = completedRequests.incrementAndGet();
                        Log.d(TAG, "Initial location requests completed (with error): " + completed + "/" + totalUsers);

                        if (completed == totalUsers) {
                            int successful = successfulLoads.get();
                            Log.d(TAG, "=== INITIAL LOCATION LOADING COMPLETE (WITH ERRORS) ===");
                            Log.d(TAG, "Successfully loaded " + successful + " out of " + totalUsers + " initial locations");

                            // Notify callback about initial load completion
                            if (locationUpdateCallback != null) {
                                locationUpdateCallback.onInitialLocationsLoaded(successful, totalUsers);
                            }

                            // Continue with real-time listener setup
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                    });
        }
    }

    /**
     * Start listening to location updates for a single user
     */
    public void startLocationListeningForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "Invalid user ID provided for location listening");
            return;
        }

        // Check if we're already listening to this user
        if (isListeningToUser(userId)) {
            Log.d(TAG, "Already listening to user: " + userId);
            return;
        }

        Log.d(TAG, "Setting up real-time listener for user: " + userId);
        Log.d(TAG, "Firestore collection: " + COLLECTION_NAME);
        Log.d(TAG, "Document path: " + COLLECTION_NAME + "/" + userId);

        ListenerRegistration listener = firestore.collection(COLLECTION_NAME)
                .document(userId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    Log.d(TAG, "=== REAL-TIME SNAPSHOT LISTENER TRIGGERED ===");
                    Log.d(TAG, "User ID: " + userId);

                    if (error != null) {
                        Log.e(TAG, "❌ ERROR listening to location for user " + userId, error);
                        Log.e(TAG, "Error message: " + error.getMessage());
                        Log.e(TAG, "Error code: " + error.getCode());
                        if (locationUpdateCallback != null) {
                            locationUpdateCallback.onError(userId, error.getMessage());
                        }
                        return;
                    }

                    Log.d(TAG, "Real-time document snapshot received for user: " + userId);
                    Log.d(TAG, "Document exists: " + (documentSnapshot != null && documentSnapshot.exists()));

                    if (documentSnapshot != null) {
                        Log.d(TAG, "Document ID: " + documentSnapshot.getId());
                        Log.d(TAG, "Document path: " + documentSnapshot.getReference().getPath());
                        Log.d(TAG, "Document metadata - from cache: " + documentSnapshot.getMetadata().isFromCache());
                        Log.d(TAG, "Document metadata - has pending writes: " + documentSnapshot.getMetadata().hasPendingWrites());

                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "✅ Real-time document exists for user: " + userId);

                            try {
                                // Parse the location data
                                Localisation location = parseLocationFromDocument(documentSnapshot, userId);
                                if (location != null) {
                                    Log.d(TAG, "✅ Real-time location parsed successfully for user: " + userId);

                                    // Check if this is actually a new/updated location
                                    Localisation existingLocation = userLocations.get(userId);
                                    boolean isNewLocation = existingLocation == null ||
                                            !existingLocation.getTimestamp().equals(location.getTimestamp()) ||
                                            !(existingLocation.getLatitude() == location.getLatitude()) ||
                                            !(existingLocation.getLongitude() == location.getLongitude());

                                    if (isNewLocation) {
                                        Log.d(TAG, "📍 New location update for user: " + userId);
                                        Log.d(TAG, "New location details:");
                                        Log.d(TAG, "  - Latitude: " + location.getLatitude());
                                        Log.d(TAG, "  - Longitude: " + location.getLongitude());
                                        Log.d(TAG, "  - Timestamp: " + location.getTimestamp());

                                        // Update the location in our map
                                        userLocations.put(userId, location);
                                        Log.d(TAG, "Total tracked users: " + userLocations.size());

                                        // Notify callback
                                        if (locationUpdateCallback != null) {
                                            Log.d(TAG, "Notifying callback of real-time location update for user: " + userId);
                                            locationUpdateCallback.onLocationUpdated(userId, location);
                                        }
                                    } else {
                                        Log.d(TAG, "📍 Same location data for user: " + userId + " (no update needed)");
                                    }
                                } else {
                                    Log.w(TAG, "❌ Failed to parse real-time location from document for user: " + userId);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "❌ Error parsing real-time location data for user " + userId, e);
                                if (locationUpdateCallback != null) {
                                    locationUpdateCallback.onError(userId, "Error parsing location data: " + e.getMessage());
                                }
                            }
                        } else {
                            Log.d(TAG, "❌ No real-time document found for user: " + userId);
                            Log.d(TAG, "Document path checked: " + COLLECTION_NAME + "/" + userId);

                            // Remove location if document doesn't exist
                            Localisation removedLocation = userLocations.remove(userId);
                            if (removedLocation != null) {
                                Log.d(TAG, "Removed location from cache for user: " + userId);
                            }

                            if (locationUpdateCallback != null) {
                                Log.d(TAG, "Notifying callback of location removal for user: " + userId);
                                locationUpdateCallback.onLocationRemoved(userId);
                            }
                        }
                    } else {
                        Log.w(TAG, "❌ DocumentSnapshot is null for user: " + userId);
                    }

                    Log.d(TAG, "=== REAL-TIME SNAPSHOT PROCESSING COMPLETE ===");
                });

        locationListeners.add(listener);
        Log.d(TAG, "✅ Started real-time location listener for user: " + userId + " (Total listeners: " + locationListeners.size() + ")");
    }

    /**
     * Stop listening to location updates for a specific user
     */
    public void stopLocationListeningForUser(String userId) {
        Log.d(TAG, "Stop location listener requested for user: " + userId);
        userLocations.remove(userId);
        Log.d(TAG, "Removed user from location cache: " + userId);
    }

    /**
     * Stop all location listeners
     */
    public void stopLocationListening() {
        Log.d(TAG, "=== STOPPING ALL LOCATION LISTENERS ===");
        Log.d(TAG, "Stopping " + locationListeners.size() + " location listeners");

        for (int i = 0; i < locationListeners.size(); i++) {
            ListenerRegistration listener = locationListeners.get(i);
            if (listener != null) {
                listener.remove();
                Log.d(TAG, "Removed listener " + (i + 1));
            }
        }

        locationListeners.clear();
        userLocations.clear();

        Log.d(TAG, "All location listeners stopped and caches cleared");
        Log.d(TAG, "=== CLEANUP COMPLETE ===");
    }

    /**
     * Parse location data from Firestore document
     */
    private Localisation parseLocationFromDocument(DocumentSnapshot document, String userId) {
        Log.d(TAG, "=== PARSING LOCATION FROM DOCUMENT ===");
        Log.d(TAG, "User ID: " + userId);

        try {
            Double latitude = document.getDouble("latitude");
            Double longitude = document.getDouble("longitude");
            Date timestamp = document.getDate("timestamp");

            Log.d(TAG, "Raw field values:");
            Log.d(TAG, "  - latitude: " + latitude);
            Log.d(TAG, "  - longitude: " + longitude);
            Log.d(TAG, "  - timestamp: " + timestamp);

            if (latitude != null && longitude != null) {
                Localisation location = new Localisation(
                        document.getId(), // Use document ID as localisation ID
                        userId,
                        timestamp != null ? timestamp : new Date(),
                        latitude,
                        longitude
                );

                Log.d(TAG, "✅ Successfully created Localisation object");
                Log.d(TAG, "=== PARSING COMPLETE ===");
                return location;
            } else {
                Log.w(TAG, "❌ Invalid location data for user " + userId + ": lat=" + latitude + ", lng=" + longitude);
                Log.w(TAG, "Either latitude or longitude is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception while parsing location document for user " + userId, e);
        }

        Log.d(TAG, "=== PARSING FAILED ===");
        return null;
    }

    /**
     * Get current location of a specific user
     */
    public Localisation getUserLocation(String userId) {
        Localisation location = userLocations.get(userId);
        Log.d(TAG, "Get location for user '" + userId + "': " + (location != null ? "Found" : "Not found"));
        return location;
    }

    /**
     * Get all current user locations
     */
    public Map<String, Localisation> getAllUserLocations() {
        Log.d(TAG, "Getting all user locations. Total tracked users: " + userLocations.size());
        for (Map.Entry<String, Localisation> entry : userLocations.entrySet()) {
            Localisation loc = entry.getValue();
            Log.d(TAG, "  User '" + entry.getKey() + "': " + loc.getLatitude() + ", " + loc.getLongitude());
        }
        return new HashMap<>(userLocations);
    }

    /**
     * Check if a user's location is being tracked
     */
    public boolean isUserLocationTracked(String userId) {
        boolean tracked = userLocations.containsKey(userId);
        Log.d(TAG, "Is user '" + userId + "' location tracked: " + tracked);
        return tracked;
    }

    /**
     * Check if we're currently listening to a specific user's location
     */
    public boolean isListeningToUser(String userId) {
        // This is a simplified check - in production you might want to maintain
        // a more sophisticated mapping of userId to ListenerRegistration
        boolean listening = userLocations.containsKey(userId) || locationListeners.size() > 0;
        Log.d(TAG, "Is listening to user '" + userId + "': " + listening);
        return listening;
    }

    /**
     * Get the number of active location listeners
     */
    public int getActiveListenerCount() {
        Log.d(TAG, "Active listener count: " + locationListeners.size());
        return locationListeners.size();
    }

    /**
     * Get the number of users currently being tracked
     */
    public int getTrackedUserCount() {
        Log.d(TAG, "Tracked user count: " + userLocations.size());
        return userLocations.size();
    }

    /**
     * Get a list of user IDs currently being tracked
     */
    public List<String> getTrackedUserIds() {
        List<String> userIds = new ArrayList<>(userLocations.keySet());
        Log.d(TAG, "Tracked user IDs: " + userIds.toString());
        return userIds;
    }

    /**
     * Add a single user to the existing listeners
     */
    public void addUserToLocationTracking(String userId) {
        Log.d(TAG, "Adding user to location tracking: " + userId);
        if (userId != null && !userId.trim().isEmpty()) {
            // First load their current location, then set up real-time listener
            List<String> singleUserList = new ArrayList<>();
            singleUserList.add(userId);
            loadInitialLocations(singleUserList, () -> {
                startLocationListeningForUser(userId);
            });
        } else {
            Log.w(TAG, "Cannot add invalid user ID to tracking");
        }
    }

    /**
     * Get the most recent location update timestamp for a user
     */
    public Date getLastLocationUpdateTime(String userId) {
        Localisation location = getUserLocation(userId);
        Date timestamp = location != null ? location.getTimestamp() : null;
        Log.d(TAG, "Last location update time for user '" + userId + "': " + timestamp);
        return timestamp;
    }

    /**
     * Check if a user's location is recent (within specified minutes)
     */
    public boolean isLocationRecent(String userId, int withinMinutes) {
        Date lastUpdate = getLastLocationUpdateTime(userId);
        if (lastUpdate == null) {
            Log.d(TAG, "No location data for user '" + userId + "' - not recent");
            return false;
        }

        long timeDifference = new Date().getTime() - lastUpdate.getTime();
        long minutesDifference = timeDifference / (1000 * 60);

        boolean isRecent = minutesDifference <= withinMinutes;
        Log.d(TAG, "Location for user '" + userId + "' is " + minutesDifference + " minutes old - recent: " + isRecent);
        return isRecent;
    }

    /**
     * Get distance between two users (if both have locations)
     * Returns distance in meters, or -1 if one or both users don't have locations
     */
    public double getDistanceBetweenUsers(String userId1, String userId2) {
        Localisation loc1 = getUserLocation(userId1);
        Localisation loc2 = getUserLocation(userId2);

        if (loc1 == null || loc2 == null) {
            Log.d(TAG, "Cannot calculate distance - missing location data");
            return -1;
        }

        double distance = calculateDistance(loc1.getLatitude(), loc1.getLongitude(),
                loc2.getLatitude(), loc2.getLongitude());

        Log.d(TAG, "Distance between '" + userId1 + "' and '" + userId2 + "': " + distance + " meters");
        return distance;
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     * Returns distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Cleanup method - should be called when the controller is no longer needed
     */
    public void cleanup() {
        Log.d(TAG, "=== CLEANING UP CURRENT LOCATION CONTROLLER ===");
        stopLocationListening();
        locationUpdateCallback = null;
        Log.d(TAG, "CurrentLocationController cleanup complete");
    }
}