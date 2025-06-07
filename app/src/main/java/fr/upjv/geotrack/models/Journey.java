package fr.upjv.geotrack.models;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Journey {
    private String id;
    private String userUUID;
    private Date start;
    private Date end;
    private String name;
    private List<String> imagePaths; // Storage paths for Firebase Storage
    private String thumbnailPath; // Main thumbnail image path

    // Constructor
    public Journey(String Id, String UserUUID, Date Start, Date End, String Name) {
        this.id = Id;
        this.userUUID = UserUUID;
        this.start = Start;
        this.end = End;
        this.name = Name;
        this.imagePaths = new ArrayList<>();
        this.thumbnailPath = null;
    }

    // Constructor with image paths
    public Journey(String Id, String UserUUID, Date Start, Date End, String Name, List<String> ImagePaths, String ThumbnailPath) {
        this.id = Id;
        this.userUUID = UserUUID;
        this.start = Start;
        this.end = End;
        this.name = Name;
        this.imagePaths = ImagePaths != null ? ImagePaths : new ArrayList<>();
        this.thumbnailPath = ThumbnailPath;
    }

    // Default constructor for Firebase
    public Journey() {
        this.imagePaths = new ArrayList<>();
    }

    // Convert to HashMap for Firebase storage
    public HashMap<String, Object> toJson() {
        HashMap<String, Object> hash = new HashMap<>();
        hash.put("id", this.id);
        hash.put("userUUID", this.userUUID);
        hash.put("start", this.start);
        hash.put("end", this.end);
        hash.put("name", this.name);
        hash.put("imagePaths", this.imagePaths);
        hash.put("thumbnailPath", this.thumbnailPath);
        return hash;
    }

    // Getters
    public String getId() {
        return this.id;
    }

    public String getUserUUID() {
        return userUUID;
    }

    public Date getStart() {
        return start;
    }

    public Date getEnd() {
        return end;
    }

    public String getName() {
        return name;
    }

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setUserUUID(String userUUID) {
        this.userUUID = userUUID;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImagePaths(List<String> imagePaths) {
        this.imagePaths = imagePaths != null ? imagePaths : new ArrayList<>();
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    // Image management methods

    /**
     * Add an image path to the journey
     * @param imagePath The storage path of the image
     */
    public void addImagePath(String imagePath) {
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            if (this.imagePaths == null) {
                this.imagePaths = new ArrayList<>();
            }
            this.imagePaths.add(imagePath);

            // Set as thumbnail if it's the first image
            if (this.thumbnailPath == null) {
                this.thumbnailPath = imagePath;
            }
        }
    }

    /**
     * Remove an image path from the journey
     * @param imagePath The storage path to remove
     * @return true if the path was removed
     */
    public boolean removeImagePath(String imagePath) {
        if (this.imagePaths != null && imagePath != null) {
            boolean removed = this.imagePaths.remove(imagePath);

            // If removed path was the thumbnail, set a new one
            if (removed && imagePath.equals(this.thumbnailPath)) {
                if (!this.imagePaths.isEmpty()) {
                    this.thumbnailPath = this.imagePaths.get(0);
                } else {
                    this.thumbnailPath = null;
                }
            }
            return removed;
        }
        return false;
    }

    /**
     * Get the number of images
     * @return Number of images in the journey
     */
    public int getImageCount() {
        return this.imagePaths != null ? this.imagePaths.size() : 0;
    }

    /**
     * Check if the journey has images
     * @return true if the journey has at least one image
     */
    public boolean hasImages() {
        return getImageCount() > 0;
    }

    /**
     * Clear all image paths
     */
    public void clearImagePaths() {
        if (this.imagePaths != null) {
            this.imagePaths.clear();
        }
        this.thumbnailPath = null;
    }

    /**
     * Generate storage path for a new image
     * @param imageIndex The index of the image
     * @param fileExtension The file extension (e.g., "jpg", "png")
     * @return Storage path for the image
     */
    public String generateImagePath(int imageIndex, String fileExtension) {
        return String.format("journeys/%s/%s/image_%d.%s",
                this.userUUID, this.id, imageIndex, fileExtension);
    }

    /**
     * Generate storage path for thumbnail
     * @param fileExtension The file extension (e.g., "jpg", "png")
     * @return Storage path for the thumbnail
     */
    public String generateThumbnailPath(String fileExtension) {
        return String.format("journeys/%s/%s/thumbnail.%s",
                this.userUUID, this.id, fileExtension);
    }

    // Utility methods

    /**
     * Get the duration of the journey in days
     * @return Number of days between start and end date
     */
    public long getDurationInDays() {
        if (start == null || end == null) {
            return 0;
        }
        long diffInMillies = Math.abs(end.getTime() - start.getTime());
        return TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    /**
     * Get the duration of the journey in hours
     * @return Number of hours between start and end date
     */
    public long getDurationInHours() {
        if (start == null || end == null) {
            return 0;
        }
        long diffInMillies = Math.abs(end.getTime() - start.getTime());
        return TimeUnit.HOURS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    /**
     * Get formatted start date
     * @return Formatted start date string
     */
    public String getFormattedStartDate() {
        if (start == null) return "N/A";
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return dateFormat.format(start);
    }

    /**
     * Get formatted end date
     * @return Formatted end date string
     */
    public String getFormattedEndDate() {
        if (end == null) return "N/A";
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return dateFormat.format(end);
    }

    /**
     * Get formatted date range
     * @return Formatted date range string
     */
    public String getFormattedDateRange() {
        return getFormattedStartDate() + " - " + getFormattedEndDate();
    }

    /**
     * Check if the journey is currently active (current date is between start and end)
     * @return true if journey is currently active
     */
    public boolean isActive() {
        if (start == null || end == null) return false;
        Date now = new Date();
        return now.after(start) && now.before(end);
    }

    /**
     * Check if the journey is upcoming (start date is in the future)
     * @return true if journey is upcoming
     */
    public boolean isUpcoming() {
        if (start == null) return false;
        Date now = new Date();
        return start.after(now);
    }

    /**
     * Check if the journey is completed (end date is in the past)
     * @return true if journey is completed
     */
    public boolean isCompleted() {
        if (end == null) return false;
        Date now = new Date();
        return end.before(now);
    }

    /**
     * Get journey status as string
     * @return Status string: "Active", "Upcoming", or "Completed"
     */
    public String getStatus() {
        if (isActive()) return "Active";
        if (isUpcoming()) return "Upcoming";
        if (isCompleted()) return "Completed";
        return "Unknown";
    }

    /**
     * Validate if the journey data is valid
     * @return true if journey data is valid
     */
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
                userUUID != null && !userUUID.isEmpty() &&
                name != null && !name.trim().isEmpty() &&
                start != null && end != null &&
                !start.after(end);
    }

    @Override
    public String toString() {
        return "Journey{" +
                "id='" + id + '\'' +
                ", userUUID='" + userUUID + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", name='" + name + '\'' +
                ", imageCount=" + getImageCount() +
                ", thumbnailPath='" + thumbnailPath + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Journey journey = (Journey) obj;
        return id != null ? id.equals(journey.id) : journey.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}