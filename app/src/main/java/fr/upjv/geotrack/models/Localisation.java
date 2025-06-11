package fr.upjv.geotrack.models;

import java.util.Date;
import java.util.HashMap;

public class Localisation {
    private String id;
    private String userUUID;
    private Date timestamp;
    private double longitude;
    private double latitude;

    // Main constructor
    public Localisation(String Id, String UserUUID, Date Time, double Latitude, double Longitude) {
        this.id = Id;
        this.userUUID = UserUUID;
        this.timestamp = Time;
        this.longitude = Longitude;
        this.latitude = Latitude;
    }

    // Default constructor for Firebase
    public Localisation() {
    }

    public HashMap<String, Object> toJson() {
        HashMap<String, Object> hash = new HashMap<>();
        hash.put("id", this.id);
        hash.put("userUUID", this.userUUID);
        hash.put("timestamp", this.timestamp);
        hash.put("longitude", this.longitude);
        hash.put("latitude", this.latitude);
        return hash;
    }

    // Getters
    public String getId(){
        return this.id;
    }

    public String getUserUUID() {
        return userUUID;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    // Setters (needed for Firebase)
    public void setId(String id) {
        this.id = id;
    }

    public void setUserUUID(String userUUID) {
        this.userUUID = userUUID;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @Override
    public String toString() {
        return "Localisation{" +
                "id='" + id + '\'' +
                ", userUUID='" + userUUID + '\'' +
                ", timestamp=" + timestamp +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                '}';
    }
}