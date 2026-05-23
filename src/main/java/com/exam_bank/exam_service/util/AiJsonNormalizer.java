package com.exam_bank.exam_service.util;

public final class AiJsonNormalizer {

    private AiJsonNormalizer() {
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    public static String normalizeQuestionArray(String rawJson) {
        if (rawJson == null) {
            return "";
        }

        String json = stripMarkdownFence(rawJson.trim());
        int firstArray = json.indexOf('[');
        int lastArray = json.lastIndexOf(']');
        if (firstArray >= 0 && lastArray >= firstArray) {
            json = json.substring(firstArray, lastArray + 1);
        }

        // Walk the JSON text and only alter backslashes that appear inside JSON string
        // literals and are not part of a valid JSON escape sequence. This avoids
        // brittle
        // regex behavior and handles edge-cases such as "\\a" or "\\c" produced
        // by some AI outputs.
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (!inString) {
                out.append(ch);
                if (ch == '"') {
                    inString = true;
                    escaped = false;
                }
                continue;
            }

            // inside a JSON string
            if (escaped) {
                // previous char was a backslash; just copy this char and clear escaped
                out.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                // look ahead to decide if it's a valid escape
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == 'u') {
                        // preserve unicode escape sequence as-is (copy '\\u' and up to 4 hex digits)
                        out.append('\\');
                        out.append('u');
                        i++; // consumed 'u'
                        int copied = 0;
                        while (copied < 4 && i + 1 < json.length() && isHex(json.charAt(i + 1))) {
                            i++;
                            out.append(json.charAt(i));
                            copied++;
                        }
                        continue;
                    }

                    // valid short escapes per JSON spec
                    if (next == '"' || next == '\\' || next == '/' || next == 'b' || next == 'f' || next == 'n'
                            || next == 'r' || next == 't') {
                        // keep the single backslash and let next char be appended in next iteration
                        out.append('\\');
                        // do not set escaped=true; next loop will append next char normally
                        continue;
                    }

                    // invalid escape like \\a or \\c -> escape the backslash itself by writing two
                    // backslashes
                    out.append('\\');
                    out.append('\\');
                    // do not consume 'next' here; let it be processed in following iteration
                    continue;
                } else {
                    // trailing backslash at end of string -> escape it
                    out.append('\\');
                    out.append('\\');
                    continue;
                }
            }

            if (ch == '"') {
                out.append(ch);
                inString = false;
                escaped = false;
                continue;
            }

            out.append(ch);
        }

        return out.toString().trim();
    }

    private static String stripMarkdownFence(String json) {
        if (!json.startsWith("```")) {
            return json;
        }

        int firstNewline = json.indexOf('\n');
        if (firstNewline > 0) {
            json = json.substring(firstNewline + 1);
        } else {
            json = json.substring(3);
        }

        json = json.trim();
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }
}
