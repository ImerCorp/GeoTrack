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
    }

    /**
     * Set callback for location updates
     */
    public void setLocationUpdateCallback(LocationUpdateCallback callback) {
        this.locationUpdateCallback = callback;
    }

    /**
     * Start listening to real-time location updates for multiple users
     */
    public void startLocationListening(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            Log.d(TAG, "No user IDs provided for location listening");
            return;
        }

        // Clear existing listeners first
        stopLocationListening();

        Log.d(TAG, "Starting location listeners for " + userIds.size() + " users");

        for (String userId : userIds) {
            startLocationListeningForUser(userId);
        }

        Log.d(TAG, "Started " + locationListeners.size() + " location listeners");
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

        ListenerRegistration listener = firestore.collection(COLLECTION_NAME)
                .document(userId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to location for user " + userId, error);
                        if (locationUpdateCallback != null) {
                            locationUpdateCallback.onError(userId, error.getMessage());
                        }
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        try {
                            // Parse the location data
                            Localisation location = parseLocationFromDocument(documentSnapshot, userId);
                            if (location != null) {
                                // Update the location in our map
                                userLocations.put(userId, location);

                                Log.d(TAG, String.format("Location updated for user %s: %.6f, %.6f",
                                        userId, location.getLatitude(), location.getLongitude()));

                                // Notify callback
                                if (locationUpdateCallback != null) {
                                    locationUpdateCallback.onLocationUpdated(userId, location);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing location data for user " + userId, e);
                            if (locationUpdateCallback != null) {
                                locationUpdateCallback.onError(userId, "Error parsing location data: " + e.getMessage());
                            }
                        }
                    } else {
                        Log.d(TAG, "No location data found for user " + userId);
                        // Remove location if document doesn't exist
                        userLocations.remove(userId);

                        if (locationUpdateCallback != null) {
                            locationUpdateCallback.onLocationRemoved(userId);
                        }
                    }
                });

        locationListeners.add(listener);
        Log.d(TAG, "Started location listener for user: " + userId);
    }

    /**
     * Stop listening to location updates for a specific user
     */
    public void stopLocationListeningForUser(String userId) {
        // Note: This is more complex since we need to track which listener belongs to which user
        // For simplicity, this implementation stops all listeners and restarts without the specified user
        // In a production app, you might want to maintain a map of userId -> ListenerRegistration

        Log.d(TAG, "Stopping location listener for user: " + userId);
        userLocations.remove(userId);

        // For now, we'll just remove the location from our cache
        // The listener will remain active but won't update our cache
    }

    /**
     * Stop all location listeners
     */
    public void stopLocationListening() {
        Log.d(TAG, "Stopping " + locationListeners.size() + " location listeners");

        for (ListenerRegistration listener : locationListeners) {
            if (listener != null) {
                listener.remove();
            }
        }

        locationListeners.clear();
        userLocations.clear();

        Log.d(TAG, "All location listeners stopped");
    }

    /**
     * Parse location data from Firestore document
     */
    private Localisation parseLocationFromDocument(DocumentSnapshot document, String userId) {
        try {
            Double latitude = document.getDouble("latitude");
            Double longitude = document.getDouble("longitude");
            Date timestamp = document.getDate("timestamp");

            if (latitude != null && longitude != null) {
                return new Localisation(
                        document.getId(), // Use document ID as localisation ID
                        userId,
                        timestamp != null ? timestamp : new Date(),
                        latitude,
                        longitude
                );
            } else {
                Log.w(TAG, "Invalid location data for user " + userId + ": lat=" + latitude + ", lng=" + longitude);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing location document for user " + userId, e);
        }
        return null;
    }

    /**
     * Get current location of a specific user
     */
    public Localisation getUserLocation(String userId) {
        return userLocations.get(userId);
    }

    /**
     * Get all current user locations
     */
    public Map<String, Localisation> getAllUserLocations() {
        return new HashMap<>(userLocations);
    }

    /**
     * Check if a user's location is being tracked
     */
    public boolean isUserLocationTracked(String userId) {
        return userLocations.containsKey(userId);
    }

    /**
     * Check if we're currently listening to a specific user's location
     */
    public boolean isListeningToUser(String userId) {
        // This is a simplified check - in production you might want to maintain
        // a more sophisticated mapping of userId to ListenerRegistration
        return userLocations.containsKey(userId) || locationListeners.size() > 0;
    }

    /**
     * Get the number of active location listeners
     */
    public int getActiveListenerCount() {
        return locationListeners.size();
    }

    /**
     * Get the number of users currently being tracked
     */
    public int getTrackedUserCount() {
        return userLocations.size();
    }

    /**
     * Get a list of user IDs currently being tracked
     */
    public List<String> getTrackedUserIds() {
        return new ArrayList<>(userLocations.keySet());
    }

    /**
     * Add a single user to the existing listeners
     */
    public void addUserToLocationTracking(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            startLocationListeningForUser(userId);
        }
    }

    /**
     * Get the most recent location update timestamp for a user
     */
    public Date getLastLocationUpdateTime(String userId) {
        Localisation location = getUserLocation(userId);
        return location != null ? location.getTimestamp() : null;
    }

    /**
     * Check if a user's location is recent (within specified minutes)
     */
    public boolean isLocationRecent(String userId, int withinMinutes) {
        Date lastUpdate = getLastLocationUpdateTime(userId);
        if (lastUpdate == null) {
            return false;
        }

        long timeDifference = new Date().getTime() - lastUpdate.getTime();
        long minutesDifference = timeDifference / (1000 * 60);

        return minutesDifference <= withinMinutes;
    }

    /**
     * Get distance between two users (if both have locations)
     * Returns distance in meters, or -1 if one or both users don't have locations
     */
    public double getDistanceBetweenUsers(String userId1, String userId2) {
        Localisation loc1 = getUserLocation(userId1);
        Localisation loc2 = getUserLocation(userId2);

        if (loc1 == null || loc2 == null) {
            return -1;
        }

        return calculateDistance(loc1.getLatitude(), loc1.getLongitude(),
                loc2.getLatitude(), loc2.getLongitude());
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
        Log.d(TAG, "Cleaning up CurrentLocationController");
        stopLocationListening();
        locationUpdateCallback = null;
    }
}