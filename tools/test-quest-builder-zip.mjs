import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const zip = require("./quest-builder/zip-utils.js");
const model = require("./quest-builder/quest-model.js");
const decoder = new TextDecoder();

async function testRoundTrip() {
  const quest = model.createBranchingQuest("zip_test");
  const path = model.questFilePath(quest);
  const packMeta = JSON.stringify({ pack: { pack_format: 34, description: "Zip test" } });
  const bytes = zip.createZip({
    "pack.mcmeta": packMeta,
    [path]: JSON.stringify(quest)
  }, { date: new Date("2025-01-01T00:00:00Z") });
  assert(bytes instanceof Uint8Array);
  assert.equal(new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(0, true), 0x04034b50);

  const files = await zip.readZip(bytes);
  assert.equal(decoder.decode(files["pack.mcmeta"]), packMeta);
  const quests = zip.decodeJsonFiles(files, (filePath) => filePath.includes("/quests/"));
  assert.equal(quests.length, 1);
  assert.equal(quests[0].path, path);
  assert.equal(quests[0].value.id, quest.id);
}

async function testWrappedPackNormalization() {
  const bytes = zip.createZip({
    "wrapped/pack.mcmeta": "{}",
    "wrapped/data/demo/quests/first.json": JSON.stringify(model.createLinearQuest("demo"))
  });
  const files = await zip.readZip(bytes);
  assert(Object.hasOwn(files, "pack.mcmeta"));
  assert(Object.hasOwn(files, "data/demo/quests/first.json"));
}

function testUnsafePaths() {
  assert.throws(() => zip.createZip({ "../outside.json": "{}" }), /Unsafe zip path/);
  assert.throws(() => zip.normalizePath("data/demo/../../outside.json"), /Unsafe zip path/);
}

await testRoundTrip();
await testWrappedPackNormalization();
testUnsafePaths();

console.log("Quest builder zip tests passed.");
