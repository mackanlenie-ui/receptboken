package se.steffy.receptboken;

import android.content.Intent;
import android.view.Gravity;
import android.widget.EditText;

/**
 * Stable launcher. Long recipe fields are edited in a separate full-screen
 * TextEditorActivity. This avoids nested scrolling/dialog/keyboard conflicts.
 */
public class RecoveryActivity extends MainActivity {

    private static final int EDIT_LONG_TEXT = 9001;
    private EditText pendingLongEditor;

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = super.field(hint, val, multi);
        if (!multi) return editor;

        editor.setMinLines(4);
        editor.setMaxLines(4);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setVerticalScrollBarEnabled(true);
        editor.setHorizontallyScrolling(false);

        // Use this field as a preview only. Editing happens in a separate Activity,
        // where the EditText gets the whole available screen above the keyboard.
        editor.setFocusable(false);
        editor.setFocusableInTouchMode(false);
        editor.setCursorVisible(false);
        editor.setClickable(true);
        editor.setLongClickable(false);
        editor.setOnClickListener(v -> {
            pendingLongEditor = editor;
            Intent intent = new Intent(this, TextEditorActivity.class);
            intent.putExtra(TextEditorActivity.EXTRA_TITLE, hint);
            intent.putExtra(TextEditorActivity.EXTRA_TEXT, editor.getText().toString());
            startActivityForResult(intent, EDIT_LONG_TEXT);
        });
        return editor;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EDIT_LONG_TEXT) {
            if (resultCode == RESULT_OK && data != null && pendingLongEditor != null) {
                pendingLongEditor.setText(data.getStringExtra(TextEditorActivity.EXTRA_TEXT));
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
