package fr.upjv.geotrack.controllers;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
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
                .collection(this.collectionName)
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
