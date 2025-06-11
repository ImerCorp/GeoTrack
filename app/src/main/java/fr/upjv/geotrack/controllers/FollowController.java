package fr.upjv.geotrack.controllers;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.Date;
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
}
