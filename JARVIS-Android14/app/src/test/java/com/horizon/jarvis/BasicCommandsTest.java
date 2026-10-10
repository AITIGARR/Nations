package com.horizon.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Date;

public class BasicCommandsTest {
    @Test public void colloquialTime() {
        for (String phrase : new String[]{
            "сколько время", "Сколько времени?", "Джарвис, сколько время?",
            "который час", "скажи время", "ВРЕМЯ", "какое сейчас время"
        }) assertEquals(phrase, BasicCommands.Kind.TIME, BasicCommands.classify(phrase));
    }
    @Test public void dateAndBattery() {
        assertEquals(BasicCommands.Kind.DATE, BasicCommands.classify("какое сегодня число"));
        assertEquals(BasicCommands.Kind.DATE, BasicCommands.classify("Джарвис, какой день недели?"));
        assertEquals(BasicCommands.Kind.BATTERY, BasicCommands.classify("сколько заряд батареи"));
        assertEquals(BasicCommands.Kind.BATTERY, BasicCommands.classify("сколько процентов на телефоне"));
    }
    @Test public void greetingsAndAbilities() {
        assertEquals(BasicCommands.Kind.GREETING, BasicCommands.classify("Привет"));
        assertEquals(BasicCommands.Kind.HELP, BasicCommands.classify("что ты умеешь"));
        assertEquals(BasicCommands.Kind.IDENTITY, BasicCommands.classify("как тебя зовут"));
        assertEquals(BasicCommands.Kind.THANKS, BasicCommands.classify("спасибо"));
    }
    @Test public void doNotHijackRealQuestions() {
        assertEquals(BasicCommands.Kind.NONE, BasicCommands.classify("Что такое OpenCode?"));
        assertEquals(BasicCommands.Kind.NONE, BasicCommands.classify("Напиши код для Android"));
        assertEquals(BasicCommands.Kind.NONE, BasicCommands.classify("Открой Minecraft"));
    }
    @Test public void hasImmediateLocalAnswer() {
        String text=BasicCommands.answer(BasicCommands.Kind.TIME,new Date(0),44);
        assertTrue(text.startsWith("Сейчас "));
        assertEquals("Заряд батареи 44 процентов.",
            BasicCommands.answer(BasicCommands.Kind.BATTERY,new Date(0),44));
    }
}
