package com.horizon.jarvis;

import java.util.Locale;

/**
 * Recognition preferences, accent/dialect presets and multilingual wake aliases.
 * The recognizer's actual coverage depends on the device's speech provider.
 */
final class MultilingualSpeech {
    static final String PREF_LANGUAGE = "recognition_language_v9";
    static final String AUTO = "auto";
    static final String[] LANGUAGE_LABELS = {
        "🌐 Автоматически — все доступные языки",
        "🇷🇺 Русский", "🇺🇸 English (US)", "🇬🇧 English (UK)", "🇮🇳 English (India)",
        "🇩🇪 Deutsch", "🇫🇷 Français", "🇪🇸 Español (España)", "🇲🇽 Español (México)",
        "🇧🇷 Português (Brasil)", "🇵🇹 Português (Portugal)", "🇮🇹 Italiano",
        "🇹🇷 Türkçe", "🇸🇦 العربية", "🇪🇬 العربية (مصر)", "🇮🇳 हिन्दी",
        "🇵🇰 اردو", "🇨🇳 中文 (简体)", "🇹🇼 中文 (繁體)", "🇯🇵 日本語",
        "🇰🇷 한국어", "🇺🇦 Українська", "🇵🇱 Polski", "🇳🇱 Nederlands",
        "🇸🇪 Svenska", "🇮🇩 Bahasa Indonesia", "🇻🇳 Tiếng Việt",
        "🇹🇭 ไทย", "🇮🇷 فارسی", "🇧🇩 বাংলা", "🇮🇳 தமிழ்",
        "🇰🇿 Қазақша", "🇺🇿 Oʻzbekcha"
    };
    static final String[] LANGUAGE_TAGS = {
        AUTO, "ru-RU", "en-US", "en-GB", "en-IN", "de-DE", "fr-FR", "es-ES",
        "es-MX", "pt-BR", "pt-PT", "it-IT", "tr-TR", "ar-SA", "ar-EG",
        "hi-IN", "ur-PK", "zh-CN", "zh-TW", "ja-JP", "ko-KR", "uk-UA",
        "pl-PL", "nl-NL", "sv-SE", "id-ID", "vi-VN", "th-TH", "fa-IR",
        "bn-BD", "ta-IN", "kk-KZ", "uz-UZ"
    };

    private static final String[] WAKE = {
        "джарвис", "джервис", "жарвис", "джарвэс",
        "jarvis", "jarves", "جارفيس", "جارڤيس", "जार्विस",
        "贾维斯", "賈維斯", "ジャービス", "ジャーヴィス", "자비스"
    };
    private MultilingualSpeech() {}

    static String validated(String requested) {
        if (requested == null) return AUTO;
        for (String value : LANGUAGE_TAGS)
            if (value.equalsIgnoreCase(requested)) return value;
        return AUTO;
    }

    static int optionIndex(String tag) {
        for (int i=0;i<LANGUAGE_TAGS.length;i++)
            if (LANGUAGE_TAGS[i].equalsIgnoreCase(tag)) return i;
        return 0;
    }

    static String wakeCommand(String phrase) {
        if (phrase == null) return null;
        String lower = phrase.toLowerCase(Locale.ROOT).trim();
        int best = -1, size = 0;
        for (String alias : WAKE) {
            int at = lower.indexOf(alias);
            if (at < 0 || at > 16) continue;
            int end = at + alias.length();
            boolean ideographic = alias.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}].*");
            boolean boundaryL = at == 0 || !Character.isLetterOrDigit(lower.charAt(at-1));
            boolean boundaryR = end == lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
            if (!ideographic && !(boundaryL && boundaryR)) continue;
            if (best < 0 || at < best) {best=at;size=alias.length();}
        }
        if (best < 0) return null;
        return phrase.trim().substring(best + size).replaceFirst("^[\\s,.:!?;،，。—-]+", "").trim();
    }

    static Locale voiceLocale(String selectedLanguage, String lastDetected) {
        String tag = validated(selectedLanguage);
        if (AUTO.equals(tag)) tag = (lastDetected == null || lastDetected.isEmpty()) ? "ru-RU" : lastDetected;
        Locale result = Locale.forLanguageTag(tag);
        return result.getLanguage().isEmpty() ? Locale.forLanguageTag("ru-RU") : result;
    }

    static String shortLabel(String tag) {
        if (tag == null || tag.trim().isEmpty()) return "не определён";
        return Locale.forLanguageTag(tag).getDisplayLanguage(new Locale("ru", "RU"));
    }
}
