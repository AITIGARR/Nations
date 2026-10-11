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
        String latin = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        if (latin.contains("what time is it") || latin.contains("tell me the time")
            || latin.equals("time") || latin.contains("current time")
            || latin.contains("que hora es") || latin.contains("dime la hora")
            || latin.contains("quelle heure") || latin.contains("wie spat ist es")
            || latin.contains("wie viel uhr") || latin.contains("que horas sao")
            || latin.contains("che ore sono") || latin.contains("saat kac")
            || q.contains("كم الساعة") || q.contains("几点") || q.contains("何時"))
            return Kind.TIME;
        if (latin.contains("what date is it") || latin.contains("what is the date")
            || latin.contains("today s date") || latin.contains("what day is today")
            || latin.contains("que fecha es") || latin.contains("que dia es hoy")
            || latin.contains("quelle date") || latin.contains("quel jour sommes")
            || latin.contains("welches datum") || latin.contains("heutiges datum")
            || latin.contains("qual e a data") || latin.contains("che giorno e oggi")
            || q.contains("几号") || q.contains("今天日期") || q.contains("今日の日付")
            || q.contains("ما تاريخ اليوم")) return Kind.DATE;
        if (latin.contains("battery level") || latin.contains("battery percentage")
            || latin.contains("how much battery") || latin.contains("nivel de bateria")
            || latin.contains("bateria restante") || latin.contains("niveau de batterie")
            || latin.contains("akkustand") || latin.contains("batteriestand")
            || q.contains("电量") || q.contains("バッテリー残量") || q.contains("نسبة البطارية"))
            return Kind.BATTERY;
        if (latin.equals("hello") || latin.equals("hi") || latin.equals("hey")
            || latin.equals("bonjour") || latin.equals("hola")
            || latin.equals("hallo") || latin.equals("ciao") || latin.equals("merhaba")
            || q.equals("你好") || q.equals("こんにちは") || q.equals("مرحبا"))
            return Kind.GREETING;
        if (latin.contains("what can you do") || latin.contains("your abilities")
            || latin.contains("que puedes hacer") || latin.contains("que sais tu faire")
            || latin.contains("was kannst du") || q.contains("你会做什么"))
            return Kind.HELP;
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

    static String answer(Kind kind, Date now, int batteryPercent, String languageTag) {
        String lang = languageTag == null ? "ru" : java.util.Locale.forLanguageTag(languageTag).getLanguage();
        if (lang.isEmpty() || "ru".equals(lang) || "uk".equals(lang)
            || "kk".equals(lang) || "uz".equals(lang))
            return answer(kind, now, batteryPercent);
        java.util.Locale loc = java.util.Locale.forLanguageTag(languageTag);
        String time = new java.text.SimpleDateFormat("HH:mm", loc).format(now);
        String date = new java.text.SimpleDateFormat("d MMMM yyyy, EEEE", loc).format(now);
        switch (lang) {
            case "en":
                switch (kind) {
                    case TIME: return "The time is " + time + ".";
                    case DATE: return "Today is " + date + ".";
                    case BATTERY: return "Battery level is " + batteryPercent + " percent.";
                    case GREETING: return "I'm here. How can I help?";
                    default: return answer(kind,now,batteryPercent);
                }
            case "es":
                if (kind == Kind.TIME) return "Son las " + time + ".";
                if (kind == Kind.DATE) return "Hoy es " + date + ".";
                if (kind == Kind.BATTERY) return "La batería está al " + batteryPercent + " por ciento.";
                if (kind == Kind.GREETING) return "Estoy aquí. ¿Cómo puedo ayudarte?";
                break;
            case "de":
                if (kind == Kind.TIME) return "Es ist " + time + " Uhr.";
                if (kind == Kind.DATE) return "Heute ist " + date + ".";
                if (kind == Kind.BATTERY) return "Der Akku hat noch " + batteryPercent + " Prozent.";
                if (kind == Kind.GREETING) return "Ich bin da. Wie kann ich helfen?";
                break;
            case "fr":
                if (kind == Kind.TIME) return "Il est " + time + ".";
                if (kind == Kind.DATE) return "Aujourd'hui, c'est " + date + ".";
                if (kind == Kind.BATTERY) return "La batterie est à " + batteryPercent + " pour cent.";
                if (kind == Kind.GREETING) return "Je suis là. Comment puis-je aider ?";
                break;
            case "pt":
                if (kind == Kind.TIME) return "São " + time + ".";
                if (kind == Kind.DATE) return "Hoje é " + date + ".";
                if (kind == Kind.BATTERY) return "A bateria está em " + batteryPercent + " por cento.";
                break;
            case "it":
                if (kind == Kind.TIME) return "Sono le " + time + ".";
                if (kind == Kind.DATE) return "Oggi è " + date + ".";
                if (kind == Kind.BATTERY) return "La batteria è al " + batteryPercent + " per cento.";
                break;
            case "zh":
                if (kind == Kind.TIME) return "现在是 " + time + "。";
                if (kind == Kind.DATE) return "今天是 " + date + "。";
                if (kind == Kind.BATTERY) return "电池电量为百分之" + batteryPercent + "。";
                break;
            case "ja":
                if (kind == Kind.TIME) return "現在の時刻は " + time + " です。";
                if (kind == Kind.DATE) return "今日は " + date + " です。";
                if (kind == Kind.BATTERY) return "バッテリー残量は " + batteryPercent + " パーセントです。";
                break;
            case "ar":
                if (kind == Kind.TIME) return "الوقت الآن " + time + ".";
                if (kind == Kind.DATE) return "تاريخ اليوم " + date + ".";
                if (kind == Kind.BATTERY) return "شحن البطارية " + batteryPercent + " بالمئة.";
                break;
            case "tr":
                if (kind == Kind.TIME) return "Saat " + time + ".";
                if (kind == Kind.DATE) return "Bugünün tarihi " + date + ".";
                if (kind == Kind.BATTERY) return "Pil seviyesi yüzde " + batteryPercent + ".";
                break;
            default: break;
        }
        return answer(kind,now,batteryPercent);
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
