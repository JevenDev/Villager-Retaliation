# Villager Names

Villager Retaliation can assign preset names to villagers that do not already have custom names.

## Path

Preset names are loaded from one exact datapack resource:

```text
data/villagerretaliation/villager_names/preset_names.json
```

## Format

```json
{
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

## Selection Behavior

When a villager without a custom name needs a preset name, the mod assigns a persistent gender, then chooses a name deterministically from the matching name list based on that villager's UUID. The chosen name and gender are stored in the villager's persistent data, so changing the name list later does not rename villagers that already received stored identity data.

Villagers with Minecraft custom names keep their custom names, but still receive a persistent gender for family and breeding logic. If the custom name appears in exactly one gendered name list, that gender is used; otherwise the gender is chosen deterministically from the villager UUID.

## Replacement Strategy

To replace the built-in list, provide your own file at the exact same path in a datapack with higher priority.

To keep the built-in list and add names, copy the built-in file, append your names, and ship the combined replacement file.
