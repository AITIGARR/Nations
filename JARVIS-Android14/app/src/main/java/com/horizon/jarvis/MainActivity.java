package com.horizon.jarvis;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final String ACTION_START = "com.horizon.jarvis.START";
    static final String ACTION_STOP = "com.horizon.jarvis.STOP";
    static final String ACTION_QUERY = "com.horizon.jarvis.QUERY";
    static final String EVENT = "com.horizon.jarvis.EVENT";
    private static final int PERMISSIONS_REQUEST = 17;
    private TextView status;
    private EditText question;
    private EditText keyInput;
    private boolean registered;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (status != null) status.setText(intent.getStringExtra("message"));
        }
    };

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String value, int size, int color) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextColor(color);
        result.setTextSize(size);
        result.setPadding(0, dp(13), 0, dp(10));
        return result;
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        int cyan = Color.rgb(89, 234, 244);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(30));
        content.setBackgroundColor(Color.rgb(7, 16, 27));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = label("J.A.R.V.I.S.", 34, cyan);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        TextView subtitle = label("ANDROID 14  •  VOICE AI  •  v0.1", 12, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle);
        TextView orb = label("◉", 115, cyan);
        orb.setGravity(Gravity.CENTER);
        content.addView(orb);

        status = label("Готов. Нажмите «АКТИВИРОВАТЬ»", 16, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        content.addView(status);

        Button start = new Button(this);
        start.setText("АКТИВИРОВАТЬ ДЖАРВИСА");
        content.addView(start);
        start.setOnClickListener(v -> startWithPermissions());

        Button stop = new Button(this);
        stop.setText("ОСТАНОВИТЬ");
        content.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this, VoiceService.class).setAction(ACTION_STOP)));

        content.addView(label("Текстовая команда", 15, cyan));
        question = new EditText(this);
        question.setSingleLine(false);
        question.setMinLines(1);
        question.setTextColor(Color.WHITE);
        question.setHintTextColor(Color.LTGRAY);
        question.setHint("Например: какая сегодня погода?");
        content.addView(question);
        Button send = new Button(this);
        send.setText("ОТПРАВИТЬ");
        content.addView(send);
        send.setOnClickListener(v -> {
            String q = question.getText().toString().trim();
            if (q.isEmpty()) return;
            Intent intent = new Intent(this, VoiceService.class).setAction(ACTION_QUERY).putExtra("query", q);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            question.setText("");
        });

        content.addView(label("Ключ OpenAI API (хранится зашифрованно)", 15, cyan));
        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.LTGRAY);
        keyInput.setHint("sk-...");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(keyInput);
        Button save = new Button(this);
        save.setText("СОХРАНИТЬ API-КЛЮЧ");
        content.addView(save);
        save.setOnClickListener(v -> {
            String value = keyInput.getText().toString().trim();
            if (value.isEmpty()) { status.setText("Введите API-ключ."); return; }
            try {
                KeyVault.save(this, value);
                keyInput.setText("");
                status.setText("API-ключ сохранён на устройстве.");
            } catch (Exception e) { status.setText("Ошибка сохранения ключа: " + e.getClass().getSimpleName()); }
        });
        content.addView(label("Активация: «Джарвис» или «Джарвис, открой YouTube». Для голоса нужен системный распознаватель русской речи.", 13, Color.LTGRAY));
    }

    private void startWithPermissions() {
        ArrayList<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), PERMISSIONS_REQUEST);
            return;
        }
        Intent i = new Intent(this, VoiceService.class).setAction(ACTION_START);
        startForegroundService(i);
        status.setText("Запускаю микрофон...");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSIONS_REQUEST) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWithPermissions();
            else status.setText("Для активации нужен доступ к микрофону.");
        }
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(EVENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updateReceiver, filter);
        registered = true;
    }
    @Override protected void onStop() {
        if (registered) unregisterReceiver(updateReceiver);
        registered = false;
        super.onStop();
    }

    static class KeyVault {
        static final String ALIAS = "jarvis_key_v1";
        static void save(Context ctx, String apiKey) throws Exception {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                generator.generateKey();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, (java.security.Key)ks.getKey(ALIAS, null));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            ctx.getSharedPreferences("jarvis", MODE_PRIVATE).edit()
                .putString("enc", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                .putString("iv", android.util.Base64.encodeToString(cipher.getIV(), android.util.Base64.NO_WRAP))
                .apply();
        }
        static String load(Context ctx) {
            try {
                android.content.SharedPreferences prefs = ctx.getSharedPreferences("jarvis", MODE_PRIVATE);
                String encrypted = prefs.getString("enc", "");
                String iv = prefs.getString("iv", "");
                if (encrypted.isEmpty() || iv.isEmpty()) return "";
                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, (java.security.Key)ks.getKey(ALIAS, null),
                    new GCMParameterSpec(128, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)));
                return new String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), StandardCharsets.UTF_8);
            } catch (Exception e) { return ""; }
        }
    }

    public static class VoiceService extends Service implements RecognitionListener {
        private static final String CHANNEL = "jarvis_listening";
        private final Handler handler = new Handler(Looper.getMainLooper());
        private SpeechRecognizer recognizer;
        private TextToSpeech tts;
        private boolean running, listening, speaking, busy, ttsReady;
        private long nextCommandUntil;
        private Runnable restart;

        @Override public void onCreate() {
            super.onCreate();
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "JARVIS — голос", NotificationManager.IMPORTANCE_LOW));
            tts = new TextToSpeech(this, result -> {
                if (result == TextToSpeech.SUCCESS && tts != null) {
                    ttsReady = tts.setLanguage(new Locale("ru", "RU")) >= 0;
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { handler.post(() -> finishTalking()); }
                        @Override public void onError(String id) { handler.post(() -> finishTalking()); }
                    });
                }
            });
        }

        @Override public int onStartCommand(Intent intent, int flags, int startId) {
            String action = intent == null ? ACTION_START : intent.getAction();
            if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
            if (!running) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    emit("Нет доступа к микрофону"); stopSelf(); return START_NOT_STICKY;
                }
                try {
                    startForeground(1, notification("Слушаю фразу «Джарвис»"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                    running = true;
                    emit("Слушаю фразу «Джарвис»");
                    if (SpeechRecognizer.isRecognitionAvailable(this)) {
                        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                        recognizer.setRecognitionListener(this);
                        schedule(500);
                    } else emit("Распознавание речи недоступно. Используйте текстовые команды.");
                } catch (Exception e) { emit("Не удалось начать прослушивание: " + e.getClass().getSimpleName()); stopSelf(); return START_NOT_STICKY; }
            }
            if (ACTION_QUERY.equals(action) && intent != null) {
                String text = intent.getStringExtra("query");
                if (text != null && !text.trim().isEmpty()) {
                    cancelListen();
                    command(text.trim());
                }
            }
            return START_NOT_STICKY;
        }

        private Notification notification(String message) {
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent stopIntent = new Intent(this, VoiceService.class).setAction(ACTION_STOP);
            PendingIntent stopIntentPending = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("JARVIS AI").setContentText(message)
                .setContentIntent(contentIntent).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "СТОП", stopIntentPending).build();
        }

        private void emit(String msg) {
            Intent status = new Intent(EVENT);
            status.setPackage(getPackageName());
            status.putExtra("message", msg);
            sendBroadcast(status);
        }

        private void cancelListen() {
            if (restart != null) handler.removeCallbacks(restart);
            restart = null;
            if (recognizer != null && listening) {
                listening = false;
                recognizer.cancel();
            }
        }

        private void schedule(long delay) {
            if (restart != null) handler.removeCallbacks(restart);
            if (!running || recognizer == null || listening || busy || speaking) return;
            restart = () -> {
                restart = null;
                if (!running || busy || speaking || listening) return;
                try {
                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
                    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                    recognizer.startListening(i);
                    listening = true;
                } catch (Exception e) { schedule(3000); }
            };
            handler.postDelayed(restart, delay);
        }

        private void finishTalking() {
            speaking = false;
            busy = false;
            schedule(550);
        }

        private void say(String text) {
            emit(text);
            if (tts != null && ttsReady) {
                speaking = true;
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply") == TextToSpeech.ERROR) finishTalking();
            } else finishTalking();
        }

        private void onSpeechText(String raw) {
            String s = raw.trim();
            String lower = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
            int idx = -1, length = 0;
            for (String word : new String[]{"джарвис", "жарвис", "джервис", "jarvis"}) {
                int i = lower.indexOf(word);
                if (i >= 0 && (idx < 0 || i < idx)) { idx = i; length = word.length(); }
            }
            if (idx >= 0) {
                String after = s.substring(idx + length).replaceFirst("^[\\s,.:!?—-]+", "").trim();
                if (after.isEmpty()) {
                    nextCommandUntil = SystemClock.elapsedRealtime() + 12000;
                    say("Слушаю, сэр.");
                } else {
                    nextCommandUntil = 0;
                    command(after);
                }
            } else if (SystemClock.elapsedRealtime() < nextCommandUntil) {
                nextCommandUntil = 0;
                command(s);
            } else schedule(600);
        }

        private void command(String question) {
            if (busy) return;
            busy = true;
            String text = question.length() > 1200 ? question.substring(0, 1200) : question;
            String s = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
            emit("Команда: " + text);
            if (s.contains("сколько времени") || s.contains("который час") || s.equals("время")) {
                say("Сейчас " + new SimpleDateFormat("HH:mm", new Locale("ru", "RU")).format(new Date()));
            } else if (s.contains("заряд") || s.contains("батаре")) {
                android.os.BatteryManager b = getSystemService(android.os.BatteryManager.class);
                say("Заряд батареи: " + b.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) + " процентов.");
            } else if (s.contains("остановись") || s.contains("выключись") || s.contains("прекрати слушать")) {
                emit("Отключаюсь.");
                stopSelf();
            } else if (s.contains("открой") || s.contains("запусти")) {
                openCommand(s);
            } else if (s.contains("привет") || s.contains("ты тут")) {
                say("Всегда к вашим услугам, сэр.");
            } else {
                String key = KeyVault.load(this);
                if (key.isEmpty()) { say("Сохраните ключ OpenAI API в приложении, чтобы задавать вопросы искусственному интеллекту."); return; }
                emit("Связываюсь с ИИ...");
                new Thread(() -> {
                    String response;
                    try { response = askOpenAi(key, text); }
                    catch (Exception e) { response = "Ошибка запроса: " + e.getClass().getSimpleName(); }
                    final String answer = response;
                    handler.post(() -> { if (running) say(answer); });
                }, "jarvis-ai-request").start();
            }
        }

        private void openCommand(String query) {
            String pkg = null;
            if (query.contains("ютуб") || query.contains("youtube")) pkg = "com.google.android.youtube";
            else if (query.contains("телеграм") || query.contains("telegram")) pkg = "org.telegram.messenger";
            else if (query.contains("майнкрафт") || query.contains("minecraft")) pkg = "com.mojang.minecraftpe";
            Intent launch = null;
            if (pkg != null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
            else if (query.contains("настрой")) launch = new Intent(Settings.ACTION_SETTINGS);
            else if (query.contains("браузер")) launch = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"));
            if (launch == null) { say("Не удалось найти нужное приложение."); return; }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                // Background activity launches are restricted: show an actionable notification.
                PendingIntent pending = PendingIntent.getActivity(this, 42, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.notify(9, new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle("JARVIS — открыть приложение")
                    .setContentText("Нажмите для запуска").setContentIntent(pending).setAutoCancel(true).build());
                say("Подготовил запуск. Нажмите на уведомление.");
            } catch (Exception e) { say("Не удалось открыть приложение."); }
        }

        private static String askOpenAi(String apiKey, String question) throws Exception {
            URL url = new URL("https://api.openai.com/v1/responses");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(35000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject();
                body.put("model", "gpt-4.1-mini");
                body.put("store", false);
                body.put("instructions", "Ты Джарвис, краткий вежливый голосовой помощник на русском. Отвечай обычно не более двух предложений. Не притворяйся, что управляешь функциями, к которым нет доступа.");
                body.put("input", question);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) return "Сервис ИИ временно недоступен.";
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] part = new byte[4096];
                int n;
                try (InputStream input = stream) {
                    while ((n = input.read(part)) != -1 && buffer.size() < 150000) buffer.write(part, 0, n);
                }
                JSONObject json = new JSONObject(buffer.toString("UTF-8"));
                if (code < 200 || code >= 300) {
                    JSONObject error = json.optJSONObject("error");
                    return "Ошибка API: " + (error == null ? code : error.optString("message", String.valueOf(code)));
                }
                JSONArray items = json.optJSONArray("output");
                StringBuilder output = new StringBuilder();
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    JSONArray parts = item.optJSONArray("content");
                    if (parts == null) continue;
                    for (int j = 0; j < parts.length(); j++) {
                        JSONObject piece = parts.optJSONObject(j);
                        if (piece != null && "output_text".equals(piece.optString("type")))
                            output.append(piece.optString("text")).append(' ');
                    }
                }
                String result = output.toString().trim();
                return result.isEmpty() ? "Ответ от ИИ не получен." : result.substring(0, Math.min(result.length(), 1600));
            } finally { connection.disconnect(); }
        }

        @Override public void onReadyForSpeech(Bundle b) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rms) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partial) {}
        @Override public void onEvent(int type, Bundle b) {}
        @Override public void onError(int error) {
            listening = false;
            if (running && !busy && !speaking) schedule(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 3000 : 1000);
        }
        @Override public void onResults(Bundle b) {
            listening = false;
            ArrayList<String> results = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (results != null && !results.isEmpty()) onSpeechText(results.get(0));
            else schedule(600);
        }
        @Override public IBinder onBind(Intent i) { return null; }
        @Override public void onDestroy() {
            running = false;
            cancelListen();
            if (recognizer != null) recognizer.destroy();
            if (tts != null) { tts.stop(); tts.shutdown(); }
            super.onDestroy();
        }
    }
}
