package dev.danvega.springevals;

/**
 * Removes comments the way the compiler would see them, so a comment can never
 * satisfy or trip a source pattern. Java: unicode escapes are translated first
 * (JLS 3.3), text blocks honor backslash escapes, block comments do not nest.
 * Kotlin: raw strings take no escapes and block comments nest.
 */
final class JavaComments {

    private JavaComments() {
    }

    static String strip(String source) {
        return strip(source, false);
    }

    static String strip(String source, boolean kotlin) {
        String text = kotlin ? source : translateUnicodeEscapes(source);
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"' && text.startsWith("\"\"\"", i)) {
                int end = tripleQuoteEnd(text, i + 3, !kotlin);
                out.append(text, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = literalEnd(text, i, c);
                out.append(text, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                i = blockCommentEnd(text, i + 2, kotlin);
                // Keep a separator so tokens on either side of a comment never fuse.
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** A backslash starts an escape only when preceded by an even number of backslashes; the result is never re-scanned. */
    static String translateUnicodeEscapes(String source) {
        if (source.indexOf("\\u") < 0) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        int backslashes = 0;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\\' && backslashes % 2 == 0 && i + 1 < n && source.charAt(i + 1) == 'u') {
                int j = i + 1;
                while (j < n && source.charAt(j) == 'u') {
                    j++;
                }
                if (j + 4 <= n && isHex(source, j)) {
                    out.append((char) Integer.parseInt(source.substring(j, j + 4), 16));
                    i = j + 4;
                    backslashes = 0;
                    continue;
                }
            }
            backslashes = c == '\\' ? backslashes + 1 : 0;
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isHex(String source, int from) {
        for (int k = from; k < from + 4; k++) {
            if (Character.digit(source.charAt(k), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int tripleQuoteEnd(String text, int from, boolean escapes) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (escapes && c == '\\') {
                i += 2;
                continue;
            }
            if (text.startsWith("\"\"\"", i)) {
                return i + 3;
            }
            i++;
        }
        return text.length();
    }

    private static int blockCommentEnd(String text, int from, boolean nested) {
        int depth = 1;
        int i = from;
        while (i < text.length()) {
            if (nested && text.startsWith("/*", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith("*/", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static int literalEnd(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return i + 1;
            }
            i++;
        }
        return source.length();
    }
}
