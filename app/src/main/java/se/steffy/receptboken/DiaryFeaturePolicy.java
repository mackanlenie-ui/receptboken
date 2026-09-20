package se.steffy.receptboken;

public final class DiaryFeaturePolicy {
    public static final int TRASH_RETENTION_DAYS = 30;
    public static final long TRASH_RETENTION_MS = TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L;
    public static final int MAX_VERSIONS_PER_ENTRY = 20;
    public static final long VERSION_INTERVAL_MS = 5L * 60L * 1000L;

    private DiaryFeaturePolicy() {}

    public static boolean shouldCreateVersion(long lastVersionAt, long now) {
        return lastVersionAt <= 0L || now - lastVersionAt >= VERSION_INTERVAL_MS;
    }

    public static boolean isTrashExpired(long deletedAt, long now) {
        return deletedAt > 0L && now - deletedAt >= TRASH_RETENTION_MS;
    }
}
