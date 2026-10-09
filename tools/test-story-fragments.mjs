import assert from "node:assert/strict";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";
import { mkdtemp, cp, readFile, writeFile, mkdir, rm, symlink, readdir } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { compileStory, authoredLocation, projectPath, resourceKind, StoryCompileError } from "./story-fragments.mjs";
import { compileStoryProject, loadStoryProject, validateStory, storyPackBytes } from "./story-authoring.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixture = path.join(root, "tools/datapack-builder/test-fixtures/story-fragments");
const runtimeFixture = path.join(root, "neoforge/src/gameTest/resources/campaign_fixture");
const { readZip } = createRequire(import.meta.url)("./quest-builder/zip-utils.js");
const readJson = async file => JSON.parse(await readFile(file, "utf8"));
const quest = "data/campaign_fixture/quests/campaign_fixture/characters/quest.json";
const conversation = "data/campaign_fixture/quests/campaign_fixture/characters/conversations/mentor.json";
const campaign = "data/campaign_fixture/campaigns/characters/campaign.json";
const arg = name => ({ $arg: name });
const use = (name, args = {}) => ({ $use: name, args });
const metadata = { pack: { pack_format: 48, description: "Fragment test {player}" } };
const manifest = (value, fragments = {}) => ({
  schema: "villagerretaliation:story_authoring/v1", fragments,
  resources: [{ path: "pack.mcmeta", value: metadata }, { path: quest, value }]
});
const compile = (value, fragments, options) => compileStory("story.json", new Map([["story.json", manifest(value, fragments)]]), options);
const rejected = (fn, pattern) => {
  assert.throws(fn, error => error instanceof StoryCompileError && pattern.test(error.message), String(pattern));
};
const library = fragments => ({ schema: "villagerretaliation:story_fragments/v1", fragments });

const parameters = { text: "string", count: "integer", fraction: "number", flag: "boolean", record: "object", list: "array", empty: "null" };
const values = { text: "{player}", count: 3, fraction: 1.25, flag: false, record: { id: "fixed" }, list: [3, 1, 2], empty: null };
const typed = compile(use("example:typed", values), {
  "example:typed": { parameters, value: Object.fromEntries(Object.keys(parameters).map(key => [key, arg(key)])) }
});
assert.deepEqual(typed.outputs.get(quest), values);
assert.equal(authoredLocation(typed, quest, "/record/id").pointer, "/resources/1/value/args/record/id");
assert.equal(authoredLocation(typed, quest, "/record/missing").pointer, "/resources/1/value/args/record");
for (const [name, type] of Object.entries(parameters)) {
  const wrong = { ...values, [name]: type === "string" ? 42 : "wrong" };
  rejected(() => compile(use("example:typed", wrong), { "example:typed": { parameters, value: arg(name) } }), /requires/);
}
assert.deepEqual(compile({ $literal: { $use: "ordinary text", nested: { $arg: "still text" } } }).outputs.get(quest),
  { $use: "ordinary text", nested: { $arg: "still text" } });
assert.deepEqual(compile(["before", { $splice: use("example:list") }, "after"], {
  "example:list": { parameters: {}, value: ["second", "third"] }
}).outputs.get(quest), ["before", "second", "third", "after"]);
assert.deepEqual(compile(use("example:zero"), { "example:zero": { parameters: {}, value: 0 } }).outputs.get(quest), 0);
rejected(() => compile(use("missing:fragment")), /Unknown fragment/);
rejected(() => compile({ $arg: "missing" }), /Unknown fragment parameter/);
rejected(() => compile({ $use: "example:a", extra: true }), /mixed with ordinary fields/);
rejected(() => compile({ $literal: 1, $arg: "x" }), /exactly one directive/);
rejected(() => compile({ $splice: [] }), /only valid as an array element/);
rejected(() => compile([{ $splice: 4 }]), /requires an array/);
rejected(() => compile(use("example:a", { unexpected: true }), { "example:a": { parameters: {}, value: 1 } }), /exactly these arguments/);
rejected(() => compile(use("example:a"), { "example:a": { parameters: { required: "string" }, value: 1 } }), /exactly these arguments/);
rejected(() => compile(1, { "example:a": { parameters: {}, value: use("example:b") }, "example:b": { parameters: {}, value: use("example:a") } }), /Fragment cycle/);
rejected(() => compile(1, { "example:a": { parameters: {}, value: use("example:a") } }), /Fragment cycle/);
rejected(() => compile(1, { "example:a": { parameters: { "bad-name": "string" }, value: 1 } }), /Invalid parameter name/);
rejected(() => compile(1, { unqualified: { parameters: {}, value: 1 } }), /namespaced stable IDs/);
rejected(() => compile(1, undefined, { maxNodes: NaN }), /positive safe integer/);
rejected(() => compile([1, 2, 3], undefined, { maxNodes: 2 }), /node or depth limit/);
rejected(() => compile(Infinity), /finite/);
let deep = 0;
for (let i = 0; i < 70; i++) deep = [deep];
rejected(() => compile(deep), /depth limit/);
const exponential = {};
for (let i = 0; i < 18; i++) exponential["example:n" + i] = {
  parameters: {}, value: i === 17 ? [0, 1] : [use("example:n" + (i + 1)), use("example:n" + (i + 1))]
};
rejected(() => compile(use("example:n0"), exponential, { maxNodes: 2000 }), /node or depth limit/);

const cyclic = manifest({});
cyclic.libraries = ["a.json"];
const a = { ...library({}), libraries: ["b.json"] };
const b = { ...library({}), libraries: ["a.json"] };
rejected(() => compileStory("story.json", new Map([["story.json", cyclic], ["a.json", a], ["b.json", b]])), /Library cycle/);
const duplicate = { "example:a": { parameters: {}, value: "one" } };
rejected(() => compileStory("story.json", new Map([
  ["story.json", { ...manifest({}, duplicate), libraries: ["a.json"] }], ["a.json", library(duplicate)]
])), /Duplicate fragment/);
const duplicateOutput = manifest({});
duplicateOutput.resources.push(duplicateOutput.resources[1]);
rejected(() => compileStory("story.json", new Map([["story.json", duplicateOutput]])), /Duplicate output/);
const noPack = manifest({});
noPack.resources.shift();
rejected(() => compileStory("story.json", new Map([["story.json", noPack]])), /requires authored pack/);
const ambiguousSource = manifest({});
ambiguousSource.resources[1].source = "source.json";
rejected(() => compileStory("story.json", new Map([["story.json", ambiguousSource]])), /unsupported combination/);
for (const source of ["../escape.json", "/absolute.json", "C:/outside.json", "nested\\file.json", "a//b.json", "./a.json"]) {
  rejected(() => projectPath("story.json", source), /Source paths/);
}
assert.equal(projectPath("fragments/library.json", "shared.json"), "fragments/shared.json");
for (const file of [
  "data/../campaigns/a/campaign.json", "data/example/quests/line/../quest.json", "assets/example/lang/en_us.json",
  "data/example/quests/line/_shared/quest.json", "data/example/quests/line/a/pools/pool.json", "data/Example/quests/line/a/quest.json"
]) assert.equal(resourceKind(file), null, file);
for (const [file, kind] of [
  ["data/example/quests/_shared/conversations/hello.json", "conversation"],
  ["data/example/quests/_shared/pools/offers.json", "pool"],
  ["data/example/campaigns/series/chapter/campaign.json", "campaign"],
  ["data/example/campaigns/series/chapter/locales/en_us.json", "locale"]
]) assert.equal(resourceKind(file), kind);
assert.deepEqual(validateContentSchema({ valid: 1 }, { propertyNames: { pattern: "^[a-z]+$" } }), []);
assert.equal(validateContentSchema({ "bad/name": 1 }, { propertyNames: { pattern: "^[a-z]+$" } })[0].pointer, "/bad~1name");
assert.equal(validateContentSchema({ "": 1 }, { propertyNames: { minLength: 1 } }).length, 1);
assert.equal(validateContentSchema({ any: 1 }, { propertyNames: false }).length, 1);

const project = await loadStoryProject(path.join(fixture, "story.json"));
const compiled = compileStory(project.manifestFile, project.sources);
for (const [file, value] of compiled.outputs) assert.deepEqual(value, await readJson(path.join(runtimeFixture, file)), file);
const welcome = authoredLocation(compiled, conversation, "/nodes/0/exchanges/0/text/key");
assert.deepEqual({ file: welcome.file, pointer: welcome.pointer },
  { file: "content/mentor.json", pointer: "/nodes/0/exchanges/0/$splice/args/greeting" });
assert(welcome.via.some(entry => entry.file === "content/mentor.json"));
assert(welcome.via.some(entry => entry.file === "fragments/conversation.json"));
assert.equal(welcome.via.at(-1).pointer, "/fragments/campaign_fixture:opening/value/0/text");
const camera = authoredLocation(compiled, conversation, "/nodes/0/exchanges/0/camera/zoom");
assert.deepEqual({ file: camera.file, pointer: camera.pointer },
  { file: "fragments/conversation.json", pointer: "/fragments/campaign_fixture:opening/value/0/camera/zoom" });
const voice = authoredLocation(compiled, conversation, "/nodes/0/exchanges/2/voices/en_us");
assert.deepEqual({ file: voice.file, pointer: voice.pointer },
  { file: "content/mentor.json", pointer: "/nodes/0/exchanges/1/voices/en_us" });
assert.equal(new Set(welcome.via.map(entry => entry.file + entry.pointer)).size, welcome.via.length);
const role = authoredLocation(compiled, campaign, "/characters/0/loss_policy");
assert.equal(role.file, "fragments/common.json");
assert.equal(role.pointer, "/fragments/campaign_fixture:resident/value/loss_policy");
const bytes = storyPackBytes(compiled);
assert.deepEqual(storyPackBytes(compileStory(project.manifestFile, project.sources)), bytes);
const files = await readZip(bytes);
for (const [file, value] of compiled.outputs) assert.deepEqual(JSON.parse(new TextDecoder().decode(files[file])), value);
const sourceMap = JSON.parse(new TextDecoder().decode(files["story-sources.json"]));
assert.equal(sourceMap.schema, "villagerretaliation:story_sources/v1");
assert.deepEqual(sourceMap.resources[conversation]["/nodes/0/exchanges/0/text/key"], welcome);
assert.doesNotMatch(new TextDecoder().decode(files["story-sources.json"]), /C:\\\\|story-validation-/);

const temporary = await mkdtemp(path.join(os.tmpdir(), "story-authoring-test-"));
const checkDiagnostics = async (sources, check) => {
  const result = await validateStory(compileStory(project.manifestFile, sources));
  assert(result.errors.length, "invalid expanded content must fail");
  check(result.errors);
};
try {
  const sources = structuredClone(project.sources);
  sources.get("fragments/common.json").fragments["campaign_fixture:resident"].value.source = "unsupported";
  await checkDiagnostics(sources, issues => assert(issues.some(issue =>
    issue.file === "fragments/common.json" && issue.pointer === "/fragments/campaign_fixture:resident/value/source"
      && issue.generated.file === campaign && issue.via.length)));
  const invalidArgument = structuredClone(project.sources);
  invalidArgument.get("content/mentor.json").nodes[0].exchanges[0].$splice.args.role = "stranger";
  await checkDiagnostics(invalidArgument, issues => assert(issues.some(issue =>
    issue.file === "content/mentor.json" && issue.pointer.endsWith("/args/role") && /unknown.*role|unknown.*character/i.test(issue.message)), JSON.stringify(issues)));
  const invalidDestination = structuredClone(project.sources);
  invalidDestination.get("content/mentor.json").nodes[0].responses[1].next = "absent";
  await checkDiagnostics(invalidDestination, issues => assert(issues.some(issue =>
    issue.file === "content/mentor.json" && issue.pointer === "/nodes/0/responses/1/next" && /unknown node/.test(issue.message))));
  const pack = path.join(temporary, "story.zip");
  const result = await compileStoryProject(path.join(fixture, "story.json"), { output: pack });
  assert.equal(result.outputs.size, 7);
  assert.deepEqual(await readFile(pack), Buffer.from(bytes));
  await assert.rejects(compileStoryProject(path.join(fixture, "story.json"), { output: pack }), /already exists/);
  assert.deepEqual(await readFile(pack), Buffer.from(bytes));
  await compileStoryProject(path.join(fixture, "story.json"), { output: pack, overwrite: true });
  assert.deepEqual(await readFile(pack), Buffer.from(bytes));
  const projectCopy = path.join(temporary, "project");
  await cp(fixture, projectCopy, { recursive: true });
  const source = path.join(projectCopy, "content/mentor.json");
  const broken = await readJson(source);
  broken.nodes[0].responses[1].next = "missing";
  await writeFile(source, JSON.stringify(broken));
  await assert.rejects(compileStoryProject(path.join(projectCopy, "story.json"), { output: pack, overwrite: true }), error =>
    error instanceof StoryCompileError && error.diagnostics.some(issue => issue.pointer === "/nodes/0/responses/1/next"));
  assert.deepEqual(await readFile(pack), Buffer.from(bytes));
  assert(!(await readdir(temporary)).some(name => name.startsWith(".story-output-")));
  await writeFile(source, "{");
  await assert.rejects(loadStoryProject(path.join(projectCopy, "story.json")), /Invalid JSON/);
  const outside = path.join(temporary, "outside");
  await mkdir(outside);
  await writeFile(path.join(outside, "library.json"), JSON.stringify(library({})));
  const escape = path.join(projectCopy, "escape");
  await symlink(outside, escape, process.platform === "win32" ? "junction" : "dir");
  await writeFile(path.join(projectCopy, "story.json"), JSON.stringify({ ...manifest({}), libraries: ["escape/library.json"] }));
  await assert.rejects(loadStoryProject(path.join(projectCopy, "story.json")), /outside the authoring project/);
  await rm(escape);
  const cli = (...args) => spawnSync(process.execPath, [path.join(root, "tools/compile-story.mjs"), ...args], { encoding: "utf8", cwd: root });
  assert.equal(cli("--help").status, 0);
  assert.match(cli("--overwrite", path.join(fixture, "story.json")).stderr, /requires --output/);
  assert.match(cli("--output").stderr, /requires one ZIP/);
  assert.match(cli("--unknown").stderr, /Unsupported option/);
  const checked = cli(path.join(fixture, "story.json"));
  assert.equal(checked.status, 0, checked.stderr);
  assert.match(checked.stdout, /7 resources validated/);
  const fileList = path.join(temporary, "files.json");
  const validateCli = (...args) => spawnSync(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--json", ...args], { encoding: "utf8", cwd: root });
  await writeFile(fileList, JSON.stringify({ campaigns: [path.relative(temporary, path.join(runtimeFixture, campaign))] }));
  const accepted = validateCli("--file-list", fileList);
  assert.equal(accepted.status, 0, accepted.stderr + accepted.stdout);
  assert.deepEqual(JSON.parse(accepted.stdout).errors, []);
  await writeFile(fileList, JSON.stringify({ campaigns: "wrong" }));
  const invalid = validateCli("--file-list", fileList);
  assert.equal(invalid.status, 1);
  assert(JSON.parse(invalid.stdout).errors.some(message => /file list/.test(message)));
} finally {
  assert.equal(path.dirname(temporary), path.resolve(os.tmpdir()));
  await rm(temporary, { recursive: true, force: true });
}
console.log("Story fragment checks passed (typed expansion, ordering, source maps, runtime fixture parity, validation, paths, and pack publishing).");
