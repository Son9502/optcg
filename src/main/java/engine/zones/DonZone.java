package engine.zones;

import java.util.ArrayList;

import engine.cards.DonCard;
import engine.player.Player;
import engine.cards.Card;

public class DonZone extends Zone {
    public DonZone(Player owner) {
        super(ZoneType.COST, owner);
        this.maxSize = 10; // Set the maximum size for the Don Zone
    }

    public int getRested() {
        int count = 0;
        for (Card card : cards) {
            if (card instanceof DonCard donCard && donCard.isRested() && !donCard.isAttached()) {
                count++;
            }
        }
        return count;
    }

    public int getActive() {
        int count = 0;
        for (Card card : cards) {
            if (card instanceof DonCard donCard && !donCard.isRested() && !donCard.isAttached()) {
                count++;
            }
        }
        return count;
    }

    public int getAttached() {
        int count = 0;
        for (Card card : cards) {
            if (card instanceof DonCard donCard && donCard.isAttached()) {
                count++;
            }
        }
        return count;
    }
    public int getTotalCount() {
        return cards.size();
    }
}
