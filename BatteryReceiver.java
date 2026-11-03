package com.example.monagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BatteryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Alerts.show(context, "Batterie faible", "Pensez à brancher le chargeur.");
    }
}
