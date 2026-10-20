import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const zip = require("./zip-utils.js");
const decoder = new TextDecoder();

async function testRoundTrip() {
  const quest = { schema: "villagerretaliation:quest/v3", id: "zip_test:round_trip" };
  const locale = { schema: "villagerretaliation:quest_locale/v1", messages: { "zip_test.title": "Round trip" } };
  const path = "data/zip_test/quests/examples/round_trip/quest.json";
  const localePath = "data/zip_test/quests/examples/round_trip/locales/en_us.json";
  const packMeta = JSON.stringify({ pack: { pack_format: 48, description: "Zip test" } });
  const bytes = zip.createZip({
    "pack.mcmeta": packMeta,
    [path]: JSON.stringify(quest),
    [localePath]: JSON.stringify(locale)
  }, { date: new Date("2025-01-01T00:00:00Z") });
  assert(bytes instanceof Uint8Array);
  assert.equal(new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(0, true), 0x04034b50);

  const files = await zip.readZip(bytes);
  assert.equal(decoder.decode(files["pack.mcmeta"]), packMeta);
  const quests = zip.decodeJsonFiles(files, (filePath) => filePath.includes("/quests/"));
  assert.equal(quests.length, 2);
  const questEntry = quests.find((entry) => entry.path === path);
  assert(questEntry);
  assert(quests.some((entry) => entry.path === localePath));
  assert.deepEqual(questEntry.value, quest);
  assert.deepEqual(quests.find((entry) => entry.path === localePath).value, locale);
}

async function testWrappedPackNormalization() {
  const bytes = zip.createZip({
    "wrapped/pack.mcmeta": "{}",
    "wrapped/data/demo/quests/quests/a_helping_hand/quest.json": JSON.stringify({ id: "demo:a_helping_hand" })
  });
  const files = await zip.readZip(bytes);
  assert(Object.hasOwn(files, "pack.mcmeta"));
  assert(Object.hasOwn(files, "data/demo/quests/quests/a_helping_hand/quest.json"));
}

function testUnsafePaths() {
  assert.throws(() => zip.createZip({ "../outside.json": "{}" }), /Unsafe zip path/);
  assert.throws(() => zip.normalizePath("data/demo/../../outside.json"), /Unsafe zip path/);
}

await testRoundTrip();
await testWrappedPackNormalization();
testUnsafePaths();

console.log("Quest zip tests passed.");
