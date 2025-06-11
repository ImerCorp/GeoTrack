package fr.upjv.geotrack.fragments.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.SettingsActivity;

public class MapFragment extends Fragment {

    private ImageView hamburgerMenu;
    private ImageView appLogo;
    private ImageView searchIcon;
    private ImageView profileIcon;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private StorageReference storageRef;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // Initialize Firebase
        initializeFirebase();

        // Initialize header components
        initializeHeader(view);

        // Load user profile image
        loadUserProfileImage();

        return view;
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
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
                // TODO: Navigate to home or refresh
                Toast.makeText(getContext(), "Logo clicked", Toast.LENGTH_SHORT).show();
            });
        }

        // Search icon click
        if (searchIcon != null) {
            searchIcon.setOnClickListener(v -> {
                // TODO: Open search functionality
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
            transaction.addToBackStack("MapFragment");
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
}
