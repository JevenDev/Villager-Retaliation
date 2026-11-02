import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { readFileSync } from "node:fs";
import { validateConversationSchema } from "./conversation-validation.mjs";
import { presentationTemplates, expandPresentationDocument } from "./dialogue-template-validation.mjs";

const conditionSchema = JSON.parse(readFileSync(new URL("./schemas/quest-v2.schema.json", import.meta.url), "utf8"));
import { fileURLToPath } from "node:url";

const builtInData = fileURLToPath(new URL("../neoforge/src/main/resources/data", import.meta.url));
const reference = /^\{word:([a-z0-9_.-]+:[a-z0-9/._-]+)}$/;
const token = /\{word(?=[:}\s]|$)[^{}]*(?:}|(?=\{|$))/g;
const poolPath = /(?:^|\/)data\/([^/]+)\/(?:(world_campaigns|campaign_addons)\/([^/]+)\/)?dialogue_words\/([^/]+)\/(.+)\.json$/;
const normalizeLocale = value => value.toLowerCase().replaceAll("-", "_");

const poolFields = ["values", "groups", "selection_group", "avoid_recent", "extends", "remove", "role", "optional", "chance", "text_overrides"];
export const wordPoolRoles = ["literal", "sentence", "opening_clause", "address", "adjective_phrase"];
const entryId = /^[a-z0-9_.-]{1,64}$/;
const resourceId = /^[a-z0-9_.-]+:[a-z0-9/._-]+$/;

export function flattenWordEntries(value) {
  const result = [];
  function append(values, defaults = {}) {
    if (!Array.isArray(values)) return;
    for (const raw of values) {
      const entry = raw && typeof raw === "object" && !Array.isArray(raw) ? { ...raw } : { text: raw };
      for (const field of ["priority", "variant"]) if (!(field in entry) && field in defaults) entry[field] = defaults[field];
      if ("conditions" in defaults || "conditions" in entry)
        entry.conditions = Array.isArray("conditions" in defaults ? defaults.conditions : []) && Array.isArray("conditions" in entry ? entry.conditions : [])
          ? [...(defaults.conditions ?? []), ...(entry.conditions ?? [])] : null;
      result.push(entry);
    }
  }
  append(value.values);
  if (Array.isArray(value.groups)) for (const group of value.groups) if (group && typeof group === "object") append(group.values, group);
  return result;
}

export function wordPoolIssues(value) {
  if (!value || typeof value !== "object" || Array.isArray(value) || Object.keys(value).some(key => !poolFields.includes(key)))
    return ["unknown word pool field or invalid object"];
  const errors = [];
  const inherited = "extends" in value;
  if (inherited && (typeof value.extends !== "string" || !resourceId.test(value.extends) || value.extends.length > 256)) errors.push("extends must be a namespaced resource ID");
  if ("values" in value && !Array.isArray(value.values)) errors.push("values must be an array");
  if ("groups" in value) {
    if (!Array.isArray(value.groups) || value.groups.length > 256) errors.push("groups must be an array of at most 256 groups");
    else value.groups.forEach((group, index) => {
      if (!group || typeof group !== "object" || Array.isArray(group) || Object.keys(group).some(key => !["values", "conditions", "priority", "variant"].includes(key)) || !Array.isArray(group.values))
        errors.push(`groups/${index}: expected values and shared conditions, priority, or variant`);
      else {
        if ("priority" in group && (!Number.isInteger(group.priority) || Math.abs(group.priority) > 1000000)) errors.push(`groups/${index}: invalid priority`);
        if ("variant" in group && (typeof group.variant !== "string" || !entryId.test(group.variant) || !value.selection_group && !inherited)) errors.push(`groups/${index}: invalid variant`);
        if ("conditions" in group) validateConditions(group.conditions, `groups/${index}`);
      }
    });
  }
  if ("remove" in value && (!inherited || !Array.isArray(value.remove) || value.remove.length > 256 || value.remove.some(id => typeof id !== "string" || !entryId.test(id)) || new Set(value.remove).size !== value.remove.length))
    errors.push("remove requires a parent and unique entry IDs");
  if ("selection_group" in value && (typeof value.selection_group !== "string" || !resourceId.test(value.selection_group) || value.selection_group.length > 256)) errors.push("selection_group must be a namespaced resource ID");
  if ("avoid_recent" in value && (!Number.isInteger(value.avoid_recent) || value.avoid_recent < 0 || value.avoid_recent > 16)) errors.push("avoid_recent must be an integer from 0 to 16");
  if ("role" in value && !wordPoolRoles.includes(value.role)) errors.push("unknown grammatical role");
  if ("optional" in value && typeof value.optional !== "boolean") errors.push("optional must be a boolean");
  if ("chance" in value && (typeof value.chance !== "number" || !Number.isFinite(value.chance) || value.chance < 0 || value.chance > 1 || !value.optional && !inherited)) errors.push("chance requires an optional pool and a number from 0 to 1");
  if ("text_overrides" in value) {
    if (!inherited || !value.text_overrides || typeof value.text_overrides !== "object" || Array.isArray(value.text_overrides)
        || Object.keys(value.text_overrides).length > 256) errors.push("text_overrides requires a parent and an object of at most 256 entry IDs");
    else for (const [id, text] of Object.entries(value.text_overrides)) {
      if (!entryId.test(id)) errors.push("text_overrides requires lowercase entry IDs");
      if (typeof text !== "string" || !text.trim() && text !== "" || text.length > 512 || /[{}<>\u00a7\u0000-\u001f\u007f-\u009f]/.test(text))
        errors.push("text_overrides/" + id + ": expected literal text");
    }
  }
  const entries = flattenWordEntries(value);
  if (entries.length > 256 || !entries.length && !inherited) errors.push("word pools require 1 to 256 values");
  const ids = new Set();
  function validateConditions(conditions, pointer) {
    if (!Array.isArray(conditions)) errors.push(`${pointer}: conditions must be an array`);
    else conditions.forEach((condition, child) => {
      for (const issue of validateConversationSchema(condition, { $defs: conditionSchema.$defs, $ref: "#/$defs/condition" })) errors.push(`${pointer}/conditions/${child}${issue.pointer}: ${issue.message}`);
    });
  }
  entries.forEach((entry, index) => {
    if (typeof entry.text !== "string" || !entry.text.trim() && !((value.optional || inherited) && entry.text === "") || entry.text.length > 512 || /[{}<>\u00a7\u0000-\u001f\u007f-\u009f]/.test(entry.text)) errors.push(`values/${index}: expected literal text, empty only for optional pools`);
    if (Object.keys(entry).some(key => !["text", "weight", "conditions", "priority", "variant", "id"].includes(key))) errors.push(`values/${index}: unknown word field`);
    if ("id" in entry) {
      if (typeof entry.id !== "string" || !entryId.test(entry.id) || ids.has(entry.id)) errors.push(`values/${index}: entry IDs must be unique lowercase names`);
      ids.add(entry.id);
      if (Object.hasOwn(value.text_overrides ?? {}, entry.id)) errors.push("entry has both a full replacement and a text override: " + entry.id);
    }
    if ("weight" in entry && (!Number.isInteger(entry.weight) || entry.weight < 1 || entry.weight > 1000)) errors.push(`values/${index}: weight must be an integer from 1 to 1000`);
    if ("priority" in entry && (!Number.isInteger(entry.priority) || Math.abs(entry.priority) > 1000000)) errors.push(`values/${index}: invalid priority`);
    if ("variant" in entry && (!value.selection_group && !inherited || typeof entry.variant !== "string" || !entryId.test(entry.variant))) errors.push(`values/${index}: invalid variant`);
    if ("conditions" in entry) validateConditions(entry.conditions, `values/${index}`);
  });
  if (!inherited && !value.optional && !entries.some(entry => !entry.conditions?.length && !entry.variant)) errors.push("word pools require an unconditional, untagged fallback");
  return errors;
}

export function compileWordPool(id, lookup, active = new Set(), cache = new Map()) {
  if (cache.has(id)) return cache.get(id);
  if (active.size >= 16 || active.has(id)) throw new Error(`cyclic or excessively nested word pool inheritance: ${id}`);
  const raw = lookup(id);
  if (!raw) throw new Error(`missing parent word pool: ${id}`);
  const issues = wordPoolIssues(raw);
  if (issues.length) throw new Error(issues.join(", "));
  active.add(id);
  try {
    const parent = raw.extends ? compileWordPool(raw.extends, lookup, active, cache) : {};
    const entries = [...(parent.values ?? [])];
    for (const removed of raw.remove ?? []) {
      const index = entries.findIndex(entry => entry.id === removed);
      if (index < 0) throw new Error(`unknown inherited entry ID: ${removed}`);
      entries.splice(index, 1);
    }
    for (const entry of flattenWordEntries(raw)) {
      const index = entry.id ? entries.findIndex(inherited => inherited.id === entry.id) : -1;
      if (index < 0) entries.push(entry); else entries[index] = entry;
    }
    for (const [entryId, text] of Object.entries(raw.text_overrides ?? {})) {
      const index = entries.findIndex(entry => entry.id === entryId);
      if (index < 0) throw new Error("unknown inherited entry ID in text_overrides: " + entryId);
      entries[index] = { ...entries[index], text };
    }
    const result = { ...parent, ...raw, values: entries };
    delete result.extends; delete result.remove; delete result.groups; delete result.text_overrides;
    const effectiveIssues = wordPoolIssues(result);
    if (effectiveIssues.length) throw new Error(effectiveIssues.join(", "));
    cache.set(id, result);
    return result;
  } finally { active.delete(id); }
}

export function wordReferences(text) {
  return [...text.matchAll(token)].map(match => ({ token: match[0], id: reference.exec(match[0])?.[1] }));
}

export function validateWordDocuments(documents) {
  const templates = presentationTemplates(documents);
  const errors = [...templates.errors];
  const pools = new Set();
  const definitions = new Map();
  const owners = [];
  for (const [file, value] of documents) {
    const match = poolPath.exec(file.replaceAll("\\", "/"));
    if (!match) {
      if (file.replaceAll("\\", "/").includes("/dialogue_words/")) errors.push(`${file}: expected dialogue_words/<locale>/<pool>.json`);
      continue;
    }
    if (!/^[a-z0-9_.-]+:[a-z0-9/._-]+$/.test(`${match[1]}:${match[5]}`)) {
      errors.push(`${file}: invalid word pool resource ID`);
      continue;
    }
    const issues = wordPoolIssues(value);
    errors.push(...issues.map(issue => `${file}: ${issue}`));
    if (!issues.length) {
      const scope = match[2] ? match[2] + "/" + match[3] : "";
      const locale = normalizeLocale(match[4]);
      const id = `${match[1]}:${match[5]}`;
      const key = `${scope}|${locale}|${id}`;
      pools.add(key); definitions.set(key, value); owners.push({ file, scope, locale, id });
    }
  }
  for (const { file, scope, locale, id } of owners) {
    try { compileWordPool(id, key => {
      for (const language of [...new Set([locale, "en_us"])]) for (const owner of [...new Set([scope, ""])]) {
        const raw = definitions.get(`${owner}|${language}|${key}`);
        if (raw) return raw;
      }
    }); } catch (error) { errors.push(`${file}: ${error.message}`); }
  }
  for (const [file, value] of documents) {
    const normalized = file.replaceAll("\\", "/");
    if (poolPath.test(normalized)) continue;
    const scope = /\/(world_campaigns|campaign_addons)\/([^/]+)\//.exec(normalized);
    const scopeKey = scope ? scope[1] + "/" + scope[2] : "";
    const locale = normalizeLocale(/\/(?:dialogue|dialogue_trees|paired_dialogue|paired_greetings)\/([^/]+)\//.exec(normalized)?.[1]
      ?? /\/locales\/([^/]+)\.json$/.exec(normalized)?.[1] ?? "en_us");
    function walk(child, pointer) {
      if (typeof child === "string") {
        for (const entry of wordReferences(child)) {
          if (!entry.id) errors.push(`${file}${pointer}: malformed word pool reference ${entry.token}`);
          else if (![locale, "en_us"].some(language => [scopeKey, ""].some(owner => pools.has(`${owner}|${language}|${entry.id}`))))
            errors.push(`${file}${pointer}: missing word pool ${entry.id} for ${locale}`);
        }
      } else if (Array.isArray(child)) child.forEach((entry, index) => walk(entry, pointer + "/" + index));
      else if (child && typeof child === "object") Object.entries(child).forEach(([key, entry]) => walk(entry, pointer + "/" + key));
    }
    const expanded = /\/dialogue\//.test(normalized)
      ? expandPresentationDocument(value, templates.lookup(file), issue => errors.push(file + ": " + issue)) : value;
    walk(expanded, "");
  }
  return errors;
}

async function jsonFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true }).catch(error => {
    if (error.code === "ENOENT") return [];
    throw error;
  });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? jsonFiles(path.join(directory, entry.name))
    : entry.name.endsWith(".json") ? [path.join(directory, entry.name)] : []))).flat();
}

export async function validateWordDirectory(dataDirectory = builtInData, selectedFiles = null) {
  const effective = new Map();
  const errors = [];
  for (const directory of dataDirectory === builtInData ? [builtInData] : [builtInData, dataDirectory]) {
    for (const file of await jsonFiles(directory)) {
      const text = (await readFile(file, "utf8")).replace(/^\uFEFF/, "");
      const normalized = file.replaceAll("\\", "/");
      const isPool = normalized.includes("/dialogue_words/");
      const isTemplate = normalized.includes("/dialogue_templates/");
      if (!isPool && !isTemplate && (directory !== dataDirectory || selectedFiles && !selectedFiles.includes(file)
          || !text.includes("{word") && !text.includes('"template"'))) continue;
      try {
        effective.set(path.relative(directory, file).replaceAll("\\", "/"), [file, JSON.parse(text)]);
      } catch (error) {
        if (!(error instanceof SyntaxError)) throw error;
        errors.push(`${file}: invalid JSON: ${error.message}`);
      }
    }
  }
  return [...errors, ...validateWordDocuments([...effective.values()])];
}
