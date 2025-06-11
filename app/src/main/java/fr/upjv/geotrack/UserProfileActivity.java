package fr.upjv.geotrack;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import fr.upjv.geotrack.adapters.JourneyPostAdapter;
import fr.upjv.geotrack.models.Journey;

public class UserProfileActivity extends AppCompatActivity {
    private ImageView imageProfile;
    private TextView textUsername;
    private Button buttonFollow;
    private RecyclerView recyclerUserPosts;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String userId;
    private boolean isFollowing = false;

    private JourneyPostAdapter postAdapter;
    private List<Journey> userPosts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        // 1) Récupérer l’UID passé par intent
        userId = getIntent().getStringExtra("USER_ID");
        if (userId == null) {
            Toast.makeText(this, "Utilisateur introuvable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2) Lier les vues
        imageProfile      = findViewById(R.id.image_profile);
        textUsername      = findViewById(R.id.text_username);
        buttonFollow      = findViewById(R.id.button_follow);
        recyclerUserPosts = findViewById(R.id.recycler_user_posts);

        // 3) Charger profil
        loadUserInfo();

        // 4) Bouton Follow
        setupFollowButton();

        // 5) RecyclerView posts
        setupRecycler();

        // 6) Charger ses posts
        loadUserPosts();
    }

    private void loadUserInfo() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("username");
                    String url  = doc.getString("avatarUrl");
                    textUsername.setText(name != null ? name : "Inconnu");
                    if (url != null && !url.isEmpty()) {
                        Glide.with(this)
                                .load(url)
                                .placeholder(R.drawable.ic_profile_modern)
                                .error(R.drawable.ic_profile_modern)
                                .into(imageProfile);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur chargement profil", Toast.LENGTH_SHORT).show()
                );
    }

    private void setupFollowButton() {
        buttonFollow.setText(isFollowing ? "Unfollow" : "Follow");
        buttonFollow.setOnClickListener(v -> {
            isFollowing = !isFollowing;
            buttonFollow.setText(isFollowing ? "Unfollow" : "Follow");
            Toast.makeText(this,
                    isFollowing ? "Vous suivez maintenant" : "Vous ne suivez plus",
                    Toast.LENGTH_SHORT).show();
            // TODO: persister en Firestore la relation follow/unfollow
        });
    }

    private void setupRecycler() {
        postAdapter = new JourneyPostAdapter(
                userPosts,
                this,
                new JourneyPostAdapter.OnJourneyClickListener() {
                    @Override public void onJourneyClick(Journey j) {
                        JourneyDetailActivity.startActivity(UserProfileActivity.this, j);
                    }
                    @Override public void onLikeClick(Journey j, int pos) { /* … */ }
                    @Override public void onUserProfileClick(String ignored) { /* pas ici */ }
                }
        );
        recyclerUserPosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerUserPosts.setAdapter(postAdapter);
    }

    private void loadUserPosts() {
        db.collection("journey")
                .whereEqualTo("userId", userId)
                .orderBy("start", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    userPosts.clear();
                    for (QueryDocumentSnapshot d : qs) {
                        Journey j = d.toObject(Journey.class);
                        if (j != null) userPosts.add(j);
                    }
                    postAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur chargement posts", Toast.LENGTH_SHORT).show()
                );
    }
}
