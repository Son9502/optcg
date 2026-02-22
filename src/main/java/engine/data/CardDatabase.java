package engine.data;

import java.io.File;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import engine.cards.CardData;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CardDatabase {

    // Primary index: card_set_id → CardData
    private final Map<String, CardData> cardCache = new HashMap<>();

    // Secondary index: normalized name (lowercase) → all cards with that name.
    // A name maps to multiple cards because the same character is printed in multiple sets.
    private final Map<String, List<CardData>> byName = new HashMap<>();

    // sub_types is a space-separated string of multi-word subtype names
    // (e.g. "Straw Hat Crew Fish-Man Island"). Because subtype names themselves
    // contain spaces there is no reliable way to tokenize them — so getBySubtype()
    // uses String.contains() at query time rather than a prebuilt inverted index.

    private final ObjectMapper mapper;
    private final String[] directories = { "sets", "decks", "promos" };

    public CardDatabase(String dataDir) {
        mapper = new ObjectMapper();
        // Handle "NULL" strings and string-encoded numbers (cost/power/life)
        SimpleModule intModule = new SimpleModule();
        intModule.addDeserializer(Integer.class, new NullableIntDeserializer());
        mapper.registerModule(intModule);
        // Return null instead of throwing for unknown enum values (dirty promo data)
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        loadAllCards(dataDir);
    }

    public CardDatabase() {
        this("src/main/resources/raw/data/");
    }

    public void loadAllCards(String compiledDir) {
        try {
            // Iterate through each category and load card data
            for (String dir : directories) {
                // Construct the path to the directory containing the JSON files for this category
                Path dirPath = Path.of(compiledDir, dir);
                System.out.println("Loading card data from: " + dirPath);
                
                // List all JSON files in the directory
                File[] files = new File(dirPath.toString()).listFiles((d, name) -> name.endsWith(".json"));
                if (files == null) {
                    System.out.println("No JSON files found in directory: " + dirPath);
                    continue;
                }
                System.out.println("Found " + files.length + " JSON files. Loading card data...");
                
                // Load each JSON file and add card data to the cache
                for (File file : files) {
                    System.out.println("Loading card data from file: " + file.getName());
                    JsonNode rootNode = mapper.readTree(file);
                    if (rootNode.isArray()) {
                        for (JsonNode node : rootNode) {
                            try {
                                CardData cardData = mapper.treeToValue(node, CardData.class);
                                if (cardData.getId() != null) {
                                    cardCache.put(cardData.getId(), cardData);
                                    byName.computeIfAbsent(
                                            cardData.name().toLowerCase().trim(),
                                            k -> new ArrayList<>()
                                    ).add(cardData);
                                }
                            } catch (Exception e) {
                                System.out.println("Skipping malformed card entry: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error loading card data: " + e.getMessage());
        }
    }

    /** Returns the card with this exact card_set_id, or throws if not found. */
    public CardData getCardData(String cardId) {
        CardData data = cardCache.get(cardId);
        if (data == null) {
            throw new IllegalArgumentException("Card with ID " + cardId + " not found in database.");
        }
        return data;
    }

    /**
     * Returns all cards whose name matches the given string (case-insensitive).
     * Multiple results are normal — the same character is printed across many sets.
     * Returns an empty list if no match is found.
     */
    public List<CardData> getByName(String name) {
        if (name == null || name.isBlank()) return Collections.emptyList();
        List<CardData> result = byName.get(name.toLowerCase().trim());
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    /**
     * Returns all cards whose sub_types string contains the given subtype (case-sensitive).
     *
     * Uses String.contains() rather than a prebuilt index because sub_types is a
     * space-separated string of multi-word names (e.g. "Straw Hat Crew Fish-Man Island")
     * with no reliable token boundary — tokenizing on spaces would split "Straw Hat Crew"
     * into three meaningless tokens.
     *
     * Performance: O(n) over all loaded cards. Acceptable for < 2000 card databases
     * and for game-time queries that typically operate on a player's deck (~50 cards).
     */
    public List<CardData> getBySubtype(String subtype) {
        if (subtype == null || subtype.isBlank()) return Collections.emptyList();
        return cardCache.values().stream()
                .filter(c -> c.subTypes() != null && c.subTypes().contains(subtype))
                .collect(Collectors.toList());
    }

    /** Returns an unmodifiable view of all loaded cards. */
    public Collection<CardData> getAllCards() {
        return Collections.unmodifiableCollection(cardCache.values());
    }

    public static void main(String[] args) {
        System.out.println("Card Database");
        String rawPath = "src/main/resources/raw/data/";
        CardDatabase cardDatabase = new CardDatabase(rawPath);
        System.out.println(cardDatabase.getCardData("OP03-008"));
    }

}
