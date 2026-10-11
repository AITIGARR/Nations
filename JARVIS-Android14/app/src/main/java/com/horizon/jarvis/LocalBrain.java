package com.horizon.jarvis;

import android.content.Context;
import android.os.StatFs;
import java.io.*;

/** Built-in Qwen2.5 0.5B Q4_K_M GGUF, powered by llama.cpp (offline, no API key). */
final class LocalBrain {
    private static final String QWEN = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf";
    private static final long MIN_SIZE = 350_000_000L;
    private static final long MAX_SIZE = 455_000_000L;
    private static boolean tried, loaded;
    interface Progress { void update(String message); }
    private LocalBrain() {}

    static File file(Context ctx) {return new File(ctx.getFilesDir(),QWEN);}
    static File strongFile(Context ctx) {return file(ctx);}
    static boolean hasStrong(Context ctx) {
        long n=file(ctx).length(); return n >= MIN_SIZE && n <= MAX_SIZE;
    }
    static boolean ready(Context ctx) {return hasStrong(ctx);}

    static synchronized void install(Context ctx) throws IOException {
        if (hasStrong(ctx)) return;
        File folder=ctx.getFilesDir(), dest=file(ctx);
        StatFs storage = new StatFs(folder.getAbsolutePath());
        if (storage.getAvailableBytes() < 470_000_000L)
            throw new IOException("Для Qwen нужно около 470 МБ свободного места после установки APK.");
        File staging = new File(folder,QWEN+".partial");
        long total=0;
        byte[] bytes=new byte[131072];
        try (InputStream input=new BufferedInputStream(ctx.getAssets().open(QWEN));
             OutputStream output=new BufferedOutputStream(new FileOutputStream(staging))) {
            int n;
            while ((n=input.read(bytes))!=-1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Подготовка отменена.");
                output.write(bytes,0,n);total+=n;
                if (total>MAX_SIZE) throw new IOException("Некорректный размер модели");
            }
        } catch (IOException e) {staging.delete();throw e;}
        if (total < MIN_SIZE || total > MAX_SIZE) {staging.delete();throw new IOException("Модель Qwen неполная.");}
        try (InputStream check=new FileInputStream(staging)) {
            byte[] magic=new byte[4];
            if (check.read(magic)!=4 || magic[0]!='G' || magic[1]!='G' || magic[2]!='U' || magic[3]!='F') {
                staging.delete();throw new IOException("Неверный формат модели.");
            }
        }
        if (dest.exists() && !dest.delete()) throw new IOException("Не могу заменить файл GGUF.");
        if (!staging.renameTo(dest)) throw new IOException("Не удалось разместить модель на устройстве.");

        // Old prototype model is no longer used: recover private storage on upgrades.
        File old=new File(folder,"SmolLM2-135M-Instruct-Q4_K_M.gguf");
        if (old.exists()) old.delete();
    }

    /** Kept for compatibility with the previous settings page; never uses network. */
    static void downloadStrong(Context ctx, Progress progress) throws IOException {
        progress.update("Подготавливаю встроенный Qwen2.5 на телефоне...");
        install(ctx);
        progress.update("Qwen2.5 готова. Интернет не нужен.");
    }

    private static synchronized void load() {
        if(tried)return;
        tried=true;
        try {System.loadLibrary("jarvis_local");loaded=true;}
        catch(UnsatisfiedLinkError ignored) {loaded=false;}
    }

    static String answer(Context ctx,String question) {
        if (!ready(ctx)) return "Подготавливаю встроенную Qwen2.5. При первой подготовке это может занять немного времени.";
        load();
        if(!loaded) return "Не удалось загрузить локальный движок ИИ на этом устройстве.";
        try {
            String prompt=question.length()>600 ? question.substring(0,600):question;
            String result=generate(file(ctx).getAbsolutePath(),prompt);
            return result==null || result.trim().isEmpty()
                ? "Локальная модель не смогла сформулировать ответ." : result.trim();
        } catch (Throwable e) {
            return "Ошибка Qwen2.5: "+e.getClass().getSimpleName();
        }
    }
    private static native String generate(String modelPath,String prompt);
}
