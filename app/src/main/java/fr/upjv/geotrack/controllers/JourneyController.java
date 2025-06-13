package fr.upjv.geotrack.controllers;

import android.net.Uri;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import fr.upjv.geotrack.models.Journey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class JourneyController {
    private static final String TAG = "JourneyController";
    private String collectionName = "journey";
    private FirebaseFirestore DBFireStore;
    private FirebaseStorage storage;
    private StorageReference storageRef;

    public JourneyController() {
        this.DBFireStore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.storageRef = storage.getReference();
    }

    /**
     * Create a new journey in Firestore with a specific document ID
     * @param journey The journey object to create
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> createJourney(Journey journey) {
        String documentId = journey.getId();
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
     * Delete a journey from Firestore and all associated images from Storage
     * @param journey The journey to delete (needed for image paths)
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> deleteJourneyWithImages(Journey journey) {
        Log.d(TAG, "Deleting journey with images: " + journey.getId());

        // First delete all images from storage
        List<Task<Void>> deleteTasks = new ArrayList<>();

        // Delete all journey images
        if (journey.getImagePaths() != null) {
            for (String imagePath : journey.getImagePaths()) {
                StorageReference imageRef = storageRef.child(imagePath);
                deleteTasks.add(imageRef.delete());
            }
        }

        // Delete thumbnail if different from main images
        if (journey.getThumbnailPath() != null) {
            StorageReference thumbnailRef = storageRef.child(journey.getThumbnailPath());
            deleteTasks.add(thumbnailRef.delete());
        }

        // Wait for all image deletions to complete, then delete the Firestore document
        return Tasks.whenAll(deleteTasks).continueWithTask(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "All images deleted successfully");
            } else {
                Log.w(TAG, "Some images could not be deleted", task.getException());
            }

            // Delete the Firestore document regardless of image deletion success
            return DBFireStore
                    .collection(this.collectionName)
                    .document(journey.getId())
                    .delete();
        });
    }

    /**
     * Delete a journey from Firestore only (without deleting images)
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
     * Upload multiple images for a journey
     * @param journey The journey to add images to
     * @param imageUris List of image URIs to upload
     * @return Task<Journey> containing the updated journey with image paths
     */
    public Task<Journey> uploadJourneyImages(Journey journey, List<Uri> imageUris) {
        Log.d(TAG, "Uploading " + imageUris.size() + " images for journey: " + journey.getId());

        List<Task<String>> uploadTasks = new ArrayList<>();

        for (int i = 0; i < imageUris.size(); i++) {
            Uri imageUri = imageUris.get(i);
            String imagePath = journey.generateImagePath(i, getFileExtension(imageUri));
            StorageReference imageRef = storageRef.child(imagePath);

            // Create upload task and map it to return the storage path
            Task<String> uploadTask = imageRef.putFile(imageUri)
                    .continueWith(task -> {
                        if (task.isSuccessful()) {
                            return imagePath;
                        } else {
                            throw task.getException();
                        }
                    });

            uploadTasks.add(uploadTask);
        }

        // Wait for all uploads to complete
        return Tasks.<String>whenAllSuccess(uploadTasks).continueWith(task -> {
            if (task.isSuccessful()) {
                List<String> imagePaths = task.getResult();
                journey.setImagePaths(imagePaths);

                // Set the first image as thumbnail if not already set
                if (!imagePaths.isEmpty() && journey.getThumbnailPath() == null) {
                    journey.setThumbnailPath(imagePaths.get(0));
                }

                Log.d(TAG, "Successfully uploaded " + imagePaths.size() + " images");
                return journey;
            } else {
                throw task.getException();
            }
        });
    }

    /**
     * Upload a single image for a journey
     * @param journey The journey to add the image to
     * @param imageUri The URI of the image to upload
     * @return Task<String> containing the storage path of the uploaded image
     */
    public Task<String> uploadSingleJourneyImage(Journey journey, Uri imageUri) {
        int imageIndex = journey.getImageCount();
        String imagePath = journey.generateImagePath(imageIndex, getFileExtension(imageUri));
        StorageReference imageRef = storageRef.child(imagePath);

        Log.d(TAG, "Uploading single image to: " + imagePath);

        return imageRef.putFile(imageUri).continueWith(task -> {
            if (task.isSuccessful()) {
                journey.addImagePath(imagePath);
                return imagePath;
            } else {
                throw task.getException();
            }
        });
    }

    /**
     * Get download URL for an image
     * @param imagePath The storage path of the image
     * @return Task<Uri> containing the download URL
     */
    public Task<Uri> getImageDownloadUrl(String imagePath) {
        StorageReference imageRef = storageRef.child(imagePath);
        return imageRef.getDownloadUrl();
    }

    /**
     * Get download URLs for multiple images
     * @param imagePaths List of storage paths
     * @return Task<List<Uri>> containing the download URLs
     */
    public Task<List<Uri>> getImageDownloadUrls(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        List<Task<Uri>> urlTasks = new ArrayList<>();
        for (String imagePath : imagePaths) {
            urlTasks.add(getImageDownloadUrl(imagePath));
        }

        return Tasks.whenAllSuccess(urlTasks);
    }

    /**
     * Delete a specific image from storage and update the journey
     * @param journey The journey containing the image
     * @param imagePath The path of the image to delete
     * @return Task<Void> for handling success/failure
     */
    public Task<Void> deleteJourneyImage(Journey journey, String imagePath) {
        Log.d(TAG, "Deleting image: " + imagePath);

        StorageReference imageRef = storageRef.child(imagePath);
        return imageRef.delete().continueWithTask(task -> {
            if (task.isSuccessful()) {
                journey.removeImagePath(imagePath);
                return updateJourney(journey);
            } else {
                throw task.getException();
            }
        });
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
     * Helper method to get file extension from URI
     * @param uri The URI of the file
     * @return File extension (defaults to "jpg" if not found)
     */
    private String getFileExtension(Uri uri) {
        String uriString = uri.toString();
        String extension = "jpg"; // default

        int lastDot = uriString.lastIndexOf('.');
        if (lastDot != -1 && lastDot < uriString.length() - 1) {
            extension = uriString.substring(lastDot + 1).toLowerCase();
        }

        return extension;
    }
}