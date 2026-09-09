package se.steffy.smsreader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final int REQ_OPEN = 2001;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Conversation> conversations = new ArrayList<>();
    private final List<Conversation> filtered = new ArrayList<>();
    private final SimpleDateFormat dateTime = new SimpleDateFormat("d MMM yyyy HH:mm", new Locale("sv", "SE"));
    private final SimpleDateFormat shortDate = new SimpleDateFormat("d MMM HH:mm", new Locale("sv", "SE"));

    private LinearLayout root;
    private TextView status;
    private EditText search;
    private ListView list;
    private ConversationAdapter conversationAdapter;
    private Uri currentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(25, 48, 95));
        getWindow().setNavigationBarColor(Color.rgb(245, 247, 251));
        showHome();
        String saved = getSharedPreferences("reader", MODE_PRIVATE).getString("last_uri", null);
        if (saved != null) {
            currentUri = Uri.parse(saved);
            status.setText("Senaste backup: " + fileName(currentUri) + "\nTryck Öppna senaste backup för att läsa den igen.");
            addOpenLastButton();
        }
    }

    private void showHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(245, 247, 251));
        setContentView(root);

        TextView title = text("SMS Arkiv", 30, Color.rgb(22, 32, 55), true);
        root.addView(title);
        TextView subtitle = text("Läs dina säkerhetskopierade SMS från USB – helt offline.", 15, Color.rgb(91, 101, 122), false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(14);
        root.addView(subtitle, subLp);

        Button open = primaryButton("Öppna SMS-backup från USB");
        open.setOnClickListener(v -> chooseBackup());
        root.addView(open, new LinearLayout.LayoutParams(-1, dp(52)));

        status = text("Välj en JSON-backup skapad av SMS Backup USB.", 14, Color.rgb(91, 101, 122), false);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = dp(10);
        stLp.bottomMargin = dp(12);
        root.addView(status, stLp);

        search = new EditText(this);
        search.setHint("Sök nummer eller meddelandetext");
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(roundRect(Color.WHITE, Color.rgb(220, 226, 238), 14));
        search.setVisibility(View.GONE);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        list = new ListView(this);
        list.setDividerHeight(0);
        list.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        listLp.topMargin = dp(10);
        root.addView(list, listLp);
        conversationAdapter = new ConversationAdapter();
        list.setAdapter(conversationAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> showConversation(filtered.get(position)));

        TextView footer = text("🔒 Ingen internet- eller SMS-behörighet används. Appen läser bara filen du väljer.", 12, Color.rgb(106, 116, 137), false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private void addOpenLastButton() {
        if (currentUri == null) return;
        Button last = secondaryButton("Öppna senaste backup");
        last.setOnClickListener(v -> loadBackup(currentUri));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(10);
        root.addView(last, 3, lp);
    }

    private void chooseBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream", "*/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN && resultCode == RESULT_OK && data != null && data.getData() != null) {
            currentUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(currentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            getSharedPreferences("reader", MODE_PRIVATE).edit().putString("last_uri", currentUri.toString()).apply();
            loadBackup(currentUri);
        }
    }

    private void loadBackup(Uri uri) {
        status.setText("Läser " + fileName(uri) + " …");
        search.setVisibility(View.GONE);
        conversations.clear();
        filtered.clear();
        conversationAdapter.notifyDataSetChanged();
        executor.execute(() -> {
            try {
                Map<String, Conversation> map = parseBackup(uri);
                List<Conversation> result = new ArrayList<>(map.values());
                Collections.sort(result, (a, b) -> Long.compare(b.lastDate, a.lastDate));
                int total = 0;
                for (Conversation c : result) total += c.messages.size();
                final int totalMessages = total;
                runOnUiThread(() -> {
                    conversations.clear();
                    conversations.addAll(result);
                    filtered.clear();
                    filtered.addAll(result);
                    conversationAdapter.notifyDataSetChanged();
                    search.setVisibility(View.VISIBLE);
                    status.setText("✅ " + totalMessages + " SMS i " + result.size() + " konversationer • " + fileName(uri));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("❌ Kunde inte läsa backupen: " + safe(e.getMessage()));
                    Toast.makeText(this, "Kontrollera att du valt JSON-filen från SMS Backup USB", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Map<String, Conversation> parseBackup(Uri uri) throws Exception {
        Map<String, Conversation> map = new LinkedHashMap<>();
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalStateException("Filen kunde inte öppnas");
        try (JsonReader r = new JsonReader(new BufferedReader(new InputStreamReader(input, "UTF-8"), 65536))) {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("messages".equals(name)) {
                    r.beginArray();
                    while (r.hasNext()) {
                        Message m = readMessage(r);
                        String address = cleanAddress(m.address);
                        String key = m.threadId > 0 ? "thread:" + m.threadId : "address:" + address;
                        Conversation c = map.get(key);
                        if (c == null) {
                            c = new Conversation();
                            c.key = key;
                            c.title = address;
                            map.put(key, c);
                        }
                        c.messages.add(m);
                        if (m.date >= c.lastDate) {
                            c.lastDate = m.date;
                            c.preview = m.body == null ? "" : m.body;
                            if (c.title.startsWith("Okänt") && !address.startsWith("Okänt")) c.title = address;
                        }
                    }
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        }
        for (Conversation c : map.values()) {
            Collections.sort(c.messages, Comparator.comparingLong(a -> a.date));
        }
        return map;
    }

    private Message readMessage(JsonReader r) throws Exception {
        Message m = new Message();
        r.beginObject();
        while (r.hasNext()) {
            String n = r.nextName();
            switch (n) {
                case "threadId": m.threadId = nextLong(r); break;
                case "address": m.address = nextString(r); break;
                case "date": m.date = nextLong(r); break;
                case "type": m.type = (int) nextLong(r); break;
                case "body": m.body = nextString(r); break;
                default: r.skipValue();
            }
        }
        r.endObject();
        return m;
    }

    private long nextLong(JsonReader r) throws Exception {
        if (r.peek() == JsonToken.NULL) { r.nextNull(); return 0; }
        if (r.peek() == JsonToken.STRING) {
            String s = r.nextString();
            try { return Long.parseLong(s); } catch (Exception ignored) { return 0; }
        }
        return r.nextLong();
    }

    private String nextString(JsonReader r) throws Exception {
        if (r.peek() == JsonToken.NULL) { r.nextNull(); return ""; }
        return r.nextString();
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        if (q.isEmpty()) {
            filtered.addAll(conversations);
        } else {
            for (Conversation c : conversations) {
                boolean hit = c.title.toLowerCase(Locale.ROOT).contains(q);
                if (!hit) {
                    for (Message m : c.messages) {
                        if (m.body != null && m.body.toLowerCase(Locale.ROOT).contains(q)) { hit = true; break; }
                    }
                }
                if (hit) filtered.add(c);
            }
        }
        conversationAdapter.notifyDataSetChanged();
    }

    private void showConversation(Conversation c) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(14), dp(8));
        header.setBackgroundColor(Color.WHITE);
        Button back = secondaryButton("‹ Tillbaka");
        back.setOnClickListener(v -> showHomeAfterConversation());
        header.addView(back, new LinearLayout.LayoutParams(dp(112), dp(44)));
        TextView title = text(c.title, 18, Color.rgb(22, 32, 55), true);
        title.setSingleLine(true);
        title.setPadding(dp(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(60)));

        TextView count = text(c.messages.size() + " meddelanden", 12, Color.rgb(106, 116, 137), false);
        count.setGravity(Gravity.CENTER);
        page.addView(count, new LinearLayout.LayoutParams(-1, dp(30)));

        ListView messages = new ListView(this);
        messages.setDividerHeight(0);
        messages.setBackgroundColor(Color.rgb(245, 247, 251));
        messages.setAdapter(new MessageAdapter(c));
        page.addView(messages, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        if (!c.messages.isEmpty()) messages.setSelection(c.messages.size() - 1);
    }

    private void showHomeAfterConversation() {
        String lastQuery = search == null ? "" : search.getText().toString();
        Uri keep = currentUri;
        showHome();
        currentUri = keep;
        if (!conversations.isEmpty()) {
            search.setVisibility(View.VISIBLE);
            status.setText("✅ " + totalMessages() + " SMS i " + conversations.size() + " konversationer • " + fileName(currentUri));
            search.setText(lastQuery);
            applyFilter(lastQuery);
        }
    }

    @Override
    public void onBackPressed() {
        View current = getWindow().getDecorView().findViewById(android.R.id.content);
        super.onBackPressed();
    }

    private int totalMessages() {
        int n = 0;
        for (Conversation c : conversations) n += c.messages.size();
        return n;
    }

    private String fileName(Uri uri) {
        if (uri == null) return "backupfil";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        String s = uri.getLastPathSegment();
        return s == null ? "backupfil" : s;
    }

    private String cleanAddress(String s) {
        if (s == null || s.trim().isEmpty()) return "Okänt nummer";
        return s.trim();
    }

    private String safe(String s) { return s == null || s.trim().isEmpty() ? "okänt fel" : s; }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect(Color.rgb(36, 87, 230), Color.rgb(36, 87, 230), 14));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(36, 87, 230));
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect(Color.rgb(239, 244, 255), Color.rgb(194, 210, 255), 13));
        return b;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private class ConversationAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Conversation c = filtered.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));
            row.setBackground(roundRect(Color.WHITE, Color.rgb(229, 233, 242), 15));
            LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
            outer.bottomMargin = dp(8);

            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(c.title, 17, Color.rgb(22, 32, 55), true);
            top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView when = text(c.lastDate > 0 ? shortDate.format(new Date(c.lastDate)) : "", 12, Color.rgb(112, 121, 140), false);
            top.addView(when);
            row.addView(top);

            TextView preview = text(c.preview == null || c.preview.isEmpty() ? "(tomt meddelande)" : c.preview, 14, Color.rgb(79, 90, 112), false);
            preview.setSingleLine(true);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, -2);
            pLp.topMargin = dp(5);
            row.addView(preview, pLp);

            TextView count = text(c.messages.size() + " SMS", 12, Color.rgb(36, 87, 230), true);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
            cLp.topMargin = dp(5);
            row.addView(count, cLp);
            row.setLayoutParams(outer);
            return row;
        }
    }

    private class MessageAdapter extends BaseAdapter {
        private final Conversation conversation;
        MessageAdapter(Conversation c) { conversation = c; }
        @Override public int getCount() { return conversation.messages.size(); }
        @Override public Object getItem(int position) { return conversation.messages.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Message m = conversation.messages.get(position);
            boolean incoming = m.type == 1;

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(4), dp(12), dp(4));
            row.setGravity(Gravity.BOTTOM);

            View spacerA = new View(MainActivity.this);
            View spacerB = new View(MainActivity.this);
            LinearLayout bubble = new LinearLayout(MainActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(13), dp(9), dp(13), dp(8));
            bubble.setBackground(roundRect(incoming ? Color.WHITE : Color.rgb(36, 87, 230), incoming ? Color.rgb(224, 229, 239) : Color.rgb(36, 87, 230), 17));

            TextView body = text(m.body == null || m.body.isEmpty() ? "(tomt meddelande)" : m.body, 15, incoming ? Color.rgb(27, 36, 54) : Color.WHITE, false);
            body.setTextIsSelectable(true);
            bubble.addView(body, new LinearLayout.LayoutParams(-1, -2));
            TextView time = text((incoming ? "Mottaget • " : "Skickat • ") + (m.date > 0 ? dateTime.format(new Date(m.date)) : "okänd tid"), 10, incoming ? Color.rgb(105, 115, 135) : Color.rgb(225, 233, 255), false);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
            tLp.topMargin = dp(5);
            bubble.addView(time, tLp);

            if (incoming) {
                row.addView(bubble, new LinearLayout.LayoutParams(0, -2, 0.82f));
                row.addView(spacerB, new LinearLayout.LayoutParams(0, 1, 0.18f));
            } else {
                row.addView(spacerA, new LinearLayout.LayoutParams(0, 1, 0.18f));
                row.addView(bubble, new LinearLayout.LayoutParams(0, -2, 0.82f));
            }
            return row;
        }
    }

    private static class Message {
        long threadId;
        String address;
        long date;
        int type;
        String body;
    }

    private static class Conversation {
        String key;
        String title;
        String preview = "";
        long lastDate;
        final List<Message> messages = new ArrayList<>();
    }
}
