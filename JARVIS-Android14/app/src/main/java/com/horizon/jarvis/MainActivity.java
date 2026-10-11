package com.horizon.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.io.File;

public class MainActivity extends Activity {
    static final String PREFS = "jarvis_v2";
    static final String EVENT = "com.horizon.jarvis.EVENT";
    static final String START = "com.horizon.jarvis.START";
    static final String STOP = "com.horizon.jarvis.STOP";
    static final String QUERY = "com.horizon.jarvis.QUERY";
    private static final int PERMISSIONS = 400;
    private static final int CYAN = Color.rgb(0, 222, 255);
    private static final int DARK = Color.rgb(5, 15, 28);
    private LinearLayout transcript;
    private TextView state;
    private TextView overlayHint;
    private ScrollView messagesScroll;
    private boolean receiverAdded;
    private boolean notificationPrompted;

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message == null) return;
            state.setText(message);
            String role = intent.getStringExtra("role");
            if (role != null && ("user".equals(role) || "assistant".equals(role))) addLine(role, message);
        }
    };

    private int px(float dp) { return (int)(getResources().getDisplayMetrics().density * dp + .5f); }

    static GradientDrawable bg(int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(1, border);
        return d;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackground(bg(Color.rgb(9, 48, 69), CYAN, px(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(49));
        lp.topMargin = px(9);
        b.setLayoutParams(lp);
        return b;
    }

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(DARK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(18), px(20), px(20));
        rootScroll.addView(root);
        setContentView(rootScroll);

        TextView headline = text("J.A.R.V.I.S.", 32, CYAN);
        headline.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        headline.setGravity(Gravity.CENTER);
        root.addView(headline);

        TextView subtitle = text("PERSONAL ARTIFICIAL INTELLIGENCE  //  07", 10, 0xFF7EAEC7);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(.1f);
        root.addView(subtitle);

        Orb orb = new Orb(this);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(-1, px(248));
        orbParams.topMargin = px(9);
        root.addView(orb, orbParams);

        this.state = text("Система ожидает запуска", 16, Color.WHITE);
        this.state.setGravity(Gravity.CENTER);
        root.addView(this.state);

        TextView caption = text("АКТИВАЦИЯ: «ДЖАРВИС»   •   ANDROID 14", 11, 0xFF84B3C9);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, px(10), 0, px(6));
        root.addView(caption);

        Button start = action("◉   ЗАПУСТИТЬ ГОЛОСОВОЙ РЕЖИМ");
        root.addView(start);
        start.setOnClickListener(v -> startWithPermission());
        Button stop = action("■   ОСТАНОВИТЬ АССИСТЕНТА");
        root.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this, JarvisService.class).setAction(STOP)));

        TextView sec = text("ПОВЕРХ ДРУГИХ ПРИЛОЖЕНИЙ", 12, CYAN);
        sec.setPadding(0, px(18), 0, px(7));
        root.addView(sec);
        overlayHint = text("", 12, Color.LTGRAY);
        root.addView(overlayHint);
        Button permission = action("▣   РАЗРЕШИТЬ ПЛАВАЮЩЕЕ ОКНО");
        root.addView(permission);
        permission.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                startWithPermission();
            }
        });

        TextView controls = text("УПРАВЛЕНИЕ ТЕЛЕФОНОМ", 12, CYAN);
        controls.setPadding(0, px(17), 0, px(7));
        root.addView(controls);
        TextView accessibilityInfo = text("Для команд «домой», «назад», прокрутки и нажатий включите сервис JARVIS в специальных возможностях.", 12, 0xFF93B3C8);
        root.addView(accessibilityInfo);
        Button accessibilityButton = action("◈   ОТКРЫТЬ СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ");
        root.addView(accessibilityButton);
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button explainAccess = action("ⓘ   ПОЧЕМУ ANDROID БЛОКИРУЕТ ДОСТУП?");
        root.addView(explainAccess);
        explainAccess.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
            .setTitle("Настройки с ограниченным доступом")
            .setMessage("Android 13–14 может блокировать специальные возможности для приложений, установленных из APK.\n\n" +
                "Если вы доверяете этой сборке:\n" +
                "1. Настройки → Приложения → JARVIS AI.\n" +
                "2. Меню ⋮ → «Разрешить ограниченные настройки» (если пункт доступен).\n" +
                "3. Вернитесь в «Специальные возможности» и включите JARVIS.\n\n" +
                "Разрешение открывает доступ к управлению интерфейсом других приложений. " +
                "Оно необязательно для времени, даты, заряда и обычных вопросов.")
            .setNeutralButton("Сведения о приложении", (d, which) -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            })
            .setPositiveButton("Понятно", null)
            .show());
        Button notificationButton = action("◉   НАСТРОЙКИ УВЕДОМЛЕНИЙ");
        root.addView(notificationButton);
        notificationButton.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });

        TextView askTitle = text("ДИАЛОГ С JARVIS", 12, CYAN);
        askTitle.setPadding(0, px(22), 0, px(8));
        root.addView(askTitle);
        messagesScroll = new ScrollView(this);
        messagesScroll.setBackground(bg(0xFF071C2C, 0xFF15577B, px(14)));
        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcript.setPadding(px(13), px(13), px(13), px(13));
        messagesScroll.addView(transcript);
        root.addView(messagesScroll, new LinearLayout.LayoutParams(-1, px(176)));
        addLine("assistant", "Система готова. Произнесите «Джарвис» после запуска прослушивания.");

        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setTextSize(14);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF82A3B5);
        input.setHint("Написать Джарвису...");
        root.addView(input);
        Button send = action("➤   ОТПРАВИТЬ");
        root.addView(send);
        send.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) return;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                BasicCommands.Kind basic = BasicCommands.classify(q);
                if (basic != BasicCommands.Kind.NONE) {
                    android.os.BatteryManager battery = getSystemService(android.os.BatteryManager.class);
                    int percentage = battery == null ? -1 :
                        battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    addLine("user", q);
                    String response = BasicCommands.answer(basic, new Date(), percentage);
                    addLine("assistant", response);
                    state.setText(response);
                    input.setText("");
                } else {
                    state.setText("Разрешите микрофон для голосового режима или запустите JARVIS.");
                }
                return;
            }
            Intent i = new Intent(this, JarvisService.class).setAction(QUERY).putExtra("query", q);
            startForegroundService(i);
            input.setText("");
        });

        TextView settingsTitle = text("НАСТРОЙКИ ИИ", 12, CYAN);
        settingsTitle.setPadding(0, px(22), 0, px(8));
        root.addView(settingsTitle);
        EditText apiKey = new EditText(this);
        apiKey.setTextSize(14);
        apiKey.setTextColor(Color.WHITE);
        apiKey.setSingleLine(true);
        apiKey.setHintTextColor(0xFF82A3B5);
        apiKey.setHint(JarvisBrain.hasKey(this) ? "Ключ сохранён — введите новый для замены" : "OpenAI API key (sk-...)");
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(apiKey);
        Button save = action("СОХРАНИТЬ API-КЛЮЧ");
        root.addView(save);
        save.setOnClickListener(v -> {
            String value = apiKey.getText().toString().trim();
            if (value.isEmpty()) { this.state.setText("Введите API-ключ"); return; }
            try {
                JarvisBrain.saveKey(this, value);
                apiKey.setText("");
                apiKey.setHint("Ключ сохранён — введите новый для замены");
                this.state.setText("Ключ зашифрован и сохранён только на телефоне.");
            } catch (Exception e) { this.state.setText("Не удалось сохранить ключ."); }
        });
        Switch cloudVoice = new Switch(this);
        cloudVoice.setText("Голос ИИ (сгенерированное аудио; оплачивается через API)");
        cloudVoice.setTextSize(12);
        cloudVoice.setTextColor(Color.WHITE);
        cloudVoice.setPadding(0, px(13), 0, px(10));
        cloudVoice.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("cloud_voice", true));
        cloudVoice.setOnCheckedChangeListener((b, on) ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("cloud_voice", on).apply());
        root.addView(cloudVoice);

        TextView fishTitle = text("ГОЛОС FISH AUDIO — JARVIS", 12, CYAN);
        fishTitle.setPadding(0, px(22), 0, px(7));
        root.addView(fishTitle);
        TextView fishInfo = text("Fish Audio озвучивает ответы живым, выразительным голосом. " +
            "В списке голосов есть «Jarvis | Iron Man». Доступ и права на использование голоса регулируются Fish Audio. " +
            "Нужен интернет и отдельный Fish API-ключ.", 12, 0xFF93B3C8);
        root.addView(fishInfo);
        Button fishWeb = action("♫   ПРОСЛУШАТЬ ГОЛОС НА FISH AUDIO");
        root.addView(fishWeb);
        fishWeb.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse(FishVoice.SOURCE_VOICE_URL))));

        EditText fishKeyInput = new EditText(this);
        fishKeyInput.setSingleLine(true);
        fishKeyInput.setTextSize(14);
        fishKeyInput.setTextColor(Color.WHITE);
        fishKeyInput.setHintTextColor(0xFF82A3B5);
        fishKeyInput.setHint(FishVoice.getKey(this).isEmpty()
            ? "Fish Audio API key" : "Ключ Fish сохранён — введите новый для замены");
        fishKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(fishKeyInput);

        EditText fishVoiceId = new EditText(this);
        fishVoiceId.setTextColor(Color.WHITE);
        fishVoiceId.setSingleLine(true);
        fishVoiceId.setTextSize(12);
        fishVoiceId.setHintTextColor(0xFF82A3B5);
        fishVoiceId.setHint("Fish Audio reference_id");
        fishVoiceId.setText(FishVoice.voiceId(this));
        root.addView(fishVoiceId);

        Button fishSave = action("СОХРАНИТЬ НАСТРОЙКИ FISH AUDIO");
        root.addView(fishSave);
        fishSave.setOnClickListener(v -> {
            try {
                String id = fishVoiceId.getText().toString().trim();
                FishVoice.saveVoiceId(this, id);
                String value = fishKeyInput.getText().toString().trim();
                if (!value.isEmpty()) FishVoice.saveKey(this, value);
                fishKeyInput.setText("");
                fishKeyInput.setHint("Ключ Fish сохранён — введите новый для замены");
                state.setText("Настройки Fish Audio сохранены в защищённом хранилище телефона.");
            } catch (Exception e) {
                state.setText("Ошибка Fish Audio: проверьте 32-символьный ID и API-ключ.");
            }
        });

        Switch fishEnabled = new Switch(this);
        fishEnabled.setText("Озвучивать ответы через Fish Audio");
        fishEnabled.setTextColor(Color.WHITE);
        fishEnabled.setTextSize(12);
        fishEnabled.setChecked(FishVoice.isEnabled(this));
        fishEnabled.setOnCheckedChangeListener((button, enabled) -> {
            FishVoice.setEnabled(this, enabled);
            if (enabled && FishVoice.getKey(this).isEmpty())
                state.setText("Чтобы Fish Audio заработал, сохраните отдельный Fish API-ключ.");
        });
        root.addView(fishEnabled);

        Button fishPreview = action("▶   ПРОВЕРИТЬ ГОЛОС FISH AUDIO");
        root.addView(fishPreview);
        fishPreview.setOnClickListener(v -> {
            if (FishVoice.getKey(this).isEmpty()) {
                state.setText("Сначала введите и сохраните Fish Audio API-ключ.");
                return;
            }
            if (!NetworkState.hasValidatedInternet(this)) {
                state.setText("Для синтеза Fish Audio нужен интернет.");
                return;
            }
            fishPreview.setEnabled(false);
            state.setText("Fish Audio синтезирует тестовую фразу...");
            new Thread(() -> {
                java.io.File audio = null;
                String error = null;
                try {
                    audio = FishVoice.synthesize(this,
                        "Добрый вечер. Джарвис на связи. Чем могу помочь?");
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final java.io.File result = audio;
                final String err = error;
                runOnUiThread(() -> {
                    fishPreview.setEnabled(true);
                    if (isFinishing()) return;
                    if (result == null) {
                        state.setText("Fish Audio не ответил: " + (err == null ? "неизвестная ошибка" : err));
                        return;
                    }
                    try {
                        android.media.MediaPlayer testPlayer = new android.media.MediaPlayer();
                        testPlayer.setDataSource(result.getAbsolutePath());
                        testPlayer.setOnCompletionListener(p -> p.release());
                        testPlayer.setOnErrorListener((p, what, extra) -> {
                            p.release();
                            state.setText("Ошибка воспроизведения Fish Audio.");
                            return true;
                        });
                        testPlayer.prepare();
                        testPlayer.start();
                        state.setText("Воспроизвожу пример голоса Fish Audio.");
                    } catch (Exception e) {
                        state.setText("Не удалось воспроизвести звук.");
                    }
                });
            }, "jarvis-fish-preview").start();
        });

        TextView piperTitle = text("ГОЛОС БЕЗ ИНТЕРНЕТА — PIPER DMITRI", 12, CYAN);
        piperTitle.setPadding(0, px(22), 0, px(7));
        root.addView(piperTitle);
        root.addView(text("Русский мужской голос Дмитрий, встроенный в приложение. " +
            "Работает без Fish Audio и API-ключей. При первом использовании распакуется локально.", 12, 0xFF93B3C8));
        Switch piperToggle = new Switch(this);
        piperToggle.setText("Офлайн-голос Piper Dmitri");
        piperToggle.setTextSize(12);
        piperToggle.setTextColor(Color.WHITE);
        piperToggle.setChecked(PiperVoice.enabled(this));
        piperToggle.setOnCheckedChangeListener((b, enabled) -> PiperVoice.setEnabled(this, enabled));
        root.addView(piperToggle);
        Button piperPreview = action("▶   ПРОВЕРИТЬ ОФЛАЙН-ГОЛОС");
        root.addView(piperPreview);
        piperPreview.setOnClickListener(v -> {
            piperPreview.setEnabled(false);
            state.setText("Piper готовит голос... Это может занять несколько секунд.");
            new Thread(() -> {
                File voice = null;
                String error = null;
                try {
                    voice = new File(PiperVoice.synthesize(this,
                        "Добрый вечер. Джарвис на связи. Все системы работают штатно."));
                } catch (Exception e) { error = e.getClass().getSimpleName(); }
                final File audio = voice;
                final String problem = error;
                runOnUiThread(() -> {
                    piperPreview.setEnabled(true);
                    if (isFinishing()) return;
                    if (audio == null) {
                        state.setText("Ошибка Piper: " + problem);
                        return;
                    }
                    try {
                        android.media.MediaPlayer sample = new android.media.MediaPlayer();
                        sample.setDataSource(audio.getAbsolutePath());
                        sample.setOnCompletionListener(p -> p.release());
                        sample.setOnErrorListener((p, what, extra) -> { p.release(); return true; });
                        sample.prepare();
                        sample.start();
                        state.setText("Проигрываю офлайн-голос Piper Dmitri.");
                    } catch (Exception e) { state.setText("Не удалось воспроизвести Piper."); }
                });
            }, "jarvis-piper-test").start();
        });

        TextView localTitle = text("АВТОМАТИЧЕСКИЙ ИИ: ОНЛАЙН / ОФЛАЙН", 12, CYAN);
        localTitle.setPadding(0, px(22), 0, px(7));
        root.addView(localTitle);
        TextView localDetail = text(LocalBrain.ready(this)
            ? "АВТО: доступен интернет и API-ключ → облачный ИИ; нет сети/ошибка API → локальный SmolLM2. Маленькая модель хуже отвечает на русском."
            : "АВТО: при доступном интернете и API-ключе работает облачный ИИ. Без них локальный SmolLM2 автоматически подготовится из APK (нужно около 105 МБ свободного места).", 12, 0xFF93B3C8);
        root.addView(localDetail);
        TextView strongInfo = text(LocalBrain.hasStrong(this)
            ? "Улучшенная русскоязычная модель Qwen2.5-0.5B уже установлена."
            : "Хотите лучшее качество офлайн-ответов? Можно отдельно установить Qwen2.5-0.5B (Apache 2.0). Загрузка около 398 МБ. Для коротких команд не нужна.", 12, 0xFF93B3C8);
        root.addView(strongInfo);
        Button strongerModel = action(LocalBrain.hasStrong(this)
            ? "✓   QWEN2.5 УЖЕ УСТАНОВЛЕНА"
            : "↓   УСТАНОВИТЬ QWEN2.5 (398 МБ)");
        root.addView(strongerModel);
        if (LocalBrain.hasStrong(this)) strongerModel.setEnabled(false);
        strongerModel.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
            .setTitle("Установить Qwen2.5-0.5B?")
            .setMessage("Это открытая языковая модель, лучше приспособленная к русскому языку, чем встроенная SmolLM2.\n\n" +
                "Для загрузки понадобится около 398 МБ трафика (Wi-Fi или мобильный интернет) и более 550 МБ свободной памяти. " +
                "Это не системный ИИ: управление телефоном осуществляется отдельными Android-командами.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Скачать", (dialog, which) -> {
                strongerModel.setEnabled(false);
                new Thread(() -> {
                    String result;
                    try {
                        LocalBrain.downloadStrong(this, progress ->
                            runOnUiThread(() -> { if (!isFinishing()) state.setText(progress); }));
                        result = "Модель Qwen2.5 установлена и готова к офлайн-вопросам.";
                    } catch (Exception e) {
                        String why = e.getMessage();
                        result = "Не удалось скачать Qwen: " + (why == null
                            ? e.getClass().getSimpleName() : why);
                    }
                    final String message = result;
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            state.setText(message);
                            strongerModel.setEnabled(!LocalBrain.hasStrong(this));
                            strongInfo.setText(message);
                        }
                    });
                }, "jarvis-qwen-download").start();
            }).show());

        Switch offline = new Switch(this);
        offline.setText("Всегда офлайн — не использовать интернет для ответов");
        offline.setTextSize(12);
        offline.setTextColor(Color.WHITE);
        offline.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("offline_ai", false));
        offline.setOnCheckedChangeListener((b,on) ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("offline_ai", on).apply());
        root.addView(offline);
        Button installModel = action("◈   ПОДГОТОВИТЬ ЛОКАЛЬНЫЙ ИИ");
        root.addView(installModel);
        installModel.setOnClickListener(v -> {
            installModel.setEnabled(false);
            state.setText("Подготавливаю модель в памяти телефона...");
            new Thread(() -> {
                String result;
                try {
                    LocalBrain.install(this);
                    result = "Локальная модель готова. В автоматическом режиме она используется при отсутствии интернета/API.";
                } catch(Exception e) {
                    result = "Ошибка подготовки модели: " + e.getClass().getSimpleName();
                }
                final String message = result;
                runOnUiThread(() -> {
                    installModel.setEnabled(true);
                    state.setText(message);
                    localDetail.setText(message);
                });
            }, "jarvis-local-setup").start();
        });

        TextView info = text("Голос синтезирован ИИ и не принадлежит актёру. Прослушивание работает только после запуска видимого сервиса с уведомлением. Android может приостанавливать распознавание в фоне.", 11, 0xFFA6B9CA);
        info.setPadding(0, px(18), 0, px(15));
        root.addView(info);
    }

    private void addLine(String role, String s) {
        if (transcript == null) return;
        TextView item = text(("user".equals(role) ? "ВЫ   " : "JARVIS   ") + s, 13,
            "user".equals(role) ? 0xFFA7ECFF : Color.WHITE);
        item.setPadding(px(11), px(10), px(11), px(10));
        item.setBackground(bg("user".equals(role) ? 0xFF10384E : 0xFF10293B, 0xFF225C7A, px(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(7);
        transcript.addView(item, lp);
        if (transcript.getChildCount() > 25) transcript.removeViewAt(0);
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void startWithPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, PERMISSIONS);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationPrompted
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPrompted = true;
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, PERMISSIONS);
            return;
        }
        Intent i = new Intent(this, JarvisService.class).setAction(START);
        startForegroundService(i);
        state.setText("Активирую JARVIS...");
    }
    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == PERMISSIONS) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWithPermission();
            else state.setText("Доступ к микрофону не предоставлен.");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (overlayHint != null) overlayHint.setText(Settings.canDrawOverlays(this)
            ? "✓ Разрешение получено: плавающий JARVIS будет отображаться при запуске"
            : "Разрешите показ поверх окон — появится перетаскиваемый JARVIS");
    }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(EVENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(events, filter);
        receiverAdded = true;
    }
    @Override protected void onStop() {
        if (receiverAdded) unregisterReceiver(events);
        receiverAdded = false;
        super.onStop();
    }

    private static class Orb extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Orb(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) * .39f;
            long t = android.os.SystemClock.uptimeMillis();
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 9; i++) {
                float radius = r * (.45f + i * .067f);
                paint.setStrokeWidth(i % 3 == 0 ? 2.4f : .85f);
                paint.setColor(Color.argb(65 + (i * 15) % 130, 0, 195 + i * 6, 255));
                canvas.drawCircle(cx, cy, radius, paint);
            }
            paint.setStrokeWidth(5);
            paint.setColor(0xFF00DFFF);
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, (t / 28f) % 360f, 92, false, paint);
            paint.setColor(0xFF4B8EFF);
            paint.setStrokeWidth(3);
            canvas.drawArc(cx - r * .7f, cy - r * .7f, cx + r * .7f, cy + r * .7f, 270 - (t / 41f) % 360f, 55, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC0B385D);
            canvas.drawCircle(cx, cy, r * .45f, paint);
            paint.setColor(0xFF9BEAFF);
            paint.setTextSize(r * .19f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            canvas.drawText("J.A.R.V.I.S.", cx, cy + r * .05f, paint);
            paint.setTextSize(r * .095f);
            paint.setColor(0xFF5FD4F5);
            canvas.drawText("VOICE AI  •  ONLINE", cx, cy + r * .2f, paint);
            postInvalidateDelayed(45);
        }
    }
}
