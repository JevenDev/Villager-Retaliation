# Village Names

Villager Retaliation builds a persistent name for each newly discovered village by combining one prefix with one suffix. Existing villages keep their stored names when datapacks change.

## Path

```text
data/villagerretaliation/village_names/<file>.json
```

## Example

```json
{
  "prefixes": [
    "Copper",
    "Juniper"
  ],
  "suffixes": [
    "bridge",
    "hollow"
  ]
}
```

This example adds `Copperbridge`, `Copperhollow`, `Juniperbridge`, and `Juniperhollow` to the possible generated names.

## Replace Example

```json
{
  "replace": true,
  "prefixes": ["Sun"],
  "suffixes": ["haven", "wick"]
}
```

## How It Works

- Files are additive by default and are read in resource-location order.
- If any add-on file uses `replace: true`, built-in village-name files are skipped. The replacement file clears prefixes and suffixes loaded from earlier add-on files before adding its values.
- Prefixes and suffixes are joined directly, without an automatic space.
- Selection is deterministic from the village identity, while duplicate names already used in the world are skipped.
- Generated names are persisted. Reloading or changing the pool affects only villages that receive a name afterward.
- Generated results longer than 32 characters, or containing formatting or control codes, are skipped.
- If either final pool is empty, VR uses a stable emergency name based on the village identity so village creation still succeeds.

Additive files are the safest way to expand the pool. Use `replace: true` only when you want to rebuild both halves of every future generated village name.
