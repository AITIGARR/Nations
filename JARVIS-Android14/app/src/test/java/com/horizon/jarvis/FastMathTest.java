package com.horizon.jarvis;
import org.junit.Test;
import static org.junit.Assert.*;
public class FastMathTest {
    @Test public void basic() {
        assertEquals("Получается 42.", FastMath.calculate("Джарвис, сколько будет 6 умножить на 7?"));
        assertEquals("Получается 55.", FastMath.calculate("посчитай 100 разделить на 2 плюс 5"));
        assertEquals("Получается 17.", FastMath.calculate("2+3*5"));
        assertEquals("Получается 25.", FastMath.calculate("вычисли (2+3)*5"));
    }
    @Test public void realNumbers() {
        assertEquals("Получается 3.75.", FastMath.calculate("сколько будет 1,5 умножить на 2,5"));
        assertEquals("Получается -2.", FastMath.calculate("посчитай 2 минус 4"));
        assertEquals("Получается 30.", FastMath.calculate("20 процентов от 150"));
        assertEquals("Получается 50.", FastMath.calculate("посчитай 25% от 200"));
    }
    @Test public void errors() {
        assertEquals("На ноль делить нельзя.", FastMath.calculate("посчитай 8 / 0"));
        assertNull(FastMath.calculate("объясни, как работает андроид"));
        assertNull(FastMath.calculate("открой YouTube"));
    }
}
