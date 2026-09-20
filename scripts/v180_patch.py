from pathlib import Path

java = Path('app/src/main/java/se/steffy/receptboken/DiaryMainActivity.java')
s = java.read_text(encoding='utf-8')

# Rensa papperskorgen enligt 30-dagarsregeln när appen startar.
old = '        db = new DiaryDb(this);\n        configureColors();'
new = '        db = new DiaryDb(this);\n        db.purgeOldTrash(System.currentTimeMillis());\n        configureColors();'
if old not in s:
    raise SystemExit('Kunde inte koppla papperskorgsrensning till appstart')
s = s.replace(old, new, 1)

# App-låset kan nu använda antingen fingeravtryck eller egen PIN.
old = '''        if (prefs.getBoolean("biometric_lock", false)) {
            unlocked = false;
            showLockPage();
            authenticate();
        } else {
            unlocked = true;
        }'''
new = '''        if (isAppLockEnabled()) {
            unlocked = false;
            showLockPage();
            if (prefs.getBoolean("biometric_lock", false)) authenticate();
        } else {
            unlocked = true;
        }'''
if old not in s:
    raise SystemExit('Kunde inte uppdatera appstartens låslogik')
s = s.replace(old, new, 1)

old = '''        if (prefs != null && prefs.getBoolean("biometric_lock", false) && lastPaused > 0 &&
                System.currentTimeMillis() - lastPaused > 30_000L && !authShowing) {
            unlocked = false;
            showLockPage();
            authenticate();
        }'''
new = '''        if (prefs != null && isAppLockEnabled() && lastPaused > 0 &&
                System.currentTimeMillis() - lastPaused > 30_000L && !authShowing) {
            unlocked = false;
            showLockPage();
            if (prefs.getBoolean("biometric_lock", false)) authenticate();
        }'''
if old not in s:
    raise SystemExit('Kunde inte uppdatera återlåsning')
s = s.replace(old, new, 1)

s = s.replace('if (!unlocked && prefs.getBoolean("biometric_lock", false)) showLockPage();',
              'if (!unlocked && isAppLockEnabled()) showLockPage();', 1)
s = s.replace('if (!unlocked && prefs.getBoolean("biometric_lock", false)) {',
              'if (!unlocked && isAppLockEnabled()) {', 1)
s = s.replace('if (!unlocked && prefs.getBoolean("biometric_lock", false)) return;',
              'if (!unlocked && isAppLockEnabled()) return;', 1)

# Papperskorgen får en egen vy.
old = '''            case "settings": showSettings(); break;
            case "reader": if (readingEntry != null) showReader(readingEntry); else showHome(); break;
            default: showHome(); break;'''
new = '''            case "settings": showSettings(); break;
            case "reader": if (readingEntry != null) showReader(readingEntry); else showHome(); break;
            case "trash": showTrash(); break;
            default: showHome(); break;'''
if old not in s:
    raise SystemExit('Kunde inte koppla papperskorgsvyn')
s = s.replace(old, new, 1)

# Hämta ett nöddraft om det är nyare än den senast sparade databaskopian.
start = s.index('    private void openEditor(LocalDate date) {')
end = s.index('    private void renderEditor() {', start)
new_open_editor = '''    private void openEditor(LocalDate date) {
        editingDate = date;
        String dateKey = date.format(iso);
        Entry found = db.getByDate(dateKey);
        if (found == null) {
            found = new Entry();
            found.date = dateKey;
            found.mood = 1;
        }
        Entry recovered = DraftManager.restoreIfNewer(this, dateKey, found);
        if (recovered != null) found = recovered;
        editorEntry = found;
        currentPage = "editor";
        if (wide) buildShell();
        renderEditor();
    }

'''
s = s[:start] + new_open_editor + s[end:]

old = '''    private void scheduleAutosave() {
        autosaveHandler.removeCallbacks(autosaveRunnable);
        autosaveHandler.postDelayed(autosaveRunnable, 850);
    }'''
new = '''    private void scheduleAutosave() {
        if (editorEntry != null) DraftManager.save(this, editorEntry);
        autosaveHandler.removeCallbacks(autosaveRunnable);
        autosaveHandler.postDelayed(autosaveRunnable, 850);
    }'''
if old not in s:
    raise SystemExit('Kunde inte förstärka utkastssparningen')
s = s.replace(old, new, 1)

start = s.index('    private void autosaveEditor(boolean showToast) {')
end = s.index('    private void chooseEditorDate() {', start)
new_autosave = '''    private void autosaveEditor(boolean showToast) {
        if (editorEntry == null || editingDate == null) return;
        autosaveHandler.removeCallbacks(autosaveRunnable);
        editorEntry.date = editingDate.format(iso);
        String titleValue = editorEntry.title == null ? "" : editorEntry.title.trim();
        String bodyValue = editorEntry.body == null ? "" : editorEntry.body.trim();
        boolean hasContent = !titleValue.isEmpty() || !bodyValue.isEmpty() || !editorEntry.photos.isEmpty();
        if (!hasContent && editorEntry.id <= 0) {
            DraftManager.clear(this, editorEntry.date);
            return;
        }
        DraftManager.save(this, editorEntry);
        long result = db.save(editorEntry);
        if (result >= 0) DraftManager.clear(this, editorEntry.date);
        if (showToast && result >= 0) Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();
    }

'''
s = s[:start] + new_autosave + s[end:]

old = '''        db.save(editorEntry);
        AutoBackupManager.createIfNeededAsync(this);
        hideKeyboard();
        Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();'''
new = '''        long savedId = db.save(editorEntry);
        if (savedId >= 0) DraftManager.clear(this, editorEntry.date);
        AutoBackupManager.createIfNeededAsync(this);
        hideKeyboard();
        Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();'''
if old not in s:
    raise SystemExit('Kunde inte slutföra nöddraft vid Spara')
s = s.replace(old, new, 1)

# Förbättrad kalender: humör direkt på dagen, dagens datum tydligt och läsvy för sparade dagar.
start = s.index('    private void showCalendar() {')
end = s.index('    private GridLayout.LayoutParams gridCell() {', start)
calendar = '''    private void showCalendar() {
        currentPage = "calendar";
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Kalender", wide ? 32 : 27, true, text));
        c.addView(text("Humöret visas på dagar där du har skrivit. Tryck på en sparad dag för att läsa.", 13, false, muted));

        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = iconButton("‹");
        Button next = iconButton("›");
        TextView month = text(capitalize(calendarMonth.format(monthTitle)), 18, true, text);
        month.setGravity(Gravity.CENTER);
        monthBar.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        monthBar.addView(month, new LinearLayout.LayoutParams(0, dp(48), 1f));
        monthBar.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams mb = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mb.topMargin = dp(14);
        c.addView(monthBar, mb);
        prev.setOnClickListener(v -> { calendarMonth = calendarMonth.minusMonths(1); showCalendar(); });
        next.setOnClickListener(v -> { calendarMonth = calendarMonth.plusMonths(1); showCalendar(); });

        if (!calendarMonth.equals(YearMonth.now())) {
            Button todayButton = outlinedButton("Hoppa till idag");
            LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            tbp.bottomMargin = dp(8);
            c.addView(todayButton, tbp);
            todayButton.setOnClickListener(v -> { calendarMonth = YearMonth.now(); showCalendar(); });
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setUseDefaultMargins(false);
        String[] weekdays = {"M", "T", "O", "T", "F", "L", "S"};
        for (String w : weekdays) {
            TextView h = text(w, 12, true, muted);
            h.setGravity(Gravity.CENTER);
            grid.addView(h, gridCell());
        }

        ArrayList<Entry> entries = db.month(calendarMonth.toString());
        java.util.HashMap<Integer, Entry> byDay = new java.util.HashMap<>();
        for (Entry e : entries) {
            try { byDay.put(LocalDate.parse(e.date, iso).getDayOfMonth(), e); } catch (Exception ignored) {}
        }

        LocalDate first = calendarMonth.atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) grid.addView(new Space(this), gridCell());

        for (int day = 1; day <= calendarMonth.lengthOfMonth(); day++) {
            final LocalDate d = calendarMonth.atDay(day);
            final Entry dayEntry = byDay.get(day);
            boolean has = dayEntry != null;
            boolean today = d.equals(LocalDate.now());
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(has ? day + "\\n" + MOOD_EMOJI[clampMood(dayEntry.mood)] : String.valueOf(day));
            b.setTextSize(has ? 12 : 13);
            b.setPadding(0, 0, 0, 0);
            b.setGravity(Gravity.CENTER);
            b.setTextColor(has ? accentText : (today ? accent : text));
            b.setBackground(round(has ? accent : Color.TRANSPARENT, 14, today && !has ? 2 : 0, accent));
            b.setContentDescription(has ? "Dag " + day + ", " + MOOD_NAME[clampMood(dayEntry.mood)] + ", har anteckning" : "Dag " + day);
            b.setOnClickListener(v -> {
                if (dayEntry != null) showReader(dayEntry);
                else openEditor(d);
            });
            grid.addView(b, gridCell());
        }

        LinearLayout calendarCard = card();
        calendarCard.setPadding(dp(10), dp(12), dp(10), dp(12));
        calendarCard.addView(grid);
        c.addView(calendarCard, cardLp());

        TextView summary = text(entries.size() + (entries.size() == 1 ? " anteckning den här månaden" : " anteckningar den här månaden"), 13, true, entries.isEmpty() ? muted : accent);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.bottomMargin = dp(12);
        c.addView(summary, sumLp);

        c.addView(sectionTitle("Den här månaden"));
        if (entries.isEmpty()) c.addView(emptyState("Inga anteckningar den här månaden ännu."));
        else for (Entry e : entries) c.addView(entryCard(e), cardLpSmall());

        setPage("calendar", pageScroll(c));
    }

'''
s = s[:start] + calendar + s[end:]

# PIN-inställningar i integritetskortet.
marker = '        Switch secure = new Switch(this);'
pin_settings = '''        TextView pinInfo = text(PinManager.hasPin(this)
                ? "Egen PIN-kod är aktiverad som alternativ till fingeravtryck."
                : "Du kan lägga till en egen PIN-kod på 4–8 siffror.", 12, false, muted);
        LinearLayout.LayoutParams pilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pilp.topMargin = dp(8);
        security.addView(pinInfo, pilp);

        Button pinButton = outlinedButton(PinManager.hasPin(this) ? "Ändra PIN-kod" : "Ställ in PIN-kod");
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        pinLp.topMargin = dp(10);
        security.addView(pinButton, pinLp);
        pinButton.setOnClickListener(v -> showSetPinDialog());

        if (PinManager.hasPin(this)) {
            Button removePin = outlinedButton("Ta bort PIN-kod");
            removePin.setTextColor(muted);
            LinearLayout.LayoutParams rplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            rplp.topMargin = dp(8);
            security.addView(removePin, rplp);
            removePin.setOnClickListener(v -> showVerifyPinDialog("Verifiera PIN-koden", () -> {
                PinManager.clear(this);
                Toast.makeText(this, "PIN-koden är borttagen", Toast.LENGTH_SHORT).show();
                showSettings();
            }));
        }

'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till PIN-inställningarna')
s = s.replace(marker, pin_settings + marker, 1)
s = s.replace('Toast.makeText(this, "App-låset är avstängt", Toast.LENGTH_SHORT).show();',
              'Toast.makeText(this, "Fingeravtryckslåset är avstängt", Toast.LENGTH_SHORT).show();', 1)

# Papperskorgskort i Inställningar.
marker = '        LinearLayout about = card();'
trash_settings = '''        LinearLayout trashCard = card();
        trashCard.addView(text("🗑  Papperskorg", 18, true, text));
        int trashCount = db.trashCount();
        TextView trashInfo = text(trashCount == 0
                ? "Papperskorgen är tom. Raderade anteckningar sparas här i 30 dagar."
                : trashCount + (trashCount == 1 ? " raderad anteckning. Sparas i 30 dagar." : " raderade anteckningar. Sparas i 30 dagar."),
                13, false, muted);
        LinearLayout.LayoutParams trInfoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trInfoLp.topMargin = dp(8);
        trInfoLp.bottomMargin = dp(12);
        trashCard.addView(trashInfo, trInfoLp);
        Button trashButton = outlinedButton("Öppna papperskorgen");
        trashButton.setEnabled(trashCount > 0);
        trashCard.addView(trashButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        trashButton.setOnClickListener(v -> showTrash());
        c.addView(trashCard, cardLp());

'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till papperskorgen i Inställningar')
s = s.replace(marker, trash_settings + marker, 1)

# Läsaren får versionshistorik och säker radering.
old = '''        edit.setOnClickListener(v -> {
            try { openEditor(LocalDate.parse(readingEntry.date, iso)); } catch (Exception ignored) {}
        });

        TextView hint = text("Scrolla upp och ned för att läsa hela anteckningen.", 12, false, muted);'''
new = '''        edit.setOnClickListener(v -> {
            try { openEditor(LocalDate.parse(readingEntry.date, iso)); } catch (Exception ignored) {}
        });

        int versionCount = db.versionsFor(readingEntry.date, DiaryFeaturePolicy.MAX_VERSIONS_PER_ENTRY).size();
        if (versionCount > 0) {
            Button history = outlinedButton("Versionshistorik (" + versionCount + ")");
            LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            historyLp.topMargin = dp(10);
            c.addView(history, historyLp);
            history.setOnClickListener(v -> showVersionHistory(readingEntry.date));
        }

        Button delete = outlinedButton("🗑  Flytta till papperskorg");
        delete.setTextColor(dark ? Color.rgb(255, 180, 175) : Color.rgb(150, 40, 40));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        deleteLp.topMargin = dp(10);
        c.addView(delete, deleteLp);
        delete.setOnClickListener(v -> confirmMoveToTrash(readingEntry));

        TextView hint = text("Scrolla upp och ned för att läsa hela anteckningen.", 12, false, muted);'''
if old not in s:
    raise SystemExit('Kunde inte lägga historik och papperskorg i läsvyn')
s = s.replace(old, new, 1)

# Hjälpmetoder för historik, papperskorg och PIN.
marker = '    private String preview(Entry e) {'
helpers = '''    private void showVersionHistory(String date) {
        ArrayList<Entry> versions = db.versionsFor(date, DiaryFeaturePolicy.MAX_VERSIONS_PER_ENTRY);
        if (versions.isEmpty()) {
            Toast.makeText(this, "Ingen äldre version finns ännu", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[versions.size()];
        for (int i = 0; i < versions.size(); i++) {
            Entry version = versions.get(i);
            String titleValue = version.title == null || version.title.trim().isEmpty() ? "Min dag" : version.title.trim();
            labels[i] = formatDateTime(version.updated) + " · " + titleValue;
        }
        new AlertDialog.Builder(this)
                .setTitle("Versionshistorik")
                .setMessage("Appen sparar högst 20 äldre versioner per anteckning.")
                .setItems(labels, (dialog, which) -> confirmRestoreVersion(versions.get(which)))
                .setNegativeButton("Stäng", null)
                .show();
    }

    private void confirmRestoreVersion(Entry version) {
        String previewText = version.body == null ? "" : version.body.trim();
        if (previewText.length() > 220) previewText = previewText.substring(0, 217) + "…";
        String message = "Den nuvarande texten sparas i historiken innan den äldre versionen återställs.";
        if (!previewText.isEmpty()) message += "\\n\\n" + previewText;
        new AlertDialog.Builder(this)
                .setTitle("Återställ den här versionen?")
                .setMessage(message)
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Återställ", (dialog, which) -> {
                    if (db.restoreVersion(version)) {
                        DraftManager.clear(this, version.date);
                        AutoBackupManager.createIfNeededAsync(this);
                        Entry fresh = db.getByDate(version.date);
                        Toast.makeText(this, "Den äldre versionen är återställd", Toast.LENGTH_SHORT).show();
                        if (fresh != null) showReader(fresh); else showHome();
                    }
                })
                .show();
    }

    private void confirmMoveToTrash(Entry entry) {
        if (entry == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Flytta till papperskorgen?")
                .setMessage("Anteckningen kan återställas i 30 dagar.")
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Flytta", (dialog, which) -> {
                    long id = db.moveToTrash(entry.date);
                    if (id >= 0) {
                        DraftManager.clear(this, entry.date);
                        AutoBackupManager.createIfNeededAsync(this);
                        readingEntry = null;
                        Toast.makeText(this, "Anteckningen ligger i papperskorgen", Toast.LENGTH_SHORT).show();
                        showHome();
                    }
                })
                .show();
    }

    private void showTrash() {
        currentPage = "trash";
        db.purgeOldTrash(System.currentTimeMillis());
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Papperskorg", wide ? 32 : 27, true, text));
        TextView info = text("Raderade anteckningar sparas i 30 dagar och tas sedan bort permanent.", 13, false, muted);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.bottomMargin = dp(14);
        c.addView(info, infoLp);

        Button back = outlinedButton("←  Till Inställningar");
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        backLp.bottomMargin = dp(14);
        c.addView(back, backLp);
        back.setOnClickListener(v -> showSettings());

        ArrayList<DiaryDb.TrashItem> items = db.trashItems();
        if (items.isEmpty()) {
            c.addView(emptyState("Papperskorgen är tom."));
        } else {
            for (DiaryDb.TrashItem item : items) {
                LinearLayout box = card();
                String titleValue = item.entry.title == null || item.entry.title.trim().isEmpty() ? "Min dag" : item.entry.title.trim();
                box.addView(text(MOOD_EMOJI[clampMood(item.entry.mood)] + "  " + titleValue, 17, true, text));
                TextView dates = text(formatDate(item.entry.date) + " · raderad " + formatDateTime(item.deletedAt), 12, false, muted);
                LinearLayout.LayoutParams datesLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                datesLp.topMargin = dp(5);
                datesLp.bottomMargin = dp(12);
                box.addView(dates, datesLp);

                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                Button restore = primaryButton("Återställ");
                Button forever = outlinedButton("Ta bort permanent");
                forever.setTextColor(dark ? Color.rgb(255, 180, 175) : Color.rgb(150, 40, 40));
                actions.addView(restore, new LinearLayout.LayoutParams(0, dp(48), 1f));
                Space gap = new Space(this);
                actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
                actions.addView(forever, new LinearLayout.LayoutParams(0, dp(48), 1f));
                box.addView(actions);

                restore.setOnClickListener(v -> confirmRestoreTrash(item));
                forever.setOnClickListener(v -> confirmDeleteTrashForever(item));
                c.addView(box, cardLpSmall());
            }
        }
        setPage("trash", pageScroll(c));
    }

    private void confirmRestoreTrash(DiaryDb.TrashItem item) {
        boolean conflict = db.getByDate(item.entry.date) != null;
        new AlertDialog.Builder(this)
                .setTitle("Återställ anteckningen?")
                .setMessage(conflict
                        ? "Det finns redan en anteckning på samma datum. Den kommer att ersättas, men sparas först i versionshistoriken."
                        : "Anteckningen flyttas tillbaka till dagboken.")
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Återställ", (dialog, which) -> {
                    if (db.restoreTrash(item.trashId)) {
                        AutoBackupManager.createIfNeededAsync(this);
                        Toast.makeText(this, "Anteckningen är återställd", Toast.LENGTH_SHORT).show();
                        showTrash();
                    }
                })
                .show();
    }

    private void confirmDeleteTrashForever(DiaryDb.TrashItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Ta bort permanent?")
                .setMessage("Det här går inte att ångra från papperskorgen.")
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Ta bort", (dialog, which) -> {
                    if (db.deleteTrashPermanently(item.trashId)) {
                        Toast.makeText(this, "Anteckningen är permanent borttagen", Toast.LENGTH_SHORT).show();
                        showTrash();
                    }
                })
                .show();
    }

    private String formatDateTime(long millis) {
        try {
            java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", sv);
            return java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault()).format(formatter);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isAppLockEnabled() {
        return prefs != null && (prefs.getBoolean("biometric_lock", false) || PinManager.hasPin(this));
    }

    private android.widget.EditText pinInput(String hint) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(hint);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));
        input.setBackground(round(surface, 16, 1, divider));
        return input;
    }

    private void showSetPinDialog() {
        if (PinManager.hasPin(this)) {
            showVerifyPinDialog("Verifiera nuvarande PIN", this::showNewPinDialog);
        } else {
            showNewPinDialog();
        }
    }

    private void showNewPinDialog() {
        final android.widget.EditText first = pinInput("Ny PIN-kod, 4–8 siffror");
        new AlertDialog.Builder(this)
                .setTitle("Ställ in PIN-kod")
                .setView(first)
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Fortsätt", (dialog, which) -> {
                    String value = first.getText().toString();
                    if (!PinManager.isValidFormat(value)) {
                        Toast.makeText(this, "PIN-koden måste vara 4–8 siffror", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showConfirmNewPinDialog(value);
                })
                .show();
    }

    private void showConfirmNewPinDialog(String firstPin) {
        final android.widget.EditText second = pinInput("Upprepa PIN-koden");
        new AlertDialog.Builder(this)
                .setTitle("Bekräfta PIN-kod")
                .setView(second)
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Spara", (dialog, which) -> {
                    String value = second.getText().toString();
                    if (!firstPin.equals(value)) {
                        Toast.makeText(this, "PIN-koderna stämmer inte överens", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        PinManager.setPin(this, value);
                        Toast.makeText(this, "PIN-koden är aktiverad", Toast.LENGTH_SHORT).show();
                        showSettings();
                    } catch (Exception e) {
                        Toast.makeText(this, "PIN-koden kunde inte sparas", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showVerifyPinDialog(String titleValue, Runnable success) {
        final android.widget.EditText input = pinInput("PIN-kod");
        new AlertDialog.Builder(this)
                .setTitle(titleValue)
                .setView(input)
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Verifiera", (dialog, which) -> {
                    if (PinManager.verify(this, input.getText().toString())) {
                        if (success != null) success.run();
                    } else {
                        Toast.makeText(this, "Fel PIN-kod", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showPinUnlock() {
        showVerifyPinDialog("Lås upp med PIN", this::unlockAfterAuth);
    }

'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till säkerhetsfunktionerna')
s = s.replace(marker, helpers + marker, 1)

# Ny låsskärm med val mellan biometri och PIN.
start = s.index('    private void showLockPage() {')
end = s.index('    private LinearLayout card() {', start)
lock_page = '''    private void showLockPage() {
        currentPage = "locked";
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(30), dp(30), dp(30), dp(30));
        c.setBackgroundColor(bg);
        c.addView(text("🔒", 52, false, text));
        TextView title = text("Min Dagbok är låst", 24, true, text);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(12);
        c.addView(title, tlp);
        TextView sub = text("Lås upp för att läsa eller skriva i dagboken.", 14, false, muted);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(7);
        slp.bottomMargin = dp(22);
        c.addView(sub, slp);

        int buttonWidth = wide ? dp(320) : ViewGroup.LayoutParams.MATCH_PARENT;
        if (prefs.getBoolean("biometric_lock", false)) {
            Button biometricUnlock = primaryButton("Lås upp med fingeravtryck");
            biometricUnlock.setOnClickListener(v -> authenticate());
            c.addView(biometricUnlock, new LinearLayout.LayoutParams(buttonWidth, dp(54)));
        }
        if (PinManager.hasPin(this)) {
            Button pinUnlock = outlinedButton("Använd PIN-kod");
            LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(buttonWidth, dp(54));
            pinLp.topMargin = dp(10);
            c.addView(pinUnlock, pinLp);
            pinUnlock.setOnClickListener(v -> showPinUnlock());
        }
        setPage("locked", c);
    }

'''
s = s[:start] + lock_page + s[end:]

# Versionsnummer.
if 'Min Dagbok  ·  version 1.7.0' not in s:
    raise SystemExit('Kunde inte uppdatera versionsnamnet')
s = s.replace('Min Dagbok  ·  version 1.7.0', 'Min Dagbok  ·  version 1.8.0', 1)
java.write_text(s, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
if "versionCode 7" not in g or "versionName '1.7.0'" not in g:
    raise SystemExit('Kunde inte hitta v1.7.0 i build.gradle')
g = g.replace('versionCode 7', 'versionCode 8', 1)
g = g.replace("versionName '1.7.0'", "versionName '1.8.0'", 1)
gradle.write_text(g, encoding='utf-8')
