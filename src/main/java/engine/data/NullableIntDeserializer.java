package engine.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Custom Jackson deserializer for Integer fields that handles:
 * - JSON null        → null
 * - string "NULL"   → null  (dirty API data)
 * - string "1"/"10" → parsed integer (API returns numbers as strings)
 * - actual integer   → passed through
 */
class NullableIntDeserializer extends StdDeserializer<Integer> {

    NullableIntDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank() || text.equalsIgnoreCase("null")
                || text.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Integer getNullValue(DeserializationContext ctx) {
        return null;
    }
}
