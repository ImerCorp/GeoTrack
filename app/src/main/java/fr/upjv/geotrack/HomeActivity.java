package fr.upjv.geotrack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    // Data bases - connections
    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;

    // Components
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        DBFireStore = FirebaseFirestore.getInstance();

        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Initialize and set text to TextView
        textView = findViewById(R.id.textView);
        if (currentUser != null) {
            textView.setText("Welcome, " + currentUser.getEmail() + " + " + currentUser.getUid());
        } else {
            // If not signed in, redirect to AuthActivity
            startActivity(new Intent(HomeActivity.this, MainActivity.class));
            finish();
            return;
        }
    }
}
