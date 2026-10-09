import path from "node:path";
import { readFile } from "node:fs/promises";
import { validateContentSchema } from "./content-schema-validation.mjs";

const schema = JSON.parse(await readFile(new URL("./datapack-builder/story-authoring-v1.schema.json", import.meta.url), "utf8"));
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const escape = key => String(key).replaceAll("~", "~0").replaceAll("/", "~1");
const child = (pointer, key) => pointer + "/" + escape(key);
const markerKeys = ["$use", "$arg", "$splice", "$literal"];
const maximumNodes = 1000000;
const maximumDepth = 64;

export class StoryCompileError extends Error {
  constructor(file, pointer, message, via = []) {
    super(message);
    this.diagnostics = [{ file, pointer, message, via }];
  }
}

export function projectPath(owner, reference) {
  if (typeof reference !== "string" || !reference || reference.includes("\\") || reference.includes(":")
      || reference.startsWith("/") || reference.split("/").some(part => !part || part === "." || part === "..")) {
    throw new StoryCompileError(owner, "", "Source paths must be relative, use forward slashes, and stay inside the authoring project.");
  }
  return path.posix.join(path.posix.dirname(owner), reference);
}

export function resourceKind(file) {
  if (file === "pack.mcmeta") return "pack";
  const match = /^data\/([a-z0-9_.-]+)\/(.+)$/.exec(file);
  if (!match || file.split("/").some(part => part === "." || part === "..")) return null;
  const rest = match[2];
  if (rest.startsWith("quests/") && rest.split("/")[1] !== "_shared" && rest.split("/")[2] === "_shared") return null;
  if (/^campaigns\/(?:[a-z0-9_.-]+\/)+campaign\.json$/.test(rest)) return "campaign";
  if (/^campaigns\/(?:[a-z0-9_.-]+\/)+locales\/[a-z0-9_-]+\.json$/.test(rest)) return "locale";
  if (/^quests\/(?!_shared\/)[a-z0-9_.-]+\/[a-z0-9_.-]+\/quest\.json$/.test(rest)) return "quest";
  const bundle = /^quests\/(?:_shared|[a-z0-9_.-]+\/[a-z0-9_.-]+)\/(locales|conversations|scenes|encounters|rewards|pools)\/[a-z0-9_.-]+\.json$/.exec(rest);
  if (!bundle || bundle[1] === "pools" && !rest.startsWith("quests/_shared/")) return null;
  return ({ locales: "locale", conversations: "conversation", scenes: "scene", encounters: "encounter", rewards: "reward", pools: "pool" })[bundle[1]];
}

export function compileStory(manifestFile, sources, options = {}) {
  const fragments = new Map();
  const loaded = new Set();
  const visiting = [];
  const fail = (origin, message, via = []) => { throw new StoryCompileError(origin.file, origin.pointer, message, via); };
  const document = (file, expected) => {
    if (!sources.has(file)) throw new StoryCompileError(file, "", "Authoring source is missing.");
    const root = sources.get(file);
    const issues = validateContentSchema(root, schema);
    if (issues.length) throw new StoryCompileError(file, issues[0].pointer, issues[0].message);
    if (root.schema !== expected) throw new StoryCompileError(file, "/schema", "Expected " + expected + ".");
    return root;
  };
  const load = (file, main = false) => {
    if (visiting.includes(file)) throw new StoryCompileError(file, "/libraries", "Library cycle: " + [...visiting, file].join(" -> "));
    if (loaded.has(file)) return;
    if (visiting.length >= maximumDepth || loaded.size >= 4096) throw new StoryCompileError(file, "", "Authoring library limit exceeded.");
    const root = document(file, main ? "villagerretaliation:story_authoring/v1" : "villagerretaliation:story_fragments/v1");
    visiting.push(file);
    for (const library of root.libraries ?? []) load(projectPath(file, library));
    for (const [id, definition] of Object.entries(root.fragments ?? {})) {
      const origin = { file, pointer: child("/fragments", id) };
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id)) fail(origin, "Fragment IDs must be namespaced stable IDs.");
      if (fragments.has(id)) fail(origin, "Duplicate fragment " + id + ". Libraries cannot silently override each other.");
      for (const parameter of Object.keys(definition.parameters)) {
        if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(parameter)) fail(origin, "Invalid parameter name: " + parameter);
      }
      fragments.set(id, { ...definition, origin });
    }
    visiting.pop();
    loaded.add(file);
  };
  load(manifestFile, true);
  if (options.maxNodes !== undefined && (!Number.isSafeInteger(options.maxNodes) || options.maxNodes < 1)) {
    throw new StoryCompileError(manifestFile, "", "maxNodes must be a positive safe integer.");
  }
  let remaining = Math.min(maximumNodes, options.maxNodes ?? maximumNodes);
  const budget = (origin, depth) => {
    if (--remaining < 0 || depth > maximumDepth) fail(origin, "Story expansion exceeds its node or depth limit.");
  };
  const directive = (value, origin) => {
    const found = markerKeys.filter(key => Object.hasOwn(value, key));
    if (!found.length) return "";
    if (found.length !== 1) fail(origin, "A template object must contain exactly one directive.");
    const kind = found[0];
    const allowed = kind === "$use" ? ["$use", "args"] : [kind];
    if (Object.keys(value).some(key => !allowed.includes(key))) fail(origin, "Template directives cannot be mixed with ordinary fields.");
    return kind;
  };
  const references = new Map();
  const validate = (value, origin, parameters, arrayItem = false, depth = 0, dependencies = new Set()) => {
    budget(origin, depth);
    if (Array.isArray(value)) {
      value.forEach((entry, index) => validate(entry, { file: origin.file, pointer: child(origin.pointer, index) }, parameters, true, depth + 1, dependencies));
    } else if (object(value)) {
      const kind = directive(value, origin);
      if (kind === "$literal") return dependencies;
      if (kind === "$arg") {
        if (typeof value.$arg !== "string" || !parameters.has(value.$arg)) fail(origin, "Unknown fragment parameter: " + String(value.$arg));
      } else if (kind === "$use") {
        if (typeof value.$use !== "string" || !fragments.has(value.$use)) fail(origin, "Unknown fragment: " + String(value.$use));
        const target = fragments.get(value.$use);
        const args = value.args ?? {};
        if (!object(args)) fail(origin, "Fragment args must be an object.");
        const expected = Object.keys(target.parameters);
        if (expected.some(key => !Object.hasOwn(args, key)) || Object.keys(args).some(key => !Object.hasOwn(target.parameters, key))) {
          fail(origin, "Fragment " + value.$use + " requires exactly these arguments: " + expected.join(", "));
        }
        dependencies.add(value.$use);
        Object.entries(args).forEach(([key, entry]) => validate(entry, { file: origin.file, pointer: child(origin.pointer + "/args", key) },
          parameters, false, depth + 1, dependencies));
      } else if (kind === "$splice") {
        if (!arrayItem) fail(origin, "$splice is only valid as an array element.");
        validate(value.$splice, { file: origin.file, pointer: origin.pointer + "/$splice" }, parameters, false, depth + 1, dependencies);
      } else if (!kind) {
        Object.entries(value).forEach(([key, entry]) => validate(entry, { file: origin.file, pointer: child(origin.pointer, key) },
          parameters, false, depth + 1, dependencies));
      }
    }
    return dependencies;
  };
  for (const [id, fragment] of fragments) {
    references.set(id, validate(fragment.value, { file: fragment.origin.file, pointer: fragment.origin.pointer + "/value" },
      new Set(Object.keys(fragment.parameters))));
  }
  const checked = new Set();
  const checkCycle = (id, stack = []) => {
    if (stack.includes(id)) fail(fragments.get(id).origin, "Fragment cycle: " + [...stack, id].join(" -> "));
    if (checked.has(id)) return;
    if (stack.length >= maximumDepth) fail(fragments.get(id).origin, "Fragment dependency limit exceeded.");
    for (const dependency of references.get(id)) checkCycle(dependency, [...stack, id]);
    checked.add(id);
  };
  fragments.forEach((_, id) => checkCycle(id));
  const annotated = (value, origin, via) => ({ value, origin, via });
  const copy = (entry, via, depth) => {
    budget(entry.origin, depth);
    const chain = [...entry.via, ...via].filter((source, index, all) =>
      all.findIndex(prior => prior.file === source.file && prior.pointer === source.pointer) === index);
    const next = annotated(entry.value, entry.origin, chain);
    if (Array.isArray(entry.value)) next.value = entry.value.map(value => copy(value, via, depth + 1));
    else if (object(entry.value)) next.value = Object.fromEntries(Object.entries(entry.value).map(([key, value]) => [key, copy(value, via, depth + 1)]));
    return next;
  };
  const expand = (value, origin, args = new Map(), via = [], depth = 0, literal = false) => {
    budget(origin, depth);
    const kind = !literal && object(value) ? directive(value, origin) : "";
    if (kind === "$arg") return copy(args.get(value.$arg), via, depth + 1);
    if (kind === "$literal") return expand(value.$literal, { file: origin.file, pointer: origin.pointer + "/$literal" }, args, via, depth + 1, true);
    if (kind === "$use") {
      const fragment = fragments.get(value.$use);
      const parameters = new Map();
      for (const [name, type] of Object.entries(fragment.parameters)) {
        const entry = expand(value.args[name], { file: origin.file, pointer: child(origin.pointer + "/args", name) }, args, via, depth + 1);
        const match = type === "object" ? object(entry.value) : type === "array" ? Array.isArray(entry.value)
          : type === "null" ? entry.value === null : type === "integer" ? Number.isSafeInteger(entry.value)
          : typeof entry.value === type;
        if (!match) fail(entry.origin, "Argument " + name + " requires " + type + ".", via);
        parameters.set(name, entry);
      }
      return expand(fragment.value, { file: fragment.origin.file, pointer: fragment.origin.pointer + "/value" },
        parameters, [...via, origin], depth + 1);
    }
    if (Array.isArray(value)) {
      const entries = [];
      value.forEach((entry, index) => {
        const source = { file: origin.file, pointer: child(origin.pointer, index) };
        if (!literal && object(entry) && Object.hasOwn(entry, "$splice")) {
          const result = expand(entry.$splice, { file: source.file, pointer: source.pointer + "/$splice" }, args, via, depth + 1);
          if (!Array.isArray(result.value)) fail(source, "$splice requires an array.", via);
          for (const value of result.value) entries.push(value);
        } else entries.push(expand(entry, source, args, via, depth + 1, literal));
      });
      return annotated(entries, origin, via);
    }
    if (object(value)) {
      return annotated(Object.fromEntries(Object.entries(value).map(([key, entry]) =>
        [key, expand(entry, { file: origin.file, pointer: child(origin.pointer, key) }, args, via, depth + 1, literal)])), origin, via);
    }
    if (typeof value === "number" && !Number.isFinite(value)) fail(origin, "JSON numbers must be finite.", via);
    return annotated(value, origin, via);
  };
  const outputs = new Map();
  const mappings = new Map();
  const root = sources.get(manifestFile);
  root.resources.forEach((resource, index) => {
    const origin = { file: manifestFile, pointer: "/resources/" + index };
    if (!resourceKind(resource.path)) fail({ ...origin, pointer: origin.pointer + "/path" }, "Output must use the existing campaign or quest bundle layout, or pack.mcmeta.");
    if (outputs.has(resource.path)) fail(origin, "Duplicate output path: " + resource.path);
    let input = resource.value;
    let source = { file: manifestFile, pointer: origin.pointer + "/value" };
    if (Object.hasOwn(resource, "source")) {
      source = { file: projectPath(manifestFile, resource.source), pointer: "" };
      if (!sources.has(source.file)) fail(source, "Authoring source is missing.");
      input = sources.get(source.file);
    }
    validate(input, source, new Set());
    const expanded = expand(input, source);
    const map = new Map();
    const unwrap = (entry, pointer) => {
      map.set(pointer, { ...entry.origin, via: entry.via });
      if (Array.isArray(entry.value)) return entry.value.map((value, index) => unwrap(value, child(pointer, index)));
      if (object(entry.value)) return Object.fromEntries(Object.entries(entry.value).map(([key, value]) => [key, unwrap(value, child(pointer, key))]));
      return entry.value;
    };
    outputs.set(resource.path, unwrap(expanded, ""));
    mappings.set(resource.path, map);
  });
  if (!outputs.has("pack.mcmeta")) throw new StoryCompileError(manifestFile, "/resources", "A compiled story pack requires authored pack.mcmeta.");
  return { outputs, mappings };
}

export function authoredLocation(compiled, file, pointer = "") {
  const map = compiled.mappings.get(file);
  let current = pointer;
  while (map && !map.has(current) && current) current = current.slice(0, current.lastIndexOf("/"));
  return map?.get(current) ?? { file, pointer, via: [] };
}
