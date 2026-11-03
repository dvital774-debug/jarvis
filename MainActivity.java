package com.example.monagent;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);

        TextView t = new TextView(this);
        t.setText("Jarvis\nDites « Jarvis » suivi de votre demande.");
        t.setTextSize(20);
        root.addView(t);

        EditText key = new EditText(this);
        key.setHint("Clé API Anthropic");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString("api_key", ""));
        root.addView(key);

        root.addView(button("Enregistrer la clé", v -> {
            prefs.edit().putString("api_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Clé enregistrée", Toast.LENGTH_SHORT).show();
        }));

        root.addView(button("Démarrer Jarvis (voix)", v -> {
            if (checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED) {
                startJarvis();
            } else {
                requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 3);
            }
        }));

        root.addView(button("Arrêter Jarvis", v -> stopService(new Intent(this, JarvisService.class))));

        root.addView(button("Autoriser la lecture des notifications", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));

        root.addView(button("Activer le filtrage des appels", v -> {
            RoleManager rm = getSystemService(RoleManager.class);
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1);
        }));

        root.addView(button("Autoriser notifications + contacts", v ->
                requestPermissions(new String[]{
                        "android.permission.POST_NOTIFICATIONS",
                        "android.permission.READ_CONTACTS"}, 2)));

        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 3 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startJarvis();
    }

    private void startJarvis() {
        startForegroundService(new Intent(this, JarvisService.class));
    }

    private Button button(String label, android.view.View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setOnClickListener(l);
        return btn;
    }
}
