# Localization Guide

Villager Retaliation has two localization layers:

- **Datapack text** for dialogue, notifications, ambient world text, and villager name pools.
- **Resource-pack language keys** for client UI labels, generated GUI rows, reputation overlays, and Minecraft entity names.

Use both layers when translating the whole experience. A datapack can translate villager speech and notification text, but it cannot translate the interaction-screen buttons by itself. A resource pack can translate the GUI, but it cannot replace datapack dialogue pools by itself.

## Datapack Locale Files

Dialogue and notifications are locale-aware datapack systems. The mod loads `en_us` first, then overlays the player's normalized client locale when the server knows it.

```text
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
data/villagerretaliation/dialogue/fr_fr/my_pack_dialogue.json
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
data/villagerretaliation/notifications/fr_fr/my_pack_notifications.json
```

Use the same `id` in the fallback entry and the translated entry. The translated entry replaces the fallback only for players using that locale.

```json
{
  "messages": [
    {
      "id": "my_pack.follow_start",
      "key": "interaction.follow_start",
      "text": "All right. I will follow you."
    }
  ]
}
```

```json
{
  "messages": [
    {
      "id": "my_pack.follow_start",
      "key": "interaction.follow_start",
      "text": "D'accord. Je vais te suivre."
    }
  ]
}
```

Players using `fr_fr` see the French line. Players using any other language keep the `en_us` fallback.

## Resource-Pack Language Files

Client UI text lives in normal Minecraft language files:

```text
assets/villagerretaliation/lang/en_us.json
assets/villagerretaliation/lang/fr_fr.json
```

Use a resource pack to translate these strings:

- Interaction-screen title, buttons, Esc hints, gift tooltip labels, and info rows.
- Generated family labels such as father, cousin, spouse, grandparent, and descendant rows.
- Generated relationship labels, relationship stages, and alive/deceased status text.
- Reputation overlay labels and reputation tier names.
- Villager chat tag text and speaker formatting.

Example:

```json
{
  "villagerretaliation.gui.root.talk": "Parler",
  "villagerretaliation.gui.root.trade": "Commercer",
  "villagerretaliation.gui.info.gender": "Genre : %s",
  "villagerretaliation.gui.relationships.active_format": "%s : %s (%s) - affection %s, compatibilite %s",
  "villagerretaliation.reputation.value_format": "Reputation : %s"
}
```

Keep `%s` placeholders in the translated string. You can reorder them if the target language needs a different sentence structure.

## Format Keys

Some resource-pack language keys are format strings. Keep the same number of `%s` placeholders unless the key notes otherwise. Minecraft uses the values passed by the mod at runtime, so removing a required placeholder can hide information, and adding extra placeholders can display incorrectly.

| Key | Arguments |
| --- | --- |
| `container.villagerretaliation.villager_inventory` | villager name |
| `villagerretaliation.reputation.value_format` | reputation value |
| `villagerretaliation.reputation.tier_value_format` | reputation tier, reputation value |
| `villagerretaliation.reputation.debug.value_and_level` | reputation value, reputation tier |
| `villagerretaliation.reputation.debug.health` | current health, max health |
| `villagerretaliation.reputation.debug.armor` | armor value |
| `villagerretaliation.gui.info.gender` | localized gender |
| `villagerretaliation.gui.info.mood` | localized mood |
| `villagerretaliation.gui.info.reputation` | reputation value |
| `villagerretaliation.gui.speaker.profession` | localized profession |
| `villagerretaliation.gui.speaker.named` | localized profession, villager name |
| `villagerretaliation.gui.chat.speaker_prefix` | speaker label |
| `villagerretaliation.gui.family.tree_count` | known family count |
| `villagerretaliation.gui.family.row` | relationship label, member name |
| `villagerretaliation.gui.family.member.deceased_format` | member name, deceased status |
| `villagerretaliation.gui.family.great_prefix` | existing ancestor or descendant label |
| `villagerretaliation.gui.relationships.count` | known relationship count |
| `villagerretaliation.gui.relationships.active_format` | stage, partner name, status, affection, compatibility |
| `villagerretaliation.gui.relationships.past_format` | stage, partner name, status |
| `villagerretaliation.gui.relationships.past_format_reason` | stage, partner name, status, end reason |

Configuration screen keys such as `villagerretaliation.configuration.title` and `villagerretaliation.configuration.section.villagerretaliation.common.toml` also use `%s` for the mod or section name.

## Profession Names

The interaction GUI resolves villager profession names through language keys instead of server-side English strings.

Vanilla professions use Minecraft's own keys:

```json
{
  "entity.minecraft.villager.farmer": "Farmer",
  "entity.minecraft.villager.librarian": "Librarian"
}
```

Custom professions use the same pattern with the namespace included:

```json
{
  "entity.minecraft.villager.my_mod.crystal_smith": "Crystal Smith"
}
```

If a custom profession id uses folders in its path, write those path separators as dots in the language key. For example, `my_mod:jobs/crystal_smith` becomes `entity.minecraft.villager.my_mod.jobs.crystal_smith`.

Villager Retaliation also provides fallback profession labels:

```json
{
  "villagerretaliation.gui.profession.child": "Child",
  "villagerretaliation.gui.profession.unemployed": "Unemployed"
}
```

## Gender, Mood, Family, And Relationships

Villager gender and mood labels are client-localized:

```json
{
  "villagerretaliation.gui.gender.male": "Male",
  "villagerretaliation.gui.gender.female": "Female",
  "villagerretaliation.gui.gender.unknown": "Unknown",
  "villagerretaliation.gui.mood.friendly": "Friendly",
  "villagerretaliation.gui.mood.hostile": "Hostile"
}
```

Family and relationship rows are assembled from smaller translated parts. This makes generated rows localizable without datapacks having to hardcode every possible family tree.

```json
{
  "villagerretaliation.gui.family.row": "%s: %s",
  "villagerretaliation.gui.family.father": "Father",
  "villagerretaliation.gui.family.great_prefix": "Great-%s",
  "villagerretaliation.gui.family.member.deceased_format": "%s (%s)",
  "villagerretaliation.gui.relationships.status.deceased": "deceased",
  "villagerretaliation.gui.relationship.stage.married": "Married",
  "villagerretaliation.gui.relationships.active_format": "%s: %s (%s) - affection %s, compatibility %s"
}
```

If a language cannot build ancestor words with a simple prefix such as `Great-%s`, translate `villagerretaliation.gui.family.great_prefix` as the best readable fallback and keep an eye on future releases. This key is intentionally isolated so the format can be improved later without changing datapack text.

## What Not To Translate In Datapacks

Do not translate these in dialogue or notification JSON:

- Interaction buttons such as Talk, Trade, Gift, Recruit, and Goodbye.
- GUI labels such as Gender, Mood, Reputation, Family Tree, and Relationships.
- Generated family relationship labels.
- Reputation overlay labels.
- Vanilla or custom profession display names.

Put those in `assets/villagerretaliation/lang/<locale>.json` or, for vanilla profession names, rely on Minecraft's existing language files.

## Pack Developer Checklist

- Put fallback text in `en_us`.
- Give every translated dialogue and notification entry a stable `id`.
- Use the same `id` in translated locale files to replace only that entry.
- Keep placeholders such as `{villager}`, `{item}`, and `%s` intact.
- Test once with `en_us` and once with the target language selected in the client.
- Ship a resource pack alongside your datapack if you translate the interaction GUI or reputation UI.
