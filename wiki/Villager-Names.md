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
  "names": [
    "Ada",
    "Bram",
    "Cora",
    "Dorian"
  ]
}
```

Only non-blank string values are used.

## Selection Behavior

When a villager without a custom name needs a preset name, the mod chooses one deterministically from the name list based on that villager's UUID. The chosen name is stored in the villager's persistent data, so changing the name list later does not rename villagers that already received a stored name.

Villagers with Minecraft custom names keep their custom names.

## Replacement Strategy

To replace the built-in list, provide your own file at the exact same path in a datapack with higher priority.

To keep the built-in list and add names, copy the built-in file, append your names, and ship the combined replacement file.

