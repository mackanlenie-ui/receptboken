from pathlib import Path

java = Path('app/src/main/java/se/steffy/receptboken/DiaryMainActivity.java')
s = java.read_text(encoding='utf-8')

# Skapa en automatisk återställningspunkt när appen lämnas efter en ändring.
old = '''        if ("editor".equals(currentPage)) autosaveEditor(false);\n        if (!isChangingConfigurations()) lastPaused = System.currentTimeMillis();'''
new = '''        if ("editor".equals(currentPage)) autosaveEditor(false);\n        AutoBackupManager.createIfNeededAsync(this);\n        if (!isChangingConfigurations()) lastPaused = System.currentTimeMillis();'''
if old not in s:
    raise SystemExit('Kunde inte koppla automatisk backup till onPause')
s = s.replace(old, new, 1)

# En uttrycklig Spara ska också kunna skapa dagens återställningspunkt direkt.
old = '''        db.save(editorEntry);\n        hideKeyboard();\n        Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();'''
new = '''        db.save(editorEntry);\n        AutoBackupManager.createIfNeededAsync(this);\n        hideKeyboard();\n        Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();'''
if old not in s:
    raise SystemExit('Kunde inte koppla automatisk backup till Spara')
s = s.replace(old, new, 1)

# Visa status och återställningspunkter i den befintliga backup-sektionen.
old = '''        restore.setOnClickListener(v -> confirmImport());\n        c.addView(backup, cardLp());'''
new = '''        restore.setOnClickListener(v -> confirmImport());\n\n        int restorePointCount = AutoBackupManager.listBackups(this).size();\n        TextView autoInfo = text("Automatiska återställningspunkter: På · " + restorePointCount + " av 7 sparade. Skapas högst en gång per dag efter en ändring. De ligger lokalt i appen och försvinner vid avinstallation.", 12, false, muted);\n        LinearLayout.LayoutParams ailp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n        ailp.topMargin = dp(14);\n        ailp.bottomMargin = dp(10);\n        backup.addView(autoInfo, ailp);\n\n        Button autoRestore = outlinedButton("Visa automatiska återställningspunkter");\n        backup.addView(autoRestore, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));\n        autoRestore.setOnClickListener(v -> showAutomaticRestorePoints());\n        c.addView(backup, cardLp());'''
if old not in s:
    raise SystemExit('Kunde inte lägga till återställningspunkter i Inställningar')
s = s.replace(old, new, 1)

marker = '    private void exportBackup() {'
methods = '''    private void showAutomaticRestorePoints() {\n        java.util.List<File> backups = AutoBackupManager.listBackups(this);\n        if (backups.isEmpty()) {\n            Toast.makeText(this, "Inga automatiska återställningspunkter ännu", Toast.LENGTH_LONG).show();\n            return;\n        }\n\n        String[] labels = new String[backups.size()];\n        for (int i = 0; i < backups.size(); i++) {\n            String dateValue = AutoBackupManager.dateFromFile(backups.get(i));\n            try {\n                labels[i] = capitalize(LocalDate.parse(dateValue, iso).format(shortDate));\n            } catch (Exception ignored) {\n                labels[i] = dateValue;\n            }\n        }\n\n        new AlertDialog.Builder(this)\n                .setTitle("Automatiska återställningspunkter")\n                .setMessage("Välj vilken dag du vill gå tillbaka till.")\n                .setItems(labels, (dialog, which) -> confirmAutomaticRestore(backups.get(which), labels[which]))\n                .setNegativeButton("Stäng", null)\n                .show();\n    }\n\n    private void confirmAutomaticRestore(File file, String label) {\n        new AlertDialog.Builder(this)\n                .setTitle("Återställ " + label + "?")\n                .setMessage("Nuvarande anteckningar ersätts av den valda återställningspunkten. Din manuella export påverkas inte.")\n                .setNegativeButton("Avbryt", null)\n                .setPositiveButton("Återställ", (dialog, which) -> {\n                    try {\n                        int count = AutoBackupManager.restore(this, db, file);\n                        Toast.makeText(this, count + " anteckningar återställdes", Toast.LENGTH_LONG).show();\n                        showHome();\n                    } catch (Exception e) {\n                        String reason = e.getMessage();\n                        if (reason == null || reason.trim().isEmpty()) reason = e.getClass().getSimpleName();\n                        Toast.makeText(this, "Återställningen misslyckades: " + reason, Toast.LENGTH_LONG).show();\n                    }\n                })\n                .show();\n    }\n\n'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till återställningsdialogerna')
s = s.replace(marker, methods + marker, 1)

if 'Min Dagbok  ·  version 1.6.0' not in s:
    raise SystemExit('Kunde inte uppdatera versionsnamnet i Inställningar')
s = s.replace('Min Dagbok  ·  version 1.6.0', 'Min Dagbok  ·  version 1.7.0', 1)
java.write_text(s, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
if "versionCode 6" not in g or "versionName '1.6.0'" not in g:
    raise SystemExit('Kunde inte hitta v1.6.0 i build.gradle')
g = g.replace('versionCode 6', 'versionCode 7', 1)
g = g.replace("versionName '1.6.0'", "versionName '1.7.0'", 1)
gradle.write_text(g, encoding='utf-8')
