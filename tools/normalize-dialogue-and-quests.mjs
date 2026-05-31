import { readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const metadataTagPattern = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/;
const roots = {
  dialogue: path.join(root, "neoforge/src/main/resources/data/villagerretaliation/dialogue/en_us"),
  dialogueTrees: path.join(root, "neoforge/src/main/resources/data/villagerretaliation/dialogue_trees/en_us"),
  forcedDialogue: path.join(root, "neoforge/src/main/resources/data/villagerretaliation/forced_dialogue"),
  quests: path.join(root, "neoforge/src/main/resources/data/villagerretaliation/quests")
};

const dialogueTreeIds = new Map();
const dialogueTreeEntries = new Map();
const forcedDialogueQuestIds = new Map();
const forcedDialogueModuleIds = new Map();

await indexDialogueTrees();
await indexForcedDialogue();
await normalizeDialogueFiles();
await normalizeDialogueTrees();
await normalizeForcedDialogue();
await normalizeQuests();

async function indexDialogueTrees() {
  for (const file of await jsonFiles(roots.dialogueTrees)) {
    const data = await parseJson(file);
    if (!isObject(data)) {
      continue;
    }
    const basename = path.basename(file, ".json");
    const moduleKey = dialogueTreeModuleKey(file);
    const id = typeof data.id === "string" && data.id.trim() ? data.id.trim() : `villagerretaliation:${moduleKey}`;
    const entries = Array.isArray(data.entries)
      ? data.entries
          .filter((entry) => isObject(entry) && typeof entry.id === "string" && entry.id.trim())
          .map((entry) => entry.id.trim())
      : [];
    for (const key of new Set([basename, moduleKey].filter(Boolean))) {
      dialogueTreeIds.set(key, id);
      dialogueTreeEntries.set(key, entries);
    }
  }
}

async function indexForcedDialogue() {
  for (const file of await jsonFiles(roots.forcedDialogue)) {
    const relativePath = path.relative(roots.forcedDialogue, file).replaceAll("\\", "/");
    const moduleKey = forcedDialogueModuleKey(file);
    const questline = forcedDialogueQuestline(relativePath);
    if (!questline && !moduleKey) {
      continue;
    }

    const data = await parseJson(file);
    const entryIds = entriesFor(data)
      .filter((entry) => isObject(entry) && typeof entry.id === "string" && entry.id.trim())
      .map((entry) => entry.id.trim());
    if (entryIds.length > 0) {
      if (questline) {
        forcedDialogueQuestIds.set(questline, entryIds);
      }
      if (moduleKey) {
        forcedDialogueModuleIds.set(moduleKey, entryIds);
      }
    }
  }
}

async function normalizeDialogueFiles() {
  for (const file of await jsonFiles(roots.dialogue)) {
    const data = await parseJson(file);
    if (!isObject(data)) {
      continue;
    }

    const relativePath = path.relative(roots.dialogue, file).replaceAll("\\", "/");
    const relativeSegments = relativePath.split("/");
    const section = dialogueSectionFromPath(relativeSegments) || inferDialogueSection(data) || "lines";
    const stem = cleanStem(path.basename(file, ".json"));
    const scope = scopeInfo(relativeSegments, section);
    const topic = [scope.topicRoot, stem].filter(Boolean).join(".");
    const tags = ["content.dialogue", "dialogue.ambient", `section.${section}`, ...scope.tags];
    data.metadata = mergeMetadata(data.metadata, { topic, tags });
    await writeJson(file, data);
  }
}

async function normalizeDialogueTrees() {
  for (const file of await jsonFiles(roots.dialogueTrees)) {
    const data = await parseJson(file);
    if (!isObject(data)) {
      continue;
    }

    const relativePath = path.relative(roots.dialogueTrees, file).replaceAll("\\", "/");
    const segments = relativePath.split("/");
    const stem = cleanStem(path.basename(file, ".json"));
    const topic = [segments.slice(0, -1).map(cleanToken).filter(Boolean).join("."), stem].filter(Boolean).join(".");
    if (isObject(data.metadata)) {
      const existingMetadata = normalizeMetadata(data.metadata);
      const tags = ["content.dialogue", "dialogue.scene"];
      if (segments[0] === "quests") {
        tags.push("scope.quest_scene", "quest.linked");
      }
      if (existingMetadata.questline) {
        tags.push(`questline.${existingMetadata.questline}`);
      }
      data.metadata = mergeMetadata(data.metadata, {
        topic,
        questline: existingMetadata.questline,
        quest: existingMetadata.quest,
        tags
      });
    }
    await writeJson(file, data);
  }
}

async function normalizeForcedDialogue() {
  for (const file of await jsonFiles(roots.forcedDialogue)) {
    const data = await parseJson(file);
    if (!isObject(data)) {
      continue;
    }

    const relativePath = path.relative(roots.forcedDialogue, file).replaceAll("\\", "/");
    const segments = relativePath.split("/");
    const stem = cleanStem(path.basename(file, ".json"));
    const questline = forcedDialogueQuestline(relativePath);
    const tags = ["content.dialogue", "dialogue.forced"];
    if (questline) {
      tags.push("quest.linked", `questline.${questline}`);
    }
    if (isObject(data.metadata)) {
      data.metadata = mergeMetadata(data.metadata, {
        topic: [segments.slice(0, -1).map(cleanToken).filter(Boolean).join("."), stem].filter(Boolean).join("."),
        questline,
        tags
      });
    }
    await writeJson(file, data);
  }
}

async function normalizeQuests() {
  for (const file of await jsonFiles(roots.quests)) {
    const data = await parseJson(file);
    if (!isObject(data)) {
      continue;
    }

    const basename = path.basename(file, ".json");
    const moduleKey = questModuleKey(file);
    const questline = cleanToken(typeof data.questline === "string" ? data.questline : moduleKey.split("/")[0] || basename);
    const questId = typeof data.id === "string" && data.id.trim() ? data.id.trim() : `villagerretaliation:${moduleKey || basename}`;
    const linkedTreeId = dialogueTreeIds.get(moduleKey) ?? dialogueTreeIds.get(basename) ?? "";
    const linkedTreeEntries = dialogueTreeEntries.get(moduleKey) ?? dialogueTreeEntries.get(basename) ?? [];
    const forcedIds = mergeStringLists(forcedDialogueModuleIds.get(moduleKey) ?? [], forcedDialogueQuestIds.get(questline) ?? []);

    if (isObject(data.metadata)) {
      data.metadata = mergeMetadata(data.metadata, {
        topic: ["quests", questline].filter(Boolean).join("."),
        questline,
        quest: questId,
        tags: ["content.quest", "dialogue.linked", `questline.${questline}`]
      });
    }

    if (isObject(data.links)) {
      const links = { ...data.links };
      if (linkedTreeId) {
        links.dialogue_tree = typeof links.dialogue_tree === "string" && links.dialogue_tree.trim()
          ? links.dialogue_tree
          : linkedTreeId;
      }
      if (linkedTreeEntries.includes("offer") && !stringValue(links.offer)) {
        links.offer = "offer";
      }
      if (linkedTreeEntries.includes("reminder") && !stringValue(links.reminder)) {
        links.reminder = "reminder";
      }
      if (linkedTreeEntries.includes("turn_in") && !stringValue(links.turn_in)) {
        links.turn_in = "turn_in";
      }
      const mergedForcedIds = mergeStringLists(links.forced_dialogue, forcedIds);
      if (mergedForcedIds.length > 0) {
        links.forced_dialogue = mergedForcedIds;
      }
      data.links = sortObjectKeys(links, ["dialogue_tree", "offer", "reminder", "turn_in", "forced_dialogue"]);
    }

    await writeJson(file, data);
  }
}

function entriesFor(data) {
  if (!isObject(data)) {
    return [];
  }
  if (Array.isArray(data.entries)) {
    return data.entries.filter((entry) => isObject(entry));
  }
  return [data];
}

function questModuleKey(file) {
  return moduleKeyFromRoot(file, roots.quests);
}

function dialogueTreeModuleKey(file) {
  const key = moduleKeyFromRoot(file, roots.dialogueTrees);
  return key.startsWith("quests/") ? key.slice("quests/".length) : key;
}

function forcedDialogueModuleKey(file) {
  const key = moduleKeyFromRoot(file, roots.forcedDialogue);
  if (key.startsWith("quests/")) {
    return key.slice("quests/".length);
  }
  if (key.startsWith("quest/")) {
    return key.slice("quest/".length);
  }
  return "";
}

function forcedDialogueQuestline(relativePath) {
  const segments = relativePath.split("/");
  if (segments[0] === "quest") {
    return cleanToken(path.basename(relativePath, ".json"));
  }
  if (segments[0] === "quests") {
    return cleanToken(segments[1] || path.basename(relativePath, ".json"));
  }
  return "";
}

function moduleKeyFromRoot(file, rootDirectory) {
  const relativePath = path.relative(rootDirectory, file).replaceAll("\\", "/");
  return relativePath.endsWith(".json") ? relativePath.slice(0, -".json".length) : relativePath;
}

function scopeInfo(relativeSegments, section) {
  const sectionIndex = relativeSegments.findIndex((segment) => sectionMatches(segment, section));
  const scopeSegments = (sectionIndex >= 0 ? relativeSegments.slice(0, sectionIndex) : relativeSegments.slice(0, -1))
    .map(cleanToken)
    .filter(Boolean);
  if (scopeSegments[0] === "professions") {
    const professionSegments = scopeSegments.slice(1);
    return {
      topicRoot: ["professions", ...professionSegments].join("."),
      tags: professionSegments.length > 0 ? [`scope.profession.${professionSegments.join(".")}`] : ["scope.profession"]
    };
  }
  if (scopeSegments[0] === "groups") {
    const groupSegments = scopeSegments.slice(1);
    return {
      topicRoot: ["groups", ...groupSegments].join("."),
      tags: groupSegments.length > 0 ? [`scope.group.${groupSegments.join(".")}`] : ["scope.group"]
    };
  }
  if (scopeSegments[0] === "global") {
    return { topicRoot: "global", tags: ["scope.global"] };
  }
  return {
    topicRoot: scopeSegments.join("."),
    tags: scopeSegments.length > 0 ? [`scope.${scopeSegments.join(".")}`] : []
  };
}

function dialogueSectionFromPath(segments) {
  for (const segment of segments) {
    if (sectionMatches(segment, "options")) {
      return "options";
    }
    if (sectionMatches(segment, "lines")) {
      return "lines";
    }
    if (sectionMatches(segment, "messages")) {
      return "messages";
    }
    if (sectionMatches(segment, "openings")) {
      return "openings";
    }
    if (sectionMatches(segment, "closings")) {
      return "closings";
    }
    if (sectionMatches(segment, "pacify")) {
      return "pacify";
    }
  }
  return "";
}

function sectionMatches(segment, canonical) {
  return segment === canonical
    || (canonical === "options" && segment === "option")
    || (canonical === "lines" && segment === "line")
    || (canonical === "messages" && segment === "message")
    || (canonical === "openings" && segment === "opening")
    || (canonical === "closings" && segment === "closing")
    || (canonical === "pacify" && segment === "pacification");
}

function inferDialogueSection(data) {
  if (!isObject(data)) {
    return "";
  }
  if (data.type === "dialogue_option" || Object.hasOwn(data, "label")) {
    return "options";
  }
  if (Object.hasOwn(data, "key")) {
    return "messages";
  }
  if (Object.hasOwn(data, "request")) {
    return "lines";
  }
  if (Object.hasOwn(data, "outcomes")) {
    return "pacify";
  }
  return "";
}

function mergeMetadata(existing, incoming) {
  const current = normalizeMetadata(existing);
  const next = normalizeMetadata(incoming);
  return compactMetadata({
    topic: current.topic || next.topic,
    tags: mergeStringLists(current.tags, next.tags),
    questline: current.questline || next.questline,
    quest: current.quest || next.quest,
    stage: current.stage || next.stage,
    notes: current.notes || next.notes
  });
}

function normalizeMetadata(metadata) {
  if (!isObject(metadata)) {
    return { topic: "", tags: [], questline: "", quest: "", stage: "", notes: "" };
  }
  return {
    topic: stringValue(metadata.topic),
    tags: mergeStringLists(metadata.tags),
    questline: stringValue(metadata.questline),
    quest: stringValue(metadata.quest),
    stage: stringValue(metadata.stage),
    notes: stringValue(metadata.notes)
  };
}

function compactMetadata(metadata) {
  const result = {};
  if (metadata.topic) {
    result.topic = metadata.topic;
  }
  if (metadata.tags.length > 0) {
    result.tags = metadata.tags;
  }
  if (metadata.questline) {
    result.questline = metadata.questline;
  }
  if (metadata.quest) {
    result.quest = metadata.quest;
  }
  if (metadata.stage) {
    result.stage = metadata.stage;
  }
  if (metadata.notes) {
    result.notes = metadata.notes;
  }
  return result;
}

function mergeStringLists(...lists) {
  const values = new Set();
  for (const list of lists) {
    if (typeof list === "string") {
      const value = list.trim();
      if (value && metadataTagPattern.test(value)) {
        values.add(value);
      }
      continue;
    }
    if (!Array.isArray(list)) {
      continue;
    }
    for (const entry of list) {
      if (typeof entry !== "string") {
        continue;
      }
      const value = entry.trim();
      if (value && metadataTagPattern.test(value)) {
        values.add(value);
      }
    }
  }
  return [...values].sort((left, right) => left.localeCompare(right, "en"));
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function cleanStem(stem) {
  return cleanToken(stem.replace(/^\d+[._-]*/, ""));
}

function cleanToken(value) {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replaceAll(/[:/\\]+/g, ".")
    .replaceAll(/[^a-z0-9_.-]+/g, "_")
    .replaceAll(/\.{2,}/g, ".")
    .replaceAll(/^[_\-.]+|[_\-.]+$/g, "");
}

function sortObjectKeys(object, preferredOrder = []) {
  const entries = [];
  const used = new Set();
  for (const key of preferredOrder) {
    if (Object.hasOwn(object, key)) {
      entries.push([key, object[key]]);
      used.add(key);
    }
  }
  for (const key of Object.keys(object).sort((left, right) => left.localeCompare(right, "en"))) {
    if (!used.has(key)) {
      entries.push([key, object[key]]);
    }
  }
  return Object.fromEntries(entries);
}

function isObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

async function jsonFiles(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await jsonFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".json")) {
      files.push(fullPath);
    }
  }
  return files.sort((left, right) => left.localeCompare(right, "en"));
}

async function parseJson(file) {
  return JSON.parse(stripBom(await readFile(file, "utf8")));
}

async function writeJson(file, data) {
  const normalized = normalizeNode(data);
  await writeFile(file, `${JSON.stringify(normalized, null, 2)}\n`, "utf8");
}

function normalizeNode(value) {
  if (Array.isArray(value)) {
    return value.map((entry) => normalizeNode(entry));
  }
  if (!isObject(value)) {
    return value;
  }

  const preferredOrder = keyOrder(value);
  const sorted = sortObjectKeys(value, preferredOrder);
  for (const [key, child] of Object.entries(sorted)) {
    sorted[key] = normalizeNode(child);
  }
  return sorted;
}

function keyOrder(object) {
  if (Object.hasOwn(object, "display") && (Object.hasOwn(object, "questline") || Object.hasOwn(object, "offer") || Object.hasOwn(object, "target"))) {
    return [
      "id",
      "display",
      "metadata",
      "links",
      "questline",
      "parent",
      "offer",
      "target",
      "objectives",
      "rules",
      "tracker",
      "triggers",
      "rewards",
      "dialogue"
    ];
  }
  if (Object.hasOwn(object, "nodes") && Object.hasOwn(object, "entries")) {
    return ["id", "display", "metadata", "entries", "nodes"];
  }
  if (Object.hasOwn(object, "entries") && !Object.hasOwn(object, "nodes")) {
    return ["metadata", "entries"];
  }
  if (Object.hasOwn(object, "options") || Object.hasOwn(object, "messages") || Object.hasOwn(object, "openings") || Object.hasOwn(object, "closings") || Object.hasOwn(object, "pacify")) {
    return [
      "metadata",
      "replace",
      "replace_sections",
      "replace_options",
      "replace_lines",
      "replace_messages",
      "replace_openings",
      "replace_closings",
      "replace_pacify",
      "options",
      "lines",
      "messages",
      "openings",
      "closings",
      "pacify"
    ];
  }
  if (Object.hasOwn(object, "label") || Object.hasOwn(object, "request") || Object.hasOwn(object, "key") || Object.hasOwn(object, "outcomes")) {
    return ["metadata", "id", "label", "type", "request", "key", "order", "text", "lines"];
  }
  return [];
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}
