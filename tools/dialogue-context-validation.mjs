import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { validateConversationSchema } from "./conversation-validation.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const builtInData = path.join(root, "neoforge/src/main/resources/data");
const resourceId = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;

export function validateContextDocuments(documents, schema) {
  const errors = [];
  const rules = new Map();
  const references = [];
  const dependencies = new Map();
  function walk(value, pointer, owner) {
    if (!value || typeof value !== "object") return;
    if (value.type === "context") {
      references.push({ id: value.id, pointer });
      if (owner) dependencies.get(owner).push(value.id);
    }
    if (value.type === "health" && (value.min ?? 0) > (value.max ?? 1))
      errors.push(pointer + ": health requires min <= max");
    for (const [key, child] of Object.entries(value)) walk(child, pointer + "/" + key, owner);
  }
  for (const [file, value] of documents) {
    const contextFile = file.replaceAll("\\", "/").includes("/dialogue_contexts/");
    if (contextFile) {
      if (!value || typeof value !== "object" || Array.isArray(value)
          || Object.keys(value).some(key => !["id", "conditions"].includes(key))
          || typeof value.id !== "string" || !resourceId.test(value.id) || !Array.isArray(value.conditions)) {
        errors.push(file + ": expected explicit id and conditions array");
        continue;
      }
      if (rules.has(value.id)) errors.push(file + ": duplicate dialogue context " + value.id);
      rules.set(value.id, file);
      dependencies.set(value.id, []);
      value.conditions.forEach((condition, index) => {
        for (const issue of validateConversationSchema(condition, { $defs: schema.$defs, $ref: "#/$defs/condition" }))
          errors.push(file + "/conditions/" + index + issue.pointer + ": " + issue.message);
      });
    }
    walk(value, file, contextFile ? value.id : null);
  }
  for (const reference of references)
    if (!rules.has(reference.id)) errors.push(reference.pointer + ": missing dialogue context " + reference.id);
  const complete = new Set();
  function visit(id, active) {
    if (complete.has(id) || !rules.has(id)) return;
    if (active.has(id) || active.size >= 64) {
      errors.push(rules.get(id) + ": cyclic or excessively nested dialogue context " + [...active, id].join(" -> "));
      return;
    }
    active.add(id);
    for (const child of dependencies.get(id) ?? []) visit(child, active);
    active.delete(id);
    complete.add(id);
  }
  for (const id of rules.keys()) visit(id, new Set());
  return errors;
}

async function jsonFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true }).catch(error => {
    if (error.code === "ENOENT") return [];
    throw error;
  });
  return (await Promise.all(entries.map(entry => entry.isDirectory()
    ? jsonFiles(path.join(directory, entry.name))
    : entry.name.endsWith(".json") ? [path.join(directory, entry.name)] : []))).flat();
}

export async function validateContextDirectory(dataDirectory = builtInData, selectedFiles = null) {
  const schema = JSON.parse(await readFile(path.join(root, "tools/datapack-builder/quest-v2.schema.json"), "utf8"));
  const effective = new Map();
  const parseErrors = [];
  async function load(directory, file) {
    const relative = path.relative(directory, file).replaceAll("\\", "/");
    try {
      effective.set(relative, [file, JSON.parse(await readFile(file, "utf8"))]);
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
      parseErrors.push(file + ": invalid JSON: " + error.message);
    }
  }
  for (const directory of dataDirectory === builtInData ? [builtInData] : [builtInData, dataDirectory]) {
    const namespaces = await readdir(directory, { withFileTypes: true });
    for (const namespace of namespaces.filter(entry => entry.isDirectory())) {
      for (const file of await jsonFiles(path.join(directory, namespace.name, "dialogue_contexts")))
        await load(directory, file);
    }
  }
  if (selectedFiles) {
    for (const file of selectedFiles) await load(dataDirectory, file);
  } else {
    for (const file of await jsonFiles(dataDirectory)) {
      const relative = path.relative(dataDirectory, file).replaceAll("\\", "/");
      if (/^[^/]+\/(dialogue|dialogue_trees|forced_dialogue|quests)\//.test(relative))
        await load(dataDirectory, file);
    }
  }
  return [...parseErrors, ...validateContextDocuments([...effective.values()], schema)];
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = await validateContextDirectory(process.argv[2] ? path.resolve(process.argv[2]) : builtInData);
  for (const error of errors) console.error(error);
  if (errors.length) process.exitCode = 1;
  else console.log("Dialogue context validation passed.");
}
