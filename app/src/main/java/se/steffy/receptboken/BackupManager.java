package se.steffy.receptboken;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private BackupManager() {}

    public static void exportBackup(Context context, DiaryDb db, Uri target) throws Exception {
        JSONArray entries = new JSONArray();
        HashMap<String, String> archivedFiles = new HashMap<>();
        int imageCounter = 0;

        for (Entry original : db.all()) {
            JSONObject item = new JSONObject();
            item.put("date", original.date);
            item.put("title", original.title);
            item.put("body", original.body);
            item.put("mood", original.mood);
            item.put("favorite", original.favorite);
            item.put("updated", original.updated);
            JSONArray photos = new JSONArray();
            for (String path : original.photos) {
                File f = fileFromValue(path);
                if (f != null && f.isFile()) {
                    String ext = extension(f.getName());
                    String archiveName = "images/image_" + (++imageCounter) + ext;
                    archivedFiles.put(archiveName, f.getAbsolutePath());
                    photos.put(archiveName);
                }
            }
            item.put("photos", photos);
            entries.put(item);
        }

        JSONObject root = new JSONObject();
        root.put("format", "MinDagbokBackup");
        root.put("version", 1);
        root.put("created", System.currentTimeMillis());
        root.put("entries", entries);

        try (OutputStream raw = context.getContentResolver().openOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry("entries.json"));
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            byte[] buffer = new byte[16 * 1024];
            for (Map.Entry<String, String> image : archivedFiles.entrySet()) {
                zip.putNextEntry(new ZipEntry(image.getKey()));
                try (InputStream in = new FileInputStream(image.getValue())) {
                    int n;
                    while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    public static int importBackup(Context context, DiaryDb db, Uri source) throws Exception {
        String json = null;
        HashMap<String, byte[]> images = new HashMap<>();
        try (InputStream raw = context.getContentResolver().openInputStream(source);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int n;
                while ((n = zip.read(buffer)) > 0) out.write(buffer, 0, n);
                if ("entries.json".equals(entry.getName())) {
                    json = out.toString(StandardCharsets.UTF_8.name());
                } else if (entry.getName().startsWith("images/")) {
                    images.put(entry.getName(), out.toByteArray());
                }
                zip.closeEntry();
            }
        }

        if (json == null) throw new IllegalArgumentException("entries.json saknas");
        JSONObject root = new JSONObject(json);
        if (!"MinDagbokBackup".equals(root.optString("format"))) {
            throw new IllegalArgumentException("Fel säkerhetskopieformat");
        }
        JSONArray arr = root.optJSONArray("entries");
        if (arr == null) throw new IllegalArgumentException("Inga anteckningar hittades");

        File imageDir = new File(context.getFilesDir(), "diary_images");
        if (!imageDir.exists() && !imageDir.mkdirs()) throw new IllegalStateException("Bildmappen kunde inte skapas");

        ArrayList<Entry> restored = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            Entry e = new Entry();
            e.date = item.optString("date", "");
            if (e.date.isEmpty()) continue;
            e.title = item.optString("title", "");
            e.body = item.optString("body", "");
            e.mood = item.optInt("mood", 2);
            e.favorite = item.optBoolean("favorite", false);
            e.updated = item.optLong("updated", System.currentTimeMillis());
            JSONArray p = item.optJSONArray("photos");
            if (p != null) {
                for (int j = 0; j < p.length(); j++) {
                    String archiveName = p.optString(j, "");
                    byte[] bytes = images.get(archiveName);
                    if (bytes == null) continue;
                    String ext = extension(archiveName);
                    File out = new File(imageDir, "restored_" + UUID.randomUUID() + ext);
                    try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
                    e.photos.add(out.getAbsolutePath());
                }
            }
            restored.add(e);
        }

        db.clearAll();
        for (Entry e : restored) db.save(e);
        return restored.size();
    }

    private static File fileFromValue(String value) {
        if (value == null || value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        if ("file".equalsIgnoreCase(uri.getScheme())) return new File(uri.getPath());
        if (uri.getScheme() == null) return new File(value);
        return null;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && name.length() - dot <= 6) return name.substring(dot);
        return ".jpg";
    }
}
