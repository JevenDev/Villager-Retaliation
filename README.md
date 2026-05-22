<div align="center">

<h2><strong>Villagers remember, villagers fight back.</strong></h2>

</div>

<div align="center">

<a href="https://modrinth.com/mod/Villager-Retaliation/settings/versions?l=neoforge"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/cozy/supported/neoforge_64h.png" alt="Available for NeoForge"></a>
<a href="https://modrinth.com/mod/toucan"><img src="https://raw.githubusercontent.com/JevenDev/toucanLib/refs/heads/1.21.1/docs/badges/toucanlib_toucanlib_cozy_64h.png" alt="Requires toucanLib"></a>
<br>
<a href="https://modrinth.com/mod/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/modrinth_46h.png" alt="Available on Modrinth"></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/villager-retaliation" target="_blank" rel="nofollow"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/curseforge_46h.png" alt="Available on CurseForge"></a>
<a href="https://github.com/JevenDev/Villager-Retaliation" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/github_46h.png" alt="Available on GitHub"></a>

</div>

![villagers chasing a player](https://cdn.modrinth.com/data/cached_images/16269e99f4ef7ac15b6d24f3b523e5fa5778d5f5.png)

Villager Retaliation! is a Vanilla+ NeoForge mod that makes villagers and wandering traders less helpless without turning villages into warzones.

Adult villagers can defend themselves, react to crimes they witness, use profession-based combat roles, and remember how each player treats them through a personal reputation system.

- Attack a villager and that villager can fight back
- Kill a villager in public and nearby villagers may rally
- Player-placed lava and fire can count as aggression for a short attribution window
- Build trust through trade and positive village defense
- Lose trust through attacks, witnessed crimes, and village harm
- Make villagers suspicious, hostile, despised, or feared if you push things too far
- Keep babies defenseless and nitwits cowardly as intended

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

![villagers carrying profession based weapons](https://cdn.modrinth.com/data/cached_images/44e64c624e3bad762345ee5f8df5d91c5ef5f6cd.png)

## Main Features

### Retaliation

Villagers are neutral instead of purely passive.

By default, hitting a villager only angers that villager. Killing an adult villager can anger nearby adult villagers if they can witness it. Anger expires after a configurable duration, and creative/spectator players can be ignored.

Villagers and wandering traders can also block trading while hostile. Hostile villagers can be pacified with datapack-defined item payments, unless your reputation has fallen too low.

### Reputation

Reputation is tracked per villager and per player.

That means one villager can trust you while another despises you. Reputation can affect trade prices, pacification, anger duration, fleeing, despised attack-on-sight behavior, cleric support, and advancement progression.

Default tiers:

| Tier | Default threshold |
| --- | ---: |
| Royalty | 750 |
| Revered | 400 |
| Respected | 250 |
| Trusted | 75 |
| Neutral | 0 |
| Suspicious | -75 |
| Hostile | -100 |
| Despised | -250 |
| Feared | -750 |

Reputation changes are configurable. By default, direct villager hits are punished more than witnessed hits, villager kills are punished heavily, and player-attributed lava/fire damage counts for half the normal reputation penalty.

![villagers retaliating against a player that killed another villager](https://i.imgur.com/6c3vdac.gif)

### Profession Combat

Villagers fight in ways that match their profession.

| Profession | Behavior |
| --- | --- |
| Weaponsmith | Uses stronger melee behavior with swords |
| Armorer | Can fight defensively, gain resistance, and use shields in hard mode |
| Toolsmith | Uses tool-based melee |
| Mason | Uses mining-tool style melee |
| Butcher | Uses axe-based melee |
| Fletcher | Uses bows or crossbows |
| Farmer | Can defend themselves and heal with bread |
| Cleric | Uses potions for defense, attacks, and ally support |
| Librarian | Can fight with book-themed |
| Nitwit | Usually flees unless holding a usable weapon |

Fletchers and other villagers holding ranged weapons can use bows, crossbows, and tridents. Crossbows use charge/hold/fire states, and tridents use a thrown attack.

### Cleric Support

Clerics have the most advanced combat role.

They can drink defensive potions, throw harmful or slowing splash potions, avoid friendly splash damage where possible, heal injured villagers and wandering traders, and support trusted-or-better players.

Passive cleric healing has configurable range, health threshold, and line-of-sight rules.

### Hazard Attribution

Player-placed hazards can count as aggression.

If a player places lava, uses flint and steel, or uses a fire charge, nearby lava/fire damage can be attributed to that player for a short window. This allows "accidental" environmental attacks to still matter for retaliation, reputation, and advancements.

The default attribution window is 2 real-time minutes. Natural lava, old hazards, worldgen hazards, and untracked hazards are not meant to count.

![A player using lava to kill a villager, and being attributed to the kill](https://i.imgur.com/GSgbQr8.gif)

## Reputation Behavior

Higher trust can reduce anger duration, improve trade pricing, and unlock helpful cleric support.

Lower trust can make villagers more dangerous or less willing to deal with you:

- Suspicious villagers apply negative trade pressure
- Hostile villagers can harass you with eggs or poisonous potatoes
- Despised villagers can attack on sight when enabled
- Feared villagers visibly react when you get close
- Despised or feared villagers may refuse pacification payments
- Babies, nitwits, and non-combat villagers can flee from hated players

## Loot

Villager Retaliation! adds configurable drops for villagers and wandering traders.

- Villagers can drop emeralds and bread
- Adult villagers can roll profession-themed loot
- Combat weapons can drop at configurable rates
- Baby villager loot is disabled by default
- Wandering traders can drop emeralds, invisibility potions, and safe copies of current trade results

Profession loot generally requires player kill credit by default.

## Advancements

The mod includes a full reputation advancement tab.

<details>
<summary><strong>All Advancements (Click to Expand)</strong></summary>

<br>

| Advancement | Type | Criteria | Hidden |
| --- | --- | --- | --- |
| Village Relations | Task (Tab Root) | Automatically granted when any Villager Retaliation advancement is awarded. | No |
| Commonfolk | Task | Interact with any villager, or enter a village. | No |
| I'm Sorry! | Task | Pacify a hostile villager with a payment item. | No |
| A Familiar Face | Task | Reach Trusted reputation with any villager. | No |
| Respect Is Earned | Task | Reach Respected reputation with any villager. | No |
| Friend of the Village | Goal | Reach Trusted with 5 villagers in one village area. | No |
| Local Legend | Challenge | Reach Revered reputation with any villager. | No |
| Crowned by the Village | Challenge | Reach Royalty reputation with any villager. | No |
| Second Chance | Goal | Cure a zombie villager that retains known reputation data for you. | No |
| The Village Remembers | Goal | Move from Suspicious or lower back to Neutral or higher. | No |
| Bad First Impression | Task | Reach Suspicious reputation with any villager. | No |
| Hands Off | Task | Damage a villager. | No |
| The Village Has Eyes | Goal | Harm or kill a villager while at least 3 adult villagers can witness it. | No |
| Marked | Challenge | Reach Feared reputation with any villager. | No |
| Village Enemy | Challenge | Have 5 or more villagers targeting you at the same time. | No |
| Mob Justice | Challenge | Have 8 or more villagers targeting you at the same time. | No |
| Regular Customer | Goal | Complete 10 trades with the same villager. | No |
| Community Support | Goal | Trade with 5 different villagers in one village area. | No |
| Price of Trust | Goal | Reach a positive trust tier with a villager after trading with them. | No |
| Refused Service | Task | Attempt to trade with a villager that blocks interaction due to low reputation. | No |
| Hero, Not Menace | Goal | While distrusted by nearby villagers, gain positive reputation by defending against hostiles. | No |
| An Unwise Decision | Goal | Damage an iron golem associated with a village. | No |
| Peace Offering | Challenge | After being Hostile or worse with a villager, return to Neutral or higher with that villager. | Yes |
| Accidentally, Of Course | Challenge | A villager dies from a player-attributed environmental hazard without direct player damage. | Yes |

</details>

![keybinds](https://cdn.modrinth.com/data/cached_images/201d5ce49ba16974e3c3b0b562c392e03f38e35f.png)

## Commands

Requires operator.

```mcfunction
/villagerretaliation setNearbyReputation <integer>
/villagerretaliation setNearestRelationship <crush|dating|engaged|married|separated>
```

This sets nearby villagers' and wandering traders' reputation toward the executing player. It is mainly useful for testing tiers, trade pricing, despised behavior, feared behavior, pacification, and the debug overlay.

`setNearestRelationship` is a relationship-system debug command. It applies the chosen romantic stage to the two nearest adult villagers, respecting the social graph's adult, alive, close-family, and exclusive-partner validation rules.

Example:

```mcfunction
/villagerretaliation setNearbyReputation -150
```

Debug-only family testing items are available through commands:

```mcfunction
/give @s villagerretaliation:villager_breeding_stick
/give @s villagerretaliation:villager_maturity_emerald
```

Right-click two adult villagers with the breeding stick to create a biological baby immediately, bypassing vanilla breeding cooldown. Biological births require opposite-gender parents and still respect close-family checks. Sneak-right-click the second adult instead to select any valid adult pair for adoption, then right-click an orphan baby villager. Same-gender adult pairs automatically enter adoption mode instead of creating a biological baby. Right-click a baby villager with the maturity emerald to make them an adult immediately.

## Configuration

Main config file:

- Singleplayer/client: `config/villagerretaliation-common.toml`
- Dedicated server: `<server root>/config/villagerretaliation-common.toml`

Config categories include:

- `general` - master feature toggles
- `balance` - loot and drop rates
- `retaliation` - anger rules, aggro radius, duration, line-of-sight witnesses
- `reputation` - penalties, gains, thresholds, gossip, trade pricing
- `combat` - profession combat toggles, hostile mob targeting/retaliation, weapon pickup, armorer shields, clerics, farmers
- `debugOverlay` - optional reputation display for testing
- `wanderer` - wandering trader drop behavior

Pack creators can tune monster-defense behavior independently with `combat.villagersTargetHostileMobs`, `combat.villagersRetaliateAgainstHostileMobs`, `combat.villagersStandGroundAgainstHostileMobs`, `combat.villagersPickUpGroundWeapons`, and the equivalent wandering trader options. `combat.naturalHostileTargetRadius` still controls the scan distance when proactive hostile mob targeting is enabled.

## Debug Overlay

There is an optional client-side debug overlay for testing reputation.

When enabled, it can show the villager's reputation tier and/or exact reputation value above their head. Optional health and armor lines can be shown under the reputation line. It can be limited by distance, sneaking, or advanced tooltips.

This is disabled by default and is mainly intended for testing and balancing.

## Data-driven dialogue

Villager dialogue is loaded from datapack JSON under:

```text
data/villagerretaliation/dialogue/en_us/
```

The built-in files live in `global.json`, `professions/<profession>.json`, and optional nested files such as `professions/<profession>/share_stories.json`. Packs can add or replace files in the same namespace to add new dialogue, tune weights, add profession-specific lines, or expose new talk choices. Files directly under `professions/<profession>.json` and nested under `professions/<profession>/` automatically apply to that profession unless an entry provides its own `professions` filter.

Dialogue uses the player's client language when the server knows it. Files in `dialogue/<locale>/` are layered over `dialogue/en_us/`, so translated packs can provide only the entries they need to replace. Matching `id` values replace fallback entries; entries without explicit ids use stable generated ids based on file path and order.

For example, a pack can define the English fallback:

```text
data/villagerretaliation/dialogue/en_us/global.json
```

```json
{
  "messages": [
    {
      "id": "gift.loved.diamond",
      "key": "gift_response.global.loved",
      "text": "You brought me something precious."
    }
  ]
}
```

Then provide only the translated replacement for French:

```text
data/villagerretaliation/dialogue/fr_fr/global.json
```

```json
{
  "messages": [
    {
      "id": "gift.loved.diamond",
      "key": "gift_response.global.loved",
      "text": "Vous m'avez apporte quelque chose de precieux."
    }
  ]
}
```

Players using `fr_fr` see the French text. Players using any locale without a matching override fall back to `en_us`.

Dialogue choices are declared with an `options` array:

```json
{
  "options": [
    {
      "id": "ask_about_raids",
      "label": "Ask About Raids",
      "type": "story",
      "order": 20,
      "show_for_babies": false
    }
  ],
  "lines": [
    {
      "id": "raid_warning_story",
      "option": "ask_about_raids",
      "type": "story",
      "text": "When the bell rings like that, everyone learns how fast fear can run.",
      "event_tags": ["raid"],
      "weight": 30
    }
  ]
}
```

`type` controls the existing dialogue behavior and reputation handling: `chat`, `greeting`, `question`, `gift_preferences`, `gift_advice_followup`, `map_report`, `combat_survival_report`, `gear_report`, `recruitment_followup`, `apology`, `village_defense_report`, `story`, `share_story`, `joke`, or `insult`. `option` or `option_ids` binds a line to a custom choice. Lines can also filter by `professions`, `dispositions`, `weather`, `times`, `event_tags`, `player_event_tags`, `show_for_adults`, `show_for_babies`, `requires_recent_broken_bed_memory`, `requires_recent_direct_hit_memory`, `requires_gear_report_used_in_combat`, `requires_gear_report_unused_in_combat`, `recruitment_followup_scenarios`, `requires_recruitment_memory`, `recruitment_memory_scenarios`, `min_recruitment_follow_distance`, `requires_recruitment_boat_trip`, `requires_recruitment_ocean_crossing`, `requires_recruitment_swim_trip`, `excludes_recruitment_ocean_crossing`, `requires_known_family`, `requires_known_parent`, `requires_known_sibling`, `requires_known_spouse`, `requires_known_child`, `requires_known_grandparent`, `requires_known_grandchild`, `requires_known_descendant`, `requires_known_aunt_uncle`, `requires_known_cousin`, `requires_known_niece_nephew`, `requires_known_extended_family`, `requires_known_deceased_family`, and `first_conversation_only`. Opening and closing lines also support `first_conversation_only` and `first_village_interaction_only`. Recruitment memory chat lines can use `{follow_biome}` and `{follow_distance}` placeholders. Family-aware lines can use `{parent}`, `{sibling}`, `{spouse}`, `{child}`, `{grandparent}`, `{ancestor}`, `{grandchild}`, `{descendant}`, `{aunt_uncle}`, `{cousin}`, `{niece_nephew}`, `{deceased_family}`, `{extended_relative}`, `{relative}`, and their `_possessive` variants. Shared story lines can use `{target}` and `{target_article}` placeholders, and can filter to specific discovered structures or biomes with `story_structure`, `story_structures`, `story_biome`, or `story_biomes`. Options can set `requires_unreported_cartographer_map_discovery` to appear only after the player finds a cartographer dialogue map target and before they report it, `requires_unreported_gift_advice_result` to appear after the player tests that villager's gift advice on another villager and before the result is discussed, `requires_unreported_combat_survival_report` to appear after a followed villager or nearby fighting villager survives a raid/night hostile encounter and before that survival is acknowledged, `requires_unreported_gear_report` to appear after the player gives that villager armor or a usable weapon and before they ask how it is working, `requires_unreported_recruitment_followup` to appear after a follower is dismissed safely near the village, dismissed injured near the village, or betrayed by the player and before that outcome is discussed, `requires_unapologized_remembered_harm` to appear after the player hits that villager, breaks their bed, or is recently caught harming a nearby villager and before they apologize, `requires_unreported_village_defense` to appear for nearby villagers after the player kills raiders during an active raid and before that defense is discussed, any `requires_known_*` family filter to appear only when the social graph knows that relationship, or `requires_shareable_story` to appear only after the player has discovered a configured structure or biome near that villager. Higher `weight` values are picked more often.

Structures that unlock `share_story` dialogue are loaded from datapack JSON under:

```text
data/villagerretaliation/story_structures/
```

Biomes that unlock `share_story` dialogue are loaded from datapack JSON under:

```text
data/villagerretaliation/story_biomes/
```

Each file can define one entry or an `entries` array. `structure` accepts any vanilla or modded structure id, and `biome` accepts any vanilla or modded biome id. `name` is optional; if omitted, the id path is converted into a readable name. Structure `radius` controls how close the player must be to the structure before nearby villagers can receive a story to share. The built-in pack includes every vanilla 1.21.1 biome and structure id, with at least five unique `share_story` responses per villager profession for each one.

```json
{
  "radius": 96,
  "entries": [
    {
      "structure": "minecraft:ancient_city",
      "name": "Ancient City",
      "radius": 128
    },
    {
      "structure": "examplemod:haunted_keep",
      "name": "Haunted Keep"
    }
  ]
}
```

Biome entries use the same shape:

```json
{
  "entries": [
    {
      "biome": "minecraft:deep_dark",
      "name": "Deep Dark"
    },
    {
      "biome": "examplemod:crystal_marsh",
      "name": "Crystal Marsh"
    }
  ]
}
```

When the player discovers one of these structures or biomes near villagers, those villagers can show the built-in `Share a Story` option until the story is told. Babies use their own baby-only `share_story` dialogue lines when available.

Structure-specific shared-story lines can filter with `story_structure`:

```json
{
  "lines": [
    {
      "id": "librarian_share_story_haunted_keep",
      "type": "share_story",
      "option": "adult_share_story",
      "story_structure": "examplemod:haunted_keep",
      "text": "{target_article}. I will write that under warnings, not wonders.",
      "weight": 24
    },
    {
      "id": "farmer_share_story_crystal_marsh",
      "type": "share_story",
      "option": "adult_share_story",
      "story_biome": "examplemod:crystal_marsh",
      "text": "{target_article}? Then the fields should know which road not to trust.",
      "weight": 24
    }
  ]
}
```

Large dialogue sets can be split into nested profession files for readability:

```text
data/villagerretaliation/dialogue/en_us/professions/fisherman/share_stories.json
```

Lines in that file automatically default to the `fisherman` profession, so they do not need to repeat `"professions": ["fisherman"]` unless a pack intentionally wants a different filter.

Gift advice is not always reliable. Villagers with low personal reputation toward the player can mislead them by recommending a gift that the target profession actually dislikes, and neutral or lightly trusted villagers can occasionally be wrong. Bad advice is not added to the known gift lists until the player tests it; returning to the recommender afterward can reveal a `gift_advice_followup` response.

One-off villager replies are declared with `messages`:

```json
{
  "messages": [
    {
      "key": "sleep.broken_bed",
      "text": "That was my bed. I will remember this.",
      "weight": 20
    },
    {
      "key": "gift_response.global.liked",
      "text": "{gift_item} is a welcome gift."
    }
  ]
}
```

Messages cover sleeping interruptions, refusals, repeated-dialogue reactions, gift memories, gift acceptance responses, follower betrayal, and generated story hints. Message entries support the same `professions`, `dispositions`, `show_for_adults`, `show_for_babies`, and `weight` fields as other dialogue pools.

## Data-driven notifications

Villager HUD notifications and ambient world-text indicators are loaded from datapack JSON under:

```text
data/villagerretaliation/notifications/en_us/
```

Each entry binds text and color to a trigger emitted by code. Dialogue can trigger notifications, and world events can trigger them too, as long as the event has a trigger ID exposed by the mod.

```json
{
  "notifications": [
    {
      "trigger": "gift.liked",
      "text": "Good gift: {item}",
      "kind": "gift_liked",
      "color": "#55FF55"
    },
    {
      "trigger": "ambient.murmur",
      "text": "War changes every trade route.",
      "world_text_kind": "murmur",
      "color": "gold",
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "weight": 20
    }
  ]
}
```

`color`, `text_color`, and `chat_color` accept common color names or hex values such as `#FFD166`. HUD entries can set `kind` for existing notification behavior. World-text entries can set `world_text_kind`: `alert`, `murmur`, `positive`, `negative`, `trade`, `dialogue`, or `sleep`.

Built-in triggers include `gift.liked`, `gift.neutral`, `gift.disliked`, `gift.received_item`, recruitment triggers such as `recruitment.follow_start`, reputation tier triggers such as `reputation.tier.trusted.improved`, dialogue triggers such as `dialogue.question`, ambient triggers such as `ambient.murmur`, sleep triggers, trade triggers, and villager alert triggers. Entries can filter by `professions`, `reputation_levels`, `min_reputation`, `max_reputation`, `show_for_adults`, `show_for_babies`, `chance`, and `weight`.

Notification text also follows the player's client language. Files in `notifications/<locale>/` overlay `notifications/en_us/` the same way dialogue files do.

For notification text, use the same `id` overlay pattern:

```text
data/villagerretaliation/notifications/en_us/global.json
data/villagerretaliation/notifications/fr_fr/global.json
```

```json
{
  "notifications": [
    {
      "id": "notification.gift.liked",
      "trigger": "gift.liked",
      "text": "Good gift: {item}",
      "color": "#55FF55"
    }
  ]
}
```

The French file can include only that same notification `id` with translated `text`; the trigger, kind, color, and filters can stay in the fallback file unless the translation pack intentionally wants to replace them too.

## Data-driven gifts

Gift reactions and high-reputation reward items are loaded from datapack JSON under:

```text
data/villagerretaliation/gifts/
```

Gift rules are not locale-specific because they describe item behavior rather than display text. The villager lines and HUD text that mention gifts still come from the localized dialogue and notification resources.

Gift preference entries map items or item tags to a reaction. Profession-specific entries override global entries when both match the same item.

```json
{
  "preferences": [
    {
      "reaction": "liked",
      "items": ["minecraft:bread", "minecraft:apple"]
    },
    {
      "professions": ["farmer"],
      "reaction": "loved",
      "items": ["minecraft:wheat", "#minecraft:villager_plantable_seeds"],
      "reputation_per_item": 6
    }
  ]
}
```

Valid reactions are `loved`, `liked`, `neutral`, `disliked`, and `hated`. Each reaction has a default per-item reputation value, but `reputation_per_item` can override it for a specific entry.

Gifted items are stored in the villager's inventory. Trusted-or-better villagers may keep a loved or liked gift as a visible keepsake when they have an empty hand, armor slot, or offhand slot for it.

High-reputation reward entries decide what trusted villagers can give back:

```json
{
  "rewards": [
    {
      "professions": ["farmer"],
      "reputation_levels": ["revered", "royalty"],
      "item": "minecraft:golden_carrot",
      "min_count": 2,
      "max_count": 5,
      "weight": 10
    }
  ]
}
```

If any profession-specific reward matches, the generic rewards are ignored for that roll. Packs can add files for extra entries or override `gifts/default.json` to replace the built-in table.

![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

Villager Retaliation! is designed to work with vanilla villager systems rather than replacing villager entities.

- Built for NeoForge 1.21.1
- Uses vanilla-style entity events and AI memory adjustments
- Uses vanilla gossip integration where enabled
- Uses NeoForge item tags for weapon detection where possible
- Does not replace the villager entity type

Compatibility may vary with mods that heavily replace villager AI, trading, combat, or entity classes.

### Resource Pack Textures

Villagers and wandering traders use vanilla texture paths while idle, trading, or otherwise using the vanilla crossed-arms model. When they enter a combat posture, they use Villager Retaliation's combat model and a matching combat texture path.

Resource packs that change villagers should include both the vanilla texture and the combat-state texture:

| Entity | Normal texture | Combat texture |
| --- | --- | --- |
| Villager | `assets/minecraft/textures/entity/villager/villager.png` | `assets/villagerretaliation/textures/entity/villager/villager.png` |
| Wandering Trader | `assets/minecraft/textures/entity/wandering_trader.png` | `assets/villagerretaliation/textures/entity/wandering_trader/wandering_trader.png` |

The combat texture is separate because the combat model has independent animated arms for weapons, shields, bows, potions, and throwing poses. A vanilla villager texture alone does not contain enough arm layout information to support every combat pose cleanly.

### Resource Pack Combat Model

Resource packs can also override the combat model geometry at:

```text
assets/villagerretaliation/models/entity/villager/combat_villager.json
```

The model uses a small JSON format with `texture_width`, `texture_height`, and recursive `parts`. Each part can define `name`, `pivot`, `rotation` in degrees, `cubes`, and `children`. Cubes use `uv`, `origin`, `size`, optional `inflate`, and optional `mirror`.

The animation system requires these part names to remain present: `body`, `head`, `RightArm`, `LeftArm`, `RightLeg`, and `LeftLeg`. Decorative children such as `nose`, `helmet`, and `brim` may be changed or removed, so packs can do things like remove the villager nose or shorten the head without code changes.

By default, idle villagers still use Minecraft's vanilla crossed-arms model. Packs that want their normal, non-combat villagers to use a custom arms-at-side model can opt in with:

```text
assets/villagerretaliation/models/entity/villager/render_options.json
```

```json
{
  "non_combat_model": "custom"
}
```

Then provide the non-combat model at:

```text
assets/villagerretaliation/models/entity/villager/non_combat_villager.json
```

Use `"non_combat_model": "vanilla"` or omit `render_options.json` to keep the vanilla crossed-arms model.

If Entity Model Features (EMF) is installed, Villager Retaliation exposes its combat model through Minecraft's normal entity model layer instead. This lets EMF packs use the standard OptiFine CEM workflow and export a starter `.jem` from EMF's in-game model export tools. Entity Texture Features (ETF) is still optional and useful for random or emissive entity texture packs, but it is not required for the combat model override above.

## Version and Loaders

- NeoForge 1.21.1 - active development
- Forge - not planned anytime soon, don't ask please :)
- Fabric - possible future port, not anytime soon
- Older Minecraft versions - not planned
- Eventually, will skip straight to 26.1/whatever the newest standard will be for modding.

![credits & license](https://cdn.modrinth.com/data/cached_images/5fd3ad80e342e6985dd6ebda1f7afd9c48749fce.png)

## Modpacks

You may use this mod in modpacks, videos, servers, and other projects. A link back to the Modrinth page is appreciated.

## Credits

Created by me :D

## License

All Rights Reserved.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

Please don't port this mod without express permission from me.

For any general queries/unlisted questions, DM me on Twitter (@prodbyjvn) / Discord (ijvn).

<div align="center">

  <p><strong>⚠ <em>This mod ONLY exists on Modrinth & CurseForge as of May 2026. Any sites hosting this mod outside of Modrinth/CurseForge are not official releases.</em> ⚠</strong></p>

</div>
