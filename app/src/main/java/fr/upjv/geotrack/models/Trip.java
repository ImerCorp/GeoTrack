// Trip.java
package fr.upjv.geotrack.models;

import java.util.Date;
import java.util.List;

public class Trip {
    private String tripId;
    private String userId;
    private String tripName;
    private Date startTime;
    private Date endTime;
    private boolean isActive;
    private List<TripPoint> points;

    public Trip() {
        // Required empty constructor for Firestore
    }

    public Trip(String tripId, String userId, String tripName, Date startTime) {
        this.tripId = tripId;
        this.userId = userId;
        this.tripName = tripName;
        this.startTime = startTime;
        this.isActive = true;
    }

    // Getters and Setters
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTripName() { return tripName; }
    public void setTripName(String tripName) { this.tripName = tripName; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public List<TripPoint> getPoints() { return points; }
    public void setPoints(List<TripPoint> points) { this.points = points; }
}