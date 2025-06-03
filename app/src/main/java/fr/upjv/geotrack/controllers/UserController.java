package fr.upjv.geotrack.controllers;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import fr.upjv.geotrack.HomeActivity;
import fr.upjv.geotrack.models.User;

public class UserController {
    private FirebaseFirestore DBFireStore;
    private String collectionName = "users";
    private String TAG = "None";

    public UserController(String tag){
        this.TAG = tag;
        this.DBFireStore = FirebaseFirestore.getInstance();
    }

    public void saveUser(User user){
        // Add a new document with user ID
        DBFireStore.collection("users")
                .document(user.getUid())
                .set(user.toJson())
                .addOnSuccessListener(
                        success -> {}
                )
                .addOnFailureListener(
                        fail -> {}
                );
    }
}
