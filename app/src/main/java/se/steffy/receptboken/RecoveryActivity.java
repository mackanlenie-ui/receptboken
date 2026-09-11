package se.steffy.receptboken;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Stable launcher. Long recipe fields are edited in a dedicated large editor
 * so the text stays above the keyboard and can be scrolled while typing.
 */
public class RecoveryActivity extends MainActivity {

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = super.field(hint, val, multi);
        if (!multi) return editor;

        editor.setMinLines(4);
        editor.setMaxLines(4);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setVerticalScrollBarEnabled(true);
        editor.setHorizontallyScrolling(false);

        // The compact field is only a preview. Tapping it opens a large,
        // dedicated editor that remains visible above the keyboard.
        editor.setFocusable(false);
        editor.setFocusableInTouchMode(false);
        editor.setCursorVisible(false);
        editor.setClickable(true);
        editor.setLongClickable(false);
        editor.setOnClickListener(v -> openLargeEditor(editor, hint));
        return editor;
    }

    private void openLargeEditor(EditText target, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        TextView help = new TextView(this);
        help.setText("Skriv och rulla i texten. Tryck Klar när du är färdig.");
        help.setTextSize(14);
        help.setTypeface(null, Typeface.NORMAL);
        help.setPadding(0, 0, 0, dp(8));
        box.addView(help, new LinearLayout.LayoutParams(-1, -2));

        EditText large = new EditText(this);
        large.setHint(hint);
        large.setText(target.getText());
        large.setSelection(large.getText().length());
        large.setTextSize(17);
        large.setGravity(Gravity.TOP | Gravity.START);
        large.setSingleLine(false);
        large.setHorizontallyScrolling(false);
        large.setVerticalScrollBarEnabled(true);
        large.setScrollbarFadingEnabled(false);
        large.setMovementMethod(ScrollingMovementMethod.getInstance());
        large.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        large.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(large, new LinearLayout.LayoutParams(-1, dp(330)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(hint)
                .setView(box)
                .setNegativeButton("Avbryt", null)
                .setPositiveButton("Klar", (d, which) -> target.setText(large.getText().toString()))
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            large.requestFocus();
            large.post(() -> large.setSelection(large.getText().length()));
        });
        dialog.show();
    }
}
