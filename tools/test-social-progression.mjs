import assert from "node:assert/strict";
import { readFile, mkdtemp, mkdir, writeFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { validateSocialProgression, collectSocialProgressions } from "./social-progression-validation.mjs";
import { validateConversation } from "./conversation-validation.mjs";
import { compileStory, resourceKind } from "./story-fragments.mjs";
import { validateStory } from "./story-authoring.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixture = path.join(root, "neoforge/src/gameTest/resources/campaign_fixture");
const profilePath = "data/campaign_fixture/quests/_shared/social_progression/training.json";
const owner = "data/campaign_fixture/quests/campaign_fixture/characters";
const conversationPath = owner + "/conversations/social_mentor.json";
const readJson = async file => JSON.parse(await readFile(file, "utf8"));
const schema = await readJson(path.join(root, "tools/schemas/social-progression-v1.schema.json"));
const conversationSchema = await readJson(path.join(root, "tools/schemas/conversation-v1.schema.json"));
const profile = await readJson(path.join(fixture, profilePath));
const graph = await readJson(path.join(fixture, owner, "conversations/mentor.json"));
const quest = await readJson(path.join(fixture, owner, "quest.json"));
const locale = await readJson(path.join(fixture, owner, "locales/en_us.json"));
const campaign = await readJson(path.join(fixture, "data/campaign_fixture/campaigns/characters/campaign.json"));
const context = { prefix: quest.localization_prefix, quest: quest.id, messages: locale.messages, campaign,
  progressions: new Map([[profile.id, profile]]) };
const cases = await readJson(path.join(fixture, "social_progression_cases.json"));
for (const entry of cases) {
  const value = structuredClone(entry.target === "profile" ? profile : graph);
  for (const patch of entry.patches) {
    const keys = patch.path.slice(1).split("/");
    const parent = keys.slice(0, -1).reduce((value, key) => value[key], value);
    if (patch.remove) delete parent[keys.at(-1)];
    else parent[keys.at(-1)] = structuredClone(patch.value);
  }
  const issues = entry.target === "profile" ? validateSocialProgression(value, schema) : validateConversation(value, conversationSchema, context);
  if (entry.valid) assert.deepEqual(issues, [], entry.name);
  else assert(issues.some(issue => issue.pointer === entry.pointer), entry.name + ": " + JSON.stringify(issues));
}
const resource = { file: path.join(fixture, profilePath), data: profile };
assert.deepEqual(collectSocialProgressions([resource], schema).issues, []);
const duplicate = collectSocialProgressions([resource, { ...resource, file: resource.file.replace("training.json", "duplicate.json") }], schema);
assert.equal(duplicate.progressions.size, 0);
assert(duplicate.issues.some(issue => issue.pointer === "/id"));
const foreign = collectSocialProgressions([{ ...resource, data: { ...profile, id: "another:profile" } }], schema);
assert(foreign.issues.some(issue => issue.pointer === "/id"));
assert.equal(resourceKind(profilePath), "social_progression");
assert.equal(resourceKind(owner + "/social_progression/training.json"), null);
const manifest = value => ({
  schema: "villagerretaliation:story_authoring/v1",
  resources: [{ path: "pack.mcmeta", value: { pack: { pack_format: 48, description: "Social progression fixture" } } },
    { path: profilePath, value }]
});
const compile = value => compileStory("story.json", new Map([["story.json", manifest(value)]]));
assert.deepEqual((await validateStory(compile(profile))).errors, []);
const invalidCurve = structuredClone(profile);
invalidCurve.skills[0].levels[1].experience = 0;
const fragmentIssues = await validateStory(compile(invalidCurve));
assert(fragmentIssues.errors.some(issue => issue.file === "story.json" && issue.pointer === "/resources/1/value/skills/0/levels/1/experience"));

const temporary = await mkdtemp(path.join(os.tmpdir(), "social-content-"));
const writeJson = async (file, value) => {
  const target = path.join(temporary, file);
  await mkdir(path.dirname(target), { recursive: true });
  await writeFile(target, JSON.stringify(value), "utf8");
  return target;
};
const cli = (...args) => spawnSync(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--quiet", ...args],
  { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120000 });
try {
  const file = await writeJson(profilePath, profile);
  let result = cli("--social-progression", file);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  await writeJson(owner + "/quest.json", quest);
  await writeJson(owner + "/locales/en_us.json", locale);
  await writeJson("data/campaign_fixture/campaigns/characters/campaign.json", campaign);
  const socialGraph = structuredClone(graph);
  socialGraph.checks[0].social = { progression: profile.id, skill: "persuasion", experience: 10 };
  const conversationFile = await writeJson(conversationPath, socialGraph);
  result = cli("--conversation", conversationFile);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  await writeJson(profilePath, null);
  result = cli("--social-progression", file);
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stderr, /expected object/);
  result = cli("--conversation", conversationFile);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /social\/progression: unknown social progression/);
  await writeJson(profilePath, invalidCurve);
  result = cli("--conversation", conversationFile);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /levels\/1\/experience/);
  await writeJson(profilePath, profile);
  const list = await writeJson("files.json", { social_progressions: [profilePath] });
  result = cli("--file-list", list);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  const privateFile = await writeJson(owner + "/social_progression/private.json", profile);
  result = cli("--social-progression", privateFile);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /belongs to quests\/_shared\/social_progression/);
} finally {
  assert.equal(path.dirname(temporary), path.resolve(os.tmpdir()));
  assert(path.basename(temporary).startsWith("social-content-"));
  await rm(temporary, { recursive: true, force: true });
}
console.log("Social progression checks passed (" + cases.length + " shared cases, references, fragments, and CLI diagnostics).");
