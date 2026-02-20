package engine.data;

import java.io.File;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;

import engine.cards.CardData;

import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;

public class CardDatabase {

    private final Map<String, CardData> cardCache = new HashMap<>();
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

    // Method to look up and return card information by card ID
    public CardData getCardData(String cardId) {
        CardData data = cardCache.get(cardId);
        if (data == null) {
            throw new IllegalArgumentException("Card with ID " + cardId + " not found in database.");
        }
        return data;
    }

    public static void main(String[] args) {
        System.out.println("Card Database");
        String rawPath = "src/main/resources/raw/data/";
        CardDatabase cardDatabase = new CardDatabase(rawPath);
        System.out.println(cardDatabase.getCardData("OP03-008"));
    }

}
