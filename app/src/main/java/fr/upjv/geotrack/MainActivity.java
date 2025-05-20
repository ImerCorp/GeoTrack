package fr.upjv.geotrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        this.DBFireStore = FirebaseFirestore.getInstance();

        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // If not signed in, redirect to AuthActivity
            startActivity(new Intent(MainActivity.this, AuthActivity.class));
            finish();
            // User is signed in
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Welcome, " + currentUser.getDisplayName());
            }
            return;
        }
        else {
            // If not signed in, redirect to AuthActivity
            startActivity(new Intent(MainActivity.this, HomeActivity.class));
            finish();
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in when activity starts
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(MainActivity.this, AuthActivity.class));
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            signOut();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        mAuth.signOut();
        Toast.makeText(MainActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(MainActivity.this, AuthActivity.class));
        finish();
    }
}