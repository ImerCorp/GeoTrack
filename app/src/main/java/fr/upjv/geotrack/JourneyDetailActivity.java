package fr.upjv.geotrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.Journey;

public class JourneyDetailActivity extends AppCompatActivity {

    private static final String TAG = "JourneyDetailActivity";
    public static final String EXTRA_JOURNEY_ID = "journey_id";
    public static final String EXTRA_JOURNEY_NAME = "journey_name";
    public static final String EXTRA_JOURNEY_DESCRIPTION = "journey_description";
    public static final String EXTRA_JOURNEY_START_DATE = "journey_start_date";
    public static final String EXTRA_JOURNEY_END_DATE = "journey_end_date";
    public static final String EXTRA_JOURNEY_USER_UUID = "journey_user_uuid";

    // UI Components
    private ImageButton backButton;
    private TextView journeyTitle;
    private TextView journeyDescription;
    private TextView journeyDates;
    private TextView journeyStatus;
    private TextView journeyDuration;

    // Journey data
    private Journey journey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey_detail);

        // Initialize views
        initializeViews();

        // Get journey data from intent
        loadJourneyFromIntent();

        // Setup UI
        setupUI();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        journeyTitle = findViewById(R.id.journey_title);
        journeyDescription = findViewById(R.id.journey_description);
        journeyDates = findViewById(R.id.journey_dates);
        journeyStatus = findViewById(R.id.journey_status);
        journeyDuration = findViewById(R.id.journey_duration);
    }

    private void loadJourneyFromIntent() {
        Intent intent = getIntent();

        if (intent != null) {
            String id = intent.getStringExtra(EXTRA_JOURNEY_ID);
            String name = intent.getStringExtra(EXTRA_JOURNEY_NAME);
            String description = intent.getStringExtra(EXTRA_JOURNEY_DESCRIPTION);
            String userUUID = intent.getStringExtra(EXTRA_JOURNEY_USER_UUID);

            long startDateMs = intent.getLongExtra(EXTRA_JOURNEY_START_DATE, 0);
            long endDateMs = intent.getLongExtra(EXTRA_JOURNEY_END_DATE, 0);

            if (id != null && name != null && startDateMs != 0 && endDateMs != 0) {
                Date startDate = new Date(startDateMs);
                Date endDate = new Date(endDateMs);

                journey = new Journey(id, userUUID, startDate, endDate, name, description, null, null);
                Log.d(TAG, "Journey loaded: " + journey.getName());
            } else {
                Log.e(TAG, "Missing required journey data in intent");
                Toast.makeText(this, "Error loading journey details", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Log.e(TAG, "No intent data received");
            Toast.makeText(this, "Error loading journey details", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupUI() {
        if (journey == null) {
            return;
        }

        // Setup back button
        backButton.setOnClickListener(v -> finish());

        // Set journey title
        journeyTitle.setText(journey.getName());

        // Set journey description
        if (journey.hasDescription()) {
            journeyDescription.setText(journey.getDescription());
            journeyDescription.setVisibility(android.view.View.VISIBLE);
        } else {
            journeyDescription.setVisibility(android.view.View.GONE);
        }

        // Format and set dates
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
        String startDateStr = dateFormat.format(journey.getStart());
        String endDateStr = dateFormat.format(journey.getEnd());

        journeyDates.setText("From " + startDateStr + " to " + endDateStr);

        // Calculate and set duration
        long durationMs = journey.getEnd().getTime() - journey.getStart().getTime();
        long durationDays = durationMs / (24 * 60 * 60 * 1000);

        if (durationDays == 0) {
            journeyDuration.setText("Same day");
        } else if (durationDays == 1) {
            journeyDuration.setText("1 day");
        } else {
            journeyDuration.setText(durationDays + " days");
        }

        // Set status
        setJourneyStatus();
    }

    private void setJourneyStatus() {
        Date now = new Date();

        if (journey.getEnd().before(now)) {
            journeyStatus.setText("COMPLETED");
            journeyStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            journeyStatus.setBackgroundResource(R.drawable.status_completed_background);
        } else if (journey.getStart().before(now) && journey.getEnd().after(now)) {
            journeyStatus.setText("IN PROGRESS");
            journeyStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
            journeyStatus.setBackgroundResource(R.drawable.status_in_progress_background);
        } else {
            journeyStatus.setText("UPCOMING");
            journeyStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
            journeyStatus.setBackgroundResource(R.drawable.gradient_header_background);
        }
    }

    public static void startActivity(android.content.Context context, Journey journey) {
        Intent intent = new Intent(context, JourneyDetailActivity.class);
        intent.putExtra(EXTRA_JOURNEY_ID, journey.getId());
        intent.putExtra(EXTRA_JOURNEY_NAME, journey.getName());
        intent.putExtra(EXTRA_JOURNEY_DESCRIPTION, journey.getDescription());
        intent.putExtra(EXTRA_JOURNEY_START_DATE, journey.getStart().getTime());
        intent.putExtra(EXTRA_JOURNEY_END_DATE, journey.getEnd().getTime());
        intent.putExtra(EXTRA_JOURNEY_USER_UUID, journey.getUserUUID());
        context.startActivity(intent);
    }
}