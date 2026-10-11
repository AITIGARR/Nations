package com.horizon.jarvis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, fast local calculator. No language model, network, reflection or eval.
 * Returns null when a phrase isn't clearly a calculation.
 */
final class FastMath {
    private static final MathContext PRECISION = new MathContext(16, RoundingMode.HALF_UP);
    private static final Pattern PERCENT = Pattern.compile("([+-]?\\d+(?:[.,]\\d+)?)\\s*(?:%|процент(?:ов|а)?)\\s+от\\s+([+-]?\\d+(?:[.,]\\d+)?)");
    private FastMath() {}

    static String calculate(String request) {
        if (request == null || request.length() > 240) return null;
        String original = request.toLowerCase(Locale.ROOT).replace('ё','е').trim();
        if (original.startsWith("джарвис")) original = original.substring(7).replaceFirst("^[,\\s.:!?-]+", "").trim();
        Matcher percent = PERCENT.matcher(original);
        if (percent.find() && percent.start() <= 22) {
            try {
                BigDecimal a = number(percent.group(1)), b = number(percent.group(2));
                return "Получается " + display(a.multiply(b, PRECISION).divide(BigDecimal.valueOf(100), PRECISION)) + ".";
            } catch (ArithmeticException ignored) { return "Не удалось вычислить проценты."; }
        }
        boolean asked = original.matches("^(сколько будет|посчитай|рассчитай|вычисли|реши пример|посчитай пример|чему равно|сколько получится|найди значение|посчитай сколько будет)\\b.*");
        String expression = original.replaceFirst("^(сколько будет|посчитай|рассчитай|вычисли|реши пример|посчитай пример|чему равно|сколько получится|найди значение|посчитай сколько будет)\\s*", "");
        expression = expression.replaceFirst("\\s*(пожалуйста|скажи мне|сколько получится|равно|это)\\s*[?.!]*$", "");
        expression = expression.replaceAll("[?!]$", "").trim();
        expression = expression.replace("умноженное на", "*").replace("умножить на","*")
            .replace("помножить на","*").replace("разделить на","/")
            .replace("делить на","/").replace("поделить на","/")
            .replace("плюс","+").replace("минус","-")
            .replace("х","*").replace("×","*").replace("÷","/")
            .replace(" : "," / ");
        expression = expression.replaceAll("(?<=\\d),(?=\\d)", ".");
        expression = expression.replaceAll("\\s+", "");
        if (!expression.matches("[0-9.+*/()\\-]{2,150}")) return null;
        if (!asked && !expression.matches(".*[+*/\\-].*")) return null;
        try {
            Parser parser = new Parser(expression);
            BigDecimal value = parser.sum(0);
            if (parser.pos != parser.s.length()) return null;
            if (value.precision() > 100) return "Слишком большое число.";
            return "Получается " + display(value) + ".";
        } catch (ArithmeticException e) {
            return "На ноль делить нельзя.";
        } catch (RuntimeException e) {
            return asked ? "Не удалось разобрать пример. Скажите его числами, например: «25 умножить на 8»." : null;
        }
    }

    private static BigDecimal number(String text) { return new BigDecimal(text.replace(',', '.')); }
    private static String display(BigDecimal x) {
        String s = x.stripTrailingZeros().toPlainString();
        return s.equals("-0") ? "0" : s;
    }

    private static final class Parser {
        final String s;
        int pos;
        int steps;
        Parser(String text) { s=text; }
        BigDecimal sum(int depth) {
            BigDecimal x = product(depth);
            while (pos < s.length()) {
                char op=s.charAt(pos);
                if (op != '+' && op != '-') break;
                pos++;
                BigDecimal y=product(depth);
                x=op=='+' ? x.add(y, PRECISION) : x.subtract(y, PRECISION);
            }
            return x;
        }
        BigDecimal product(int depth) {
            BigDecimal x=unary(depth);
            while (pos < s.length()) {
                char op=s.charAt(pos);
                if (op != '*' && op != '/') break;
                pos++;
                BigDecimal y=unary(depth);
                x=op=='*' ? x.multiply(y, PRECISION) : x.divide(y, PRECISION);
            }
            return x;
        }
        BigDecimal unary(int depth) {
            if (++steps > 120 || depth > 12) throw new IllegalArgumentException("Too complex");
            if (pos < s.length() && s.charAt(pos)=='-') {pos++;return unary(depth+1).negate();}
            if (pos < s.length() && s.charAt(pos)=='+') {pos++;return unary(depth+1);}
            if (pos < s.length() && s.charAt(pos)=='(') {
                pos++;BigDecimal x=sum(depth+1);
                if (pos>=s.length() || s.charAt(pos++)!=')') throw new IllegalArgumentException("Missing )");
                return x;
            }
            int start=pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos)=='.')) pos++;
            if (start==pos) throw new IllegalArgumentException("Expected number");
            return number(s.substring(start,pos));
        }
    }
}
