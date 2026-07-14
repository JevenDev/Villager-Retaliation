# Player Raids

Player Raids turn a tracked village into a continuous siege with the player and their snapshotted party as the raiders.

## Starting a raid

Wear a helmet with an attached banner, stand inside a tracked village footprint, and begin using a goat horn. A raid cannot start during a vanilla raid, while that village or any participant is already in another Player Raid, or while a successfully defended village is on cooldown.

The initiating player's current party is snapshotted. Later party changes do not change either side. Recruited villagers whose recorded home is the target village permanently leave the party, confront the initiating player in chained forced dialogue, and defend their home. Every snapshotted defender sets every raider player's reputation to at most `-250`; values already below `-250` lose another `250`.

## Siege rules

- The red ten-segment bar fills during the default 10-second preparation period.
- Once active, it displays tracked villagers remaining. Babies, nitwits, and defectors count; iron golems do not.
- Adults other than nitwits fill empty equipment slots from the `player_raid_loadouts` datapack catalog. Equipment persists and uses normal low mob-equipment drop chances.
- Babies and nitwits seek hiding places. Capable villagers and aligned iron golems engage raiders, including when the villager's reputation tier is Feared.
- Golems arrive in batches at activation and the 75%, 50%, and 25% defender thresholds. The fixed budget is calculated once, and dead golems are not replaced.
- Raiders win when every snapshotted defender is dead or converted. Births and visiting villagers after declaration are not added.
- The village wins if no living, non-spectator raider player remains inside its footprint for the configured abandonment time (30 seconds by default).
- During the active siege, a raider player wearing a banner helmet can use a goat horn to make tracked defenders within 48 blocks glow for 3 seconds.

Operators can settle the Player Raid involving them or containing their current position with `/villagerretaliation debug raid win` or `/villagerretaliation debug raid lose`.

## Configuration

The `playerRaids` config section controls activation, preparation and abandonment ticks, defended-village cooldown days, boss-bar range, and the golem formula. `reputation.fearedThreshold` now defaults to `-1000`; the exact legacy default of `-750` migrates automatically, while custom values are preserved.

## Datapack loadouts

Place loadout catalogs at:

```text
data/villagerretaliation/player_raid_loadouts/*.json
```

Each file supports `replace` and a `loadouts` array. A loadout has a stable `id`, optional `professions` and `excluded_professions` filters, and `difficulty_pools` keyed by `peaceful`, `easy`, `normal`, and `hard`. Each pool can define `weapons`, `armor_chance`, `enchant_chance`, and weighted `armor_sets`.

```json
{
  "replace": false,
  "loadouts": [
    {
      "id": "my_pack_militia",
      "professions": ["minecraft:fletcher"],
      "difficulty_pools": {
        "normal": {
          "weapons": ["minecraft:crossbow"],
          "armor_chance": 0.75,
          "enchant_chance": 0.1,
          "armor_sets": [
            {
              "weight": 1,
              "head": "minecraft:chainmail_helmet",
              "chest": "minecraft:chainmail_chestplate",
              "legs": "minecraft:chainmail_leggings",
              "feet": "minecraft:chainmail_boots"
            }
          ]
        }
      }
    }
  ]
}
```

Profiles are checked in datapack order and the first profession match is used. Empty slots only are filled. Missing difficulty pools fall back to the first pool in that profile.

The forced-dialogue trigger name is `player_raid_betrayal`. The built-in resource exposes the `primary`, `chained`, and `turn` definition IDs, each with 15 line variations, under `data/villagerretaliation/forced_dialogue/events/player_raid_betrayal.json`.
