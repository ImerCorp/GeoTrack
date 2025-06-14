package fr.upjv.geotrack.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.upjv.geotrack.models.Journey;
import fr.upjv.geotrack.models.Localisation;

public class GPXExporter {
    private static final String TAG = "GPXExporter";

    public static class ExportResult {
        public final boolean success;
        public final String filePath;
        public final String errorMessage;

        public ExportResult(boolean success, String filePath, String errorMessage) {
            this.success = success;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }
    }

    public static ExportResult exportToGPX(Context context, Journey journey, List<Localisation> locations) {
        if (journey == null || locations == null || locations.isEmpty()) {
            return new ExportResult(false, null, "No location data to export");
        }

        try {
            // Create the Downloads directory if it doesn't exist
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Create filename with journey name and timestamp
            String sanitizedJourneyName = sanitizeFileName(journey.getName());
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = sanitizedJourneyName + "_" + timestamp + ".gpx";

            File gpxFile = new File(downloadsDir, fileName);

            // Generate GPX content
            String gpxContent = generateGPXContent(journey, locations);

            // Write to file
            FileWriter writer = new FileWriter(gpxFile);
            writer.write(gpxContent);
            writer.close();

            Log.d(TAG, "GPX file exported successfully: " + gpxFile.getAbsolutePath());
            return new ExportResult(true, gpxFile.getAbsolutePath(), null);

        } catch (IOException e) {
            Log.e(TAG, "Error exporting GPX file", e);
            return new ExportResult(false, null, "Error writing file: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during GPX export", e);
            return new ExportResult(false, null, "Unexpected error: " + e.getMessage());
        }
    }

    public static ExportResult exportToKML(Context context, Journey journey, List<Localisation> locations) {
        if (journey == null || locations == null || locations.isEmpty()) {
            return new ExportResult(false, null, "No location data to export");
        }

        try {
            // Create the Downloads directory if it doesn't exist
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Create filename with journey name and timestamp
            String sanitizedJourneyName = sanitizeFileName(journey.getName());
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = sanitizedJourneyName + "_" + timestamp + ".kml";

            File kmlFile = new File(downloadsDir, fileName);

            // Generate KML content
            String kmlContent = generateKMLContent(journey, locations);

            // Write to file
            FileWriter writer = new FileWriter(kmlFile);
            writer.write(kmlContent);
            writer.close();

            Log.d(TAG, "KML file exported successfully: " + kmlFile.getAbsolutePath());
            return new ExportResult(true, kmlFile.getAbsolutePath(), null);

        } catch (IOException e) {
            Log.e(TAG, "Error exporting KML file", e);
            return new ExportResult(false, null, "Error writing file: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during KML export", e);
            return new ExportResult(false, null, "Unexpected error: " + e.getMessage());
        }
    }

    private static String generateGPXContent(Journey journey, List<Localisation> locations) {
        StringBuilder gpx = new StringBuilder();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

        // GPX header
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"GeoTrack\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");

        // Metadata
        gpx.append("  <metadata>\n");
        gpx.append("    <name>").append(escapeXML(journey.getName())).append("</name>\n");
        if (journey.hasDescription()) {
            gpx.append("    <desc>").append(escapeXML(journey.getDescription())).append("</desc>\n");
        }
        gpx.append("    <time>").append(isoFormat.format(journey.getStart())).append("</time>\n");
        gpx.append("  </metadata>\n");

        // Track
        gpx.append("  <trk>\n");
        gpx.append("    <name>").append(escapeXML(journey.getName())).append("</name>\n");
        if (journey.hasDescription()) {
            gpx.append("    <desc>").append(escapeXML(journey.getDescription())).append("</desc>\n");
        }
        gpx.append("    <trkseg>\n");

        // Track points
        for (Localisation location : locations) {
            gpx.append("      <trkpt lat=\"").append(location.getLatitude())
                    .append("\" lon=\"").append(location.getLongitude()).append("\">\n");

            gpx.append("        <time>").append(isoFormat.format(location.getTimestamp())).append("</time>\n");
            gpx.append("      </trkpt>\n");
        }

        gpx.append("    </trkseg>\n");
        gpx.append("  </trk>\n");
        gpx.append("</gpx>");

        return gpx.toString();
    }

    private static String generateKMLContent(Journey journey, List<Localisation> locations) {
        StringBuilder kml = new StringBuilder();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

        // KML header
        kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        kml.append("  <Document>\n");
        kml.append("    <name>").append(escapeXML(journey.getName())).append("</name>\n");

        if (journey.hasDescription()) {
            kml.append("    <description>").append(escapeXML(journey.getDescription())).append("</description>\n");
        }

        // Style for the track
        kml.append("    <Style id=\"trackStyle\">\n");
        kml.append("      <LineStyle>\n");
        kml.append("        <color>ff5cce6c</color>\n"); // Purple color in KML format
        kml.append("        <width>4</width>\n");
        kml.append("      </LineStyle>\n");
        kml.append("    </Style>\n");

        // Start marker
        if (!locations.isEmpty()) {
            Localisation firstLocation = locations.get(0);
            kml.append("    <Placemark>\n");
            kml.append("      <name>Journey Start</name>\n");
            kml.append("      <description>Started at: ").append(isoFormat.format(firstLocation.getTimestamp())).append("</description>\n");
            kml.append("      <Point>\n");
            kml.append("        <coordinates>").append(firstLocation.getLongitude()).append(",").append(firstLocation.getLatitude());
            kml.append("</coordinates>\n");
            kml.append("      </Point>\n");
            kml.append("    </Placemark>\n");

            // End marker
            if (locations.size() > 1) {
                Localisation lastLocation = locations.get(locations.size() - 1);
                kml.append("    <Placemark>\n");
                kml.append("      <name>Journey End</name>\n");
                kml.append("      <description>Ended at: ").append(isoFormat.format(lastLocation.getTimestamp())).append("</description>\n");
                kml.append("      <Point>\n");
                kml.append("        <coordinates>").append(lastLocation.getLongitude()).append(",").append(lastLocation.getLatitude());
                kml.append("</coordinates>\n");
                kml.append("      </Point>\n");
                kml.append("    </Placemark>\n");
            }
        }

        // Track line
        kml.append("    <Placemark>\n");
        kml.append("      <name>").append(escapeXML(journey.getName())).append(" Track</name>\n");
        kml.append("      <styleUrl>#trackStyle</styleUrl>\n");
        kml.append("      <LineString>\n");
        kml.append("        <tessellate>1</tessellate>\n");
        kml.append("        <coordinates>\n");

        for (Localisation location : locations) {
            kml.append("          ").append(location.getLongitude()).append(",").append(location.getLatitude());
            kml.append("\n");
        }

        kml.append("        </coordinates>\n");
        kml.append("      </LineString>\n");
        kml.append("    </Placemark>\n");
        kml.append("  </Document>\n");
        kml.append("</kml>");

        return kml.toString();
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) return "journey";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escapeXML(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}