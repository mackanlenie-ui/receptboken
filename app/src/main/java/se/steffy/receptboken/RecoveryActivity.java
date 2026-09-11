package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Version 1.21 deliberately uses MainActivity's original recipe editor again.
 * The only editor change is a fixed-height, internally scrollable EditText for
 * long text. No overlay, dialog or second Activity is involved.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 1.21", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::showSavedCrashIfAny, 500);
    }

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = super.field(hint, val, multi);
        if (!multi) return editor;

        // Keep long recipe text in a normal Android EditText. A fixed height
        // makes Android scroll the contents inside the field as the cursor moves.
        editor.setMinLines(6);
        editor.setMaxLines(6);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(false);
        editor.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        // While the user drags inside a long field, let that field consume the
        // vertical scroll instead of the surrounding page ScrollView.
        editor.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||
                    event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        return editor;
    }

    @Override
    Button btn(String text) {
        Button button = super.btn(text);
        // Prevent a second tap on "Redigera" from landing on "Välj bild" when
        // the edit screen replaces the recipe details at almost the same spot.
        if (text != null && (text.contains("Välj bild") || text.contains("Byt bild"))) {
            button.setEnabled(false);
            button.postDelayed(() -> button.setEnabled(true), 1800);
        }
        return button;
    }

    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String report = Log.getStackTraceString(error);
                getSharedPreferences(DIAG_PREFS, MODE_PRIVATE)
                        .edit().putString(LAST_CRASH, report).commit();
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void showSavedCrashIfAny() {
        String report = getSharedPreferences(DIAG_PREFS, MODE_PRIVATE)
                .getString(LAST_CRASH, "");
        if (report == null || report.trim().isEmpty()) return;

        getSharedPreferences(DIAG_PREFS, MODE_PRIVATE)
                .edit().remove(LAST_CRASH).apply();

        TextView text = new TextView(this);
        text.setText(report);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setPadding(dp(16), dp(10), dp(16), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("Felrapport från senaste kraschen")
                .setMessage("Om appen fortfarande kraschar kan du kopiera rapporten och skicka den till mig. Då ser vi exakt vilken kodrad som orsakar felet.")
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
