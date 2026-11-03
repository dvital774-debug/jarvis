package com.example.monagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

public class Alerts {
    private static final String CHANNEL = "alerts";
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private static Notification.Builder base(Context c, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Alertes de Jarvis", NotificationManager.IMPORTANCE_HIGH));
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true);
    }

    static void show(Context c, String title, String text) {
        c.getSystemService(NotificationManager.class)
                .notify(NEXT_ID.incrementAndGet(), base(c, title, text).build());
    }

    /**
     * Propose une réponse avec un bouton « Envoyer ».
     * L'envoi n'a lieu que si vous touchez le bouton, et le téléphone doit être déverrouillé (Android 12+).
     */
    static void showReply(Context c, String sender, String reply, Notification.Action original) {
        int id = NEXT_ID.incrementAndGet();
        SendReplyReceiver.PENDING.put(id, original);

        Intent i = new Intent(c, SendReplyReceiver.class).putExtra("id", id).putExtra("reply", reply);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action.Builder ab = new Notification.Action.Builder(
                Icon.createWithResource(c, android.R.drawable.ic_menu_send), "Envoyer", pi);
        if (Build.VERSION.SDK_INT >= 31) ab.setAuthenticationRequired(true);

        Notification n = base(c, "Réponse suggérée pour " + sender, reply).addAction(ab.build()).build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
