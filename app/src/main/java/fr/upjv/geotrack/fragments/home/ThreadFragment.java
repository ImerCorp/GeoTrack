package fr.upjv.geotrack.fragments.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import fr.upjv.geotrack.R;

public class ThreadFragment extends Fragment {

    private ImageView hamburgerMenu;
    private ImageView appLogo;
    private ImageView searchIcon;
    private ImageView profileIcon;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_thread, container, false);

        // Initialize header components
        initializeHeader(view);

        return view;
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

    private void setupHeaderClickListeners() {
        // Hamburger menu click
        if (hamburgerMenu != null) {
            hamburgerMenu.setOnClickListener(v -> {
                // TODO: Open navigation drawer or side menu
                Toast.makeText(getContext(), "Menu clicked", Toast.LENGTH_SHORT).show();
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

            // Create new ProfileFragment instance
            ProfileFragment profileFragment = new ProfileFragment();

            // Replace current fragment with ProfileFragment
            // Assuming your main container ID is something like R.id.fragment_container
            // You may need to adjust this based on your MainActivity's layout
            transaction.replace(R.id.fragment_container, profileFragment);

            // Add to back stack so user can navigate back
            transaction.addToBackStack("ThreadFragment");

            // Add transition animation (optional)
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

            // Commit the transaction
            transaction.commit();

        } catch (Exception e) {
            // Fallback: Show toast if navigation fails
            Toast.makeText(getContext(), "Opening Profile", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}