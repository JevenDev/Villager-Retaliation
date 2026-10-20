import assert from "node:assert/strict";
import { readFile, readdir, mkdtemp, cp, writeFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { scenarioPath, scenarioContext, validateCampaignScenario } from "./campaign-scenario-validation.mjs";
import { compileStory, resourceKind } from "./story-fragments.mjs";
import { loadStoryProject, validateStory } from "./story-authoring.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixture = path.join(root, "neoforge/src/gameTest/resources/campaign_fixture");
const readJson = async file => JSON.parse(await readFile(file, "utf8"));
const schema = await readJson(path.join(root, "tools/schemas/campaign-scenario-v1.schema.json"));
const resources = [];
async function readResources(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) await readResources(file);
    else if (entry.name.endsWith(".json")) resources.push({ file, data: await readJson(file) });
  }
}
await readResources(path.join(fixture, "data"));
const context = scenarioContext(resources);
const scenarios = new Map(resources.filter(resource => scenarioPath(resource.file)).map(resource => [resource.data.id.split(":")[1], resource]));
for (const resource of scenarios.values()) assert.deepEqual(validateCampaignScenario(resource.data, schema, context), [], resource.file);
const cases = await readJson(path.join(fixture, "campaign_scenario_cases.json"));
for (const test of cases) {
  const changed = structuredClone(scenarios.get(test.fixture).data);
  for (const patch of test.patches) {
    const keys = patch.path.slice(1).split("/").map(key => key.replaceAll("~1", "/").replaceAll("~0", "~"));
    const parent = keys.slice(0, -1).reduce((value, key) => value[key], changed);
    if (patch.remove) delete parent[keys.at(-1)];
    else parent[keys.at(-1)] = structuredClone(patch.value);
  }
  const issues = validateCampaignScenario(changed, schema, test.catalog ? context : undefined);
  assert.equal(issues.length === 0, test.valid, test.name + ": " + JSON.stringify(issues));
  if (test.pointer) assert(issues.some(issue => issue.pointer === test.pointer), test.name + ": " + JSON.stringify(issues));
}
const relative = "data/campaign_fixture/campaign_scenarios/conversation_success.json";
assert.equal(resourceKind(relative), "scenario");
assert.equal(scenarioPath(path.join(fixture, relative)).id, "campaign_fixture:conversation_success");
assert.equal(scenarioPath(path.join(fixture, "scenario.json")), null);
const project = await loadStoryProject(path.join(root, "tools/test-fixtures/story-fragments/story.json"));
const manifest = project.sources.get(project.manifestFile);
const source = structuredClone(scenarios.get("conversation_success").data);
const choice = source.steps.findIndex(step => step.id === "appeal");
manifest.fragments = {
  ...manifest.fragments,
  "example:choose": { parameters: { response: "string" }, value: { ...source.steps[choice], argument: { $arg: "response" } } }
};
source.steps[choice] = { $use: "example:choose", args: { response: "appeal" } };
const resourceIndex = manifest.resources.length;
manifest.resources.push({ path: relative, value: source });
const compiled = compileStory(project.manifestFile, project.sources);
assert.deepEqual((await validateStory(compiled)).errors, []);
source.steps[choice].args.response = "missing";
const rejected = await validateStory(compileStory(project.manifestFile, project.sources));
const expected = "/resources/" + resourceIndex + "/value/steps/" + choice + "/args/response";
assert(rejected.errors.some(issue => issue.file === project.manifestFile && issue.pointer === expected && /unknown conversation response/.test(issue.message)),
  JSON.stringify(rejected.errors));

const temporary = await mkdtemp(path.join(os.tmpdir(), "campaign-scenarios-"));
const cli = args => spawnSync(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--json", ...args],
  { encoding: "utf8", cwd: root, windowsHide: true, timeout: 300000 });
const report = result => {
  assert(!result.error, String(result.error));
  assert.equal(result.stderr.trim(), "");
  return JSON.parse(result.stdout);
};
try {
  const list = path.join(temporary, "files.json");
  await writeFile(list, JSON.stringify({ scenarios: [...scenarios.values()].map(resource => resource.file) }));
  const all = cli(["--file-list", list]);
  assert.equal(all.status, 0, all.stdout + all.stderr);
  assert.deepEqual(report(all).errors, []);
  await cp(path.join(fixture, "data"), path.join(temporary, "data"), { recursive: true });
  const target = path.join(temporary, relative);
  const invalid = structuredClone(scenarios.get("conversation_success").data);
  invalid.steps[choice].argument = "missing";
  await writeFile(target, JSON.stringify(invalid));
  const broken = cli(["--scenario", target]);
  assert.equal(broken.status, 1);
  assert(report(broken).errors.some(error => error.includes("/steps/" + choice + "/argument: unknown conversation response")));
  invalid.steps[choice].argument = "appeal";
  invalid.id = "campaign_fixture:wrong_path";
  await writeFile(target, JSON.stringify(invalid));
  const wrongPath = cli(["--scenario", target]);
  assert.equal(wrongPath.status, 1);
  assert(report(wrongPath).errors.some(error => error.includes("/id: scenario ID must match its resource path")));
  await writeFile(target, "null");
  const malformed = cli(["--scenario", target]);
  assert.equal(malformed.status, 1);
  assert(report(malformed).errors.some(error => error.includes("expected object")));
  const missing = cli(["--scenario"]);
  assert.equal(missing.status, 1);
  assert(report(missing).errors.some(error => error.includes("--scenario requires")));
} finally {
  assert.equal(path.dirname(temporary), os.tmpdir());
  await rm(temporary, { recursive: true, force: true });
}
console.log("Campaign scenario authoring checks passed: " + scenarios.size + " scenarios and " + cases.length + " shared cases.");
