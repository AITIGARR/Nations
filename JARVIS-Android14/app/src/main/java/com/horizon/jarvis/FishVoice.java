package com.horizon.jarvis;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

/** Fish Audio text-to-speech; optional and user-configured; never embeds API keys. */
final class FishVoice {
    static final String DEFAULT_VOICE_ID = "f22f684f44d74c4a86d72d95c296ba26";
    static final String SOURCE_VOICE_URL =
        "https://fish.audio/m/" + DEFAULT_VOICE_ID + "/";
    private static final String KEY_ALIAS = "jarvis_fish_audio_api_v1";
    private static final String KEY_FIELD = "fish_api_ciphertext";
    private static final String IV_FIELD = "fish_api_iv";
    private static final long MAX_MP3 = 5_000_000L;

    private FishVoice() {}

    static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getBoolean("fish_voice_enabled", false);
    }

    static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("fish_voice_enabled", enabled).apply();
    }

    static String voiceId(Context ctx) {
        String id = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getString("fish_voice_id", DEFAULT_VOICE_ID);
        return isValidVoiceId(id) ? id : DEFAULT_VOICE_ID;
    }

    static boolean isValidVoiceId(String id) {
        return id != null && id.matches("[a-fA-F0-9]{32}");
    }

    static void saveVoiceId(Context ctx, String value) {
        if (!isValidVoiceId(value)) throw new IllegalArgumentException("Неверный ID Fish Audio");
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
            .putString("fish_voice_id", value.toLowerCase(java.util.Locale.ROOT)).apply();
    }

    static void saveKey(Context ctx, String key) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Fish API key empty");
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, (java.security.Key)store.getKey(KEY_ALIAS, null));
        byte[] bytes = cipher.doFinal(key.trim().getBytes(StandardCharsets.UTF_8));
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FIELD, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putString(IV_FIELD, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).apply();
    }

    static String getKey(Context ctx) {
        try {
            String encrypted = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FIELD, "");
            String iv = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(IV_FIELD, "");
            if (encrypted.isEmpty() || iv.isEmpty()) return "";
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, (java.security.Key)store.getKey(KEY_ALIAS, null),
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    static File synthesize(Context ctx, String text) throws Exception {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("Text is empty");
        String key = getKey(ctx);
        if (key.isEmpty()) throw new IllegalStateException("Fish Audio key is not set");
        JSONObject body = new JSONObject();
        body.put("text", text.substring(0, Math.min(1400, text.length())));
        body.put("reference_id", voiceId(ctx));
        body.put("format", "mp3");

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.fish.audio/v1/tts").openConnection();
        try {
            c.setConnectTimeout(12_000);
            c.setReadTimeout(50_000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "audio/mpeg");
            c.setRequestProperty("model", "s2.1-pro");
            byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(request); }

            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                // Avoid logging tokens, request body or full errors from the provider.
                throw new IOException("Fish Audio HTTP " + status);
            }
            String type = c.getContentType();
            if (type != null && (type.startsWith("application/json") || type.startsWith("text/")))
                throw new IOException("Fish Audio response is not audio");
            File path = new File(ctx.getCacheDir(), "jarvis_fish_last_voice.mp3");
            long total = 0L;
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 OutputStream out = new FileOutputStream(path)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_MP3) throw new IOException("Fish Audio file size limit");
                    out.write(buffer, 0, n);
                }
            }
            if (total < 512) {
                path.delete();
                throw new IOException("Fish Audio response is too small");
            }
            return path;
        } finally {
            c.disconnect();
        }
    }
}
