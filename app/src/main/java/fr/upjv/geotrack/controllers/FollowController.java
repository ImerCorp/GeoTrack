package fr.upjv.geotrack.controllers;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import fr.upjv.geotrack.models.Follow;

public class FollowController {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

    private CollectionReference followsRef() {
        return db.collection("follows");
    }

    /** Crée une relation “currentUser suit targetUser” */
    public Task<DocumentReference> follow(String targetUserId) {
        Follow follow = new Follow(
                /* id */      null,
                /* follower */currentUserId,
                /* following*/targetUserId,
                /* timestamp*/new Date()
        );
        // id auto généré
        return followsRef().add(follow.toMap());
    }

    /** Supprime la relation si elle existe */
    public Task<Void> unfollow(String targetUserId) {
        // On cherche le doc où followerId==currentUserId && followingId==targetUserId
        return followsRef()
                .whereEqualTo("followerId", currentUserId)
                .whereEqualTo("followingId", targetUserId)
                .get()
                .continueWithTask(task -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : task.getResult()) {
                        batch.delete(doc.getReference());
                    }
                    return batch.commit();
                });
    }

    /** Vérifie si on suit déjà targetUserId */
    public Task<Boolean> isFollowing(String targetUserId) {
        return followsRef()
                .whereEqualTo("followerId", currentUserId)
                .whereEqualTo("followingId", targetUserId)
                .get()
                .continueWith(task -> !task.getResult().isEmpty());
    }

    /** Récupère le nombre de followers de targetUserId */
    public Task<Integer> countFollowers(String targetUserId) {
        return followsRef()
                .whereEqualTo("followingId", targetUserId)
                .get()
                .continueWith(task -> task.getResult().size());
    }

    // Add these methods to your FollowController class

    /**
     * Interface for callbacks when fetching following users
     */
    public interface FollowingUsersCallback {
        void onSuccess(List<String> followingUserIds);
        void onFailure(String error);
    }

    /**
     * Get list of user IDs that the current user follows
     */
    public void getFollowingUserIds(FollowingUsersCallback callback) {
        followsRef()
                .whereEqualTo("followerId", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> followingIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String followingId = doc.getString("followingId");
                        if (followingId != null) {
                            followingIds.add(followingId);
                        }
                    }
                    callback.onSuccess(followingIds);
                })
                .addOnFailureListener(e -> {
                    callback.onFailure("Error fetching following users: " + e.getMessage());
                });
    }

    /**
     * Get list of user IDs that follow the current user (followers)
     */
    public void getFollowerUserIds(FollowingUsersCallback callback) {
        followsRef()
                .whereEqualTo("followingId", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> followerIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String followerId = doc.getString("followerId");
                        if (followerId != null) {
                            followerIds.add(followerId);
                        }
                    }
                    callback.onSuccess(followerIds);
                })
                .addOnFailureListener(e -> {
                    callback.onFailure("Error fetching followers: " + e.getMessage());
                });
    }

    /**
     * Get count of users the current user follows
     */
    public Task<Integer> countFollowing() {
        return followsRef()
                .whereEqualTo("followerId", currentUserId)
                .get()
                .continueWith(task -> task.getResult().size());
    }
}
