package com.horizon.jarvis;
import org.junit.Test;
import static org.junit.Assert.*;
public class BasicMultilingualTest {
    @Test public void recognizeCommands() {
        assertEquals(BasicCommands.Kind.TIME,BasicCommands.classify("what time is it"));
        assertEquals(BasicCommands.Kind.TIME,BasicCommands.classify("Qué hora es"));
        assertEquals(BasicCommands.Kind.TIME,BasicCommands.classify("Quelle heure est-il"));
        assertEquals(BasicCommands.Kind.TIME,BasicCommands.classify("Wie spät ist es"));
        assertEquals(BasicCommands.Kind.TIME,BasicCommands.classify("现在几点"));
        assertEquals(BasicCommands.Kind.BATTERY,BasicCommands.classify("battery percentage"));
        assertEquals(BasicCommands.Kind.DATE,BasicCommands.classify("what is the date"));
    }
    @Test public void englishAnswer() {
        assertTrue(BasicCommands.answer(BasicCommands.Kind.TIME,new java.util.Date(0),60,"en-US").startsWith("The time is"));
        assertEquals("Battery level is 60 percent.",BasicCommands.answer(BasicCommands.Kind.BATTERY,new java.util.Date(0),60,"en-GB"));
    }
}
