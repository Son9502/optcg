package engine.cards.effects;
import engine.core.GameState;
import engine.player.Player;
public interface Effect {
    void apply(GameState gameState, Player source);
    
}
