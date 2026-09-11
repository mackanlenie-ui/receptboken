package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
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
 * Receptboken 1.23.
 *
 * One UI on the test phone crashes if Android tries to draw a ScrollBarDrawable,
 * so every visual scrollbar stays disabled. Long recipe fields still scroll
 * internally. The active field is also moved above the keyboard and the cursor
 * line is kept visible while the user types.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    private ScrollView pageScroll;

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 1.23", Toast.LENGTH_SHORT).show();
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
        root.setPadding(dp(18), top(), dp(18), dp(28));
        root.setBackgroundColor(BG);
        pageScroll.addView(root);
        setContentView(pageScroll);
    }

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = super.field(hint, val, multi);
        if (!multi) return editor;

        // Fixed-height text area. Text itself scrolls inside the field, but no
        // visual scrollbar is requested from Android (One UI crash workaround).
        editor.setMinLines(6);
        editor.setMaxLines(6);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(false);
        editor.setHorizontalScrollBarEnabled(false);
        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        // When typing, make TextView scroll its own content so the cursor line
        // remains visible, then move the whole field above the keyboard.
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!editor.hasFocus()) return;
                editor.post(() -> {
                    try {
                        editor.bringPointIntoView(Math.max(0, editor.getSelectionStart()));
                    } catch (Throwable ignored) {}
                    keepFieldAboveKeyboard(editor);
                });
            }
        });

        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                editor.postDelayed(() -> {
                    try {
                        editor.bringPointIntoView(Math.max(0, editor.getSelectionStart()));
                    } catch (Throwable ignored) {}
                    keepFieldAboveKeyboard(editor);
                }, 250);
            }
        });

        // Finger drag inside the text area scrolls the text field rather than
        // the surrounding recipe page.
        editor.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||
                    event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                editor.post(() -> keepFieldAboveKeyboard(editor));
            }
            return false;
        });

        return editor;
    }

    /**
     * Scroll the outer recipe page just enough to place the complete active
     * long-text field above the keyboard. getWindowVisibleDisplayFrame() gives
     * us the currently visible area after the Samsung keyboard has appeared.
     */
    private void keepFieldAboveKeyboard(EditText editor) {
        if (pageScroll == null || editor == null || !editor.hasFocus()) return;

        pageScroll.post(() -> {
            try {
                Rect visible = new Rect();
                getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);

                int[] location = new int[2];
                editor.getLocationInWindow(location);
                int fieldTop = location[1];
                int fieldBottom = fieldTop + editor.getHeight();
                int safeBottom = visible.bottom - dp(18);
                int safeTop = visible.top + dp(12);

                if (fieldBottom > safeBottom) {
                    pageScroll.smoothScrollBy(0, fieldBottom - safeBottom);
                } else if (fieldTop < safeTop) {
                    pageScroll.smoothScrollBy(0, fieldTop - safeTop);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    Button btn(String text) {
        Button button = super.btn(text);
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
        text.setVerticalScrollBarEnabled(false);
        text.setHorizontalScrollBarEnabled(false);
        text.setPadding(dp(16), dp(10), dp(16), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("Felrapport från senaste kraschen")
                .setMessage("Om appen fortfarande kraschar kan du kopiera rapporten och skicka den till mig.")
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
