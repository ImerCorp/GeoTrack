package fr.upjv.geotrack;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import fr.upjv.geotrack.adapters.UserSearchAdapter;
import fr.upjv.geotrack.models.User;

public class SearchUsersActivity extends AppCompatActivity {

    private static final String TAG = "SearchUsersActivity";
    private EditText searchEditText;
    private ImageView backButton;
    private ImageView clearButton;
    private RecyclerView searchResultsRecyclerView;
    private TextView noResultsText;
    private ProgressBar progressBar;

    private UserSearchAdapter userSearchAdapter;
    private List<User> userList;
    private FirebaseFirestore db;

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, SearchUsersActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup listeners
        setupListeners();

        // Focus on search input
        searchEditText.requestFocus();
    }

    private void initializeViews() {
        searchEditText = findViewById(R.id.search_edit_text);
        backButton = findViewById(R.id.back_button);
        clearButton = findViewById(R.id.clear_button);
        searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view);
        noResultsText = findViewById(R.id.no_results_text);
        progressBar = findViewById(R.id.progress_bar);

        // Initially hide clear button and no results text
        clearButton.setVisibility(View.GONE);
        noResultsText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        userList = new ArrayList<>();
        userSearchAdapter = new UserSearchAdapter(userList, this, new UserSearchAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                UserProfileActivity.startActivity(SearchUsersActivity.this, user.getUid());
                finish();
            }
        });

        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchResultsRecyclerView.setAdapter(userSearchAdapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        clearButton.setOnClickListener(v -> {
            searchEditText.setText("");
            clearResults();
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();

                if (query.isEmpty()) {
                    clearButton.setVisibility(View.GONE);
                    clearResults();
                } else {
                    clearButton.setVisibility(View.VISIBLE);
                    searchEditText.removeCallbacks(searchRunnable);
                    searchEditText.postDelayed(searchRunnable, 300);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                searchUsers(query);
            }
        }
    };

    /**
     * This method performs the user search against Firestore.
     * It queries the "users" collection on the 'displayNameLower' field for case-insensitive search,
     * and filters results client-side for "contains" matching on display name or email.
     * @param query The search string entered by the user.
     */
    private void searchUsers(String query) {
        if (query.length() < 2) {
            clearResults();
            return;
        }

        showLoading(true);
        Log.d(TAG, "Searching for users with query: " + query);

        // Convert query to lowercase for database query and client-side filtering
        String queryLower = query.toLowerCase();

        // '\uf8ff' is a high-value Unicode character used to make an "ends with" filter
        // when combined with startAt(). It effectively gets all strings starting with queryLower.
        String queryEnd = queryLower + "\uf8ff";

        // Perform the Firestore query on the 'displayNameLower' field
        // This query requires an index on 'displayNameLower' in Firestore.
        db.collection("users")
                .orderBy("displayNameLower")
                .startAt(queryLower)       // Utilise la version en minuscules pour la requête serveur
                .endAt(queryEnd)           // Utilise la version en minuscules pour la requête serveur
                .limit(20) // Limit the number of results for efficiency
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false); // Hide loading indicator once task is complete
                    if (task.isSuccessful()) {
                        userList.clear(); // Clear previous results
                        Log.d(TAG, "Firestore query successful. Documents found: " + task.getResult().size());

                        int usersAddedToAdapter = 0; // Counter for users actually added to the adapter's list
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                User user = document.toObject(User.class);
                                if (user != null) {
                                    // Client-side filtering: Check if display name OR email contains the query.
                                    String userDisplayNameLower = (user.getDisplayName() != null) ? user.getDisplayName().toLowerCase() : "";
                                    String userEmailLower = (user.getEmail() != null) ? user.getEmail().toLowerCase() : "";

                                    // Check if the lowercase display name OR lowercase email contains the query string (lowercase)
                                    if (userDisplayNameLower.contains(queryLower) || userEmailLower.contains(queryLower)) {
                                        userList.add(user);
                                        usersAddedToAdapter++;
                                    } else {
                                        // Log if a user document was fetched but then filtered out by client-side logic
                                        Log.d(TAG, "User filtered out (client-side): " + user.getDisplayNameOrEmail() + " - Query: " + queryLower);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing user document: " + document.getId(), e);
                            }
                        }
                        Log.d(TAG, "Total users added to list after client-side filtering: " + usersAddedToAdapter);
                        updateUI(); // Refresh the RecyclerView
                    } else {
                        // Log any Firestore query errors
                        Log.e(TAG, "Error searching users", task.getException());
                        Toast.makeText(this, "Erreur lors de la recherche", Toast.LENGTH_SHORT).show();
                        showNoResults(); // Show "no results" message on error
                    }
                });
    }

    private void updateUI() {
        if (userSearchAdapter != null) {
            userSearchAdapter.notifyDataSetChanged();
        }

        if (userList.isEmpty()) {
            showNoResults();
        } else {
            showResults();
        }
    }

    private void clearResults() {
        userList.clear();
        if (userSearchAdapter != null) {
            userSearchAdapter.notifyDataSetChanged();
        }
        hideNoResults();
        showLoading(false);
    }

    private void showResults() {
        searchResultsRecyclerView.setVisibility(View.VISIBLE);
        noResultsText.setVisibility(View.GONE);
    }

    private void showNoResults() {
        searchResultsRecyclerView.setVisibility(View.GONE);
        noResultsText.setVisibility(View.VISIBLE);
    }

    private void hideNoResults() {
        noResultsText.setVisibility(View.GONE);
        searchResultsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchEditText != null) {
            searchEditText.removeCallbacks(searchRunnable);
        }
    }
}
