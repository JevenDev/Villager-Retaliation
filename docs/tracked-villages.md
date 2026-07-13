# Tracked Villages

Tracked villages are server-authoritative identities seeded by occupied village POIs. Each record has a UUID, generated or custom name, canonical alias, section footprint, resident roster, lifecycle, and last observed state. The footprint combines POI influence, tagged worldgen structure pieces, and connected tagged terrain.

### Data-driven footprint support

Two ordinary datapack tags control non-POI coverage:

- `villagerretaliation:village_footprint` is a `worldgen/structure` tag. It includes `#minecraft:village` by default, so every vanilla plains, desert, savanna, snowy, and taiga village piece—including road pieces—is covered.
- `villagerretaliation:village_terrain` is a block tag containing `minecraft:dirt_path` by default. Sections containing these blocks extend the footprint only when they form a connected chain from the POI/structure footprint; unrelated paths elsewhere remain outside.

Mods and modpacks can append structures or terrain blocks with normal `replace: false` tag files. Structure and terrain scans inspect loaded chunks only and do not force-load worldgen.

## Allegiance rules

- Villagers and naturally created iron golems spawned inside a tracked footprint receive that village permanently.
- Newborn villagers inherit a shared parent village. Mixed-village parentage uses the physical village when it clearly matches one parent; otherwise both parent communities remain protected while assignment is unresolved.
- Villagers spawned outside every tracked footprint are Wanderers. They are neutral to all villages and can be recruited against any village.
- Recruited foreign residents can fight another village without the target belonging to a party.
- Same-party and same-canonical-village combat is always rejected. A merge therefore invalidates an older combat authorization immediately.
- Community retaliation begins only after damage lands and spreads only through the harmed resident's canonical village.
- Conversion preserves current-version allegiance. Legacy v1 or missing data is classified lazily from the entity's position when it loads.
- An uncertain assignment remains pending across entity saves and retries until the surrounding chunks provide enough evidence to resolve it safely.
- Reassignment requires Revered individual trust, a claimed bed or workstation in the destination, one resident day, acceptance from at least half of its active adult residents, and a repeated confirmation within 30 seconds.

Connected POI footprints merge automatically only after three spaced observations show the same occupied-POI connection. Terrain-only or diagonal section contact cannot merge identities. Current footprints may shrink as evidence changes while historical coverage remains available for diagnostics. Empty records advance toward archival only while every footprint chunk is loaded and observed. After 72,000 observed ticks with no occupied source POI, the identity archives; rebuilding creates a new identity.

The Allegiance page shows the latest three assignment changes. The entity payload retains the latest eight, including the responsible player for trusted reassignment.

## Village naming

Use any banner on a bell inside an active tracked village. The banner stays in the player's hand. A non-operator must have Revered or Royalty reputation with at least half (rounded up) of the tracked living adult residents. Names are 1–32 characters, whitespace-normalized, formatting-free, and unique across tracked identities.

## Debug visualization

Set `debugOverlay.showVillageBounds` in the generated config screen. The default is `false`.

One option controls the complete feature:

- enabling subscribes to a server preview of active and empty-grace villages within 256 blocks;
- world outlines use the actual POI-section union with internal coplanar edges removed;
- a gold center marker matches the existing job-site debug treatment;
- the canonical village name appears as plain shadowed gold text at the top center only while the player is inside its footprint;
- F1 hides the HUD label with the rest of the GUI;
- disabling, logout, dimension changes, server unsubscribe, and preview timeout clear cached geometry and labels.

The preview never force-loads chunks, sends archived footprints, or updates players who are not subscribed. Payloads are capped at 64 villages, 512 sections per village, and 4,096 sections total.

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
