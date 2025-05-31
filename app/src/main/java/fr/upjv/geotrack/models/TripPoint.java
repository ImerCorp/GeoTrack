package fr.upjv.geotrack.models;

import java.util.Date;

public class TripPoint {
    private double latitude;
    private double longitude;
    private Date timestamp;
    private float accuracy;

    public TripPoint() {
        // Required empty constructor for Firestore
    }

    public TripPoint(double latitude, double longitude, Date timestamp, float accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    // Getters and Setters
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
}