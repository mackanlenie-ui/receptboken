package se.steffy.receptboken;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AutoBackupManager {
    private static final String DIR_NAME = "auto_backups";
    private static final String PREF_LAST_DATE = "auto_backup_last_date";
    private static final String PREF_LAST_UPDATED = "auto_backup_last_updated";
    private static final int KEEP_COUNT = 7;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AutoBackupManager() {}

    public static void createIfNeededAsync(Context context) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                createIfNeeded(app);
            } catch (Exception ignored) {
                // Automatisk backup ska aldrig störa användaren eller krascha appen.
            }
        });
    }

    static boolean createIfNeeded(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        DiaryDb db = new DiaryDb(context);
        try {
            ArrayList<Entry> entries = db.all();
            long latestUpdated = 0L;
            for (Entry e : entries) latestUpdated = Math.max(latestUpdated, e.updated);

            LocalDate todayDate = LocalDate.now();
            String today = todayDate.toString();
            long lastUpdated = prefs.getLong(PREF_LAST_UPDATED, 0L);
            String lastDate = prefs.getString(PREF_LAST_DATE, "");

            if (!AutoBackupPolicy.shouldCreate(!entries.isEmpty(), latestUpdated, lastUpdated, today, lastDate)) {
                return false;
            }

            File dir = backupDir(context);
            File target = new File(dir, AutoBackupPolicy.dailyFileName(todayDate));
            File temp = new File(dir, target.getName() + ".tmp");
            if (temp.exists()) temp.delete();

            try {
                BackupManager.exportBackup(context, db, temp);
                int validatedCount = BackupManager.validateBackup(temp);
                if (validatedCount != entries.size()) {
                    throw new IllegalStateException("Backupkontrollen hittade fel antal anteckningar");
                }
                if (target.exists() && !target.delete()) {
                    throw new IllegalStateException("Kunde inte ersätta dagens återställningspunkt");
                }
                if (!temp.renameTo(target)) {
                    throw new IllegalStateException("Kunde inte slutföra återställningspunkten");
                }
                prefs.edit()
                        .putString(PREF_LAST_DATE, today)
                        .putLong(PREF_LAST_UPDATED, latestUpdated)
                        .apply();
                prune(dir);
                return true;
            } finally {
                if (temp.exists()) temp.delete();
            }
        } finally {
            db.close();
        }
    }

    public static List<File> listBackups(Context context) {
        File dir = backupDir(context);
        File[] files = dir.listFiles(file -> file.isFile() && AutoBackupPolicy.isAutoBackupName(file.getName()));
        if (files == null || files.length == 0) return Collections.emptyList();
        ArrayList<File> result = new ArrayList<>(Arrays.asList(files));
        result.sort(Comparator.comparing(File::getName).reversed());
        return result;
    }

    public static int restore(Context context, DiaryDb db, File file) throws Exception {
        if (file == null || !file.isFile() || !AutoBackupPolicy.isAutoBackupName(file.getName())) {
            throw new IllegalArgumentException("Återställningspunkten finns inte längre");
        }
        BackupManager.validateBackup(file);
        return BackupManager.importBackup(context, db, file);
    }

    public static String dateFromFile(File file) {
        if (file == null || !AutoBackupPolicy.isAutoBackupName(file.getName())) return "";
        String name = file.getName();
        return name.substring("Min_Dagbok_auto_".length(), name.length() - ".zip".length());
    }

    private static File backupDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Kunde inte skapa mappen för återställningspunkter");
        }
        return dir;
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        ArrayList<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        for (String name : AutoBackupPolicy.filesToDelete(names, KEEP_COUNT)) {
            try { new File(dir, name).delete(); } catch (Exception ignored) {}
        }
    }
}
