package se.steffy.receptboken;

import android.view.Gravity;
import android.widget.EditText;

/**
 * Minimal launcher used to keep recipe editing stable.
 * It relies on MainActivity's proven screen/layout code and only limits
 * multi-line editor fields so long text scrolls inside the field.
 */
public class RecoveryActivity extends MainActivity {

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = super.field(hint, val, multi);
        if (multi) {
            editor.setMinLines(5);
            editor.setMaxLines(5);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setVerticalScrollBarEnabled(true);
            editor.setHorizontallyScrolling(false);
        }
        return editor;
    }
}
