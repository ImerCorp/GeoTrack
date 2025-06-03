package fr.upjv.geotrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.app.ActivityManager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.datatypes.ExerciseRoute;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.UUID;

import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.models.User;
import fr.upjv.geotrack.services.LocationService;


public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {
        private static final int LOCATION_PERMISSION_REQUEST = 1000;

        // Firebase
        private FirebaseFirestore DBFireStore;
        private FirebaseAuth mAuth;
        FirebaseUser currentUser;

        // Permission request codes
        private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
        private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002;
        private FusedLocationProviderClient fusedLocationClient;

        // Map & Location
        private GoogleMap map;
        private FusedLocationProviderClient fusedClient;

        // Couleurs cycliques pour chaque voyage (trip)
        private final int[] POLYLINE_COLORS = {
                0xFFFF0000,  // rouge
                0xFF00FF00,  // vert
                0xFF0000FF,  // bleu
                0xFFFFFF00,  // jaune
                0xFFFF00FF,  // magenta
                0xFF00FFFF   // cyan
        };
        private int colorIndex = 0;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_home);

            // 1) Init Firebase Auth & Firestore
            mAuth = FirebaseAuth.getInstance();
            DBFireStore = FirebaseFirestore.getInstance();

            // 2) Vérifier si l’utilisateur est bien connecté
            this.currentUser = mAuth.getCurrentUser();
            new UserController("HomeActivity")
                    .saveUser(new User(
                            currentUser.getUid(),
                            currentUser.getEmail(),
                            currentUser.getDisplayName()
                    ));
            if (currentUser != null) {
                // Check and request location permissions
                checkLocationPermission();
            } else {
                // Rediriger vers l’écran d’authentification si non connecté
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            // 3) Init du client de localisation (FusedLocationProvider)
            fusedClient = LocationServices.getFusedLocationProviderClient(this);

            // 4) Configuration du fragment Google Map
            SupportMapFragment mapFrag = (SupportMapFragment)
                    getSupportFragmentManager().findFragmentById(R.id.home_map);
            mapFrag.getMapAsync(this);
        }

        /** Callback lorsque la carte est prête */
        @Override
        public void onMapReady(GoogleMap googleMap) {
            map = googleMap;

            // 1) Vérifier la permission ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // Demander la permission si elle n’est pas déjà accordée
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                        LOCATION_PERMISSION_REQUEST
                );
            } else {
                // Permission déjà accordée → activer la couche “My Location” et charger tous les voyages
                enableMyLocation();
                loadAllUserTrips();
            }
        }

        /** Active la couche “My Location” (point bleu + bouton) */
        @SuppressWarnings("MissingPermission")
        private void enableMyLocation() {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);

            // Optionnel : centrer la caméra sur la dernière position connue
            fusedClient.getLastLocation()
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 12f));
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.e("HomeActivity", "Erreur getLastLocation", e)
                    );
        }

        /** Charge la liste de tous les voyages (tripId) de l’utilisateur */
        private void loadAllUserTrips() {
            String userId = mAuth.getCurrentUser().getUid();

            DBFireStore.collection("voyages")
                    .document(userId)
                    .collection("trips")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (DocumentSnapshot tripDoc : querySnapshot.getDocuments()) {
                            String tripId = tripDoc.getId();
                            // Pour chaque voyage on charge ses points et on trace la polyline
                            loadTripPoints(userId, tripId);
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.e("HomeActivity", "Erreur fetch trips", e)
                    );
        }

        /**
         * Pour un voyage donné (tripId), on récupère tous ses points GPS
         * triés par timestamp, puis on dessine une polyline de couleur différente.
         */
        private void loadTripPoints(String userId, String tripId) {
            DBFireStore.collection("voyages")
                    .document(userId)
                    .collection("trips")
                    .document(tripId)
                    .collection("points")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener(pointsSnap -> {
                        List<LatLng> path = new ArrayList<>();
                        for (DocumentSnapshot pDoc : pointsSnap.getDocuments()) {
                            Double lat = pDoc.getDouble("latitude");
                            Double lng = pDoc.getDouble("longitude");
                            if (lat != null && lng != null) {
                                path.add(new LatLng(lat, lng));
                            }
                        }
                        if (!path.isEmpty()) {
                            // Choisir une couleur cyclique dans POLYLINE_COLORS
                            int polyColor = POLYLINE_COLORS[colorIndex % POLYLINE_COLORS.length];
                            colorIndex++;

                            // Dessiner la polyline pour ce voyage
                            map.addPolyline(new PolylineOptions()
                                    .addAll(path)
                                    .color(polyColor)
                                    .width(6f)
                            );

                            // Ajouter un marqueur au début et à la fin du voyage
                            LatLng start = path.get(0);
                            LatLng end   = path.get(path.size() - 1);

                            map.addMarker(new MarkerOptions()
                                    .position(start)
                                    .title("Début voyage " + tripId)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                            BitmapDescriptorFactory.HUE_AZURE))
                            );
                            map.addMarker(new MarkerOptions()
                                    .position(end)
                                    .title("Fin voyage " + tripId)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                            BitmapDescriptorFactory.HUE_VIOLET))
                            );

                            // Si c’est le tout premier voyage, centrer la caméra dessus
                            if (colorIndex == 1) {
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 12f));
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.e("HomeActivity", "Erreur fetch points trip " + tripId, e)
                    );
        }

        private void checkLocationPermission() {
            // Check if we have foreground location permissions
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // Request foreground location permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                // We have foreground location permissions, now check for background location
                checkBackgroundLocationPermission();
            }
        }

        private void checkBackgroundLocationPermission() {
            // Background location permission is only needed on Android 10 (Q) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    // Request background location permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                } else {
                    // All permissions granted, start location service
                    startLocationService();
                }
            } else {
                // For pre-Android 10, we don't need separate background permission
                startLocationService();
            }
        }

        private void startLocationService() {
            if (!isLocationServiceRunning()) {
                try {
                    Intent serviceIntent = new Intent(this, LocationService.class);
                    startForegroundService(serviceIntent);
                    Toast.makeText(this, "Location service started", Toast.LENGTH_SHORT).show();
                    Log.d("HomeActivity", "Location service started successfully");
                } catch (Exception e) {
                    Log.e("HomeActivity", "Failed to start location service", e);
                    Toast.makeText(this, "Failed to start location service", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Location service already running", Toast.LENGTH_SHORT).show();
                Log.d("HomeActivity", "Location service already running");
            }
        }

        private boolean isLocationServiceRunning() {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                for (ActivityManager.RunningServiceInfo service :
                        activityManager.getRunningServices(Integer.MAX_VALUE)) {
                    if (LocationService.class.getName().equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);


            if (requestCode == LOCATION_PERMISSION_REQUEST
                    && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
                loadAllUserTrips();
            }

            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {

                    // Foreground location permissions granted, now check background
                    checkBackgroundLocationPermission();

                } else {
                    // Permissions denied
                    Toast.makeText(this,
                            "Location permissions denied. The app needs location access to function properly.",
                            Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Background location permission granted
                    startLocationService();
                } else {
                    // Background permission denied
                    Toast.makeText(this,
                            "Background location permission denied. Location will only work when the app is open.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            // Note: We DON'T stop the location service here since we want it to run permanently
        }
}
