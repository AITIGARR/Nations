package com.horizon.jarvis;

import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Settings;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Original Android skills, inspired by the modular skills found in Dicio and OpenVoiceOS.
 * No code copied from third-party assistant repositories.
 */
final class JarvisSkills {
    private JarvisSkills() {}
    static String handle(JarvisService ctx, JarvisOverlay overlay, String query) {
        if (query == null || query.length() > 500) return null;
        String q=query.toLowerCase(Locale.ROOT).replace('ё','е').trim();
        Matcher timer = Pattern.compile("(?:(?:поставь|установи|запусти|включи)\\s+)?(?:таймер|timer|set (?:a )?timer|start (?:a )?timer)\\s*(?:на|for)?\\s*(\\d{1,4})\\s*(секунд[ыу]?|сек|seconds?|sec|минут[ыу]?|мин|min(?:utes?)?|час[аов]?|hours?|hrs?)").matcher(q);
        if (timer.find()) {
            int amount;
            try { amount=Integer.parseInt(timer.group(1)); }
            catch (NumberFormatException e) {return "Не удалось понять продолжительность таймера.";}
            String unit=timer.group(2);
            int factor=(unit.startsWith("час")||unit.startsWith("hour")||unit.startsWith("hr")) ? 3600
                : (unit.startsWith("мин")||unit.startsWith("min")) ? 60 : 1;
            int seconds=amount*factor;
            if (seconds < 1 || seconds > 36000) return "Таймер может длиться от секунды до десяти часов.";
            Intent intent=new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            return open(ctx, overlay, intent, "таймер " + amount + " " + unit);
        }

        String search=stripPrefix(query,
            "найди в интернете ","поищи в интернете ","поиск в интернете ",
            "search for ","search the web for ","google ","recherche ","cherche sur internet ",
            "busca en internet ","buscar en internet ","procura na internet ");
        if (search != null) {
            if (search.length()<2) return "Уточните, что искать в интернете.";
            Intent intent=new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q="+Uri.encode(search)));
            return open(ctx,overlay,intent,"поиск в интернете");
        }
        String place=stripPrefix(query,
            "маршрут до ","проложи маршрут до ","навигация до ",
            "navigate to ","directions to ","go to ","route to ",
            "navigiere zu ","itinéraire vers ","ruta a ","rota para ");
        if (place != null) {
            if (place.length()<2) return "Назовите место назначения.";
            Intent maps = new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q="+Uri.encode(place)));
            if (maps.resolveActivity(ctx.getPackageManager()) == null)
                maps = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query="+Uri.encode(place)));
            return open(ctx,overlay,maps,"маршрут: " + place);
        }
        return null;
    }

    private static String stripPrefix(String phrase,String... prefixes) {
        String normalized=phrase.toLowerCase(Locale.ROOT).trim();
        for (String prefix: prefixes)
            if (normalized.startsWith(prefix)) return phrase.trim().substring(prefix.length()).trim();
        return null;
    }

    private static String open(JarvisService ctx, JarvisOverlay overlay, Intent target,String label) {
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(target);
                return "Открываю " + label + ".";
            } catch (Exception ignored) { /* fallback */ }
            if (overlay != null) {
                overlay.offerLaunch(target, label);
                return "Нажмите «Открыть» в плавающем окне для действия: " + label + ".";
            }
        }
        return "Разрешите JARVIS показ поверх приложений, чтобы выполнить: " + label + ".";
    }
}
