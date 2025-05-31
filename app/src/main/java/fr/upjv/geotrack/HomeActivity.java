package fr.upjv.geotrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.Spinner;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import fr.upjv.geotrack.adapters.UserSpinnerAdapter;
import fr.upjv.geotrack.models.Trip;
import fr.upjv.geotrack.models.TripPoint;
import fr.upjv.geotrack.models.User;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Constants
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long LOCATION_FASTEST_INTERVAL = 5000; // 5 seconds

    // Firebase
    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    // Maps
    private GoogleMap mMap;
    private List<LatLng> currentTripPoints;
    private PolylineOptions currentPolyline;

    // Trip management
    private Trip currentTrip;
    private boolean isTripActive = false;

    // UI Components
    private TextView textView;
    private TextView tvTripStatus;
    private MaterialButton btnStartTrip;
    private MaterialButton btnStopTrip;
    private Spinner spinnerUsers;
    private MaterialButton btnLoadUserTrips;

    // User management
    private List<User> usersList;
    private UserSpinnerAdapter usersAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        DBFireStore = FirebaseFirestore.getInstance();

        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(HomeActivity.this, AuthActivity.class));
            finish();
            return;
        }

        // Initialize UI
        initializeUI(currentUser);

        // Initialize location services
        initializeLocationServices();

        // Initialize Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Initialize trip data
        currentTripPoints = new ArrayList<>();
        currentPolyline = new PolylineOptions().width(8f).color(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
    }

    private void initializeUI(FirebaseUser currentUser) {
        textView = findViewById(R.id.textView);
        tvTripStatus = findViewById(R.id.tvTripStatus);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnStopTrip = findViewById(R.id.btnStopTrip);
        spinnerUsers = findViewById(R.id.spinnerUsers);
        btnLoadUserTrips = findViewById(R.id.btnLoadUserTrips);

        textView.setText("Bienvenue, " + currentUser.getEmail());

        btnStartTrip.setOnClickListener(v -> startTrip());
        btnStopTrip.setOnClickListener(v -> stopTrip());
        btnLoadUserTrips.setOnClickListener(v -> loadSelectedUserTrips());

        // Initialize users list and adapter
        usersList = new ArrayList<>();
        usersAdapter = new UserSpinnerAdapter(this, new ArrayList<>());
        spinnerUsers.setAdapter(usersAdapter);

        // Load all users
        loadAllUsers();
    }

    private void initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (isTripActive) {
                        addLocationToTrip(location);
                    }
                }
            }
        };
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            getCurrentLocationAndMoveCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void getCurrentLocationAndMoveCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                        }
                    });
        }
    }

    private void startTrip() {
        if (!checkLocationPermission()) {
            return;
        }

        // Create new trip
        String tripId = UUID.randomUUID().toString();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        currentTrip = new Trip(tripId, currentUser.getUid(), "Voyage " + new Date().toString(), new Date());
        isTripActive = true;

        // Clear previous trip data
        currentTripPoints.clear();
        mMap.clear();

        // Update UI
        btnStartTrip.setEnabled(false);
        btnStopTrip.setEnabled(true);
        tvTripStatus.setText("Voyage en cours...");

        // Start location updates
        startLocationUpdates();

        Toast.makeText(this, "Voyage démarré!", Toast.LENGTH_SHORT).show();
    }

    private void stopTrip() {
        if (!isTripActive || currentTrip == null) return;

        isTripActive = false;
        stopLocationUpdates();

        // Finalize trip
        currentTrip.setEndTime(new Date());
        currentTrip.setActive(false);

        // Save trip to Firestore
        saveTripToFirestore();

        // Update UI
        btnStartTrip.setEnabled(true);
        btnStopTrip.setEnabled(false);
        tvTripStatus.setText("Voyage terminé - " + currentTripPoints.size() + " points enregistrés");

        Toast.makeText(this, "Voyage sauvegardé!", Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        if (checkLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void addLocationToTrip(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        currentTripPoints.add(latLng);

        // Add point to polyline
        currentPolyline.add(latLng);

        // Update map
        mMap.clear();
        if (currentTripPoints.size() > 1) {
            mMap.addPolyline(currentPolyline);
        }

        // Add marker for current location
        mMap.addMarker(new MarkerOptions().position(latLng).title("Position actuelle"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        // Update status
        tvTripStatus.setText("Voyage en cours - " + currentTripPoints.size() + " points");
    }

    private void saveTripToFirestore() {
        if (currentTrip == null || currentTripPoints.isEmpty()) return;

        // Convert LatLng to TripPoint
        List<TripPoint> tripPoints = new ArrayList<>();
        for (LatLng latLng : currentTripPoints) {
            tripPoints.add(new TripPoint(latLng.latitude, latLng.longitude, new Date(), 0f));
        }
        currentTrip.setPoints(tripPoints);

        // Save to Firestore
        DBFireStore.collection("trips").document(currentTrip.getTripId())
                .set(currentTrip)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Voyage sauvegardé avec succès!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "Erreur lors de la sauvegarde: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
                getCurrentLocationAndMoveCamera();
            } else {
                Toast.makeText(this, "Permission de localisation requise", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTripActive) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isTripActive) {
            startLocationUpdates();
        }
    }

    // ========== USER MANAGEMENT METHODS ==========

    private void loadAllUsers() {
        android.util.Log.d("HomeActivity", "Chargement des utilisateurs depuis Firestore...");

        DBFireStore.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        usersList.clear();
                        android.util.Log.d("HomeActivity", "Nombre de documents trouvés: " + task.getResult().size());

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            android.util.Log.d("HomeActivity", "Document trouvé: " + document.getId());
                            android.util.Log.d("HomeActivity", "Données du document: " + document.getData());

                            try {
                                // Vérifier les données brutes
                                String uid = document.getString("uid");
                                String email = document.getString("email");
                                String displayName = document.getString("displayName");

                                android.util.Log.d("HomeActivity", "UID: " + uid);
                                android.util.Log.d("HomeActivity", "Email: " + email);
                                android.util.Log.d("HomeActivity", "DisplayName: " + displayName);

                                if (uid != null && email != null) {
                                    User user = new User(uid, email, displayName != null ? displayName : email);
                                    usersList.add(user);
                                    android.util.Log.d("HomeActivity", "Utilisateur ajouté: " + user.getEmail());
                                } else {
                                    android.util.Log.e("HomeActivity", "Document invalide - UID ou Email manquant");
                                }

                            } catch (Exception e) {
                                android.util.Log.e("HomeActivity", "Erreur conversion utilisateur: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        android.util.Log.d("HomeActivity", "Taille finale de usersList: " + usersList.size());

                        if (usersList.isEmpty()) {
                            android.util.Log.d("HomeActivity", "Aucun utilisateur valide trouvé, création de l'utilisateur actuel");
                            createCurrentUserInFirestore();
                        } else {
                            android.util.Log.d("HomeActivity", "Mise à jour de l'adapter avec " + usersList.size() + " utilisateurs");
                            usersAdapter.updateUsers(usersList);
                            Toast.makeText(this, usersList.size() + " utilisateurs trouvés", Toast.LENGTH_SHORT).show();

                            // Vérifier que l'adapter fonctionne
                            android.util.Log.d("HomeActivity", "Adapter count: " + usersAdapter.getCount());
                        }

                    } else {
                        android.util.Log.e("HomeActivity", "Erreur lors du chargement des utilisateurs", task.getException());
                        Toast.makeText(this, "Erreur lors du chargement des utilisateurs: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Erreur inconnue"),
                                Toast.LENGTH_SHORT).show();
                        createCurrentUserInFirestore();
                    }
                });
    }

    private void createCurrentUserInFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            User user = new User(currentUser.getUid(), currentUser.getEmail(), currentUser.getDisplayName());

            DBFireStore.collection("users").document(currentUser.getUid())
                    .set(user)
                    .addOnSuccessListener(aVoid -> {
                        usersList.add(user);
                        usersAdapter.updateUsers(usersList);
                        Toast.makeText(this, "Utilisateur créé dans Firestore", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Erreur création utilisateur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void loadSelectedUserTrips() {
        int selectedPosition = spinnerUsers.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= usersList.size()) {
            Toast.makeText(this, "Aucun utilisateur sélectionné", Toast.LENGTH_SHORT).show();
            return;
        }

        User selectedUser = usersList.get(selectedPosition);
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && selectedUser.getUid().equals(currentUser.getUid())) {
            Toast.makeText(this, "Chargement de vos propres voyages...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Chargement des voyages de " + selectedUser.getEmail(), Toast.LENGTH_SHORT).show();
        }

        // Charger les voyages de l'utilisateur sélectionné
        DBFireStore.collection("trips")
                .whereEqualTo("userId", selectedUser.getUid())
                .whereEqualTo("active", false) // Seulement les voyages terminés
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        mMap.clear(); // Effacer la carte
                        int tripsCount = 0;

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Trip trip = document.toObject(Trip.class);
                            displayTripOnMap(trip, tripsCount);
                            tripsCount++;
                        }

                        if (tripsCount == 0) {
                            Toast.makeText(this, "Aucun voyage trouvé pour cet utilisateur", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, tripsCount + " voyage(s) affiché(s)", Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Toast.makeText(this, "Erreur lors du chargement des voyages: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayTripOnMap(Trip trip, int tripIndex) {
        if (trip.getPoints() == null || trip.getPoints().isEmpty()) {
            return;
        }

        // Couleurs différentes pour chaque voyage
        int[] colors = {
                ContextCompat.getColor(this, android.R.color.holo_blue_dark),
                ContextCompat.getColor(this, android.R.color.holo_red_dark),
                ContextCompat.getColor(this, android.R.color.holo_green_dark),
                ContextCompat.getColor(this, android.R.color.holo_orange_dark),
                ContextCompat.getColor(this, android.R.color.holo_purple)
        };
        int color = colors[tripIndex % colors.length];

        PolylineOptions polylineOptions = new PolylineOptions()
                .width(6f)
                .color(color);

        LatLng firstPoint = null;
        LatLng lastPoint = null;

        for (TripPoint point : trip.getPoints()) {
            LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
            polylineOptions.add(latLng);

            if (firstPoint == null) {
                firstPoint = latLng;
            }
            lastPoint = latLng;
        }

        // Ajouter la polyline
        mMap.addPolyline(polylineOptions);

        // Marqueurs de début et fin
        if (firstPoint != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(firstPoint)
                    .title("Début - " + trip.getTripName())
                    .snippet("Démarré le " + trip.getStartTime()));
        }

        if (lastPoint != null && !lastPoint.equals(firstPoint)) {
            mMap.addMarker(new MarkerOptions()
                    .position(lastPoint)
                    .title("Fin - " + trip.getTripName())
                    .snippet("Terminé le " + trip.getEndTime()));

            // Centrer la caméra sur le dernier point
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 13));
        }
    }
}