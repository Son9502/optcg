package tools;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class CardScraper {
    private static final String all_set = "https://www.optcgapi.com/api/allSets/";
    private static final String set_base = "https://www.optcgapi.com/api/sets/";
    private static final String all_deck = "https://www.optcgapi.com/api/allDecks/";
    private static final String deck_base = "https://www.optcgapi.com/api/decks/";
    private static final String all_promo = "https://www.optcgapi.com/api/allPromos/";

    private static final String[] sets = { all_set, set_base };
    private static final String[] decks = { all_deck, deck_base };
    private static final String[] promos = { all_promo };

    public static void main(String[] args) throws Exception {
        System.out.println("Card Importer Tool");

        HashMap<String, String[]> uri_map = new HashMap<>();

        uri_map.put("sets", sets);
        uri_map.put("decks", decks);
        uri_map.put("promos", promos);

        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        final ObjectMapper mapper = new ObjectMapper();

        // Iterate through each category and import cards
        for (String key : uri_map.keySet()) {
            if (key.equals("promos")) {
                URI uri = URI.create(uri_map.get(key)[0]);

                System.out.println("Fetching " + key + " list from: " + uri);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    System.out.println("Error: Unable to fetch " + key + " list. Status code: " + statusCode);
                    continue;
                }
                System.out.println("Fetched " + key + " list successfully.");

                File outputFile = new File("src/main/resources/raw/data/promos/all_promos.json");
                if (!outputFile.getParentFile().exists()) {
                    outputFile.getParentFile().mkdirs();
                }
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputFile, mapper.readTree(response.body()));
                System.out.println("Saved " + key + " list to " + outputFile.getAbsolutePath());
                continue;
            }
            // Fetch all items in the category
            URI all_uri = URI.create(uri_map.get(key)[0]);

            System.out.println("Fetching " + key + " list from: " + all_uri);
            HttpRequest all_request = HttpRequest.newBuilder()
                    .uri(all_uri)
                    .GET()
                    .build();
            HttpResponse<String> all_response = client.send(all_request, HttpResponse.BodyHandlers.ofString());
            int statusCode = all_response.statusCode();

            if (statusCode != 200) {
                System.out.println("Error: Unable to fetch " + key + " list. Status code: " + statusCode);
                continue;
            }
            System.out.println("Fetched " + key + " list successfully.");

            // Extract IDs from response
            JsonNode rootNode = mapper.readTree(all_response.body());
            List<String> ids = new ArrayList<>();

            for (JsonNode node : rootNode) {
                String id = null;

                if (key.equals("sets")) {
                    id = node.get("set_id").asText();
                } else if (key.equals("decks")) {
                    id = node.get("structure_deck_id").asText();
                }

                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }

            String[] idsArray = ids.toArray(new String[0]);
            System.out.println("Extracted " + idsArray.length + " " + key + " IDs");

            // Fetch detailed info for each ID
            for (String id : idsArray) {
                URI detail_uri = URI.create(uri_map.get(key)[1] + id + "/");
                System.out.println("Fetching details for " + key + " ID: " + id + " from: " + detail_uri);
                HttpRequest detail_request = HttpRequest.newBuilder()
                        .uri(detail_uri)
                        .GET()
                        .build();
                HttpResponse<String> detail_response = client.send(detail_request,
                        HttpResponse.BodyHandlers.ofString());
                int detailStatusCode = detail_response.statusCode();
                if (detailStatusCode != 200) {
                    System.out.println("Error: Unable to fetch details for " + key + " ID " + id + ". Status code: "
                            + detailStatusCode);
                    continue;
                }
                System.out.println("Fetched details for " + key + " ID " + id + " successfully.");

                // Save detailed info to file
                try {
                    File outputFile = new File(
                            "src/main/resources/raw/data/" + key + "/" + id.replace("-", "").toLowerCase() + ".json");

                    if (!outputFile.getParentFile().exists()) {
                        outputFile.getParentFile().mkdirs();
                    }

                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValue(outputFile, mapper.readTree(detail_response.body()));
                    System.out.println("Saved details to " + outputFile.getAbsolutePath());
                    
                    // Optional: Sort the JSON file by card_set_id
                    JsonNode root = mapper.readTree(outputFile); // or file
                    List<JsonNode> nodes = new ArrayList<>();
                    root.forEach(nodes::add);

                    nodes.sort(Comparator.comparing(n -> {
                        JsonNode node = n.path("card_set_id");
                        return node.isMissingNode() ? "defaultValue" : node.asText();
                    }));

                    ArrayNode sorted = mapper.createArrayNode().addAll(nodes);
                    String sortedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted);
                    
                    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, sorted);
                    System.out.println("Saved sorted details to " + outputFile.getAbsolutePath());
                } catch (Exception e) {
                    System.out.println("Error saving details for " + key + " ID " + id);
                    e.printStackTrace();

                }
            }
        }
    }
}
