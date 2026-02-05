package engine.cards;

import engine.cards.types.CardType;
import engine.cards.types.Attribute;
import engine.cards.types.Color;
import engine.cards.types.Rarity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardData(
        @JsonProperty("card_set_id") String id,
        @JsonProperty("set_id") String setId,
        @JsonProperty("card_name") String name,
        @JsonProperty("card_text") String description,
        @JsonProperty("set_name") String setName,
        Rarity rarity,
        @JsonProperty("card_type") CardType cardType,
        Attribute attribute,
        @JsonProperty("card_color") Color color,
        @JsonProperty("card_cost") Integer cost,
        @JsonProperty("card_power") Integer power,
        Integer life,
        @JsonProperty("counter_amount") Integer counter,
        @JsonProperty("market_price") Double marketPrice) {
    public CardData {
        // Validate and set default values for optional fields
        if (description == null) {
            description = "";
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
    }
    public String getId() {
        return id;
    }
    public String toString() {
        return "CardData [id=" + id + ", setId=" + setId + ", name=" + name + ", description=" + description
                + ", setName=" + setName + ", rarity=" + rarity + ", cardType=" + cardType + ", attribute="
                + attribute + ", color=" + color + ", cost=" + cost + ", power=" + power + ", life=" + life
                + ", counter=" + counter + ", marketPrice=" + marketPrice + "]";
    }
}
