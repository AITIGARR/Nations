package com.horizon.jarvis;

import android.content.Context;
import android.os.StatFs;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** Offline llama.cpp runner. Built-in SmolLM2; optional better Qwen2.5 0.5B (Apache 2.0). */
final class LocalBrain {
    private static final String BUILTIN = "SmolLM2-135M-Instruct-Q4_K_M.gguf";
    private static final String STRONG = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf";
    private static final String STRONG_URL =
        "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" +
        "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf?download=true";
    private static final long MIN_BUILTIN = 95_000_000L;
    private static final long MIN_STRONG = 350_000_000L;
    private static final long MAX_STRONG = 455_000_000L;
    private static boolean tried, loaded;

    interface Progress { void update(String message); }

    private LocalBrain() {}

    static File file(Context ctx) { return new File(ctx.getFilesDir(), BUILTIN); }
    static File strongFile(Context ctx) { return new File(ctx.getFilesDir(), STRONG); }

    static boolean hasStrong(Context ctx) {
        long length = strongFile(ctx).length();
        return length >= MIN_STRONG && length <= MAX_STRONG;
    }

    static boolean ready(Context ctx) {
        return hasStrong(ctx) || file(ctx).length() > MIN_BUILTIN;
    }

    static synchronized void install(Context ctx) throws IOException {
        if (ready(ctx)) return;
        File output = file(ctx);
        File partial = new File(ctx.getFilesDir(), BUILTIN + ".partial");
        byte[] b = new byte[65536];
        try (InputStream in = ctx.getAssets().open(BUILTIN);
             OutputStream out = new FileOutputStream(partial)) {
            int count;
            while ((count = in.read(b)) != -1) out.write(b, 0, count);
        } catch (IOException e) {
            partial.delete();
            throw e;
        }
        if (partial.length() < MIN_BUILTIN) {
            partial.delete();
            throw new IOException("Built-in model is incomplete");
        }
        if (output.exists() && !output.delete()) throw new IOException("Cannot replace built-in model");
        if (!partial.renameTo(output)) throw new IOException("Cannot move built-in model");
    }

    /** User-initiated download of 398MB GGUF. Never run on Android main thread. */
    static synchronized void downloadStrong(Context ctx, Progress progress) throws IOException {
        if (hasStrong(ctx)) {
            progress.update("Улучшенная модель уже установлена.");
            return;
        }
        File dest = strongFile(ctx);
        File partial = new File(ctx.getFilesDir(), STRONG + ".partial");
        StatFs storage = new StatFs(ctx.getFilesDir().getAbsolutePath());
        if (storage.getAvailableBytes() < 550_000_000L) {
            throw new IOException("Недостаточно памяти: нужно около 550 МБ свободного места.");
        }

        HttpURLConnection connection = null;
        long total = 0;
        try {
            connection = (HttpURLConnection) new URL(STRONG_URL).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "JarvisAI-Android/0.5");
            int response = connection.getResponseCode();
            if (response != 200) throw new IOException("Ошибка загрузки: HTTP " + response);
            long expected = connection.getContentLengthLong();
            if (expected > MAX_STRONG) throw new IOException("Файл модели больше допустимого размера");

            byte[] buffer = new byte[65536];
            long nextMessageAt = 5_000_000L;
            progress.update("Загрузка Qwen2.5 (около 398 МБ)...");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(partial))) {
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Загрузка отменена");
                    total += n;
                    if (total > MAX_STRONG) throw new IOException("Слишком большой файл");
                    output.write(buffer, 0, n);
                    if (total >= nextMessageAt) {
                        progress.update("Загружено " + total / 1_000_000 + " МБ из примерно 398 МБ");
                        nextMessageAt = total + 5_000_000L;
                    }
                }
            }
            if (total < MIN_STRONG) throw new IOException("Модель скачана не полностью");
            try (InputStream input = new FileInputStream(partial)) {
                byte[] magic = new byte[4];
                if (input.read(magic) != 4 || magic[0] != 'G' || magic[1] != 'G'
                    || magic[2] != 'U' || magic[3] != 'F')
                    throw new IOException("Загруженный файл не является GGUF-моделью");
            }
            if (dest.exists() && !dest.delete()) throw new IOException("Cannot replace model");
            if (!partial.renameTo(dest)) throw new IOException("Не удалось сохранить GGUF");
            progress.update("Qwen2.5 установлена! Следующие офлайн-ответы будут использовать её.");
        } catch (IOException e) {
            partial.delete();
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static synchronized void load() {
        if (tried) return;
        tried = true;
        try {
            System.loadLibrary("jarvis_local");
            loaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            loaded = false;
        }
    }

    static String answer(Context ctx, String prompt) {
        if (!ready(ctx))
            return "Офлайн-модель пока не подготовлена. Нажмите «Подготовить локальный ИИ» в настройках.";
        load();
        if (!loaded)
            return "Локальный движок не поддерживается устройством. При возможности используйте облачную модель.";
        try {
            String question = prompt.length() > 750 ? prompt.substring(0, 750) : prompt;
            String path = hasStrong(ctx) ? strongFile(ctx).getAbsolutePath() : file(ctx).getAbsolutePath();
            String result = generate(path, question);
            return result == null || result.trim().isEmpty()
                ? "Локальная модель не смогла ответить." : result.trim();
        } catch (Exception e) {
            return "Ошибка локального ИИ: " + e.getClass().getSimpleName();
        }
    }

    private static native String generate(String modelPath, String prompt);
}
