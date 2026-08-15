# Generated Containers

Generated-container files tell Villager Retaliation which loot tables count as village property. They matter when `dialogue.containerWatchMode` is set to `GENERATED_LOOT_ONLY`.

A listed loot table marks containers created with that table as eligible for watched-container dialogue. This does not change the loot table or its drops.

## Path

```text
data/<namespace>/generated_containers/<file>.json
```

Files from every namespace are combined.

## Example

```json
{
  "loot_tables": [
    "examplemod:chests/village/alchemist_house",
    "examplemod:chests/village/watch_tower"
  ]
}
```

This makes containers generated from either table eligible for container-opened and container-theft reactions when the server uses generated-loot-only watching.

A single entry also works:

```json
{
  "loot_table": "examplemod:chests/village/alchemist_house"
}
```

For a large integration pack, `entries` can group several definitions:

```json
{
  "entries": [
    {
      "loot_tables": [
        "examplemod:chests/village/alchemist_house",
        "examplemod:chests/village/watch_tower"
      ]
    },
    {
      "loot_table": "anothermod:chests/village_store"
    }
  ]
}
```

## Loading And Overrides

All valid loot table IDs are added to one set. Repeating an ID has no extra effect.

To replace a lower-priority file, use the same namespace and file path. There is no `replace` or `remove` field for individual IDs. If another pack must remove a built-in group, it must override the exact built-in resource path with a file that lists only the IDs it wants to keep.

The built-in village list is:

```text
data/villagerretaliation/generated_containers/village_property.json
```

A container that has already generated and resolved its loot table no longer exposes that table to this check.
