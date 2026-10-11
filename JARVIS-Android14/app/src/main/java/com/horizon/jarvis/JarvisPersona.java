package com.horizon.jarvis;

import java.util.Locale;

/** Concise, idempotent JARVIS honorific for visible and spoken answers. */
final class JarvisPersona {
    private JarvisPersona() {}

    static String withSir(String source) {
        if (source == null || source.trim().isEmpty()) return "";
        String value = source.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*\\bсэр[.!?…]*$")) return value;
        int last = value.length() - 1;
        char punctuation = value.charAt(last);
        String base = (punctuation == '.' || punctuation == '!' || punctuation == '?' ||
            punctuation == '…') ? value.substring(0, last).trim() : value;
        if (base.isEmpty()) return "Сэр.";
        if (base.endsWith(",") || base.endsWith(":")) base = base.substring(0, base.length() - 1).trim();
        return base + ", сэр" + (punctuation == '?' ? "?" : ".");
    }
}
