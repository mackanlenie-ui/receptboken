package se.steffy.smsbackup;

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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 1001;
    private static final int REQ_TREE = 1002;
    private static final String PREFS = "sms_backup_prefs";
    private static final String KEY_TREE = "tree_uri";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView permissionStatus;
    private TextView usbStatus;
    private TextView smsCount;
    private TextView exportStatus;
    private ProgressBar progress;
    private Button exportButton;
    private Uri treeUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(25, 48, 95));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 251));
        setContentView(buildUi());
        restoreTree();
        refreshState();
    }

    private View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(26), pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("SMS Backup USB", 30, Color.rgb(22, 32, 55), true);
        root.addView(title);
        TextView subtitle = text("Säker, lokal export av dina SMS direkt till ett USB-minne.", 16, Color.rgb(91, 101, 122), false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(22);
        root.addView(subtitle, subLp);

        LinearLayout card = card();
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(sectionTitle("1. SMS-åtkomst"));
        permissionStatus = statusText();
        card.addView(permissionStatus);
        Button permissionButton = primaryButton("Ge SMS-åtkomst");
        permissionButton.setOnClickListener(v -> requestSmsPermission());
        addButton(card, permissionButton);

        card.addView(divider());
        card.addView(sectionTitle("2. Välj USB-minne eller mapp"));
        usbStatus = statusText();
        card.addView(usbStatus);
        Button usbButton = secondaryButton("Välj USB / mapp");
        usbButton.setOnClickListener(v -> chooseTree());
        addButton(card, usbButton);

        card.addView(divider());
        card.addView(sectionTitle("3. Exportera"));
        smsCount = statusText();
        card.addView(smsCount);
        exportButton = primaryButton("EXPORTERA ALLA SMS");
        exportButton.setOnClickListener(v -> exportSms());
        addButton(card, exportButton);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, dp(8));
        pLp.topMargin = dp(16);
        card.addView(progress, pLp);

        exportStatus = statusText();
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(-1, -2);
        eLp.topMargin = dp(10);
        card.addView(exportStatus, eLp);

        LinearLayout info = card();
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(-1, -2);
        iLp.topMargin = dp(16);
        root.addView(info, iLp);
        TextView infoTitle = text("🔒 Helt offline", 17, Color.rgb(22, 32, 55), true);
        info.addView(infoTitle);
        TextView infoText = text("Appen har ingen internetbehörighet. Export sker bara när du trycker på knappen och filerna sparas i den mapp du själv väljer. Android måste först godkänna SMS-behörigheten.", 14, Color.rgb(91, 101, 122), false);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.topMargin = dp(8);
        info.addView(infoText, tLp);

        return scroll;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(225, 230, 240));
        layout.setBackground(bg);
        layout.setElevation(dp(2));
        return layout;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 17, Color.rgb(22, 32, 55), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView statusText() {
        TextView t = text("", 14, Color.rgb(91, 101, 122), false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(235, 238, 245));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(20);
        v.setLayoutParams(lp);
        return v;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(36, 87, 230));
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setMinHeight(dp(52));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(36, 87, 230));
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(239, 244, 255));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(194, 210, 255));
        b.setBackground(bg);
        b.setMinHeight(dp(52));
        return b;
    }

    private void addButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(12);
        parent.addView(button, lp);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        if (hasSmsPermission()) {
            Toast.makeText(this, "SMS-åtkomst är redan godkänd", Toast.LENGTH_SHORT).show();
            refreshState();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshState();
            } else {
                permissionStatus.setText("❌ SMS-åtkomst nekades eller blockerades av Android.");
                showPermissionHelp();
            }
        }
    }

    private void showPermissionHelp() {
        new AlertDialog.Builder(this)
                .setTitle("SMS-behörighet krävs")
                .setMessage("Utan READ_SMS kan appen inte läsa eller exportera meddelanden. På vissa nya Android-versioner är SMS en extra begränsad behörighet och installatören kan behöva tillåta den.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void chooseTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            treeUri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(treeUri, flags);
            } catch (SecurityException ignored) {
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TREE, treeUri.toString()).apply();
            refreshState();
        }
    }

    private void restoreTree() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = p.getString(KEY_TREE, null);
        if (saved != null) treeUri = Uri.parse(saved);
    }

    private void refreshState() {
        boolean permission = hasSmsPermission();
        permissionStatus.setText(permission ? "✅ SMS-åtkomst godkänd" : "⚠️ SMS-åtkomst saknas");
        usbStatus.setText(treeUri != null ? "✅ Mål valt: " + readableTree(treeUri) : "⚠️ Inget USB-minne eller mål valt");
        exportStatus.setText("");
        exportButton.setEnabled(permission && treeUri != null);
        exportButton.setAlpha(exportButton.isEnabled() ? 1f : 0.45f);
        if (permission) countSms(); else smsCount.setText("SMS hittade: –");
    }

    private String readableTree(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            int colon = id.indexOf(':');
            if (colon >= 0) {
                String volume = id.substring(0, colon);
                String path = id.substring(colon + 1);
                return path.isEmpty() ? volume : volume + "/" + path;
            }
            return id;
        } catch (Exception e) {
            return "vald mapp";
        }
    }

    private void countSms() {
        smsCount.setText("Räknar SMS…");
        executor.execute(() -> {
            int count = 0;
            try (Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI, new String[]{Telephony.Sms._ID}, null, null, null)) {
                if (c != null) count = c.getCount();
            } catch (Exception e) {
                final String msg = e.getMessage();
                runOnUiThread(() -> smsCount.setText("Kunde inte läsa SMS: " + safe(msg)));
                return;
            }
            final int result = count;
            runOnUiThread(() -> smsCount.setText("SMS hittade: " + result));
        });
    }

    private void exportSms() {
        if (!hasSmsPermission()) {
            requestSmsPermission();
            return;
        }
        if (treeUri == null) {
            chooseTree();
            return;
        }
        exportButton.setEnabled(false);
        exportButton.setAlpha(0.45f);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        exportStatus.setText("Förbereder export…");

        final Uri targetTree = treeUri;
        executor.execute(() -> doExport(targetTree));
    }

    private void doExport(Uri targetTree) {
        int exported = 0;
        Uri jsonUri = null;
        Uri htmlUri = null;
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            jsonUri = createFile(targetTree, "application/json", "SMS_Backup_" + stamp + ".json");
            htmlUri = createFile(targetTree, "text/html", "SMS_Backup_" + stamp + ".html");
            if (jsonUri == null || htmlUri == null) throw new IllegalStateException("Kunde inte skapa backupfiler i den valda mappen.");

            ContentResolver resolver = getContentResolver();
            try (OutputStream jsonOut = resolver.openOutputStream(jsonUri, "w");
                 OutputStream htmlOut = resolver.openOutputStream(htmlUri, "w");
                 BufferedWriter json = new BufferedWriter(new OutputStreamWriter(jsonOut, "UTF-8"), 65536);
                 BufferedWriter html = new BufferedWriter(new OutputStreamWriter(htmlOut, "UTF-8"), 65536);
                 Cursor c = resolver.query(Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " ASC")) {

                if (jsonOut == null || htmlOut == null || c == null) throw new IllegalStateException("Kunde inte öppna SMS eller målfil.");
                int total = c.getCount();
                writeJsonHeader(json, stamp, total);
                writeHtmlHeader(html, stamp, total);

                boolean first = true;
                while (c.moveToNext()) {
                    long id = getLong(c, Telephony.Sms._ID, 0);
                    long threadId = getLong(c, Telephony.Sms.THREAD_ID, 0);
                    String address = getString(c, Telephony.Sms.ADDRESS);
                    long date = getLong(c, Telephony.Sms.DATE, 0);
                    long dateSent = getLong(c, Telephony.Sms.DATE_SENT, 0);
                    int type = (int) getLong(c, Telephony.Sms.TYPE, 0);
                    String body = getString(c, Telephony.Sms.BODY);
                    int read = (int) getLong(c, Telephony.Sms.READ, 0);
                    int status = (int) getLong(c, Telephony.Sms.STATUS, 0);
                    String serviceCenter = getString(c, Telephony.Sms.SERVICE_CENTER);
                    long subId = getLong(c, Telephony.Sms.SUBSCRIPTION_ID, -1);

                    if (!first) json.write(",\n");
                    first = false;
                    writeJsonMessage(json, id, threadId, address, date, dateSent, type, body, read, status, serviceCenter, subId);
                    writeHtmlMessage(html, address, date, type, body);
                    exported++;

                    if (exported % 50 == 0 || exported == total) {
                        final int done = exported;
                        final int all = total;
                        runOnUiThread(() -> {
                            int pct = all <= 0 ? 0 : Math.min(100, Math.round(done * 100f / all));
                            progress.setProgress(pct);
                            exportStatus.setText("Exporterar " + done + " av " + all + " SMS…");
                        });
                    }
                }
                json.write("\n  ]\n}\n");
                html.write("</main></body></html>");
                json.flush();
                html.flush();
            }

            final int done = exported;
            runOnUiThread(() -> {
                progress.setProgress(100);
                exportStatus.setText("✅ Klart! " + done + " SMS exporterade.\nTvå filer skapades: JSON + HTML.");
                exportButton.setEnabled(true);
                exportButton.setAlpha(1f);
                Toast.makeText(this, done + " SMS exporterade", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            final String msg = safe(e.getMessage());
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                exportStatus.setText("❌ Exporten misslyckades: " + msg);
                exportButton.setEnabled(true);
                exportButton.setAlpha(1f);
            });
        }
    }

    private Uri createFile(Uri tree, String mime, String name) throws Exception {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
        return DocumentsContract.createDocument(getContentResolver(), parent, mime, name);
    }

    private void writeJsonHeader(BufferedWriter w, String stamp, int total) throws Exception {
        w.write("{\n");
        w.write("  \"format\": \"SMS Backup USB v1\",\n");
        w.write("  \"exportedAt\": \"" + json(iso(System.currentTimeMillis())) + "\",\n");
        w.write("  \"fileStamp\": \"" + json(stamp) + "\",\n");
        w.write("  \"count\": " + total + ",\n");
        w.write("  \"messages\": [\n");
    }

    private void writeJsonMessage(BufferedWriter w, long id, long threadId, String address, long date, long dateSent,
                                  int type, String body, int read, int status, String serviceCenter, long subId) throws Exception {
        w.write("    {");
        w.write("\"id\":" + id + ",");
        w.write("\"threadId\":" + threadId + ",");
        w.write("\"address\":\"" + json(address) + "\",");
        w.write("\"date\":" + date + ",");
        w.write("\"dateIso\":\"" + json(iso(date)) + "\",");
        w.write("\"dateSent\":" + dateSent + ",");
        w.write("\"type\":" + type + ",");
        w.write("\"typeText\":\"" + json(typeName(type)) + "\",");
        w.write("\"read\":" + read + ",");
        w.write("\"status\":" + status + ",");
        w.write("\"subscriptionId\":" + subId + ",");
        w.write("\"serviceCenter\":\"" + json(serviceCenter) + "\",");
        w.write("\"body\":\"" + json(body) + "\"");
        w.write("}");
    }

    private void writeHtmlHeader(BufferedWriter w, String stamp, int total) throws Exception {
        w.write("<!doctype html><html lang=\"sv\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        w.write("<title>SMS Backup " + html(stamp) + "</title><style>");
        w.write("body{font-family:Arial,sans-serif;background:#f5f7fb;color:#162037;margin:0}header{background:#2457e6;color:white;padding:24px}main{max-width:900px;margin:auto;padding:18px}.meta{opacity:.85}.msg{background:white;border:1px solid #e1e6f0;border-radius:14px;padding:14px 16px;margin:10px 0}.top{display:flex;gap:12px;justify-content:space-between;flex-wrap:wrap}.who{font-weight:700}.date{color:#667085;font-size:13px}.type{font-size:12px;background:#eff4ff;color:#2457e6;border-radius:99px;padding:3px 8px}.body{white-space:pre-wrap;word-break:break-word;margin-top:10px;line-height:1.45}</style></head><body>");
        w.write("<header><h1>SMS Backup USB</h1><div class=\"meta\">" + total + " SMS • " + html(stamp) + "</div></header><main>");
    }

    private void writeHtmlMessage(BufferedWriter w, String address, long date, int type, String body) throws Exception {
        w.write("<section class=\"msg\"><div class=\"top\"><span class=\"who\">" + html(empty(address, "Okänt nummer")) + "</span><span class=\"type\">" + html(typeName(type)) + "</span><span class=\"date\">" + html(displayDate(date)) + "</span></div><div class=\"body\">" + html(body) + "</div></section>");
    }

    private String typeName(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "Mottaget";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "Skickat";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "Utkast";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX: return "Utkorg";
            case Telephony.Sms.MESSAGE_TYPE_FAILED: return "Misslyckat";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED: return "Köat";
            default: return "Typ " + type;
        }
    }

    private long getLong(Cursor c, String column, long fallback) {
        int i = c.getColumnIndex(column);
        if (i < 0 || c.isNull(i)) return fallback;
        try { return c.getLong(i); } catch (Exception e) { return fallback; }
    }

    private String getString(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        if (i < 0 || c.isNull(i)) return "";
        try { return c.getString(i); } catch (Exception e) { return ""; }
    }

    private String iso(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(new Date(millis));
    }

    private String displayDate(long millis) {
        if (millis <= 0) return "Okänt datum";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private String json(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 32) b.append(String.format(Locale.US, "\\u%04x", (int) ch));
                    else b.append(ch);
            }
        }
        return b.toString();
    }

    private String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String safe(String s) {
        return s == null || s.trim().isEmpty() ? "Okänt fel" : s;
    }

    private String empty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
