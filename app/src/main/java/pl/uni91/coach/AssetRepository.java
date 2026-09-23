package pl.uni91.coach;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AssetRepository {
    private final Context context;

    public AssetRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public DatabaseSummary validate() throws IOException, JSONException {
        int fea = loadFea().length();
        int motor = loadMotor().length();
        int behaviors = loadBehaviors().length();

        String[] images = context.getAssets().list("images");
        int imageCount = images == null ? 0 : images.length;
        if (fea != 40 || motor != 40 || behaviors != 26 || imageCount != 80) {
            throw new JSONException("Niepełna baza: FEA=" + fea + ", M=" + motor
                    + ", zachowania=" + behaviors + ", grafiki=" + imageCount);
        }
        return new DatabaseSummary(fea, motor, behaviors, imageCount);
    }

    public JSONArray loadFea() throws IOException, JSONException {
        return readArray("data/fea.json");
    }

    public JSONArray loadMotor() throws IOException, JSONException {
        return readArray("data/motor.json");
    }

    public JSONArray loadBehaviors() throws IOException, JSONException {
        return readArray("data/behaviors.json");
    }

    public JSONArray readArray(String path) throws IOException, JSONException {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        return new JSONArray(json.toString());
    }

    public static final class DatabaseSummary {
        public final int fea;
        public final int motor;
        public final int behaviors;
        public final int images;

        DatabaseSummary(int fea, int motor, int behaviors, int images) {
            this.fea = fea;
            this.motor = motor;
            this.behaviors = behaviors;
            this.images = images;
        }
    }
}
