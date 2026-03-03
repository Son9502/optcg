package engine.cards;

import engine.cards.abilities.AbilityData;
import engine.cards.types.CardType;
import engine.cards.types.Attribute;
import engine.cards.types.Color;
import engine.cards.types.Rarity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardData(
        @JsonProperty("card_set_id") String id,
        @JsonProperty("set_id") String setId,
        @JsonProperty("card_name") String name,
        @JsonProperty("card_text") String description,
        @JsonProperty("sub_types") String subTypes,
        @JsonProperty("set_name") String setName,
        Rarity rarity,
        @JsonProperty("card_type") CardType cardType,
        Attribute attribute,
        @JsonDeserialize(using = ColorDeserializer.class)
        @JsonProperty("card_color") List<Color> colors,
        @JsonProperty("card_cost") Integer cost,
        @JsonProperty("card_power") Integer power,
        Integer life,
        @JsonProperty("counter_amount") Integer counter,

        @JsonProperty("market_price") Double marketPrice,
        @JsonProperty("abilities")    List<AbilityData> abilities) {
    public CardData {
        // Validate and set default values for optional fields
        if (description == null) {
            description = "";
        }
        if (subTypes == null || subTypes.equalsIgnoreCase("null")) {
            subTypes = "";
        }
        if (colors == null) {
            colors = List.of();
        }
        if (cost == null) {
            cost = 0;
        }
        if (power == null) {
            power = 0;
        }
        if (life == null) {
            life = 0;
        }
        if (counter == null) {
            counter = 0;
        }
        if (marketPrice == null) {
            marketPrice = 0.0;
        }
        if (abilities == null) {
            abilities = Collections.emptyList();
        }
    }

    public String getId() {
        return id;
    }

    public String toString() {
        return "CardData [id=" + id + ", name=" + name + ", cardType=" + cardType + ", colors=" + colors
                + ", cost=" + cost + ", power=" + power + ", life=" + life + ", counter=" + counter
                + ", subTypes=" + subTypes + ", marketPrice=" + marketPrice + "]";
    }
}
