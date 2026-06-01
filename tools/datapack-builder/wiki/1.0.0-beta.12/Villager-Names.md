# Villager Names

Villager Retaliation can assign preset names to villagers that do not already have custom names.

## Path

```text
data/villagerretaliation/villager_names/<file>.json
```

## Example

```json
{
  "male_names": [
    "Bram",
    "Edric"
  ],
  "female_names": [
    "Cora",
    "Mira"
  ]
}
```

## Replace Example

```json
{
  "replace": true,
  "male_names": ["Alden"],
  "female_names": ["Lyra"]
}
```

## How It Works

- Villagers with a normal generated identity can receive a preset name.
- The assigned name is persistent once chosen.
- Changing the pool later does not rename villagers that already stored identity data.
- Villagers with explicit Minecraft custom names keep those names.

Additive files are the safest way to expand the pool. Use `replace: true` only when you want to rebuild the entire preset list.
