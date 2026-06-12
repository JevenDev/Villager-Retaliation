# Builder Structures

Builder structure files control which structure templates hired builders can offer in the builder menu and how much extra currency each structure adds to the job price.

Use this for modpack packs that add custom village houses, modded houses, or any other structure template you want builders to construct.

## Paths

```text
data/<namespace>/builder_structures/<file>.json
```

Examples:

```text
data/my_pack/builder_structures/custom_village_houses.json
data/villagerretaliation/builder_structures/vanilla_village_houses.json
```

Files can live in any namespace. The built-in vanilla village houses are declared at:

```text
data/villagerretaliation/builder_structures/vanilla_village_houses.json
```

## Add One Structure

```json
{
  "entries": [
    {
      "structure": "examplemod:village/houses/carpenter_house",
      "category": "Modded Village",
      "label": "Carpenter House",
      "base_cost": 18
    }
  ]
}
```

`structure` must point at a real Minecraft structure template id. For modded structures, use the full `modid:path` id.

## Add Several Structures

Use `structures` when several entries share the same category and cost. Labels are generated from the template path.

```json
{
  "entries": [
    {
      "category": "Modded Village",
      "base_cost": 16,
      "structures": [
        "examplemod:village/houses/small_house_1",
        "examplemod:village/houses/small_house_2",
        "examplemod:village/houses/fisher_house"
      ]
    }
  ]
}
```

## Fields

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `structure` | string | none | One structure template id. Alias: `id`, `structure_id`, `structureId`. |
| `structures` | string or array | none | Several structure template ids using shared category and cost. |
| `category` | string | `Structures` | Builder menu category. |
| `label` | string | generated | Display label for a single structure entry. Ignored for multi-structure entries. |
| `base_cost` | integer | `0` | Extra currency added to this structure's build price. Aliases: `baseCost`, `cost`. |
| `remove` | boolean | `false` | Removes the listed structure id or ids from the builder menu. |
| `enabled` | boolean | `true` | Set `false` to remove the listed structure id or ids. |

The final job price is:

```text
builder base cost config + entry base_cost + configured per-64-block cost
```

Blueprint creation uses the configured blueprint percentage of that final job price.

## Remove A Built-In Structure

```json
{
  "entries": [
    {
      "structure": "minecraft:village/plains/houses/plains_small_house_1",
      "remove": true
    }
  ]
}
```

## Replace The Whole List

`replace: true` clears previously loaded builder structures before this file is applied.

```json
{
  "replace": true,
  "entries": [
    {
      "structure": "examplemod:village/houses/starter_home",
      "category": "Starter Village",
      "label": "Starter Home",
      "base_cost": 10
    }
  ]
}
```

To guarantee a full built-in replacement, override the built-in file path:

```text
data/villagerretaliation/builder_structures/vanilla_village_houses.json
```

## Reload Behavior

Builder structure files are loaded on server start and `/reload`. The server syncs the current builder catalog to connected clients after reload, so newly added structures appear in the builder menu without a relog.

The server still validates every preview, confirmation, blueprint placement, and job start against the server-loaded catalog.

## Practical Notes

- Keep custom structure templates small enough for the builder max-block config.
- Builders copy block entity data for placed blocks, but generated loot table tags are stripped from built containers.
- If a structure appears in the menu but preview says unavailable, confirm the template id exists and the structure is not over the builder max-block limit.
- Use `/villagerretaliation datapack diagnostics` after `/reload` to see skipped or duplicate datapack entries.
