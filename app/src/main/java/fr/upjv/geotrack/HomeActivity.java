package fr.upjv.geotrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
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

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST = 1000;

    // Firebase
    private FirebaseFirestore DBFireStore;
    private FirebaseAuth mAuth;

    // UI
    private TextView textView;

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
        textView = findViewById(R.id.textView);
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            textView.setText("Welcome, "
                    + currentUser.getEmail() + " + "
                    + currentUser.getUid());
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

    /** Résultat de la demande de permission */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
}
