package fr.upjv.geotrack;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.adapters.JourneyAdapter;
import fr.upjv.geotrack.controllers.FollowController;
import fr.upjv.geotrack.controllers.JourneyController;
import fr.upjv.geotrack.models.Journey;

public class UserProfileActivity extends AppCompatActivity implements JourneyAdapter.OnJourneyActionListener {

    private static final String TAG = "UserProfileActivity";

    // Views
    private ImageView imageProfile;
    private TextView textUsername, emailAddress, memberSince, journeyCount;
    private Button buttonFollow;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView journeyRecyclerView;

    // Firebase & Controllers
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FollowController followController;
    private JourneyController journeyController;
    private FirebaseUser currentUser;

    // Adapter
    private JourneyAdapter journeyAdapter;

    // Data
    private String userId;
    private boolean isFollowing;
    private boolean isOwnProfile;
    private List<Journey> userJourneys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        userId = getIntent().getStringExtra("USER_ID");
        if (userId == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isOwnProfile = currentUser != null && userId.equals(currentUser.getUid());

        initializeViews();
        setupRecyclerView();

        followController = new FollowController();
        journeyController = new JourneyController();
        userJourneys = new ArrayList<>();

        loadUserProfile();
        setupSwipeRefresh();

        if (!isOwnProfile) {
            setupFollowButton();
        } else {
            buttonFollow.setVisibility(android.view.View.GONE);
        }
    }

    private void initializeViews() {
        imageProfile = findViewById(R.id.image_profile);
        textUsername = findViewById(R.id.text_username);
        emailAddress = findViewById(R.id.email_address);
        memberSince = findViewById(R.id.member_since);
        journeyCount = findViewById(R.id.journey_count);
        buttonFollow = findViewById(R.id.button_follow);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        journeyRecyclerView = findViewById(R.id.thread_recycler_view);

        // Setup back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        // Initialize adapter with empty list and disable edit/delete features
        journeyAdapter = new JourneyAdapter(new ArrayList<>(), this ); // Added false parameter to disable edit/delete

        // Setup RecyclerView
        journeyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        journeyRecyclerView.setAdapter(journeyAdapter);
        journeyRecyclerView.setHasFixedSize(true);

        // Add some spacing between items
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        journeyRecyclerView.addItemDecoration(new androidx.recyclerview.widget.DividerItemDecoration(this, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL));
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::loadUserProfile);
        swipeRefreshLayout.setColorSchemeResources(
                R.color.primary_color,
                R.color.colorAccent
        );
    }

    private void loadUserProfile() {
        loadUserBasicInfo();
        loadUserJourneys();
        if (!isOwnProfile) {
            checkFollowingStatus();
        }
    }

    private void hideRefreshIndicator() {
        if (swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void loadUserBasicInfo() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(this::onUserBasicInfoLoaded)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user basic info", e);
                    Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show();
                    hideRefreshIndicator();
                });
    }

    private void onUserBasicInfoLoaded(@NonNull DocumentSnapshot doc) {
        if (!doc.exists()) {
            Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String displayName = doc.getString("displayName");
        String email = doc.getString("email");
        Date creationDate = doc.getDate("createdAt");

        // Set username
        String nameToDisplay = displayName != null && !displayName.isEmpty()
                ? displayName : email != null ? email : "Unknown User";
        textUsername.setText(nameToDisplay);

        // Set email
        if (email != null) {
            emailAddress.setText(email);
        }

        // Set member since
        if (creationDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            memberSince.setText("Member since " + dateFormat.format(creationDate));
        }

        loadProfilePicture(doc.getString("profilePictureUrl"));
    }

    private void loadProfilePicture(String profilePictureUrl) {
        RequestOptions requestOptions = new RequestOptions()
                .transform(new CircleCrop())
                .placeholder(R.drawable.ic_profile_modern)
                .error(R.drawable.ic_profile_modern);

        if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
            Glide.with(this).load(profilePictureUrl).apply(requestOptions).into(imageProfile);
        } else {
            imageProfile.setImageResource(R.drawable.ic_profile_modern);
        }
    }

    private void loadUserJourneys() {
        Log.d(TAG, "Loading journeys for user: " + userId);

        journeyController.getUserJourneys(userId)
                .addOnSuccessListener(this::onUserJourneysLoaded)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user journeys", e);
                    Toast.makeText(this, "Error loading journeys", Toast.LENGTH_SHORT).show();
                    journeyCount.setText("0 journeys");
                    hideRefreshIndicator();
                });
    }

    private void onUserJourneysLoaded(@NonNull QuerySnapshot querySnapshot) {
        userJourneys.clear();

        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            try {
                Journey journey = createJourneyFromDocument(doc);
                if (journey != null) {
                    userJourneys.add(journey);
                    Log.d(TAG, "Loaded journey: " + journey.getName());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing journey document: " + doc.getId(), e);
            }
        }

        // Update journey count
        int count = userJourneys.size();
        journeyCount.setText(count == 1 ? count + " journey" : count + " journeys");

        // Update RecyclerView adapter with loaded journeys
        journeyAdapter.updateJourneys(userJourneys);

        Log.d(TAG, "Successfully loaded " + count + " journeys for user");
        hideRefreshIndicator();
    }

    private Journey createJourneyFromDocument(DocumentSnapshot doc) {
        try {
            String id = doc.getString("id");
            String userUUID = doc.getString("userUUID");
            String name = doc.getString("name");
            String description = doc.getString("description");
            Date start = doc.getDate("start");
            Date end = doc.getDate("end");
            List<String> imagePaths = (List<String>) doc.get("imagePaths");
            String thumbnailPath = doc.getString("thumbnailPath");

            if (id == null || userUUID == null || name == null || start == null || end == null) {
                Log.w(TAG, "Journey document missing required fields: " + doc.getId());
                return null;
            }

            return new Journey(id, userUUID, start, end, name, description, imagePaths, thumbnailPath);
        } catch (Exception e) {
            Log.e(TAG, "Error creating Journey from document: " + doc.getId(), e);
            return null;
        }
    }

    private void checkFollowingStatus() {
        followController.isFollowing(userId)
                .addOnSuccessListener(following -> {
                    isFollowing = following;
                    buttonFollow.setText(isFollowing ? R.string.unfollow : R.string.follow);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking following status", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupFollowButton() {
        buttonFollow.setOnClickListener(v -> {
            buttonFollow.setEnabled(false);

            if (isFollowing) {
                followController.unfollow(userId)
                        .addOnSuccessListener(aVoid -> {
                            isFollowing = false;
                            buttonFollow.setText(R.string.follow);
                            buttonFollow.setEnabled(true);
                            Toast.makeText(this, "You are no longer following this user", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error unfollowing user", e);
                            buttonFollow.setEnabled(true);
                            Toast.makeText(this, "Error unfollowing user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            } else {
                followController.follow(userId)
                        .addOnSuccessListener(docRef -> {
                            isFollowing = true;
                            buttonFollow.setText(R.string.unfollow);
                            buttonFollow.setEnabled(true);
                            Toast.makeText(this, "You are now following this user", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error following user", e);
                            buttonFollow.setEnabled(true);
                            Toast.makeText(this, "Error following user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
            }
        });
    }

    // Update this method in UserProfileActivity
    @Override
    public void onJourneyClick(Journey journey) {
        // Handle journey click - navigate to journey detail view
        Log.d(TAG, "Journey clicked: " + journey.getName());

        if (journey == null) {
            Toast.makeText(this, "Journey data not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (journey.getId() == null || journey.getName() == null ||
                journey.getStart() == null || journey.getEnd() == null) {
            Toast.makeText(this, "Incomplete journey data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use the static method from JourneyDetailActivity
        JourneyDetailActivity.startActivity(this, journey);
    }

    @Override
    public void onEditJourney(Journey journey) {
        // Edit functionality is now disabled - this method should not be called
        // But keeping it for interface compliance
        Log.d(TAG, "Edit journey feature is disabled");
    }

    @Override
    public void onDeleteJourney(Journey journey) {
        // Delete functionality is now disabled - this method should not be called
        // But keeping it for interface compliance
        Log.d(TAG, "Delete journey feature is disabled");
    }

    // Removed the deleteJourney method since it's no longer needed

    // Getter methods for accessing loaded data (useful for future features)
    public List<Journey> getUserJourneys() {
        return new ArrayList<>(userJourneys);
    }

    public boolean isOwnProfile() {
        return isOwnProfile;
    }

    public String getUserId() {
        return userId;
    }

    public static void startActivity(Context context, String userUUID) {
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra("USER_ID", userUUID);
        context.startActivity(intent);
    }
}