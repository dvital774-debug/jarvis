package com.example.monagent;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

/** Prévient quand un numéro inconnu appelle. N'empêche jamais l'appel. */
public class CallScreener extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING && details.getHandle() != null) {
            String number = details.getHandle().getSchemeSpecificPart();
            if (!inContacts(number)) {
                Alerts.show(this, "Appel d'un numéro inconnu", number + " : méfiance si on vous demande un code ou de l'argent.");
            }
        }
        respondToCall(details, new CallResponse.Builder().build()); // laisse passer l'appel
    }

    private boolean inContacts(String number) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
                return c != null && c.getCount() > 0;
            }
        } catch (SecurityException e) {
            return true; // permission contacts absente : on ne prévient pas pour éviter les faux positifs
        }
    }
}
