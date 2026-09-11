package se.steffy.receptboken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Receptboken 2.2
 *
 * Redigeraren använder samma fungerande modell som Mat & Fika:
 * - en enda ScrollView för hela sidan
 * - långa EditText-fält växer med innehållet
 * - inga nästlade scrollfält
 * - IME/system-insets läggs på det yttre skalet så sidan går att rulla
 *   ovanför tangentbordet även på Samsung/One UI.
 */
public class RecoveryActivity extends MainActivity {

    private static final String DIAG_PREFS = "receptboken_diagnostics";
    private static final String LAST_CRASH = "last_crash";

    private LinearLayout shell;
    private ScrollView page;

    @Override
    public void onCreate(Bundle state) {
        installCrashRecorder();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        super.onCreate(state);
        Toast.makeText(this, "Receptboken 2.2", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::showSavedCrashIfAny, 500);
    }

    /**
     * Samma sidmodell som i Mat & Fika. Tangentbordets höjd blir padding på
     * shell och själva ScrollView:n får därför alltid ett synligt område ovanför
     * tangentbordet att rulla i.
     */
    @Override
    void base() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        page.setHorizontalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);

        int windowDp = getResources().getConfiguration().screenWidthDp;
        int side = Math.max(dp(18), dp((windowDp - 1120) / 2));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(side, dp(16), side, dp(30));
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);

        page.addView(root, new ScrollView.LayoutParams(-1, -2));
        shell.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell);

        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
                v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
            } else {
                v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        shell.requestApplyInsets();
    }

    /**
     * Kopierad redigeringsprincip från Mat & Fika. Viktigt: ett långt fält har
     * ingen maxhöjd och ingen egen scroll. Det växer i stället, så den enda
     * scrollägaren är hela sidan.
     */
    @Override
    EditText field(String hint, String value, boolean multi) {
        TextView label = txt(hint, 14, MUTED);
        label.setPadding(dp(3), dp(16), 0, dp(7));
        root.addView(label);

        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(17);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        e.setBackground(round(Color.WHITE));
        e.setSingleLine(!multi);
        e.setHorizontallyScrolling(false);
        e.setVerticalScrollBarEnabled(false);
        e.setHorizontalScrollBarEnabled(false);
        e.setOverScrollMode(View.OVER_SCROLL_NEVER);
        e.setInputType(InputType.TYPE_CLASS_TEXT |
                (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));

        if (hint.equals("Antal") || hint.equals("Tid i minuter")) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        if (multi) {
            e.setMinLines(4);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        root.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
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
                .setMessage("Om appen kraschar igen kan du kopiera rapporten och skicka den till mig.")
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
