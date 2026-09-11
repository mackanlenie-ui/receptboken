package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Stable launcher with a recipe editor that keeps long text in a separate
 * full-screen editor. The image picker is briefly disabled when the screen
 * opens so a previous tap cannot accidentally fall through onto "Välj bild".
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

    @Override
    public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 1.19", Toast.LENGTH_SHORT).show();
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

        Runnable open = () -> openLongEditor(editorTitle, requestCode);
        preview.setOnClickListener(v -> open.run());
        editButton.setOnClickListener(v -> open.run());
        return preview;
    }

    private void openLongEditor(String editorTitle, int requestCode) {
        String text = requestCode == EDIT_INGREDIENTS ? currentIngredients
                : requestCode == EDIT_STEPS ? currentSteps : currentTips;
        try {
            Intent intent = new Intent(this, TextEditorActivity.class);
            intent.putExtra(TextEditorActivity.EXTRA_TITLE, editorTitle);
            intent.putExtra(TextEditorActivity.EXTRA_TEXT, text);
            startActivityForResult(intent, requestCode);
        } catch (Throwable error) {
            Toast.makeText(this, "Kunde inte öppna textredigeraren", Toast.LENGTH_LONG).show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_INGREDIENTS || requestCode == EDIT_STEPS || requestCode == EDIT_TIPS) {
            if (resultCode == RESULT_OK && data != null) {
                String value = data.getStringExtra(TextEditorActivity.EXTRA_TEXT);
                if (value == null) value = "";
                if (requestCode == EDIT_INGREDIENTS) currentIngredients = value;
                else if (requestCode == EDIT_STEPS) currentSteps = value;
                else currentTips = value;
                refreshPreviews();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
