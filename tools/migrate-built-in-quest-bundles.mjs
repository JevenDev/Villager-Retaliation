import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataRoot = path.join(
  repositoryRoot,
  "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const questsRoot = path.join(dataRoot, "quests");
const fixtureRoot = path.join(repositoryRoot, "tools", "quest-bundle-baseline");
const compatibility = JSON.parse(
  await readFile(path.join(fixtureRoot, "compatibility-manifest.json"), "utf8"));
const equivalence = JSON.parse(
  await readFile(path.join(fixtureRoot, "compiled-equivalence.json"), "utf8"));

const expectedCommit = "6e5913766bc93eede34185e0b87eaa428e53b0e6";
if (compatibility.source_commit !== expectedCommit
    || equivalence.source_commit !== expectedCommit) {
  throw new Error("Compatibility fixtures do not describe the approved beta.13 source commit");
}

const textFields = new Set([
  "title", "description", "label", "text", "lines", "tracker_text",
  "complete_text", "custom_name", "trophy_name"
]);
const stableArrays = new Set([
  "stages", "objectives", "events", "triggers", "scenes", "responses",
  "actors", "steps", "actions", "waves", "variants", "members", "phases"
]);
const ownerByDefinition = new Map(
  compatibility.canonical_owners
    .filter(entry => entry.kind !== "message")
    .map(entry => [entry.kind + "\0" + entry.id, entry.owner]));
const localeKeyBySource = new Map();
for (const entry of compatibility.locale_keys) {
  for (const source of entry.sources) {
    if (source.includes(":") && !source.startsWith("message:")) {
      const previous = localeKeyBySource.get(source);
      if (previous && previous !== entry.key) {
        throw new Error("Locale source " + source + " maps to both "
          + previous + " and " + entry.key);
      }
      localeKeyBySource.set(source, entry.key);
    }
  }
}

for (const [questId, source] of Object.entries(equivalence.structures)) {
  const slug = compatibility.frozen_builtin_slugs[questId];
  if (!slug) throw new Error("Missing frozen slug for " + questId);
  const questline = source.metadata?.questline;
  if (!questline) throw new Error("Missing questline for " + questId);
  const prefix = "quest." + questline + "." + slug;
  const quest = localizeDefinition(structuredClone(source), {
    owner: questId,
    prefix,
    source: "quest:" + questId,
    segments: []
  });
  quest.localization_prefix = prefix;
  const ownerDirectory = path.join(questsRoot, questline, slug);
  await writeJson(path.join(ownerDirectory, "quest.json"), quest);
  await writeLocale(ownerDirectory, questId);
}

for (const [sceneId, source] of Object.entries(equivalence.scenes)) {
  const owner = requireOwner("scene", sceneId);
  if (owner === "_shared") {
    throw new Error("Built-in scene unexpectedly has shared ownership: " + sceneId);
  }
  const ownerDirectory = questOwnerDirectory(owner);
  const prefix = questPrefix(owner);
  const scene = localizeDefinition(structuredClone(source), {
    owner,
    prefix,
    source: "scene:" + sceneId,
    segments: ["scene", stablePath(sceneId)]
  });
  await writeJson(path.join(ownerDirectory, "scenes", idPath(sceneId) + ".json"), scene);
}

for (const [encounterId, source] of Object.entries(equivalence.encounters)) {
  const owner = requireOwner("encounter", encounterId);
  const ownerDirectory = owner === "_shared"
    ? path.join(questsRoot, "_shared")
    : questOwnerDirectory(owner);
  const prefix = owner === "_shared" ? "" : questPrefix(owner);
  const encounter = localizeDefinition(structuredClone(source), {
    owner,
    prefix,
    source: "encounter:" + encounterId,
    segments: ["encounter", stablePath(encounterId)]
  });
  await writeJson(
    path.join(ownerDirectory, "encounters", idPath(encounterId) + ".json"),
    encounter);
}

for (const [rewardId, table] of Object.entries(equivalence.rewards)) {
  const owner = requireOwner("reward", rewardId);
  const ownerDirectory = owner === "_shared"
    ? path.join(questsRoot, "_shared")
    : questOwnerDirectory(owner);
  await writeJson(
    path.join(ownerDirectory, "rewards", idPath(rewardId) + ".json"),
    {
      schema: "villagerretaliation:quest_reward/v1",
      id: rewardId,
      table: structuredClone(table)
    });
}

for (const [poolId, pool] of Object.entries(equivalence.pools)) {
  const owner = requireOwner("pool", poolId);
  if (owner !== "_shared") {
    throw new Error("Built-in reusable pool must be shared: " + poolId);
  }
  await writeJson(
    path.join(questsRoot, "_shared", "pools", idPath(poolId) + ".json"),
    structuredClone(pool));
}

await writeLocale(path.join(questsRoot, "_shared"), "_shared");
await removeLooseQuestFiles();
for (const legacyRoot of [
  path.join(dataRoot, "quest_scenes"),
  path.join(dataRoot, "quest_encounters"),
  path.join(dataRoot, "quest_pools"),
  path.join(dataRoot, "loot_table", "quest"),
  path.join(dataRoot, "dialogue", "en_us", "quests", "messages")
]) {
  assertInsideDataRoot(legacyRoot);
  await rm(legacyRoot, { recursive: true, force: true });
}

console.log(
  "Migrated "
  + Object.keys(equivalence.structures).length + " quests, "
  + Object.keys(equivalence.scenes).length + " scenes, "
  + Object.keys(equivalence.encounters).length + " encounters, "
  + Object.keys(equivalence.rewards).length + " rewards, and "
  + Object.keys(equivalence.pools).length + " pools.");

function localizeDefinition(value, context) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  for (const [field, child] of Object.entries(value)) {
    if (textFields.has(field) && hasText(child)) {
      if (field === "lines" && child && typeof child === "object" && !Array.isArray(child)) {
        for (const [status, payload] of Object.entries(child)) {
          child[status] = localizedReference(context, [...context.segments, "lines"], status, payload);
        }
      } else {
        value[field] = localizedReference(context, context.segments, field, child);
      }
      continue;
    }
    if (Array.isArray(child)) {
      for (const element of child) {
        if (!element || typeof element !== "object" || Array.isArray(element)) continue;
        localizeDefinition(element, {
          ...context,
          segments: [...context.segments, singular(field), stableToken(element, field)]
        });
      }
    } else if (child && typeof child === "object") {
      localizeDefinition(child, {
        ...context,
        segments: [...context.segments, field]
      });
    }
  }
  return value;
}

function localizedReference(context, segments, field, payload) {
  const source = [context.source, ...segments, field].join("/");
  const key = localeKeyBySource.get(source);
  if (!key) {
    throw new Error("Frozen compatibility manifest has no locale key for " + source
      + " (" + JSON.stringify(payload).slice(0, 120) + ")");
  }
  const reference = key === context.prefix
    ? "#"
    : key.startsWith(context.prefix + ".")
      ? "#" + key.slice(context.prefix.length + 1)
      : key;
  return { key: reference };
}

function stableToken(object, field) {
  for (const key of [
    "id", "alias", "operation_id", "action", "type", "event",
    "trigger", "key", "name", "template", "entity"
  ]) {
    if (typeof object[key] === "string" && object[key]) return stablePath(object[key]);
  }
  if (stableArrays.has(field) && containsText(object)) {
    throw new Error("Player-facing " + field
      + " entry lacks a stable semantic identifier: "
      + JSON.stringify(object).slice(0, 180));
  }
  return singular(field);
}

function containsText(value) {
  let found = false;
  walk(value, object => {
    if (Object.keys(object).some(key => textFields.has(key))) found = true;
  });
  return found;
}

function walk(value, visitor) {
  if (!value || typeof value !== "object") return;
  if (!Array.isArray(value)) visitor(value);
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    walk(child, visitor);
  }
}

function hasText(value) {
  if (typeof value === "string") return value.length > 0;
  if (Array.isArray(value)) {
    return value.some(entry => typeof entry === "string"
      || entry?.text || entry?.line || entry?.key);
  }
  return value && typeof value === "object"
    && (value.key || Object.values(value).some(hasText));
}

function singular(value) {
  return value.endsWith("ies") ? value.slice(0, -3) + "y"
    : value.endsWith("s") ? value.slice(0, -1) : value;
}

function stablePath(value) {
  return String(value).includes(":") ? String(value).split(":")[1] : String(value);
}

function idPath(id) {
  const separator = id.indexOf(":");
  const value = separator < 0 ? id : id.slice(separator + 1);
  return value.includes("/") ? value.slice(value.lastIndexOf("/") + 1) : value;
}

function requireOwner(kind, id) {
  const owner = ownerByDefinition.get(kind + "\0" + id);
  if (!owner) throw new Error("Missing canonical " + kind + " owner for " + id);
  return owner;
}

function questOwnerDirectory(owner) {
  const source = equivalence.structures[owner];
  const slug = compatibility.frozen_builtin_slugs[owner];
  if (!source || !slug) throw new Error("Unknown quest owner " + owner);
  return path.join(questsRoot, source.metadata.questline, slug);
}

function questPrefix(owner) {
  const source = equivalence.structures[owner];
  const slug = compatibility.frozen_builtin_slugs[owner];
  return "quest." + source.metadata.questline + "." + slug;
}

async function writeLocale(ownerDirectory, owner) {
  const messages = Object.fromEntries(
    compatibility.locale_keys
      .filter(entry => entry.owner === owner)
      .sort((left, right) => left.key.localeCompare(right.key))
      .map(entry => [entry.key, structuredClone(entry.payload)]));
  if (!Object.keys(messages).length) {
    throw new Error("Owner " + owner + " has no frozen English locale payloads");
  }
  await writeJson(
    path.join(ownerDirectory, "locales", "en_us.json"),
    { schema: "villagerretaliation:quest_locale/v1", messages });
}

async function writeJson(file, value) {
  assertInsideDataRoot(file);
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

async function removeLooseQuestFiles() {
  for (const questline of await readdir(questsRoot, { withFileTypes: true })) {
    if (!questline.isDirectory() || questline.name === "_shared") continue;
    const directory = path.join(questsRoot, questline.name);
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      if (!entry.isFile() || !entry.name.endsWith(".json")) continue;
      const file = path.join(directory, entry.name);
      assertInsideDataRoot(file);
      await rm(file);
    }
  }
}

function assertInsideDataRoot(target) {
  const resolved = path.resolve(target);
  const relative = path.relative(dataRoot, resolved);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error("Refusing to write or remove outside the data root: " + resolved);
  }
}
