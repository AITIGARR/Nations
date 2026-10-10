package com.horizon.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.MediaStore;
import android.provider.Settings;
import java.text.Normalizer;
import java.util.*;

/** Execute bounded, explicitly requested Android actions. Never invent a completion. */
final class JarvisActions {
    private JarvisActions() {}

    static String handle(JarvisService service, JarvisOverlay overlay, String spoken) {
        String q = normalize(spoken);
        String action = null;
        if (q.equals("назад") || q.contains("вернись назад") || q.contains("шаг назад")) action = "back";
        else if (q.equals("домой") || q.contains("на главный экран") || q.contains("на рабочий стол")) action = "home";
        else if (q.contains("последние приложения") || q.contains("недавние приложения")) action = "recents";
        else if (q.contains("шторку уведомлений") || q.contains("покажи уведомления") || q.contains("открой уведомления")) action = "notifications";
        else if (q.contains("быстрые настройки") || q.contains("панель быстрых")) action = "quick_settings";
        else if (q.contains("листай вниз") || q.contains("прокрути вниз") || q.contains("скролл вниз")) action = "scroll_down";
        else if (q.contains("листай вверх") || q.contains("прокрути вверх") || q.contains("скролл вверх")) action = "scroll_up";
        else if (q.startsWith("нажми на ") || q.startsWith("нажми ") || q.startsWith("кликни на ")) {
            String label = q.replaceFirst("^(нажми на |нажми |кликни на )", "").trim();
            if (label.isEmpty()) return "Назовите текст кнопки, на которую нужно нажать.";
            if (!JarvisAccessibilityService.isConnected())
                return "Включите JARVIS в специальных возможностях телефона, чтобы нажимать кнопки.";
            boolean clicked = JarvisAccessibilityService.execute("tap_text", label);
            return clicked ? "Нажал на элемент «" + label + "»." :
                "Не нашёл доступную кнопку «" + label + "» или действие требует подтверждения.";
        }

        if (action != null) {
            if (!JarvisAccessibilityService.isConnected()) {
                return "Для управления экраном включите сервис JARVIS в разделе «Специальные возможности» телефона.";
            }
            boolean done = JarvisAccessibilityService.execute(action, "");
            return done ? ("Выполнено: " + description(action)) :
                "Android не разрешил это действие на текущем экране.";
        }

        if (q.matches(".*\\b(открой|запусти|открыть|покажи приложение|включи приложение|зайди в)\\b.*")) {
            String target = q.replaceFirst("^.*?\\b(открой|запусти|открыть|покажи приложение|включи приложение|зайди в)\\b\\s*", "").trim();
            if (target.isEmpty()) return "Назовите приложение, которое нужно открыть.";
            return launch(service, overlay, target);
        }
        return null;
    }

    private static String description(String action) {
        switch (action) {
            case "back": return "назад";
            case "home": return "главный экран";
            case "recents": return "недавние приложения";
            case "notifications": return "панель уведомлений";
            case "quick_settings": return "быстрые настройки";
            case "scroll_down": return "прокрутка вниз";
            case "scroll_up": return "прокрутка вверх";
            default: return "команда";
        }
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private static String launch(JarvisService ctx, JarvisOverlay overlay, String target) {
        Intent intent = null;
        String name = target;
        String pkg = null;
        if (has(target, "ютуб","youtube")) { pkg = "com.google.android.youtube"; name = "YouTube"; }
        else if (has(target, "телеграм","telegram")) { pkg = "org.telegram.messenger"; name = "Telegram"; }
        else if (has(target, "майнкрафт","minecraft")) { pkg = "com.mojang.minecraftpe"; name = "Minecraft"; }
        else if (has(target, "хром","chrome")) { pkg = "com.android.chrome"; name = "Chrome"; }
        else if (has(target, "ватсап","whatsapp","вотсап")) { pkg = "com.whatsapp"; name = "WhatsApp"; }
        else if (has(target, "дискорд","discord")) { pkg = "com.discord"; name = "Discord"; }
        else if (has(target, "спотифай","spotify")) { pkg = "com.spotify.music"; name = "Spotify"; }
        else if (has(target, "вконтакте","вк приложение")) { pkg = "com.vkontakte.android"; name = "ВКонтакте"; }
        else if (has(target, "карты","maps")) { pkg = "com.google.android.apps.maps"; name = "Карты"; }
        else if (has(target, "почта","gmail")) { pkg = "com.google.android.gm"; name = "Gmail"; }
        else if (has(target, "настройки","настройку")) {
            intent = new Intent(Settings.ACTION_SETTINGS);
            name = "Настройки";
        } else if (has(target, "камера","камеру")) {
            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            name = "Камера";
        } else if (has(target, "браузер","интернет","сайт")) {
            intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"));
            name = "Браузер";
        }

        PackageManager pm = ctx.getPackageManager();
        if (intent == null && pkg != null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null && pkg == null) {
            Intent filter = new Intent(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> installed = pm.queryIntentActivities(filter, 0);
            String requested = normalize(target).replace(" приложение", "").replace(" пожалуйста", "").trim();
            // Exact package labels first, fuzzy labels second (minimum 4 characters).
            ResolveInfo match = null;
            for (ResolveInfo info : installed) {
                String label = normalize(String.valueOf(info.loadLabel(pm)));
                if (label.equals(requested)) { match = info; break; }
                if (match == null && requested.length() >= 4 && (label.startsWith(requested) || label.contains(requested)))
                    match = info;
            }
            if (match != null && match.activityInfo != null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setClassName(match.activityInfo.packageName, match.activityInfo.name);
                name = String.valueOf(match.loadLabel(pm));
            }
        }
        if (intent == null) return "Не нашёл приложение «" + target + "». Проверьте, установлено ли оно.";
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            // On Android 14 a granted SYSTEM_ALERT_WINDOW permission is a documented exception
            // to the background activity launch restrictions.
            if (Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(intent);
                return "Открываю " + name + ".";
            }
        } catch (Exception ignored) {
            // OEM restrictions may still prevent an automatic background launch.
        }
        // No invisible notification! Always display a visible in-app/overlay action.
        if (overlay != null && Settings.canDrawOverlays(ctx)) {
            overlay.offerLaunch(intent, name);
            return "Нажмите «Открыть» в панели JARVIS для запуска " + name + ".";
        }
        return "Для открытия " + name + " из фона разрешите JARVIS показ поверх других приложений в его настройках.";
    }

    private static boolean has(String s, String... parts) {
        for (String p : parts) if (s.contains(p)) return true;
        return false;
    }
}
