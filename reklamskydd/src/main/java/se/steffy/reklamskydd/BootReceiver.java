package se.steffy.reklamskydd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("auto", false)) {
            Intent service = new Intent(context, AdBlockVpnService.class).setAction(AdBlockVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
        }
    }
}
