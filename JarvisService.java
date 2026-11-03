package com.example.monagent;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Jarvis vocal : écoute en continu, réagit au mot « Jarvis », envoie la demande à Claude
 * et lit la réponse à voix haute. Après une réponse, il écoute une fois de plus sans mot d'éveil.
 */
public class JarvisService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final String WAKE_WORD = "jarvis";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final JSONArray history = new JSONArray();

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean speaking = false;
    private boolean followUp = false; // vrai juste après une réponse : pas besoin de redire « Jarvis »

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "jarvis")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Jarvis écoute")
                .setContentText("Dites « Jarvis » suivi de votre demande.")
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);

        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        tts.setLanguage(Locale.FRENCH);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { speaking = false; main.post(JarvisService.this::listen); }
            @Override public void onError(String id) { speaking = false; main.post(JarvisService.this::listen); }
        });
        ttsReady = true;
        speak("Jarvis est prêt.");
    }

    // ------------------------------------------------------------ Écoute

    private void listen() {
        if (speaking || !ttsReady) return;
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        recognizer.startListening(i);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) { main.postDelayed(this::listen, 300); return; }

        String heard = list.get(0).trim();
        int idx = heard.toLowerCase(Locale.FRENCH).indexOf(WAKE_WORD);
        String command;
        if (idx >= 0) command = heard.substring(idx + WAKE_WORD.length()).trim();
        else if (followUp) command = heard;
        else { main.postDelayed(this::listen, 300); return; }

        followUp = false;
        if (command.isEmpty()) {
            followUp = true;
            speak("Oui ?");
        } else {
            ask(command);
        }
    }

    @Override
    public void onError(int error) {
        followUp = false;
        main.postDelayed(this::listen, 700);
    }

    // ------------------------------------------------------------ Claude + voix

    private void ask(String command) {
        // Sécurité : Jarvis ne traite rien tant que le téléphone est verrouillé
        if (getSystemService(KeyguardManager.class).isDeviceLocked()) {
            speak("Téléphone verrouillé. Déverrouillez-le d'abord.");
            return;
        }
        String key = getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("api_key", "");
        if (key.isEmpty()) { speak("Il manque la clé API dans l'application."); return; }

        pool.execute(() -> {
            String reply;
            try {
                synchronized (history) {
                    history.put(new JSONObject().put("role", "user").put("content", command));
                    reply = Claude.chat(key, history);
                    history.put(new JSONObject().put("role", "assistant").put("content", reply));
                    while (history.length() > 20) history.remove(0); // garde les 10 derniers échanges
                    while (history.length() > 0 && !"user".equals(history.getJSONObject(0).optString("role"))) history.remove(0);
                }
            } catch (Exception e) {
                android.util.Log.e("Jarvis", e.toString());
                reply = "Désolé, je n'arrive pas à joindre mon cerveau.";
            }
            final String out = reply;
            main.post(() -> { followUp = true; speak(out); });
        });
    }

    private void speak(String text) {
        if (!ttsReady) return;
        speaking = true;
        if (recognizer != null) recognizer.cancel(); // évite que Jarvis s'écoute lui-même
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    // ------------------------------------------------------------ Cycle de vie

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        pool.shutdownNow();
        super.onDestroy();
    }

    // Callbacks inutilisés
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
