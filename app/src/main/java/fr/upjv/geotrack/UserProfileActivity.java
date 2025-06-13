package fr.upjv.geotrack;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import fr.upjv.geotrack.adapters.JourneyPostAdapter;
import fr.upjv.geotrack.controllers.FollowController;
import fr.upjv.geotrack.models.Journey;

public class UserProfileActivity extends AppCompatActivity {

    // Vues
    private ImageView imageProfile;
    private TextView  textUsername;
    private Button    buttonFollow;
    private RecyclerView recyclerUserPosts;

    // Firestore & contrôleurs
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FollowController followController;

    // Données
    private String  userId;        // UID du profil affiché
    private boolean isFollowing;   // état actuel
    private final List<Journey> userPosts = new ArrayList<>();
    private JourneyPostAdapter postAdapter;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        // Récupère l’UID passé depuis l’intent
        userId = getIntent().getStringExtra("USER_ID");
        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Liaison des vues
        imageProfile      = findViewById(R.id.image_profile);
        textUsername      = findViewById(R.id.text_username);
        buttonFollow      = findViewById(R.id.button_follow);
        recyclerUserPosts = findViewById(R.id.recycler_user_posts);

        // Init contrôleur Follow
        followController = new FollowController();

        // Charge les infos du profil + configure le RecyclerView
        loadUserInfo();
        setupRecycler();
        loadUserPosts();

        // Vérifie si l’utilisateur connecté suit déjà ce profil
        followController.isFollowing(userId)
                .addOnSuccessListener(following -> {
                    isFollowing = following;
                    updateFollowButton();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

        // Clic sur le bouton Follow / Unfollow
        buttonFollow.setOnClickListener(v -> {
            if (isFollowing) {
                unfollowUser();
            } else {
                followUser();
            }
        });
    }

    // -------- UI helpers ----------------------------------------------------

    private void updateFollowButton() {
        buttonFollow.setText(isFollowing ? R.string.unfollow : R.string.follow);
    }

    // -------- Chargement Firestore ------------------------------------------

    /**
     * Charge displayName / email / profilePictureUri depuis users/{UID}
     */
    private void loadUserInfo() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(this::onUserInfoLoaded)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur chargement profil", Toast.LENGTH_SHORT).show()
                );
    }

    private void onUserInfoLoaded(@NonNull DocumentSnapshot doc) {
        String displayName       = doc.getString("displayName");
        String profilePictureUri = doc.getString("profilePictureUri");
        String email             = doc.getString("email");

        // Affiche le nom – fallback sur l’email si pas de displayName
        textUsername.setText(displayName != null && !displayName.isEmpty()
                ? displayName
                : email != null ? email : getString(R.string.unknown));

        // Charge l’avatar (ou icône par défaut)
        if (profilePictureUri != null && !profilePictureUri.isEmpty()) {
            Glide.with(this)
                    .load(profilePictureUri)
                    .placeholder(R.drawable.ic_profile_modern)
                    .error(R.drawable.ic_profile_modern)
                    .into(imageProfile);
        } else {
            imageProfile.setImageResource(R.drawable.ic_profile_modern);
        }
    }

    /**
     * Configure le RecyclerView pour la liste des journeys
     */
    private void setupRecycler() {
        postAdapter = new JourneyPostAdapter(
                userPosts,
                this,
                new JourneyPostAdapter.OnJourneyClickListener() {
                    @Override
                    public void onJourneyClick(Journey journey) {
                        JourneyDetailActivity.startActivity(UserProfileActivity.this, journey);
                    }
                    @Override public void onLikeClick(Journey j, int pos) { /* ignore */ }
                    @Override public void onUserProfileClick(String uid) { /* ignore */ }
                }
        );
        recyclerUserPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerUserPosts.setAdapter(postAdapter);
    }

    public static void startActivity(Context context, String userUUID) {
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra("USER_ID", userUUID);  // Note: This matches what you're reading in onCreate()
        context.startActivity(intent);
    }

    /**
     * Charge les journeys où userUUID == userId
     */
    private void loadUserPosts() {
        db.collection("journey")
                .whereEqualTo("userUUID", userId)                 // <-- clé correcte
                .orderBy("start", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    userPosts.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Journey j = doc.toObject(Journey.class);
                        userPosts.add(j);
                    }
                    postAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur chargement posts", Toast.LENGTH_SHORT).show()
                );
    }

    // -------- Follow / Unfollow ---------------------------------------------

    private void followUser() {
        followController.follow(userId)
                .addOnSuccessListener(docRef -> {
                    isFollowing = true;
                    updateFollowButton();
                    Toast.makeText(this, "Vous suivez maintenant cet utilisateur", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur follow : " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void unfollowUser() {
        followController.unfollow(userId)
                .addOnSuccessListener(aVoid -> {
                    isFollowing = false;
                    updateFollowButton();
                    Toast.makeText(this, "Vous ne suivez plus cet utilisateur", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur unfollow : " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
