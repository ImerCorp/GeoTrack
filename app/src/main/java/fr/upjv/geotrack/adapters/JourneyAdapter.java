package fr.upjv.geotrack.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.R;
import fr.upjv.geotrack.models.Journey;

public class JourneyAdapter extends RecyclerView.Adapter<JourneyAdapter.JourneyViewHolder> {

    private List<Journey> journeys;
    private OnJourneyActionListener listener;

    public interface OnJourneyActionListener {
        void onJourneyClick(Journey journey);
        void onEditJourney(Journey journey);
        void onDeleteJourney(Journey journey);
    }

    public JourneyAdapter(List<Journey> journeys, OnJourneyActionListener listener) {
        this.journeys = journeys;
        this.listener = listener;
    }

    @NonNull
    @Override
    public JourneyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey, parent, false);
        return new JourneyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JourneyViewHolder holder, int position) {
        Journey journey = journeys.get(position);
        holder.bind(journey);
    }

    @Override
    public int getItemCount() {
        return journeys.size();
    }

    public void updateJourneys(List<Journey> newJourneys) {
        this.journeys = newJourneys;
        notifyDataSetChanged();
    }

    class JourneyViewHolder extends RecyclerView.ViewHolder {
        private TextView journeyName;
        private TextView journeyDates;
        private TextView journeyStatus;
        private ImageView journeyImage;
        private ImageButton editButton;
        private ImageButton deleteButton;

        public JourneyViewHolder(@NonNull View itemView) {
            super(itemView);
            journeyName = itemView.findViewById(R.id.journey_name);
            journeyDates = itemView.findViewById(R.id.journey_dates);
            journeyStatus = itemView.findViewById(R.id.journey_status);
            journeyImage = itemView.findViewById(R.id.journey_image);
            editButton = itemView.findViewById(R.id.edit_journey_button);
            deleteButton = itemView.findViewById(R.id.delete_journey_button);
        }

        public void bind(Journey journey) {
            journeyName.setText(journey.getName());

            // Format dates
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            String startDate = dateFormat.format(journey.getStart());
            String endDate = dateFormat.format(journey.getEnd());
            String year = yearFormat.format(journey.getStart());

            journeyDates.setText(startDate + " - " + endDate + ", " + year);

            // Determine status
            Date now = new Date();
            if (journey.getEnd().before(now)) {
                journeyStatus.setText("COMPLETED");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
            } else if (journey.getStart().before(now) && journey.getEnd().after(now)) {
                journeyStatus.setText("IN PROGRESS");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_orange_dark));
            } else {
                journeyStatus.setText("UPCOMING");
                journeyStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_blue_dark));
            }

            // Set default image (you can load from URL later)
            journeyImage.setImageResource(R.drawable.ic_journey_placeholder);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onJourneyClick(journey);
                }
            });

            editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditJourney(journey);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteJourney(journey);
                }
            });
        }
    }
}