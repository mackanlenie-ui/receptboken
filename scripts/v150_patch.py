from pathlib import Path

java = Path('app/src/main/java/se/steffy/receptboken/DiaryMainActivity.java')
s = java.read_text(encoding='utf-8')

old = '''        if (wide) {\n            root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(244), ViewGroup.LayoutParams.MATCH_PARENT));'''
new = '''        if (wide) {\n            root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(dexSidebarWidthDp()), ViewGroup.LayoutParams.MATCH_PARENT));'''
if old not in s:
    raise SystemExit('Kunde inte göra sidomenyn responsiv')
s = s.replace(old, new, 1)

s = s.replace('TextView title = text("Min Dagbok", 23, true, text);', 'TextView title = text("Min Dagbok", 25, true, text);', 1)
s = s.replace('b.setTextSize(15);\n        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);', 'b.setTextSize(16);\n        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);', 1)
s = s.replace('LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));', 'LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));', 1)

old = '''    private ScrollView pageScroll(LinearLayout content) {\n        ScrollView scroll = new ScrollView(this);\n        scroll.setFillViewport(true);\n        scroll.setClipToPadding(false);\n        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n        return scroll;\n    }'''
new = '''    private ScrollView pageScroll(LinearLayout content) {\n        ScrollView scroll = new ScrollView(this);\n        scroll.setFillViewport(true);\n        scroll.setClipToPadding(false);\n        if (wide) {\n            FrameLayout center = new FrameLayout(this);\n            int hostDp = Math.max(620, screenWidthDp() - dexSidebarWidthDp());\n            int maxDp = screenWidthDp() >= 1600 ? 1280 : (screenWidthDp() >= 1100 ? 1120 : 900);\n            int contentDp = Math.max(560, Math.min(maxDp, hostDp - 48));\n            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(\n                    dp(contentDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);\n            center.addView(content, cp);\n            scroll.addView(center, new ScrollView.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n        } else {\n            scroll.addView(content, new ScrollView.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n        }\n        return scroll;\n    }'''
if old not in s:
    raise SystemExit('Kunde inte lägga till maxbredd i DeX')
s = s.replace(old, new, 1)

old = '''    private LinearLayout pageColumn() {\n        LinearLayout c = new LinearLayout(this);\n        c.setOrientation(LinearLayout.VERTICAL);\n        int horizontal = wide ? dp(36) : dp(18);\n        c.setPadding(horizontal, dp(18), horizontal, dp(34));\n        return c;\n    }'''
new = '''    private LinearLayout pageColumn() {\n        LinearLayout c = new LinearLayout(this);\n        c.setOrientation(LinearLayout.VERTICAL);\n        int horizontal = wide ? dp(screenWidthDp() >= 1100 ? 30 : 26) : dp(18);\n        c.setPadding(horizontal, wide ? dp(26) : dp(18), horizontal, wide ? dp(46) : dp(34));\n        return c;\n    }'''
if old not in s:
    raise SystemExit('Kunde inte justera DeX-marginaler')
s = s.replace(old, new, 1)

old = '''    private void showHome() {\n        currentPage = "home";\n        if (wide) buildShell();\n        LinearLayout c = pageColumn();'''
new = '''    private void showHome() {\n        currentPage = "home";\n        if (wide) {\n            buildShell();\n            showHomeWide();\n            return;\n        }\n        LinearLayout c = pageColumn();'''
if old not in s:
    raise SystemExit('Kunde inte koppla DeX-startsidan')
s = s.replace(old, new, 1)

marker = '    private View statCard(String value, String label) {'
wide_home = '''    private void showHomeWide() {\n        LinearLayout c = pageColumn();\n        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);\n        String greeting = hour < 10 ? "God morgon" : hour < 17 ? "God dag" : "God kväll";\n        c.addView(text(greeting, 36, true, text));\n        TextView date = text(capitalize(LocalDate.now().format(longDate)), 16, false, muted);\n        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n        dateLp.bottomMargin = dp(20);\n        c.addView(date, dateLp);\n\n        boolean large = screenWidthDp() >= 1100;\n        LinearLayout dashboard = new LinearLayout(this);\n        dashboard.setOrientation(large ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);\n        dashboard.setGravity(Gravity.TOP);\n\n        LinearLayout left = new LinearLayout(this);\n        left.setOrientation(LinearLayout.VERTICAL);\n        LinearLayout right = new LinearLayout(this);\n        right.setOrientation(LinearLayout.VERTICAL);\n\n        Entry today = db.getByDate(LocalDate.now().format(iso));\n        LinearLayout todayCard = card();\n        todayCard.addView(text(today == null ? "Hur har din dag varit?" : MOOD_EMOJI[clampMood(today.mood)] + "  Dagens anteckning", 21, true, text));\n        TextView todayBody = text(today == null ? "Skriv några rader om det som hänt, hur du mår eller något du vill minnas." : preview(today), 15, false, muted);\n        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n        tblp.topMargin = dp(8);\n        tblp.bottomMargin = dp(16);\n        todayCard.addView(todayBody, tblp);\n        Button write = primaryButton(today == null ? "✎  Skriv idag" : "✎  Fortsätt skriva");\n        write.setOnClickListener(v -> openEditor(LocalDate.now()));\n        todayCard.addView(write, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));\n        left.addView(todayCard, cardLp());\n\n        left.addView(sectionTitle("Senaste anteckningar"));\n        ArrayList<Entry> recent = db.recent(6);\n        if (recent.isEmpty()) {\n            LinearLayout empty = card();\n            TextView icon = text("📖", 38, false, text);\n            icon.setGravity(Gravity.CENTER);\n            empty.addView(icon);\n            TextView msg = text("Din dagbok är tom än så länge. Börja med några rader om dagen.", 14, false, muted);\n            msg.setGravity(Gravity.CENTER);\n            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n            mlp.topMargin = dp(8);\n            mlp.bottomMargin = dp(14);\n            empty.addView(msg, mlp);\n            Button first = outlinedButton("Skriv första anteckningen");\n            first.setOnClickListener(v -> openEditor(LocalDate.now()));\n            empty.addView(first, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));\n            left.addView(empty, cardLpSmall());\n        } else {\n            for (Entry e : recent) left.addView(entryCard(e), cardLpSmall());\n        }\n\n        LinearLayout stats = new LinearLayout(this);\n        stats.setOrientation(LinearLayout.HORIZONTAL);\n        String mm = YearMonth.now().toString();\n        stats.addView(statCard(String.valueOf(db.countMonth(mm)), "den här månaden"), new LinearLayout.LayoutParams(0, dp(112), 1f));\n        Space statGap = new Space(this);\n        stats.addView(statGap, new LinearLayout.LayoutParams(dp(12), 1));\n        stats.addView(statCard(String.valueOf(db.countAll()), "totalt"), new LinearLayout.LayoutParams(0, dp(112), 1f));\n        right.addView(stats, cardLpSmall());\n        right.addView(moodOverviewCard(), cardLp());\n\n        LocalDate now = LocalDate.now();\n        String mmDd = String.format(Locale.ROOT, "%02d-%02d", now.getMonthValue(), now.getDayOfMonth());\n        ArrayList<Entry> memories = db.sameDay(mmDd, now.format(iso));\n        if (!memories.isEmpty()) {\n            right.addView(sectionTitle("Den här dagen"));\n            TextView memorySub = text("Anteckningar från samma datum tidigare år.", 13, false, muted);\n            LinearLayout.LayoutParams ms = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n            ms.bottomMargin = dp(8);\n            right.addView(memorySub, ms);\n            for (Entry e : memories) right.addView(entryCard(e), cardLpSmall());\n        } else {\n            LinearLayout tips = card();\n            tips.addView(text("⌨  Snabbkommandon i DeX", 18, true, text));\n            TextView shortcuts = text("Ctrl+N  Ny anteckning\\nCtrl+F  Sök\\nCtrl+S  Spara medan du skriver", 14, false, muted);\n            shortcuts.setLineSpacing(dp(4), 1.12f);\n            LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);\n            shp.topMargin = dp(10);\n            tips.addView(shortcuts, shp);\n            right.addView(tips, cardLp());\n        }\n\n        if (large) {\n            dashboard.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.18f));\n            Space gap = new Space(this);\n            dashboard.addView(gap, new LinearLayout.LayoutParams(dp(18), 1));\n            dashboard.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f));\n        } else {\n            dashboard.addView(left, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n            dashboard.addView(right, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n        }\n        c.addView(dashboard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n        setPage("home", pageScroll(c));\n    }\n\n'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till DeX-startsidan')
s = s.replace(marker, wide_home + marker, 1)

old = '''    private TextView text(String value, int sp, boolean bold, int color) {\n        TextView t = new TextView(this);\n        t.setText(value);\n        t.setTextSize(sp);'''
new = '''    private TextView text(String value, int sp, boolean bold, int color) {\n        TextView t = new TextView(this);\n        t.setText(value);\n        float scale = wide ? (screenWidthDp() >= 1100 ? 1.12f : 1.06f) : 1f;\n        t.setTextSize(sp * scale);'''
if old not in s:
    raise SystemExit('Kunde inte skala DeX-typografi')
s = s.replace(old, new, 1)

s = s.replace('b.setTextSize(15);\n        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);', 'b.setTextSize(wide ? 16 : 15);\n        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);', 1)
s = s.replace('b.setTextSize(14);\n        b.setTextColor(text);', 'b.setTextSize(wide ? 15 : 14);\n        b.setTextColor(text);', 1)

old = '''        b.setPadding(dp(16), 0, dp(16), 0);\n        return b;\n    }\n\n    private Button outlinedButton'''
new = '''        b.setPadding(dp(16), 0, dp(16), 0);\n        desktopHover(b);\n        return b;\n    }\n\n    private Button outlinedButton'''
if old not in s:
    raise SystemExit('Kunde inte lägga hover på huvudknappar')
s = s.replace(old, new, 1)

old = '''        b.setPadding(dp(14), 0, dp(14), 0);\n        return b;\n    }\n\n    private Button iconButton'''
new = '''        b.setPadding(dp(14), 0, dp(14), 0);\n        desktopHover(b);\n        return b;\n    }\n\n    private Button iconButton'''
if old not in s:
    raise SystemExit('Kunde inte lägga hover på sekundärknappar')
s = s.replace(old, new, 1)

old = '''        card.setOnClickListener(v -> showReader(e));\n        return card;'''
new = '''        card.setOnClickListener(v -> showReader(e));\n        desktopHover(card);\n        return card;'''
if old not in s:
    raise SystemExit('Kunde inte lägga hover på anteckningskort')
s = s.replace(old, new, 1)

marker = '    private int clampMood(int m) {'
helpers = '''    private int screenWidthDp() {\n        return getResources().getConfiguration().screenWidthDp;\n    }\n\n    private int dexSidebarWidthDp() {\n        int w = screenWidthDp();\n        if (w >= 1600) return 292;\n        if (w >= 1100) return 272;\n        return 252;\n    }\n\n    private void desktopHover(View v) {\n        if (!wide || v == null) return;\n        v.setOnHoverListener((view, event) -> {\n            if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) {\n                view.animate().scaleX(1.012f).scaleY(1.012f).setDuration(90).start();\n            } else if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_EXIT) {\n                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();\n            }\n            return false;\n        });\n    }\n\n'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till DeX-hjälpmetoder')
s = s.replace(marker, helpers + marker, 1)

marker = '    @Override\n    public void onBackPressed() {'
shortcuts = '''    @Override\n    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {\n        if (wide && event != null && event.isCtrlPressed()) {\n            if (keyCode == android.view.KeyEvent.KEYCODE_N) {\n                openEditor(LocalDate.now());\n                return true;\n            }\n            if (keyCode == android.view.KeyEvent.KEYCODE_F) {\n                showSearch();\n                return true;\n            }\n            if (keyCode == android.view.KeyEvent.KEYCODE_S && "editor".equals(currentPage)) {\n                autosaveEditor(true);\n                return true;\n            }\n        }\n        return super.onKeyDown(keyCode, event);\n    }\n\n'''
if marker not in s:
    raise SystemExit('Kunde inte lägga till tangentbordsgenvägar')
s = s.replace(marker, shortcuts + marker, 1)

s = s.replace('Min Dagbok  ·  version 1.4.2', 'Min Dagbok  ·  version 1.5.0')
java.write_text(s, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 4", "versionCode 5")
g = g.replace("versionName '1.4.2'", "versionName '1.5.0'")
gradle.write_text(g, encoding='utf-8')
