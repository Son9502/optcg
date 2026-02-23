package engine.cards;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import engine.cards.types.Color;

import java.io.IOException;

/**
 * Custom Jackson deserializer for the Color enum.
 * Handles space-separated multi-color strings from the API (e.g. "Red Green") by
 * returning the first valid color. Returns null for unknown or dirty values.
 */
public class ColorDeserializer extends StdDeserializer<Color> {

    public ColorDeserializer() {
        super(Color.class);
    }

    @Override
    public Color deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank()) return null;
        String first = value.trim().split(" ")[0];
        try {
            return Color.valueOf(first);
        } catch (IllegalArgumentException e) {
            return null; // dirty data (e.g. "Character" in color field)
        }
    }
}
