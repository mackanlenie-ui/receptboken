package se.steffy.reklamskydd;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.VpnService;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView state, count;
    private Button toggle;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        buildUi();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad, dp(38), pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(Color.rgb(7,17,31));

        TextView logo = text("✓", 46, Color.WHITE); logo.setGravity(Gravity.CENTER);
        logo.setBackgroundResource(R.drawable.circle_green); root.addView(logo, new LinearLayout.LayoutParams(dp(92),dp(92)));
        TextView title = text("ReklamSkydd", 30, Color.WHITE); title.setPadding(0,dp(18),0,dp(6)); root.addView(title);
        TextView sub = text("Skyddar appar och webbläsare mot reklam och spårning", 16, Color.rgb(164,180,201));
        sub.setGravity(Gravity.CENTER); root.addView(sub);

        state = text("Skyddet är avstängt", 20, Color.rgb(248,113,113)); state.setPadding(0,dp(34),0,dp(16)); root.addView(state);
        toggle = new Button(this); toggle.setTextColor(Color.WHITE); toggle.setTextSize(18); toggle.setAllCaps(false);
        toggle.setOnClickListener(v -> onToggle()); root.addView(toggle, new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20),dp(18),dp(20),dp(18));
        card.setBackgroundResource(R.drawable.card); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(28),0,0);
        count = text("0", 36, Color.WHITE); count.setGravity(Gravity.CENTER); card.addView(count);
        TextView ct = text("blockerade anslutningar", 15, Color.rgb(164,180,201)); ct.setGravity(Gravity.CENTER); card.addView(ct); root.addView(card,cp);

        CheckBox auto = new CheckBox(this); auto.setText("Starta skyddet automatiskt efter omstart"); auto.setTextColor(Color.WHITE); auto.setTextSize(15);
        auto.setPadding(0,dp(25),0,0); auto.setChecked(getPreferences().getBoolean("auto",false));
        auto.setOnCheckedChangeListener((b, checked) -> getPreferences().edit().putBoolean("auto",checked).apply()); root.addView(auto);

        Button update = new Button(this); update.setText("Uppdatera blockeringslistan"); update.setAllCaps(false);
        update.setOnClickListener(v -> { Toast.makeText(this,"Listan uppdateras i bakgrunden",Toast.LENGTH_SHORT).show(); startAction(AdBlockVpnService.ACTION_UPDATE); });
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1,dp(54)); up.setMargins(0,dp(16),0,0); root.addView(update,up);
        TextView privacy = text("Ingen webbhistorik sparas eller skickas från telefonen.", 13, Color.rgb(115,133,156)); privacy.setGravity(Gravity.CENTER); privacy.setPadding(0,dp(24),0,0); root.addView(privacy);
        setContentView(root); refresh();
    }

    private void onToggle() {
        if (AdBlockVpnService.running) startAction(AdBlockVpnService.ACTION_STOP);
        else {
            Intent permission = VpnService.prepare(this);
            if (permission != null) startActivityForResult(permission, 1); else startAction(AdBlockVpnService.ACTION_START);
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data); if (request==1 && result==RESULT_OK) startAction(AdBlockVpnService.ACTION_START);
    }
    private void startAction(String action) {
        Intent i = new Intent(this,AdBlockVpnService.class).setAction(action);
        if (Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        new Handler(Looper.getMainLooper()).postDelayed(this::refresh,400);
    }
    private void refresh() {
        if (state==null) return;
        boolean on=AdBlockVpnService.running; state.setText(on?"Skyddet är aktivt":"Skyddet är avstängt");
        state.setTextColor(Color.rgb(on?34:248,on?197:113,on?94:113)); toggle.setText(on?"Stäng av skyddet":"Aktivera skyddet");
        toggle.setBackgroundResource(on?R.drawable.button_red:R.drawable.button_green);
        count.setText(String.valueOf(getPreferences().getLong("blocked",0)));
    }
    private SharedPreferences getPreferences(){return getSharedPreferences("settings",MODE_PRIVATE);}
    private TextView text(String s,int size,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v; }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();registerReceiverCompat();refresh();}
    @Override protected void onPause(){super.onPause();try{unregisterReceiver(receiver);}catch(Exception ignored){}}
    private void registerReceiverCompat(){IntentFilter f=new IntentFilter(AdBlockVpnService.ACTION_STATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}
}
