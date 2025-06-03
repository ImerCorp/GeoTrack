package fr.upjv.geotrack.models;

import java.util.HashMap;


public class User {
    private String uid;
    private String email;
    private String displayName;

    public User() {
        // Required empty constructor for Firestore
    }

    public User(String uid, String email, String displayName) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
    }

    public User(String uid, String email) {
        this.uid = uid;
        this.email = email;
        this.displayName = email; // Use email as display name if no display name
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Override
    public String toString() {
        return displayName != null ? displayName : email;
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

    public HashMap<String, String> toJson() {
        HashMap<String, String> hash = new HashMap<>();
        hash.put("id", this.uid);
        hash.put("email", this.email);
        hash.put("timestamp", this.displayName);
        return hash;
    }
}
