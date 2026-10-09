import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContentSchema } from "./content-schema-validation.mjs";
import { validateEvidenceReferences } from "./campaign-evidence-validation.mjs";
import { validateCampaignPack } from "./campaign-pack-validation.mjs";
import { validateConversation } from "./conversation-validation.mjs";

const read = async name => JSON.parse(await readFile(new URL(name, import.meta.url), "utf8"));
const schemas = Object.fromEntries(await Promise.all(["quest-v2", "conversation-v1", "scene-v1", "campaign-v1"].map(async name =>
  [name.split("-")[0], await read("./datapack-builder/" + name + ".schema.json")])));
const cases = await read("../neoforge/src/gameTest/resources/campaign_fixture/evidence_condition_cases.json");
const fixture = "../neoforge/src/gameTest/resources/campaign_fixture/data/campaign_fixture/";
const quest = await read(fixture + "quests/campaign_fixture/investigation/quest.json");
const graph = await read(fixture + "quests/campaign_fixture/investigation/conversations/ledger.json");
const scene = await read(fixture + "quests/campaign_fixture/investigation/scenes/investigation_greeting.json");
for (const entry of cases) {
  const changedQuest = structuredClone(quest);
  changedQuest.availability.conditions = [entry.condition];
  const changedGraph = structuredClone(graph);
  changedGraph.topics[0].conditions = [entry.condition];
  const changedScene = structuredClone(scene);
  changedScene.steps[0].data = { conditions: [{ type: "not", condition: entry.condition }] };
  for (const [kind, root, pointer] of [
    ["quest", changedQuest, "/availability/conditions/0"],
    ["conversation", changedGraph, "/topics/0/conditions/0"],
    ["scene", changedScene, "/steps/0/data/conditions/0/condition"]
  ]) {
    const issues = validateContentSchema(root, schemas[kind]);
    if (entry.valid) assert.deepEqual(issues, [], kind + ": " + entry.name);
    else assert(issues.some(issue => issue.pointer === pointer + entry.pointer),
      kind + ": " + entry.name + ": " + JSON.stringify(issues));
  }
}
const resources = [];
for (const relative of ["campaigns/investigation/campaign.json", "campaigns/investigation/locales/en_us.json",
  "quests/campaign_fixture/investigation/quest.json", "quests/campaign_fixture/investigation/locales/en_us.json",
  "quests/campaign_fixture/investigation/conversations/ledger.json", "quests/campaign_fixture/investigation/scenes/investigation_greeting.json"]) {
  resources.push({ file: fileURLToPath(new URL(fixture + relative, import.meta.url)), data: await read(fixture + relative) });
}
const campaign = resources[0].data;
const condition = { type: "campaign_evidence", campaign: campaign.id, evidence: "ledger" };
const context = { campaign, campaigns: new Map([[campaign.id, campaign]]), quest: quest.id,
  prefix: quest.localization_prefix, messages: resources[3].data.messages };
assert.deepEqual(validateConversation(graph, schemas.conversation, context), [], "authored challenge compiles");
const pack = () => validateCampaignPack(resources[0].file, campaign, resources, schemas);
assert.deepEqual(pack().issues, [], "fixture pack passes");
const missingGraph = structuredClone(graph);
missingGraph.nodes[0].responses[0].conditions[0].evidence = "missing";
assert(validateConversation(missingGraph, schemas.conversation, context).some(issue =>
  issue.pointer === "/nodes/0/responses/0/conditions/0/evidence"), "conversation diagnostics identify the original response");
for (const owner of [resources[2], resources[5]]) {
  const root = owner === resources[2] ? owner.data.availability : owner.data.steps[0].data;
  root.conditions = [{ type: "not", condition: { ...condition, evidence: "missing" } }];
  assert(pack().issues.some(issue => issue.file === owner.file && issue.pointer.endsWith("/conditions/0/condition/evidence")),
    "quest and scene missing references retain source pointers");
  root.conditions[0].condition.evidence = "ledger";
  assert.deepEqual(pack().issues, [], "known evidence reference resolves");
}
resources[5].file = fileURLToPath(new URL(fixture + "quests/_shared/scenes/evidence.json", import.meta.url));
assert.deepEqual(pack().issues, [], "shared scenes can reference declared campaign evidence");
const foreign = { id: "other:story", evidence: [{ id: "testimony" }] };
const crossCampaign = { conditions: [{ ...condition, campaign: foreign.id, evidence: "testimony" }] };
assert.deepEqual(validateEvidenceReferences(crossCampaign, { campaigns: new Map([[foreign.id, foreign]]) }), [],
  "explicit references can read another available campaign");
assert.equal(validateEvidenceReferences(crossCampaign, context)[0].pointer, "/conditions/0/evidence",
  "missing campaign produces a reference diagnostic");
console.log(`Campaign evidence checks passed (${cases.length} shared cases, owned dialogue, and source diagnostics).`);
