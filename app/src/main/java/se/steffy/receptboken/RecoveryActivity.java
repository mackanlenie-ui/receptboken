package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Receptboken 2.0
 *
 * Recipe editing is rebuilt around one single page ScrollView. Long text fields
 * never have their own scrolling area; instead they expand with the text and the
 * whole page scrolls. This avoids nested scrolling and the One UI scrollbar
 * rendering crash seen on the S23 Ultra.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    private ScrollView pageScroll;

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 2.0", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::showSavedCrashIfAny, 500);
    }

    @Override
    void base() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), top(), dp(18), dp(220));
        root.setBackgroundColor(BG);
        pageScroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(pageScroll);
    }

    @Override
    void edit(Recipe old, boolean importedAsNew) {
        cancelTimer();
        base();
        selectedImage = old == null ? "" : old.image;

        Button back = btn("‹ Avbryt");
        back.setOnClickListener(v -> {
            if (old == null || importedAsNew) home();
            else detail(old, old.amount);
        });
        root.addView(back, new LinearLayout.LayoutParams(-2, -2));

        title(importedAsNew ? "Importerat recept – kontrollera" : old == null ? "Nytt recept" : "Redigera recept");
        root.addView(txt("Skriv direkt i fälten. Långa fält växer och hela sidan går att rulla medan tangentbordet är öppet.", 14, MUTED));

        EditText name = normalField("Namn på maträtten", old == null ? "" : old.name);
        EditText cat = normalField("Kategori", old == null ? "" : old.category);
        EditText time = numberField("Tid i minuter", old == null ? "" : String.valueOf(old.time));
        EditText amount = numberField("Antal", old == null ? "" : String.valueOf(old.amount));
        EditText unit = normalField("Enhet, t.ex. portioner, st eller bitar", old == null ? "portioner" : old.unit);

        TextView ingTitle = sectionTitle("Ingredienser");
        EditText ing = longField("En ingrediens per rad", old == null ? "" : old.ingredients);
        TextView stepsTitle = sectionTitle("Gör så här");
        EditText steps = longField("Skriv stegen här", old == null ? "" : old.steps);
        TextView tipsTitle = sectionTitle("Tips & förvaring");
        EditText tips = longField("Valfritt", old == null ? "" : old.tips);

        if (importedAsNew) {
            root.addView(txt("Kontrollera den automatiskt avlästa texten innan du sparar.", 14, MUTED));
        }

        Button image = btn(selectedImage.isEmpty() ? "📷 Välj bild" : "📷 Byt bild");
        full(image, 14);
        image.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, PICK_IMAGE);
            } catch (Exception e) {
                Toast.makeText(this, "Kunde inte öppna bildväljaren", Toast.LENGTH_SHORT).show();
            }
        });

        Button saveBtn = btn("Spara recept");
        full(saveBtn, 8);

        if (old != null && !importedAsNew) {
            Button del = btn("Ta bort recept");
            del.setBackground(round(RED));
            full(del, 3);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Ta bort receptet?")
                    .setNegativeButton("Avbryt", null)
                    .setPositiveButton("Ta bort", (d, w) -> {
                        recipes.remove(old);
                        removeRecipeFromWeek(old.name);
                        save();
                        saveWeek();
                        home();
                    }).show());
        }

        saveBtn.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) {
                name.setError("Skriv ett namn");
                name.requestFocus();
                return;
            }

            Recipe r = (old == null || importedAsNew) ? new Recipe() : old;
            String oldName = (old != null && !importedAsNew) ? old.name : "";

            r.name = name.getText().toString().trim();
            r.category = cat.getText().toString().trim().isEmpty() ? "Övrigt" : cat.getText().toString().trim();
            r.time = num(time, 0);
            r.amount = Math.max(1, num(amount, 1));
            r.unit = unit.getText().toString().trim().isEmpty() ? "portioner" : unit.getText().toString().trim();
            r.ingredients = ing.getText().toString().trim();
            r.steps = steps.getText().toString().trim();
            r.tips = tips.getText().toString().trim();
            r.image = selectedImage;

            if (old == null || importedAsNew) recipes.add(0, r);
            else if (!oldName.equals(r.name)) renameRecipeInWeek(oldName, r.name);

            save();
            saveWeek();
            detail(r, r.amount);
        });
    }

    private TextView sectionTitle(String text) {
        TextView label = txt(text, 18, TEXT);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(dp(4), dp(16), dp(4), dp(5));
        root.addView(label);
        return label;
    }

    private EditText normalField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setVerticalScrollBarEnabled(false);
        e.setHorizontalScrollBarEnabled(false);
        root.addView(e, fieldParams());
        return e;
    }

    private EditText numberField(String hint, String value) {
        EditText e = normalField(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    /**
     * A long field deliberately has no max-lines or fixed height. It expands as
     * text is added, so there is only one scroll owner: pageScroll.
     */
    private EditText longField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setSingleLine(false);
        e.setHorizontallyScrolling(false);
        e.setMinLines(5);
        e.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(round(CARD));
        e.setVerticalScrollBarEnabled(false);
        e.setHorizontalScrollBarEnabled(false);
        e.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(8));
        root.addView(e, p);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (e.hasFocus()) e.post(() -> revealCaretOnPage(e));
            }
        };
        e.addTextChangedListener(watcher);
        e.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) e.postDelayed(() -> revealCaretOnPage(e), 250);
        });
        e.setOnClickListener(v -> e.post(() -> revealCaretOnPage(e)));
        return e;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(2), 0, dp(3));
        return p;
    }

    /**
     * Ask the single outer ScrollView to reveal the caret line. The EditText
     * itself never scrolls, which eliminates nested-scroll conflicts.
     */
    private void revealCaretOnPage(EditText editor) {
        if (pageScroll == null || editor == null || editor.getLayout() == null) return;
        try {
            int offset = Math.max(0, Math.min(editor.getSelectionStart(), editor.length()));
            int line = editor.getLayout().getLineForOffset(offset);
            int top = editor.getLayout().getLineTop(line) + editor.getCompoundPaddingTop() - dp(28);
            int bottom = editor.getLayout().getLineBottom(line) + editor.getCompoundPaddingTop() + dp(70);
            Rect rect = new Rect(0, Math.max(0, top), Math.max(1, editor.getWidth()), bottom);
            pageScroll.requestChildRectangleOnScreen(editor, rect, true);
        } catch (Throwable ignored) {
        }
    }

    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String report = Log.getStackTraceString(error);
                getSharedPreferences(DIAG_PREFS, MODE_PRIVATE)
                        .edit().putString(LAST_CRASH, report).commit();
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void showSavedCrashIfAny() {
        String report = getSharedPreferences(DIAG_PREFS, MODE_PRIVATE)
                .getString(LAST_CRASH, "");
        if (report == null || report.trim().isEmpty()) return;

        getSharedPreferences(DIAG_PREFS, MODE_PRIVATE).edit().remove(LAST_CRASH).apply();

        TextView text = new TextView(this);
        text.setText(report);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setVerticalScrollBarEnabled(false);
        text.setHorizontalScrollBarEnabled(false);
        text.setPadding(dp(16), dp(10), dp(16), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("Felrapport från senaste kraschen")
                .setMessage("Om appen kraschar igen kan du kopiera rapporten och skicka den till mig.")
                .setView(text)
                .setNegativeButton("Stäng", null)
                .setPositiveButton("Kopiera", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Receptboken felrapport", report));
                        Toast.makeText(this, "Felrapport kopierad", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
