package com.horizon.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContactCallingTest {
    @Test public void commands() {
        assertEquals("маме", ContactCalling.requestedContact("Джарвис, позвони маме"));
        assertEquals("Ивану Петрову", ContactCalling.requestedContact("позвони Ивану Петрову"));
        assertEquals("Алексей", ContactCalling.requestedContact("набери контакт Алексей"));
        assertEquals("John", ContactCalling.requestedContact("Call John"));
        assertEquals("Ольге", ContactCalling.requestedContact("позвони Ольге пожалуйста"));
    }
    @Test public void notAccidental() {
        assertNull(ContactCalling.requestedContact("Кто придумал телефон?"));
        assertNull(ContactCalling.requestedContact("Прочитай контакты"));
        assertNull(ContactCalling.requestedContact("Объясни, как работают звонки"));
    }
    @Test public void inflections() {
        assertEquals("мама",ContactCalling.normalizedName("Маме"));
        assertEquals("папа",ContactCalling.normalizedName("Папу"));
        assertEquals("бабушка",ContactCalling.normalizedName("Бабушке"));
    }
}
