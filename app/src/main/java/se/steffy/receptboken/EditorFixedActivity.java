package se.steffy.receptboken;

import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Launcher with a simpler and safer recipe editor.
 * Long recipe fields keep a fixed height and scroll internally while typing.
 */
public class EditorFixedActivity extends BatteryOptimizedActivity {

    @Override
    void base() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.setVerticalScrollBarEnabled(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), top(), dp(18), dp(28));
        root.setBackgroundColor(BG);
        screenScroll.addView(root);
        setContentView(screenScroll);
    }

    @Override
    EditText field(String hint, String val, boolean multi) {
        EditText editor = new EditText(this);
        editor.setHint(hint);
        editor.setText(val);
        editor.setTextSize(16);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setHorizontallyScrolling(false);

        if (multi) {
            editor.setMinLines(5);
            editor.setMaxLines(5);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setVerticalScrollBarEnabled(true);
            editor.setScrollbarFadingEnabled(false);
            editor.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            editor.setMovementMethod(ScrollingMovementMethod.getInstance());

            // Let the text box scroll itself when it contains more text than fits.
            // When the finger is released, the outer recipe form can scroll again.
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

            root.addView(editor, new LinearLayout.LayoutParams(-1, dp(170)));
        } else {
            editor.setSingleLine(true);
            root.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        }
        return editor;
    }
}
