package fr.upjv.geotrack.controllers;

import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import fr.upjv.geotrack.models.Journey;

public class JourneyController {
    private static final String TAG = "JourneyController";
    private String collectionName = "journey";
    private FirebaseFirestore DBFireStore;

    public JourneyController() {
        this.DBFireStore = FirebaseFirestore.getInstance();
    }

    /**
     * Create a new journey in Firestore with a specific document ID
     * @param journey The journey object to create
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> createJourney(Journey journey) {
        String documentId = journey.getId(); // Or any unique ID you choose
        Log.d(TAG, "Creating journey with ID: " + documentId);
        return DBFireStore
                .collection(this.collectionName)
                .document(documentId)
                .set(journey.toJson());
    }

    /**
     * Update an existing journey in Firestore
     * @param journey The journey object to update
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> updateJourney(Journey journey) {
        Log.d(TAG, "Updating journey: " + journey.getName());
        return DBFireStore
                .collection(this.collectionName)
                .document(journey.getId())
                .set(journey.toJson());
    }

    /**
     * Delete a journey from Firestore
     * @param journeyId The ID of the journey to delete
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> deleteJourney(String journeyId) {
        Log.d(TAG, "Deleting journey with ID: " + journeyId);
        return DBFireStore
                .collection(this.collectionName)
                .document(journeyId)
                .delete();
    }

    /**
     * Get all journeys for a specific user
     * @param userUUID The UUID of the user
     * @return Task<QuerySnapshot> containing the user's journeys
     */
    public Task<QuerySnapshot> getUserJourneys(String userUUID) {
        Log.d(TAG, "Fetching journeys for user: " + userUUID);
        return DBFireStore
                .collection(this.collectionName)
                .whereEqualTo("userUUID", userUUID)
                .orderBy("start", Query.Direction.DESCENDING)
                .get();
    }

    /**
     * Get a specific journey by ID
     * @param journeyId The ID of the journey to retrieve
     * @return Task<DocumentSnapshot> containing the journey data
     */
    public Task<com.google.firebase.firestore.DocumentSnapshot> getJourneyById(String journeyId) {
        Log.d(TAG, "Fetching journey with ID: " + journeyId);
        return DBFireStore
                .collection(this.collectionName)
                .document(journeyId)
                .get();
    }

    /**
     * Legacy method - kept for backward compatibility
     * @param journey The journey to save
     * @param TAG The tag for logging
     */
    public void saveLocalisation(Journey journey, String TAG) {
        // Save to Firestore with unique document ID
        DBFireStore
                .collection(this.collectionName)
                .document(journey.getId())
                .set(journey.toJson())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Journey saved successfully to Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save journey to Firestore", e);
                });
    }

    /**
     * Search journeys by name for a specific user
     * @param userUUID The UUID of the user
     * @param searchTerm The term to search for in journey names
     * @return Task<QuerySnapshot> containing matching journeys
     */
    public Task<QuerySnapshot> searchUserJourneys(String userUUID, String searchTerm) {
        Log.d(TAG, "Searching journeys for user: " + userUUID + " with term: " + searchTerm);
        return DBFireStore
                .collection(this.collectionName)
                .whereEqualTo("userUUID", userUUID)
                .whereGreaterThanOrEqualTo("name", searchTerm)
                .whereLessThanOrEqualTo("name", searchTerm + "\uf8ff")
                .get();
    }

    /**
     * Get journeys within a date range for a specific user
     * @param userUUID The UUID of the user
     * @param startDate The start date for filtering
     * @param endDate The end date for filtering
     * @return Task<QuerySnapshot> containing journeys within the date range
     */
    public Task<QuerySnapshot> getJourneysInDateRange(String userUUID, java.util.Date startDate, java.util.Date endDate) {
        Log.d(TAG, "Fetching journeys for user: " + userUUID + " between " + startDate + " and " + endDate);
        return DBFireStore
                .collection(this.collectionName)
                .whereEqualTo("userUUID", userUUID)
                .whereGreaterThanOrEqualTo("start", startDate)
                .whereLessThanOrEqualTo("end", endDate)
                .orderBy("start", Query.Direction.ASCENDING)
                .get();
    }
}