package fr.upjv.geotrack.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import fr.upjv.geotrack.models.User;

public class UserSpinnerAdapter extends ArrayAdapter<User> {
    private LayoutInflater inflater;

    public UserSpinnerAdapter(@NonNull Context context, List<User> users) {
        // On crée une copie pour éviter de modifier la liste d'origine
        super(context, android.R.layout.simple_spinner_item, new ArrayList<>(users));
        this.inflater = LayoutInflater.from(context);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    public void updateUsers(List<User> newUsers) {
        android.util.Log.d("UserSpinnerAdapter", "Mise à jour avec " + newUsers.size() + " utilisateurs");
        clear();             // Vider la liste interne de l'adapter
        addAll(newUsers);    // Ajouter les nouveaux utilisateurs
        notifyDataSetChanged();
        android.util.Log.d("UserSpinnerAdapter", "Adapter mis à jour, count: " + getCount());
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        User user = getItem(position);
        if (user != null) {
            String displayText = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            textView.setText(displayText);
        }

        return convertView;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        User user = getItem(position);
        if (user != null) {
            String displayText = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            textView.setText(displayText);
        }

        return convertView;
    }
}
