package com.horizon.jarvis;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class JarvisService extends Service implements RecognitionListener, JarvisOverlay.Listener {
    private static final String CHANNEL = "jarvis_voice_v3";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ArrayList<org.json.JSONObject> conversation = new ArrayList<>();
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private JarvisOverlay overlay;
    private MediaPlayer player;
    private boolean running, listening, busy, speaking, ttsReady;
    private String queuedTts;
    private Runnable restart;
    private long nextCommandUntil;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL, "JARVIS — микрофон", NotificationManager.IMPORTANCE_LOW));
        overlay = new JarvisOverlay(this, this);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                ttsReady = tts.setLanguage(new Locale("ru", "RU")) >= 0;
                tts.setSpeechRate(.94f);
                if (queuedTts != null && ttsReady) {
                    String saved = queuedTts;
                    queuedTts = null;
                    handler.post(() -> { if (running && speaking) speakOnDevice(saved); });
                }
                tts.setPitch(.9f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { handler.post(() -> finishSpeech()); }
                    @Override public void onError(String id) { handler.post(() -> finishSpeech()); }
                });
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? MainActivity.START : intent.getAction();
        if (MainActivity.STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (!running) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                publish("Нет разрешения на микрофон", "system");
                stopSelf();
                return START_NOT_STICKY;
            }
            try {
                startForeground(1, foregroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } catch (Exception e) {
                publish("Не удалось запустить микрофон: " + e.getClass().getSimpleName(), "system");
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            overlay.show();
            publish(Settings.canDrawOverlays(this)
                ? "JARVIS активен. Скажите «Джарвис»." :
                  "JARVIS активен. Для плавающего окна разрешите показ поверх приложений.", "system");
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                try {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                    recognizer.setRecognitionListener(this);
                    schedule(400);
                } catch (Exception e) { publish("Не удалось начать распознавание речи.", "system"); }
            } else {
                publish("Распознавание речи недоступно. Используйте текстовый ввод.", "system");
            }
        }
        if (MainActivity.QUERY.equals(action) && intent != null) {
            String q = intent.getStringExtra("query");
            if (q != null && !q.trim().isEmpty()) command(q.trim());
        }
        return START_NOT_STICKY;
    }

    private Notification foregroundNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent activity = PendingIntent.getActivity(this, 10, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, JarvisService.class).setAction(MainActivity.STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 11, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS AI")
            .setContentText("Слушаю обращение «Джарвис»")
            .setContentIntent(activity)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPending)
            .build();
    }

    private void publish(String message, String role) {
        Intent event = new Intent(MainActivity.EVENT);
        event.setPackage(getPackageName());
        event.putExtra("message", message);
        event.putExtra("role", role);
        sendBroadcast(event);
        if (overlay != null) overlay.update(message);
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
                Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
                speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                listening = true;
                recognizer.startListening(speech);
            } catch (Exception e) {
                listening = false;
                schedule(2500);
            }
        };
        handler.postDelayed(restart, delay);
    }

    private void heard(String words) {
        String lower = words.toLowerCase(Locale.ROOT).replace('ё', 'е');
        int at = -1, size = 0;
        for (String word : new String[] {"джарвис", "жарвис", "джервис", "jarvis"}) {
            int i = lower.indexOf(word);
            if (i != -1 && (at == -1 || i < at)) { at = i; size = word.length(); }
        }
        if (at >= 0) {
            String tail = words.substring(at + size).replaceFirst("^[\\s,.:!?—-]+", "").trim();
            if (tail.isEmpty()) {
                nextCommandUntil = SystemClock.elapsedRealtime() + 15000;
                busy = true;
                speak("Слушаю.", false);
            } else {
                nextCommandUntil = 0;
                command(tail);
            }
        } else if (SystemClock.elapsedRealtime() < nextCommandUntil) {
            nextCommandUntil = 0;
            command(words);
        } else schedule(700);
    }

    @Override public void onCommand(String query) {
        command(query);
    }
    @Override public void onStop() { stopSelf(); }

    private void command(String question) {
        if (!running) return;
        if (busy) { publish("Я ещё обрабатываю предыдущую команду.", "system"); return; }
        busy = true;
        cancelListen();
        String q = question.length() > 1200 ? question.substring(0, 1200) : question;
        String lower = q.toLowerCase(Locale.ROOT).replace('ё', 'е');
        publish(q, "user");

        BasicCommands.Kind basic = BasicCommands.classify(lower);
        if (basic != BasicCommands.Kind.NONE) {
            android.os.BatteryManager battery = getSystemService(android.os.BatteryManager.class);
            int percentage = battery == null ? -1 :
                battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            // Deterministic fast reply. Do not block on an online TTS request.
            speak(BasicCommands.answer(basic, new Date(), percentage), false);
        } else if (lower.contains("остановись") || lower.contains("выключись") || lower.contains("прекрати слушать")) {
            publish("Отключаюсь.", "assistant"); stopSelf();
        } else if (isDeviceCommand(lower)) {
            String outcome = JarvisActions.handle(this, overlay, lower);
            speak(outcome == null ? "Команда не распознана." : outcome, true);
        } else if (lower.equals("привет") || lower.contains("ты тут")) {
            speak("Я на связи. Чем могу помочь?", true);
        } else {
            final boolean forcedOffline = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getBoolean("offline_ai", false);
            final String key = JarvisBrain.getKey(this);
            final boolean useCloud = !forcedOffline && NetworkState.hasValidatedInternet(this)
                && !key.isEmpty();
            publish(useCloud ? "ИИ через интернет..." : "Локальный ИИ (без сети/API)...", "system");
            network.execute(() -> {
                String answer;
                boolean cloudAnswered = false;
                if (useCloud) {
                    try {
                        answer = JarvisBrain.respond(key, conversation, q);
                        conversation.add(JarvisBrain.turn("user", q));
                        conversation.add(JarvisBrain.turn("assistant", answer));
                        while (conversation.size() > 12) { conversation.remove(0); conversation.remove(0); }
                        cloudAnswered = true;
                    } catch (Exception e) {
                        // Even validated Wi-Fi/mobile data cannot guarantee API reachability.
                        answer = null;
                    }
                } else answer = null;
                if (!cloudAnswered) {
                    try {
                        if (!LocalBrain.ready(this)) LocalBrain.install(this);
                        answer = LocalBrain.answer(this, q);
                    } catch (Exception e) {
                        answer = "Офлайн-ИИ пока недоступен: " + e.getClass().getSimpleName() +
                            ". При подключённом интернете проверьте OpenAI API-ключ.";
                    }
                }
                String finalAnswer = answer;
                final boolean usedCloud = cloudAnswered;
                handler.post(() -> { if (running) {
                    publish(usedCloud ? "Ответ из облачного ИИ" : "Ответ локального ИИ", "system");
                    speak(finalAnswer, usedCloud);
                }});
            });
        }
    }

    private boolean isDeviceCommand(String q) {
        return q.startsWith("открой ") || q.startsWith("запусти ") ||
            q.startsWith("включи приложение ") || q.startsWith("открыть ") ||
            q.startsWith("покажи приложение ") || q.startsWith("зайди в ") ||
            q.contains("назад") || q.equals("домой") || q.contains("на главный экран") ||
            q.contains("на рабочий стол") || q.contains("недавние приложения") ||
            q.contains("последние приложения") || q.contains("покажи уведомления") ||
            q.contains("открой уведомления") || q.contains("шторку уведомлений") ||
            q.contains("быстрые настройки") || q.contains("панель быстрых") ||
            q.contains("прокрути ") || q.contains("листай ") ||
            q.startsWith("нажми ") || q.startsWith("кликни ");
    }

    private void speak(String text, boolean preferCloudVoice) {
        if (!running) return;
        publish(text, "assistant");
        speaking = true;

        boolean hasNetwork = NetworkState.hasValidatedInternet(this);
        boolean forcedOffline = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean("offline_ai", false);
        boolean fish = FishVoice.isEnabled(this) && !FishVoice.getKey(this).isEmpty()
            && hasNetwork && !forcedOffline && !"Слушаю.".equals(text);

        if (fish) {
            network.execute(() -> {
                File voice = null;
                try { voice = FishVoice.synthesize(this, text); }
                catch (Exception ignored) { /* transparently fall back to existing speech engines */ }
                final File generated = voice;
                handler.post(() -> {
                    if (!running) return;
                    if (generated != null) playAudio(generated, text);
                    else speakStandard(text, preferCloudVoice);
                });
            });
        } else speakStandard(text, preferCloudVoice);
    }

    private void speakStandard(String text, boolean preferCloudVoice) {
        String key = JarvisBrain.getKey(this);
        boolean cloud = preferCloudVoice && !key.isEmpty()
            && NetworkState.hasValidatedInternet(this)
            && !getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("offline_ai", false)
            && getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("cloud_voice", true);
        if (!cloud) { speakOnDevice(text); return; }
        network.execute(() -> {
            File file;
            try { file = JarvisBrain.createSpeech(this, key, text); }
            catch (Exception e) { file = null; }
            final File finalFile = file;
            handler.post(() -> {
                if (!running) return;
                if (finalFile != null) playAudio(finalFile, text);
                else speakOnDevice(text);
            });
        });
    }

    private void playAudio(File source, String fallback) {
        try {
            if (player != null) { player.release(); player = null; }
            player = new MediaPlayer();
            player.setDataSource(source.getAbsolutePath());
            player.setOnCompletionListener(p -> {
                p.release();
                player = null;
                finishSpeech();
            });
            player.setOnErrorListener((p, what, extra) -> {
                p.release();
                player = null;
                speakOnDevice(fallback);
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) { player.release(); player = null; }
            speakOnDevice(fallback);
        }
    }

    private void speakOnDevice(String text) {
        if (ttsReady && tts != null) {
            int res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_v2_reply");
            if (res == TextToSpeech.ERROR) finishSpeech();
        } else if (tts != null) {
            queuedTts = text;
            handler.postDelayed(() -> {
                if (queuedTts != null && queuedTts.equals(text)) {
                    queuedTts = null;
                    publish("Голос Android не инициализировался. Ответ есть на экране.", "system");
                    finishSpeech();
                }
            }, 4500);
        } else finishSpeech();
    }

    private void finishSpeech() {
        if (!running) return;
        speaking = false;
        busy = false;
        schedule(650);
    }

    @Override public void onReadyForSpeech(Bundle b) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rms) {}
    @Override public void onBufferReceived(byte[] data) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onPartialResults(Bundle b) {}
    @Override public void onEvent(int type, Bundle b) {}
    @Override public void onResults(Bundle results) {
        listening = false;
        ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (words != null && !words.isEmpty()) heard(words.get(0));
        else schedule(900);
    }
    @Override public void onError(int error) {
        listening = false;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            publish("Распознавание остановлено: разрешение микрофона отозвано.", "system");
            stopSelf();
            return;
        }
        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
            publish("Распознавание речи временно недоступно: проверьте интернет или офлайн-языковой пакет.", "system");
        if (running && !busy && !speaking)
            schedule(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 3200 : 1500);
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        running = false;
        cancelListen();
        if (recognizer != null) recognizer.destroy();
        queuedTts = null;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (player != null) { player.release(); player = null; }
        if (overlay != null) overlay.remove();
        network.shutdownNow();
        super.onDestroy();
    }
}
