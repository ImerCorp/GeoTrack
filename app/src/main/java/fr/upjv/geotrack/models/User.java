package fr.upjv.geotrack.models;

import java.util.HashMap;

public class User {
    private String uid;
    private String email;
    private String displayName;
    private String displayNameLower; // version en minuscules du displayName pour que ça soit insensible à la casse
    private String profilePicturePath;
    private String profilePictureUrl;

    public User() {
        // Required empty constructor for Firestore
    }

    public User(String uid, String email, String displayName) {
        this.uid = uid;
        this.email = email;
        this.setDisplayName(displayName);
        this.profilePicturePath = null;
        this.profilePictureUrl = null;
    }

    public User(String uid, String email) {
        this.uid = uid;
        this.email = email;
        this.setDisplayName(email);
        this.profilePicturePath = null;
        this.profilePictureUrl = null;
    }

    // Constructor with profile picture
    public User(String uid, String email, String displayName, String profilePicturePath, String profilePictureUrl) {
        this.uid = uid;
        this.email = email;
        this.setDisplayName(displayName);
        this.profilePicturePath = profilePicturePath;
        this.profilePictureUrl = profilePictureUrl;
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        // Met à jour automatiquement displayNameLower quand displayName est modifié
        this.displayNameLower = (displayName != null) ? displayName.toLowerCase() : null;
    }

    public String getDisplayNameLower() {
        return displayNameLower;
    }

    public String getProfilePicturePath() {
        return profilePicturePath;
    }

    public void setProfilePicturePath(String profilePicturePath) {
        this.profilePicturePath = profilePicturePath;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    // Profile picture utility

    /**
     * Generate storage path for profile picture
     * @param fileExtension The file extension (e.g., "jpg", "png")
     * @return Storage path for the profile picture
     */
    public String generateProfilePicturePath(String fileExtension) {
        return String.format("users/%s/profile.%s", this.uid, fileExtension);
    }

    /**
     * Check if user has a profile picture
     * @return true if user has a profile picture path
     */
    public boolean hasProfilePicture() {
        return profilePicturePath != null && !profilePicturePath.trim().isEmpty();
    }

    /**
     * Check if user has a cached profile picture URL
     * @return true if user has a profile picture URL
     */
    public boolean hasProfilePictureUrl() {
        return profilePictureUrl != null && !profilePictureUrl.trim().isEmpty();
    }

    /**
     * Clear profile picture data
     */
    public void clearProfilePicture() {
        this.profilePicturePath = null;
        this.profilePictureUrl = null;
    }

    /**
     * Get display name or email as fallback
     * @return Display name if available, otherwise email
     */
    public String getDisplayNameOrEmail() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : email;
    }

    /**
     * Get user initials for avatar placeholder
     * @return User initials (up to 2 characters)
     */
    public String getInitials() {
        String name = getDisplayNameOrEmail();
        if (name == null || name.trim().isEmpty()) {
            return "U";
        }

        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        if (parts.length >= 2) {
            // First name and last name initials
            initials.append(parts[0].charAt(0));
            initials.append(parts[parts.length - 1].charAt(0));
        } else if (parts.length == 1) {
            // Single name - take first character
            initials.append(parts[0].charAt(0));
            if (parts[0].length() > 1) {
                initials.append(parts[0].charAt(1));
            }
        }

        return initials.toString().toUpperCase();
    }

    /**
     * Validate if user data is complete
     * @return true if user has required fields
     */
    public boolean isValid() {
        return uid != null && !uid.trim().isEmpty() &&
                email != null && !email.trim().isEmpty();
    }

    public HashMap<String, Object> toJson() {
        HashMap<String, Object> hash = new HashMap<>();
        hash.put("uid", this.uid);
        hash.put("email", this.email);
        hash.put("displayName", this.displayName);
        hash.put("displayNameLower", this.displayNameLower);
        hash.put("profilePicturePath", this.profilePicturePath);
        hash.put("profilePictureUrl", this.profilePictureUrl);
        return hash;
    }

    @Override
    public String toString() {
        return "User{" +
                "uid='" + uid + '\'' +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                ", displayNameLower='" + displayNameLower + '\'' +
                ", hasProfilePicture=" + hasProfilePicture() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return uid != null ? uid.equals(user.uid) : user.uid == null;
    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }
}
