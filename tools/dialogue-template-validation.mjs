import { readFile, readdir } from "node:fs/promises";
import path from "node:path";

const fields = new Set(["extends", "parameters", "arguments", "text", "lines", "variants", "text_key"]);
const presentationFields = ["text", "lines", "variants", "text_key"];
const idPattern = /^[a-z0-9_.-]+:[a-z0-9/._-]+$/;
const namePattern = /^[a-z][a-z0-9_]{0,63}$/;
const parameterPattern = /\{([a-z][a-z0-9_]*)}/g;
const templatePath = /(?:^|\/)data\/([^/]+)\/(?:(world_campaigns|campaign_addons)\/([^/]+)\/)?dialogue_templates\/([^/]+)\/(.+)\.json$/;
const clone = value => structuredClone(value);

function replaceParameters(source, values) {
  const parts = [];
  let cursor = 0, size = 0;
  for (const match of source.matchAll(parameterPattern)) {
    const value = values(match[1]);
    if (value == null) continue;
    size += match.index - cursor + value.length;
    if (size > 16384) throw new Error("expanded template text is too long");
    parts.push(source.slice(cursor, match.index), value);
    cursor = match.index + match[0].length;
  }
  if (size + source.length - cursor > 16384) throw new Error("expanded template text is too long");
  parts.push(source.slice(cursor));
  return parts.join("");
}

function argumentsOf(value) {
  if (!Object.hasOwn(value, "arguments")) return {};
  const args = value.arguments;
  if (!args || typeof args !== "object" || Array.isArray(args) || Object.keys(args).length > 32
      || Object.entries(args).some(([key, text]) => !namePattern.test(key) || typeof text !== "string" || text.length > 16384))
    throw new Error("template arguments require an object of at most 32 named strings");
  return args;
}

export function templateLibrary(lookup) {
  const cache = new Map(), active = new Set();
  function compile(id) {
    if (!idPattern.test(id)) throw new Error("invalid presentation template ID: " + id);
    if (cache.has(id)) return cache.get(id);
    if (active.size >= 16 || active.has(id)) throw new Error("cyclic or excessively nested presentation template: " + id);
    active.add(id);
    try {
      const value = lookup(id);
      if (!value) throw new Error("missing presentation template: " + id);
      if (typeof value !== "object" || Array.isArray(value) || Object.keys(value).some(key => !fields.has(key)))
        throw new Error("unknown presentation template field: " + id);
      const parent = Object.hasOwn(value, "extends") ? compile(value.extends) : { presentation: {}, parameters: [], arguments: {} };
      const local = Object.hasOwn(value, "parameters") ? value.parameters : [];
      if (!Array.isArray(local) || local.length > 32 || local.some(name => typeof name !== "string" || !namePattern.test(name))
          || new Set(local).size !== local.length) throw new Error("template parameters require unique lowercase names");
      const parameters = [...new Set([...parent.parameters, ...local])];
      const args = { ...parent.arguments, ...argumentsOf(value) };
      if (Object.keys(args).some(name => !parameters.includes(name))) throw new Error("unknown default template argument: " + id);
      const own = Object.fromEntries(presentationFields.filter(key => Object.hasOwn(value, key)).map(key => [key, clone(value[key])]));
      const presentation = Object.keys(own).length ? own : clone(parent.presentation);
      if (!Object.keys(presentation).length) throw new Error("presentation template needs text or variants: " + id);
      const result = { presentation, parameters, arguments: args };
      cache.set(id, result);
      return result;
    } finally { active.delete(id); }
  }
  return compile;
}

export function expandPresentationEntry(entry, library) {
  if (!Object.hasOwn(entry, "template")) {
    if (Object.hasOwn(entry, "arguments")) throw new Error("arguments requires a presentation template");
    return entry;
  }
  if (presentationFields.some(field => Object.hasOwn(entry, field))) throw new Error("template instances cannot also supply text or variants");
  const definition = library(entry.template);
  const args = { ...definition.arguments, ...argumentsOf(entry) };
  if (Object.keys(args).some(name => !definition.parameters.includes(name))) throw new Error("unknown template argument");
  for (const name of definition.parameters) if (!Object.hasOwn(args, name)) throw new Error("missing template argument: " + name);
  const active = new Set(), resolved = new Map();
  function resolve(name) {
    if (resolved.has(name)) return resolved.get(name);
    if (active.has(name)) throw new Error("cyclic template argument: " + name);
    active.add(name);
    try {
      const text = replaceParameters(args[name], key => definition.parameters.includes(key) ? resolve(key) : null);
      resolved.set(name, text);
      return text;
    } finally { active.delete(name); }
  }
  definition.parameters.forEach(resolve);
  function substitute(value, text = true) {
    if (typeof value === "string" && text) {
      return replaceParameters(value, key => resolved.get(key));
    }
    if (Array.isArray(value)) return value.map(child => substitute(child, text));
    if (value && typeof value === "object") return Object.fromEntries(Object.entries(value)
      .map(([key, child]) => [key, substitute(child, ["text", "lines", "variants"].includes(key))]));
    return value;
  }
  const result = clone(entry);
  delete result.template; delete result.arguments;
  return { ...result, ...Object.fromEntries(Object.entries(definition.presentation).map(([key, value]) => [key, substitute(value)])) };
}

export function expandPresentationDocument(source, library, diagnostic = message => { throw new Error(message); }) {
  const result = clone(source);
  if (!result || typeof result !== "object" || Array.isArray(result)) return result;
  if (Object.hasOwn(result, "template") || Object.hasOwn(result, "arguments")) {
    try { return expandPresentationEntry(result, library); }
    catch (error) { diagnostic(error.message); return {}; }
  }
  for (const section of ["messages", "openings", "closings", "lines"]) {
    if (!Array.isArray(result[section])) continue;
    result[section] = result[section].map(entry => {
      if (!entry || typeof entry !== "object" || (!Object.hasOwn(entry, "template") && !Object.hasOwn(entry, "arguments"))) return entry;
      try { return expandPresentationEntry(entry, library); }
      catch (error) { diagnostic(error.message); return null; }
    });
  }
  return result;
}

export function presentationTemplates(documents) {
  const definitions = new Map(), owners = [];
  for (const [file, value] of documents) {
    const match = templatePath.exec(file.replaceAll("\\", "/"));
    if (!match) continue;
    const scope = match[2] ? match[2] + "/" + match[3] : "";
    const locale = match[4].toLowerCase().replaceAll("-", "_");
    const id = match[1] + ":" + match[5];
    definitions.set(scope + "|" + locale + "|" + id, value);
    owners.push({ file, id });
  }
  const libraries = new Map();
  function lookup(file) {
    const normalized = file.replaceAll("\\", "/");
    const scope = /\/(world_campaigns|campaign_addons)\/([^/]+)\//.exec(normalized);
    const scopeKey = scope ? scope[1] + "/" + scope[2] : "";
    const locale = /\/(?:dialogue|dialogue_templates)\/([^/]+)\//.exec(normalized)?.[1]?.toLowerCase().replaceAll("-", "_") ?? "en_us";
    const key = scopeKey + "|" + locale;
    if (!libraries.has(key)) libraries.set(key, templateLibrary(id => {
      for (const language of [...new Set([locale, "en_us"])]) for (const owner of [...new Set([scopeKey, ""])]) {
        const definition = definitions.get(owner + "|" + language + "|" + id);
        if (definition) return definition;
      }
    }));
    return libraries.get(key);
  }
  const errors = [];
  for (const owner of owners) try { lookup(owner.file)(owner.id); }
  catch (error) { errors.push(owner.file + ": " + error.message); }
  return { lookup, errors };
}

export async function loadPresentationDocuments(dataDirectory) {
  const result = [];
  async function scan(directory) {
    const entries = await readdir(directory, { withFileTypes: true }).catch(error => {
      if (error.code === "ENOENT") return [];
      throw error;
    });
    for (const entry of entries) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) await scan(file);
      else if (entry.name.endsWith(".json")) result.push([file, JSON.parse(await readFile(file, "utf8"))]);
    }
  }
  for (const entry of await readdir(dataDirectory, { withFileTypes: true })) if (entry.isDirectory())
    await scan(path.join(dataDirectory, entry.name, "dialogue_templates"));
  return result;
}
