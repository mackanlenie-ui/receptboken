package se.steffy.receptboken;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class AutoBackupPolicyTest {
    @Test
    public void createsBackupOnlyWhenDataChangedAndNoBackupExistsToday() {
        assertTrue(AutoBackupPolicy.shouldCreate(true, 200L, 100L, "2026-09-16", "2026-09-15"));
        assertFalse(AutoBackupPolicy.shouldCreate(true, 200L, 100L, "2026-09-16", "2026-09-16"));
        assertFalse(AutoBackupPolicy.shouldCreate(true, 100L, 100L, "2026-09-16", "2026-09-15"));
        assertFalse(AutoBackupPolicy.shouldCreate(false, 200L, 100L, "2026-09-16", "2026-09-15"));
    }

    @Test
    public void dailyFileNameIsStable() {
        assertEquals("Min_Dagbok_auto_2026-09-16.zip", AutoBackupPolicy.dailyFileName(LocalDate.of(2026, 9, 16)));
    }

    @Test
    public void pruningKeepsSevenNewestDailyBackups() {
        List<String> names = Arrays.asList(
                "Min_Dagbok_auto_2026-09-01.zip",
                "Min_Dagbok_auto_2026-09-08.zip",
                "Min_Dagbok_auto_2026-09-02.zip",
                "Min_Dagbok_auto_2026-09-07.zip",
                "Min_Dagbok_auto_2026-09-03.zip",
                "Min_Dagbok_auto_2026-09-06.zip",
                "Min_Dagbok_auto_2026-09-04.zip",
                "Min_Dagbok_auto_2026-09-05.zip",
                "notes.txt");

        assertEquals(Arrays.asList("Min_Dagbok_auto_2026-09-01.zip"), AutoBackupPolicy.filesToDelete(names, 7));
    }
}
