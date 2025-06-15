# GeoTrack

A Java travel tracking and social platform that integrates with Google Maps to visualize user journeys and routes. Users can track their location, share travels through a social feed, and export routes in GPX/KML formats.

## Documentation (for Mister Loge)

- **Main Documentation**: [GeoTrack Overview](https://anisse-imerzoukene.notion.site/GeoTrack-1ebd37ed979d8093882bc99df9995257?source=copy_link)
- **User Guide**: [Usage Guide](https://www.notion.so/anisse-imerzoukene/Guide-d-utilisation-de-l-application-GEOTRACK-1ebd37ed979d80ac882effe579789a3f)
- **Technical Documentation**: [Technical Docs](https://www.notion.so/anisse-imerzoukene/Documentation-technique-213d37ed979d80e6b6b0e712d627aee9)

## Configuration

### Setup
1. **Google Maps API Key**
   - Add your Google Maps API key to the `AndroidManifest.xml` file
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```

2. **Firebase Configuration**
   - Create a new Firebase project with the following services:
     - **Authentication**: Enable Email/Password authentication
     - **Firestore Database**: Create a database with the following security rules:
     ```javascript
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         // Users – lecture/écriture si authentifié
         match /users/{userId} {
           allow read, write: if request.auth != null;
         }
         
         // Journey – lecture/écriture si authentifié
         match /journey/{journeyId} {
           allow read, write: if request.auth != null;
         }
         
         // Trips – lecture/écriture si authentifié
         match /trips/{tripId} {
           allow read, write: if request.auth != null;
         }
         
         // Localisation – lecture/écriture si authentifié
         match /localisation/{document} {
           allow read, write: if request.auth != null;
         }
         
         // updateUserCurrentLocation – lecture/écriture si authentifié
         match /updateUserCurrentLocation/{document} {
           allow read, write: if request.auth != null;
         }
         
         // Follows collection
         match /follows/{followId} {
           allow create: if request.auth != null
                         && request.resource.data.followerId == request.auth.uid
                         && request.resource.data.followingId is string
                         && request.resource.data.timestamp is timestamp;
           allow delete: if request.auth != null
                         && resource.data.followerId == request.auth.uid;
           allow read:   if request.auth != null
                         && (
                              resource.data.followerId == request.auth.uid ||
                              resource.data.followingId == request.auth.uid
                            );
         }
       }
     }
     ```
     - **Storage**: Create storage with these rules:
     ```javascript
     rules_version = '2';
     service firebase.storage {
       match /b/{bucket}/o {
         match /{allPaths=**} {
           allow read: if true; // Anyone can read
           allow write: if request.auth != null; // Only logged-in users can write
         }
       }
     }
     ```
   - Replace the default Firebase configuration file with your own `google-services.json` file in the `app/` directory

3. **Build and Run**
   - Build the project using Android Studio
   - Install on your device

## Features

- **User Management**: Account creation and authentication
- **Location Tracking**: Customizable GPS update intervals (1-60 seconds)
- **Social Feed**: View all user journeys with profiles, duration, and distance
- **Interactive Maps**: Real-time route display on Google Maps
- **User Profiles**: Search users, follow/unfollow system
- **Journey Creation**: Add dates, photos, and trip information
- **Export**: Generate GPX/KML files compatible with GPS apps and Google Earth
- **Sharing**: Email route exports

## Usage

1. **Login/Register**: Create an account or login with existing credentials
2. **Location Permissions**: Grant location access (precise or approximate)
3. **Feed**: Browse user journeys on the main feed
4. **Create Journey**: Use the orange + button to create new trips
5. **Settings**: Configure GPS update intervals in the hamburger menu
6. **Export**: Export routes as GPX/KML files from journey details

## Tech Stack

- Java (Android)
- Google Maps API
- Firebase
- GPS/Location Services
