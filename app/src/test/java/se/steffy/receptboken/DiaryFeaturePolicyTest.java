package se.steffy.receptboken;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiaryFeaturePolicyTest {
    @Test
    public void versionHistoryIsRateLimited() {
        long now = 1_000_000L;
        assertTrue(DiaryFeaturePolicy.shouldCreateVersion(0L, now));
        assertFalse(DiaryFeaturePolicy.shouldCreateVersion(now - DiaryFeaturePolicy.VERSION_INTERVAL_MS + 1L, now));
        assertTrue(DiaryFeaturePolicy.shouldCreateVersion(now - DiaryFeaturePolicy.VERSION_INTERVAL_MS, now));
    }

    @Test
    public void trashExpiresAfterThirtyDays() {
        long now = 100L * 24L * 60L * 60L * 1000L;
        assertFalse(DiaryFeaturePolicy.isTrashExpired(now - DiaryFeaturePolicy.TRASH_RETENTION_MS + 1L, now));
        assertTrue(DiaryFeaturePolicy.isTrashExpired(now - DiaryFeaturePolicy.TRASH_RETENTION_MS, now));
    }

    @Test
    public void pinFormatRequiresFourToEightDigits() {
        assertTrue(PinManager.isValidFormat("1234"));
        assertTrue(PinManager.isValidFormat("12345678"));
        assertFalse(PinManager.isValidFormat("123"));
        assertFalse(PinManager.isValidFormat("123456789"));
        assertFalse(PinManager.isValidFormat("12ab"));
    }
}
