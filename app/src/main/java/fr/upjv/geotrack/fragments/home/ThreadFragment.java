package fr.upjv.geotrack.fragments.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

import fr.upjv.geotrack.JourneyDetailActivity;
import fr.upjv.geotrack.R;
import fr.upjv.geotrack.SettingsActivity;
import fr.upjv.geotrack.adapters.JourneyPostAdapter;
import fr.upjv.geotrack.models.Journey;

public class ThreadFragment extends Fragment {

    private ImageView hamburgerMenu;
    private ImageView appLogo;
    private ImageView searchIcon;
    private ImageView profileIcon;
    private RecyclerView journeyRecyclerView;
    private JourneyPostAdapter journeyPostAdapter;
    private List<Journey> journeyList;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore db;
    private static final String TAG = "ThreadFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_thread, container, false);

        // Initialize Firebase
        initializeFirebase();

        // Initialize header components
        initializeHeader(view);

        // Initialize RecyclerView for journey posts
        initializeRecyclerView(view);

        // Load user profile image
        loadUserProfileImage();

        // Load random journeys
        loadRandomJourneys();

        return view;
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        db = FirebaseFirestore.getInstance();
    }

    private void initializeHeader(View view) {
        // Find header views
        hamburgerMenu = view.findViewById(R.id.hamburger_menu);
        appLogo = view.findViewById(R.id.app_logo);
        searchIcon = view.findViewById(R.id.search_icon);
        profileIcon = view.findViewById(R.id.profile_icon);

        // Set click listeners
        setupHeaderClickListeners();
    }

    private void initializeRecyclerView(View view) {
        journeyRecyclerView = view.findViewById(R.id.thread_recycler_view);
        journeyList = new ArrayList<>();

        journeyPostAdapter = new JourneyPostAdapter(journeyList, getContext(), new JourneyPostAdapter.OnJourneyClickListener() {
            @Override
            public void onJourneyClick(Journey journey) {
                Log.d(TAG, "Journey clicked: " + journey.getName());
                // Navigate to JourneyDetailActivity
                JourneyDetailActivity.startActivity(getContext(), journey);
            }

            @Override
            public void onLikeClick(Journey journey, int position) {
                // Handle like button click
                handleLikeClick(journey, position);
            }

            @Override
            public void onUserProfileClick(String userUUID) {
                // Handle user profile click
                Toast.makeText(getContext(), "Opening user profile", Toast.LENGTH_SHORT).show();
                // TODO: Navigate to user profile
            }
        });

        journeyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        journeyRecyclerView.setAdapter(journeyPostAdapter);
    }

    private void loadUserProfileImage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && profileIcon != null) {
            String userId = currentUser.getUid();
            StorageReference profileImageRef = storageRef.child("users/" + userId + "/profile.jpg");

            profileImageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        if (getContext() != null && isAdded()) {
                            Glide.with(this)
                                    .load(uri)
                                    .transform(
                                            new MultiTransformation<>(
                                                    new CenterCrop(),
                                                    new CircleCrop()
                                            )
                                    )
                                    .placeholder(R.drawable.ic_profile_modern)
                                    .error(R.drawable.ic_profile_modern)
                                    .into(profileIcon);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        if (getContext() != null && isAdded()) {
                            Glide.with(this)
                                    .load(R.drawable.ic_profile_modern)
                                    .into(profileIcon);
                        }
                    });
        } else {
            if (profileIcon != null) {
                profileIcon.setImageResource(R.drawable.ic_profile_modern);
            }
        }
    }

    private void loadRandomJourneys() {
        db.collection("journey")
                .orderBy("start", Query.Direction.DESCENDING)
                .limit(20) // Limit to 20 recent journeys
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && getContext() != null) {
                        journeyList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Journey journey = document.toObject(Journey.class);
                                if (journey != null && journey.isValid()) {
                                    journeyList.add(journey);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (journeyPostAdapter != null) {
                            journeyPostAdapter.notifyDataSetChanged();
                        }

                        if (journeyList.isEmpty()) {
                            // Show empty state
                            Toast.makeText(getContext(), "No journeys found", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Failed to load journeys", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Error loading journeys: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleLikeClick(Journey journey, int position) {
        // TODO: Implement like functionality
        // This would typically involve updating the journey's like count in Firestore
        // and updating the UI accordingly
        Toast.makeText(getContext(), "Liked journey: " + journey.getName(), Toast.LENGTH_SHORT).show();

        // Example implementation:
        // updateJourneyLikes(journey.getId(), true);
    }

    private void setupHeaderClickListeners() {
        // Hamburger menu click
        if (hamburgerMenu != null) {
            hamburgerMenu.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), SettingsActivity.class);
                startActivity(intent);
            });
        }

        // App logo click
        if (appLogo != null) {
            appLogo.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Logo clicked", Toast.LENGTH_SHORT).show();
            });
        }

        // Search icon click
        if (searchIcon != null) {
            searchIcon.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Search clicked", Toast.LENGTH_SHORT).show();
            });
        }

        // Profile icon click - Navigate to ProfileFragment
        if (profileIcon != null) {
            profileIcon.setOnClickListener(v -> {
                navigateToProfileFragment();
            });
        }
    }

    private void navigateToProfileFragment() {
        try {
            FragmentManager fragmentManager = getParentFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            ProfileFragment profileFragment = new ProfileFragment();
            transaction.replace(R.id.fragment_container, profileFragment);
            transaction.addToBackStack("ThreadFragment");
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.commit();

        } catch (Exception e) {
            Toast.makeText(getContext(), "Opening Profile", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // Method to refresh profile image (call this when user updates their profile)
    public void refreshProfileImage() {
        loadUserProfileImage();
    }

    // Method to refresh journey posts
    public void refreshJourneyPosts() {
        loadRandomJourneys();
    }
}