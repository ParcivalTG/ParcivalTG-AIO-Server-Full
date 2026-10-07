package com.aio.founder;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded edge JSON decoder. It never coerces types or silently replaces duplicate properties. */
final class StrictProjectionJson {
    static final class ObjectValue extends LinkedHashMap<String,Object> { int wireBytes; }
    private final String source;
    private final int stringLimit;
    private int offset, nodes;
    private StrictProjectionJson(String source,int stringLimit) { this.source = source; this.stringLimit=stringLimit; }

    static ObjectValue object(byte[] input, int maximum) throws Exception {
        return object(input,maximum,Math.min(8192,maximum));
    }

    static ObjectValue object(byte[] input, int maximum,int stringLimit) throws Exception {
        if (input == null || input.length == 0 || input.length > maximum) fail("FABRIC_WIRE_BUDGET");
        if (stringLimit < 1 || stringLimit > maximum) fail("FABRIC_STRING_BUDGET");
        String source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
        StrictProjectionJson parser = new StrictProjectionJson(source,stringLimit);
        Object value = parser.value(0); parser.space();
        if (parser.offset != source.length() || !(value instanceof ObjectValue)) fail("FABRIC_JSON_ROOT");
        return (ObjectValue) value;
    }
    private static void fail(String reason) { throw new IllegalArgumentException(reason); }
    private void space() { while (offset < source.length() && " \n\r\t".indexOf(source.charAt(offset)) >= 0) offset++; }
    private boolean take(char expected) { space(); if (offset < source.length() && source.charAt(offset) == expected) { offset++; return true; } return false; }
    private void need(char expected) { if (!take(expected)) fail("FABRIC_JSON_SYNTAX"); }
    private Object value(int depth) {
        if (++nodes > 1024 || depth > 16) fail("FABRIC_JSON_COMPLEXITY");
        space(); if (offset >= source.length()) { fail("FABRIC_JSON_TRUNCATED"); return null; }
        char c = source.charAt(offset);
        if (c == '{') {
            int start = offset++; ObjectValue object = new ObjectValue();
            if (!take('}')) {
                do {
                    space(); if (offset >= source.length() || source.charAt(offset) != '"') fail("FABRIC_JSON_KEY");
                    String key = string(); need(':');
                    if (object.containsKey(key) || object.size() >= 64) fail("FABRIC_JSON_DUPLICATE_OR_KEY_LIMIT");
                    object.put(key,value(depth + 1));
                } while (take(',')); need('}');
            }
            object.wireBytes = source.substring(start,offset).getBytes(StandardCharsets.UTF_8).length; return object;
        }
        if (c == '[') {
            offset++; List<Object> array = new ArrayList<>();
            if (!take(']')) { do { if (array.size() >= 256) fail("FABRIC_JSON_ARRAY_LIMIT"); array.add(value(depth + 1)); } while (take(',')); need(']'); }
            return array;
        }
        if (c == '"') return string();
        if (source.startsWith("true",offset)) { offset += 4; return Boolean.TRUE; }
        if (source.startsWith("false",offset)) { offset += 5; return Boolean.FALSE; }
        if (source.startsWith("null",offset)) { offset += 4; return null; }
        int start = offset;
        while (offset < source.length() && "-+0123456789.eE".indexOf(source.charAt(offset)) >= 0) offset++;
        String token = source.substring(start,offset);
        if (token.length() > 40 || !token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) fail("FABRIC_JSON_NUMBER");
        try { return token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0 ? new BigDecimal(token) : Long.valueOf(token); }
        catch (NumberFormatException invalid) { fail("FABRIC_JSON_NUMBER"); return null; }
    }
    private String string() {
        need('"'); StringBuilder value = new StringBuilder();
        while (offset < source.length()) {
            char c = source.charAt(offset++);
            if (c == '"') {
                String result = value.toString();
                for (int i=0;i<result.length();i++) {
                    char item=result.charAt(i);
                    if (Character.isHighSurrogate(item)) { if (++i>=result.length() || !Character.isLowSurrogate(result.charAt(i))) fail("FABRIC_JSON_UNICODE"); }
                    else if (Character.isLowSurrogate(item)) fail("FABRIC_JSON_UNICODE");
                }
                return result;
            }
            if (c < 32) fail("FABRIC_JSON_CONTROL");
            if (c == '\\') {
                if (offset >= source.length()) fail("FABRIC_JSON_TRUNCATED");
                char escaped = source.charAt(offset++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break; case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break; case 'r': value.append('\r'); break; case 't': value.append('\t'); break;
                    case 'u':
                        if (offset + 4 > source.length()) fail("FABRIC_JSON_TRUNCATED");
                        String hex = source.substring(offset,offset+4);
                        if (!hex.matches("[0-9A-Fa-f]{4}")) fail("FABRIC_JSON_UNICODE");
                        value.append((char)Integer.parseInt(hex,16)); offset += 4; break;
                    default: fail("FABRIC_JSON_ESCAPE");
                }
            } else value.append(c);
            if (value.length() > stringLimit) fail("FABRIC_JSON_STRING_LIMIT");
        }
        fail("FABRIC_JSON_TRUNCATED"); return null;
    }
}
