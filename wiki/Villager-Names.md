# Villager Names

Villager Retaliation can assign preset names to villagers that do not already have custom names.

## Paths

Preset names are loaded from JSON files under:

```text
data/villagerretaliation/villager_names/preset_names.json
data/villagerretaliation/villager_names/my_pack_names.json
```

## Format

```json
{
  "replace": false,
  "male_names": [
    "Ada",
    "Bram"
  ],
  "female_names": [
    "Cora",
    "Dorian"
  ]
}
```

Only non-blank string values are used. The older `names` array is still supported as a fallback for packs that have not split their name pools yet.

Files are read in sorted resource-location order. A file with top-level `"replace": true` clears the names loaded before it, then adds its own names.

## Selection Behavior

When a villager without a custom name needs a preset name, the mod assigns a persistent gender, then chooses a name deterministically from the matching name list based on that villager's UUID. The chosen name and gender are stored in the villager's persistent data, so changing the name list later does not rename villagers that already received stored identity data.

Villagers with Minecraft custom names keep their custom names, but still receive a persistent gender for family and breeding logic. If the custom name appears in exactly one gendered name list, that gender is used; otherwise the gender is chosen deterministically from the villager UUID.

## Replacement Strategy

To add names, create a new file under `data/villagerretaliation/villager_names/`.

To replace the built-in list, provide a higher-priority file at the exact same `preset_names.json` path, or create a later-sorting file with `"replace": true`.
