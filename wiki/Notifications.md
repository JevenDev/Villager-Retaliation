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

## Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable id for translations and overrides. |
| `trigger` | string | required | Event trigger emitted by the mod. |
| `text` | string | required | HUD/world text. Supports trigger-specific placeholders. |
| `kind` | enum | `default` | HUD notification category. |
| `world_text_kind` | enum | `dialogue` | Style used for world text. |
| `style` | enum | `dialogue` | Alias for `world_text_kind`. |
| `color` | color | default white | Sets text and chat color unless more specific colors are provided. |
| `text_color` | color | `color` | On-screen text color. |
| `chat_color` | color | `text_color` | Chat/log color where used. |
| `professions` | string or array | any | Profession filter. |
| `reputation_levels` | string or array | any | Reputation tier filter. |
| `target_entity_types` | string or array | any | Retaliation target entity ids such as `minecraft:player` or `minecraft:zombie`. |
| `target_entities` | string or array | any | Alias for `target_entity_types`. |
| `min_reputation` | integer | none | Minimum exact reputation. |
| `max_reputation` | integer | none | Maximum exact reputation. |
| `player_items` | string or array | none | Requires the player to have one matching item or item tag. Prefix tags with `#`. |
| `player_item_slots` | string or array | `hands` when `player_items` is set | Slots to check: `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `equipment`, or `any`. |
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
| Combat | `combat.retaliation_started`, `combat.attack_landed`, `combat.player_killed` |
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

Player item filtered notifications can use `{player_item}`, `{held_item}`, `{player_item_id}`, `{held_item_id}`, `{player_item_slot}`, and `{held_item_slot}`. The aliases `player_item`, `player_item_tag`, `player_item_tags`, and `player_item_slot` are also accepted as fields.

Alert world text supports `{player}`, `{attacker}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `alert.player_attacked_villager` for an immediate response from the damaged villager when the attacker is a player. If no entry matches, it falls back to `alert.villager_damaged`.

`combat.retaliation_started` is emitted as world text when a villager or wandering trader acquires a new retaliation target. It supports `{target}`, `{target_name}`, `{target_kind}`, `{target_type}`, `{player}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `target_entity_types` to target specific mobs or players.

`combat.attack_landed` is emitted as world text when a villager or wandering trader lands a damaging hit on a living target. It supports `{target}`, `{target_name}`, `{target_kind}`, `{target_type}`, `{player}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`. Use `target_entity_types` to target specific mobs or players.

`combat.player_killed` is emitted as world text above the villager or wandering trader credited with killing a player. It supports `{player}`, `{victim}`, `{villager}`, `{villager_name}`, `{villager_kind}`, and `{profession}`.

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
