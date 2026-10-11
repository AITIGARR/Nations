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
    private String detectedLanguage = "ru-RU";
    private boolean autoRecognitionFallback = false;

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
        message = "assistant".equals(role) ? JarvisPersona.withSir(message) : message;
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
                String selectedLanguage = MultilingualSpeech.validated(
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getString(MultilingualSpeech.PREF_LANGUAGE, MultilingualSpeech.AUTO));
                if (!MultilingualSpeech.AUTO.equals(selectedLanguage)) {
                    speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage);
                } else if (Build.VERSION.SDK_INT >= 34 && !autoRecognitionFallback) {
                    speech.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
                    speech.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
                } else {
                    speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
                }
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

    private void heard(ArrayList<String> hypotheses) {
        if (hypotheses == null || hypotheses.isEmpty()) { schedule(700); return; }
        String fallback = hypotheses.get(0);
        String found = null;
        for (String phrase : hypotheses) {
            if (phrase == null) continue;
            String candidate = MultilingualSpeech.wakeCommand(phrase);
            if (candidate != null) { found = candidate; break; }
        }
        if (found != null) {
            nextCommandUntil = 0;
            if (found.isEmpty()) {
                nextCommandUntil = SystemClock.elapsedRealtime() + 15000;
                busy = true;
                speakFast("Слушаю.");
            } else {
                command(found);
            }
        } else if (SystemClock.elapsedRealtime() < nextCommandUntil) {
            nextCommandUntil = 0;
            command(fallback);
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

        String instantAnswer = FastMath.calculate(q);
        BasicCommands.Kind basic = BasicCommands.classify(lower);
        String skillAnswer = basic == BasicCommands.Kind.NONE && instantAnswer == null
            ? JarvisSkills.handle(this, overlay, q) : null;
        if (instantAnswer != null) {
            speakFast(instantAnswer);
        } else if (basic != BasicCommands.Kind.NONE) {
            android.os.BatteryManager battery = getSystemService(android.os.BatteryManager.class);
            int percentage = battery == null ? -1 :
                battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            // Deterministic fast reply. Do not block on an online TTS request.
            String chosen = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(MultilingualSpeech.PREF_LANGUAGE, MultilingualSpeech.AUTO);
            String tag = MultilingualSpeech.AUTO.equals(chosen) ? detectedLanguage : chosen;
            speakFast(BasicCommands.answer(basic, new Date(), percentage, tag));
        } else if (skillAnswer != null) {
            speakFast(skillAnswer);
        } else if (lower.contains("остановись") || lower.contains("выключись") || lower.contains("прекрати слушать")) {
            publish("Отключаюсь.", "assistant"); stopSelf();
        } else if (isDeviceCommand(lower)) {
            String outcome = JarvisActions.handle(this, overlay, q);
            speakFast(outcome == null ? "Команда не распознана." : outcome);
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
        return q.startsWith("open ") || q.startsWith("launch ") ||
            q.startsWith("ouvre ") || q.startsWith("abre ") ||
            q.startsWith("öffne ") || q.startsWith("apri ") || q.startsWith("abra ") ||
            q.startsWith("افتح ") || q.startsWith("打开") ||
            q.equals("go back") || q.equals("back") || q.equals("home") ||
            q.equals("scroll down") || q.equals("scroll up") ||
            q.startsWith("type ") || q.startsWith("enter text ") ||
            q.startsWith("tap ") || q.equals("read the screen") ||
            q.startsWith("открой ") || q.startsWith("запусти ") ||
            q.startsWith("включи приложение ") || q.startsWith("открыть ") ||
            q.startsWith("покажи приложение ") || q.startsWith("зайди в ") ||
            q.contains("назад") || q.equals("домой") || q.contains("на главный экран") ||
            q.contains("на рабочий стол") || q.contains("недавние приложения") ||
            q.contains("последние приложения") || q.contains("покажи уведомления") ||
            q.contains("открой уведомления") || q.contains("шторку уведомлений") ||
            q.contains("быстрые настройки") || q.contains("панель быстрых") ||
            q.contains("прокрути ") || q.contains("листай ") ||
            q.startsWith("нажми ") || q.startsWith("кликни ") ||
            q.startsWith("введи ") || q.startsWith("напечатай ") ||
            q.startsWith("напиши в поле ") || q.startsWith("вставь текст ") ||
            q.contains("клавиатуру") || q.equals("очисти поле") ||
            q.equals("очисти текст") || q.equals("сотри текст в поле") ||
            q.equals("удали последний символ") || q.equals("сотри последнюю букву") ||
            q.contains("прочитай экран") || q.contains("что на экране");
    }

    /** Fast path: do not queue neural inference, Fish API or first-run Piper model loading. */
    private void speakFast(String text) {
        if (!running) return;
        text = JarvisPersona.withSir(text);
        publish(text, "assistant");
        speaking = true;
        speakOnDevice(text);
    }

    private void speak(String text, boolean preferCloudVoice) {
        if (!running) return;
        final String spoken = JarvisPersona.withSir(text);
        publish(spoken, "assistant");
        speaking = true;

        boolean hasNetwork = NetworkState.hasValidatedInternet(this);
        boolean forcedOffline = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean("offline_ai", false);
        boolean fish = FishVoice.isEnabled(this) && !FishVoice.getKey(this).isEmpty()
            && hasNetwork && !forcedOffline && !"Слушаю.".equals(text);

        if (fish) {
            network.execute(() -> {
                File voice = null;
                try { voice = FishVoice.synthesize(this, spoken); }
                catch (Exception ignored) { /* transparently fall back to existing speech engines */ }
                final File generated = voice;
                handler.post(() -> {
                    if (!running) return;
                    if (generated != null) playAudio(generated, spoken);
                    else speakOfflineOrStandard(spoken, preferCloudVoice);
                });
            });
        } else speakOfflineOrStandard(spoken, preferCloudVoice);
    }

    private void speakOfflineOrStandard(String text, boolean preferCloudVoice) {
        if (PiperVoice.enabled(this) && "ru".equals(currentLocale().getLanguage())
            && !text.startsWith("Слушаю")) {
            network.execute(() -> {
                File generated = null;
                try {
                    generated = new File(PiperVoice.synthesize(this, text));
                } catch (Exception e) {
                    // Keep built-in Android voice available if voice files/native runtime fail.
                    android.util.Log.w("JARVIS", "Piper offline voice unavailable: " + e.getClass().getSimpleName());
                }
                File wav = generated;
                handler.post(() -> {
                    if (!running) return;
                    if (wav != null && wav.isFile()) playAudio(wav, text);
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

    private Locale currentLocale() {
        String selected = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getString(MultilingualSpeech.PREF_LANGUAGE, MultilingualSpeech.AUTO);
        return MultilingualSpeech.voiceLocale(selected, detectedLanguage);
    }

    private void speakOnDevice(String text) {
        if (ttsReady && tts != null) {
            Locale lang = currentLocale();
            if (tts.isLanguageAvailable(lang) >= TextToSpeech.LANG_AVAILABLE)
                tts.setLanguage(lang);
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
        if (words != null && !words.isEmpty()) heard(words);
        else schedule(900);
    }
    @Override public void onLanguageDetection(Bundle results) {
        if (Build.VERSION.SDK_INT < 34 || results == null) return;
        String tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
        if (tag != null && tag.matches("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8}){0,3}")
            && !tag.equalsIgnoreCase("und") && !tag.equalsIgnoreCase(detectedLanguage)) {
            detectedLanguage = tag;
            publish("Язык речи: " + MultilingualSpeech.shortLabel(tag), "system");
        }
    }
    @Override public void onError(int error) {
        listening = false;
        if (Build.VERSION.SDK_INT >= 34 &&
            (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
             error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
            if (!autoRecognitionFallback) {
                autoRecognitionFallback = true;
                publish("Автоопределение языка недоступно; переключаюсь на системный язык. "
                    + "Можно выбрать язык вручную.", "system");
            }
        }
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
