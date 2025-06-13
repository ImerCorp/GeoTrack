package fr.upjv.geotrack.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.List;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.User;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    private List<User> userList;
    private Context context;
    private OnUserClickListener onUserClickListener;
    private FirebaseStorage storage;
    private StorageReference storageRef; // Reference to the root of Firebase Storage

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserSearchAdapter(List<User> userList, Context context, OnUserClickListener listener) {
        this.userList = userList;
        this.context = context;
        this.onUserClickListener = listener;
        this.storage = FirebaseStorage.getInstance();
        this.storageRef = storage.getReference(); // Initialize storageRef
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public class UserViewHolder extends RecyclerView.ViewHolder {

        private ImageView profileImage;
        private TextView usernameTextView;
        private TextView displayNameTextView;
        private View itemContainer;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            usernameTextView = itemView.findViewById(R.id.username);
            displayNameTextView = itemView.findViewById(R.id.display_name);
            itemContainer = itemView.findViewById(R.id.item_container);
        }

        public void bind(User user) {
            // Set display name (using getDisplayNameOrEmail for robustness)
            String displayName = user.getDisplayNameOrEmail();
            if (displayName != null && !displayName.isEmpty()) {
                displayNameTextView.setText(displayName);
                displayNameTextView.setVisibility(View.VISIBLE);
            } else {
                displayNameTextView.setVisibility(View.GONE);
            }

            // Set username (using email as a fallback for a unique identifier/handle)
            String userEmail = user.getEmail();
            if (userEmail != null && !userEmail.isEmpty()) {
                // Optionally display only the part before '@' for a cleaner username
                String shortUsername = userEmail.split("@")[0];
                usernameTextView.setText("@" + shortUsername);
                usernameTextView.setVisibility(View.VISIBLE);
            } else {
                usernameTextView.setVisibility(View.GONE);
            }


            // Load profile image
            loadProfileImage(user);

            // Set click listener
            itemContainer.setOnClickListener(v -> {
                if (onUserClickListener != null) {
                    onUserClickListener.onUserClick(user);
                }
            });
        }

        private void loadProfileImage(User user) {
            // Check if a profile picture URL is already cached/available in the User object
            if (user.hasProfilePictureUrl()) {
                Glide.with(context)
                        .load(user.getProfilePictureUrl())
                        .transform(
                                new MultiTransformation<>(
                                        new CenterCrop(),
                                        new CircleCrop()
                                )
                        )
                        .placeholder(R.drawable.ic_profile_modern)
                        .error(R.drawable.ic_profile_modern)
                        .into(profileImage);
            }
            // If no URL, but a path is available, try to get the download URL from Storage
            else if (user.hasProfilePicture()) {
                // Use the profilePicturePath from the User object
                StorageReference profileImageRef = storageRef.child(user.getProfilePicturePath());

                profileImageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            // Update the User object with the URL for future caching
                            user.setProfilePictureUrl(uri.toString());
                            Glide.with(context)
                                    .load(uri)
                                    .transform(
                                            new MultiTransformation<>(
                                                    new CenterCrop(),
                                                    new CircleCrop()
                                            )
                                    )
                                    .placeholder(R.drawable.ic_profile_modern)
                                    .error(R.drawable.ic_profile_modern)
                                    .into(profileImage);
                        })
                        .addOnFailureListener(exception -> {
                            // Load default profile image on failure
                            Glide.with(context)
                                    .load(R.drawable.ic_profile_modern)
                                    .transform(new CircleCrop())
                                    .into(profileImage);
                        });
            }
            // If no profile picture path or URL, load the default image directly
            else {
                Glide.with(context)
                        .load(R.drawable.ic_profile_modern)
                        .transform(new CircleCrop())
                        .into(profileImage);
            }
        }
    }
}
