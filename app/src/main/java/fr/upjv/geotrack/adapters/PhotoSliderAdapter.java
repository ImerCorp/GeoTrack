package fr.upjv.geotrack.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.util.ArrayList;
import java.util.List;
import fr.upjv.geotrack.R;

public class PhotoSliderAdapter extends RecyclerView.Adapter<PhotoSliderAdapter.PhotoViewHolder> {

    private static final String TAG = "PhotoSliderAdapter";
    private List<String> photoUrls;
    private Context context;
    private OnPhotoClickListener onPhotoClickListener;
    private OnPhotoChangeListener onPhotoChangeListener;
    private RecyclerView recyclerView;
    private PagerSnapHelper snapHelper;

    public interface OnPhotoClickListener {
        void onPhotoClick(int position, String photoUrl);
    }

    public interface OnPhotoChangeListener {
        void onPhotoChanged(int position, int total);
    }

    public PhotoSliderAdapter(List<String> photoUrls, Context context) {
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        this.context = context;
        this.snapHelper = new PagerSnapHelper();
        Log.d(TAG, "PhotoSliderAdapter created with " + this.photoUrls.size() + " photos");
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;

        // Setup snap helper for page-like behavior
        snapHelper.attachToRecyclerView(recyclerView);

        // Add scroll listener to track current position
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        View snapView = snapHelper.findSnapView(layoutManager);
                        if (snapView != null) {
                            int position = layoutManager.getPosition(snapView);
                            if (onPhotoChangeListener != null) {
                                onPhotoChangeListener.onPhotoChanged(position, getItemCount());
                            }
                        }
                    }
                }
            }
        });
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    public void setOnPhotoChangeListener(OnPhotoChangeListener listener) {
        this.onPhotoChangeListener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo_slider_enhanced, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, @SuppressLint("RecyclerView") int position) {
        Log.d(TAG, "onBindViewHolder called for position: " + position);

        if (photoUrls == null || position >= photoUrls.size()) {
            Log.e(TAG, "Invalid position or null photoUrls: position=" + position + ", size=" + (photoUrls != null ? photoUrls.size() : "null"));
            return;
        }

        String photoUrl = photoUrls.get(position);
        Log.d(TAG, "Loading photo at position " + position + ": " + photoUrl);

        // Reset image view to normal state first
        holder.imageView.setScaleX(1.0f);
        holder.imageView.setScaleY(1.0f);
        holder.imageView.setAlpha(1.0f);
        holder.imageView.clearColorFilter();

        // Load image using Glide with enhanced settings - FIX: Use fitCenter instead of centerCrop
        Glide.with(context)
                .load(photoUrl)
                .fitCenter() // Changed from centerCrop to fitCenter to avoid gray overlay
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_photo)
                .error(R.drawable.error_photo)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load image at position " + position + ": " + photoUrl, e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded image at position " + position);
                        return false;
                    }
                })
                .into(holder.imageView);

        // Set click listener
        holder.imageView.setOnClickListener(v -> {
            Log.d(TAG, "Photo clicked at position: " + position);
            if (onPhotoClickListener != null) {
                onPhotoClickListener.onPhotoClick(position, photoUrl);
            }
        });

        // REMOVED: Scale animation that could cause gray overlay issues
        // The scale animation was potentially causing visual issues
    }

    @Override
    public int getItemCount() {
        int count = photoUrls != null ? photoUrls.size() : 0;
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    public void updatePhotos(List<String> newPhotos) {
        Log.d(TAG, "updatePhotos called with " + (newPhotos != null ? newPhotos.size() : "null") + " photos");

        if (newPhotos == null) {
            this.photoUrls.clear();
        } else {
            this.photoUrls.clear();
            this.photoUrls.addAll(newPhotos);
        }

        Log.d(TAG, "After update, photoUrls size: " + this.photoUrls.size());
        notifyDataSetChanged();

        // Notify initial position
        if (onPhotoChangeListener != null && !this.photoUrls.isEmpty()) {
            onPhotoChangeListener.onPhotoChanged(0, this.photoUrls.size());
        }

        // Log the URLs for debugging
        for (int i = 0; i < Math.min(3, this.photoUrls.size()); i++) {
            Log.d(TAG, "Photo URL " + i + ": " + this.photoUrls.get(i));
        }
    }

    // Navigation methods
    public void goToNext() {
        if (recyclerView != null && photoUrls != null && !photoUrls.isEmpty()) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                int currentPosition = getCurrentPosition();
                int nextPosition = (currentPosition + 1) % photoUrls.size();
                recyclerView.smoothScrollToPosition(nextPosition);
            }
        }
    }

    public void goToPrevious() {
        if (recyclerView != null && photoUrls != null && !photoUrls.isEmpty()) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                int currentPosition = getCurrentPosition();
                int prevPosition = currentPosition > 0 ? currentPosition - 1 : photoUrls.size() - 1;
                recyclerView.smoothScrollToPosition(prevPosition);
            }
        }
    }

    public void goToPosition(int position) {
        if (recyclerView != null && photoUrls != null && position >= 0 && position < photoUrls.size()) {
            recyclerView.smoothScrollToPosition(position);
        }
    }

    public int getCurrentPosition() {
        if (recyclerView != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                View snapView = snapHelper.findSnapView(layoutManager);
                if (snapView != null) {
                    return layoutManager.getPosition(snapView);
                }
                return layoutManager.findFirstVisibleItemPosition();
            }
        }
        return 0;
    }

    public boolean hasPhotos() {
        return photoUrls != null && !photoUrls.isEmpty();
    }

    public String getPhotoUrl(int position) {
        if (photoUrls != null && position >= 0 && position < photoUrls.size()) {
            return photoUrls.get(position);
        }
        return null;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.photo_image);

            if (imageView == null) {
                Log.e("PhotoSliderAdapter", "ImageView with id 'photo_image' not found in item_photo_slider_enhanced layout!");
            }
        }
    }
}