package fr.upjv.geotrack;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import fr.upjv.geotrack.adapters.PhotoSliderAdapter;
import fr.upjv.geotrack.controllers.LocalisationController;
import fr.upjv.geotrack.controllers.UserController;
import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.Localisation;
import fr.upjv.geotrack.models.User;

public class JourneyDetailActivity extends AppCompatActivity
        implements PhotoSliderAdapter.OnPhotoClickListener,
        PhotoSliderAdapter.OnPhotoChangeListener,
        OnMapReadyCallback {

    private static final String TAG = "JourneyDetailActivity";

    public static final String EXTRA_JOURNEY_ID          = "journey_id";
    public static final String EXTRA_JOURNEY_NAME        = "journey_name";
    public static final String EXTRA_JOURNEY_DESCRIPTION = "journey_description";
    public static final String EXTRA_JOURNEY_START_DATE  = "journey_start_date";
    public static final String EXTRA_JOURNEY_END_DATE    = "journey_end_date";
    public static final String EXTRA_JOURNEY_USER_UUID   = "journey_user_uuid";

    // UI
    private ImageButton backButton;
    private ImageView  userProfilePicture;
    private TextView   userDisplayName, journeyTitle, journeyDescription,
            journeyDates, journeyStatus, journeyDuration;
    private RecyclerView photosRecyclerView;
    private View      photosContainer;
    private TextView  noPhotosText, photoCounter;
    private LinearLayout photoIndicators;
    private TextView  localisationCount, localisationRange;
    private Button    buttonExportGpx;

    // Map
    private GoogleMap googleMap;
    private View      mapLoadingOverlay, mapNoDataOverlay;
    private LinearLayout mapControls;
    private ImageButton btnCenterMap, btnFullscreenMap;

    // Data
    private Journey                 journey;
    private List<String>            photoUrls;
    private List<Localisation>      journeyLocalisations;
    private PhotoSliderAdapter      photoSliderAdapter;
    private FirebaseFirestore       db;
    private FirebaseStorage         storage;
    private UserController          userController;
    private LocalisationController  localisationController;

    public static void startActivity(Context context, Journey journey) {
        Intent i = new Intent(context, JourneyDetailActivity.class);
        i.putExtra(EXTRA_JOURNEY_ID, journey.getId());
        i.putExtra(EXTRA_JOURNEY_NAME, journey.getName());
        i.putExtra(EXTRA_JOURNEY_DESCRIPTION, journey.getDescription());
        i.putExtra(EXTRA_JOURNEY_START_DATE, journey.getStart().getTime());
        i.putExtra(EXTRA_JOURNEY_END_DATE, journey.getEnd().getTime());
        i.putExtra(EXTRA_JOURNEY_USER_UUID, journey.getUserUUID());
        context.startActivity(i);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey_detail);

        // services & listes
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        userController = new UserController(TAG, this);
        localisationController = new LocalisationController();
        photoUrls = new ArrayList<>();
        journeyLocalisations = new ArrayList<>();

        bindViews();
        initMap();
        loadJourneyFromIntent();
        setupUI();

        if (journey != null && journey.getUserUUID() != null) {
            loadUserInformation();
            loadJourneyPhotos();
            loadJourneyLocalisations();
        }
    }

    private void bindViews() {
        backButton           = findViewById(R.id.back_button);
        userProfilePicture   = findViewById(R.id.user_profile_picture);
        userDisplayName      = findViewById(R.id.user_display_name);
        journeyTitle         = findViewById(R.id.journey_title);
        journeyDescription   = findViewById(R.id.journey_description);
        journeyDates         = findViewById(R.id.journey_dates);
        journeyStatus        = findViewById(R.id.journey_status);
        journeyDuration      = findViewById(R.id.journey_duration);

        photosRecyclerView   = findViewById(R.id.photos_recycler_view);
        photosContainer      = findViewById(R.id.photos_container);
        noPhotosText         = findViewById(R.id.no_photos_text);
        photoCounter         = findViewById(R.id.photo_counter);
        photoIndicators      = findViewById(R.id.photo_indicators);

        localisationCount    = findViewById(R.id.localisation_count);
        localisationRange    = findViewById(R.id.localisation_range);

        mapLoadingOverlay    = findViewById(R.id.map_loading_overlay);
        mapNoDataOverlay     = findViewById(R.id.map_no_data_overlay);
        mapControls          = findViewById(R.id.map_controls);
        btnCenterMap         = findViewById(R.id.btn_center_map);
        btnFullscreenMap     = findViewById(R.id.btn_fullscreen_map);

        buttonExportGpx      = findViewById(R.id.button_export_gpx);

        // photos slider
        photosRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoSliderAdapter = new PhotoSliderAdapter(photoUrls, this);
        photoSliderAdapter.setOnPhotoClickListener(this);
        photoSliderAdapter.setOnPhotoChangeListener(this);
        photosRecyclerView.setAdapter(photoSliderAdapter);

        // listeners
        backButton.setOnClickListener(v -> finish());
        btnCenterMap.setOnClickListener(v -> centerMapOnRoute());
        btnFullscreenMap.setOnClickListener(v ->
                Toast.makeText(this, "Fullscreen map coming soon", Toast.LENGTH_SHORT).show());
        buttonExportGpx.setOnClickListener(v -> exportAndSendGpx());
    }

    private void initMap() {
        SupportMapFragment mapFrag = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFrag != null) {
            mapFrag.getMapAsync(this);
        } else {
            mapNoDataOverlay.setVisibility(View.VISIBLE);
            mapLoadingOverlay.setVisibility(View.GONE);
        }
    }

    @Override public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(49.8941, 2.2956), 10));
        if (!journeyLocalisations.isEmpty()) {
            updateMapWithLocalisations();
        } else {
            updateMapUI();
        }
    }

    private void loadJourneyFromIntent() {
        Intent i = getIntent();
        String id   = i.getStringExtra(EXTRA_JOURNEY_ID);
        String name = i.getStringExtra(EXTRA_JOURNEY_NAME);
        String desc = i.getStringExtra(EXTRA_JOURNEY_DESCRIPTION);
        long   sMs  = i.getLongExtra(EXTRA_JOURNEY_START_DATE, 0);
        long   eMs  = i.getLongExtra(EXTRA_JOURNEY_END_DATE, 0);
        String uuid = i.getStringExtra(EXTRA_JOURNEY_USER_UUID);
        if (id != null && sMs > 0 && eMs > 0) {
            journey = new Journey(id, uuid,
                    new Date(sMs), new Date(eMs),
                    name, desc, null, null);
        } else {
            Toast.makeText(this,
                    "Missing journey data", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupUI() {
        journeyTitle.setText(journey.getName());
        if (journey.hasDescription()) {
            journeyDescription.setVisibility(View.VISIBLE);
            journeyDescription.setText(journey.getDescription());
        } else {
            journeyDescription.setVisibility(View.GONE);
        }
        SimpleDateFormat fmt = new SimpleDateFormat(
                "EEEE, MMMM dd, yyyy", Locale.getDefault());
        String sd = fmt.format(journey.getStart());
        String ed = fmt.format(journey.getEnd());
        journeyDates.setText("From " + sd + " to " + ed);
        long days = (journey.getEnd().getTime() - journey.getStart().getTime())
                / (24 * 3600 * 1000);
        journeyDuration.setText(days <= 0 ? "Same day" :
                days == 1 ? "1 day" : days + " days");
        Date now = new Date();
        if (journey.getEnd().before(now)) {
            journeyStatus.setText("COMPLETED");
            journeyStatus.setTextColor(getColor(android.R.color.holo_green_dark));
        } else if (journey.getStart().before(now)) {
            journeyStatus.setText("IN PROGRESS");
            journeyStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            journeyStatus.setText("UPCOMING");
            journeyStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
        }
    }

    private void loadUserInformation() {
        userController.getUser(journey.getUserUUID(),
                new UserController.UserCallback() {
                    @Override public void onSuccess(User u) {
                        userDisplayName.setText(
                                u.getDisplayName() != null ? u.getDisplayName() : u.getEmail());
                        if (u.hasProfilePictureUrl()) {
                            Glide.with(JourneyDetailActivity.this)
                                    .load(u.getProfilePictureUrl())
                                    .transform(new CircleCrop())
                                    .into(userProfilePicture);
                        }
                    }
                    @Override public void onFailure(String e) {
                        userDisplayName.setText("Unknown User");
                    }
                });
    }

    private void loadJourneyPhotos() {
        db.collection("journey")
                .whereEqualTo("id", journey.getId())
                .get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot doc : qs) {
                        Object o = doc.get("imagePaths");
                        if (o instanceof List) {
                            for (Object p : (List<?>) o) {
                                if (p instanceof String) {
                                    String clean = ((String)p).replaceFirst("^/","");
                                    storage.getReference().child(clean)
                                            .getDownloadUrl()
                                            .addOnSuccessListener(uri -> {
                                                photoUrls.add(uri.toString());
                                                photoSliderAdapter.updatePhotos(photoUrls);
                                            });
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error loading photos", Toast.LENGTH_SHORT).show());
    }

    private void loadJourneyLocalisations() {
        localisationController.getLocalisationsForJourney(journey)
                .addOnSuccessListener(list -> {
                    journeyLocalisations.clear();
                    journeyLocalisations.addAll(list);
                    if (googleMap != null) updateMapWithLocalisations();
                    else updateMapUI();
                })
                .addOnFailureListener(e -> updateMapUI());
    }

    private void updateMapWithLocalisations() {
        googleMap.clear();
        Collections.sort(journeyLocalisations,
                Comparator.comparing(Localisation::getTimestamp));
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        PolylineOptions poly = new PolylineOptions()
                .color(Color.parseColor("#6C5CE7"))
                .width(8f).geodesic(true);
        SimpleDateFormat tmFmt = new SimpleDateFormat(
                "MMM dd, HH:mm", Locale.getDefault());
        for (int i = 0; i < journeyLocalisations.size(); i++) {
            Localisation L = journeyLocalisations.get(i);
            LatLng pt = new LatLng(L.getLatitude(), L.getLongitude());
            poly.add(pt);
            b.include(pt);
            googleMap.addMarker(new MarkerOptions()
                    .position(pt)
                    .snippet(tmFmt.format(L.getTimestamp()))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            i==0?BitmapDescriptorFactory.HUE_GREEN:BitmapDescriptorFactory.HUE_RED)));
        }
        googleMap.addPolyline(poly);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 100));
        updateMapUI();
    }

    private void updateMapUI() {
        boolean has = !journeyLocalisations.isEmpty();
        mapLoadingOverlay.setVisibility(View.GONE);
        mapNoDataOverlay.setVisibility(has ? View.GONE : View.VISIBLE);
        mapControls.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private void centerMapOnRoute() {
        if (googleMap != null && !journeyLocalisations.isEmpty()) {
            updateMapWithLocalisations();
        }
    }

    // === EXPORT GPX & ENVOI PAR MAIL ===

    private void exportAndSendGpx() {
        if (journeyLocalisations.isEmpty()) {
            Toast.makeText(this,
                    "Aucun point à exporter", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File gpx = buildGpxFile(journeyLocalisations);
            sendMailWithAttachment(gpx);
        } catch (IOException ex) {
            Toast.makeText(this,
                    "Erreur export GPX: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private File buildGpxFile(List<Localisation> pts) throws IOException {
        String fn = "journey_" + journey.getId() + ".gpx";
        File f = new File(getExternalFilesDir(null), fn);
        SimpleDateFormat iso = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<gpx version=\"1.1\" creator=\"GeoTrack\">");
            pw.println("  <trk><name>" + escape(journey.getName()) +
                    "</name><trkseg>");
            for (Localisation L : pts) {
                pw.printf(Locale.US,
                        "    <trkpt lat=\"%.6f\" lon=\"%.6f\">" +
                                "<time>%s</time></trkpt>%n",
                        L.getLatitude(), L.getLongitude(),
                        iso.format(L.getTimestamp()));
            }
            pw.println("  </trkseg></trk>");
            pw.println("</gpx>");
        }
        return f;
    }

    @SuppressLint("RestrictedApi")
    private void sendMailWithAttachment(File gpx) {
        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                gpx);
        Intent email = new Intent(Intent.ACTION_SEND);
        email.setType("application/gpx+xml");
        email.putExtra(Intent.EXTRA_EMAIL,
                new String[]{"destinataire@exemple.com"});
        email.putExtra(Intent.EXTRA_SUBJECT,
                "Export GPX – " + journey.getName());
        email.putExtra(Intent.EXTRA_TEXT,
                "Veuillez trouver en pièce jointe le GPX de mon voyage.");
        email.putExtra(Intent.EXTRA_STREAM, uri);
        email.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(email, "Envoyer le fichier GPX"));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;");
    }

    // ── Photo slider callbacks ──
    @Override public void onPhotoChanged(int pos, int tot){}
    @Override public void onPhotoClick(int pos, String url) {
        Intent i = new Intent(this, FullScreenPhotoActivity.class);
        i.putStringArrayListExtra("photo_urls", new ArrayList<>(photoUrls));
        i.putExtra("initial_position", pos);
        startActivity(i);
    }
}
