package com.example.agentlab.tool.tools;

import com.example.agentlab.tool.Tool;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "数学计算器，toolInput 传数学表达式如 \"3.14 * 10 * 10\" 或 \"(100 + 200) / 3\"";
    }

    @Override
    public String call(String input) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
            if (engine == null) {
                engine = new ScriptEngineManager().getEngineByName("nashorn");
            }
            if (engine == null) {
                return evaluateFallback(input);
            }
            Object result = engine.eval(input.trim());
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算出错：" + e.getMessage();
        }
    }

    private String evaluateFallback(String expr) {
        try {
            double result = new Object() {
                int pos = -1;
                int ch;
                String str = expr.trim();

                double parse() {
                    nextChar();
                    double v = parseExpr();
                    return v;
                }

                void nextChar() {
                    ch = (++pos < str.length()) ? str.charAt(pos) : -1;
                }

                boolean eat(int c) {
                    while (ch == ' ') nextChar();
                    if (ch == c) { nextChar(); return true; }
                    return false;
                }

                double parseExpr() {
                    double x = parseTerm();
                    for (;;) {
                        if (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }

                double parseTerm() {
                    double x = parseFactor();
                    for (;;) {
                        if (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }

                double parseFactor() {
                    if (eat('+')) return +parseFactor();
                    if (eat('-')) return -parseFactor();
                    double x;
                    if (eat('(')) {
                        x = parseExpr();
                        eat(')');
                    } else {
                        int start = pos;
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(str.substring(start, pos));
                    }
                    return x;
                }
            }.parse();
            if (result == (long) result) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算出错：" + e.getMessage();
        }
    }
}
