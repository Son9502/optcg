# optcg

Possible File Architecture: 
```text
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── engine/
│   │   │   │   ├── core/
│   │   │   │   │   ├── GameEngine
│   │   │   │   │   ├── GameState
│   │   │   │   │   ├── Phase
│   │   │   │   │   └── TurnManager
│   │   │   │   │
│   │   │   │   ├── player/
│   │   │   │   │   ├── Player
│   │   │   │   │   ├── Leader
│   │   │   │   │   └── DonManager
│   │   │   │   │
│   │   │   │   ├── cards/
│   │   │   │   │   ├── Card
│   │   │   │   │   ├── CardData
│   │   │   │   │   ├── CardFactory
│   │   │   │   │   ├── types/
│   │   │   │   │   │   ├── Character
│   │   │   │   │   │   ├── Event
│   │   │   │   │   │   ├── Stage
│   │   │   │   │   │   └── DonCard
│   │   │   │   │   │
│   │   │   │   │   ├── abilities/
│   │   │   │   │   │   ├── Ability
│   │   │   │   │   │   ├── TriggerType
│   │   │   │   │   │   └── Condition
│   │   │   │   │   │
│   │   │   │   │   └── effects/
│   │   │   │   │       ├── Effect
│   │   │   │   │       ├── DamageEffect
│   │   │   │   │       ├── DrawEffect
│   │   │   │   │       ├── RestEffect
│   │   │   │   │       ├── KOEffect
│   │   │   │   │       └── etc...
│   │   │   │   │
│   │   │   │   ├── zones/
│   │   │   │   │   ├── Zone
│   │   │   │   │   ├── Deck
│   │   │   │   │   └── ZoneType
│   │   │   │   │
│   │   │   │   ├── battle/
│   │   │   │   │   └── BattleSystem
│   │   │   │   │
│   │   │   │   ├── events/
│   │   │   │   │   ├── EventBus
│   │   │   │   │   ├── Event
│   │   │   │   │   └── EventType
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── RNG
│   │   │   │       └── IDGenerator
│   │   │   │
│   │   │   ├── tools/
│   │   │   │   ├── scraper/
│   │   │   │   │   └──  CardScraper.java
│   │   │   ├── cli/ or ui/ (optional)
│   │   ├── resources/
│   │   │   ├── raw/
│   │   │   │   ├── data/
│   │   │   │   │   ├── cards/
│   │   │   │   │   │   ├── op01.json
│   │   │   │   │   │   ├── op02.json
│   │   │   │   │   │   └── etc...
│   │   │   │   │   └── decks/
│   │   │   ├── compiled/
│   │   │   │   ├── data/
│   │   │   │   │   ├── cards/
│   │   │   │   │   │   ├── op01.json
│   │   │   │   │   │   ├── op02.json
│   │   │   │   │   │   └── etc...
│   │   │   │   └── decks/
│   ├── test/
│   │   ├── java/
│   │   │   ├── engine/
```
