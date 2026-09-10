package se.steffy.receptboken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Entry {
    public long id;
    public String date = "";
    public String title = "";
    public String body = "";
    public int mood = 2;
    public boolean favorite;
    public final ArrayList<String> photos = new ArrayList<>();
    public long updated;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("date", date);
            o.put("title", title);
            o.put("body", body);
            o.put("mood", mood);
            o.put("favorite", favorite);
            o.put("updated", updated);
            JSONArray a = new JSONArray();
            for (String p : photos) a.put(p);
            o.put("photos", a);
        } catch (Exception ignored) {}
        return o;
    }

    public static Entry fromJson(JSONObject o) {
        Entry e = new Entry();
        e.date = o.optString("date", "");
        e.title = o.optString("title", "");
        e.body = o.optString("body", "");
        e.mood = o.optInt("mood", 2);
        e.favorite = o.optBoolean("favorite", false);
        e.updated = o.optLong("updated", System.currentTimeMillis());
        JSONArray a = o.optJSONArray("photos");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String p = a.optString(i, "");
                if (!p.isEmpty()) e.photos.add(p);
            }
        }
        return e;
    }

    public static String photosToDb(List<String> photos) {
        JSONArray a = new JSONArray();
        for (String p : photos) a.put(p);
        return a.toString();
    }

    public static ArrayList<String> photosFromDb(String value) {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(value == null ? "[]" : value);
            for (int i = 0; i < a.length(); i++) {
                String p = a.optString(i, "");
                if (!p.isEmpty()) out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
