import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContentSchema } from "./content-schema-validation.mjs";
import { validateCompanionReferences } from "./campaign-companion-validation.mjs";
import { validateCampaignPack } from "./campaign-pack-validation.mjs";
import { validateConversation } from "./conversation-validation.mjs";

const read = async name => JSON.parse(await readFile(new URL(name, import.meta.url), "utf8"));
const schemas = Object.fromEntries(await Promise.all(["quest-v3", "conversation-v1", "scene-v1", "campaign-v1"].map(async name =>
  [name.split("-")[0], await read("./schemas/" + name + ".schema.json")])));
const cases = await read("../neoforge/src/gameTest/resources/campaign_fixture/companion_condition_cases.json");
const fixture = "../neoforge/src/gameTest/resources/campaign_fixture/data/campaign_fixture/";
const resources = [];
for (const relative of ["campaigns/investigation/campaign.json", "campaigns/investigation/locales/en_us.json",
  "quests/campaign_fixture/investigation/quest.json", "quests/campaign_fixture/investigation/locales/en_us.json",
  "quests/campaign_fixture/investigation/conversations/ledger.json", "quests/campaign_fixture/investigation/scenes/investigation_greeting.json"]) {
  resources.push({ file: fileURLToPath(new URL(fixture + relative, import.meta.url)), data: await read(fixture + relative) });
}
const [campaign, , quest, locale, graph, scene] = resources.map(resource => resource.data);
for (const entry of cases) {
  const changedQuest = structuredClone(quest);
  changedQuest.availability.conditions = [entry.condition];
  const changedGraph = structuredClone(graph);
  changedGraph.nodes[0].exchanges[0].conditions = [entry.condition];
  const changedScene = structuredClone(scene);
  changedScene.steps[0].data = { conditions: [{ type: "not", condition: entry.condition }] };
  for (const [kind, root, pointer] of [
    ["quest", changedQuest, "/availability/conditions/0"],
    ["conversation", changedGraph, "/nodes/0/exchanges/0/conditions/0"],
    ["scene", changedScene, "/steps/0/data/conditions/0/condition"]
  ]) {
    const issues = validateContentSchema(root, schemas[kind]);
    if (entry.valid) assert.deepEqual(issues, [], kind + ": " + entry.name);
    else assert(issues.some(issue => issue.pointer === pointer + entry.pointer),
      kind + ": " + entry.name + ": " + JSON.stringify(issues));
  }
}
const condition = { type: "campaign_companion", campaign: campaign.id, role: "mentor" };
const context = { campaign, campaigns: new Map([[campaign.id, campaign]]), quest: quest.id,
  prefix: quest.localization_prefix, messages: locale.messages };
const pack = () => validateCampaignPack(resources[0].file, campaign, resources, schemas);
assert.deepEqual(pack().issues, [], "fixture pack passes");
graph.nodes[0].exchanges[0].conditions = [{ type: "not", condition: { ...condition, role: "missing" } }];
assert(validateConversation(graph, schemas.conversation, context).some(issue =>
  issue.pointer === "/nodes/0/exchanges/0/conditions/0/condition/role"), "companion line retains its source pointer");
graph.nodes[0].exchanges[0].conditions[0].condition.role = "mentor";
assert.deepEqual(validateConversation(graph, schemas.conversation, context), [], "known companion reference resolves");
for (const owner of [resources[2], resources[5]]) {
  const root = owner === resources[2] ? owner.data.availability : owner.data.steps[0].data;
  root.conditions = [{ type: "not", condition: { ...condition, role: "missing" } }];
  assert(pack().issues.some(issue => issue.file === owner.file && issue.pointer.endsWith("/conditions/0/condition/role")),
    "quest and scene missing references retain source pointers");
  root.conditions[0].condition.role = "mentor";
  assert.deepEqual(pack().issues, [], "known companion reference resolves in the pack");
}
resources[5].file = fileURLToPath(new URL(fixture + "quests/_shared/scenes/companion.json", import.meta.url));
assert.deepEqual(pack().issues, [], "shared scene can reference an authored companion");
const foreign = { id: "other:story", characters: [{ id: "traveller" }] };
const crossCampaign = { conditions: [{ ...condition, campaign: foreign.id, role: "traveller" }] };
assert.deepEqual(validateCompanionReferences(crossCampaign, { campaigns: new Map([[foreign.id, foreign]]) }), [],
  "companion may be owned by another available campaign");
assert.equal(validateCompanionReferences(crossCampaign, context)[0].pointer, "/conditions/0/role",
  "missing campaign produces a reference diagnostic");
console.log(`Campaign companion checks passed (${cases.length} shared cases, conversation lines, and source diagnostics).`);
