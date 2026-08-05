# Tracked Villages

This page explains how village identity works for contributors and pack authors.

A **point of interest**, or POI, is a workstation, bell, bed, or other block that Minecraft uses to recognize village activity. A **footprint** is the set of 16 by 16 block sections that Villager Retaliation considers part of a village. A **canonical village** is the surviving shared identity after two village records merge.

Tracked villages are saved on the server and begin around occupied village POIs. Each record has a UUID, a generated or custom name, a canonical identity, a footprint, a resident list, a lifecycle state, and the time it was last observed. The footprint combines POI influence, tagged generated structures, and connected tagged terrain.

## Data-driven footprint support

Two ordinary datapack tags control non-POI coverage:

- `villagerretaliation:village_footprint` is a `worldgen/structure` tag. It includes `#minecraft:village` by default, so vanilla village buildings and roads are covered.
- `villagerretaliation:village_terrain` is a block tag containing `minecraft:dirt_path` by default. Sections containing these blocks extend the footprint only when they form a connected chain from the POI or structure footprint. Unrelated paths elsewhere remain outside.

Mods and modpacks can append structures or terrain blocks with normal `replace: false` tag files. Structure and terrain scans inspect loaded chunks only and do not force-load world generation.

For example, this file adds a custom village structure:

```text
data/villagerretaliation/tags/worldgen/structure/village_footprint.json
```

```json
{
  "replace": false,
  "values": ["my_pack:river_village"]
}
```

This file lets connected custom paths extend a village:

```text
data/villagerretaliation/tags/block/village_terrain.json
```

```json
{
  "replace": false,
  "values": ["my_pack:packed_mud_path"]
}
```

Run `/reload` after changing either tag. Inspect the result with the debug overlay described below.

## Allegiance rules

- Villagers and naturally created iron golems spawned inside a tracked footprint receive that village permanently.
- Newborn villagers born inside a village receive that village, regardless of their parents' homes. Outside a village, a baby inherits the first parent's known home. If neither parent has one, the baby is a Wanderer.
- Villagers spawned outside every tracked footprint are Wanderers. They are neutral to all villages and can be recruited against any village.
- A non-party Wanderer who remains inside the same active village for 24,000 ticks settles there automatically. Leaving, changing villages, joining a party, or a backward game-time change resets the settlement clock.
- Villagers who already have a home never change it merely by traveling or claiming a local bed or workstation.
- Party villagers never settle automatically. A party villager can adopt the current village only when ordered by a Revered or Royalty player in that same party. Outside players cannot issue the order.
- Recruited foreign residents can fight another village without the target belonging to a party.
- Same-party and same-canonical-village combat is always rejected. A merge therefore invalidates an older combat authorization immediately.
- Community retaliation begins only after damage lands and spreads only through the harmed resident's canonical village.
- Conversion preserves current-version allegiance. Older or missing data is classified from the entity's position when it loads.
- An uncertain assignment remains pending across entity saves and retries until the surrounding chunks provide enough evidence to resolve it safely.
- Deliberate reassignment requires Revered individual trust and a repeated confirmation within 30 seconds. The party restriction above is applied before trust is considered.

Connected POI footprints merge automatically only after three checks at different times show the same occupied POI connection. Terrain-only or diagonal section contact cannot merge identities. Current footprints may shrink as evidence changes, while historical coverage remains available for debugging. Empty records move toward archival only while every footprint chunk is loaded and observed. After 72,000 observed ticks with no occupied source POI, the identity is archived. Rebuilding creates a new identity.

The player-facing Home topic answers direct questions about the villager's home and the current village. Technical assignment history remains available through allegiance inspection commands. The entity data keeps the latest eight changes, including the player responsible for a trusted reassignment.

## Village naming

Use any banner on a bell inside an active tracked village. The banner stays in the player's hand. A non-operator must have Revered or Royalty reputation with at least half, rounded up, of the tracked living adult residents. Names are 1 to 32 characters. Extra whitespace is removed, formatting codes are rejected, and names must be unique across tracked villages.

## Debug visualization

Set `debugOverlay.showVillageBounds` in the generated config screen. The default is `false`.

When enabled, the option:

- Subscribes to a server preview of active and recently emptied villages within 256 blocks.
- Draws the actual POI section boundary without internal faces.
- Shows a gold center marker that matches the job-site debug marker.
- Shows the canonical village name as gold text at the top center while the player is inside its footprint.
- Hides the label with the rest of the GUI when F1 is pressed.
- Clears saved preview shapes and labels after disabling the option, logging out, changing dimension, unsubscribing, or missing the next preview update.

The preview never force-loads chunks, sends archived footprints, or updates players who are not subscribed. One update is limited to 64 villages, 512 sections per village, and 4,096 sections in total.

## Administration

```mcfunction
/villagerretaliation allegiance inspect <villager>
/villagerretaliation allegiance explain <villager>
/villagerretaliation allegiance repair <villager>
/villagerretaliation allegiance statistics
/villagerretaliation allegiance undo_merge <source-uuid>
/villagerretaliation allegiance village inspect_here
/villagerretaliation allegiance village list
/villagerretaliation allegiance village rename_here <name>
```

Use `inspect` for the saved assignment, `explain` for the rule that produced it, and `repair` when the assignment is stuck pending. The village commands inspect, list, rename, or undo a merge for tracked village identities.
