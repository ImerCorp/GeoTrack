package fr.upjv.geotrack.fragments.home;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import fr.upjv.geotrack.MainActivity;
import fr.upjv.geotrack.R;
import fr.upjv.geotrack.adapters.JourneyAdapter;
import fr.upjv.geotrack.controllers.JourneyController;
import fr.upjv.geotrack.models.Journey;

public class ProfileFragment extends Fragment implements JourneyAdapter.OnJourneyActionListener {

    private static final String TAG = "ProfileFragment";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    // Controllers
    private JourneyController journeyController;

    // UI Components
    private ImageView profileImage;
    private TextView displayName;
    private TextView emailAddress;
    private TextView memberSince;
    private Button addJourneyButton;
    private RecyclerView journeysRecyclerView;
    private LinearLayout emptyStateLayout;

    // Journey Management
    private JourneyAdapter journeyAdapter;
    private List<Journey> journeyList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Initialize controllers
        journeyController = new JourneyController();

        // Initialize journey list
        journeyList = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize UI components
        initializeViews(view);

        // Load user profile data
        loadUserProfile();

        // Setup journey management
        setupJourneyManagement();

        // Load user journeys
        loadUserJourneys();

        return view;
    }

    private void initializeViews(View view) {
        // Profile views
        profileImage = view.findViewById(R.id.profile_image);
        displayName = view.findViewById(R.id.display_name);
        emailAddress = view.findViewById(R.id.email_address);
        memberSince = view.findViewById(R.id.member_since);

        // Journey views
        addJourneyButton = view.findViewById(R.id.add_journey_button);
        journeysRecyclerView = view.findViewById(R.id.journeys_recycler_view);
        emptyStateLayout = view.findViewById(R.id.empty_state_layout);
    }

    private void loadUserProfile() {
        if (currentUser != null) {
            loadBasicUserInfo();
        } else {
            redirectToLogin();
        }
    }

    private void loadBasicUserInfo() {
        // Display name
        String name = currentUser.getDisplayName();
        if (name != null && !name.isEmpty()) {
            displayName.setText(name);
        } else {
            displayName.setText("User");
        }

        // Email address
        String email = currentUser.getEmail();
        if (email != null) {
            emailAddress.setText(email);
        }

        // Set default profile image
        profileImage.setImageResource(R.drawable.ic_profile_logo);

        // Member since
        long creationTimestamp = currentUser.getMetadata().getCreationTimestamp();
        Date creationDate = new Date(creationTimestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        memberSince.setText("Member since " + dateFormat.format(creationDate));
    }

    private void setupJourneyManagement() {
        // Setup RecyclerView
        journeyAdapter = new JourneyAdapter(journeyList, this);
        journeysRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        journeysRecyclerView.setAdapter(journeyAdapter);

        // Setup add journey button
        addJourneyButton.setOnClickListener(v -> showJourneyDialog(null));
    }

    private void loadUserJourneys() {
        if (currentUser == null) return;

        String userUUID = currentUser.getUid();

        db.collection("journey")
                .whereEqualTo("userUUID", userUUID)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        journeyList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                String id = document.getString("id");
                                String userUUIDDoc = document.getString("userUUID");
                                Date start = document.getDate("start");
                                Date end = document.getDate("end");
                                String name = document.getString("name");
                                String imageURL = document.getString("imageURL");

                                Journey journey = new Journey(id, userUUIDDoc, start, end, name, imageURL);
                                journeyList.add(journey);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing journey document", e);
                            }
                        }

                        // Update UI
                        journeyAdapter.updateJourneys(journeyList);
                        updateEmptyState();

                    } else {
                        Log.w(TAG, "Error getting journeys", task.getException());
                        Toast.makeText(getContext(), "Failed to load journeys", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateEmptyState() {
        if (journeyList.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            journeysRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            journeysRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showJourneyDialog(Journey existingJourney) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_journey, null);

        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText nameInput = dialogView.findViewById(R.id.journey_name_input);
        TextInputEditText startDateInput = dialogView.findViewById(R.id.start_date_input);
        TextInputEditText endDateInput = dialogView.findViewById(R.id.end_date_input);

        // Setup for edit mode
        boolean isEditMode = existingJourney != null;
        if (isEditMode) {
            dialogTitle.setText("Edit Journey");
            nameInput.setText(existingJourney.getName());

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            startDateInput.setText(dateFormat.format(existingJourney.getStart()));
            endDateInput.setText(dateFormat.format(existingJourney.getEnd()));
        }

        // Date picker setup
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        if (isEditMode) {
            startCalendar.setTime(existingJourney.getStart());
            endCalendar.setTime(existingJourney.getEnd());
        }

        startDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        startDateInput.setText(dateFormat.format(startCalendar.getTime()));
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        endDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        endCalendar.set(year, month, dayOfMonth);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        endDateInput.setText(dateFormat.format(endCalendar.getTime()));
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button saveButton = dialogView.findViewById(R.id.save_button);

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a journey name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (startCalendar.after(endCalendar)) {
                Toast.makeText(getContext(), "Start date must be before end date", Toast.LENGTH_SHORT).show();
                return;
            }

            // Continuation from where the code was cut off...

            // Create or update journey
            String journeyId = isEditMode ? existingJourney.getId() : UUID.randomUUID().toString();
            Journey journey = new Journey(
                    journeyId,
                    currentUser.getUid(),
                    startCalendar.getTime(),
                    endCalendar.getTime(),
                    name,
                    null // imageURL - can be set later
            );

            if (isEditMode) {
                updateJourney(journey, dialog);
            } else {
                createJourney(journey, dialog);
            }
        });

        dialog.show();
    }

    private void createJourney(Journey journey, AlertDialog dialog) {
        journeyController.createJourney(journey)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Journey created successfully");
                    Toast.makeText(getContext(), "Journey created successfully", Toast.LENGTH_SHORT).show();
                    loadUserJourneys(); // Refresh the list
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating journey", e);
                    Toast.makeText(getContext(), "Failed to create journey", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateJourney(Journey journey, AlertDialog dialog) {
        journeyController.updateJourney(journey)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Journey updated successfully");
                    Toast.makeText(getContext(), "Journey updated successfully", Toast.LENGTH_SHORT).show();
                    loadUserJourneys(); // Refresh the list
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating journey", e);
                    Toast.makeText(getContext(), "Failed to update journey", Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteJourney(Journey journey) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Journey")
                .setMessage("Are you sure you want to delete \"" + journey.getName() + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    journeyController.deleteJourney(journey.getId())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Journey deleted successfully");
                                Toast.makeText(getContext(), "Journey deleted successfully", Toast.LENGTH_SHORT).show();
                                loadUserJourneys(); // Refresh the list
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting journey", e);
                                Toast.makeText(getContext(), "Failed to delete journey", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    // JourneyAdapter.OnJourneyActionListener implementation
    @Override
    public void onEditJourney(Journey journey) {
        showJourneyDialog(journey);
    }

    @Override
    public void onDeleteJourney(Journey journey) {
        deleteJourney(journey);
    }

    @Override
    public void onJourneyClick(Journey journey) {
        // Handle journey click - navigate to journey details or map view
        // This could start a new activity or navigate to another fragment
        Log.d(TAG, "Journey clicked: " + journey.getName());
        // TODO: Implement navigation to journey details
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh journeys when returning to this fragment
        if (currentUser != null) {
            loadUserJourneys();
        }
    }
}