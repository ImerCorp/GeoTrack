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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.List;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.User;

public class JourneyPostAdapter extends RecyclerView.Adapter<JourneyPostAdapter.JourneyPostViewHolder> {

    private List<Journey> journeyList;
    private Context context;
    private OnJourneyClickListener listener;
    private FirebaseStorage storage;
    private FirebaseFirestore db;

    public interface OnJourneyClickListener {
        void onJourneyClick(Journey journey);
        void onLikeClick(Journey journey, int position);
        void onUserProfileClick(String userUUID);
    }

    public JourneyPostAdapter(List<Journey> journeyList, Context context, OnJourneyClickListener listener) {
        this.journeyList = journeyList;
        this.context = context;
        this.listener = listener;
        this.storage = FirebaseStorage.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public JourneyPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_journey_post, parent, false);
        return new JourneyPostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JourneyPostViewHolder holder, int position) {
        Journey journey = journeyList.get(position);
        holder.bind(journey, position);
    }

    @Override
    public int getItemCount() {
        return journeyList.size();
    }

    public class JourneyPostViewHolder extends RecyclerView.ViewHolder {
        private ImageView userProfileImage;
        private TextView userName;
        private TextView postTime;
        private TextView journeyTitle;
        private TextView journeyDescription;
        private TextView journeyDuration;
        private TextView journeyStatus;
        private ImageView journeyImage;
        private ImageView likeButton;
        private TextView likeCount;
        private View cardView;

        public JourneyPostViewHolder(@NonNull View itemView) {
            super(itemView);

            userProfileImage = itemView.findViewById(R.id.user_profile_image);
            userName = itemView.findViewById(R.id.user_name);
            postTime = itemView.findViewById(R.id.post_time);
            journeyTitle = itemView.findViewById(R.id.journey_title);
            journeyDescription = itemView.findViewById(R.id.journey_description);
            journeyDuration = itemView.findViewById(R.id.journey_duration);
            journeyStatus = itemView.findViewById(R.id.journey_status);
            journeyImage = itemView.findViewById(R.id.journey_image);
            likeButton = itemView.findViewById(R.id.like_button);
            likeCount = itemView.findViewById(R.id.like_count);
            cardView = itemView.findViewById(R.id.card_view);
        }

        public void bind(Journey journey, int position) {
            // Set journey details
            journeyTitle.setText(journey.getName());

            // Set description or hide if empty
            if (journey.hasDescription()) {
                journeyDescription.setText(journey.getTruncatedDescription(100));
                journeyDescription.setVisibility(View.VISIBLE);
            } else {
                journeyDescription.setVisibility(View.GONE);
            }

            // Set duration and status
            long duration = journey.getDurationInDays();
            if (duration > 0) {
                journeyDuration.setText(duration + " day" + (duration > 1 ? "s" : ""));
            } else {
                journeyDuration.setText(journey.getDurationInHours() + " hours");
            }

            journeyStatus.setText(journey.getStatus());

            // Set status color based on journey status
            int statusColor;
            switch (journey.getStatus()) {
                case "Active":
                    statusColor = context.getResources().getColor(R.color.colorAccent);
                    break;
                case "Upcoming":
                    statusColor = context.getResources().getColor(R.color.colorPrimary);
                    break;
                case "Completed":
                    statusColor = context.getResources().getColor(R.color.slider_indicator);
                    break;
                default:
                    statusColor = context.getResources().getColor(R.color.black);
                    break;
            }
            journeyStatus.setTextColor(statusColor);

            // Set post time (for now, using journey start date)
            postTime.setText(journey.getFormattedStartDate());

            // Load journey image
            loadJourneyImage(journey);

            // Load user profile data
            loadUserProfile(journey.getUserUUID());

            // Set click listeners
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onJourneyClick(journey);
                }
            });

            likeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLikeClick(journey, position);
                }
            });

            userProfileImage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserProfileClick(journey.getUserUUID());
                }
            });

            userName.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserProfileClick(journey.getUserUUID());
                }
            });

            // Set initial like count (placeholder for now)
            likeCount.setText("0");
        }

        private void loadJourneyImage(Journey journey) {
            if (journey.hasImages() && journey.getThumbnailPath() != null) {
                StorageReference imageRef = storage.getReference().child(journey.getThumbnailPath());

                imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            Glide.with(context)
                                    .load(uri)
                                    .transform(new MultiTransformation<>(
                                            new CenterCrop(),
                                            new RoundedCorners(16)
                                    ))
                                    .placeholder(R.drawable.ic_journey_placeholder)
                                    .error(R.drawable.ic_journey_placeholder)
                                    .into(journeyImage);
                        })
                        .addOnFailureListener(exception -> {
                            // Load placeholder image
                            journeyImage.setImageResource(R.drawable.ic_journey_placeholder);
                        });
            } else {
                // No image available, show placeholder
                journeyImage.setImageResource(R.drawable.ic_journey_placeholder);
            }
        }

        private void loadUserProfile(String userUUID) {
            // Load user data from Firestore
            db.collection("users").document(userUUID).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null) {
                                // Set user name
                                userName.setText(user.getDisplayNameOrEmail());

                                // Load user profile image
                                loadUserProfileImage(user);
                            } else {
                                setDefaultUserInfo();
                            }
                        } else {
                            setDefaultUserInfo();
                        }
                    })
                    .addOnFailureListener(e -> {
                        setDefaultUserInfo();
                    });
        }

        private void loadUserProfileImage(User user) {
            if (user.hasProfilePicture()) {
                StorageReference profileRef = storage.getReference().child(user.getProfilePicturePath());

                profileRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            Glide.with(context)
                                    .load(uri)
                                    .transform(new MultiTransformation<>(
                                            new CenterCrop(),
                                            new CircleCrop()
                                    ))
                                    .placeholder(R.drawable.ic_profile_modern)
                                    .error(R.drawable.ic_profile_modern)
                                    .into(userProfileImage);
                        })
                        .addOnFailureListener(exception -> {
                            userProfileImage.setImageResource(R.drawable.ic_profile_modern);
                        });
            } else {
                userProfileImage.setImageResource(R.drawable.ic_profile_modern);
            }
        }

        private void setDefaultUserInfo() {
            userName.setText("Anonymous User");
            userProfileImage.setImageResource(R.drawable.ic_profile_modern);
        }
    }
}