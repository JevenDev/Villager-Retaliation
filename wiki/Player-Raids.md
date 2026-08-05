# Player Raids

Player Raids turn a tracked village into a continuous siege with the player and their snapshotted party as the raiders.

## Starting a raid

Wear a helmet with an attached banner, stand inside a tracked village footprint, and begin using a goat horn. A raid cannot start during a vanilla raid, while that village or any participant is already in another Player Raid, or while a successfully defended village is on cooldown.

The initiating player's current party is snapshotted. Later party changes do not change either side. Recruited villagers whose recorded home is the target village permanently leave the party, confront the initiating player in chained forced dialogue, and defend their home. Every snapshotted defender sets every raider player's reputation to at most `-250`. Values already below `-250` lose another `250`.

## Siege rules

- The red ten-segment bar fills during the default 10-second preparation period.
- Once active, it displays combat-capable defenders remaining. Babies and nitwits are snapshotted separately as noncombatants. Defectors remain on the appropriate side of that split, and iron golems do not count.
- Adults other than nitwits fill empty equipment slots from the `player_raid_loadouts` datapack catalog. Equipment persists and uses normal low mob-equipment drop chances.
- Babies and nitwits seek hiding places until the armed defense is defeated. Capable villagers and aligned iron golems engage raiders, including when the villager's reputation tier is Feared.
- Golems arrive in batches at activation and the 75%, 50%, and 25% defender thresholds. The fixed budget is calculated once, and dead golems are not replaced.
- When every combat-capable defender is dead or converted, the raid enters its mercy stage. Births and visiting villagers after declaration are not added, and villagers snapshotted as babies remain mercy candidates even if they mature during the raid.
- A living raider player can empty-hand right-click each unresolved baby or nitwit and choose **Spare**, **Kill**, or **Say nothing**. Spare leaves the villager alive and sets their reputation toward every snapshotted raider player to exactly `-1000`. Kill closes the menu so the player must attack manually, and Say nothing closes it without a response. Either unresolved choice can be reconsidered later.
- Mercy candidates plead only when a raider comes within normal dialogue range. Each villager waits 30–60 seconds between pleas, and the raid allows at most one plea every five seconds.
- Raiders win after every mercy candidate has been spared, killed, or converted. The normal abandonment timer continues during mercy.
- The village wins if no living, non-spectator raider player remains inside its footprint for the configured abandonment time (30 seconds by default).
- At either outcome, each surviving recruited raider villager delivers one of 15 victory or 15 loss reactions to online raider players.
- During the active siege, a raider player wearing a banner helmet can use a goat horn to make tracked defenders within 48 blocks glow for 3 seconds. During mercy, the same signal reveals unresolved mercy candidates.

Operators can settle the Player Raid involving them or containing their current position with `/villagerretaliation debug raid win` or `/villagerretaliation debug raid lose`.

## Configuration

The `playerRaids` config section controls activation, preparation and abandonment ticks, defended-village cooldown days, boss-bar range, and the golem formula. `reputation.fearedThreshold` now defaults to `-1000`. The exact legacy default of `-750` migrates automatically, while custom values are preserved.

## Datapack loadouts

Place loadout catalogs at:

```text
data/villagerretaliation/player_raid_loadouts/*.json
```

Each file supports `replace` and a `loadouts` array. A loadout has a stable `id`, optional `professions` and `excluded_professions` filters, and `difficulty_pools` keyed by `peaceful`, `easy`, `normal`, and `hard`. Each pool can define `weapons`, `armor_chance`, `enchant_chance`, and weighted `armor_sets`.

### Minimal profession weapon pool

This smallest useful profile gives fletchers a crossbow on every difficulty. When a requested difficulty is absent, the loader uses the first pool in the profile.

```json
{
  "loadouts": [
    {
      "id": "my_pack_fletcher_crossbow",
      "professions": ["minecraft:fletcher"],
      "difficulty_pools": {
        "normal": {
          "weapons": ["minecraft:crossbow"]
        }
      }
    }
  ]
}
```

### Advanced militia armor

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

Party-villager outcome reactions use the global message keys `interaction.party.player_raid_victory` and `interaction.party.player_raid_loss`. Packs can override their line pools through normal localized dialogue message resources.

Mercy dialogue uses the global message keys under `interaction.player_raid.mercy.*`, including separate baby and nitwit plea, spared, and kill-response pools plus the three option labels. Packs can override them through normal localized dialogue message resources.
