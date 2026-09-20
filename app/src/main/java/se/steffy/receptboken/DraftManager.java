package se.steffy.receptboken;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class DraftManager {
    private static final String PREFS = "diary_drafts";
    private static final String PREFIX_DATA = "data_";
    private static final String PREFIX_TIME = "time_";

    private DraftManager() {}

    public static void save(Context context, Entry entry) {
        if (entry == null || entry.date == null || entry.date.trim().isEmpty()) return;
        try {
            JSONObject o = entry.toJson();
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREFIX_DATA + entry.date, o.toString())
                    .putLong(PREFIX_TIME + entry.date, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static Entry restoreIfNewer(Context context, String date, Entry base) {
        if (date == null || date.isEmpty()) return base;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(PREFIX_DATA + date, null);
        long draftTime = p.getLong(PREFIX_TIME + date, 0L);
        if (raw == null || draftTime <= 0L) return base;
        if (base != null && base.updated > 0L && draftTime <= base.updated) return base;
        try {
            Entry draft = Entry.fromJson(new JSONObject(raw));
            draft.date = date;
            if (base != null) draft.id = base.id;
            return draft;
        } catch (Exception ignored) {
            return base;
        }
    }

    public static void clear(Context context, String date) {
        if (date == null || date.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(PREFIX_DATA + date)
                .remove(PREFIX_TIME + date)
                .apply();
    }
}
