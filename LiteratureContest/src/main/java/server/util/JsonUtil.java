package server.util;

import java.util.*;

/**
 * Небольшой JSON-парсер без внешних зависимостей.
 * Поддерживает объекты, массивы, строки с экранированием, числа, null и boolean.
 */
public final class JsonUtil {
    private JsonUtil() {}

    public static Map<String, Object> parseObject(String json) {
        Parser p = new Parser(json);
        Object v = p.parseValue();
        if (!(v instanceof Map<?,?> map)) {
            throw new IllegalArgumentException("JSON root is not object");
        }
        p.skipWs();
        if (!p.eof()) throw new IllegalArgumentException("Trailing characters after JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) map;
        return res;
    }

    public static String getString(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof String ? (String) v : null;
    }

    public static List<Map<String, Object>> getObjectList(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        List<Map<String, Object>> res = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?,?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                res.add(mm);
            }
        }
        return res;
    }

    private static final class Parser {
        private final String s;
        private int pos;
        Parser(String s) { this.s = s; this.pos = 0; }

        Object parseValue() {
            skipWs();
            if (eof()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield parseNumber();
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at " + pos);
                }
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip {
            skipWs();
            if (peek('}')) { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (!peek(':')) throw new IllegalArgumentException("Expected ':' after key at " + pos);
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                if (peek('}')) { pos++; break; }
                if (!peek(',')) throw new IllegalArgumentException("Expected ',' in object at " + pos);
                pos++;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip [
            skipWs();
            if (peek(']')) { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek(']')) { pos++; break; }
                if (!peek(',')) throw new IllegalArgumentException("Expected ',' in array at " + pos);
                pos++;
            }
            return list;
        }

        private String parseString() {
            if (!peek('"')) throw new IllegalArgumentException("Expected string at " + pos);
            pos++; // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (!eof()) {
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    if (eof()) throw new IllegalArgumentException("Incomplete escape at end of string");
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Bad escape \\" + esc + " at " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            if (!eof() && s.charAt(pos) == '.') {
                pos++;
                while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (!eof() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                pos++;
                if (!eof() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (!eof() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(start, pos);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                } else {
                    return Long.parseLong(num);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad number '" + num + "'");
            }
        }

        private void expect(String token) {
            if (!s.startsWith(token, pos)) throw new IllegalArgumentException("Expected '" + token + "' at " + pos);
            pos += token.length();
        }

        private void skipWs() {
            while (!eof()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        private boolean peek(char c) { return !eof() && s.charAt(pos) == c; }
        private boolean eof() { return pos >= s.length(); }
    }
}
