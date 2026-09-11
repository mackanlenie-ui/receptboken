package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Stable launcher with an in-app full-screen editor for long recipe text.
 * No second Activity is launched, so the recipe form stays alive underneath.
 */
public class RecoveryActivity extends MainActivity {

    private static final int EDIT_INGREDIENTS = 9101;
    private static final int EDIT_STEPS = 9102;
    private static final int EDIT_TIPS = 9103;

    private String currentIngredients = "";
    private String currentSteps = "";
    private String currentTips = "";
    private TextView ingredientsPreview;
    private TextView stepsPreview;
    private TextView tipsPreview;
    private View activeEditorOverlay;

    @Override
    public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 1.20", Toast.LENGTH_SHORT).show();
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
        if (importedAsNew) {
            root.addView(txt("Texten är automatiskt avläst. Kontrollera mängder och steg innan du sparar.", 14, MUTED));
        }

        Button image = btn(selectedImage.isEmpty() ? "📷 Välj bild" : "📷 Byt bild");
        full(image, 8);
        image.setEnabled(false);
        image.postDelayed(() -> image.setEnabled(true), 700);

        EditText name = field("Namn på maträtten", old == null ? "" : old.name, false);
        EditText cat = field("Kategori", old == null ? "" : old.category, false);
        EditText time = field("Tid i minuter", old == null ? "" : "" + old.time, false);
        EditText amount = field("Antal", old == null ? "" : "" + old.amount, false);
        EditText unit = field("Enhet, t.ex. portioner, st eller bitar", old == null ? "portioner" : old.unit, false);

        currentIngredients = old == null ? "" : old.ingredients;
        currentSteps = old == null ? "" : old.steps;
        currentTips = old == null ? "" : old.tips;

        ingredientsPreview = addLongField("Ingredienser", "Ingredienser – en per rad", currentIngredients, EDIT_INGREDIENTS);
        stepsPreview = addLongField("Gör så här", "Gör så här – steg för steg", currentSteps, EDIT_STEPS);
        tipsPreview = addLongField("Tips & förvaring", "Tips & förvaring (valfritt)", currentTips, EDIT_TIPS);

        Button saveBtn = btn("Spara recept");
        full(saveBtn, 16);

        if (old != null && !importedAsNew) {
            Button del = btn("Ta bort recept");
            del.setBackground(round(RED));
            full(del, 2);
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

        saveBtn.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) {
                name.setError("Skriv ett namn");
                return;
            }
            Recipe r = (old == null || importedAsNew) ? new Recipe() : old;
            String oldName = (old != null && !importedAsNew) ? old.name : "";
            r.name = name.getText().toString().trim();
            r.category = cat.getText().toString().trim().isEmpty() ? "Övrigt" : cat.getText().toString().trim();
            r.time = num(time, 0);
            r.amount = Math.max(1, num(amount, 1));
            r.unit = unit.getText().toString().trim().isEmpty() ? "portioner" : unit.getText().toString().trim();
            r.ingredients = currentIngredients.trim();
            r.steps = currentSteps.trim();
            r.tips = currentTips.trim();
            r.image = selectedImage;

            if (old == null || importedAsNew) recipes.add(0, r);
            else if (!oldName.equals(r.name)) renameRecipeInWeek(oldName, r.name);

            save();
            saveWeek();
            detail(r, r.amount);
        });
    }

    private TextView addLongField(String label, String editorTitle, String value, int requestCode) {
        TextView heading = txt(label, 18, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(4), dp(14), dp(4), dp(5));
        root.addView(heading);

        TextView preview = txt(previewText(value), 16, TEXT);
        preview.setGravity(Gravity.TOP | Gravity.START);
        preview.setMinLines(3);
        preview.setMaxLines(5);
        preview.setPadding(dp(14), dp(12), dp(14), dp(12));
        preview.setBackground(round(CARD));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(0, 0, 0, dp(6));
        root.addView(preview, pp);

        Button editButton = btn("✏ Redigera " + label.toLowerCase());
        full(editButton, 0);

        View.OnClickListener open = v -> openLongEditor(editorTitle, requestCode);
        preview.setOnClickListener(open);
        editButton.setOnClickListener(open);
        return preview;
    }

    private void openLongEditor(String editorTitle, int requestCode) {
        if (activeEditorOverlay != null) return;

        String text = requestCode == EDIT_INGREDIENTS ? currentIngredients
                : requestCode == EDIT_STEPS ? currentSteps : currentTips;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(BG);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), top(), dp(18), dp(18));
        panel.setBackgroundColor(BG);

        TextView heading = txt(editorTitle, 25, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView help = txt("Skriv direkt i rutan. När texten blir längre kan du rulla inne i rutan medan tangentbordet är öppet.", 14, MUTED);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.setMargins(0, dp(4), 0, dp(10));
        panel.addView(help, helpParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = btn("‹ Avbryt");
        Button done = btn("✓ Klar");
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1f);
        right.setMargins(dp(6), 0, 0, 0);
        actions.addView(cancel, left);
        actions.addView(done, right);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.setMargins(0, 0, 0, dp(10));
        panel.addView(actions, actionParams);

        EditText editor = new EditText(this);
        editor.setText(text == null ? "" : text);
        editor.setTextSize(18);
        editor.setTextColor(TEXT);
        editor.setHint("Skriv här…");
        editor.setHintTextColor(MUTED);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editor.setPadding(dp(14), dp(14), dp(14), dp(14));
        editor.setBackground(round(CARD));
        editor.setSelection(editor.getText().length());
        panel.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        overlay.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        addContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
        activeEditorOverlay = overlay;

        cancel.setOnClickListener(v -> closeEditorOverlay(editor));
        done.setOnClickListener(v -> {
            String value = editor.getText().toString();
            if (requestCode == EDIT_INGREDIENTS) currentIngredients = value;
            else if (requestCode == EDIT_STEPS) currentSteps = value;
            else currentTips = value;
            refreshPreviews();
            closeEditorOverlay(editor);
        });

        editor.requestFocus();
    }

    private void closeEditorOverlay(EditText editor) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && editor != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        } catch (Exception ignored) {}

        if (activeEditorOverlay != null) {
            ViewGroup parent = (ViewGroup) activeEditorOverlay.getParent();
            if (parent != null) parent.removeView(activeEditorOverlay);
            activeEditorOverlay = null;
        }
    }

    private String previewText(String value) {
        if (value == null || value.trim().isEmpty()) return "Tryck här för att skriva";
        return value;
    }

    private void refreshPreviews() {
        if (ingredientsPreview != null) ingredientsPreview.setText(previewText(currentIngredients));
        if (stepsPreview != null) stepsPreview.setText(previewText(currentSteps));
        if (tipsPreview != null) tipsPreview.setText(previewText(currentTips));
    }

    @Override
    public void onBackPressed() {
        if (activeEditorOverlay != null) {
            ViewGroup parent = (ViewGroup) activeEditorOverlay.getParent();
            if (parent != null) parent.removeView(activeEditorOverlay);
            activeEditorOverlay = null;
            return;
        }
        super.onBackPressed();
    }
}
