package engine.cards;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import engine.cards.types.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Jackson deserializer for the card_color field.
 * Handles space-separated multi-color strings from the API (e.g. "Green Yellow")
 * by returning a List of all valid Color values in order.
 * Skips tokens that don't map to a known Color (e.g. dirty data like "null").
 */
public class ColorDeserializer extends StdDeserializer<List<Color>> {

    public ColorDeserializer() {
        super(List.class);
    }

    @Override
    public List<Color> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank()) return List.of();
        List<Color> result = new ArrayList<>();
        for (String token : value.trim().split(" ")) {
            try {
                result.add(Color.valueOf(token));
            } catch (IllegalArgumentException ignored) {
                // skip unknown tokens (e.g. "null", dirty data)
            }
        }
        return List.copyOf(result);
    }
}
