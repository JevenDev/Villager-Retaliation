import assert from "node:assert/strict";
import { readFile, mkdtemp, mkdir, writeFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { validateConversation, validateConversationSchema } from "./conversation-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixtureRoot = path.join(root, "neoforge/src/gameTest/resources/campaign_fixture");
const ownerPath = "data/campaign_fixture/quests/campaign_fixture/characters";
const conversationPath = `${ownerPath}/conversations/mentor.json`;
const campaignPath = "data/campaign_fixture/campaigns/characters/campaign.json";
const readJson = async file => JSON.parse(await readFile(file, "utf8"));
const schema = await readJson(path.join(root, "tools/datapack-builder/conversation-v1.schema.json"));
const questSchema = await readJson(path.join(root, "tools/datapack-builder/quest-v2.schema.json"));
const conversation = await readJson(path.join(fixtureRoot, conversationPath));
const quest = await readJson(path.join(fixtureRoot, ownerPath, "quest.json"));
const locale = await readJson(path.join(fixtureRoot, ownerPath, "locales/en_us.json"));
const campaign = await readJson(path.join(fixtureRoot, campaignPath));
const cases = await readJson(path.join(fixtureRoot, "conversation_cases.json"));
const context = { prefix: quest.localization_prefix, quest: quest.id, messages: locale.messages, campaign };

for (const entry of cases) {
  const changed = structuredClone(conversation);
  for (const patch of entry.patches) {
    const keys = patch.path.slice(1).split("/").map(key => key.replaceAll("~1", "/").replaceAll("~0", "~"));
    const parent = keys.slice(0, -1).reduce((value, key) => value[key], changed);
    if (patch.remove) delete parent[keys.at(-1)];
    else parent[keys.at(-1)] = structuredClone(patch.value);
  }
  const issues = validateConversation(changed, schema, context);
  if (entry.valid) assert.deepEqual(issues, [], entry.name);
  else assert(issues.some(issue => issue.pointer === entry.pointer), `${entry.name}: ${JSON.stringify(issues)}`);
}
for (const name of ["condition", "action", "localized_reference"]) assert.deepEqual(schema.$defs[name], questSchema.$defs[name], name);
assert.throws(() => validateConversationSchema({}, { properties: { unused: { unsupportedKeyword: true } } }), /Unsupported schema keyword/);
assert.throws(() => validateConversationSchema({}, { $ref: "#/$defs/missing" }), /Unresolved schema reference/);
assert.deepEqual(validateConversationSchema({ label: "🎲" }, { properties: { label: { minLength: 1, maxLength: 1 } } }), []);
const cyclic = { $defs: { loop: { $ref: "#/$defs/loop" } }, $ref: "#/$defs/loop" };
assert.match(validateConversationSchema({}, cyclic)[0].message, /validation limit/);
const cli = file => spawnSync(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--quiet", "--conversation", file], { encoding: "utf8", cwd: root });
const valid = cli(path.join(fixtureRoot, conversationPath));
assert.equal(valid.status, 0, valid.stdout + valid.stderr);

const temporary = await mkdtemp(path.join(os.tmpdir(), "conversation-content-"));
const writeJson = async (relative, value) => {
  const file = path.join(temporary, relative);
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, JSON.stringify(value), "utf8");
};
try {
  await writeJson(`${ownerPath}/quest.json`, quest);
  await writeJson(`${ownerPath}/locales/en_us.json`, locale);
  await writeJson(campaignPath, campaign);
  const run = async value => {
    await writeJson(conversationPath, value);
    return cli(path.join(temporary, conversationPath));
  };
  const malformed = await run(null);
  assert.equal(malformed.status, 1);
  assert.match(malformed.stderr, /expected object/);
  assert.doesNotMatch(malformed.stderr, /TypeError/);
  const unavailable = structuredClone(conversation);
  unavailable.campaign = "campaign_fixture:missing";
  const missingCampaign = await run(unavailable);
  assert.equal(missingCampaign.status, 1);
  assert.match(missingCampaign.stderr, /campaign content is unavailable/);
  const missing = structuredClone(conversation);
  missing.nodes[0].responses[1].next = "missing";
  const broken = await run(missing);
  assert.equal(broken.status, 1);
  assert.match(broken.stderr, /\/nodes\/0\/responses\/1\/next: unknown node missing/);
  const wrongRole = structuredClone(conversation);
  wrongRole.nodes[0].exchanges[0].speaker.role = "stranger";
  const invalidRole = await run(wrongRole);
  assert.equal(invalidRole.status, 1);
  assert.match(invalidRole.stderr, /speaker\/role: unknown campaign character stranger/);
  const questEffect = structuredClone(conversation);
  questEffect.nodes[1].effects[0].action = { type: "quest", action: "reminder" };
  const inherited = await run(questEffect);
  assert.equal(inherited.status, 0, inherited.stdout + inherited.stderr);
  const invalidEffect = structuredClone(conversation);
  invalidEffect.nodes[1].effects[0].action = { type: "memory" };
  const requiredField = await run(invalidEffect);
  assert.equal(requiredField.status, 1);
  assert.match(requiredField.stderr, /memory_event/);
  const shared = structuredClone(conversation);
  shared.id = "campaign_fixture:shared_mentor";
  shared.quest = quest.id;
  const expand = value => {
    if (!value || typeof value !== "object") return;
    if (typeof value.key === "string" && value.key.startsWith("#")) value.key = `campaign_fixture.shared.${value.key.slice(1)}`;
    for (const child of Object.values(value)) expand(child);
  };
  expand(shared);
  const sharedMessages = Object.fromEntries(Object.entries(locale.messages).map(([key, value]) => [key.replace(quest.localization_prefix, "campaign_fixture.shared"), value]));
  await writeJson("data/campaign_fixture/quests/_shared/locales/en_us.json", sharedMessages);
  await writeJson("data/campaign_fixture/quests/_shared/conversations/mentor.json", shared);
  const validShared = cli(path.join(temporary, "data/campaign_fixture/quests/_shared/conversations/mentor.json"));
  assert.equal(validShared.status, 0, validShared.stdout + validShared.stderr);
  const borrowed = structuredClone(shared);
  borrowed.id = "borrower:mentor";
  await writeJson("data/borrower/quests/_shared/conversations/mentor.json", borrowed);
  const sharedText = cli(path.join(temporary, "data/borrower/quests/_shared/conversations/mentor.json"));
  assert.equal(sharedText.status, 0, sharedText.stdout + sharedText.stderr);
  shared.nodes[1].effects[0].action = { type: "start_scene", scene: "campaign_fixture:private_scene", operation_id: "greeting", wait_for_result: true, required: true };
  await writeJson("data/campaign_fixture/quests/_shared/conversations/mentor.json", shared);
  await writeJson(`${ownerPath}/scenes/private.json`, { schema: "villagerretaliation:scene/v1", id: "campaign_fixture:private_scene" });
  const crossOwner = cli(path.join(temporary, "data/campaign_fixture/quests/_shared/conversations/mentor.json"));
  assert.equal(crossOwner.status, 1);
  assert.match(crossOwner.stderr, /private scene belongs to another quest bundle/);
  const sceneAction = structuredClone(conversation);
  sceneAction.nodes[1].effects[0].action = { type: "scene_start", scene: "private_scene", scene_operation_id: "greet" };
  const sceneAlias = await run(sceneAction);
  assert.equal(sceneAlias.status, 0, sceneAlias.stdout + sceneAlias.stderr);
  delete sceneAction.nodes[1].effects[0].action.scene_operation_id;
  const missingOperation = await run(sceneAction);
  assert.equal(missingOperation.status, 1);
  assert.match(missingOperation.stderr, /scene action requires a stable operation id/);
} finally {
  assert.equal(path.dirname(temporary), path.resolve(os.tmpdir()));
  assert(path.basename(temporary).startsWith("conversation-content-"));
  await rm(temporary, { recursive: true, force: true });
}
console.log(`Conversation content checks passed (${cases.length} shared cases, schema parity, and CLI diagnostics).`);
