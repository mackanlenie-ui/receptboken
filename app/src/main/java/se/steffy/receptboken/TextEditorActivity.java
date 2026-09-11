package se.steffy.receptboken;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
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
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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
        help.setText("Skriv och rulla i texten. Den rad du skriver på hålls synlig ovanför tangentbordet.");
        help.setTextSize(14);
        help.setTextColor(Color.rgb(110, 103, 97));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(6), 0, dp(12));
        root.addView(help, hp);

        EditText editor = new EditText(this);
        editor.setText(initial);
        editor.setTextSize(18);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(editor, ep);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);

        Button cancel = new Button(this);
        cancel.setText("Avbryt");
        Button done = new Button(this);
        done.setText("Klar");
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.setMargins(dp(6), 0, 0, 0);
        buttons.addView(cancel, bp1);
        buttons.addView(done, bp2);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

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

        editor.requestFocus();
        editor.postDelayed(() -> {
            editor.setSelection(editor.getText().length());
            if (editor.getText().length() > 0) {
                int line = Math.max(0, editor.getLineCount() - 1);
                editor.setSelection(editor.getText().length());
            }
        }, 120);
    }
}
