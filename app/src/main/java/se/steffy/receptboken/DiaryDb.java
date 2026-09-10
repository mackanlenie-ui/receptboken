package se.steffy.receptboken;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DiaryDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "diary.db";
    private static final int DB_VERSION = 1;

    public DiaryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL UNIQUE," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "mood INTEGER NOT NULL DEFAULT 2," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "photos TEXT NOT NULL DEFAULT '[]'," +
                "updated INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_entries_updated ON entries(updated DESC)");
        db.execSQL("CREATE INDEX idx_entries_favorite ON entries(favorite, updated DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long save(Entry e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date);
        v.put("title", e.title == null ? "" : e.title.trim());
        v.put("body", e.body == null ? "" : e.body);
        v.put("mood", e.mood);
        v.put("favorite", e.favorite ? 1 : 0);
        v.put("photos", Entry.photosToDb(e.photos));
        e.updated = System.currentTimeMillis();
        v.put("updated", e.updated);
        SQLiteDatabase db = getWritableDatabase();
        long id = db.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        e.id = id;
        return id;
    }

    public Entry getByDate(String date) {
        try (Cursor c = getReadableDatabase().query("entries", null, "date=?", new String[]{date}, null, null, null, "1")) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public ArrayList<Entry> recent(int limit) {
        return query(null, null, "date DESC", String.valueOf(limit));
    }

    public ArrayList<Entry> favorites() {
        return query("favorite=1", null, "date DESC", null);
    }

    public ArrayList<Entry> search(String term) {
        String like = "%" + term + "%";
        return query("title LIKE ? OR body LIKE ? OR date LIKE ?", new String[]{like, like, like}, "date DESC", null);
    }

    public ArrayList<Entry> month(String yyyyMm) {
        return query("date LIKE ?", new String[]{yyyyMm + "%"}, "date ASC", null);
    }

    public int countAll() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int countMonth(String yyyyMm) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries WHERE date LIKE ?", new String[]{yyyyMm + "%"})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public ArrayList<Entry> all() {
        return query(null, null, "date ASC", null);
    }

    public void clearAll() {
        getWritableDatabase().delete("entries", null, null);
    }

    private ArrayList<Entry> query(String selection, String[] args, String order, String limit) {
        ArrayList<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("entries", null, selection, args, null, null, order, limit)) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    private Entry fromCursor(Cursor c) {
        Entry e = new Entry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.date = c.getString(c.getColumnIndexOrThrow("date"));
        e.title = c.getString(c.getColumnIndexOrThrow("title"));
        e.body = c.getString(c.getColumnIndexOrThrow("body"));
        e.mood = c.getInt(c.getColumnIndexOrThrow("mood"));
        e.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) == 1;
        e.photos.addAll(Entry.photosFromDb(c.getString(c.getColumnIndexOrThrow("photos"))));
        e.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
        return e;
    }
}
