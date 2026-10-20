import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { projectLegacyQuest, checkFrozenLocale } from "./quest-bundle-compatibility.mjs";

const { migrate } = createRequire(import.meta.url)("./quest-v3-format.js");
const frozen = {
  schema: "villagerretaliation:quest/v2", id: "test:delivery",
  metadata: { title: "Delivery", description: "Bring the supplies." },
  ui: { title: "Delivery tracker" },
  availability: { repeatable: true, consume_on_completion: true },
  entry_stage: "work",
  stages: [
    { id: "work", objectives: [{ id: "bring", type: "item", count: 3 }], next: "return",
      dialogue: { offer: { lines: ["Original provider line"] } } },
    { id: "return", objectives: [], next: { complete: true } }
  ]
};
const current = migrate(frozen);
current.availability.consume_on_completion = false;
current.stages[0].dialogue.offer.exchanges = [{ speaker: { type: "role", role: "provider" }, text: ["Original provider line"] }];
assert.deepEqual(projectLegacyQuest(current, frozen), frozen);
assert.equal(current.schema, "villagerretaliation:quest/v3");
assert.equal(frozen.availability.consume_on_completion, true);
for (const mutate of [
  quest => { quest.stages[0].objectives[0].count = 4; },
  quest => { delete quest.stages[0].objectives[0].id; },
  quest => { quest.stages.reverse(); },
  quest => { quest.stages.push({ id: "extra", objectives: [] }); },
  quest => { quest.availability.consume_on_completion = true; },
  quest => { quest.display.title = "Changed delivery"; },
  quest => { quest.stages[0].transition.stage = "missing"; },
  quest => { quest.stages[0].objectives[0].chance = 0; },
  quest => { quest.stages[0].dialogue.offer.exchanges[0].text = ["Changed provider line"]; }
]) {
  const changed = structuredClone(current);
  mutate(changed);
  assert.throws(() => projectLegacyQuest(changed, frozen), /Frozen quest contract/);
}

const cartographer = structuredClone(frozen);
cartographer.id = "villagerretaliation:blank_map_promise";
cartographer.events = [{ id: "offered", actions: [{ type: "notification", text: "Original" }] }];
const continued = migrate(cartographer);
continued.availability.consume_on_completion = false;
continued.events[0].actions.push({ type: "notification", text: "Continuation" });
assert.deepEqual(projectLegacyQuest(continued, cartographer), cartographer);
continued.events[0].actions[0].text = "Changed original";
assert.throws(() => projectLegacyQuest(continued, cartographer), /Frozen quest contract/);

checkFrozenLocale("test:title", "Original", "Original");
checkFrozenLocale("test:lines", { lines: ["First", "Second"] }, { lines: ["First", "Second"] });
for (const [expected, actual] of [
  ["Original", undefined], ["Original", "Changed"],
  [["First", "Second"], ["Second", "First"]],
  [["First"], ["First", "Extra"]],
  [{ lines: ["First"] }, { lines: ["First"], weight: 2 }]
]) assert.throws(() => checkFrozenLocale("test:unchanged", expected, actual), /Frozen quest contract/);
const key = "quest.cartographers_atlas.blank_map_promise.stage.gather_paper.dialogue.offer.lines";
checkFrozenLocale(key, ["Original"], ["Original", "Continuation"]);
assert.throws(() => checkFrozenLocale(key, ["Original"], ["Changed", "Continuation"]), /Frozen quest contract/);
console.log("Quest bundle compatibility passed: V3 projection, repeatability, original fields, ordered arrays, and frozen English payloads.");
