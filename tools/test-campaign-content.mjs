import assert from "node:assert/strict";
import { readFile, mkdtemp, cp, writeFile, rm, mkdir } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { validateCampaign } from "./campaign-validation.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixture = path.join(root, "neoforge/src/gameTest/resources/campaign_fixture");
const data = "data/campaign_fixture/";
const campaignPath = data + "campaigns/reconciliation/campaign.json";
const readJson = async file => JSON.parse(await readFile(file, "utf8"));
const schema = await readJson(path.join(root, "tools/datapack-builder/campaign-v1.schema.json"));
const scenes = await readJson(path.join(root, "tools/datapack-builder/scene-v1.schema.json"));
const cases = await readJson(path.join(fixture, "campaign_authoring_cases.json"));
const quests = new Map();
for (const name of ["decision", "dragon", "decision_catch_up", "dragon_catch_up"]) {
  const quest = await readJson(path.join(fixture, data, "quests/campaign_fixture", name, "quest.json"));
  quests.set(quest.id, quest);
}
for (const entry of cases) {
  const definition = await readJson(path.join(fixture, data, "campaigns", entry.fixture, "campaign.json"));
  for (const patch of entry.patches) {
    const keys = patch.path.slice(1).split("/").map(key => key.replaceAll("~1", "/").replaceAll("~0", "~"));
    const target = keys.slice(0, -1).reduce((value, key) => value[key], definition);
    if (patch.remove) {
      if (Array.isArray(target)) target.splice(Number(keys.at(-1)), 1);
      else delete target[keys.at(-1)];
    } else target[keys.at(-1)] = structuredClone(patch.value);
  }
  const issues = validateCampaign(definition, schema, entry.catalog ? { quests } : {});
  if (entry.valid) assert.deepEqual(issues, [], entry.name);
  else assert(issues.some(issue => issue.pointer === entry.pointer), entry.name + ": " + JSON.stringify(issues));
}
assert.equal(validateContentSchema({}, { minProperties: 1 }).length, 1);
assert.equal(validateContentSchema({ a: true }, { maxProperties: 0 }).length, 1);
const actorDefaults = await readJson(path.join(fixture, data, "quests/campaign_fixture/characters/scenes/character_greeting.json"));
assert.deepEqual(validateContentSchema(actorDefaults, scenes), []);
actorDefaults.actors.forEach(actor => { delete actor.binding_source; delete actor.replacement_policy; });
assert.deepEqual(validateContentSchema(actorDefaults, scenes), []);
const cli = (...args) => spawnSync(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--quiet", ...args], { encoding: "utf8", cwd: root });
const passed = result => assert.equal(result.status, 0, result.stdout + result.stderr);
const rejected = (result, pattern) => {
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stderr, pattern);
  assert.doesNotMatch(result.stderr, /TypeError|SyntaxError: Unexpected/);
};
for (const name of ["characters", "following", "investigation", "locations", "losses", "reconciliation", "replacements", "spawns", "structures", "variants", "standing"]) {
  passed(cli("--campaign", path.join(fixture, data, "campaigns", name, "campaign.json")));
}
rejected(cli("--campaign"), /requires a JSON file path/);
const temporary = await mkdtemp(path.join(os.tmpdir(), "campaign-authoring-"));
try {
  await cp(path.join(fixture, "data"), path.join(temporary, "data"), { recursive: true });
  const run = (campaign = campaignPath) => cli("--campaign", path.join(temporary, campaign));
  const mutate = async (file, change, check, target = campaignPath) => {
    const absolute = path.join(temporary, file);
    const before = await readFile(absolute);
    try {
      await writeFile(absolute, JSON.stringify(change(JSON.parse(before.toString("utf8")))), "utf8");
      check(run(target));
    } finally { await writeFile(absolute, before); }
  };
  passed(run());
  await mutate(campaignPath, () => null, result => rejected(result, /expected object/));
  await mutate(campaignPath, value => { delete value.milestones[0].catch_up; return value; }, result => rejected(result, /\/milestones\/0\/catch_up/));
  await mutate(campaignPath, value => { value.milestones[0].catch_up.stage = "missing"; return value; }, result => rejected(result, /catch-up stage does not exist/));
  const questPath = data + "quests/campaign_fixture/decision/quest.json";
  await mutate(questPath, value => { value.story_arc = "campaign_fixture:wrong"; return value; }, result => rejected(result, /story_arc must identify/));
  await mutate(questPath, value => { value.stages = null; return value; }, result => rejected(result, /expected array/));
  const english = data + "campaigns/reconciliation/locales/en_us.json";
  await mutate(english, value => { delete value.messages["campaign_fixture.reconciliation.title"]; return value; }, result => rejected(result, /English localization is missing/));
  await mutate(english, value => { value.messages["foreign.title"] = "Not owned"; return value; }, result => rejected(result, /owned outside this campaign/));
  await mutate(english, value => { value.messages["campaign_fixture.reconciliation.title"] = { translate: "other.key" }; return value; }, result => rejected(result, /not plain text/));
  const duplicate = path.join(temporary, data, "campaigns/duplicate/campaign.json");
  await mkdir(path.dirname(duplicate), { recursive: true });
  await cp(path.join(temporary, campaignPath), duplicate);
  rejected(run(), /campaign id is already defined/);
  await writeFile(duplicate, "null", "utf8");
  rejected(run(), /expected object/);
  await rm(duplicate);
  const french = path.join(temporary, data, "campaigns/reconciliation/locales/fr_fr.json");
  await writeFile(french, "null", "utf8");
  rejected(run(), /locale root and message payloads/);
  await rm(french);
  const collision = path.join(temporary, data, "quests/_shared/locales/fr_fr.json");
  await mkdir(path.dirname(collision), { recursive: true });
  await writeFile(collision, JSON.stringify({ "campaign_fixture.reconciliation.title": "Borrowed" }), "utf8");
  rejected(run(), /also owned outside this campaign/);
  await rm(collision);
  const characterCampaign = data + "campaigns/characters/campaign.json";
  const characterQuest = data + "quests/campaign_fixture/characters/quest.json";
  await mutate(characterQuest, value => { value.provider.campaign_role = "stranger"; return value; },
    result => rejected(result, /unknown campaign character stranger/), characterCampaign);
  const actorFile = data + "quests/campaign_fixture/characters/scenes/character_greeting.json";
  await mutate(actorFile, value => {
    value.actors.forEach(actor => { actor.source = actor.binding; delete actor.binding; });
    return value;
  }, passed, characterCampaign);
  await mutate(actorFile, value => { value.actors[0].binding = "stranger"; return value; },
    result => rejected(result, /unknown campaign character stranger/), characterCampaign);
  await mutate(actorFile, value => { value.actors = [null]; return value; },
    result => rejected(result, /expected object/), characterCampaign);
  const investigationQuest = data + "quests/campaign_fixture/investigation/quest.json";
  await mutate(investigationQuest, value => {
    for (const stage of value.stages) for (const objective of stage.objectives ?? []) if (objective.story?.evidence) objective.story.evidence = "missing";
    return value;
  }, result => rejected(result, /unknown campaign evidence source missing/), data + "campaigns/investigation/campaign.json");
  const followQuest = data + "quests/campaign_fixture/following/quest.json";
  await mutate(followQuest, value => {
    for (const stage of value.stages) for (const objective of stage.objectives ?? []) if (objective.story?.step) objective.story.step = "finish";
    return value;
  }, result => rejected(result, /authored follow_actor step/), data + "campaigns/following/campaign.json");
  const conversation = data + "quests/campaign_fixture/characters/conversations/mentor.json";
  await mutate(conversation, value => { value.nodes[0].responses[0].next = "missing"; delete value.nodes[0].responses[0].check; return value; },
    result => rejected(result, /unknown node missing/), characterCampaign);
  const standingCampaign = data + "campaigns/standing/campaign.json";
  const standingConversation = data + "quests/campaign_fixture/standing/conversations/standing.json";
  await mutate(standingConversation, value => { value.topics[1].conditions[0].reaction = "missing"; return value; },
    result => rejected(result, /\/topics\/1\/conditions\/0\/reaction: unknown standing reaction/), standingCampaign);
  await mutate(standingConversation, value => { value.nodes[0].responses[0].effects[0].action.subject = "missing"; return value; },
    result => rejected(result, /\/nodes\/0\/responses\/0\/effects\/0\/action\/subject: unknown campaign standing/), standingCampaign);
  await mutate(standingConversation, value => { value.nodes[0].responses[0].effects[0].action.quest = "campaign_fixture:missing"; return value; },
    result => rejected(result, /standing action requires a quest declared by its campaign/), standingCampaign);
  await mutate(data + "campaigns/standing/locales/en_us.json", value => { delete value.messages["campaign_fixture.campaign.standing.reaction.trusted"]; return value; },
    result => rejected(result, /English localization is missing/), standingCampaign);
  passed(cli("--campaign", path.join(temporary, campaignPath), "--campaign", path.join(temporary, characterCampaign)));
  passed(cli("--quest", path.join(temporary, questPath), "--campaign", path.join(temporary, campaignPath)));
  passed(cli("--conversation", path.join(temporary, conversation), "--campaign", path.join(temporary, characterCampaign)));
} finally {
  await rm(temporary, { recursive: true, force: true });
}
const standingCases = await readJson(path.join(fixture, "standing_cases.json"));
const questSchema = await readJson(path.join(root, "tools/datapack-builder/quest-v3.schema.json"));
for (const test of standingCases) {
  const quest = await readJson(path.join(fixture, data, "quests/campaign_fixture/standing/quest.json"));
  if (test.kind === "action") quest.stages[0].on_enter = [test.input];
  else quest.availability.conditions = [{ type: "not", condition: test.input }];
  assert.equal(validateContentSchema(quest, questSchema).length === 0, test.valid, test.name);
}
console.log("Campaign authoring checks passed (" + cases.length + " campaign cases, " + standingCases.length + " standing field cases, fixture campaigns, and CLI diagnostics).");
