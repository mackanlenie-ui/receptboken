package se.steffy.receptboken;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DiaryDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "diary.db";
    private static final int DB_VERSION = 2;

    public static final class TrashItem {
        public long trashId;
        public final Entry entry = new Entry();
        public long deletedAt;
    }

    public DiaryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createEntries(db);
        createSafetyTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createSafetyTables(db);
    }

    private void createEntries(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "date TEXT NOT NULL UNIQUE," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "mood INTEGER NOT NULL DEFAULT 2," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "photos TEXT NOT NULL DEFAULT '[]'," +
                "updated INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_updated ON entries(updated DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_favorite ON entries(favorite, updated DESC)");
    }

    private void createSafetyTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trash (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "original_date TEXT NOT NULL," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "mood INTEGER NOT NULL DEFAULT 2," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "photos TEXT NOT NULL DEFAULT '[]'," +
                "updated INTEGER NOT NULL DEFAULT 0," +
                "deleted_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_deleted ON trash(deleted_at DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS versions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_date TEXT NOT NULL," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "mood INTEGER NOT NULL DEFAULT 2," +
                "favorite INTEGER NOT NULL DEFAULT 0," +
                "photos TEXT NOT NULL DEFAULT '[]'," +
                "saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_versions_date ON versions(source_date, saved_at DESC)");
    }

    public long save(Entry e) {
        if (e == null || e.date == null || e.date.trim().isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();

        Entry existing = getByDateInternal(db, e.date);
        if (existing != null && existing.updated > 0 && !sameContent(existing, e)) {
            maybeSaveVersion(db, existing, now);
        }

        ContentValues v = entryValues(e);
        e.updated = now;
        v.put("updated", e.updated);
        long id = db.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        e.id = id;
        return id;
    }

    private void maybeSaveVersion(SQLiteDatabase db, Entry previous, long now) {
        long latest = 0L;
        try (Cursor c = db.rawQuery(
                "SELECT saved_at FROM versions WHERE source_date=? ORDER BY saved_at DESC LIMIT 1",
                new String[]{previous.date})) {
            if (c.moveToFirst()) latest = c.getLong(0);
        }
        if (!DiaryFeaturePolicy.shouldCreateVersion(latest, now)) return;

        ContentValues v = new ContentValues();
        v.put("source_date", previous.date);
        v.put("title", previous.title == null ? "" : previous.title);
        v.put("body", previous.body == null ? "" : previous.body);
        v.put("mood", previous.mood);
        v.put("favorite", previous.favorite ? 1 : 0);
        v.put("photos", Entry.photosToDb(previous.photos));
        v.put("saved_at", now);
        db.insert("versions", null, v);
        trimVersions(db, previous.date);
    }

    private void trimVersions(SQLiteDatabase db, String date) {
        ArrayList<Long> oldIds = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT id FROM versions WHERE source_date=? ORDER BY saved_at DESC",
                new String[]{date})) {
            int index = 0;
            while (c.moveToNext()) {
                if (index++ >= DiaryFeaturePolicy.MAX_VERSIONS_PER_ENTRY) oldIds.add(c.getLong(0));
            }
        }
        for (Long id : oldIds) db.delete("versions", "id=?", new String[]{String.valueOf(id)});
    }

    public Entry getByDate(String date) {
        return getByDateInternal(getReadableDatabase(), date);
    }

    private Entry getByDateInternal(SQLiteDatabase db, String date) {
        try (Cursor c = db.query("entries", null, "date=?", new String[]{date}, null, null, null, "1")) {
            if (c.moveToFirst()) return fromEntryCursor(c);
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
        return query("title LIKE ? OR body LIKE ? OR date LIKE ?",
                new String[]{like, like, like}, "date DESC", null);
    }

    public ArrayList<Entry> month(String yyyyMm) {
        return query("date LIKE ?", new String[]{yyyyMm + "%"}, "date ASC", null);
    }

    public ArrayList<Entry> sameDay(String mmDd, String excludeDate) {
        return query("substr(date,6,5)=? AND date<>?",
                new String[]{mmDd, excludeDate}, "date DESC", "3");
    }

    public int countAll() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int countMonth(String yyyyMm) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM entries WHERE date LIKE ?",
                new String[]{yyyyMm + "%"})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public ArrayList<Entry> all() {
        return query(null, null, "date ASC", null);
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("entries", null, null);
        db.delete("versions", null, null);
    }

    public long moveToTrash(String date) {
        SQLiteDatabase db = getWritableDatabase();
        Entry e = getByDateInternal(db, date);
        if (e == null) return -1;

        ContentValues v = new ContentValues();
        v.put("original_date", e.date);
        v.put("title", e.title == null ? "" : e.title);
        v.put("body", e.body == null ? "" : e.body);
        v.put("mood", e.mood);
        v.put("favorite", e.favorite ? 1 : 0);
        v.put("photos", Entry.photosToDb(e.photos));
        v.put("updated", e.updated);
        v.put("deleted_at", System.currentTimeMillis());

        db.beginTransaction();
        try {
            long id = db.insertOrThrow("trash", null, v);
            db.delete("entries", "date=?", new String[]{date});
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public ArrayList<TrashItem> trashItems() {
        ArrayList<TrashItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "trash", null, null, null, null, null, "deleted_at DESC")) {
            while (c.moveToNext()) out.add(fromTrashCursor(c));
        }
        return out;
    }

    public int trashCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM trash", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public boolean restoreTrash(long trashId) {
        SQLiteDatabase db = getWritableDatabase();
        TrashItem item = getTrashItem(db, trashId);
        if (item == null) return false;
        long result = save(item.entry);
        if (result < 0) return false;
        db.delete("trash", "id=?", new String[]{String.valueOf(trashId)});
        return true;
    }

    public boolean deleteTrashPermanently(long trashId) {
        SQLiteDatabase db = getWritableDatabase();
        TrashItem item = getTrashItem(db, trashId);
        int deleted = db.delete("trash", "id=?", new String[]{String.valueOf(trashId)});
        if (deleted > 0 && item != null && getByDateInternal(db, item.entry.date) == null) {
            db.delete("versions", "source_date=?", new String[]{item.entry.date});
        }
        return deleted > 0;
    }

    public int purgeOldTrash(long now) {
        SQLiteDatabase db = getWritableDatabase();
        long cutoff = now - DiaryFeaturePolicy.TRASH_RETENTION_MS;
        ArrayList<Long> ids = new ArrayList<>();
        try (Cursor c = db.query("trash", new String[]{"id"}, "deleted_at<?",
                new String[]{String.valueOf(cutoff)}, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        int count = 0;
        for (Long id : ids) if (deleteTrashPermanently(id)) count++;
        return count;
    }

    private TrashItem getTrashItem(SQLiteDatabase db, long id) {
        try (Cursor c = db.query("trash", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null, "1")) {
            if (c.moveToFirst()) return fromTrashCursor(c);
        }
        return null;
    }

    private TrashItem fromTrashCursor(Cursor c) {
        TrashItem item = new TrashItem();
        item.trashId = c.getLong(c.getColumnIndexOrThrow("id"));
        item.entry.date = c.getString(c.getColumnIndexOrThrow("original_date"));
        item.entry.title = c.getString(c.getColumnIndexOrThrow("title"));
        item.entry.body = c.getString(c.getColumnIndexOrThrow("body"));
        item.entry.mood = c.getInt(c.getColumnIndexOrThrow("mood"));
        item.entry.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) == 1;
        item.entry.photos.addAll(Entry.photosFromDb(c.getString(c.getColumnIndexOrThrow("photos"))));
        item.entry.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
        item.deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at"));
        return item;
    }

    public ArrayList<Entry> versionsFor(String date, int limit) {
        ArrayList<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "versions", null, "source_date=?", new String[]{date},
                null, null, "saved_at DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(c.getColumnIndexOrThrow("id"));
                e.date = c.getString(c.getColumnIndexOrThrow("source_date"));
                e.title = c.getString(c.getColumnIndexOrThrow("title"));
                e.body = c.getString(c.getColumnIndexOrThrow("body"));
                e.mood = c.getInt(c.getColumnIndexOrThrow("mood"));
                e.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) == 1;
                e.photos.addAll(Entry.photosFromDb(c.getString(c.getColumnIndexOrThrow("photos"))));
                e.updated = c.getLong(c.getColumnIndexOrThrow("saved_at"));
                out.add(e);
            }
        }
        return out;
    }

    public boolean restoreVersion(Entry version) {
        if (version == null || version.date == null || version.date.isEmpty()) return false;
        Entry restored = new Entry();
        restored.date = version.date;
        restored.title = version.title;
        restored.body = version.body;
        restored.mood = version.mood;
        restored.favorite = version.favorite;
        restored.photos.addAll(version.photos);
        return save(restored) >= 0;
    }

    private ArrayList<Entry> query(String selection, String[] args, String order, String limit) {
        ArrayList<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "entries", null, selection, args, null, null, order, limit)) {
            while (c.moveToNext()) out.add(fromEntryCursor(c));
        }
        return out;
    }

    private Entry fromEntryCursor(Cursor c) {
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

    private ContentValues entryValues(Entry e) {
        ContentValues v = new ContentValues();
        v.put("date", e.date);
        v.put("title", e.title == null ? "" : e.title.trim());
        v.put("body", e.body == null ? "" : e.body);
        v.put("mood", e.mood);
        v.put("favorite", e.favorite ? 1 : 0);
        v.put("photos", Entry.photosToDb(e.photos));
        return v;
    }

    private boolean sameContent(Entry a, Entry b) {
        String at = a.title == null ? "" : a.title.trim();
        String bt = b.title == null ? "" : b.title.trim();
        String ab = a.body == null ? "" : a.body;
        String bb = b.body == null ? "" : b.body;
        return at.equals(bt)
                && ab.equals(bb)
                && a.mood == b.mood
                && a.favorite == b.favorite
                && Entry.photosToDb(a.photos).equals(Entry.photosToDb(b.photos));
    }
}
