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
    void unterminatedCommentDropsToEndOfFile() {
        assertEquals("int a; ", JavaComments.strip("int a;/* never closed"));
        assertEquals("int b;\nint c;", JavaComments.strip("int b;// gone\nint c;"));
    }
}
