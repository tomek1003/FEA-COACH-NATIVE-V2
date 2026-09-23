package pl.uni91.coach;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AppStateStore {
    private final File stateFile;

    public AppStateStore(Context context) { stateFile = new File(context.getFilesDir(), "fea_coach_state.json"); }

    public JSONObject load() {
        if (!stateFile.exists()) return empty();
        try (FileInputStream input = new FileInputStream(stateFile)) {
            byte[] bytes = new byte[(int) stateFile.length()];
            int read = input.read(bytes);
            if (read != bytes.length) return empty();
            JSONObject state = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            ensure(state); return state;
        } catch (Exception ignored) { return empty(); }
    }

    public void save(JSONObject state) throws IOException {
        File temp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(state.toString(2).getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        } catch (JSONException error) { throw new IOException(error); }
        if (stateFile.exists() && !stateFile.delete()) throw new IOException("Nie można zastąpić poprzedniego zapisu");
        if (!temp.renameTo(stateFile)) throw new IOException("Nie można zakończyć zapisu");
    }

    private static JSONObject empty() {
        JSONObject state = new JSONObject();
        try { ensure(state); } catch (JSONException ignored) { }
        return state;
    }

    private static void ensure(JSONObject state) throws JSONException {
        if (!state.has("match")) state.put("match", new JSONObject());
        if (!state.has("plans")) state.put("plans", new JSONObject());
        if (!state.has("scores")) state.put("scores", new JSONObject());
        if (!state.has("history")) state.put("history", new org.json.JSONArray());
    }
}
