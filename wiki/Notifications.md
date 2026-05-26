# Notifications JSON

Notifications JSON controls short HUD messages and ambient world text above villagers.

## Paths

Notification files must be in the `villagerretaliation` namespace and include a locale:

```text
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
data/villagerretaliation/notifications/fr_fr/my_pack_notifications.json
```

The mod loads `en_us` first, then overlays the player's locale. Matching `id` values replace earlier definitions.

Use a unique file name for addon notifications. A datapack file at `data/villagerretaliation/notifications/en_us/global.json` replaces the mod's built-in `global.json`, which can hide default notification text. Only use that exact path when you intentionally want a full-file override.

Notification files translate HUD notification text and ambient world text. They do not translate the interaction GUI, generated relationship/family labels, reputation overlay labels, or villager chat tag labels. Put those strings in a resource-pack language file; see [Localization Guide](Localization.md).

## Minimal Notification

```json
{
  "notifications": [
    {
      "id": "my_pack.gift.liked",
      "trigger": "gift.liked",
      "text": "Good gift: {item}",
      "kind": "gift_liked",
      "color": "green"
    }
  ]
}
```

## Text And Line Variations

Notification entries can use either `text` for one output or `lines` for several equal variations. The entry passes its filters and `chance`, then weighted selection chooses an entry, then one line variation is selected at random.

```json
{
  "notifications": [
    {
      "id": "my_pack.baby_hit_alert",
      "trigger": "alert.player_attacked_villager",
      "lines": [
        "Ow!",
        "Stop!",
        "Help!"
      ],
      "world_text_kind": "alert",
      "show_for_adults": false,
      "show_for_babies": true,
      "weight": 30
    }
  ]
}
```

Use `text` for single-line entries. Use `lines` when several entries would otherwise share the same trigger, filters, chance, and style.

## Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable id for translations and overrides. |
| `trigger` | string | required | Event trigger emitted by the mod. |
| `text` | string | required unless `lines` is set | HUD/world text. Supports trigger-specific placeholders. |
| `lines` | array | required unless `text` is set | Alternate HUD/world texts. One is selected at random after this entry wins weighted selection. |
| `kind` | enum | `default` | HUD notification category. |
| `world_text_kind` | enum | `dialogue` | Style used for world text. |
| `style` | enum | `dialogue` | Alias for `world_text_kind`. |
| `color` | color | default white | Sets text and chat color unless more specific colors are provided. |
| `text_color` | color | `color` | On-screen text color. |
| `chat_color` | color | `text_color` | Chat/log color where used. |
| `professions` | string or array | any | Profession filter. |
| `requires_villager_unarmed` | boolean | `false` | Requires the notification villager to have no usable weapon in either hand. `villager_unarmed` is also accepted as an alias. |
| `requires_villager_armed` | boolean | `false` | Requires the notification villager to have a usable weapon in either hand. `villager_armed` is also accepted as an alias. |
| `reputation_levels` | string or array | any | Reputation tier filter. |
| `target_entity_types` | string or array | any | Retaliation target entity ids such as `minecraft:player` or `minecraft:zombie`. |
| `target_entities` | string or array | any | Alias for `target_entity_types`. |
| `min_reputation` | integer | none | Minimum exact reputation. |
| `max_reputation` | integer | none | Maximum exact reputation. |
| `player_items` | string or array | none | Requires the player to have one matching item or item tag. Prefix tags with `#`. |
| `player_item_slots` | string or array | `hands` when `player_items` is set | Slots to check: `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `equipment`, or `any`. |
| `min_player_item_durability` | integer | none | Minimum remaining durability on the matched player item. Alias: `min_held_item_durability`. |
| `max_player_item_durability` | integer | none | Maximum remaining durability on the matched player item. Alias: `max_held_item_durability`. |
| `min_player_item_durability_percent` | integer | none | Minimum remaining durability percent on the matched player item. Alias: `min_held_item_durability_percent`. |
| `max_player_item_durability_percent` | integer | none | Maximum remaining durability percent on the matched player item. Alias: `max_held_item_durability_percent`. |
| `player_item_enchantment` | string | none | Requires the matched player item to have this enchantment. Alias: `held_item_enchantment`. |
| `player_item_enchantments` | string, object, or array | none | Requires one matching enchantment. String entries use top-level level filters; object entries can use `id`, `min_level`, and `max_level`. Alias: `held_item_enchantments`. |
| `min_player_item_enchantment_level` | integer | none | Minimum level for string enchantment filters. Alias: `min_held_item_enchantment_level`. |
| `max_player_item_enchantment_level` | integer | none | Maximum level for string enchantment filters. Alias: `max_held_item_enchantment_level`. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true` | Baby visibility. |
| `weight` | integer | `10` | Weighted selection. |
| `chance` | number | `1.0` | Random chance from `0.0` to `1.0`. |

## HUD Kinds

Use these values in `kind`:

```text
default
map_discovery
received_item
gift_liked
gift_neutral
gift_disliked
villager_following
villager_dismissed
villager_hired
villager_fired
villager_death
```

## World Text Kinds

Use these values in `world_text_kind`:

```text
alert
murmur
positive
negative
trade
dialogue
sleep
```

## Built-In Trigger Families

The built-in notification file uses these trigger families:

| Family | Examples |
| --- | --- |
| Gift HUD | `gift.liked`, `gift.neutral`, `gift.disliked`, `gift.received_item` |
| Gift world text | `gift.high_reputation`, `gift.world.liked`, `gift.world.neutral`, `gift.world.disliked` |
| Dialogue | `dialogue.greeting`, `dialogue.question`, `dialogue.cooldown`, `dialogue.joke.positive`, `dialogue.insult.negative` |
| Discovery | `dialogue.map.found`, `dialogue.rumor.found` |
| Recruitment | `recruitment.follow_start`, `recruitment.follow_stop`, `recruitment.hired`, `recruitment.fired`, `recruitment.follower_death`, `recruitment.hired_death`, `recruitment.betrayed_follower_death` |
| Reputation tiers | `reputation.tier.<level>.improved`, `reputation.tier.<level>.worsened` |
| Ambient | `ambient.murmur`, `ambient.player_item`, `ambient.sleep_breathing`, `ambient.sleep_murmur` |
| Combat | `combat.retaliation_started`, `combat.flee_started`, `combat.attack_landed`, `combat.player_killed` |
| Trade | `trade.completed`, `trade.refused` |
| Alerts | `alert.player_attacked_villager`, `alert.villager_damaged`, `alert.witness_attack.player`, `alert.witness_attack`, `alert.witness_death.player`, `alert.witness_death` |

See [Notification Triggers](Notification-Triggers.md) for simple and expanded dropdown examples for every built-in `trigger` value.

For reputation tiers, `<level>` is one of:

```text
royalty
revered
respected
trusted
neutral
suspicious
hostile
despised
feared
```

## Placeholders

Placeholder support depends on the trigger. Common built-in notification placeholders include:

```text
{item}
{target}
{villager}
{villager_possessive}
```

Unknown placeholders are left as literal text.

Player item filtered notifications can use `{player_item}`, `{held_item}`, `{player_item_id}`, `{held_item_id}`, `{player_item_slot}`, `{held_item_slot}`, `{player_item_durability}`, `{held_item_durability}`, `{player_item_max_durability}`, `{held_item_max_durability}`, `{player_item_damage}`, `{held_item_damage}`, `{player_item_durability_percent}`, `{held_item_durability_percent}`, `{player_item_enchantment}`, `{held_item_enchantment}`, `{player_item_enchantment_full}`, `{held_item_enchantment_full}`, `{player_item_enchantment_id}`, `{held_item_enchantment_id}`, `{player_item_enchantment_level}`, and `{held_item_enchantment_level}`. The aliases `player_item`, `player_item_tag`, `player_item_tags`, `player_item_slot`, `player_item_enchantment`, and `held_item_enchantment` are also accepted as fields.

Equipment-filtered notifications can use `requires_villager_armed` or `requires_villager_unarmed`, so the same trigger can show different text for armed defenders and empty-handed villagers.

Alert world text supports `{player}`, `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `alert.player_attacked_villager` for an immediate response from the damaged villager when the attacker is a player. If no entry matches, it falls back to `alert.villager_damaged`. Baby villagers can use the same alert triggers with `show_for_adults: false` and `show_for_babies: true`.

When a baby villager is damaged and an alert world-text line is emitted, the mod also sends a baby-specific villager chat line for the hit. Built-in data uses baby-only `alert.player_attacked_villager` and `alert.villager_damaged` entries for the in-world alert text.

`combat.retaliation_started` is emitted as world text when a villager or wandering trader acquires a new retaliation target. It supports `{target}`, `{target_name}`, `{target_kind}`, `{target_type}`, `{player}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `target_entity_types` to target specific mobs or players.

`combat.flee_started` is emitted as world text when a villager keeps fleeing from a remembered hostile instead of standing ground. It supports the same placeholders and `target_entity_types` filter as `combat.retaliation_started`. This is useful for unarmed villager help lines.

`combat.attack_landed` is emitted as world text when a villager or wandering trader lands a damaging hit on a living target. It supports `{target}`, `{target_name}`, `{target_kind}`, `{target_type}`, `{player}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `target_entity_types` to target specific mobs or players.

`combat.player_killed` is emitted as world text above the villager or wandering trader credited with killing a player. It supports `{player}`, `{victim}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

`alert.witness_death.player` and `alert.witness_death` can be shown by baby witnesses when `retaliation.babyVillagersFleeWitnessedDeaths` is enabled. Built-in data keeps adult and baby witness-death alert text separate with `show_for_babies` / `show_for_adults` filters.

## Ambient World Text Example

```json
{
  "notifications": [
    {
      "id": "my_pack.ambient.farmer.trusted",
      "trigger": "ambient.murmur",
      "text": "The fields know that one",
      "world_text_kind": "murmur",
      "color": "#E9EEF5",
      "professions": ["farmer"],
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "weight": 25,
      "chance": 0.5
    }
  ]
}
```

## Player Item Example

```json
{
  "notifications": [
    {
      "id": "my_pack.sword_warning",
      "trigger": "ambient.player_item",
      "text": "Easy with {held_item}.",
      "world_text_kind": "alert",
      "color": "#FFD166",
      "player_items": ["#minecraft:swords"],
      "player_item_slots": ["main_hand"],
      "weight": 20
    }
  ]
}
```

## Translation Overlay Example

```json
{
  "notifications": [
    {
      "id": "my_pack.gift.liked",
      "trigger": "gift.liked",
      "text": "Bon cadeau: {item}",
      "kind": "gift_liked",
      "color": "green"
    }
  ]
}
```

Use the same `id` as the fallback entry to replace it for that locale.
