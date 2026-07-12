# Tracked Villages

Tracked villages are server-authoritative identities built from occupied vanilla village POIs. Each record has a UUID, generated or custom name, canonical alias, POI-section footprint, resident roster, lifecycle, and last observed state.

## Allegiance rules

- Villagers and naturally created iron golems spawned or born inside a tracked footprint receive that village permanently.
- Villagers spawned outside every tracked footprint are Wanderers. They are neutral to all villages and can be recruited against any village.
- Recruited foreign residents can fight another village without the target belonging to a party.
- Same-party and same-canonical-village combat is always rejected. A merge therefore invalidates an older combat authorization immediately.
- Community retaliation begins only after damage lands and spreads only through the harmed resident's canonical village.
- Conversion preserves current-version allegiance. Legacy v1 or missing data is classified lazily from the entity's position when it loads.
- A Revered-or-higher player may repeatedly ask a villager to adopt the active village at the villager's current position.

Connected POI footprints merge automatically. Empty records advance toward archival only while every footprint chunk is loaded and observed. After 72,000 observed ticks with no occupied source POI, the identity archives; rebuilding creates a new identity.

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
/villagerretaliation allegiance statistics
/villagerretaliation allegiance village inspect_here
/villagerretaliation allegiance village list
/villagerretaliation allegiance village rename_here <name>
```
