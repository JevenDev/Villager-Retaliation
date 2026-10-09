import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateCampaignPack } from "./campaign-pack-validation.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";
import { validateTransactionReferences } from "./campaign-transaction-validation.mjs";

const read = async name => JSON.parse(await readFile(new URL(name, import.meta.url), "utf8"));
const schema = await read("./datapack-builder/scene-v1.schema.json");
const cases = await read("../neoforge/src/gameTest/resources/campaign_fixture/transaction_scene_cases.json");
for (const entry of cases) {
  const scene = {
    schema: "villagerretaliation:scene/v1", id: "campaign_fixture:payment_scene", ownership: "player",
    entry_step: "pay", actors: [], steps: [
      { id: "pay", type: "villagerretaliation:action_batch", data: entry.data, next: "done" },
      { id: "done", type: "villagerretaliation:scene_complete" }
    ]
  };
  const issues = validateContentSchema(scene, schema);
  if (entry.valid) assert.deepEqual(issues, [], entry.name);
  else assert(issues.some(issue => issue.pointer === "/steps/0/data" + entry.pointer), entry.name + ": " + JSON.stringify(issues));
}
const quest = { id: "campaign_fixture:characters", metadata: { campaign: "campaign_fixture:characters" } };
const campaign = { id: quest.id, quest_bundles: [quest.id],
  transactions: [{ id: "supplies", quest: quest.id, character: "mentor" }] };
const context = { quests: new Map([[quest.id, quest]]), campaigns: new Map([[campaign.id, campaign]]) };
const payment = { type: "campaign_payment", quest: quest.id, quest_id: "campaign_fixture:other", transaction: "supplies" };
assert.deepEqual(validateTransactionReferences({ actions: [payment] }, context), [], "quest takes precedence over quest_id");
const missing = validateTransactionReferences({ steps: [{ data: { actions: [{ ...payment, transaction: "missing" }] } }] }, context);
assert.equal(missing[0].pointer, "/steps/0/data/actions/0/transaction");
assert.match(missing[0].message, /unknown campaign transaction/);
const condition = { type: "campaign_transaction", campaign: campaign.id, quest: quest.id, transaction: "missing", state: "paid" };
const nested = validateTransactionReferences({ conditions: [{ type: "not", condition }] }, context);
assert.equal(nested[0].pointer, "/conditions/0/condition/transaction");
const fixture = "../neoforge/src/gameTest/resources/campaign_fixture/data/campaign_fixture/";
const resources = [];
for (const relative of ["campaigns/characters/campaign.json", "campaigns/characters/locales/en_us.json",
  "quests/campaign_fixture/characters/quest.json", "quests/campaign_fixture/characters/locales/en_us.json",
  "quests/campaign_fixture/characters/scenes/character_greeting.json"]) {
  resources.push({ file: fileURLToPath(new URL(fixture + relative, import.meta.url)), data: await read(fixture + relative) });
}
const packCampaign = resources[0];
packCampaign.data.transactions = [{ id: "supplies", title_key: "campaign_fixture.campaign.characters.mentor",
  quest: quest.id, character: "mentor", payment: { item: "minecraft:emerald", count: 8 }, bargained_count: 5 }];
const packScene = resources.at(-1);
packScene.data.steps[0].type = "villagerretaliation:action_batch";
packScene.data.steps[0].data = { actions: [{ ...payment, id: "buy" }] };
const schemas = { scene: schema, campaign: await read("./datapack-builder/campaign-v1.schema.json"),
  quest: await read("./datapack-builder/quest-v2.schema.json") };
const pack = () => validateCampaignPack(packCampaign.file, packCampaign.data, resources, schemas);
assert.deepEqual(pack().issues, [], "an owned scene can pay its declared transaction");
packScene.data.steps[0].data.actions[0].transaction = "missing";
assert(pack().issues.some(issue => issue.file === packScene.file && issue.pointer === "/steps/0/data/actions/0/transaction"
  && issue.message.includes("unknown campaign transaction")), "pack validation checks scene transaction references");
packScene.file = fileURLToPath(new URL(fixture + "quests/_shared/scenes/payment.json", import.meta.url));
assert(pack().issues.some(issue => issue.file === packScene.file && issue.pointer === "/steps/0/data/actions/0/transaction"),
  "shared scene references are checked with their explicit quest");
packScene.data.steps[0].data.actions[0].transaction = "supplies";
assert.deepEqual(pack().issues, [], "a shared scene can use its explicit transaction owner");
console.log(`Campaign transaction authoring checks passed (${cases.length} shared scene cases and reference diagnostics).`);
