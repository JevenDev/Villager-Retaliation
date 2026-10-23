import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { materializeLocalizedDefinition } from "./quest-content.mjs";
import { validateQuestPerformance } from "./quest-performance-validation.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";

const schema = JSON.parse(await readFile(new URL("./schemas/quest-v3.schema.json", import.meta.url), "utf8")).properties.performance;
const quest = {
  stages: [{ id: "escort", followers: [{ actor: { alias: "companion" } }],
    objectives: [{ id: "arrive" }, { id: "supplies", optional: true }] }],
  performance: {
    measurements: {
      health: { metric: "lowest_health_percent", actor: "companion", objective: "escort.arrive" },
      speed: { metric: "elapsed_ticks", stage: "escort" },
      bonus: { metric: "optional_objectives", objective: "escort.supplies" },
      kills: { metric: "kills", entity_tags: ["minecraft:raiders"] }
    },
    tiers: [
      { id: "gold", requirements: { health: { min: 80 }, speed: { max: 1200 }, bonus: { min: 1 } },
        rewards: { experience: 0, loot_table: "example:gold" } },
      { id: "iron", requirements: { health: { min: 40 } }, rewards: { experience: 30 } },
      { id: "copper", requirements: { health: { min: 0 } }, rewards: {} }
    ]
  }
};
assert.deepEqual(validateContentSchema(quest.performance, schema), []);
assert.deepEqual(validateQuestPerformance(quest), []);
const actionFollower = structuredClone(quest);
actionFollower.stages[0].on_enter = actionFollower.stages[0].followers.map(follower => ({ type: "follower", command: "follow", ...follower }));
delete actionFollower.stages[0].followers;
assert.deepEqual(validateQuestPerformance(actionFollower), []);
for (const mutate of [
  q => q.performance.tiers.push(q.performance.tiers[0]),
  q => q.performance.measurements.health.actor = "missing",
  q => q.performance.measurements.speed.stage = "missing",
  q => q.performance.measurements.bonus.objective = "escort.arrive",
  q => q.performance.measurements.health.objective = "arrive",
  q => q.performance.measurements.speed.objective = "escort.arrive",
  q => q.performance.measurements.bonus.entity_types = ["minecraft:zombie"],
  q => q.performance.measurements.health.include_offline = true,
  q => q.performance.tiers[0].requirements.health.min = 101,
  q => q.performance.tiers[0].requirements.speed.min = 1201,
  q => q.performance.tiers[0].requirements.unknown = { min: 1 }
]) {
  const invalid = structuredClone(quest);
  mutate(invalid);
  assert(validateQuestPerformance(invalid).length > 0);
}
for (const mutate of [
  p => p.tiers[0].rewards.unknown = true,
  p => p.tiers[0].rewards.experience = -1,
  p => p.tiers[0].requirements.health = {},
  p => p.tiers[0].requirements.health.min = -1,
  p => p.measurements.speed.metric = "unknown",
  p => p.measurements["invalid id"] = { metric: "deaths" }
]) {
  const invalid = structuredClone(quest.performance);
  mutate(invalid);
  assert(validateContentSchema(invalid, schema).length > 0);
}
const fullSchema = JSON.parse(await readFile(new URL("./schemas/quest-v3.schema.json", import.meta.url), "utf8"));
for (const slug of ["escort", "speed", "optional_objectives"]) {
  const bundle = new URL("../example-packs/performance-rewards/data/performance_rewards/quests/performance_examples/" + slug + "/", import.meta.url);
  const example = JSON.parse(await readFile(new URL("quest.json", bundle), "utf8"));
  const locale = JSON.parse(await readFile(new URL("locales/en_us.json", bundle), "utf8"));
  assert.deepEqual(validateContentSchema(example, fullSchema), []);
  assert.deepEqual(validateQuestPerformance(example), []);
  const materialized = materializeLocalizedDefinition(example, example.localization_prefix, locale.messages);
  for (const tier of materialized.performance.tiers) assert.equal(typeof tier.title, "string");
  assert.equal(typeof materialized.display.title, "string");
}
console.log("Quest performance authoring and example pack checks passed");
