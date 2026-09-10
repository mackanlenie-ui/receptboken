package se.steffy.receptboken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.BiometricPrompt;
import android.app.DatePickerDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class DiaryMainActivity extends Activity {
    private static final int PICK_IMAGE = 1001;
    private static final int DEVICE_CREDENTIAL = 2002;
    private static final int EXPORT_BACKUP = 3003;
    private static final int IMPORT_BACKUP = 3004;

    private static final String[] MOOD_EMOJI = {"😄", "🙂", "😐", "😔", "😢", "😡"};
    private static final String[] MOOD_NAME = {"Toppen", "Bra", "Okej", "Låg", "Ledsen", "Irriterad"};

    private final Locale sv = new Locale("sv", "SE");
    private final DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", sv);
    private final DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", sv);
    private final DateTimeFormatter monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", sv);

    private DiaryDb db;
    private SharedPreferences prefs;
    private FrameLayout pageHost;
    private String currentPage = "home";
    private boolean wide;
    private boolean dark;
    private int bg, surface, surface2, text, muted, accent, accentText, divider;

    private LocalDate editingDate = LocalDate.now();
    private Entry editorEntry;
    private YearMonth calendarMonth = YearMonth.now();
    private boolean unlocked = false;
    private boolean authShowing = false;
    private long lastPaused = 0L;
    private boolean firstResume = true;
    private Runnable deviceCredentialSuccess;
    private Runnable deviceCredentialCancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        dark = resolveDarkMode();
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        db = new DiaryDb(this);
        configureColors();
        applySecureFlag();
        buildShell();
        showHome();

        if (prefs.getBoolean("biometric_lock", false)) {
            unlocked = false;
            showLockPage();
            authenticate();
        } else {
            unlocked = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (prefs != null && prefs.getBoolean("biometric_lock", false) && lastPaused > 0 &&
                System.currentTimeMillis() - lastPaused > 30_000L && !authShowing) {
            unlocked = false;
            showLockPage();
            authenticate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) lastPaused = System.currentTimeMillis();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        wide = getResources().getConfiguration().screenWidthDp >= 720;
        buildShell();
        if (!unlocked && prefs.getBoolean("biometric_lock", false)) showLockPage();
        else renderCurrentPage();
    }

    @Override
    public void onBackPressed() {
        if (!unlocked && prefs.getBoolean("biometric_lock", false)) {
            finish();
            return;
        }
        if (!"home".equals(currentPage)) {
            hideKeyboard();
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private boolean resolveDarkMode() {
        String mode = prefs.getString("theme", "system");
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureColors() {
        if (dark) {
            bg = Color.rgb(21, 18, 24);
            surface = Color.rgb(32, 29, 36);
            surface2 = Color.rgb(43, 40, 48);
            text = Color.rgb(245, 239, 247);
            muted = Color.rgb(202, 196, 208);
            accent = Color.rgb(208, 188, 255);
            accentText = Color.rgb(56, 30, 88);
            divider = Color.rgb(73, 69, 79);
        } else {
            bg = Color.rgb(248, 246, 250);
            surface = Color.WHITE;
            surface2 = Color.rgb(241, 236, 244);
            text = Color.rgb(31, 29, 32);
            muted = Color.rgb(102, 94, 107);
            accent = systemAccentOr(Color.rgb(103, 80, 164));
            accentText = Color.WHITE;
            divider = Color.rgb(225, 219, 226);
        }
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = 0;
            if (!dark) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int systemAccentOr(int fallback) {
        if (Build.VERSION.SDK_INT >= 31) {
            int id = getResources().getIdentifier("system_accent1_600", "color", "android");
            if (id != 0) {
                try { return getColor(id); } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private void applySecureFlag() {
        if (prefs.getBoolean("secure_screen", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void buildShell() {
        wide = getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        if (wide) {
            root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(244), ViewGroup.LayoutParams.MATCH_PARENT));
            pageHost = new FrameLayout(this);
            pageHost.setBackgroundColor(bg);
            root.addView(pageHost, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        } else {
            root.addView(buildPhoneHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            pageHost = new FrameLayout(this);
            pageHost.setBackgroundColor(bg);
            root.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(buildBottomNav(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        }
        setContentView(root);
    }

    private View buildPhoneHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(12), 0);
        bar.setBackgroundColor(bg);

        TextView title = text("Min Dagbok", 22, true, text);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button settings = iconButton("⚙");
        settings.setContentDescription("Inställningar");
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private View buildSidebar() {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(16), dp(24), dp(16), dp(20));
        side.setBackgroundColor(surface);

        TextView icon = text("📖", 34, false, text);
        side.addView(icon);
        TextView title = text("Min Dagbok", 23, true, text);
        side.addView(title);
        TextView sub = text("Privat · lokalt sparad", 12, false, muted);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.bottomMargin = dp(28);
        side.addView(sub, sp);

        side.addView(sideNav("⌂  Hem", "home"));
        side.addView(sideNav("✎  Skriv idag", "editor"));
        side.addView(sideNav("▦  Kalender", "calendar"));
        side.addView(sideNav("⌕  Sök", "search"));
        side.addView(sideNav("★  Favoriter", "favorites"));

        Space fill = new Space(this);
        side.addView(fill, new LinearLayout.LayoutParams(1, 0, 1f));
        side.addView(sideNav("⚙  Inställningar", "settings"));
        return side;
    }

    private View sideNav(String label, String page) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(15);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), 0, dp(12), 0);
        b.setTextColor(page.equals(currentPage) ? (dark ? Color.BLACK : Color.WHITE) : text);
        b.setBackground(round(page.equals(currentPage) ? accent : Color.TRANSPARENT, 18, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.bottomMargin = dp(7);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> navigate(page));
        return b;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(4), dp(4), dp(7));
        nav.setBackgroundColor(surface);
        nav.addView(bottomNavButton("⌂\nHem", "home"), weight());
        nav.addView(bottomNavButton("▦\nKalender", "calendar"), weight());
        nav.addView(bottomNavButton("✎\nSkriv", "editor"), weight());
        nav.addView(bottomNavButton("⌕\nSök", "search"), weight());
        nav.addView(bottomNavButton("★\nFavoriter", "favorites"), weight());
        return nav;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private View bottomNavButton(String label, String page) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setTextColor(page.equals(currentPage) ? accent : muted);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> navigate(page));
        return b;
    }

    private void navigate(String page) {
        if (!unlocked && prefs.getBoolean("biometric_lock", false)) return;
        hideKeyboard();
        switch (page) {
            case "editor": openEditor(LocalDate.now()); break;
            case "calendar": showCalendar(); break;
            case "search": showSearch(); break;
            case "favorites": showFavorites(); break;
            case "settings": showSettings(); break;
            default: showHome(); break;
        }
    }

    private void renderCurrentPage() {
        switch (currentPage) {
            case "editor": renderEditor(); break;
            case "calendar": showCalendar(); break;
            case "search": showSearch(); break;
            case "favorites": showFavorites(); break;
            case "settings": showSettings(); break;
            default: showHome(); break;
        }
    }

    private void setPage(String page, View content) {
        currentPage = page;
        if (pageHost == null) return;
        pageHost.removeAllViews();
        pageHost.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private ScrollView pageScroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout pageColumn() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int horizontal = wide ? dp(36) : dp(18);
        c.setPadding(horizontal, dp(18), horizontal, dp(34));
        return c;
    }

    private void showHome() {
        currentPage = "home";
        if (wide) buildShell();
        LinearLayout c = pageColumn();

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 10 ? "God morgon" : hour < 17 ? "God dag" : "God kväll";
        c.addView(text(greeting, wide ? 34 : 30, true, text));
        TextView date = text(capitalize(LocalDate.now().format(longDate)), 15, false, muted);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.bottomMargin = dp(22);
        c.addView(date, dlp);

        Entry today = db.getByDate(LocalDate.now().format(iso));
        LinearLayout todayCard = card();
        todayCard.addView(text(today == null ? "Hur har din dag varit?" : MOOD_EMOJI[clampMood(today.mood)] + "  Dagens anteckning", 20, true, text));
        TextView todayBody = text(today == null ? "Skriv några rader om det som hänt, hur du mår eller något du vill minnas." : preview(today), 14, false, muted);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tblp.topMargin = dp(8);
        tblp.bottomMargin = dp(16);
        todayCard.addView(todayBody, tblp);
        Button write = primaryButton(today == null ? "✎  Skriv idag" : "✎  Fortsätt skriva");
        write.setOnClickListener(v -> openEditor(LocalDate.now()));
        todayCard.addView(write);
        c.addView(todayCard, cardLp());

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        String mm = YearMonth.now().toString();
        stats.addView(statCard(String.valueOf(db.countMonth(mm)), "den här månaden"), new LinearLayout.LayoutParams(0, dp(105), 1f));
        Space gap = new Space(this);
        stats.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        stats.addView(statCard(String.valueOf(db.countAll()), "totalt"), new LinearLayout.LayoutParams(0, dp(105), 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(26);
        c.addView(stats, slp);

        c.addView(sectionTitle("Senaste anteckningar"));
        ArrayList<Entry> recent = db.recent(wide ? 8 : 5);
        if (recent.isEmpty()) {
            c.addView(emptyState("Din dagbok är tom än så länge. Första anteckningen kan vara hur kort eller lång du vill."));
        } else {
            for (Entry e : recent) c.addView(entryCard(e), cardLpSmall());
        }
        setPage("home", pageScroll(c));
    }

    private View statCard(String value, String label) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(text(value, 28, true, accent));
        c.addView(text(label, 12, false, muted));
        return c;
    }

    private void openEditor(LocalDate date) {
        editingDate = date;
        Entry found = db.getByDate(date.format(iso));
        if (found == null) {
            found = new Entry();
            found.date = date.format(iso);
            found.mood = 1;
        }
        editorEntry = found;
        currentPage = "editor";
        if (wide) buildShell();
        renderEditor();
    }

    private void renderEditor() {
        if (editorEntry == null) {
            openEditor(editingDate == null ? LocalDate.now() : editingDate);
            return;
        }
        LinearLayout c = pageColumn();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Skriv dagbok", wide ? 32 : 27, true, text), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (editorEntry.favorite) heading.addView(text("★", 24, false, accent));
        c.addView(heading);

        Button dateButton = outlinedButton("📅  " + capitalize(editingDate.format(longDate)));
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        dateLp.topMargin = dp(10);
        dateLp.bottomMargin = dp(22);
        c.addView(dateButton, dateLp);
        dateButton.setOnClickListener(v -> chooseEditorDate());

        c.addView(text("Hur känns dagen?", 15, true, text));
        HorizontalScrollView moodScroll = new HorizontalScrollView(this);
        moodScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout moods = new LinearLayout(this);
        moods.setOrientation(LinearLayout.HORIZONTAL);
        moods.setPadding(0, dp(8), 0, dp(18));
        for (int i = 0; i < MOOD_EMOJI.length; i++) {
            final int mood = i;
            Button m = new Button(this);
            m.setAllCaps(false);
            m.setText(MOOD_EMOJI[i] + "\n" + MOOD_NAME[i]);
            m.setTextSize(12);
            m.setGravity(Gravity.CENTER);
            m.setTextColor(editorEntry.mood == i ? accentText : text);
            m.setBackground(round(editorEntry.mood == i ? accent : surface, 18, 1, divider));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(88), dp(68));
            mlp.rightMargin = dp(8);
            moods.addView(m, mlp);
            m.setOnClickListener(v -> {
                editorEntry.mood = mood;
                renderEditor();
            });
        }
        moodScroll.addView(moods);
        c.addView(moodScroll);

        EditText title = editField("Rubrik (valfritt)", false);
        title.setText(editorEntry.title);
        title.setSingleLine(true);
        title.setTextSize(19);
        c.addView(title, fieldLp(dp(58)));
        title.addTextChangedListener(simpleWatcher(s -> editorEntry.title = s));

        EditText body = editField("Vad har hänt idag? Skriv precis så mycket du vill…", true);
        body.setText(editorEntry.body);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setMinLines(wide ? 14 : 10);
        LinearLayout.LayoutParams bodyLp = fieldLp(wide ? dp(360) : dp(300));
        bodyLp.bottomMargin = dp(18);
        c.addView(body, bodyLp);
        body.addTextChangedListener(simpleWatcher(s -> editorEntry.body = s));

        LinearLayout favRow = new LinearLayout(this);
        favRow.setGravity(Gravity.CENTER_VERTICAL);
        favRow.setPadding(dp(4), 0, dp(4), dp(8));
        CheckBox favorite = new CheckBox(this);
        favorite.setText("  Markera som favorit");
        favorite.setTextColor(text);
        favorite.setTextSize(14);
        favorite.setChecked(editorEntry.favorite);
        favorite.setOnCheckedChangeListener((buttonView, isChecked) -> editorEntry.favorite = isChecked);
        favRow.addView(favorite);
        c.addView(favRow);

        c.addView(text("Bilder", 15, true, text));
        if (!editorEntry.photos.isEmpty()) {
            HorizontalScrollView photosScroll = new HorizontalScrollView(this);
            photosScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout photos = new LinearLayout(this);
            photos.setPadding(0, dp(8), 0, dp(8));
            for (int i = 0; i < editorEntry.photos.size(); i++) {
                final int index = i;
                photos.addView(photoCard(editorEntry.photos.get(i), () -> {
                    String removed = editorEntry.photos.remove(index);
                    try { new File(removed).delete(); } catch (Exception ignored) {}
                    renderEditor();
                }), new LinearLayout.LayoutParams(dp(148), dp(120)));
                Space g = new Space(this);
                photos.addView(g, new LinearLayout.LayoutParams(dp(8), 1));
            }
            photosScroll.addView(photos);
            c.addView(photosScroll);
        }

        Button addPhoto = outlinedButton("＋  Lägg till bild");
        addPhoto.setOnClickListener(v -> pickImage());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        alp.bottomMargin = dp(12);
        c.addView(addPhoto, alp);

        Button save = primaryButton("✓  Spara anteckning");
        save.setOnClickListener(v -> saveEditor());
        c.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView autosaveHint = text("Allt sparas lokalt på den här enheten.", 12, false, muted);
        autosaveHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ah = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ah.topMargin = dp(12);
        c.addView(autosaveHint, ah);

        setPage("editor", pageScroll(c));
    }

    private void chooseEditorDate() {
        LocalDate d = editingDate;
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            LocalDate chosen = LocalDate.of(year, month + 1, day);
            openEditor(chosen);
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        dialog.show();
    }

    private void saveEditor() {
        editorEntry.date = editingDate.format(iso);
        if (editorEntry.title.trim().isEmpty() && editorEntry.body.trim().isEmpty() && editorEntry.photos.isEmpty()) {
            Toast.makeText(this, "Skriv något eller lägg till en bild först", Toast.LENGTH_SHORT).show();
            return;
        }
        db.save(editorEntry);
        hideKeyboard();
        Toast.makeText(this, "Anteckningen är sparad", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    private View photoCard(String path, Runnable removeAction) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(surface2, 16, 1, divider));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { image.setImageURI(Uri.fromFile(new File(path))); } catch (Exception ignored) {}
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Button remove = iconButton("×");
        remove.setTextSize(20);
        remove.setTextColor(Color.WHITE);
        remove.setBackground(round(Color.argb(190, 30, 30, 30), 18, 0, Color.TRANSPARENT));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.END);
        rlp.setMargins(0, dp(6), dp(6), 0);
        frame.addView(remove, rlp);
        remove.setOnClickListener(v -> removeAction.run());
        return frame;
    }

    private void showCalendar() {
        currentPage = "calendar";
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Kalender", wide ? 32 : 27, true, text));
        c.addView(text("Dagar med en anteckning är markerade.", 13, false, muted));

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
        Set<Integer> marked = new HashSet<>();
        for (Entry e : entries) {
            try { marked.add(LocalDate.parse(e.date, iso).getDayOfMonth()); } catch (Exception ignored) {}
        }
        LocalDate first = calendarMonth.atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) grid.addView(new Space(this), gridCell());
        for (int day = 1; day <= calendarMonth.lengthOfMonth(); day++) {
            final LocalDate d = calendarMonth.atDay(day);
            boolean has = marked.contains(day);
            boolean today = d.equals(LocalDate.now());
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(has ? day + "\n•" : String.valueOf(day));
            b.setTextSize(13);
            b.setPadding(0, 0, 0, 0);
            b.setGravity(Gravity.CENTER);
            b.setTextColor(has ? accentText : (today ? accent : text));
            b.setBackground(round(has ? accent : Color.TRANSPARENT, 14, today && !has ? 2 : 0, accent));
            b.setOnClickListener(v -> openEditor(d));
            grid.addView(b, gridCell());
        }
        LinearLayout calendarCard = card();
        calendarCard.setPadding(dp(10), dp(12), dp(10), dp(12));
        calendarCard.addView(grid);
        c.addView(calendarCard, cardLp());

        c.addView(sectionTitle("Den här månaden"));
        if (entries.isEmpty()) c.addView(emptyState("Inga anteckningar den här månaden ännu."));
        else for (Entry e : entries) c.addView(entryCard(e), cardLpSmall());

        setPage("calendar", pageScroll(c));
    }

    private GridLayout.LayoutParams gridCell() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(54);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private void showSearch() {
        currentPage = "search";
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Sök", wide ? 32 : 27, true, text));
        TextView hint = text("Sök i rubriker, text eller datum.", 13, false, muted);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(14);
        c.addView(hint, hlp);

        EditText search = editField("Sök i dagboken…", false);
        search.setSingleLine(true);
        c.addView(search, fieldLp(dp(56)));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(16);
        c.addView(results, rlp);

        Runnable update = () -> {
            results.removeAllViews();
            String q = search.getText().toString().trim();
            ArrayList<Entry> found = q.isEmpty() ? db.recent(12) : db.search(q);
            if (found.isEmpty()) results.addView(emptyState(q.isEmpty() ? "Här visas dina senaste anteckningar." : "Ingen anteckning matchade sökningen."));
            else for (Entry e : found) results.addView(entryCard(e), cardLpSmall());
        };
        search.addTextChangedListener(simpleWatcher(s -> update.run()));
        update.run();

        setPage("search", pageScroll(c));
    }

    private void showFavorites() {
        currentPage = "favorites";
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Favoriter", wide ? 32 : 27, true, text));
        TextView sub = text("Anteckningar du vill hitta extra snabbt.", 13, false, muted);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(18);
        c.addView(sub, slp);
        ArrayList<Entry> fav = db.favorites();
        if (fav.isEmpty()) c.addView(emptyState("Tryck på ”Markera som favorit” när du skriver en anteckning så hamnar den här."));
        else for (Entry e : fav) c.addView(entryCard(e), cardLpSmall());
        setPage("favorites", pageScroll(c));
    }

    private void showSettings() {
        currentPage = "settings";
        if (wide) buildShell();
        LinearLayout c = pageColumn();
        c.addView(text("Inställningar", wide ? 32 : 27, true, text));
        TextView sub = text("Integritet, utseende och säkerhetskopia.", 13, false, muted);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(18);
        c.addView(sub, slp);

        LinearLayout security = card();
        security.addView(text("🔒  Integritet", 18, true, text));
        Switch biometric = new Switch(this);
        biometric.setText("Lås appen med fingeravtryck/biometri");
        biometric.setTextColor(text);
        biometric.setTextSize(14);
        biometric.setChecked(prefs.getBoolean("biometric_lock", false));
        LinearLayout.LayoutParams swlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        swlp.topMargin = dp(8);
        security.addView(biometric, swlp);
        biometric.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked && !prefs.getBoolean("biometric_lock", false)) {
                authenticateForEnable(biometric);
            } else if (!checked && prefs.getBoolean("biometric_lock", false)) {
                prefs.edit().putBoolean("biometric_lock", false).apply();
                Toast.makeText(this, "App-låset är avstängt", Toast.LENGTH_SHORT).show();
            }
        });

        Switch secure = new Switch(this);
        secure.setText("Blockera skärmbilder i appen");
        secure.setTextColor(text);
        secure.setTextSize(14);
        secure.setChecked(prefs.getBoolean("secure_screen", false));
        security.addView(secure, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        secure.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean("secure_screen", checked).apply();
            applySecureFlag();
        });
        c.addView(security, cardLp());

        LinearLayout appearance = card();
        appearance.addView(text("◐  Utseende", 18, true, text));
        String mode = prefs.getString("theme", "system");
        String modeName = "dark".equals(mode) ? "Mörkt" : "light".equals(mode) ? "Ljust" : "Följ telefonen";
        Button theme = outlinedButton("Tema: " + modeName);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        tlp.topMargin = dp(12);
        appearance.addView(theme, tlp);
        theme.setOnClickListener(v -> {
            String now = prefs.getString("theme", "system");
            String next = "system".equals(now) ? "light" : "light".equals(now) ? "dark" : "system";
            prefs.edit().putString("theme", next).apply();
            recreate();
        });
        TextView dex = text("DeX-läge anpassas automatiskt när fönstret blir bredare.", 12, false, muted);
        LinearLayout.LayoutParams dxlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dxlp.topMargin = dp(10);
        appearance.addView(dex, dxlp);
        c.addView(appearance, cardLp());

        LinearLayout backup = card();
        backup.addView(text("💾  Säkerhetskopia", 18, true, text));
        TextView explain = text("Spara anteckningar och dina tillagda bilder i en enda backupfil. Bra innan telefonbyte eller ominstallation.", 13, false, muted);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(8);
        elp.bottomMargin = dp(12);
        backup.addView(explain, elp);
        Button export = primaryButton("Exportera säkerhetskopia");
        export.setOnClickListener(v -> exportBackup());
        backup.addView(export, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        Button restore = outlinedButton("Återställ från säkerhetskopia");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rlp.topMargin = dp(10);
        backup.addView(restore, rlp);
        restore.setOnClickListener(v -> confirmImport());
        c.addView(backup, cardLp());

        LinearLayout about = card();
        about.addView(text("Min Dagbok  ·  version 1.0", 15, true, text));
        TextView privacy = text("Ingen inloggning och inget moln krävs. Dagboksdatabasen och bilderna ligger i appens privata lagring på enheten.", 12, false, muted);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(6);
        about.addView(privacy, plp);
        c.addView(about, cardLp());

        setPage("settings", pageScroll(c));
    }

    private View entryCard(Entry e) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(MOOD_EMOJI[clampMood(e.mood)], 22, false, text));
        TextView date = text(formatDate(e.date), 13, true, muted);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dlp.leftMargin = dp(9);
        top.addView(date, dlp);
        if (e.favorite) top.addView(text("★", 19, false, accent));
        card.addView(top);

        String titleValue = e.title == null || e.title.trim().isEmpty() ? "Min dag" : e.title.trim();
        TextView title = text(titleValue, 17, true, text);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(9);
        card.addView(title, tlp);

        if (e.body != null && !e.body.trim().isEmpty()) {
            TextView body = text(preview(e), 13, false, muted);
            body.setMaxLines(3);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(5);
            card.addView(body, blp);
        }
        if (!e.photos.isEmpty()) {
            TextView photoCount = text("📷 " + e.photos.size() + (e.photos.size() == 1 ? " bild" : " bilder"), 11, false, muted);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(8);
            card.addView(photoCount, plp);
        }
        card.setOnClickListener(v -> {
            try { openEditor(LocalDate.parse(e.date, iso)); } catch (Exception ignored) {}
        });
        return card;
    }

    private String preview(Entry e) {
        String s = e.body == null ? "" : e.body.trim().replace('\n', ' ');
        if (s.isEmpty()) s = e.title == null ? "" : e.title.trim();
        if (s.length() > 190) s = s.substring(0, 187) + "…";
        return s;
    }

    private String formatDate(String value) {
        try { return capitalize(LocalDate.parse(value, iso).format(shortDate)); }
        catch (Exception e) { return value; }
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "Min_Dagbok_backup_" + LocalDate.now().format(iso) + ".dagbokzip");
        startActivityForResult(i, EXPORT_BACKUP);
    }

    private void confirmImport() {
        new AlertDialog.Builder(this)
                .setTitle("Återställ säkerhetskopia?")
                .setMessage("Nuvarande anteckningar ersätts av innehållet i säkerhetskopian.")
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Välj fil", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, IMPORT_BACKUP);
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEVICE_CREDENTIAL) {
            authShowing = false;
            Runnable ok = deviceCredentialSuccess;
            Runnable cancel = deviceCredentialCancelled;
            deviceCredentialSuccess = null;
            deviceCredentialCancelled = null;
            if (resultCode == RESULT_OK) { if (ok != null) ok.run(); }
            else { if (cancel != null) cancel.run(); }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_IMAGE) {
                if (editorEntry == null) return;
                editorEntry.photos.add(copyImageToPrivate(uri));
                renderEditor();
            } else if (requestCode == EXPORT_BACKUP) {
                BackupManager.exportBackup(this, db, uri);
                Toast.makeText(this, "Säkerhetskopian är sparad", Toast.LENGTH_LONG).show();
            } else if (requestCode == IMPORT_BACKUP) {
                int count = BackupManager.importBackup(this, db, uri);
                Toast.makeText(this, count + " anteckningar återställdes", Toast.LENGTH_LONG).show();
                showHome();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Det gick inte att läsa eller spara filen", Toast.LENGTH_LONG).show();
        }
    }

    private String copyImageToPrivate(Uri uri) throws Exception {
        File dir = new File(getFilesDir(), "diary_images");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Kunde inte skapa bildmapp");
        String ext = extensionFromUri(uri);
        File out = new File(dir, "diary_" + UUID.randomUUID() + ext);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("Ingen bildström");
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
        }
        return out.getAbsolutePath();
    }

    private String extensionFromUri(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        return ".jpg";
    }

    private void authenticateForEnable(Switch toggle) {
        runBiometricPrompt("Aktivera app-lås", () -> {
            prefs.edit().putBoolean("biometric_lock", true).apply();
            toggle.setChecked(true);
            Toast.makeText(this, "Biometriskt app-lås är aktiverat", Toast.LENGTH_SHORT).show();
        }, () -> toggle.setChecked(false));
    }

    private void authenticate() {
        if (authShowing) return;
        runBiometricPrompt("Lås upp Min Dagbok", this::unlockAfterAuth, this::showLockPage);
    }

    private void runBiometricPrompt(String title, Runnable success, Runnable cancelled) {
        if (Build.VERSION.SDK_INT < 28) {
            launchDeviceCredential(success, cancelled);
            return;
        }
        authShowing = true;
        CancellationSignal signal = new CancellationSignal();
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle("Verifiera att det är du")
                    .setNegativeButton("Använd skärmlås", getMainExecutor(), (dialog, which) -> launchDeviceCredential(success, cancelled))
                    .build();
            prompt.authenticate(signal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    authShowing = false;
                    success.run();
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON) return;
                    authShowing = false;
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                        Toast.makeText(DiaryMainActivity.this, "Biometri kunde inte användas", Toast.LENGTH_SHORT).show();
                    }
                    cancelled.run();
                }
            });
        } catch (Exception e) {
            authShowing = false;
            launchDeviceCredential(success, cancelled);
        }
    }

    private void launchDeviceCredential(Runnable success, Runnable cancelled) {
        try {
            deviceCredentialSuccess = success;
            deviceCredentialCancelled = cancelled;
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent intent = km.createConfirmDeviceCredentialIntent("Lås upp Min Dagbok", "Använd telefonens skärmlås");
            if (intent != null) {
                authShowing = true;
                startActivityForResult(intent, DEVICE_CREDENTIAL);
            } else {
                authShowing = false;
                if (cancelled != null) cancelled.run();
                Toast.makeText(this, "Inget skärmlås är konfigurerat", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            authShowing = false;
            if (cancelled != null) cancelled.run();
        }
    }

    private void unlockAfterAuth() {
        unlocked = true;
        authShowing = false;
        lastPaused = 0L;
        showHome();
    }

    private void showLockPage() {
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
        Button unlock = primaryButton("Lås upp");
        unlock.setOnClickListener(v -> authenticate());
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(wide ? dp(280) : ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        c.addView(unlock, ulp);
        setPage("locked", c);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        c.setBackground(round(surface, 22, 1, divider));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private LinearLayout.LayoutParams cardLpSmall() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        lp.bottomMargin = dp(7);
        return lp;
    }

    private View emptyState(String message) {
        LinearLayout e = card();
        e.setGravity(Gravity.CENTER);
        TextView t = text(message, 13, false, muted);
        t.setGravity(Gravity.CENTER);
        e.addView(t);
        return e;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 18, true, text);
        t.setPadding(0, dp(4), 0, dp(5));
        return t;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private Button primaryButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(dark ? Color.BLACK : Color.WHITE);
        b.setBackground(round(accent, 18, 0, Color.TRANSPARENT));
        b.setPadding(dp(16), 0, dp(16), 0);
        return b;
    }

    private Button outlinedButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(14);
        b.setTextColor(text);
        b.setBackground(round(surface, 16, 1, divider));
        b.setPadding(dp(14), 0, dp(14), 0);
        return b;
    }

    private Button iconButton(String value) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(22);
        b.setTextColor(text);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(Color.TRANSPARENT, 18, 0, Color.TRANSPARENT));
        return b;
    }

    private EditText editField(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(muted);
        e.setTextColor(text);
        e.setTextSize(16);
        e.setBackground(round(surface, 18, 1, divider));
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (multiline) {
            e.setSingleLine(false);
            e.setHorizontallyScrolling(false);
        }
        return e;
    }

    private LinearLayout.LayoutParams fieldLp(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private TextWatcher simpleWatcher(java.util.function.Consumer<String> consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private int clampMood(int m) { return Math.max(0, Math.min(MOOD_EMOJI.length - 1, m)); }
    private String capitalize(String s) { return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(sv) + s.substring(1); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
