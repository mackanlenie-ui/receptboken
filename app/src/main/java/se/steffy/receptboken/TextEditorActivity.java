package se.steffy.receptboken;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TextEditorActivity extends Activity {

    public static final String EXTRA_TITLE = "editor_title";
    public static final String EXTRA_TEXT = "editor_text";

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Keep this activity deliberately simple. Android handles the keyboard
        // resizing itself; no custom insets or keyboard animation code is used.
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        try {
            buildEditor();
        } catch (Throwable error) {
            buildFallbackEditor();
        }
    }

    private void buildEditor() {
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) title = "Redigera text";
        String initial = getIntent().getStringExtra(EXTRA_TEXT);
        if (initial == null) initial = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(255, 248, 240));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(24);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(Color.rgb(32, 28, 24));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView help = new TextView(this);
        help.setText("Tryck i texten och skriv. Texten rullar automatiskt så raden du skriver på förblir synlig.");
        help.setTextSize(14);
        help.setTextColor(Color.rgb(110, 103, 97));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(6), 0, dp(8));
        root.addView(help, hp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button cancel = new Button(this);
        cancel.setText("Avbryt");
        Button done = new Button(this);
        done.setText("✓ Klar");

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1f);
        left.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1f);
        right.setMargins(dp(6), 0, 0, 0);
        buttons.addView(cancel, left);
        buttons.addView(done, right);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, 0, 0, dp(8));
        root.addView(buttons, bp);

        EditText editor = new EditText(this);
        editor.setText(initial);
        editor.setTextSize(18);
        editor.setTextColor(Color.rgb(45, 39, 35));
        editor.setHintTextColor(Color.rgb(130, 120, 112));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(editor, ep);

        setContentView(root);

        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        done.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_TEXT, editor.getText().toString());
            setResult(RESULT_OK, result);
            finish();
        });

        // Do not force the keyboard open. The user taps the text area when ready.
        // This avoids Samsung/One UI keyboard + window-inset crashes.
        editor.setSelection(editor.getText().length());
    }

    private void buildFallbackEditor() {
        String initial = getIntent().getStringExtra(EXTRA_TEXT);
        if (initial == null) initial = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.WHITE);

        EditText editor = new EditText(this);
        editor.setText(initial);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button done = new Button(this);
        done.setText("Klar");
        root.addView(done, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        done.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_TEXT, editor.getText().toString());
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
