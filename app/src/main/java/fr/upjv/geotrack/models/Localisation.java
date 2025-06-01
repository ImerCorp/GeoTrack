package fr.upjv.geotrack.models;

import com.google.firebase.firestore.auth.User;

import java.util.Date;
import java.util.HashMap;

public class Localisation {
    private String id;
    private String userUUID;
    private Date timestamp;
    private double longitude;
    private double latitude;

    public Localisation(String Id, String UserUUID, Date Time, double Latitude, double Longitude) {
        this.id = Id;
        this.userUUID = UserUUID;
        this.timestamp = Time;
        this.longitude = Longitude;
        this.latitude = Latitude;
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

    public String getId(){
        return this.id;
    }
}
