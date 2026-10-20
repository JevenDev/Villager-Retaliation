# Complete settlement house templates

This optional Minecraft 1.21.1 datapack demonstrates Small, Medium and Large **complete** house registrations using existing vanilla plains templates. Example prices illustrate the syntax and can be adjusted for each pack.

Copy this folder into a world's `datapacks` folder and reload the server. The `builder_structures/zz_settlement_houses.json` file changes metadata for its three vanilla template IDs. Its name sorts after the built-in vanilla catalog so the demonstration overrides take effect. Other catalog entries remain available. For an authored variant, export the complete structure as compressed NBT, put it at `data/<namespace>/structure/houses/<variant>.nbt`, and change `structure` to `<namespace>:houses/<variant>`. Register each variant separately. Keep its authored foundation, porch, furniture and palette in that export; the cost recipe never changes output materials or assembles guide-grid modules.

`category: "House"` and `house_size: "small" | "medium" | "large"` provide browsing metadata. `label` names the variant. The template ID is also its catalog identity. The optional legacy `base_cost` remains a hired-builder fee and is separate from settlement material payment.

`construction_cost` supports two forms:

```json
{ "mode": "exact" }
```

```json
{
  "mode": "simplified",
  "materials": [
    { "item": "minecraft:cobblestone", "count": 64 },
    { "tag": "minecraft:logs", "count": 32 }
  ]
}
```

Omitting `construction_cost` selects exact mode. Each simplified material has exactly one item or item tag and a positive integer count up to 1,000,000. At most 256 material lines are allowed. Empty, misspelled and malformed recipes are rejected; an invalid override removes the old entry instead of reverting to an earlier cheaper price. Tags are resolved against real supplied stacks. Missing/empty tags cannot satisfy payment. Use a tag only when every item in it is an intentional acceptable payment. Submitted alternatives never recolor the template.

Exact scanning charges one item per complete bed, door or double-height plant, two slabs per double slab, every candle/pickle/egg/snow layer in a stacked state, a pot plus its plant, and one bucket per authored fluid or waterlogged block. Filled cauldrons require both the cauldron and its contents bucket; powder snow requires a powder-snow bucket. Charged respawn anchors also require their glowstone charges. Grass uses the existing dirt construction convention; stripped wood and worked dirt use their source block plus the builder's tool action. Air is clearance, with no material cost. Unknown blocks, unsupported itemless output and orphan multiblock parts reject the template rather than becoming free. Water costs are conservative per block; flowing-water generation is not simulated. Export empty beehives and empty composters.

Template entities are omitted. Inventories, loot tables, bees, spawner/command data and arbitrary mod block-entity payloads are removed. Sign text, banner patterns and skull appearance are retained. Export empty containers; decorations needing other block-entity data are unsupported and should be checked in preview. Do not put valuable items or entities in a template expecting them to be constructed. The original `.nbt` is never rewritten by scanning.

Schema: [`builder-structures.schema.json`](../../tools/schemas/builder-structures.schema.json). A datapack reload clears the catalog/scanner caches. New projects preserve a fingerprint of their complete output and bill; changed templates wait with a visible reason until cancelled and submitted again.
