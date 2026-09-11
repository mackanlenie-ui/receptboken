package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
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
 * Receptboken 1.24.
 *
 * Long recipe text is placed inside its own fixed-height ScrollView. The
 * EditText itself grows with the text, while the inner ScrollView performs the
 * scrolling. This avoids One UI's broken EditText scrollbar drawable and also
 * prevents the outer recipe page from stealing the drag gesture.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    private ScrollView pageScroll;

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 1.24", Toast.LENGTH_SHORT).show();
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
        if (!multi) return super.field(hint, val, false);

        final EditText editor = new EditText(this);
        editor.setHint(hint);
        editor.setText(val == null ? "" : val);
        editor.setTextSize(16);
        editor.setPadding(dp(12), dp(10), dp(12), dp(16));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        // The EditText is deliberately NOT the scrolling view. It grows with
        // its contents and is hosted inside an inner ScrollView.
        editor.setVerticalScrollBarEnabled(false);
        editor.setHorizontalScrollBarEnabled(false);
        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editor.setMinHeight(dp(190));

        final ScrollView textScroll = new ScrollView(this);
        textScroll.setFillViewport(true);
        textScroll.setVerticalScrollBarEnabled(false);
        textScroll.setHorizontalScrollBarEnabled(false);
        textScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        textScroll.setClipToPadding(true);
        textScroll.addView(editor, new ScrollView.LayoutParams(-1, -2));

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, dp(190));
        boxParams.setMargins(0, dp(2), 0, dp(4));
        root.addView(textScroll, boxParams);

        // While the finger is inside this field, the outer recipe page must not
        // intercept the vertical drag. The inner ScrollView then scrolls the
        // recipe text exactly like a normal text editor.
        View.OnTouchListener keepGestureInsideField = (v, event) -> {
            if (pageScroll != null) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    pageScroll.requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pageScroll.requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        };
        editor.setOnTouchListener(keepGestureInsideField);
        textScroll.setOnTouchListener(keepGestureInsideField);

        // As text is added, keep the caret line visible inside the inner field.
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!editor.hasFocus()) return;
                editor.post(() -> {
                    revealCaret(textScroll, editor);
                    keepFieldAboveKeyboard(textScroll);
                });
            }
        });

        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                editor.postDelayed(() -> {
                    revealCaret(textScroll, editor);
                    keepFieldAboveKeyboard(textScroll);
                }, 250);
            }
        });

        editor.setOnClickListener(v -> editor.post(() -> revealCaret(textScroll, editor)));
        return editor;
    }

    private void revealCaret(ScrollView textScroll, EditText editor) {
        try {
            if (editor.getLayout() == null || textScroll.getHeight() <= 0) return;
            int offset = Math.max(0, Math.min(editor.getSelectionStart(), editor.length()));
            int line = editor.getLayout().getLineForOffset(offset);
            int lineTop = editor.getLayout().getLineTop(line) + editor.getCompoundPaddingTop();
            int lineBottom = editor.getLayout().getLineBottom(line) + editor.getCompoundPaddingTop();

            int currentTop = textScroll.getScrollY();
            int currentBottom = currentTop + textScroll.getHeight();
            int margin = dp(30);

            if (lineBottom + margin > currentBottom) {
                textScroll.smoothScrollTo(0, Math.max(0, lineBottom + margin - textScroll.getHeight()));
            } else if (lineTop - margin < currentTop) {
                textScroll.smoothScrollTo(0, Math.max(0, lineTop - margin));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Keep the complete long-text box above the on-screen keyboard. */
    private void keepFieldAboveKeyboard(View fieldBox) {
        if (pageScroll == null || fieldBox == null) return;

        pageScroll.post(() -> {
            try {
                Rect visible = new Rect();
                getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);

                int[] location = new int[2];
                fieldBox.getLocationInWindow(location);
                int fieldTop = location[1];
                int fieldBottom = fieldTop + fieldBox.getHeight();
                int safeBottom = visible.bottom - dp(14);
                int safeTop = visible.top + dp(10);

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
