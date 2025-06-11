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
import fr.upjv.geotrack.UserProfileActivity;
import fr.upjv.geotrack.R;
import fr.upjv.geotrack.adapters.JourneyPostAdapter;
import fr.upjv.geotrack.models.Journey;

public class ThreadFragment extends Fragment {

    private ImageView hamburgerMenu, appLogo, searchIcon, profileIcon;
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

        initializeFirebase();
        initializeHeader(view);
        initializeRecyclerView(view);
        loadUserProfileImage();
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
        hamburgerMenu = view.findViewById(R.id.hamburger_menu);
        appLogo        = view.findViewById(R.id.app_logo);
        searchIcon     = view.findViewById(R.id.search_icon);
        profileIcon    = view.findViewById(R.id.profile_icon);

        setupHeaderClickListeners();
    }

    private void initializeRecyclerView(View view) {
        journeyRecyclerView = view.findViewById(R.id.thread_recycler_view);
        journeyList         = new ArrayList<>();

        journeyPostAdapter = new JourneyPostAdapter(
                journeyList,
                getContext(),
                new JourneyPostAdapter.OnJourneyClickListener() {
                    @Override
                    public void onJourneyClick(Journey journey) {
                        Log.d(TAG, "Journey clicked: " + journey.getName());
                        JourneyDetailActivity.startActivity(getContext(), journey);
                    }

                    @Override
                    public void onLikeClick(Journey journey, int position) {
                        // existing like handling...
                    }

                    @Override
                    public void onUserProfileClick(String userUUID) {
                        // Navigate to UserProfileActivity
                        Intent intent = new Intent(getContext(), UserProfileActivity.class);
                        intent.putExtra("USER_ID", userUUID);
                        startActivity(intent);
                    }
                }
        );

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
                        if (isAdded()) {
                            Glide.with(this)
                                    .load(uri)
                                    .transform(new MultiTransformation<>(new CenterCrop(), new CircleCrop()))
                                    .placeholder(R.drawable.ic_profile_modern)
                                    .error(R.drawable.ic_profile_modern)
                                    .into(profileIcon);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            Glide.with(this)
                                    .load(R.drawable.ic_profile_modern)
                                    .into(profileIcon);
                        }
                    });
        } else if (profileIcon != null) {
            profileIcon.setImageResource(R.drawable.ic_profile_modern);
        }
    }

    private void loadRandomJourneys() {
        db.collection("journey")
                .orderBy("start", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && isAdded()) {
                        journeyList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Journey journey = document.toObject(Journey.class);
                            if (journey != null && journey.isValid()) {
                                journeyList.add(journey);
                            }
                        }
                        journeyPostAdapter.notifyDataSetChanged();
                        if (journeyList.isEmpty()) {
                            Toast.makeText(getContext(), "No journeys found", Toast.LENGTH_SHORT).show();
                        }
                    } else if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to load journeys", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Error loading journeys: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupHeaderClickListeners() {
        if (hamburgerMenu != null) {
            hamburgerMenu.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Menu clicked", Toast.LENGTH_SHORT).show()
            );
        }
        if (appLogo != null) {
            appLogo.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Logo clicked", Toast.LENGTH_SHORT).show()
            );
        }
        if (searchIcon != null) {
            searchIcon.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Search clicked", Toast.LENGTH_SHORT).show()
            );
        }
        if (profileIcon != null) {
            profileIcon.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Profile header clicked", Toast.LENGTH_SHORT).show()
            );
        }
    }
}
