package com.horizon.jarvis;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class JarvisBrain {
    private JarvisBrain() {}
    private static final String KEY_ALIAS = "jarvis_key_v2";

    static boolean hasKey(Context ctx) {
        return !ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getString("encrypted_api_key", "").isEmpty();
    }

    static void saveKey(Context ctx, String apiKey) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, (java.security.Key) store.getKey(KEY_ALIAS, null));
        byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
            .putString("encrypted_api_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("encrypted_api_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .apply();
    }

    static String getKey(Context ctx) {
        try {
            String enc = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString("encrypted_api_key", "");
            String iv = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString("encrypted_api_iv", "");
            if (enc.isEmpty() || iv.isEmpty()) return "";
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, (java.security.Key) store.getKey(KEY_ALIAS, null),
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    static String respond(String key, List<JSONObject> history, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", "gpt-4.1-mini");
        body.put("store", false);
        body.put("instructions",
            "You are JARVIS, a helpful multilingual Android voice assistant. " +
            "Reply naturally in the SAME LANGUAGE as the user's most recent message, including when accented, " +
            "dialectal, transliterated or mixed-language, unless the user asks for a different language. " +
            "Preserve names accurately. Be concise: usually 1-3 sentences. " +
            "Retain conversational context, admit uncertainty, and never claim that a phone action was performed " +
            "unless the Android app explicitly confirmed it. Ask before consequential actions. " +
            "The app adds its preferred honorific after your response, so don't duplicate it.");
        JSONArray input = new JSONArray();
        for (JSONObject item : history) input.put(item);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", prompt);
        input.put(user);
        body.put("input", input);
        JSONObject response = new JSONObject(new String(postJson("https://api.openai.com/v1/responses",
            key, body, 45000, 450000), StandardCharsets.UTF_8));
        JSONArray messages = response.optJSONArray("output");
        StringBuilder answer = new StringBuilder();
        if (messages != null) for (int i = 0; i < messages.length(); i++) {
            JSONObject output = messages.optJSONObject(i);
            if (output == null) continue;
            JSONArray parts = output.optJSONArray("content");
            if (parts == null) continue;
            for (int j = 0; j < parts.length(); j++) {
                JSONObject part = parts.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type")))
                    answer.append(part.optString("text")).append(' ');
            }
        }
        String text = answer.toString().trim();
        return text.isEmpty() ? "Ответ от модели пока не получен." : text.substring(0, Math.min(2000, text.length()));
    }

    static File createSpeech(Context ctx, String key, String phrase) throws Exception {
        String input = phrase.length() > 1300 ? phrase.substring(0, 1300) : phrase;
        JSONObject data = new JSONObject();
        data.put("model", "gpt-4o-mini-tts");
        data.put("voice", "onyx");
        data.put("input", input);
        data.put("response_format", "mp3");
        data.put("instructions",
            "Speak in fluent Russian with a composed, low, warm, precise original AI-assistant voice. " +
            "Calm delivery, measured rhythm, lightly British-inspired intonation without imitating any individual.");
        byte[] mp3 = postJson("https://api.openai.com/v1/audio/speech", key, data, 45000, 5000000);
        File file = new File(ctx.getCacheDir(), "jarvis_current_reply.mp3");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(mp3); }
        return file;
    }

    private static byte[] postJson(String endpoint, String key, JSONObject data, int timeoutMs, int limitBytes) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] req = data.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = conn.getOutputStream()) { output.write(req); }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) throw new IOException("API HTTP " + code);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = stream) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = input.read(buf)) != -1) {
                    if (bytes.size() + n > limitBytes) throw new IOException("API response size limit");
                    bytes.write(buf, 0, n);
                }
            }
            if (code < 200 || code >= 300) {
                String message = "HTTP " + code;
                try {
                    JSONObject error = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).optJSONObject("error");
                    if (error != null) message = error.optString("message", message);
                } catch (JSONException ignored) {}
                throw new IOException("OpenAI: " + message);
            }
            return bytes.toByteArray();
        } finally { conn.disconnect(); }
    }

    static JSONObject turn(String role, String text) {
        JSONObject object = new JSONObject();
        try { object.put("role", role); object.put("content", text); }
        catch (JSONException ignored) {}
        return object;
    }
}
