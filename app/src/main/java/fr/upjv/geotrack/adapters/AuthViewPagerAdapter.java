package fr.upjv.geotrack;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import fr.upjv.geotrack.fragments.SignInFragment;
import fr.upjv.geotrack.fragments.SignUpFragment;

public class AuthViewPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_PAGES = 2;

    public AuthViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new SignInFragment();
        } else {
            return new SignUpFragment();
        }
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
}