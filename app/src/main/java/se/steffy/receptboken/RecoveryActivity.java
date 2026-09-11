package se.steffy.receptboken;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Receptboken 2.1
 *
 * Long recipe text is edited in a dedicated full-screen editor. The recipe form
 * itself never contains nested scrolling text fields. This avoids the One UI
 * scrollbar crash and also guarantees that long text can be scrolled while the
 * keyboard is open.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    private ScrollView pageScroll;

    private static final class TextHolder {
        String value;
        TextView preview;
        TextHolder(String value) { this.value = value == null ? "" : value; }
    }

    private interface TextReceiver {
        void onDone(String value);
    }

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 2.1", Toast.LENGTH_SHORT).show();
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
        root.setPadding(dp(18), top(), dp(18), dp(80));
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
        root.addView(txt("Tryck på Redigera under ett långt fält. Då öppnas en stor textruta som går att rulla medan du skriver.", 14, MUTED));

        EditText name = normalField("Namn på maträtten", old == null ? "" : old.name);
        EditText cat = normalField("Kategori", old == null ? "" : old.category);
        EditText time = numberField("Tid i minuter", old == null ? "" : String.valueOf(old.time));
        EditText amount = numberField("Antal", old == null ? "" : String.valueOf(old.amount));
        EditText unit = normalField("Enhet, t.ex. portioner, st eller bitar", old == null ? "portioner" : old.unit);

        TextHolder ingredients = addLongSection("Ingredienser", "En ingrediens per rad", old == null ? "" : old.ingredients);
        TextHolder steps = addLongSection("Gör så här", "Skriv stegen här", old == null ? "" : old.steps);
        TextHolder tips = addLongSection("Tips & förvaring", "Valfritt", old == null ? "" : old.tips);

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
            r.ingredients = ingredients.value.trim();
            r.steps = steps.value.trim();
            r.tips = tips.value.trim();
            r.image = selectedImage;

            if (old == null || importedAsNew) recipes.add(0, r);
            else if (!oldName.equals(r.name)) renameRecipeInWeek(oldName, r.name);

            save();
            saveWeek();
            detail(r, r.amount);
        });
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(2), 0, dp(3));
        root.addView(e, p);
        return e;
    }

    private EditText numberField(String hint, String value) {
        EditText e = normalField(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private TextHolder addLongSection(String label, String emptyHint, String value) {
        TextHolder holder = new TextHolder(value);

        TextView title = txt(label, 18, TEXT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(4), dp(16), dp(4), dp(5));
        root.addView(title);

        TextView preview = txt(previewText(holder.value, emptyHint), 16, TEXT);
        preview.setGravity(Gravity.TOP | Gravity.START);
        preview.setMinLines(3);
        preview.setMaxLines(5);
        preview.setPadding(dp(14), dp(12), dp(14), dp(12));
        preview.setBackground(round(CARD));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(0, 0, 0, dp(6));
        root.addView(preview, pp);
        holder.preview = preview;

        Button editButton = btn("✏ Redigera " + label.toLowerCase());
        full(editButton, 0);

        View.OnClickListener open = v -> openFullScreenEditor(label, holder.value, newValue -> {
            holder.value = newValue;
            holder.preview.setText(previewText(newValue, emptyHint));
        });
        preview.setOnClickListener(open);
        editButton.setOnClickListener(open);

        return holder;
    }

    private String previewText(String value, String emptyHint) {
        if (value == null || value.trim().isEmpty()) return emptyHint;
        return value;
    }

    private void openFullScreenEditor(String titleText, String initialText, TextReceiver receiver) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), top(), dp(18), dp(16));
        panel.setBackgroundColor(BG);

        TextView heading = txt(titleText, 26, TEXT);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView help = txt("Skriv och svep upp eller ner direkt i textrutan. Texten rullar inne i rutan medan tangentbordet är öppet.", 14, MUTED);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(2), 0, dp(8));
        panel.addView(help, hp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = btn("‹ Avbryt");
        Button done = btn("✓ Klar");
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1f);
        right.setMargins(dp(5), 0, 0, 0);
        actions.addView(cancel, left);
        actions.addView(done, right);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.setMargins(0, 0, 0, dp(8));
        panel.addView(actions, ap);

        final EditText editor = new EditText(this);
        editor.setText(initialText == null ? "" : initialText);
        editor.setTextSize(18);
        editor.setTextColor(TEXT);
        editor.setHint("Skriv här…");
        editor.setHintTextColor(MUTED);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackground(round(CARD));
        editor.setVerticalScrollBarEnabled(false);
        editor.setHorizontalScrollBarEnabled(false);
        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editor.setMovementMethod(ScrollingMovementMethod.getInstance());
        editor.setScrollContainer(true);
        panel.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(BG));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        cancel.setOnClickListener(v -> dialog.dismiss());
        done.setOnClickListener(v -> {
            receiver.onDone(editor.getText().toString());
            dialog.dismiss();
        });

        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            editor.requestFocus();
            editor.setSelection(editor.length());
            editor.postDelayed(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                } catch (Throwable ignored) {}
            }, 180);
        });

        dialog.show();
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
