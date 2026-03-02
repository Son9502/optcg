package tools;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class EffectLexer {
    public static final HashMap<String, TokenType> PHRASES_TABLE = new HashMap<>();
    public static final List<String> SORTED_PHRASES = new ArrayList<>();
    public static final List<String[]> LIST_PHRASES = new ArrayList<>(); // List of phrases sorted by length (longest
                                                                         // first)
    public static final List<Pattern> FILTER_PATTERNS = new ArrayList<>(); // List of regex patterns for filters like
                                                                           // "with power 3000 or less"

    static {
        // Delegate to EffectTables — all phrase and filter data lives there
        // so EffectParser can share the same tables without duplication.
        PHRASES_TABLE.putAll(EffectTables.PHRASES);
        SORTED_PHRASES.addAll(EffectTables.SORTED_PHRASES);
        LIST_PHRASES.addAll(EffectTables.LIST_PHRASES);
        FILTER_PATTERNS.addAll(EffectTables.FILTER_PATTERNS);
    }

    /**
     * Parses the input string representing a card effect and produces a list of
     * tokens that represent the structured components of the effect. The lexer
     * identifies known phrases based on a predefined table and extracts any
     * relevant values (like quantities) associated with those phrases. It also logs
     * any unknown words for debugging purposes.
     * 
     * @param input The raw text of the card effect to be parsed.
     * @return A list of tokens representing the structured components of the card
     *         effect. Each token includes its type, the raw text that matched, and
     *         any extracted value.
     */
    public static List<Token> parseEffect(String input) {
        /*
         * position = 0
         * while not at end of input:
         * skip whitespace and punctuation
         * for each phrase in table (longest first):
         * if input[position:].startsWith(phrase):
         * extract any numeric value (regex for "N")
         * emit Token(type, phrase, value)
         * advance position past the phrase
         * break
         * if nothing matched:
         * skip the current word and log it (unknown text)
         */

        // List to hold the resulting tokens
        List<Token> tokens = new LinkedList<>();

        // Normalize input for easier parsing (make it lowercase)
        input = input.toLowerCase();
        // "k.o." contains periods that the split regex would shred — collapse to "ko"
        input = input.replace("k.o.", "ko");
        // En-dash (–) and em-dash (—) appear in card text for negative costs/power.
        // Normalize to hyphen-minus so the number regex [+-]?\d+ catches them.
        input = input.replace("\u2013", "-"); // en-dash → hyphen-minus
        input = input.replace("\u2014", "-"); // em-dash → hyphen-minus

        // Split input into words and punctuation and trim whitespace
        String[] words = input.split(" +|(?=[.,;:()\\[\\]{}])|(?<=[.,;:()\\[\\]{}])");
        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].trim();
        }

        int position = 0;

        while (position < words.length) {
            // Check if the current word is empty or punctuation
            if (words[position].isEmpty() || isPunctuation(words[position])) {
                position++;
                continue; // Skip empty tokens
            }
            // Flag to indicate if we found a match for the current position
            boolean matched = false;

            // [Keyword] Filter
            if ("[".equals(words[position])) {
                // Find the closing bracket and extract the keyword
                int close = findClosingBracket(words, position + 1, "]");
                String keyword = joinWords(words, position + 1, close);
                tokens.add(new Token(TokenType.FILTER, "[" + keyword + "]", keyword));
                position = close + 1;
                continue;
            }

            // {Subtype} Filter
            if ("{".equals(words[position])) {
                // Find the closing bracket and extract the keyword
                int close = findClosingBracket(words, position + 1, "}");
                String keyword = joinWords(words, position + 1, close);
                tokens.add(new Token(TokenType.FILTER, "{" + keyword + "}", keyword));
                position = close + 1;
                continue;
            }

            // (Attribute) Filter
            if ("(".equals(words[position])) {
                // Find the closing bracket and extract the keyword
                int close = findClosingBracket(words, position + 1, ")");
                String keyword = joinWords(words, position + 1, close);
                tokens.add(new Token(TokenType.FILTER, "(" + keyword + ")", keyword));
                position = close + 1;
                continue;
            }

            // "TypeName" Filter — quoted type/subtype name that may span multiple words.
            // The split regex does not break on '"', so "Straw Hat Pirates" becomes
            // ["\"straw", "hat", "pirates\""] after splitting on spaces.
            if (words[position].startsWith("\"")) {
                int end = position;
                // Scan forward until we hit the word that closes the quote.
                while (end < words.length && !words[end].endsWith("\""))
                    end++;
                // Guard against an unclosed quote (edge case — should not happen in valid card
                // text).
                if (end >= words.length)
                    end = words.length - 1;
                String raw = joinWords(words, position, end + 1);
                String inner = raw.replaceAll("^\"|\"$", ""); // strip surrounding quotes
                tokens.add(new Token(TokenType.FILTER, raw, inner));
                position = end + 1;
                continue;
            }

            // Check for numbers (e.g., "-3, +1000, -2000, 1, 2, etc")
            if (words[position].matches("[+-]?\\d+")) { // Check for negative numbers as well (e.g., "-3 cost")
                int val = Integer.parseInt(words[position]);
                tokens.add(new Token(TokenType.QUANTITY, words[position], val));
                // System.out.println("Processed quantity: " + words[position]);
                position++;
                continue;
            }

            // Check for filters like "with power 3000 or less" or "with a BLOCKER keyword"
            String remainingText = joinWords(words, position, words.length);
            for (Pattern pattern : FILTER_PATTERNS) {
                Matcher matcher = pattern.matcher(remainingText);
                if (matcher.lookingAt()) { // Match must be at the start of the remaining text
                    Object value = matcher.groupCount() > 0 ? Integer.parseInt(matcher.group(1)) : null; // Extract the
                                                                                                         // numeric
                                                                                                         // value from
                                                                                                         // the filter
                    tokens.add(new Token(TokenType.FILTER, matcher.group(), value));
                    position += matcher.group().split(" ").length; // Advance position by the number of words in the
                                                                   // matched filter
                    // System.out.println("Processed filter: " + matcher.group() + " with value: " +
                    // value);
                    matched = true;
                    break;
                }
            }
            // Check for known phrases in the table (longest first)
            if (!matched) {
                for (int i = 0; i < LIST_PHRASES.size(); i++) {
                    String[] phrase = LIST_PHRASES.get(i); // Get the phrase as an array of words
                    String phraseStr = SORTED_PHRASES.get(i); // Get the original phrase string for logging and token
                                                              // creation
                    if (matchesPhrase(words, position, phrase)) {

                        Object value = null;
                        tokens.add(new Token(PHRASES_TABLE.get(phraseStr), phraseStr, value));
                        // System.out.println("Processed phrase: " + phraseStr + " with value: " +
                        // value);
                        position += phrase.length;
                        matched = true;
                        break;
                    }
                }
            }

            // If no known phrase matched, emit an UNKNOWN token and advance
            if (!matched) {
                tokens.add(new Token(TokenType.UNKNOWN, words[position], null));
                position++;
            }
        }
        return tokens;
    }

    /**
     * Utility method to check if a given word is punctuation. This is used to skip
     * 
     * @param word The word to check for punctuation. It is expected to be a single
     *             character or a string of punctuation characters.
     * @return true if the word is punctuation, false otherwise. The method uses a
     *         regular expression to match common punctuation characters such as
     *         .,;:
     */
    public static boolean isPunctuation(String word) {
        return word.matches("[.,;:]");
    }

    /**
     * Utility method to check if a sequence of words in the input matches a given
     * phrase. This is used by the EffectLexer to identify known phrases in the card
     * effect text. The method compares the words in the input starting from a
     * specified position with the words in the phrase. It returns true if all words
     * in the phrase match the corresponding words in the input, and false
     * otherwise.
     * 
     * @param words       The array of words from the input text, which has been
     *                    split and normalized. This represents the current state of
     *                    the input being processed by the lexer.
     * @param position    The current index in the words array from which to start
     *                    checking for a match with the phrase. This allows the
     *                    method to check for the presence of the phrase at
     *                    different points in the input text as the lexer processes
     *                    it.
     * @param phraseWords The array of words that make up the phrase we are trying
     *                    to match. This is derived from the known phrases in the
     *                    PHRASES_TABLE and is used to check if the input text
     *                    contains this specific sequence of words at the current
     *                    position.
     * @return true if the sequence of words in the input matches the phrase
     *         starting from the specified position, false otherwise. The method
     *         ensures that it does not go out of bounds of the input array and that
     *         each word in the phrase matches the corresponding word in the input.
     */
    public static boolean matchesPhrase(String[] words, int position, String[] phraseWords) {
        if (position + phraseWords.length > words.length) {
            return false; // Not enough words left to match the phrase
        }
        for (int i = 0; i < phraseWords.length; i++) {
            if (!words[position + i].equals(phraseWords[i])) {
                return false; // Words do not match
            }
        }
        return true; // All words match the phrase
    }

    /**
     * Utility method to find the index of the closing bracket in the input words
     * array. This is used to identify the end of a filter or keyword that is
     * enclosed in brackets in the card effect text. The method starts searching
     * from a specified index and looks for the closing bracket (e.g., "]"). If it
     * finds the closing bracket, it returns its index; otherwise, it returns -1 to
     * indicate that the closing bracket was not found. This allows the lexer to
     * correctly identify the boundaries of filters or keywords that are enclosed in
     * brackets and to extract the relevant text for token creation.
     * 
     * @param words   The array of words from the input text, which has been split
     *                and normalized. This represents the current state of
     * @param start   The index in the words array from which to start searching for
     *                the closing bracket. This allows the method to look for the
     *                closing bracket after a specific point in the input text,
     *                which is useful when processing filters that are enclosed in
     *                brackets.
     * @param closing The string representing the closing bracket to search for
     *                (e.g., "]"). This is used to identify the end of a filter or
     *                keyword that is enclosed in brackets in the card effect text.
     * @return The index of the closing bracket in the words array if found, or -1
     *         if not found. This allows the lexer to correctly identify the
     *         boundaries of filters or keywords that are enclosed in brackets and
     *         to extract the relevant text for token creation.
     */
    public static int findClosingBracket(String[] words, int start, String closing) {
        for (int i = start; i < words.length; i++) {
            if (closing.equals(words[i])) {
                return i;
            }
        }
        return words.length; // Not found
    }

    /**
     * Utility method to join a range of words from the input array into a single
     * string. This is used to reconstruct the original text of a filter or keyword
     * that is enclosed in brackets, which may consist of multiple words. The method
     * takes a starting index and an ending index and concatenates the words in that
     * range with spaces in between.
     * 
     * @param words The array of words from the input text, which has been split and
     *              normalized. This represents the current state of the input being
     *              processed by the lexer.
     * @param start The index in the words array from which to start joining. This
     *              allows the method to specify the beginning of the range of words
     *              that should be combined into a single string, which is useful
     *              when reconstructing filters or keywords that may consist of
     *              multiple words.
     * @param end   The index in the words array at which to stop joining
     *              (exclusive). This allows the method to specify the end of the
     *              range of words that should be combined into a single string,
     *              ensuring that only the relevant words are included in the
     *              resulting string.
     * @return A single string that is the result of joining the specified range of
     *         words with spaces in between. This is used to create the raw text for
     *         tokens that represent filters or keywords, preserving the original
     *         wording from the card effect text.
     */
    public static String joinWords(String[] words, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append(" ");
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
