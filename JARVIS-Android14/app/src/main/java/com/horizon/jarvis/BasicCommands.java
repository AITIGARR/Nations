package com.horizon.jarvis;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Device facts and simple commands are deterministic and never call a language model. */
final class BasicCommands {
    enum Kind { NONE, TIME, DATE, BATTERY, GREETING, IDENTITY, HELP, THANKS }
    private BasicCommands() {}

    static Kind classify(String phrase) {
        if (phrase == null) return Kind.NONE;
        String q = phrase.toLowerCase(Locale.ROOT).replace('ё', 'е')
            .replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        if (q.startsWith("джарвис ")) q = q.substring(8).trim();
        else if (q.equals("джарвис")) return Kind.GREETING;
        if (q.contains("сколько времени") || q.contains("сколько время")
            || q.contains("который час") || q.contains("сколько сейчас времени")
            || q.contains("какое сейчас время") || q.contains("сколько щас время")
            || q.contains("скажи время") || q.contains("назови время")
            || q.contains("что по времени") || q.contains("время сейчас")
            || q.equals("время") || q.equals("час")) return Kind.TIME;
        if (q.contains("какое сегодня число") || q.contains("какая сегодня дата")
            || q.contains("какой сегодня день") || q.contains("какой день недели")
            || q.contains("сегодняшняя дата") || q.contains("число сегодня")
            || q.contains("скажи дату") || q.equals("дата")) return Kind.DATE;
        if (q.contains("заряд") || q.contains("батаре") || q.contains("аккумулятор")
            || q.contains("сколько процентов на телефоне")) return Kind.BATTERY;
        if (q.contains("что ты умеешь") || q.contains("твои возможности")
            || q.contains("как мне тобой пользоваться") || q.equals("помощь")) return Kind.HELP;
        if (q.contains("кто ты") || q.contains("как тебя зовут")) return Kind.IDENTITY;
        if (q.equals("привет") || q.equals("здравствуй") || q.equals("ты тут")
            || q.equals("добрый день") || q.equals("доброе утро")) return Kind.GREETING;
        if (q.startsWith("спасибо") || q.equals("благодарю")) return Kind.THANKS;
        return Kind.NONE;
    }

    static String answer(Kind kind, Date now, int batteryPercent) {
        Locale ru = new Locale("ru", "RU");
        switch(kind) {
            case TIME: return "Сейчас " + new SimpleDateFormat("HH:mm", ru).format(now) + ".";
            case DATE: return "Сегодня " + new SimpleDateFormat("d MMMM yyyy", ru).format(now)
                + ", " + new SimpleDateFormat("EEEE", ru).format(now) + ".";
            case BATTERY: return batteryPercent >= 0 && batteryPercent <= 100
                ? "Заряд батареи " + batteryPercent + " процентов."
                : "Не удалось определить заряд батареи.";
            case GREETING: return "Я на связи. Чем могу помочь?";
            case IDENTITY: return "Я Джарвис, ваш голосовой помощник для Android.";
            case HELP: return "Я могу назвать время, дату и заряд батареи, открывать приложения, "
                + "отвечать на вопросы через локальный или облачный ИИ. "
                + "После включения специальных возможностей также могу листать экран и выполнять команды назад и домой.";
            case THANKS: return "Всегда к вашим услугам.";
            default: return "";
        }
    }
}
