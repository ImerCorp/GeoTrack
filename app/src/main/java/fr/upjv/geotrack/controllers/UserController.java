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

import java.util.HashMap;
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
}
