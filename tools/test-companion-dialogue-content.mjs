import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { validateContentSchema } from "./content-schema-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const data = path.join(root, "neoforge/src/main/resources/data/villagerretaliation");
const read = file => JSON.parse(readFileSync(file, "utf8"));
const schema = name => read(path.join(root, "tools/datapack-builder", `${name}-v1.schema.json`));
const rows = readdirSync(path.join(data, "paired_dialogue/en_us")).flatMap(file => {
  const value = read(path.join(data, "paired_dialogue/en_us", file));
  assert.deepEqual(validateContentSchema(value, schema("paired-dialogue")), [], file);
  return value.pairs;
});
assert.equal(rows.length, 1782);
assert.equal(new Set(rows.map(p => p.id)).size, 1782);
assert.equal(new Set(rows.map(p => p.option)).size, 32);
assert.equal(new Set(rows.map(p => `${p.player}\n${p.npc}`)).size, 1760);
for (const p of rows) {
  const tokens = new Set([...`${p.player}\n${p.npc}`.matchAll(/<([a-z_]+)>/g)].map(m => m[1]));
  assert.deepEqual([...tokens].sort(), [...p.tokens].sort(), `${p.id}: exact token contract`);
  const plain = `${p.player}\n${p.npc}`.replace(/<([a-z_]+)>/g, "literal");
  assert(!/[<>{}\u00a7]/u.test(plain), `${p.id}: no unresolved markup`);
}
const ambient = read(path.join(data, "companion_ambient/default.json"));
assert.deepEqual(validateContentSchema(ambient, schema("companion-ambient")), []);
const lines = Object.values(ambient.pools).flat();
assert.equal(Object.keys(ambient.pools).length, 26);
assert.equal(lines.length, 260);
assert.equal(new Set(lines.map(l => l.id)).size, 260);
assert.equal(new Set(lines.map(l => l.text)).size, 260);
assert(Object.values(ambient.pools).every(pool => pool.length === 10));
assert.equal(new Set(ambient.profiles.filter(p => p.enabled).flatMap(p => p.structures)).size, 33);
assert(ambient.profiles.some(p => !p.enabled && p.structures.includes("minecraft:buried_treasure")));
for (const p of ambient.profiles) if (p.enabled) assert(ambient.pools[p.pool], p.id);
for (const l of lines) assert(!/[<>{}\u00a7]/u.test(l.text.replaceAll("{direction}", "nearby")), l.id);
console.log("PASS: 1782 paired records / 1760 spoken pairs / 32 option IDs; 260 distinct ambient lines / 26 pools / 33 mapped structures. Content/schema checks only.");
