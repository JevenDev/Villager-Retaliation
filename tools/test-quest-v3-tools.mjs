import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFile, readdir, mkdtemp, mkdir, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { fingerprintJson, migrationReceipts } from "./quest-v3-compatibility.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";
const { V2, V3, migrate, runtime } = createRequire(import.meta.url)("./quest-v3-format.js");
const schema = JSON.parse(await readFile(new URL("./schemas/quest-v3.schema.json", import.meta.url), "utf8"));
const fingerprints = JSON.parse(await readFile(new URL("../neoforge/src/gameTest/resources/quest_v3/migration_fingerprints.json", import.meta.url), "utf8"));
for (const value of ["test:rescue", 3, true]) {
  assert.deepEqual(validateContentSchema(value, { type: ["string", "number", "boolean"] }), []);
}
assert(validateContentSchema({}, { type: ["string", "number", "boolean"] }).length > 0);
for (const selection of ["existing_only", "prefer_existing", "new_site"]) {
  for (const terrain of ["new_terrain_only", "generated_wilderness"]) {
    const site = { selection, terrain, structures: ["minecraft:pillager_outpost"],
      templates: ["villagerretaliation:quest/holding_camp"], map_style: "search_circle", rescue: "escort_home" };
    assert.deepEqual(validateContentSchema(site, schema.$defs.target.properties.site), []);
    if (selection !== "existing_only") {
      delete site.templates;
      assert(validateContentSchema(site, schema.$defs.target.properties.site).length > 0);
    }
  }
}
for (const example of fingerprints.values) assert.equal(fingerprintJson(example.source), example.fingerprint, example.name);
assert.throws(() => fingerprintJson('{"number":1.}'), SyntaxError);
const bundleFixture = fingerprints.bundle;
const numericQuestPath = "data/test/quests/line/example/quest.json";
const numericRewardPath = "data/test/quests/line/example/rewards/reward.json";
const numericSources = new Map([[numericQuestPath, bundleFixture.quest], [numericRewardPath, bundleFixture.reward]]);
const numericFiles = new Map([...numericSources].map(([file, text]) => [file, JSON.parse(text)]));
const numericReceipt = [...migrationReceipts(numericFiles, numericSources).values()][0];
assert.equal(numericReceipt.from_fingerprint, bundleFixture.before, "old fingerprint keeps original number spellings");
assert.equal(numericReceipt.to_fingerprint, bundleFixture.after, "new fingerprint keeps unchanged companion number spellings");
assert.deepEqual(migrate(JSON.parse(bundleFixture.quest)), JSON.parse(bundleFixture.converted_quest));
const original = {
  schema: V2, id: "test:delivery", localization_prefix: "test.quest.delivery",
  metadata: { questline: "village", title: { key: "#title" }, title_key: "test.quest.delivery.title", campaign: "test:story", revision: 4 },
  ui: { title: { key: "#tracker" } }, provider: { type: "villagerretaliation:villager", capabilities: ["inventory"] },
  entry_stage: "first", stages: [{ id: "first", objectives: [{ id: "paper", type: "item_check", item: "minecraft:paper", count: 2 }],
    completion_mode: "all", entry_actions: [{ type: "experience", amount: 1 }], next: { complete: true } }]
};
const sourceCopy = structuredClone(original);
const converted = migrate(original);
assert.deepEqual(original, sourceCopy, "migration must not mutate input");
assert.equal(converted.id, original.id);
assert.equal(converted.localization_prefix, original.localization_prefix);
assert.equal(converted.revision, 4);
assert.equal(converted.story_arc, "test:story");
assert.equal(converted.stages[0].objectives[0].id, "paper");
assert.deepEqual(converted.display, { tracker_title: { key: "#tracker" }, title: { key: "#title" } });
assert.deepEqual(converted.stages[0].transition, { complete: true });
assert.deepEqual(runtime(converted).metadata.campaign, "test:story");
assert.deepEqual(runtime(converted).ui.title, { key: "#tracker" });
assert.throws(() => migrate({ ...original, stages: [{ ...original.stages[0], on_enter: [] }] }), /Conflicting/);
assert.throws(() => migrate({ ...original, schema: V3 }), /Expected/);
assert(validateContentSchema({ ...converted, ui: {} }, schema).some(issue => issue.pointer === "/ui"));
assert(validateContentSchema({ ...converted, world_campaign: "bad id" }, schema).length > 0);
let count = 0;
async function scan(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) await scan(file);
    else if (entry.name === "quest.json") {
      const quest = JSON.parse(await readFile(file, "utf8"));
      if (quest.schema !== V3) continue;
      assert.deepEqual(validateContentSchema(quest, schema), [], file);
      count++;
    }
  }
}
await scan(fileURLToPath(new URL("../neoforge/src/main/resources/data", import.meta.url)));
assert(count >= 99);
const directory = await mkdtemp(path.join(tmpdir(), "quest-v3-"));
try {
  const input = path.join(directory, "input");
  const output = path.join(directory, "output");
  await mkdir(input);
  const valid = structuredClone(original);
  delete valid.provider.capabilities;
  const content = JSON.stringify(valid);
  const questPath = path.join("data", "test", "quests", "village", "delivery", "quest.json");
  await mkdir(path.dirname(path.join(input, questPath)), { recursive: true });
  await writeFile(path.join(input, questPath), content);
  await writeFile(path.join(input, "icon.png"), new Uint8Array([1, 2, 3]));
  const command = path.resolve("tools/migrate-quest-v2-to-v3.mjs");
  const check = spawnSync(process.execPath, [command, input, "--check"], { encoding: "utf8" });
  assert.equal(check.status, 0, check.stderr);
  const run = spawnSync(process.execPath, [command, input, "--out-dir", output], { encoding: "utf8" });
  assert.equal(run.status, 0, run.stderr);
  assert.equal(await readFile(path.join(input, questPath), "utf8"), content);
  assert.equal(JSON.parse(await readFile(path.join(output, questPath), "utf8")).schema, V3);
  const receipts = await readdir(path.join(output, "data", "test", "quest_migrations"));
  assert.equal(receipts.length, 1, "migration writes a reward recovery receipt");
  const receipt = JSON.parse(await readFile(path.join(output, "data", "test", "quest_migrations", receipts[0]), "utf8"));
  assert.equal(receipt.quest, valid.id);
  assert.notEqual(receipt.from_fingerprint, receipt.to_fingerprint);
  assert.deepEqual(await readFile(path.join(output, "icon.png")), Buffer.from([1, 2, 3]));
  assert.notEqual(spawnSync(process.execPath, [command, input, "--out-dir", output]).status, 0);
  const numericInput = path.join(directory, "numeric-input");
  const numericOutput = path.join(directory, "numeric-output");
  for (const [relative, sourceText] of numericSources) {
    const file = path.join(numericInput, relative);
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, sourceText);
  }
  const numericRun = spawnSync(process.execPath, [command, numericInput, "--out-dir", numericOutput], { encoding: "utf8" });
  assert.equal(numericRun.status, 0, numericRun.stderr);
  const numericReceiptFiles = await readdir(path.join(numericOutput, "data/test/quest_migrations"));
  const actualReceipt = JSON.parse(await readFile(path.join(numericOutput, "data/test/quest_migrations", numericReceiptFiles[0]), "utf8"));
  assert.equal(actualReceipt.from_fingerprint, bundleFixture.before);
  assert.equal(actualReceipt.to_fingerprint, bundleFixture.after);
  assert.equal(await readFile(path.join(numericInput, numericQuestPath), "utf8"), bundleFixture.quest);
  assert.equal(await readFile(path.join(numericOutput, numericRewardPath), "utf8"), bundleFixture.reward);

} finally { await rm(directory, { recursive: true, force: true }); }
console.log(`Quest V3 migration checks and ${count} built-in schema checks passed.`);
