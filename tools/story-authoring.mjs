import path from "node:path";
import os from "node:os";
import { readFile, realpath, stat, lstat, mkdtemp, mkdir, writeFile, link, rename, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { createRequire } from "node:module";
import { compileStory, projectPath, resourceKind, authoredLocation, StoryCompileError } from "./story-fragments.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const { createZip } = createRequire(import.meta.url)("./zip-utils.js");
const schemas = new Map(await Promise.all(Object.entries({
  quest: "quest-v3", quest_v2: "quest-v2", campaign: "campaign-v1", conversation: "conversation-v1", scenario: "campaign-scenario-v1", scene: "scene-v1",
  encounter: "encounter-v1", reward: "quest-reward-v1", pool: "quest-pool-v1", locale: "quest-locale-v1", social_progression: "social-progression-v1"
}).map(async ([kind, name]) => [kind, JSON.parse(await readFile(path.join(root, "tools/schemas", name + ".schema.json"), "utf8"))])));
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const portable = file => file.replaceAll(path.sep, "/");
const inside = (directory, file) => {
  const relative = path.relative(directory, file);
  return relative !== ".." && !relative.startsWith(".." + path.sep) && !path.isAbsolute(relative);
};
const fail = (file, message) => { throw new StoryCompileError(file, "", message); };

export async function loadStoryProject(manifest) {
  const absolute = await realpath(manifest);
  const directory = path.dirname(absolute);
  const manifestFile = path.basename(absolute);
  const sources = new Map();
  let totalBytes = 0;
  const load = async (file, library = false, depth = 0) => {
    if (sources.has(file)) return;
    if (sources.size >= 4096 || depth > 64) fail(file, "Authoring project exceeds its file or import depth limit.");
    const actual = await realpath(path.join(directory, file));
    if (!inside(directory, actual)) fail(file, "Source resolves outside the authoring project.");
    const info = await stat(actual);
    if (!info.isFile() || info.size > 4 * 1024 * 1024) fail(file, "Each source must be a regular JSON file no larger than 4 MiB.");
    const bytes = await readFile(actual);
    totalBytes += bytes.length;
    if (bytes.length > 4 * 1024 * 1024 || totalBytes > 64 * 1024 * 1024) fail(file, "Authoring source size limit exceeded.");
    let value;
    try { value = JSON.parse(bytes.toString("utf8").replace(/^\uFEFF/, "")); }
    catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
      fail(file, "Invalid JSON: " + error.message);
    }
    sources.set(file, value);
    if (library && object(value)) {
      for (const reference of Array.isArray(value.libraries) ? value.libraries : []) {
        await load(projectPath(file, reference), true, depth + 1);
      }
    }
  };
  await load(manifestFile, true);
  const definition = sources.get(manifestFile);
  for (const resource of Array.isArray(definition?.resources) ? definition.resources : []) {
    if (object(resource) && typeof resource.source === "string") await load(projectPath(manifestFile, resource.source));
  }
  return { manifestFile, sources };
}

const diagnostic = (compiled, file, pointer, message) => ({
  ...authoredLocation(compiled, file, pointer),
  message, generated: { file, pointer }
});

function schemaIssues(kind, value) {
  if (kind === "pack") return validateContentSchema(value, {
    type: "object", required: ["pack"], properties: {
      pack: { type: "object", required: ["pack_format", "description"], properties: {
        pack_format: { type: "integer", minimum: 1 },
        description: { anyOf: [{ type: "string" }, { type: "object" }, { type: "array" }] }
      } }
    }
  });
  if (kind === "locale" && value?.schema !== "villagerretaliation:quest_locale/v1") {
    return validateContentSchema(value, {
      type: "object", propertyNames: { minLength: 1 }, additionalProperties: { not: { type: "null" } }
    });
  }
  if (kind === "quest" && value?.schema === "villagerretaliation:quest/v2") {
    return validateContentSchema(value, schemas.get("quest_v2"));
  }
  return validateContentSchema(value, schemas.get(kind));
}

async function removeTemporary(directory, parent) {
  if (!inside(parent, directory) || path.dirname(directory) !== path.resolve(parent)) throw new Error("Invalid temporary directory.");
  await rm(directory, { recursive: true, force: true });
}

function runValidator(list) {
  return new Promise((resolve, reject) => {
    execFile(process.execPath, [path.join(root, "tools/validate-dialogue-data.mjs"), "--json", "--file-list", list],
      { cwd: root, windowsHide: true, timeout: 300000, maxBuffer: 32 * 1024 * 1024 }, (error, stdout, stderr) => {
        if (error && (error.code !== 1 || error.killed)) { reject(error); return; }
        try {
          const result = JSON.parse(stdout);
          if (!Array.isArray(result.errors) || !Array.isArray(result.warnings) || stderr.trim()) {
            throw new Error("Validator returned unexpected diagnostics: " + stderr.trim());
          }
          if (error && !result.errors.length) throw new Error("Validator failed without reporting an error.");
          resolve(result);
        } catch (parseError) { reject(parseError); }
      });
  });
}

function mapValidatorIssue(compiled, directory, issue) {
  if (object(issue)) {
    const file = portable(path.relative(directory, path.resolve(root, issue.resource)));
    return diagnostic(compiled, file, issue.pointer ?? "", issue.message + (issue.suggestion ? " " + issue.suggestion : ""));
  }
  const text = String(issue);
  for (const file of compiled.outputs.keys()) {
    const prefix = portable(path.relative(root, path.join(directory, file))) + ": ";
    if (!text.startsWith(prefix)) continue;
    const message = text.slice(prefix.length);
    const pointer = /^(\/.*?): (.*)$/s.exec(message);
    return diagnostic(compiled, file, pointer ? pointer[1] === "/" ? "" : pointer[1] : "", pointer?.[2] ?? message);
  }
  return { file: "", pointer: "", message: text, via: [] };
}

export async function validateStory(compiled) {
  const errors = [];
  for (const [file, value] of compiled.outputs) {
    const kind = resourceKind(file);
    if (!kind) fail(file, "Unsupported story resource path.");
    for (const issue of schemaIssues(kind, value)) errors.push(diagnostic(compiled, file, issue.pointer, issue.message));
  }
  if (errors.length) return { errors, warnings: [] };
  const directory = await mkdtemp(path.join(os.tmpdir(), "story-validation-"));
  try {
    const files = { quests: [], conversations: [], campaigns: [], scenarios: [], social_progressions: [] };
    const fields = { quest: "quests", conversation: "conversations", campaign: "campaigns", scenario: "scenarios", social_progression: "social_progressions" };
    for (const [file, value] of compiled.outputs) {
      const absolute = path.join(directory, file);
      await mkdir(path.dirname(absolute), { recursive: true });
      await writeFile(absolute, JSON.stringify(value), "utf8");
      const field = fields[resourceKind(file)];
      if (field) files[field].push(file);
    }
    const list = path.join(directory, "validation-files.json");
    await writeFile(list, JSON.stringify(files), "utf8");
    const report = await runValidator(list);
    return {
      errors: report.errors.map(issue => mapValidatorIssue(compiled, directory, issue)),
      warnings: report.warnings.map(issue => mapValidatorIssue(compiled, directory, issue))
    };
  } finally { await removeTemporary(directory, os.tmpdir()); }
}

export function storyPackBytes(compiled) {
  const files = Object.fromEntries([...compiled.outputs].map(([file, value]) => [file, JSON.stringify(value, null, 2) + "\n"]));
  files["story-sources.json"] = JSON.stringify({
    schema: "villagerretaliation:story_sources/v1",
    resources: Object.fromEntries([...compiled.mappings].map(([file, mappings]) => [file, Object.fromEntries(mappings)]))
  }, null, 2) + "\n";
  return createZip(files, { date: new Date(1980, 0, 1) });
}

async function publishStory(output, bytes, overwrite) {
  const absolute = path.resolve(output);
  if (path.extname(absolute).toLowerCase() !== ".zip") fail(output, "Story output must be a .zip datapack.");
  let existing;
  try { existing = await lstat(absolute); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
  if (existing && (!overwrite || !existing.isFile())) fail(output, "Output already exists. Use --overwrite only to replace an existing regular ZIP file.");
  const parent = path.dirname(absolute);
  const temporary = await mkdtemp(path.join(parent, ".story-output-"));
  try {
    const candidate = path.join(temporary, "pack.zip");
    await writeFile(candidate, bytes, { flag: "wx" });
    if (overwrite) await rename(candidate, absolute);
    else await link(candidate, absolute);
  } finally { await removeTemporary(temporary, parent); }
}

export async function compileStoryProject(manifest, options = {}) {
  const project = await loadStoryProject(manifest);
  const compiled = compileStory(project.manifestFile, project.sources);
  const report = await validateStory(compiled);
  if (report.errors.length) {
    const error = new StoryCompileError("", "", "Story validation failed.");
    error.diagnostics = report.errors;
    throw error;
  }
  if (options.output) await publishStory(options.output, storyPackBytes(compiled), options.overwrite === true);
  return { ...compiled, warnings: report.warnings };
}
