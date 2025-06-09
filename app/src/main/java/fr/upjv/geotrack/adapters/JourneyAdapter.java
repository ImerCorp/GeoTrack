package fr.upjv.geotrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.Journey;

public class JourneyAdapter extends RecyclerView.Adapter<JourneyAdapter.JourneyViewHolder> {

    private List<Journey> journeys;
    private OnJourneyActionListener listener;
    private FirebaseStorage storage;

    public interface OnJourneyActionListener {
        void onJourneyClick(Journey journey);
        void onEditJourney(Journey journey);
        void onDeleteJourney(Journey journey);
    }

    public JourneyAdapter(List<Journey> journeys, OnJourneyActionListener listener) {
        this.journeys = journeys;
        this.listener = listener;
        this.storage = FirebaseStorage.getInstance();
    }

    @NonNull
    @Override
    public JourneyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey, parent, false);
        return new JourneyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JourneyViewHolder holder, int position) {
        Journey journey = journeys.get(position);
        holder.bind(journey);
    }

    @Override
    public int getItemCount() {
        return journeys.size();
    }

    public void updateJourneys(List<Journey> newJourneys) {
        this.journeys = newJourneys;
        notifyDataSetChanged();
    }

    class JourneyViewHolder extends RecyclerView.ViewHolder implements JourneyImageAdapter.OnImageClickListener {
        private TextView journeyName;
        private TextView journeyDescription;
        private TextView journeyDates;
        private TextView journeyStatus;
        private ViewPager2 imageViewPager;
        private TextView imageCounter;
        private ImageButton menuButton;
        private JourneyImageAdapter imageAdapter;

        public JourneyViewHolder(@NonNull View itemView) {
            super(itemView);
            journeyName = itemView.findViewById(R.id.journey_name);
            journeyDescription = itemView.findViewById(R.id.journey_description);
            journeyDates = itemView.findViewById(R.id.journey_dates);
            journeyStatus = itemView.findViewById(R.id.journey_status);
            imageViewPager = itemView.findViewById(R.id.image_view_pager);
            imageCounter = itemView.findViewById(R.id.image_counter);
            menuButton = itemView.findViewById(R.id.journey_menu_button);
        }

        public void bind(Journey journey) {
            // Set journey name
            journeyName.setText(journey.getName());

            // Set journey description
            if (journey.hasDescription()) {
                journeyDescription.setText(journey.getTruncatedDescription(100));
                journeyDescription.setVisibility(View.VISIBLE);
            } else {
                journeyDescription.setVisibility(View.GONE);
            }

            // Format dates
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            String startDate = dateFormat.format(journey.getStart());
            String endDate = dateFormat.format(journey.getEnd());
            String year = yearFormat.format(journey.getStart());

            journeyDates.setText(startDate + " - " + endDate + ", " + year);

            // Determine status and set color
            Date now = new Date();
            if (journey.getEnd().before(now)) {
                journeyStatus.setText("COMPLETED");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
            } else if (journey.getStart().before(now) && journey.getEnd().after(now)) {
                journeyStatus.setText("IN PROGRESS");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_orange_dark));
            } else {
                journeyStatus.setText("UPCOMING");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_blue_dark));
            }

            // Setup image slider
            setupImageSlider(journey);

            // Set click listener for the entire item
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onJourneyClick(journey);
                }
            });

            // Set up menu button with popup menu
            menuButton.setOnClickListener(v -> {
                if (listener != null) {
                    showPopupMenu(v, journey);
                }
            });
        }

        private void setupImageSlider(Journey journey) {
            if (journey.hasImages()) {
                // Create adapter for images
                imageAdapter = new JourneyImageAdapter(journey.getImagePaths(), this);
                imageViewPager.setAdapter(imageAdapter);

                // Setup image counter
                updateImageCounter(0, journey.getImagePaths().size());
                imageCounter.setVisibility(journey.getImagePaths().size() > 1 ? View.VISIBLE : View.GONE);

                // Add page change callback for counter
                imageViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        updateImageCounter(position, journey.getImagePaths().size());
                    }
                });
            } else {
                // No images - show placeholder
                imageAdapter = new JourneyImageAdapter(null, this);
                imageViewPager.setAdapter(imageAdapter);
                imageCounter.setVisibility(View.GONE);
            }
        }

        private void updateImageCounter(int currentPosition, int totalImages) {
            imageCounter.setText((currentPosition + 1) + "/" + totalImages);
        }

        private void showPopupMenu(View anchor, Journey journey) {
            PopupMenu popup = new PopupMenu(itemView.getContext(), anchor);
            popup.getMenuInflater().inflate(R.menu.journey_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit) {
                    listener.onEditJourney(journey);
                    return true;
                } else if (itemId == R.id.action_delete) {
                    listener.onDeleteJourney(journey);
                    return true;
                }
                return false;
            });

            popup.show();
        }

        @Override
        public void onImageClick(String imagePath, int position) {
            // Handle image click - could open fullscreen gallery
            // For now, just pass the click to the journey click listener
            if (listener != null) {
                listener.onJourneyClick(journeys.get(getAdapterPosition()));
            }
        }
    }

    // Inner adapter class for handling images in ViewPager2
    public static class JourneyImageAdapter extends RecyclerView.Adapter<JourneyImageAdapter.ImageViewHolder> {
        private List<String> imagePaths;
        private OnImageClickListener clickListener;
        private FirebaseStorage storage;

        public interface OnImageClickListener {
            void onImageClick(String imagePath, int position);
        }

        public JourneyImageAdapter(List<String> imagePaths, OnImageClickListener clickListener) {
            this.imagePaths = imagePaths;
            this.clickListener = clickListener;
            this.storage = FirebaseStorage.getInstance();
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Use the correct layout for individual images
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_journey_image, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            if (imagePaths != null && !imagePaths.isEmpty()) {
                holder.bind(imagePaths.get(position), position);
            } else {
                holder.bindPlaceholder();
            }
        }

        @Override
        public int getItemCount() {
            return (imagePaths != null && !imagePaths.isEmpty()) ? imagePaths.size() : 1;
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            private ImageView imageView;

            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.journey_image);
            }

            public void bind(String imagePath, int position) {
                // Get reference to the image in Firebase Storage
                StorageReference imageRef = storage.getReference(imagePath);

                // Load image using Glide with better error handling
                imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            // Check if the view is still valid
                            if (imageView != null && itemView.getContext() != null) {
                                Glide.with(itemView.getContext())
                                        .load(uri)
                                        .placeholder(R.drawable.ic_journey_placeholder)
                                        .error(R.drawable.ic_journey_placeholder)
                                        .transition(DrawableTransitionOptions.withCrossFade(300))
                                        .centerCrop()
                                        .into(imageView);
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Load placeholder on failure
                            if (imageView != null) {
                                imageView.setImageResource(R.drawable.ic_journey_placeholder);
                            }
                        });

                // Set click listener
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onImageClick(imagePath, position);
                    }
                });
            }

            public void bindPlaceholder() {
                if (imageView != null) {
                    imageView.setImageResource(R.drawable.ic_journey_placeholder);
                }
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onImageClick(null, 0);
                    }
                });
            }
        }
    }
}