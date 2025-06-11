package fr.upjv.geotrack.controllers;

import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.models.Journey;
import java.util.ArrayList;
import java.util.List;

public class LocalisationController {
    private static final String TAG = "LocalisationController";
    private String collectionName = "localisation";
    private String currentLocationCollectionName = "updateUserCurrentLocation";
    private FirebaseFirestore DBFireStore;

    public LocalisationController(){
        this.DBFireStore = FirebaseFirestore.getInstance();
    }

    public void saveLocalisation(Localisation localisation, String TAG) {
        // Save to Firestore with unique document ID
        DBFireStore
                .collection(this.collectionName)
                .document(localisation.getId()) // Use unique ID instead of "test"
                .set(localisation.toJson())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location saved successfully to Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save location to Firestore", e);
                });
    }

    /**
     * Updates the current location of a user in a dedicated collection
     * This method continuously updates the last known position for real-time tracking
     * @param localisation The current location data to update
     * @param TAG Tag for logging purposes
     */
    public void updateUserCurrentLocation(Localisation localisation, String TAG) {
        // Update current location using userUUID as document ID
        DBFireStore
                .collection(this.currentLocationCollectionName)
                .document(localisation.getUserUUID()) // Use userUUID as document ID
                .set(localisation.toJson())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Current location updated successfully for user: " + localisation.getUserUUID());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update current location for user: " + localisation.getUserUUID(), e);
                });
    }

    /**
     * Get localizations within a journey's time period with approximately 15-minute intervals
     * @param journey The journey to get localizations for
     * @return Task<List<Localisation>> containing filtered localizations with ~15min gaps
     */
    public Task<List<Localisation>> getLocalisationsForJourney(Journey journey) {
        Log.d(TAG, "Fetching localizations for journey: " + journey.getId());

        return DBFireStore
                .collection(this.collectionName)
                .whereEqualTo("userUUID", journey.getUserUUID())
                .whereGreaterThanOrEqualTo("timestamp", journey.getStart())
                .whereLessThanOrEqualTo("timestamp", journey.getEnd())
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .continueWith(task -> {
                    List<Localisation> allLocalisations = new ArrayList<>();
                    List<Localisation> filteredLocalisations = new ArrayList<>();

                    if (task.isSuccessful() && task.getResult() != null) {
                        // First, parse all documents
                        for (DocumentSnapshot document : task.getResult()) {
                            try {
                                String id = document.getString("id");
                                String userUUID = document.getString("userUUID");
                                java.util.Date timestamp = document.getDate("timestamp");
                                Double latitude = document.getDouble("latitude");
                                Double longitude = document.getDouble("longitude");

                                if (id != null && userUUID != null && timestamp != null &&
                                        latitude != null && longitude != null) {
                                    Localisation localisation = new Localisation(
                                            id, userUUID, timestamp, latitude, longitude
                                    );
                                    allLocalisations.add(localisation);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing localisation document: " + document.getId(), e);
                            }
                        }

                        // Filter to get locations with ~15 minute intervals
                        filteredLocalisations = filterByTimeInterval(allLocalisations, 15 * 60 * 1000); // 15 minutes in milliseconds

                        Log.d(TAG, "Retrieved " + allLocalisations.size() + " total localizations, filtered to " +
                                filteredLocalisations.size() + " with 15-minute intervals");
                    } else {
                        Log.w(TAG, "Failed to retrieve localizations", task.getException());
                    }
                    return filteredLocalisations;
                });
    }

    /**
     * Filter localizations to maintain approximately the specified time interval between points
     * @param localisations List of all localizations (should be ordered by timestamp)
     * @param intervalMs Desired interval in milliseconds (e.g., 15 * 60 * 1000 for 15 minutes)
     * @return Filtered list with desired time intervals
     */
    private List<Localisation> filterByTimeInterval(List<Localisation> localisations, long intervalMs) {
        if (localisations.isEmpty()) {
            return new ArrayList<>();
        }

        List<Localisation> filtered = new ArrayList<>();

        // Always include the first location
        filtered.add(localisations.get(0));

        if (localisations.size() == 1) {
            return filtered;
        }

        long lastSelectedTime = localisations.get(0).getTimestamp().getTime();

        for (int i = 1; i < localisations.size(); i++) {
            Localisation current = localisations.get(i);
            long currentTime = current.getTimestamp().getTime();

            // If enough time has passed since the last selected location
            if (currentTime - lastSelectedTime >= intervalMs) {
                filtered.add(current);
                lastSelectedTime = currentTime;
            }
        }

        // Always include the last location if it's not already included
        Localisation lastLocation = localisations.get(localisations.size() - 1);
        if (!filtered.contains(lastLocation)) {
            filtered.add(lastLocation);
        }

        return filtered;
    }
}