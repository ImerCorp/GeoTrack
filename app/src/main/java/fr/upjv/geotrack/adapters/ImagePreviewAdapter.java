package fr.upjv.geotrack.adapters;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import java.util.List;
import fr.upjv.geotrack.R;

public class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.ImagePreviewViewHolder> {

    private List<Uri> imageUris;
    private OnImageRemoveListener onImageRemoveListener;

    public interface OnImageRemoveListener {
        void onImageRemove(Uri imageUri);
    }

    public ImagePreviewAdapter(List<Uri> imageUris, OnImageRemoveListener listener) {
        this.imageUris = imageUris;
        this.onImageRemoveListener = listener;
    }

    @NonNull
    @Override
    public ImagePreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_preview, parent, false);
        return new ImagePreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImagePreviewViewHolder holder, int position) {
        Uri imageUri = imageUris.get(position);

        // Load image using Glide with optimized settings
        Glide.with(holder.itemView.getContext())
                .load(imageUri)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error))
                .into(holder.imageView);

        // Set up remove button click listener
        holder.removeButton.setOnClickListener(v -> {
            if (onImageRemoveListener != null) {
                onImageRemoveListener.onImageRemove(imageUri);
            }
        });

        // Add thumbnail indicator for first image
        if (position == 0) {
            holder.thumbnailIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.thumbnailIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return imageUris != null ? imageUris.size() : 0;
    }

    /**
     * Update the image list and refresh the adapter
     * @param newImageUris Updated list of image URIs
     */
    public void updateImages(List<Uri> newImageUris) {
        this.imageUris = newImageUris;
        notifyDataSetChanged();
    }

    /**
     * Add a new image to the list
     * @param imageUri URI of the image to add
     */
    public void addImage(Uri imageUri) {
        if (imageUris != null && imageUri != null) {
            imageUris.add(imageUri);
            notifyItemInserted(imageUris.size() - 1);
        }
    }

    /**
     * Remove an image from the list
     * @param imageUri URI of the image to remove
     */
    public void removeImage(Uri imageUri) {
        if (imageUris != null && imageUri != null) {
            int position = imageUris.indexOf(imageUri);
            if (position != -1) {
                imageUris.remove(position);
                notifyItemRemoved(position);
                // Notify thumbnail change if first item was removed
                if (position == 0 && !imageUris.isEmpty()) {
                    notifyItemChanged(0);
                }
            }
        }
    }

    public static class ImagePreviewViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageButton removeButton;
        View thumbnailIndicator;

        public ImagePreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.preview_image);
            removeButton = itemView.findViewById(R.id.remove_image_button);
            thumbnailIndicator = itemView.findViewById(R.id.thumbnail_indicator);
        }
    }
}