package com.horizon.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class JarvisPersonaTest {
    @Test public void addsHonorific() {
        assertEquals("Сейчас 15:34, сэр.", JarvisPersona.withSir("Сейчас 15:34."));
        assertEquals("42, сэр.", JarvisPersona.withSir("42"));
        assertEquals("Чем могу помочь, сэр?", JarvisPersona.withSir("Чем могу помочь?"));
    }
    @Test public void avoidsRepeats() {
        assertEquals("Слушаю, сэр.", JarvisPersona.withSir("Слушаю, сэр."));
        assertEquals("До связи, сэр!", JarvisPersona.withSir("До связи, сэр!"));
        assertEquals("", JarvisPersona.withSir(""));
    }
}
