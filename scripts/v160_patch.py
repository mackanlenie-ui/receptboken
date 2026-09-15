from pathlib import Path

java = Path('app/src/main/java/se/steffy/receptboken/DiaryMainActivity.java')
s = java.read_text(encoding='utf-8')

# Kompaktare mobil startsida. DeX använder showHomeWide och påverkas inte.
s = s.replace('        dlp.bottomMargin = dp(22);', '        dlp.bottomMargin = dp(12);', 1)
s = s.replace('new LinearLayout.LayoutParams(0, dp(105), 1f)', 'new LinearLayout.LayoutParams(0, dp(92), 1f)', 2)
s = s.replace('        slp.bottomMargin = dp(26);', '        slp.bottomMargin = dp(18);', 1)

old = '''        TextView todayBody = text(today == null ? "Skriv några rader om det som hänt, hur du mår eller något du vill minnas." : preview(today), 14, false, muted);\n        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);'''
new = '''        TextView todayBody = text(today == null ? "Skriv några rader om det som hänt, hur du mår eller något du vill minnas." : preview(today), 14, false, muted);\n        if (today != null) {\n            todayBody.setMaxLines(2);\n            todayBody.setEllipsize(android.text.TextUtils.TruncateAt.END);\n        }\n        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);'''
if old not in s:
    raise SystemExit('Kunde inte komprimera dagens anteckning')
s = s.replace(old, new, 1)

old = '        todayCard.addView(write);\n        c.addView(todayCard, cardLp());'
new = '''        todayCard.addView(write, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));\n        LinearLayout.LayoutParams todayLp = cardLp();\n        todayLp.bottomMargin = dp(10);\n        c.addView(todayCard, todayLp);'''
if old not in s:
    raise SystemExit('Kunde inte komprimera dagens kort')
s = s.replace(old, new, 1)

# Kompaktare humörkort på telefon, oförändrat i DeX.
old = '''    private View moodOverviewCard() {\n        LinearLayout outer = card();\n        outer.addView(text("Humör den här månaden", 18, true, text));\n        ArrayList<Entry> entries = db.month(YearMonth.now().toString());'''
new = '''    private View moodOverviewCard() {\n        LinearLayout outer = card();\n        if (!wide) outer.setPadding(dp(16), dp(13), dp(16), dp(11));\n        outer.addView(text("Humör den här månaden", wide ? 18 : 17, true, text));\n        ArrayList<Entry> entries = db.month(YearMonth.now().toString());'''
if old not in s:
    raise SystemExit('Kunde inte komprimera humörkortet')
s = s.replace(old, new, 1)
s = s.replace('        row.setPadding(0, dp(10), 0, 0);', '        row.setPadding(0, dp(wide ? 10 : 6), 0, 0);', 1)
s = s.replace('            TextView emoji = text(MOOD_EMOJI[i], 20, false, text);', '            TextView emoji = text(MOOD_EMOJI[i], wide ? 20 : 18, false, text);', 1)
s = s.replace('            TextView number = text(String.valueOf(counts[i]), 11, true, counts[i] > 0 ? accent : muted);', '            TextView number = text(String.valueOf(counts[i]), wide ? 11 : 10, true, counts[i] > 0 ? accent : muted);', 1)
s = s.replace('            row.addView(cell, new LinearLayout.LayoutParams(0, dp(58), 1f));', '            row.addView(cell, new LinearLayout.LayoutParams(0, dp(wide ? 58 : 48), 1f));', 1)

# Riktiga vektorikoner i mobilens nedersta navigering.
start = s.index('    private View buildBottomNav() {')
end = s.index('    private LinearLayout.LayoutParams weight()', start)
new_nav = '''    private View buildBottomNav() {\n        LinearLayout nav = new LinearLayout(this);\n        nav.setGravity(Gravity.CENTER);\n        nav.setPadding(dp(4), dp(5), dp(4), dp(7));\n        nav.setBackgroundColor(surface);\n        nav.addView(bottomNavButton(R.drawable.ic_diary_home, "Hem", "home"), weight());\n        nav.addView(bottomNavButton(R.drawable.ic_diary_calendar, "Kalender", "calendar"), weight());\n        nav.addView(bottomNavButton(R.drawable.ic_diary_edit, "Skriv", "editor"), weight());\n        nav.addView(bottomNavButton(R.drawable.ic_diary_search, "Sök", "search"), weight());\n        nav.addView(bottomNavButton(R.drawable.ic_diary_star, "Favoriter", "favorites"), weight());\n        return nav;\n    }\n\n'''
s = s[:start] + new_nav + s[end:]

start = s.index('    private View bottomNavButton(')
end = s.index('    private void navigate(', start)
new_bottom = '''    private View bottomNavButton(int iconRes, String label, String page) {\n        boolean selected = page.equals(currentPage);\n        int itemColor = selected ? accent : muted;\n\n        LinearLayout item = new LinearLayout(this);\n        item.setOrientation(LinearLayout.VERTICAL);\n        item.setGravity(Gravity.CENTER);\n        item.setPadding(dp(3), dp(4), dp(3), dp(2));\n        item.setClickable(true);\n        item.setFocusable(true);\n        item.setContentDescription(label);\n        item.setBackground(round(Color.TRANSPARENT, 16, 0, Color.TRANSPARENT));\n\n        ImageView icon = new ImageView(this);\n        android.graphics.drawable.Drawable drawable = getDrawable(iconRes);\n        if (drawable != null) {\n            drawable = drawable.mutate();\n            drawable.setTint(itemColor);\n            icon.setImageDrawable(drawable);\n        }\n        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));\n\n        TextView caption = text(label, 11, selected, itemColor);\n        caption.setGravity(Gravity.CENTER);\n        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n        clp.topMargin = dp(3);\n        item.addView(caption, clp);\n\n        mobilePressFeedback(item);\n        item.setOnClickListener(v -> navigate(page));\n        return item;\n    }\n\n'''
s = s[:start] + new_bottom + s[end:]

# Uppdatera vald flik direkt när man navigerar på telefonen.
old = '''        if ("editor".equals(currentPage)) autosaveEditor(false);\n        hideKeyboard();\n        switch (page) {'''
new = '''        if ("editor".equals(currentPage)) autosaveEditor(false);\n        hideKeyboard();\n        if (!wide) {\n            currentPage = page;\n            buildShell();\n        }\n        switch (page) {'''
if old not in s:
    raise SystemExit('Kunde inte förbättra vald mobilflik')
s = s.replace(old, new, 1)

# Mjuka tryckanimationer + diskret haptik på mobil.
s = s.replace('        desktopHover(b);\n        return b;\n    }\n\n    private Button outlinedButton', '        desktopHover(b);\n        mobilePressFeedback(b);\n        return b;\n    }\n\n    private Button outlinedButton', 1)
s = s.replace('        desktopHover(b);\n        return b;\n    }\n\n    private Button iconButton', '        desktopHover(b);\n        mobilePressFeedback(b);\n        return b;\n    }\n\n    private Button iconButton', 1)

old = '''        b.setBackground(round(Color.TRANSPARENT, 18, 0, Color.TRANSPARENT));\n        return b;\n    }'''
new = '''        b.setBackground(round(Color.TRANSPARENT, 18, 0, Color.TRANSPARENT));\n        mobilePressFeedback(b);\n        return b;\n    }'''
if old not in s:
    raise SystemExit('Kunde inte lägga mobilfeedback på ikonknappar')
s = s.replace(old, new, 1)

# Anteckningskort, humörknappar och favorit får samma känsla.
s = s.replace('        desktopHover(card);\n        return card;', '        desktopHover(card);\n        mobilePressFeedback(card);\n        return card;', 1)

old = '''            moods.addView(m, mlp);\n            m.setOnClickListener(v -> {'''
new = '''            moods.addView(m, mlp);\n            mobilePressFeedback(m);\n            m.setOnClickListener(v -> {'''
if old not in s:
    raise SystemExit('Kunde inte lägga mobilfeedback på humör')
s = s.replace(old, new, 1)

old = '''        favorite.setChecked(editorEntry.favorite);\n        favorite.setOnCheckedChangeListener((buttonView, isChecked) -> { editorEntry.favorite = isChecked; scheduleAutosave(); });'''
new = '''        favorite.setChecked(editorEntry.favorite);\n        mobilePressFeedback(favorite);\n        favorite.setOnCheckedChangeListener((buttonView, isChecked) -> { editorEntry.favorite = isChecked; scheduleAutosave(); });'''
if old not in s:
    raise SystemExit('Kunde inte lägga mobilfeedback på favorit')
s = s.replace(old, new, 1)

# Hjälpmetod för mobilkänsla.
marker = '    private int screenWidthDp() {'
helper = '''    private void mobilePressFeedback(View v) {\n        if (wide || v == null) return;\n        v.setHapticFeedbackEnabled(true);\n        v.setOnTouchListener((view, event) -> {\n            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {\n                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).start();\n            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {\n                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();\n                int feedback = Build.VERSION.SDK_INT >= 30\n                        ? android.view.HapticFeedbackConstants.CONFIRM\n                        : android.view.HapticFeedbackConstants.VIRTUAL_KEY;\n                view.performHapticFeedback(feedback);\n            } else if (event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {\n                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();\n            }\n            return false;\n        });\n    }\n\n'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till mobilfeedback-metoden')
s = s.replace(marker, helper + marker, 1)

# Versionsnummer.
s = s.replace('Min Dagbok  ·  version 1.5.0', 'Min Dagbok  ·  version 1.6.0')
java.write_text(s, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 5", "versionCode 6")
g = g.replace("versionName '1.5.0'", "versionName '1.6.0'")
gradle.write_text(g, encoding='utf-8')

# Material-liknande vektorikoner för nedersta navigationen.
drawable = Path('app/src/main/res/drawable')
drawable.mkdir(parents=True, exist_ok=True)
icons = {
    'ic_diary_home.xml': 'M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z',
    'ic_diary_calendar.xml': 'M19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0-1.99,0.9-1.99,2L3,20c0,1.1,0.89,2,2,2h14c1.1,0,2-0.9,2-2V6c0-1.1-0.9-2-2-2zM19,20H5V9h14v11z',
    'ic_diary_edit.xml': 'M3,17.25V21h3.75L17.81,9.94l-3.75-3.75L3,17.25zM20.71,7.04c0.39-0.39,0.39-1.03,0-1.42l-2.34-2.34c-0.39-0.39-1.03-0.39-1.42,0l-1.83,1.83l3.75,3.75l1.84-1.82z',
    'ic_diary_search.xml': 'M9.5,3a6.5,6.5 0,1 0,0,13a6.5,6.5 0,0 0,0-13zM9.5,5a4.5,4.5 0,1 1,0,9a4.5,4.5 0,0 1,0-9zM14.5,13l6.5,6.5l-1.5,1.5L13,14.5z',
    'ic_diary_star.xml': 'M12,17.27L18.18,21l-1.64-7.03L22,9.24l-7.19-0.61L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21z',
}
for name, path_data in icons.items():
    xml = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path android:fillColor="#FFFFFFFF" android:pathData="{path_data}"/>\n</vector>\n'''
    (drawable / name).write_text(xml, encoding='utf-8')
