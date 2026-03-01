package tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TokenTest {

    // ── Record field access ───────────────────────────────────────────────────

    @Test
    void token_fieldsReturnConstructorValues() {
        Token t = new Token(TokenType.VERB, "draw", null);
        assertEquals(TokenType.VERB, t.type());
        assertEquals("draw", t.raw());
        assertNull(t.value());
    }

    @Test
    void token_integerValueAccessible() {
        Token t = new Token(TokenType.QUANTITY, "5", 5);
        assertEquals(5, t.value());
    }

    @Test
    void token_stringValueAccessible() {
        Token t = new Token(TokenType.FILTER, "[blocker]", "blocker");
        assertEquals("blocker", t.value());
    }

    // ── Record equality ───────────────────────────────────────────────────────

    @Test
    void token_sameFields_areEqual() {
        Token a = new Token(TokenType.CONNECTOR, "and", null);
        Token b = new Token(TokenType.CONNECTOR, "and", null);
        assertEquals(a, b);
    }

    @Test
    void token_differentType_notEqual() {
        Token a = new Token(TokenType.VERB, "draw", null);
        Token b = new Token(TokenType.CONNECTOR, "draw", null);
        assertNotEquals(a, b);
    }

    @Test
    void token_differentRaw_notEqual() {
        Token a = new Token(TokenType.VERB, "draw", null);
        Token b = new Token(TokenType.VERB, "rest", null);
        assertNotEquals(a, b);
    }

    @Test
    void token_differentValue_notEqual() {
        Token a = new Token(TokenType.QUANTITY, "5", 5);
        Token b = new Token(TokenType.QUANTITY, "5", 3);
        assertNotEquals(a, b);
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void token_toString_notNullOrEmpty() {
        Token t = new Token(TokenType.PLAYER, "your", null);
        assertNotNull(t.toString());
        assertFalse(t.toString().isEmpty());
    }
}
