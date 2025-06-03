package fr.upjv.geotrack.controllers;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.UUID;

import fr.upjv.geotrack.models.Localisation;

public class LocalisationController {
    private String collectionName = "localisation";

    private FirebaseFirestore DBFireStore;

    public LocalisationController(){
        this.DBFireStore = FirebaseFirestore.getInstance();
    }

    public void saveLocalisation(Localisation localisation, String TAG) {
        // Save to Firestore with unique document ID
        DBFireStore
                .collection("localisation")
                .document(localisation.getId()) // Use unique ID instead of "test"
                .set(localisation.toJson())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location saved successfully to Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save location to Firestore", e);
                });
    }
}
