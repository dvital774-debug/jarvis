package com.example.monagent;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Envoie la réponse suggérée en réutilisant le bouton « Répondre » de la notification d'origine. */
public class SendReplyReceiver extends BroadcastReceiver {
    static final Map<Integer, Notification.Action> PENDING = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("id", -1);
        String reply = intent.getStringExtra("reply");
        Notification.Action original = PENDING.remove(id);

        if (original == null || reply == null) {
            Alerts.show(context, "Jarvis", "Envoi impossible : la conversation n'est plus disponible.");
            return;
        }
        try {
            RemoteInput[] inputs = original.getRemoteInputs();
            Bundle results = new Bundle();
            results.putCharSequence(inputs[0].getResultKey(), reply);
            Intent fill = new Intent();
            RemoteInput.addResultsToIntent(inputs, fill, results);
            original.actionIntent.send(context, 0, fill);
            context.getSystemService(NotificationManager.class).cancel(id);
        } catch (Exception e) {
            Alerts.show(context, "Jarvis", "Envoi impossible : " + e.getMessage());
        }
    }
}
