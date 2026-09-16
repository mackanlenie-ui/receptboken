package se.steffy.receptboken;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AutoBackupPolicy {
    private static final String PREFIX = "Min_Dagbok_auto_";
    private static final String SUFFIX = ".zip";

    private AutoBackupPolicy() {}

    static boolean shouldCreate(boolean hasEntries, long latestUpdated, long lastBackedUpUpdated,
                                String today, String lastBackupDate) {
        if (!hasEntries) return false;
        if (latestUpdated <= lastBackedUpUpdated) return false;
        return today != null && !today.equals(lastBackupDate);
    }

    static String dailyFileName(LocalDate date) {
        return PREFIX + date + SUFFIX;
    }

    static List<String> filesToDelete(List<String> names, int keep) {
        ArrayList<String> backups = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (isAutoBackupName(name)) backups.add(name);
            }
        }
        Collections.sort(backups);
        int removeCount = Math.max(0, backups.size() - Math.max(0, keep));
        return new ArrayList<>(backups.subList(0, removeCount));
    }

    static boolean isAutoBackupName(String name) {
        if (name == null || !name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return false;
        String date = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
        try {
            LocalDate.parse(date);
            return date.length() == 10;
        } catch (Exception ignored) {
            return false;
        }
    }
}
