package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCommentsTest {

    @Test
    void dropsLineAndBlockCommentsButKeepsCode() {
        String stripped = JavaComments.strip("""
                /** Replaces the old new ObjectMapper() bean. */
                class A {
                    // RestClient.create() was here
                    int x = 1; /* JsonMapper.builder() */ int y = 2;
                }
                """);
        assertFalse(stripped.contains("ObjectMapper"));
        assertFalse(stripped.contains("RestClient.create"));
        assertFalse(stripped.contains("JsonMapper.builder"));
        assertTrue(stripped.contains("int x = 1;"));
        assertTrue(stripped.contains("int y = 2;"));
    }

    @Test
    void keepsCommentMarkersInsideStringAndCharLiterals() {
        String stripped = JavaComments.strip("""
                String url = "http://example.com/*not a comment*/"; // trailing
                char slash = '/'; String esc = "quote \\" // still string";
                """);
        assertTrue(stripped.contains("http://example.com/*not a comment*/"));
        assertTrue(stripped.contains("'/'"));
        assertTrue(stripped.contains("// still string"));
        assertFalse(stripped.contains("trailing"));
    }

    @Test
    void keepsTextBlocksIntact() {
        String stripped = JavaComments.strip("""
                String sql = \"""
                    -- not java
                    /* inside text block */
                    \""";
                /* real comment */
                """);
        assertTrue(stripped.contains("/* inside text block */"));
        assertFalse(stripped.contains("real comment"));
    }

    @Test
    void escapedTripleQuoteInsideATextBlockDoesNotEndIt() {
        String stripped = JavaComments.strip("String s = \"\"\"\n x \\\"\"\" y\n \"\"\";\n// ProblemDetail\nimport a.B;\n");
        assertFalse(stripped.contains("ProblemDetail"), "a comment after the text block must still be stripped");
        assertTrue(stripped.contains("import a.B;"));
        String stripped2 = JavaComments.strip("String s = \"\"\"\n x \\\"\"\" /* y\n \"\"\";\nimport a.B;\n");
        assertTrue(stripped2.contains("import a.B;"), "code after the text block must survive");
    }

    @Test
    void unicodeEscapesAreTranslatedBeforeCommentsAreFound() {
        String stripped = JavaComments.strip("int a; \\u002f\\u002f ProblemDetail\nint b;");
        assertFalse(stripped.contains("ProblemDetail"), "\\u002f\\u002f is // to the compiler");
        String newline = JavaComments.strip("int a; // gone \\u000a int c = 1; // gone too\nint d;");
        assertTrue(newline.contains("int c = 1;"), "\\u000a ends the line comment the way javac sees it");
        assertFalse(newline.contains("gone"));
        String escapedBackslash = JavaComments.strip("String s = \"\\\\u002f\"; // ProblemDetail");
        assertTrue(escapedBackslash.contains("\\\\u002f"), "an even number of backslashes is not an escape");
        assertFalse(escapedBackslash.contains("ProblemDetail"));
    }

    @Test
    void kotlinNestsBlockCommentsAndRawStringsTakeNoEscapes() {
        String kotlin = JavaComments.strip("val a = 1 /* outer /* inner */ ProblemDetail */ val b = 2", true);
        assertFalse(kotlin.contains("ProblemDetail"));
        assertTrue(kotlin.contains("val b = 2"));
        String raw = JavaComments.strip("val s = \"\"\"back\\slash\"\"\"\n// ProblemDetail\nval t = 1", true);
        assertFalse(raw.contains("ProblemDetail"));
        assertTrue(raw.contains("val t = 1"));
        String java = JavaComments.strip("int a; /* outer /* inner */ int b; */ int c;");
        assertTrue(java.contains("int b;"), "Java block comments do not nest");
    }

    @Test
    void unterminatedCommentDropsToEndOfFile() {
        assertEquals("int a; ", JavaComments.strip("int a;/* never closed"));
        assertEquals("int b;\nint c;", JavaComments.strip("int b;// gone\nint c;"));
    }
}
