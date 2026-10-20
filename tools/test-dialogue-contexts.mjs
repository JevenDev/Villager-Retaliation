import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { validateContextDocuments, validateContextDirectory } from "./dialogue-context-validation.mjs";

const schema = JSON.parse(await readFile(new URL("./schemas/quest-v2.schema.json", import.meta.url), "utf8"));
const source = id => "/data/test/dialogue_contexts/" + id + ".json";
const rules = [
  [source("hurt"), { id: "test:hurt", conditions: [{ type: "health", subject: "speaker", max: 0.5 }] }],
  [source("help"), { id: "test:help", conditions: [{ type: "context", id: "test:hurt" }] }]
];
assert.deepEqual(validateContextDocuments(rules, schema), []);
assert(validateContextDocuments([rules[1]], schema).some(error => error.includes("missing")));
assert(validateContextDocuments([...rules, [source("duplicate"), rules[0][1]]], schema).some(error => error.includes("duplicate")));
const cyclic = structuredClone(rules);
cyclic[0][1].conditions = [{ type: "not", condition: { type: "context", id: "test:help" } }];
assert(validateContextDocuments(cyclic, schema).some(error => error.includes("cyclic")));
for (const condition of [
  { type: "health", subject: "speaker", min: 0.8, max: 0.2 },
  { type: "health", subject: "player", max: 1.1 },
  { type: "health", subject: "other", max: 0.5 },
  { type: "physical_state", subject: "player", state: "invented" },
  { type: "companion_state", state: "invented" }
]) assert(validateContextDocuments([[source("bad"), { id: "test:bad", conditions: [condition] }]], schema).length);
const tierRule = condition => [[source("tiers"), { id: "test:tiers", conditions: [condition] }]];
assert.deepEqual(validateContextDocuments(tierRule({ type: "reputation_tier", tiers: ["test:ally"] }), schema), []);
for (const condition of [
  { type: "reputation_tier", tiers: [] },
  { type: "reputation_tier", tier: "unqualified" },
  { type: "reputation_tier", tiers: ["test:Invalid Tier"] },
  { type: "reputation_tier" }
]) assert(validateContextDocuments(tierRule(condition), schema).length);
assert.deepEqual(await validateContextDirectory(), []);
console.log("Dialogue context checks passed: composition, references, cycles, bounds, state schemas, and built-in content.");
