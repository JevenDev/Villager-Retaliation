# Pack Development Guide

This guide covers how to build packs that target Villager Retaliation JSON and assets.

For version-to-version pack migrations, see [Pack Format Changes](Pack-Format-Changes.md).

For a guided browser tool that creates, imports, previews, validates, and exports Villager Retaliation datapacks, see the [Datapack Generator](Datapack-Generator.md).

## Supported Pack Types

Use a datapack for server-side behavior and text pools:

```text
<datapack root>/
  pack.mcmeta
  data/
    villagerretaliation/
      dialogue/
      forced_dialogue/
      notifications/
      gifts/
      profession_loot/
      villager_names/
      loot_table/
        villager/profession/
    <your_namespace>/
      loot_table/
      story_structures/
      story_biomes/
```

Use a resource pack for client-side textures and model JSON:

```text
<resource-pack root>/
  pack.mcmeta
  assets/
    minecraft/
      textures/entity/villager/villager.png
      textures/entity/wandering_trader.png
    villagerretaliation/
      lang/
        en_us.json
        fr_fr.json
      textures/entity/villager/villager.png
      textures/entity/wandering_trader/wandering_trader.png
      models/entity/villager/combat_villager.json
      models/entity/villager/render_options.json
      models/entity/villager/non_combat_villager.json
```

Resource-pack language files translate client-side UI, including the interaction screen, generated family and relationship rows, reputation overlays, and Villager Retaliation chat labels. See [Localization Guide](Localization.md) for the split between datapack text and resource-pack language keys.

## Namespace Rules

Most Villager Retaliation data is intentionally scoped to the mod namespace:

| System | Required namespace |
| --- | --- |
| Dialogue | `villagerretaliation` |
| Forced dialogue | `villagerretaliation` |
| Notifications | `villagerretaliation` |
| Gifts | `villagerretaliation` |
| Profession loot rules | `villagerretaliation` |
| Preset names | `villagerretaliation` |
| Resource-pack models/textures | `villagerretaliation` or the vanilla texture paths documented on the model page |

Story discovery files and referenced loot tables are the exceptions. Structure and biome story entries are loaded from any namespace:

```text
data/my_pack/story_structures/ancient_places.json
data/my_pack/story_biomes/rare_biomes.json
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

Story entries still point at real structure or biome ids such as `minecraft:ancient_city` or `examplemod:crystal_marsh`. Profession loot rule files stay in `villagerretaliation`, but their `loot_table` values can reference tables from any namespace.

## File Layering And Replacement

Minecraft resources are loaded from all active packs. Villager Retaliation then reads the matching JSON files in sorted resource-location order.

The folder path decides which loader reads a file. A `notifications` array inside `data/villagerretaliation/dialogue/<locale>/...json` is still in a dialogue file, not a notification file, and forced-dialogue `entries` inside a dialogue or notification folder are not routed to the forced-dialogue loader. Keep related ideas split across the documented folders:

```text
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
data/villagerretaliation/forced_dialogue/my_pack_events.json
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
```

On reload, the mod logs warnings for common path mistakes, unsupported fields that will be ignored, notification triggers used in forced-dialogue files, forced-dialogue triggers used in notification files, inert player item slot filters, and unknown profession ids.

For additive dialogue, forced dialogue, and notification packs, use your own file names, such as:

```text
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
data/villagerretaliation/forced_dialogue/my_pack_events.json
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
```

Do not put addon content in `data/villagerretaliation/dialogue/en_us/global.json`, `data/villagerretaliation/forced_dialogue/default.json`, or `data/villagerretaliation/notifications/en_us/global.json` unless you mean to replace the mod's built-in file. Same-path datapack files replace the built-in file before Villager Retaliation reads entries, so a pack at those paths can hide default interaction-menu options, keyed messages, openings, closings, forced event entries, notification text, and other built-in data.

Dialogue, forced dialogue, notification, gift, and profession loot entries support stable `id` values. When a later locale layer or later file defines the same `id`, it replaces the previous entry in that loaded pool. This is the cleanest way to translate or override one specific line or rule without copying a full built-in file.

Top-level `"replace": true` is file-wide. In dialogue files, it clears earlier options, lines, messages, openings, closings, and pacify lines before reading that file. In notification files, it clears earlier HUD notifications and world-text entries before reading that file. In gift, profession loot, and villager name files, it clears the matching loaded pool before reading that file. Use top-level `replace` for total replacements, not for one extra entry.

Gift preference, gift reward, and profession loot entries also support `"remove": true` when an `id` is supplied.

Preset names are additive across JSON files under:

```text
data/villagerretaliation/villager_names/
```

Use a unique file name to add names. Set top-level `"replace": true` in a later-sorting file, or override `villager_names/preset_names.json`, when you intentionally want to replace the available name pool.

## Locale Layering

Dialogue and notifications are locale-aware. The mod always loads `en_us` first, then overlays the player's normalized client locale when the server knows it.

Example:

```text
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
data/villagerretaliation/dialogue/fr_fr/my_pack_dialogue.json
```

If both files define an entry with the same `id`, the `fr_fr` entry replaces the `en_us` entry for French players. Players using other languages keep the English fallback.

GUI labels are not loaded from datapack locale folders. Translate buttons, info rows, generated family and relationship labels, reputation labels, gender names, beta.12 temporary mood names, Profile page Social Attribute names/ranks, and fallback profession labels in a resource pack:

```text
assets/villagerretaliation/lang/fr_fr.json
```

Profession display names resolve through language keys too. Vanilla professions use Minecraft's keys such as `entity.minecraft.villager.farmer`; custom professions use `entity.minecraft.villager.<namespace>.<path>`, with path separators written as dots.

## Reloading

Datapack changes are read through Minecraft's resource manager. In a development world, use:

```mcfunction
/reload
```

Client resource-pack model and texture changes normally require a resource-pack reload. In most client setups, `F3 + T` reloads resources.

## JSON Failure Behavior

Villager Retaliation generally ignores invalid datapack entries so one bad custom file does not break every villager. That is friendly for players, but it means development mistakes can appear as "nothing happened."

Before testing in game:

- Validate JSON syntax with your editor or a JSON linter.
- Confirm paths exactly match the documented roots.
- Confirm enum values are spelled correctly. Values are case-insensitive in code, but lowercase snake case is recommended.
- Check the latest log after `/reload`. Villager Retaliation warns when it sees content in the wrong system folder, unsupported fields, wrong trigger families, item slot filters that cannot match, or profession ids that are not registered.
- Give overrideable dialogue, forced dialogue, notification, gift, and profession loot entries explicit `id` values.
- Use a small test pack first, then expand once the hook works.

## Testing Checklist

1. Start with one JSON file and one obvious line or rule.
2. Run `/reload`.
3. Trigger the relevant interaction in a test world.
4. For event-tagged dialogue, trigger the event near the target villager and talk to them before the short village-memory window expires.
5. For forced dialogue, trigger the event with an adult villager close enough to witness it; `container_theft` and `container_opened` also need line of sight by default.
6. For generated-container forced dialogue, test first with a container that still has an unresolved loot table, such as a newly generated village chest. After the first detected open, the mod remembers that container's original loot table for later opens. Player-placed chests do not match forced-dialogue container triggers.
7. Check latest logs for JSON warnings, especially if a datapack entry appears to do nothing.
8. Add filters one at a time after the unfiltered version works.
9. For reputation-gated options or lines, test with `/villagerretaliation setNearbyReputation <value>` and confirm each tier sees the intended choices.
10. For localized entries, test once with default `en_us` and once with the target language.
11. If the pack translates the GUI, enable the matching resource pack while testing the translated datapack.
