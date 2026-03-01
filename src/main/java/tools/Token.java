package tools;

/**
 * Token represents a structured component of a card effect, identified by the
 * EffectLexer during the parsing process. Each token has a type (defined by
 * TokenType), the raw text from the card effect that corresponds to this token,
 * and a value that can be used for further processing by the game engine. The
 * value can be of any type depending on the token type; for example, a QUANTITY
 * token might have an Integer value, while a PLAYER token might have a
 * reference to a Player object. This structured representation allows the game
 * engine to interpret and execute card effects in a flexible and extensible
 * manner.
 * For example, for the card effect "Draw 2 cards if you have more cards in hand
 * than your opponent", the EffectLexer might produce the following tokens:
 * - Token(VERB, "Draw", null)
 * - Token(QUANTITY, "2", 2)
 * - Token(CARD_TYPE, "cards", null)
 * - Token(CONDITION, "if you have more cards in hand than your opponent", some
 * structured representation of the condition)
 */
public record Token(TokenType type, String raw, Object value) {

}
