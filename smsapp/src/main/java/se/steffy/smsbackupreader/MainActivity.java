package se.steffy.smsbackupreader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 2001;
    private static final int REQ_TREE = 2002;
    private static final int REQ_BACKUP = 2003;
    private static final String PREFS = "sms_backup_arkiv_v2";
    private static final String KEY_TREE = "tree_uri";
    private static final String KEY_LAST = "last_backup_uri";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri treeUri;
    private Uri lastBackupUri;
    private TextView backupPermission;
    private TextView backupTarget;
    private TextView backupCount;
    private TextView backupStatus;
    private ProgressBar backupProgress;
    private Button exportButton;
    private Button openLastButton;
    private List<Conversation> conversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(25, 48, 95));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 251));
        restorePrefs();
        showHome();
    }

    @Override
    public void onBackPressed() {
        showHome();
    }

    private void restorePrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String tree = p.getString(KEY_TREE, null);
        String last = p.getString(KEY_LAST, null);
        if (tree != null) treeUri = Uri.parse(tree);
        if (last != null) lastBackupUri = Uri.parse(last);
    }

    private void showHome() {
        ScrollView scroll = screen();
        LinearLayout root = content(scroll);
        addHeader(root, "SMS Backup & Arkiv", "Säkerhetskopiera alla SMS till USB och läs dina sparade konversationer i samma app.");

        LinearLayout backupCard = card();
        root.addView(backupCard, match());
        backupCard.addView(iconTitle("💾  Säkerhetskopiera SMS"));
        addBody(backupCard, "Kopierar telefonens SMS till den USB-mapp du väljer. Backupen sparas som JSON och HTML.");
        Button backup = primaryButton("Öppna säkerhetskopiering");
        backup.setOnClickListener(v -> showBackup());
        addButton(backupCard, backup);

        LinearLayout archiveCard = card();
        LinearLayout.LayoutParams aLp = match();
        aLp.topMargin = dp(16);
        root.addView(archiveCard, aLp);
        archiveCard.addView(iconTitle("💬  Läs SMS-arkiv"));
        addBody(archiveCard, "Öppna en JSON-backup från USB och läs meddelandena som vanliga konversationer.");
        Button open = primaryButton("Öppna backupfil");
        open.setOnClickListener(v -> chooseBackupFile());
        addButton(archiveCard, open);

        if (lastBackupUri != null) {
            Button latest = secondaryButton("Öppna senaste backup");
            latest.setOnClickListener(v -> loadBackup(lastBackupUri));
            addButton(archiveCard, latest);
        }

        LinearLayout info = card();
        LinearLayout.LayoutParams iLp = match();
        iLp.topMargin = dp(16);
        root.addView(info, iLp);
        info.addView(iconTitle("🔒  Lokal och offline"));
        addBody(info, "Appen har ingen internetbehörighet. SMS läses bara när du själv startar en backup, och arkivdelen läser bara den fil du väljer.");
        setContentView(scroll);
    }

    private void showBackup() {
        ScrollView scroll = screen();
        LinearLayout root = content(scroll);
        addBack(root, "Till startsidan", this::showHome);
        addHeader(root, "Säkerhetskopiera", "Välj USB-minne och exportera telefonens SMS.");

        LinearLayout c = card();
        root.addView(c, match());
        c.addView(section("1. SMS-åtkomst"));
        backupPermission = status();
        c.addView(backupPermission);
        Button permission = secondaryButton("Ge SMS-åtkomst");
        permission.setOnClickListener(v -> requestSmsPermission());
        addButton(c, permission);
        c.addView(divider());

        c.addView(section("2. USB-minne / mapp"));
        backupTarget = status();
        c.addView(backupTarget);
        Button target = secondaryButton("Välj USB / mapp");
        target.setOnClickListener(v -> chooseTree());
        addButton(c, target);
        c.addView(divider());

        c.addView(section("3. Exportera"));
        backupCount = status();
        c.addView(backupCount);
        exportButton = primaryButton("EXPORTERA ALLA SMS");
        exportButton.setOnClickListener(v -> exportSms());
        addButton(c, exportButton);

        backupProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        backupProgress.setMax(100);
        backupProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(8));
        pLp.topMargin = dp(16);
        c.addView(backupProgress, pLp);
        backupStatus = status();
        LinearLayout.LayoutParams sLp = match();
        sLp.topMargin = dp(10);
        c.addView(backupStatus, sLp);

        openLastButton = secondaryButton("Öppna senaste backup i arkivet");
        openLastButton.setVisibility(lastBackupUri == null ? View.GONE : View.VISIBLE);
        openLastButton.setOnClickListener(v -> loadBackup(lastBackupUri));
        addButton(c, openLastButton);

        setContentView(scroll);
        refreshBackupState();
    }

    private boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        if (hasSmsPermission()) {
            Toast.makeText(this, "SMS-åtkomst är redan godkänd", Toast.LENGTH_SHORT).show();
            refreshBackupState();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshBackupState();
            } else {
                if (backupPermission != null) backupPermission.setText("❌ SMS-åtkomst nekades eller blockerades av Android.");
                new AlertDialog.Builder(this)
                        .setTitle("SMS-behörighet krävs")
                        .setMessage("Backupdelen behöver READ_SMS för att kunna läsa dina SMS. Arkivdelen fungerar utan den behörigheten.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void chooseTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void chooseBackupFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_TREE) {
            treeUri = uri;
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TREE, uri.toString()).apply();
            refreshBackupState();
        } else if (requestCode == REQ_BACKUP) {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            lastBackupUri = uri;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST, uri.toString()).apply();
            loadBackup(uri);
        }
    }

    private void refreshBackupState() {
        if (backupPermission == null) return;
        boolean ok = hasSmsPermission();
        backupPermission.setText(ok ? "✅ SMS-åtkomst godkänd" : "⚠️ SMS-åtkomst saknas");
        backupTarget.setText(treeUri == null ? "⚠️ Inget mål valt" : "✅ Mål valt: " + readableTree(treeUri));
        exportButton.setEnabled(ok && treeUri != null);
        exportButton.setAlpha(exportButton.isEnabled() ? 1f : 0.45f);
        if (ok) countSms(); else backupCount.setText("SMS hittade: –");
    }

    private void countSms() {
        backupCount.setText("Räknar SMS…");
        executor.execute(() -> {
            int count = 0;
            try (Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms._ID}, null, null, null)) {
                if (c != null) count = c.getCount();
            } catch (Exception e) {
                final String msg = e.getMessage();
                runOnUiThread(() -> { if (backupCount != null) backupCount.setText("Kunde inte läsa SMS: " + safe(msg)); });
                return;
            }
            final int n = count;
            runOnUiThread(() -> { if (backupCount != null) backupCount.setText("SMS hittade: " + n); });
        });
    }

    private void exportSms() {
        if (!hasSmsPermission()) { requestSmsPermission(); return; }
        if (treeUri == null) { chooseTree(); return; }
        exportButton.setEnabled(false);
        exportButton.setAlpha(0.45f);
        backupProgress.setVisibility(View.VISIBLE);
        backupProgress.setProgress(0);
        backupStatus.setText("Förbereder export…");
        Uri target = treeUri;
        executor.execute(() -> doExport(target));
    }

    private void doExport(Uri targetTree) {
        int exported = 0;
        Uri jsonUri = null;
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            jsonUri = createFile(targetTree, "application/json", "SMS_Backup_" + stamp + ".json");
            Uri htmlUri = createFile(targetTree, "text/html", "SMS_Backup_" + stamp + ".html");
            if (jsonUri == null || htmlUri == null) throw new IllegalStateException("Kunde inte skapa backupfiler.");

            ContentResolver resolver = getContentResolver();
            String[] projection = {
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE,
                    Telephony.Sms.BODY, Telephony.Sms.READ, Telephony.Sms.STATUS,
                    Telephony.Sms.SERVICE_CENTER, Telephony.Sms.SUBSCRIPTION_ID
            };
            try (OutputStream jOut = resolver.openOutputStream(jsonUri, "w");
                 OutputStream hOut = resolver.openOutputStream(htmlUri, "w");
                 BufferedWriter json = new BufferedWriter(new OutputStreamWriter(jOut, "UTF-8"), 65536);
                 BufferedWriter html = new BufferedWriter(new OutputStreamWriter(hOut, "UTF-8"), 65536);
                 Cursor c = resolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " ASC")) {
                if (jOut == null || hOut == null || c == null) throw new IllegalStateException("Kunde inte öppna SMS eller målfil.");
                int total = c.getCount();
                json.write("{\n  \"format\":\"SMS Backup & Arkiv\",\n  \"version\":2,\n  \"exportedAt\":\"" + jsonEscape(stamp) + "\",\n  \"totalMessages\":" + total + ",\n  \"messages\":[\n");
                html.write("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>SMS Backup</title><style>body{font-family:sans-serif;background:#f5f7fb;color:#162037;max-width:900px;margin:auto;padding:20px}.m{background:white;padding:14px 16px;margin:10px 0;border-radius:14px;border:1px solid #e1e6f0}.meta{color:#667085;font-size:13px;margin-bottom:7px}.sent{border-left:5px solid #2457e6}.in{border-left:5px solid #7b8ba8}</style></head><body><h1>SMS Backup</h1><p>" + total + " meddelanden</p>");
                boolean first = true;
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    long threadId = c.getLong(1);
                    String address = c.isNull(2) ? "" : c.getString(2);
                    long date = c.getLong(3);
                    long dateSent = c.getLong(4);
                    int type = c.getInt(5);
                    String body = c.isNull(6) ? "" : c.getString(6);
                    int read = c.getInt(7);
                    int status = c.getInt(8);
                    String serviceCenter = c.isNull(9) ? "" : c.getString(9);
                    long subId = c.getLong(10);
                    if (!first) json.write(",\n");
                    first = false;
                    json.write("    {\"id\":" + id + ",\"threadId\":" + threadId + ",\"address\":\"" + jsonEscape(address)
                            + "\",\"date\":" + date + ",\"dateSent\":" + dateSent + ",\"type\":" + type
                            + ",\"typeText\":\"" + jsonEscape(typeName(type)) + "\",\"read\":" + read + ",\"status\":" + status
                            + ",\"subscriptionId\":" + subId + ",\"serviceCenter\":\"" + jsonEscape(serviceCenter)
                            + "\",\"body\":\"" + jsonEscape(body) + "\"}");
                    html.write("<div class=\"m " + (isSent(type) ? "sent" : "in") + "\"><div class=\"meta\">" + html(address) + " · " + html(formatDate(date)) + " · " + html(typeName(type)) + "</div><div>" + html(body).replace("\n", "<br>") + "</div></div>");
                    exported++;
                    if (exported % 50 == 0 || exported == total) {
                        final int d = exported, all = total;
                        runOnUiThread(() -> {
                            if (backupProgress != null) backupProgress.setProgress(all == 0 ? 0 : Math.min(100, Math.round(d * 100f / all)));
                            if (backupStatus != null) backupStatus.setText("Exporterar " + d + " av " + all + "…");
                        });
                    }
                }
                json.write("\n  ]\n}\n");
                html.write("</body></html>");
                json.flush();
                html.flush();
            }
            final int totalExported = exported;
            final Uri saved = jsonUri;
            lastBackupUri = saved;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST, saved.toString()).apply();
            runOnUiThread(() -> {
                if (backupProgress != null) backupProgress.setProgress(100);
                if (backupStatus != null) backupStatus.setText("✅ Klart! " + totalExported + " SMS sparades som JSON och HTML.");
                if (exportButton != null) { exportButton.setEnabled(true); exportButton.setAlpha(1f); }
                if (openLastButton != null) openLastButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "SMS-backup klar", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            final String msg = safe(e.getMessage());
            runOnUiThread(() -> {
                if (backupStatus != null) backupStatus.setText("❌ Exporten misslyckades: " + msg);
                if (exportButton != null) { exportButton.setEnabled(true); exportButton.setAlpha(1f); }
            });
        }
    }

    private Uri createFile(Uri tree, String mime, String name) throws Exception {
        String id = DocumentsContract.getTreeDocumentId(tree);
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        return DocumentsContract.createDocument(getContentResolver(), dir, mime, name);
    }

    private String readableTree(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            int p = id.indexOf(':');
            if (p >= 0) {
                String vol = id.substring(0, p), path = id.substring(p + 1);
                return path.isEmpty() ? vol : vol + "/" + path;
            }
            return id;
        } catch (Exception e) { return "vald mapp"; }
    }

    private void loadBackup(Uri uri) {
        if (uri == null) return;
        ScrollView loading = screen();
        LinearLayout root = content(loading);
        addBack(root, "Till startsidan", this::showHome);
        addHeader(root, "Läser backup…", "Öppnar och sorterar dina sparade SMS.");
        ProgressBar p = new ProgressBar(this);
        root.addView(p);
        setContentView(loading);
        executor.execute(() -> {
            try {
                String raw = readAll(uri);
                JSONObject rootJson = new JSONObject(raw);
                JSONArray arr = rootJson.getJSONArray("messages");
                Map<String, Conversation> grouped = new LinkedHashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Message m = new Message();
                    m.address = o.optString("address", "");
                    m.date = o.optLong("date", 0);
                    m.type = o.optInt("type", 0);
                    m.body = o.optString("body", "");
                    String key = m.address == null || m.address.trim().isEmpty() ? "(okänd avsändare)" : m.address.trim();
                    Conversation c = grouped.get(key);
                    if (c == null) { c = new Conversation(key); grouped.put(key, c); }
                    c.messages.add(m);
                }
                List<Conversation> list = new ArrayList<>(grouped.values());
                for (Conversation c : list) {
                    Collections.sort(c.messages, Comparator.comparingLong(x -> x.date));
                    c.lastDate = c.messages.isEmpty() ? 0 : c.messages.get(c.messages.size() - 1).date;
                }
                Collections.sort(list, (a, b) -> Long.compare(b.lastDate, a.lastDate));
                conversations = list;
                lastBackupUri = uri;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST, uri.toString()).apply();
                runOnUiThread(() -> showArchiveList(conversations, ""));
            } catch (Exception e) {
                final String msg = safe(e.getMessage());
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Kunde inte läsa backupen")
                        .setMessage("Kontrollera att du valt JSON-filen från SMS Backup & Arkiv.\n\n" + msg)
                        .setPositiveButton("OK", (d, w) -> showHome())
                        .show());
            }
        });
    }

    private String readAll(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536)) {
            if (in == null) throw new IllegalStateException("Filen kunde inte öppnas.");
            char[] buf = new char[32768];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private void showArchiveList(List<Conversation> source, String query) {
        ScrollView scroll = screen();
        LinearLayout root = content(scroll);
        addBack(root, "Till startsidan", this::showHome);
        addHeader(root, "SMS-arkiv", source.size() + " konversationer i backupen.");

        EditText search = new EditText(this);
        search.setHint("Sök nummer eller meddelandetext…");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.WHITE);
        sBg.setCornerRadius(dp(14));
        sBg.setStroke(dp(1), Color.rgb(220, 226, 238));
        search.setBackground(sBg);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(52));
        searchLp.bottomMargin = dp(14);
        root.addView(search, searchLp);

        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        root.addView(listHolder, match());
        renderConversationCards(listHolder, source, query);
        if (query != null && !query.isEmpty()) search.setText(query);
        search.setSelection(search.length());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderConversationCards(listHolder, conversations, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        setContentView(scroll);
    }

    private void renderConversationCards(LinearLayout holder, List<Conversation> source, String query) {
        holder.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (Conversation c : source) {
            if (!q.isEmpty() && !conversationMatches(c, q)) continue;
            LinearLayout row = card();
            LinearLayout.LayoutParams lp = match();
            lp.bottomMargin = dp(10);
            holder.addView(row, lp);
            TextView name = text(c.address, 18, Color.rgb(22, 32, 55), true);
            row.addView(name);
            Message last = c.messages.get(c.messages.size() - 1);
            String preview = last.body == null ? "" : last.body.replace('\n', ' ');
            if (preview.length() > 85) preview = preview.substring(0, 82) + "…";
            TextView meta = text(c.messages.size() + " meddelanden · " + formatDate(last.date), 13, Color.rgb(100, 111, 135), false);
            LinearLayout.LayoutParams mLp = match(); mLp.topMargin = dp(4); row.addView(meta, mLp);
            TextView body = text(preview, 15, Color.rgb(64, 74, 94), false);
            LinearLayout.LayoutParams bLp = match(); bLp.topMargin = dp(7); row.addView(body, bLp);
            row.setClickable(true);
            row.setOnClickListener(v -> showConversation(c));
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("Inga konversationer matchar sökningen.", 15, Color.rgb(100, 111, 135), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            holder.addView(empty);
        }
    }

    private boolean conversationMatches(Conversation c, String q) {
        if (c.address.toLowerCase(Locale.getDefault()).contains(q)) return true;
        for (Message m : c.messages) if (m.body != null && m.body.toLowerCase(Locale.getDefault()).contains(q)) return true;
        return false;
    }

    private void showConversation(Conversation c) {
        ScrollView scroll = screen();
        LinearLayout root = content(scroll);
        addBack(root, "Till SMS-arkivet", () -> showArchiveList(conversations, ""));
        addHeader(root, c.address, c.messages.size() + " meddelanden");
        int maxBubble = Math.round(getResources().getDisplayMetrics().widthPixels * 0.80f);
        for (Message m : c.messages) {
            boolean sent = isSent(m.type);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(sent ? Gravity.END : Gravity.START);
            LinearLayout.LayoutParams rLp = match(); rLp.bottomMargin = dp(8); root.addView(row, rLp);
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(14), dp(10), dp(14), dp(9));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sent ? Color.rgb(36, 87, 230) : Color.WHITE);
            bg.setCornerRadius(dp(18));
            if (!sent) bg.setStroke(dp(1), Color.rgb(225, 230, 240));
            bubble.setBackground(bg);
            bubble.setElevation(dp(1));
            TextView body = text(m.body == null || m.body.isEmpty() ? "(tomt meddelande)" : m.body, 16,
                    sent ? Color.WHITE : Color.rgb(22, 32, 55), false);
            body.setMaxWidth(maxBubble);
            bubble.addView(body);
            TextView time = text(formatDate(m.date) + " · " + typeName(m.type), 11,
                    sent ? Color.rgb(220, 230, 255) : Color.rgb(112, 123, 145), false);
            LinearLayout.LayoutParams tLp = match(); tLp.topMargin = dp(5); bubble.addView(time, tLp);
            row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        }
        setContentView(scroll);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isSent(int type) {
        return type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                || type == Telephony.Sms.MESSAGE_TYPE_FAILED || type == Telephony.Sms.MESSAGE_TYPE_QUEUED;
    }

    private String typeName(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "Mottaget";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "Skickat";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "Utkast";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX: return "Utkorg";
            case Telephony.Sms.MESSAGE_TYPE_FAILED: return "Misslyckat";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED: return "Köat";
            default: return "SMS";
        }
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "Okänt datum";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private ScrollView screen() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.rgb(245, 247, 251));
        return s;
    }

    private LinearLayout content(ScrollView scroll) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        return root;
    }

    private void addHeader(LinearLayout root, String title, String subtitle) {
        root.addView(text(title, 29, Color.rgb(22, 32, 55), true));
        TextView s = text(subtitle, 15, Color.rgb(91, 101, 122), false);
        s.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams lp = match(); lp.topMargin = dp(6); lp.bottomMargin = dp(20); root.addView(s, lp);
    }

    private void addBack(LinearLayout root, String label, Runnable action) {
        Button b = secondaryButton("←  " + label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.bottomMargin = dp(16);
        root.addView(b, lp);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(225, 230, 240));
        c.setBackground(bg);
        c.setElevation(dp(2));
        return c;
    }

    private TextView iconTitle(String s) { return text(s, 18, Color.rgb(22, 32, 55), true); }
    private TextView section(String s) { TextView t = text(s, 17, Color.rgb(22, 32, 55), true); t.setPadding(0, 0, 0, dp(6)); return t; }
    private TextView status() { return text("", 14, Color.rgb(91, 101, 122), false); }

    private void addBody(LinearLayout parent, String s) {
        TextView t = text(s, 14, Color.rgb(91, 101, 122), false);
        t.setLineSpacing(0, 1.13f);
        LinearLayout.LayoutParams lp = match(); lp.topMargin = dp(8); parent.addView(t, lp);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(235, 238, 245));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.topMargin = dp(20); lp.bottomMargin = dp(20); v.setLayoutParams(lp);
        return v;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(36, 87, 230)); bg.setCornerRadius(dp(14)); b.setBackground(bg); b.setMinHeight(dp(52));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(15); b.setTextColor(Color.rgb(36, 87, 230)); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(239, 244, 255)); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), Color.rgb(194, 210, 255)); b.setBackground(bg); b.setMinHeight(dp(48));
        return b;
    }

    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(12); parent.addView(b, lp);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String safe(String s) { return s == null || s.trim().isEmpty() ? "okänt fel" : s; }

    private String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format(Locale.US, "\\u%04x", (int)c)); else out.append(c);
            }
        }
        return out.toString();
    }

    private String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static class Message {
        String address;
        long date;
        int type;
        String body;
    }

    static class Conversation {
        final String address;
        final List<Message> messages = new ArrayList<>();
        long lastDate;
        Conversation(String address) { this.address = address; }
    }
}
