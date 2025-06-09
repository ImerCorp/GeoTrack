package fr.upjv.geotrack;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import fr.upjv.geotrack.adapters.ImagePreviewAdapter;
import fr.upjv.geotrack.controllers.JourneyController;
import fr.upjv.geotrack.models.Journey;

public class CreateJourneyActivity extends AppCompatActivity {

    private static final String TAG = "CreateJourneyActivity";
    private static final int MAX_IMAGES = 10;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Controllers
    private JourneyController journeyController;

    // UI Components
    private ImageButton backButton;
    private TextInputEditText nameInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText startDateInput;
    private TextInputEditText endDateInput;
    private Button selectImagesButton;
    private TextView selectedImagesCount;
    private RecyclerView imagePreviewRecyclerView;
    private Button saveButton;
    private TextView tipText;

    // Image Upload Components
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private List<Uri> selectedImageUris;
    private ImagePreviewAdapter imagePreviewAdapter;

    // Date management
    private Calendar startCalendar;
    private Calendar endCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.create_journey);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Check if user is authenticated
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // Initialize controllers
        journeyController = new JourneyController();

        // Initialize lists and calendars
        selectedImageUris = new ArrayList<>();
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.DAY_OF_MONTH, 7); // Default end date is 7 days from now

        // Setup image picker launcher
        setupImagePickerLauncher();

        // Initialize UI components
        initializeViews();

        // Setup UI components
        setupViews();
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        selectedImageUris.clear();

                        // Handle multiple image selection
                        if (data.getClipData() != null) {
                            int count = Math.min(data.getClipData().getItemCount(), MAX_IMAGES);
                            for (int i = 0; i < count; i++) {
                                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                                selectedImageUris.add(imageUri);
                            }
                        } else if (data.getData() != null) {
                            selectedImageUris.add(data.getData());
                        }

                        updateImagePreview();
                    }
                }
        );
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        nameInput = findViewById(R.id.journey_name_input);
        descriptionInput = findViewById(R.id.journey_description_input);
        startDateInput = findViewById(R.id.start_date_input);
        endDateInput = findViewById(R.id.end_date_input);
        selectImagesButton = findViewById(R.id.select_images_button);
        selectedImagesCount = findViewById(R.id.selected_images_count);
        imagePreviewRecyclerView = findViewById(R.id.image_preview_recycler);
        saveButton = findViewById(R.id.save_button);
        tipText = findViewById(R.id.tip_text);
    }

    private void setupViews() {
        // Setup toolbar
        setupToolbar();

        // Setup date inputs with default values
        setupDateInputs();

        // Setup image selection
        setupImageSelection();

        // Setup save button
        setupSaveButton();
    }

    private void setupToolbar() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                // Show confirmation if there's unsaved data
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog();
                } else {
                    finish();
                }
            });
        }
    }

    private void setupDateInputs() {
        // Use a more compact date format that shows the year clearly
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd\nyyyy", Locale.getDefault());

        // Set default values
        startDateInput.setText(dateFormat.format(startCalendar.getTime()));
        endDateInput.setText(dateFormat.format(endCalendar.getTime()));

        // Setup date pickers
        startDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth);
                        startDateInput.setText(dateFormat.format(startCalendar.getTime()));

                        // Ensure end date is not before start date
                        if (endCalendar.before(startCalendar)) {
                            endCalendar.setTime(startCalendar.getTime());
                            endCalendar.add(Calendar.DAY_OF_MONTH, 1);
                            endDateInput.setText(dateFormat.format(endCalendar.getTime()));
                        }
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH)
            );

            // Set minimum date to today
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        endDateInput.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        endCalendar.set(year, month, dayOfMonth);
                        endDateInput.setText(dateFormat.format(endCalendar.getTime()));
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
            );

            // Set minimum date to start date
            datePickerDialog.getDatePicker().setMinDate(startCalendar.getTimeInMillis());
            datePickerDialog.show();
        });
    }

    private void setupImageSelection() {
        // Setup image preview RecyclerView
        imagePreviewAdapter = new ImagePreviewAdapter(selectedImageUris, uri -> {
            selectedImageUris.remove(uri);
            updateImagePreview();
        });

        imagePreviewRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        imagePreviewRecyclerView.setAdapter(imagePreviewAdapter);

        // Setup image selection button
        selectImagesButton.setOnClickListener(v -> {
            if (selectedImageUris.size() >= MAX_IMAGES) {
                Toast.makeText(this, "Maximum " + MAX_IMAGES + " images allowed", Toast.LENGTH_SHORT).show();
                return;
            }
            openImagePicker();
        });

        // Initialize image preview
        updateImagePreview();
    }

    private void setupSaveButton() {
        saveButton.setOnClickListener(v -> {
            if (validateInputs()) {
                createJourney();
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Images"));
    }

    private void updateImagePreview() {
        if (imagePreviewAdapter != null) {
            imagePreviewAdapter.notifyDataSetChanged();
        }

        // Update image count display
        if (selectedImagesCount != null) {
            if (selectedImageUris.isEmpty()) {
                selectedImagesCount.setVisibility(View.GONE);
                imagePreviewRecyclerView.setVisibility(View.GONE);
            } else {
                selectedImagesCount.setText(selectedImageUris.size() + " images selected");
                selectedImagesCount.setVisibility(View.VISIBLE);
                imagePreviewRecyclerView.setVisibility(View.VISIBLE);
            }
        }

        // Update select button text
        if (selectImagesButton != null) {
            int remaining = MAX_IMAGES - selectedImageUris.size();
            if (remaining <= 0) {
                selectImagesButton.setText("📷 Maximum Images Selected");
                selectImagesButton.setEnabled(false);
            } else {
                selectImagesButton.setText("📷 Select Images (Max " + MAX_IMAGES + ")");
                selectImagesButton.setEnabled(true);
            }
        }
    }

    private boolean validateInputs() {
        String name = nameInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Journey name is required");
            nameInput.requestFocus();
            return false;
        }

        if (startCalendar.after(endCalendar)) {
            Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void createJourney() {
        // Disable save button and show loading state
        saveButton.setEnabled(false);
        saveButton.setText("Creating Journey...");

        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        String journeyId = UUID.randomUUID().toString();

        // Create journey object
        Journey journey = new Journey(
                journeyId,
                currentUser.getUid(),
                startCalendar.getTime(),
                endCalendar.getTime(),
                name,
                description.isEmpty() ? null : description
        );

        // Create journey with images
        if (selectedImageUris.isEmpty()) {
            // Create journey without images
            createJourneyInDatabase(journey);
        } else {
            // Upload images first, then create journey
            journeyController.uploadJourneyImages(journey, selectedImageUris)
                    .addOnSuccessListener(updatedJourney -> {
                        createJourneyInDatabase(updatedJourney);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading images", e);
                        Toast.makeText(this, "Failed to upload images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        resetSaveButton();
                    });
        }
    }

    private void createJourneyInDatabase(Journey journey) {
        journeyController.createJourney(journey)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Journey created successfully");
                    Toast.makeText(this, "Journey created successfully!", Toast.LENGTH_SHORT).show();

                    // Clear form data
                    clearForm();

                    // Return to previous screen
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating journey", e);
                    Toast.makeText(this, "Failed to create journey: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetSaveButton();
                });
    }

    private void resetSaveButton() {
        saveButton.setEnabled(true);
        saveButton.setText("Create Journey");
    }

    private void clearForm() {
        nameInput.setText("");
        descriptionInput.setText("");
        selectedImageUris.clear();
        updateImagePreview();
    }

    private boolean hasUnsavedChanges() {
        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        return !name.isEmpty() || !description.isEmpty() || !selectedImageUris.isEmpty();
    }

    private void showUnsavedChangesDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Are you sure you want to leave?")
                .setPositiveButton("Leave", (dialog, which) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog();
        } else {
            super.onBackPressed();
        }
    }
}