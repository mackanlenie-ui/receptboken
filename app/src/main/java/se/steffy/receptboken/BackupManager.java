package se.steffy.receptboken;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
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
    private static final int MAX_NESTED_ZIP_DEPTH = 2;

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
        root.put("version", 2);
        root.put("created", System.currentTimeMillis());
        root.put("entries", entries);

        OutputStream raw = context.getContentResolver().openOutputStream(target, "w");
        if (raw == null) throw new IllegalStateException("Kunde inte öppna backupfilen för skrivning");

        try (OutputStream out = raw; ZipOutputStream zip = new ZipOutputStream(out)) {
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
            zip.finish();
        }
    }

    public static int importBackup(Context context, DiaryDb db, Uri source) throws Exception {
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) throw new IllegalArgumentException("Kunde inte öppna den valda backupfilen");

        byte[] sourceBytes;
        try (InputStream in = raw) {
            sourceBytes = readAll(in);
        }
        if (sourceBytes.length == 0) throw new IllegalArgumentException("Backupfilen är tom");

        ParsedBackup parsed = parseBackupBytes(sourceBytes, 0);
        JSONObject root = new JSONObject(parsed.json);
        String format = root.optString("format", "");
        if (!"MinDagbokBackup".equals(format)) {
            throw new IllegalArgumentException("Filen är inte en säkerhetskopia från Min Dagbok");
        }

        JSONArray arr = root.optJSONArray("entries");
        if (arr == null) throw new IllegalArgumentException("Backupen saknar anteckningar");
        if (arr.length() == 0) throw new IllegalArgumentException("Backupen innehåller inga sparade anteckningar");

        File imageDir = new File(context.getFilesDir(), "diary_images");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            throw new IllegalStateException("Bildmappen kunde inte skapas");
        }

        ArrayList<Entry> restored = new ArrayList<>();
        ArrayList<File> newlyWrittenImages = new ArrayList<>();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;

                Entry e = new Entry();
                e.date = item.optString("date", "").trim();
                if (e.date.isEmpty()) continue;
                e.title = item.optString("title", "");
                e.body = item.optString("body", "");
                e.mood = item.optInt("mood", 2);
                e.favorite = item.optBoolean("favorite", false);
                e.updated = item.optLong("updated", System.currentTimeMillis());

                JSONArray photos = item.optJSONArray("photos");
                if (photos != null) {
                    for (int j = 0; j < photos.length(); j++) {
                        String archiveName = photos.optString(j, "");
                        byte[] bytes = parsed.images.get(archiveName);
                        if (bytes == null || bytes.length == 0) continue;

                        String ext = extension(archiveName);
                        File out = new File(imageDir, "restored_" + UUID.randomUUID() + ext);
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            fos.write(bytes);
                        }
                        newlyWrittenImages.add(out);
                        e.photos.add(out.getAbsolutePath());
                    }
                }
                restored.add(e);
            }

            if (restored.isEmpty()) {
                throw new IllegalArgumentException("Inga giltiga dagboksanteckningar hittades i backupen");
            }

            // Radera inte den nuvarande databasen förrän hela backupen har lästs korrekt.
            db.clearAll();
            for (Entry e : restored) {
                if (db.save(e) < 0) throw new IllegalStateException("En anteckning kunde inte återställas");
            }
            return restored.size();
        } catch (Exception e) {
            // Städa endast bilder som skapades under ett misslyckat importförsök.
            for (File f : newlyWrittenImages) {
                try { if (f.exists()) f.delete(); } catch (Exception ignored) {}
            }
            throw e;
        }
    }

    private static ParsedBackup parseBackupBytes(byte[] bytes, int depth) throws Exception {
        if (depth > MAX_NESTED_ZIP_DEPTH) {
            throw new IllegalArgumentException("Backupfilen innehåller för många kapslade zip-filer");
        }

        String trimmed = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8).trim();
        if (trimmed.startsWith("{")) {
            return new ParsedBackup(new String(bytes, StandardCharsets.UTF_8), new HashMap<>());
        }

        HashMap<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int n;
                while ((n = zip.read(buffer)) > 0) out.write(buffer, 0, n);
                entries.put(name, out.toByteArray());
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Backupfilen är skadad eller har fel format");
        }

        byte[] jsonBytes = entries.get("entries.json");
        if (jsonBytes != null) {
            HashMap<String, byte[]> images = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (entry.getKey().startsWith("images/")) images.put(entry.getKey(), entry.getValue());
            }
            return new ParsedBackup(new String(jsonBytes, StandardCharsets.UTF_8), images);
        }

        // Vissa filhanterare kan lägga en zip inuti en annan zip. Acceptera det också.
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String lower = entry.getKey().toLowerCase();
            if (lower.endsWith(".zip") || lower.endsWith(".dagbokzip")) {
                return parseBackupBytes(entry.getValue(), depth + 1);
            }
        }

        throw new IllegalArgumentException("entries.json saknas i backupfilen");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
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

    private static final class ParsedBackup {
        final String json;
        final HashMap<String, byte[]> images;

        ParsedBackup(String json, HashMap<String, byte[]> images) {
            this.json = json;
            this.images = images;
        }
    }
}
