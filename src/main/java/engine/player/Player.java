package engine.player;
import engine.zones.Zone;
import engine.zones.ZoneType;
import engine.zones.Deck;
import engine.cards.Leader;

public class Player {
    private String player_id;
    private String name;

    private Deck deck;
    private Deck donDeck;
    private Zone hand;
    private Zone field;
    private Zone trash;
    private Deck life_zone;
    private Zone cost_zone;
    private Zone stage;

    private int activeDon;
    private int restedDon;

    private int lifePoints;
    private Leader leader;
    public Player(String player_id, String name, Leader leader) {
        this.player_id = player_id;
        this.name = name;
        this.deck = new Deck(this);
        this.donDeck = new Deck(ZoneType.DON_DECK, this);
        this.hand = new Zone(ZoneType.HAND, this);
        this.field = new Zone(ZoneType.CHARACTER, this);
        this.trash = new Zone(ZoneType.TRASH, this);
        this.life_zone = new Deck(ZoneType.LIFE, this);
        this.cost_zone = new Zone(ZoneType.COST, this);
        this.stage = new Zone(ZoneType.STAGE, this);
    
        this.leader = leader;
        this.lifePoints = (leader != null) ? leader.getLifePoints() : 0;
        this.activeDon = 0;
        this.restedDon = 0;
    }
    public Player(String name, Leader leader) {
        this("default_id", name, leader);
    }
    public Player() {
        this("default_id", "Default Player", null);
    }

    // Accessor methods
    public Deck getDeck() {
        return deck;
    }

    public Deck getDonDeck() {
        return donDeck;
    }

    public Zone getHand() {
        return hand;
    }

    public Zone getField() {
        return field;
    }

    public Zone getTrash() {
        return trash;
    }

    public Deck getLife() {
        return life_zone;
    }

    public Zone getCost() {
        return cost_zone;
    }

    public Leader getLeader() {
        return leader;
    }

    public String getPlayerId() {
        return player_id;
    }

    public String getName() {
        return name;
    }

    public int getActiveDon() {
        return activeDon;
    }

    public int getRestedDon() {
        return restedDon;
    }

    public int getLifePoints() {
        return lifePoints;
    }

    public Zone getStage() {
        return stage;
    }

    // Mutator methods
    public void setLeader(Leader leader) {
        this.leader = leader;
        this.lifePoints = leader != null ? leader.getLifePoints() : 0;
    }
}
