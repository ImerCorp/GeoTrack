package fr.upjv.geotrack.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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

    public interface OnPhotoClickListener {
        void onPhotoClick(int position, String photoUrl);
    }

    public PhotoSliderAdapter(List<String> photoUrls, Context context) {
        // Create a defensive copy to avoid issues with external list modifications
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        this.context = context;
        Log.d(TAG, "PhotoSliderAdapter created with " + this.photoUrls.size() + " photos");
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo_slider, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder called for position: " + position);

        if (photoUrls == null || position >= photoUrls.size()) {
            Log.e(TAG, "Invalid position or null photoUrls: position=" + position + ", size=" + (photoUrls != null ? photoUrls.size() : "null"));
            return;
        }

        String photoUrl = photoUrls.get(position);
        Log.d(TAG, "Loading photo at position " + position + ": " + photoUrl);

        // Load image using Glide with better error handling
        Glide.with(context)
                .load(photoUrl)
                .centerCrop()
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

        // Log the URLs for debugging
        for (int i = 0; i < Math.min(3, this.photoUrls.size()); i++) {
            Log.d(TAG, "Photo URL " + i + ": " + this.photoUrls.get(i));
        }
    }

    // Add method to check if adapter has data
    public boolean hasPhotos() {
        return photoUrls != null && !photoUrls.isEmpty();
    }

    // Add method to get photo URL at position
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

            // Verify that the ImageView was found
            if (imageView == null) {
                Log.e("PhotoSliderAdapter", "ImageView with id 'photo_image' not found in item_photo_slider layout!");
            }
        }
    }
}