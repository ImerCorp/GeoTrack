package fr.upjv.geotrack.controllers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.upjv.geotrack.models.User;

public class UserController {
    private FirebaseFirestore DBFireStore;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private String collectionName = "users";
    private String TAG = "UserController";
    private Context context;

    public UserController(String tag, Context context) {
        this.TAG = tag;
        this.context = context;
        this.DBFireStore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.storageRef = storage.getReference();
    }

    // Interface for callbacks
    public interface UserCallback {
        void onSuccess(User user);
        void onFailure(String error);
    }

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
        void onProgress(int progress);
    }

    public void saveUser(User user) {
        DBFireStore.collection(collectionName)
                .document(user.getUid())
                .set(user.toJson())
                .addOnSuccessListener(success -> {
                    Log.d(TAG, "User saved successfully");
                })
                .addOnFailureListener(fail -> {
                    Log.e(TAG, "Error saving user: " + fail.getMessage());
                });
    }

    public void saveUserFirstTime(User user) {
        DBFireStore.collection(collectionName)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // User doesn't exist, save them
                        DBFireStore.collection(collectionName)
                                .document(user.getUid())
                                .set(user.toJson())
                                .addOnSuccessListener(success -> {
                                    Log.d(TAG, "User saved successfully");
                                })
                                .addOnFailureListener(fail -> {
                                    Log.e(TAG, "Error saving user: " + fail.getMessage());
                                });
                    } else {
                        Log.d(TAG, "User already exists, skipping save");
                    }
                })
                .addOnFailureListener(fail -> {
                    Log.e(TAG, "Error checking user existence: " + fail.getMessage());
                });
    }


    /**
     * Get user by UID
     */
    public void getUser(String uid, UserCallback callback) {
        DBFireStore.collection(collectionName)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            User user = new User();
                            user.setUid(documentSnapshot.getString("uid"));
                            user.setEmail(documentSnapshot.getString("email"));
                            user.setDisplayName(documentSnapshot.getString("displayName"));
                            user.setProfilePicturePath(documentSnapshot.getString("profilePicturePath"));
                            user.setProfilePictureUrl(documentSnapshot.getString("profilePictureUrl"));

                            callback.onSuccess(user);
                        } catch (Exception e) {
                            callback.onFailure("Error parsing user data: " + e.getMessage());
                        }
                    } else {
                        callback.onFailure("User not found");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onFailure("Error getting user: " + e.getMessage());
                });
    }

    /**
     * Upload profile picture to Firebase Storage
     */
    public void uploadProfilePicture(String uid, Uri imageUri, String fileExtension, UploadCallback callback) {
        if (imageUri == null) {
            callback.onFailure("Image URI is null");
            return;
        }

        // Generate storage path
        String imagePath = String.format("users/%s/profile.%s", uid, fileExtension);
        StorageReference imageRef = storageRef.child(imagePath);

        UploadTask uploadTask = imageRef.putFile(imageUri);

        // Listen for state changes, errors, and completion
        uploadTask.addOnProgressListener(taskSnapshot -> {
            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
            callback.onProgress((int) progress);
            Log.d(TAG, "Upload is " + progress + "% done");
        }).addOnSuccessListener(taskSnapshot -> {
            // Upload successful - get download URL
            imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                String downloadUrl = downloadUri.toString();
                Log.d(TAG, "Profile picture uploaded successfully: " + downloadUrl);
                callback.onSuccess(downloadUrl);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error getting download URL: " + e.getMessage());
                callback.onFailure("Error getting download URL: " + e.getMessage());
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Upload failed: " + e.getMessage());
            callback.onFailure("Upload failed: " + e.getMessage());
        });
    }

    /**
     * Update user profile picture in Firestore after upload
     */
    public void updateUserProfilePicture(String uid, String profilePicturePath, String profilePictureUrl, UserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("profilePicturePath", profilePicturePath);
        updates.put("profilePictureUrl", profilePictureUrl);

        DBFireStore.collection(collectionName)
                .document(uid)
                .update(updates)
                .addOnSuccessListener(success -> {
                    Log.d(TAG, "User profile picture updated successfully");
                    // Get updated user
                    getUser(uid, callback);
                })
                .addOnFailureListener(fail -> {
                    Log.e(TAG, "Error updating user profile picture: " + fail.getMessage());
                    callback.onFailure("Error updating profile picture: " + fail.getMessage());
                });
    }

    /**
     * Complete profile picture upload and update user
     */
    public void uploadAndUpdateProfilePicture(String uid, Uri imageUri, String fileExtension, UserCallback callback) {
        uploadProfilePicture(uid, imageUri, fileExtension, new UploadCallback() {
            @Override
            public void onSuccess(String downloadUrl) {
                String imagePath = String.format("users/%s/profile.%s", uid, fileExtension);
                updateUserProfilePicture(uid, imagePath, downloadUrl, callback);
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }

            @Override
            public void onProgress(int progress) {
                // You can handle progress updates here if needed
                Log.d(TAG, "Upload progress: " + progress + "%");
            }
        });
    }

    /**
     * Delete profile picture from storage
     */
    public void deleteProfilePicture(String uid, UserCallback callback) {
        // First get the current user to check if they have a profile picture
        getUser(uid, new UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user.hasProfilePicture()) {
                    // Delete from storage
                    StorageReference imageRef = storageRef.child(user.getProfilePicturePath());
                    imageRef.delete().addOnSuccessListener(success -> {
                        // Update user in Firestore
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("profilePicturePath", null);
                        updates.put("profilePictureUrl", null);

                        DBFireStore.collection(collectionName)
                                .document(uid)
                                .update(updates)
                                .addOnSuccessListener(updateSuccess -> {
                                    Log.d(TAG, "Profile picture deleted successfully");
                                    getUser(uid, callback);
                                })
                                .addOnFailureListener(updateFail -> {
                                    callback.onFailure("Error updating user after deletion: " + updateFail.getMessage());
                                });
                    }).addOnFailureListener(deleteFail -> {
                        callback.onFailure("Error deleting profile picture: " + deleteFail.getMessage());
                    });
                } else {
                    callback.onFailure("User has no profile picture to delete");
                }
            }

            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    /**
     * Update user display name
     */
    public void updateDisplayName(String uid, String displayName, UserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName);

        DBFireStore.collection(collectionName)
                .document(uid)
                .update(updates)
                .addOnSuccessListener(success -> {
                    Log.d(TAG, "Display name updated successfully");
                    getUser(uid, callback);
                })
                .addOnFailureListener(fail -> {
                    Log.e(TAG, "Error updating display name: " + fail.getMessage());
                    callback.onFailure("Error updating display name: " + fail.getMessage());
                });
    }

    /**
     * Check if user exists in database
     */
    public void userExists(String uid, UserCallback callback) {
        DBFireStore.collection(collectionName)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        getUser(uid, callback);
                    } else {
                        callback.onFailure("User does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onFailure("Error checking user existence: " + e.getMessage());
                });
    }

    // Add these methods to your UserController class

    /**
     * Interface for callbacks when fetching multiple users
     */
    public interface MultipleUsersCallback {
        void onSuccess(List<User> users);
        void onFailure(String error);
        void onProgress(int current, int total); // Optional: for progress tracking
    }

    /**
     * Get multiple users by their UIDs
     */
    public void getMultipleUsers(List<String> userIds, MultipleUsersCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<User> users = new ArrayList<>();
        int[] completedRequests = {0}; // Using array to make it effectively final
        int totalRequests = userIds.size();

        for (String uid : userIds) {
            getUser(uid, new UserCallback() {
                @Override
                public void onSuccess(User user) {
                    synchronized (users) {
                        users.add(user);
                        completedRequests[0]++;

                        // Call progress callback
                        callback.onProgress(completedRequests[0], totalRequests);

                        // If all requests completed, return results
                        if (completedRequests[0] == totalRequests) {
                            callback.onSuccess(users);
                        }
                    }
                }

                @Override
                public void onFailure(String error) {
                    synchronized (users) {
                        completedRequests[0]++;
                        Log.e(TAG, "Failed to get user " + uid + ": " + error);

                        // Call progress callback
                        callback.onProgress(completedRequests[0], totalRequests);

                        // Continue even if some users fail to load
                        if (completedRequests[0] == totalRequests) {
                            callback.onSuccess(users);
                        }
                    }
                }
            });
        }
    }

    /**
     * Get multiple users by their UIDs using batch query (more efficient for large lists)
     */
    public void getMultipleUsersBatch(List<String> userIds, MultipleUsersCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        // Firestore 'in' queries are limited to 10 items, so we need to batch them
        final int BATCH_SIZE = 10;
        List<User> allUsers = new ArrayList<>();
        int[] completedBatches = {0};
        int totalBatches = (int) Math.ceil((double) userIds.size() / BATCH_SIZE);

        for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, userIds.size());
            List<String> batch = userIds.subList(i, endIndex);

            DBFireStore.collection(collectionName)
                    .whereIn("uid", batch)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        synchronized (allUsers) {
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                try {
                                    User user = new User();
                                    user.setUid(doc.getString("uid"));
                                    user.setEmail(doc.getString("email"));
                                    user.setDisplayName(doc.getString("displayName"));
                                    user.setProfilePicturePath(doc.getString("profilePicturePath"));
                                    user.setProfilePictureUrl(doc.getString("profilePictureUrl"));
                                    allUsers.add(user);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing user data for document: " + doc.getId(), e);
                                }
                            }

                            completedBatches[0]++;
                            callback.onProgress(completedBatches[0], totalBatches);

                            if (completedBatches[0] == totalBatches) {
                                callback.onSuccess(allUsers);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (allUsers) {
                            completedBatches[0]++;
                            Log.e(TAG, "Error getting batch of users: " + e.getMessage());

                            callback.onProgress(completedBatches[0], totalBatches);

                            if (completedBatches[0] == totalBatches) {
                                callback.onSuccess(allUsers);
                            }
                        }
                    });
        }
    }

    /**
     * Search users by display name or email (useful for finding users to follow)
     */
    public void searchUsers(String searchQuery, MultipleUsersCallback callback) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        String query = searchQuery.toLowerCase().trim();

        DBFireStore.collection(collectionName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> matchingUsers = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        try {
                            String email = doc.getString("email");
                            String displayName = doc.getString("displayName");

                            // Check if query matches email or display name
                            boolean emailMatch = email != null && email.toLowerCase().contains(query);
                            boolean nameMatch = displayName != null && displayName.toLowerCase().contains(query);

                            if (emailMatch || nameMatch) {
                                User user = new User();
                                user.setUid(doc.getString("uid"));
                                user.setEmail(email);
                                user.setDisplayName(displayName);
                                user.setProfilePicturePath(doc.getString("profilePicturePath"));
                                user.setProfilePictureUrl(doc.getString("profilePictureUrl"));
                                matchingUsers.add(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user data during search: " + e.getMessage());
                        }
                    }

                    callback.onSuccess(matchingUsers);
                })
                .addOnFailureListener(e -> {
                    callback.onFailure("Error searching users: " + e.getMessage());
                });
    }
}
