# Notifications

Notifications cover two related outputs:

- HUD notices such as quest updates and gift results
- floating world text above villagers, such as ambient murmurs or refusals

## Path

Notification files always live in the `villagerretaliation` namespace and use locale folders:

```text
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
data/villagerretaliation/notifications/fr_fr/my_pack_notifications.json
```

## Minimal File

```json
{
  "notifications": [
    {
      "id": "my_pack.quest.started",
      "trigger": "quest.started",
      "text": "Quest started: {quest}",
      "kind": "quest",
      "color": "#FFD166"
    }
  ]
}
```

## Common Uses

### Ambient World Text

```json
{
  "id": "my_pack.ambient.trusted_farmer",
  "trigger": "ambient.murmur",
  "text": "Good harvest follows good neighbors",
  "world_text_kind": "murmur",
  "professions": ["minecraft:farmer"],
  "reputation_levels": ["trusted", "respected", "revered", "royalty"]
}
```

### Quest HUD Notice

```json
{
  "id": "my_pack.quest.completed",
  "trigger": "quest.completed",
  "text": "{quest} complete.",
  "kind": "quest",
  "color": "#FFE29A"
}
```

### Trade Refusal Flavor

```json
{
  "id": "my_pack.trade.refused.hostile",
  "trigger": "trade.refused",
  "text": "Not today.",
  "world_text_kind": "negative",
  "reputation_levels": ["hostile", "despised", "feared"]
}
```

## Main Fields

| Field | Use |
| --- | --- |
| `trigger` | What event causes the notice to be considered |
| `text` or `lines` | The actual output |
| `kind` | HUD icon family such as `quest` or `gift_liked` |
| `world_text_kind` | Floating-text style such as `murmur`, `negative`, or `alert` |
| `professions` | Villager profession filter |
| `reputation_levels` | Trust-tier filter |
| `weight` | Relative selection weight |
| `chance` | Additional random gate from `0.0` to `1.0` |

## Translation Rule

Notification files behave like dialogue locale overlays:

- `en_us` is the fallback
- matching `id` values in another locale replace the fallback entry for that player

Use the same `id` in both files when translating an existing notification.

## Good Practice

- Give every notification a stable `id`.
- Use `text` for one line and `lines` when several equal variants share the same rule.
- Keep GUI translations in a resource pack, not here.

For trigger selection, see [Notification Triggers](Notification-Triggers.md).
