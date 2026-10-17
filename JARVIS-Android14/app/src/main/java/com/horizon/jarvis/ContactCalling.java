package com.horizon.jarvis;

import android.content.Intent;
import android.provider.Settings;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spoken calling intent. Never initiates a phone call without explicit confirmation. */
final class ContactCalling {
    private static final Pattern COMMAND = Pattern.compile(
        "^(?:(?:джарвис|жарвис|jarvis)[\\s,!.:]+)?" +
        "(?:позвони|позвонить|звони|набери|набрать|набер[иь]|call|dial|phone|зателефонуй)" +
        "\\s+(?:(?:контакту|контакт|contact|to)\\s+)?" +
        "(?:по номеру\\s+)?(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ContactCalling() {}

    static String requestedContact(String question) {
        if (question == null || question.length()>200) return null;
        String q = question.trim().replace('ё','е');
        Matcher match = COMMAND.matcher(q);
        if (!match.matches()) return null;
        String target = match.group(1).trim();
        target = target.replaceFirst("[\\s,.!?]+$", "")
            .replaceFirst("(?i)\\s+(?:пожалуйста|please)$", "").trim();
        if (target.isEmpty() || target.length()>70) return "";
        // Voice command to call should never accidentally match sensitive control text.
        return target;
    }

    static String open(JarvisService service, JarvisOverlay overlay, String contact) {
        if (contact == null || contact.trim().isEmpty()) return "Скажите имя контакта, которому нужно позвонить.";
        Intent confirm = new Intent(service, CallContactActivity.class);
        confirm.putExtra(CallContactActivity.EXTRA_TARGET, contact);
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Settings.canDrawOverlays(service)) {
            try {
                service.startActivity(confirm);
                return "Открываю контакты для звонка: " + contact + ". Подтвердите номер на экране.";
            } catch (RuntimeException ignored) { /* fall back to visible button */ }
            if (overlay != null) {
                overlay.offerLaunch(confirm, "звонок: " + contact);
                return "Нажмите «Открыть» в панели JARVIS, чтобы выбрать номер и подтвердить звонок.";
            }
        }
        // When Android blocks background activity launch, ask the user to return to JARVIS.
        // A safe ACTION_DIAL or direct call is NEVER attempted silently from the service.
        return "Для звонка разрешите JARVIS плавающее окно и повторите команду. Выбор номера и звонок подтверждаются вручную.";
    }

    /** Help with common Russian speech-recognition inflections, without uploading names anywhere. */
    static String normalizedName(String input) {
        if (input == null) return "";
        String s=input.toLowerCase(Locale.ROOT).replace('ё','е')
            .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
        switch(s) {
            case "маме": case "маму": case "мамой": return "мама";
            case "папе": case "папу": return "папа";
            case "бабушке": case "бабушку": return "бабушка";
            case "дедушке": case "дедушку": return "дедушка";
            case "сестре": case "сестру": return "сестра";
            case "брату": case "брата": return "брат";
            case "другу": case "друга": return "друг";
            default: return s;
        }
    }
}
