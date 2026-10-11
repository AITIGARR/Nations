package com.horizon.jarvis;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultilingualSpeechTest {
    @Test public void wakeInVariousLanguages() {
        assertEquals("открой Telegram", MultilingualSpeech.wakeCommand("Джарвис, открой Telegram"));
        assertEquals("open YouTube", MultilingualSpeech.wakeCommand("Hey Jarvis, open YouTube"));
        assertEquals("打开音乐", MultilingualSpeech.wakeCommand("贾维斯，打开音乐"));
        assertEquals("покажи время", MultilingualSpeech.wakeCommand("Жарвис, покажи время"));
        assertEquals("", MultilingualSpeech.wakeCommand("Jarvis"));
        assertNull(MultilingualSpeech.wakeCommand("Explain who Iron Man's Jarvis is"));
    }
    @Test public void preferences() {
        assertEquals("auto",MultilingualSpeech.validated("unsupported"));
        assertEquals("en-GB",MultilingualSpeech.validated("EN-GB"));
        assertEquals(3,MultilingualSpeech.optionIndex("en-GB"));
        assertEquals("es",MultilingualSpeech.voiceLocale("auto","es-MX").getLanguage());
        assertEquals("ru",MultilingualSpeech.voiceLocale("auto",null).getLanguage());
    }
}
