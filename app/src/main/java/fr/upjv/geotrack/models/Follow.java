package fr.upjv.geotrack.models;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Follow {
    private String id;
    private String followerId;
    private String followingId;
    private Date timestamp;


    public Follow() { }

    public Follow(String id, String followerId, String followingId, Date timestamp) {
        this.id = id;
        this.followerId = followerId;
        this.followingId = followingId;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getFollowerId() { return followerId; }
    public String getFollowingId() { return followingId; }
    public Date getTimestamp() { return timestamp; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("followerId", followerId);
        m.put("followingId", followingId);
        m.put("timestamp", timestamp);
        return m;
    }
}
