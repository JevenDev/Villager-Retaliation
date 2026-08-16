import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const questModel = createRequire(import.meta.url)("./quest-builder/quest-model.js");

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const questRoot = path.join(root, "neoforge/src/main/resources/data/villagerretaliation/quests");
const dialogueTreeRoot = path.join(root, "neoforge/src/main/resources/data/villagerretaliation/dialogue_trees/en_us/quests");
const runMigrationRoot = path.join(root, "run/quest-migrations");
const schemaId = "villagerretaliation:quest/v2";

const cli = parseArgs(process.argv.slice(2));
if (cli.help) {
  printHelp();
  process.exit(0);
}
if (!cli.questFile) {
  console.error("tools/migrate-quest-v1-to-v2.mjs: missing quest JSON file.");
  printHelp();
  process.exit(1);
}

const questFile = path.resolve(cli.questFile);
const quest = await readJson(questFile);
const dialogueTreeFile = cli.dialogueTree === null
  ? null
  : path.resolve(cli.dialogueTree || await inferDialogueTreePath(questFile));
const dialogueTree = dialogueTreeFile ? await readOptionalJson(dialogueTreeFile) : null;
const migration = migrateQuest(questFile, quest, dialogueTreeFile, dialogueTree);

if (cli.check) {
  console.log(stableJson({
    ok: migration.report.unsupported.length === 0,
    report: migration.report,
    comparison: semanticComparison(quest, migration.candidate)
  }));
} else if (cli.outDir) {
  const output = await writeMigrationOutput(cli.outDir, migration);
  console.log(stableJson(output));
} else {
  console.log(stableJson(migration.candidate));
  if (migration.report.unsupported.length > 0 || migration.report.warnings.length > 0) {
    console.error(stableJson({ report: migration.report }));
  }
}

function parseArgs(args) {
  const parsed = {
    help: false,
    questFile: "",
    dialogueTree: undefined,
    outDir: "",
    check: false
  };
  for (let index = 0; index < args.length; index++) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--dialogue-tree") {
      parsed.dialogueTree = args[++index] ?? "";
    } else if (arg === "--no-dialogue-tree") {
      parsed.dialogueTree = null;
    } else if (arg === "--out-dir") {
      parsed.outDir = args[++index] ?? "";
    } else if (arg === "--check") {
      parsed.check = true;
    } else if (arg.startsWith("--")) {
      throw new Error(`Unsupported option ${arg}`);
    } else if (!parsed.questFile) {
      parsed.questFile = arg;
    } else {
      throw new Error(`Unexpected argument ${arg}`);
    }
  }
  return parsed;
}

function printHelp() {
  console.log([
    "Usage: node tools/migrate-quest-v1-to-v2.mjs <quest.json> [options]",
    "",
    "Options:",
    "  --dialogue-tree <file>  Inspect a legacy tree and report the structural dialogue that must be inlined.",
    "  --no-dialogue-tree      Do not infer or load an external dialogue tree.",
    "  --out-dir <dir>         Write candidate/report under run/quest-migrations.",
    "  --check                 Print deterministic semantic comparison/report JSON."
  ].join("\n"));
}

async function readJson(file) {
  try {
    return JSON.parse(stripBom(await readFile(file, "utf8")));
  } catch (error) {
    throw new Error(`${relative(file)}: could not read JSON: ${error.message}`);
  }
}

async function readOptionalJson(file) {
  try {
    return await readJson(file);
  } catch {
    return null;
  }
}

async function inferDialogueTreePath(questFile) {
  const relativeQuest = path.relative(questRoot, questFile);
  if (relativeQuest.startsWith("..") || path.isAbsolute(relativeQuest)) {
    return "";
  }
  const candidate = path.join(dialogueTreeRoot, relativeQuest);
  try {
    await access(candidate);
    return candidate;
  } catch {
    return "";
  }
}

function migrateQuest(questFile, quest, dialogueTreeFile, dialogueTree) {
  const report = {
    source: relative(questFile),
    dialogue_tree: dialogueTree ? relative(dialogueTreeFile) : "",
    warnings: [],
    unsupported: [],
    external_refs: []
  };
  if (!quest || typeof quest !== "object" || Array.isArray(quest)) {
    throw new Error(`${relative(questFile)}: v1 quest root must be an object.`);
  }
  if (quest.schema === schemaId) {
    throw new Error(`${relative(questFile)}: input is already a quest module v2 resource.`);
  }

  const questId = stringValue(quest.id) || questIdForFile(questFile);
  if (!questId) {
    report.unsupported.push(issue("id", "Quest id could not be inferred.", "Add a namespaced id before migration."));
  }

  const candidate = {
    schema: schemaId,
    id: questId,
    localization_prefix: localizationPrefix(questId),
    metadata: buildMetadata(quest),
    provider: buildProvider(quest),
    entry_stage: entryStageId(quest),
    stages: buildStages(quest, questId, dialogueTree, report),
  };
  const availability = buildAvailability(quest, report);
  if (Object.keys(availability).length > 0) {
    candidate.availability = availability;
  }
  const events = buildEvents(quest, report);
  if (events.length > 0) {
    candidate.events = events;
  }
  const rewards = cloneObject(quest.rewards);
  if (rewards && Object.keys(rewards).length > 0) {
    candidate.rewards = rewards;
  }
  const ui = buildUi(quest);
  if (Object.keys(ui).length > 0) {
    candidate.ui = ui;
  }
  stripEmptyObjects(candidate);
  return { candidate, report };
}

function buildMetadata(quest) {
  const metadata = {};
  copyString(quest.display, metadata, "title");
  copyString(quest.display, metadata, "description");
  copyString(quest.display, metadata, "title_key");
  copyString(quest.display, metadata, "description_key");
  copyString(quest, metadata, "questline");
  copyString(quest, metadata, "parent");
  copyValue(quest, metadata, "revision");
  copyValue(quest, metadata, "migration");
  const tags = stringArray(firstDefined(quest.tags, quest.tag));
  if (tags.length > 0) {
    metadata.tags = tags;
  }
  return metadata;
}

function buildProvider(quest) {
  const provider = {
    type: "villagerretaliation:villager"
  };
  const filters = {};
  if (quest.offer && typeof quest.offer === "object" && !Array.isArray(quest.offer)) {
    copyValue(quest.offer, filters, "professions");
    copyValue(quest.offer, filters, "min_villager_level");
    copyValue(quest.offer, filters, "skills");
  }
  if (Object.keys(filters).length > 0) {
    provider.filters = filters;
  }
  return provider;
}

function buildAvailability(quest, report) {
  const availability = {};
  if (quest.offer && typeof quest.offer === "object" && !Array.isArray(quest.offer)) {
    copyValue(quest.offer, availability, "conditions");
  }
  if (quest.rules && typeof quest.rules === "object" && !Array.isArray(quest.rules)) {
    const parityRules = [
      "repeatable", "completion_cooldown", "completion_cooldown_ticks", "completion_cooldown_days",
      "completion_cooldown_seconds", "prerequisite_cooldown", "prerequisite_cooldown_ticks",
      "prerequisite_cooldown_days", "prerequisite_cooldown_seconds", "max_starts", "max_completions",
      "completion_scope", "scope", "abandonment", "abandonment_cooldown", "abandonment_cooldown_ticks",
      "abandonment_cooldown_days", "abandonment_cooldown_seconds", "consume_on_completion",
      "consume_on_abandonment", "locked_to_villager", "cross_villager_compatible", "active", "expiration"
    ];
    parityRules.forEach((key) => copyValue(quest.rules, availability, key));
    if (quest.rules.cooldown_ticks !== undefined && availability.completion_cooldown_ticks === undefined) {
      availability.completion_cooldown_ticks = clone(quest.rules.cooldown_ticks);
    }
    if (quest.rules.branch && typeof quest.rules.branch === "object" && !Array.isArray(quest.rules.branch)) {
      availability.branch = clone(quest.rules.branch);
    }
    for (const key of Object.keys(quest.rules)) {
      if (![...parityRules, "cooldown_ticks", "branch"].includes(key)) {
        report.warnings.push(issue(`rules.${key}`, `Rule "${key}" has no direct quest module v2 availability field.`, "Review the generated v2 resource manually."));
      }
    }
  }
  const prerequisites = stringArray(quest.prerequisites);
  if (prerequisites.length > 0) {
    availability.prerequisites = prerequisites;
  } else if (quest.parent) {
    availability.prerequisites = [quest.parent];
  }
  return availability;
}

function entryStageId(quest) {
  if (quest.stages && typeof quest.stages === "object" && !Array.isArray(quest.stages)) {
    const keys = Object.keys(quest.stages).filter(Boolean);
    if (keys.includes("started")) {
      return "started";
    }
    if (keys.length > 0) {
      return keys[0];
    }
  }
  return "started";
}

function buildStages(quest, questId, dialogueTree, report) {
  const objectives = Array.isArray(quest.objectives) ? quest.objectives : [];
  const objectiveById = new Map(objectives
    .filter((objective) => objective && typeof objective === "object" && !Array.isArray(objective) && stringValue(objective.id))
    .map((objective) => [stringValue(objective.id), objective]));
  const stages = quest.stages && typeof quest.stages === "object" && !Array.isArray(quest.stages)
    ? Object.entries(quest.stages)
    : [["started", { objectives: objectives.map((objective) => stringValue(objective?.id)).filter(Boolean) }]];
  const assignedObjectives = new Set();
  const result = [];
  for (const [stageId, stage] of stages) {
    const rawStage = stage && typeof stage === "object" && !Array.isArray(stage) ? stage : {};
    const refs = stageObjectiveRefs(rawStage, objectiveById);
    refs.forEach((ref) => assignedObjectives.add(ref));
    const migratedStage = {
      id: stageId,
      objectives: refs.map((ref) => migrateObjective(objectiveById.get(ref), questId, report)).filter(Boolean)
    };
    const completeWhen = completeWhenRefs(rawStage);
    if (completeWhen.length > 0) {
      migratedStage.complete_when = completeWhen;
    }
    copyValue(rawStage, migratedStage, "completion");
    copyValue(rawStage, migratedStage, "completion_mode");
    copyValue(rawStage, migratedStage, "completion_count");
    copyValue(rawStage, migratedStage, "bonuses");
    const next = stringValue(rawStage.next) || stringValue(rawStage.next_stage);
    if (next) {
      migratedStage.next = next;
    }
    copyValue(rawStage, migratedStage, "entry_actions", "on_enter");
    copyValue(rawStage, migratedStage, "exit_actions", "on_exit");
    const stageUi = trackerStepUi(quest.tracker, stageId);
    if (Object.keys(stageUi).length > 0) {
      migratedStage.ui = stageUi;
    }
    const responses = branchResponses(rawStage.branches, report);
    if (responses.length > 0) {
      migratedStage.responses = responses;
    }
    const dialogue = buildStageDialogue(quest, dialogueTree, report);
    if (dialogue && Object.keys(dialogue).length > 0) {
      migratedStage.dialogue = dialogue;
    }
    result.push(migratedStage);
  }
  const unassigned = objectives
    .map((objective) => stringValue(objective?.id))
    .filter((id) => id && !assignedObjectives.has(id));
  if (unassigned.length > 0 && result.length > 0) {
    report.warnings.push(issue("objectives", `Objectives not referenced by v1 stages were attached to entry stage: ${unassigned.join(", ")}.`, "Review stage assignment."));
    result[0].objectives.push(...unassigned.map((id) => migrateObjective(objectiveById.get(id), questId, report)).filter(Boolean));
  }
  return result;
}

function migrateObjective(objective, questId, report) {
  if (!objective || typeof objective !== "object" || Array.isArray(objective)) {
    return null;
  }
  const migrated = {};
  for (const [key, value] of Object.entries(objective)) {
    if (key === "memory_event") {
      migrated.memory = clone(value);
    } else if (["x", "y", "z", "pos"].includes(key)) {
      migrated.location = migrated.location || {};
      migrated.location[key] = clone(value);
    } else if (key === "complete_text") {
      migrated.tracker = migrated.tracker || {};
      migrated.tracker.complete_text = clone(value);
    } else if (key === "complete_text_key") {
      migrated.tracker = migrated.tracker || {};
      migrated.tracker.complete_text_key = clone(value);
    } else {
      migrated[key] = clone(value);
    }
  }
  if ((migrated.type === "fact" || migrated.type === "quest_fact" || migrated.type === "choice") && !migrated.quest) {
    migrated.quest = questId;
  }
  return migrated;
}

function stageObjectiveRefs(stage, objectiveById) {
  const refs = stringArray(firstDefined(stage.objectives, stage.objective));
  if (refs.length > 0) {
    return refs.filter((ref) => objectiveById.has(ref));
  }
  return [...objectiveById.keys()];
}

function completeWhenRefs(stage) {
  const value = stage.complete_when;
  if (value === undefined) {
    return [];
  }
  if (typeof value === "string") {
    return value.trim() ? [value.trim()] : [];
  }
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((entry) => {
      if (typeof entry === "string") {
        return entry.trim();
      }
      if (entry && typeof entry === "object" && !Array.isArray(entry)) {
        return stringValue(entry.objective) || stringValue(entry.objective_id) || stringValue(entry.id);
      }
      return "";
    })
    .filter(Boolean);
}

function branchResponses(branches, report) {
  if (branches === undefined) {
    return [];
  }
  if (!Array.isArray(branches)) {
    report.unsupported.push(issue("stages.branches", "Stage branches are not an array.", "Normalize branches before migration."));
    return [];
  }
  return branches
    .filter((branch) => branch && typeof branch === "object" && !Array.isArray(branch))
    .map((branch, index) => {
      const response = {
        id: stringValue(branch.id) || `branch_${index}`,
        label: stringValue(branch.label),
      };
      copyString(branch, response, "label_key");
      copyValue(branch, response, "conditions");
      copyValue(branch, response, "actions");
      const next = stringValue(branch.next) || stringValue(branch.next_stage);
      if (next) {
        response.transition = { stage: next };
      }
      if (branch.blocked_by !== undefined) {
        report.unsupported.push(issue(`branches.${response.id}.blocked_by`, "Branch blockers cannot be converted losslessly to v2 responses.", "Review blocker conditions and author explicit response conditions."));
      }
      stripEmptyObjects(response);
      return response;
    });
}

function buildStageDialogue(quest, dialogueTree, report) {
  if (dialogueTree && typeof dialogueTree === "object" && !Array.isArray(dialogueTree)) {
    report.unsupported.push(issue(
      "dialogue_tree",
      "Legacy external dialogue trees cannot be referenced by a beta.13 quest bundle.",
      "Inline the tree entries and nodes into quest.json structural dialogue before publishing the converted bundle."
    ));
  }
  const source = quest.dialogue && typeof quest.dialogue === "object" && !Array.isArray(quest.dialogue)
    ? quest.dialogue
    : {};
  const dialogue = {};
  for (const [v1Key, slot] of [["start", "offer"], ["reminder", "reminder"], ["turn_in", "turn_in"]]) {
    const lines = dialogueLines(source[v1Key]);
    if (lines.length > 0) {
      dialogue[slot] = { lines };
    }
  }
  for (const key of Object.keys(source)) {
    if (!["start", "reminder", "turn_in"].includes(key)) {
      report.warnings.push(issue(`dialogue.${key}`, `Dialogue status "${key}" has no direct lifecycle slot in the generated candidate.`, "Move it into explicit v2 scenes if needed."));
    }
  }
  return dialogue;
}

function dialogueLines(value) {
  if (typeof value === "string" && value.trim()) {
    return [value.trim()];
  }
  if (Array.isArray(value)) {
    return value.filter((entry) => typeof entry === "string" && entry.trim()).map((entry) => entry.trim());
  }
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return stringArray(firstDefined(value.lines, value.line, value.texts, value.text));
  }
  return [];
}

function buildEvents(quest, report) {
  if (quest.triggers === undefined) {
    return [];
  }
  if (!Array.isArray(quest.triggers)) {
    report.unsupported.push(issue("triggers", "Quest triggers are not an array.", "Normalize triggers before migration."));
    return [];
  }
  return quest.triggers
    .filter((trigger) => trigger && typeof trigger === "object" && !Array.isArray(trigger))
    .map((trigger) => {
      const event = {};
      copyString(trigger, event, "id");
      copyString(trigger, event, "event");
      copyValue(trigger, event, "conditions");
      copyValue(trigger, event, "actions");
      copyValue(trigger, event, "cooldown_ticks");
      copyValue(trigger, event, "radius");
      copyValue(trigger, event, "repeatable");
      copyValue(trigger, event, "stage");
      copyValue(trigger, event, "stages");
      stripEmptyObjects(event);
      return event;
    });
}

function buildUi(quest) {
  const ui = {};
  if (quest.tracker && typeof quest.tracker === "object" && !Array.isArray(quest.tracker)) {
    copyString(quest.tracker, ui, "title");
    copyString(quest.tracker, ui, "title_key");
    const proof = quest.tracker.steps && typeof quest.tracker.steps === "object" && !Array.isArray(quest.tracker.steps)
      ? quest.tracker.steps.proof || quest.tracker.steps.started
      : null;
    if (proof && typeof proof === "object" && !Array.isArray(proof)) {
      copyString(proof, ui, "text", "tracker_text");
      copyString(proof, ui, "text_key", "tracker_text_key");
    }
  }
  return ui;
}

function trackerStepUi(tracker, stageId) {
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)
      || !tracker.steps || typeof tracker.steps !== "object" || Array.isArray(tracker.steps)) {
    return {};
  }
  const step = tracker.steps[stageId];
  if (!step || typeof step !== "object" || Array.isArray(step)) {
    return {};
  }
  const ui = {};
  copyString(step, ui, "text", "tracker_text");
  copyString(step, ui, "text_key", "tracker_text_key");
  copyValue(step, ui, "show_progress");
  copyValue(step, ui, "progress");
  copyValue(step, ui, "metadata");
  return ui;
}

function semanticComparison(quest, candidate) {
  const v1ObjectiveIds = Array.isArray(quest.objectives)
    ? quest.objectives.map((objective) => stringValue(objective?.id)).filter(Boolean)
    : [];
  const v2ObjectiveIds = candidate.stages.flatMap((stage) => stage.objectives.map((objective) => stringValue(objective?.id)).filter(Boolean));
  const v1TriggerIds = Array.isArray(quest.triggers)
    ? quest.triggers.map((trigger) => stringValue(trigger?.id)).filter(Boolean)
    : [];
  const v2TriggerIds = Array.isArray(candidate.events)
    ? candidate.events.map((event) => stringValue(event?.id)).filter(Boolean)
    : [];
  return {
    quest_id_preserved: stringValue(quest.id) === stringValue(candidate.id),
    objective_ids_preserved: sameOrderedValues(v1ObjectiveIds, v2ObjectiveIds),
    trigger_ids_preserved: sameOrderedValues(v1TriggerIds, v2TriggerIds),
    v1_objective_count: v1ObjectiveIds.length,
    v2_objective_count: v2ObjectiveIds.length,
    v1_trigger_count: v1TriggerIds.length,
    v2_trigger_count: v2TriggerIds.length
  };
}
function localizationPrefix(questId) {
  const [namespace = "my_pack", questPath = "quest"] = String(questId || "").split(":");
  const slug = questPath.replaceAll("/", ".").replace(/[^a-z0-9_.-]+/g, "_");
  return `${namespace}.quest.${slug}`;
}


async function writeMigrationOutput(outDir, migration) {
  const resolved = path.resolve(outDir);
  if (!isSubpath(resolved, runMigrationRoot)) {
    throw new Error(`Refusing to write migrations outside ${relative(runMigrationRoot)}.`);
  }
  const bundle = questModel.questBundleFiles(migration.candidate);
  const candidatePath = path.join(resolved, ...bundle.questPath.split("/"));
  const localePath = path.join(resolved, ...bundle.localePath.split("/"));
  const reportPath = path.join(resolved, "migration-report.json");
  const packMetaPath = path.join(resolved, "pack.mcmeta");
  await mkdir(path.dirname(candidatePath), { recursive: true });
  await mkdir(path.dirname(localePath), { recursive: true });
  await writeFile(packMetaPath, stableJson({ pack: { pack_format: 48, description: "Converted Villager Retaliation quest bundle" }, villagerretaliation: { pack_version: "1.0.0-beta.13" } }) + "\n", "utf8");
  await writeFile(candidatePath, stableJson(bundle.quest) + "\n", "utf8");
  await writeFile(localePath, stableJson(bundle.locale) + "\n", "utf8");
  await writeFile(reportPath, stableJson(migration.report) + "\n", "utf8");
  return {
    candidate: relative(candidatePath),
    locale: relative(localePath),
    pack: relative(packMetaPath),
    report: relative(reportPath)
  };
}

function sameOrderedValues(left, right) {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function issue(pathText, message, suggestion) {
  return { path: pathText, message, suggestion };
}

function copyString(source, target, sourceKey, targetKey = sourceKey) {
  if (typeof source?.[sourceKey] === "string" && source[sourceKey].trim()) {
    target[targetKey] = source[sourceKey].trim();
  }
}

function copyValue(source, target, sourceKey, targetKey = sourceKey) {
  if (source && Object.hasOwn(source, sourceKey)) {
    target[targetKey] = clone(source[sourceKey]);
  }
}

function cloneObject(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? clone(value) : {};
}

function clone(value) {
  if (Array.isArray(value)) {
    return value.map(clone);
  }
  if (value && typeof value === "object") {
    const output = {};
    for (const key of Object.keys(value).sort()) {
      output[key] = clone(value[key]);
    }
    return output;
  }
  return value;
}

function stripEmptyObjects(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  for (const key of Object.keys(value)) {
    const child = value[key];
    stripEmptyObjects(child);
    if (child && typeof child === "object" && !Array.isArray(child) && Object.keys(child).length === 0) {
      delete value[key];
    }
    if (value[key] === "" || value[key] === undefined) {
      delete value[key];
    }
  }
  return value;
}

function stringArray(value) {
  if (typeof value === "string") {
    return value.trim() ? [value.trim()] : [];
  }
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((entry) => typeof entry === "string" && entry.trim()).map((entry) => entry.trim());
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function firstDefined(...values) {
  for (const value of values) {
    if (value !== undefined) {
      return value;
    }
  }
  return undefined;
}

function questIdForFile(file) {
  const relativeQuest = path.relative(questRoot, file).replaceAll(path.sep, "/");
  if (relativeQuest.startsWith("..") || path.isAbsolute(relativeQuest) || !relativeQuest.endsWith(".json")) {
    return "";
  }
  return `villagerretaliation:${relativeQuest.slice(0, -".json".length).split("/").at(-1)}`;
}

function safeFileName(value) {
  return String(value).replace(/[^a-zA-Z0-9_.-]+/g, "_");
}

function isSubpath(child, parent) {
  const relativePath = path.relative(parent, child);
  return relativePath === "" || (!relativePath.startsWith("..") && !path.isAbsolute(relativePath));
}

function relative(file) {
  return path.relative(root, file).replaceAll(path.sep, "/");
}

function stableJson(value) {
  return JSON.stringify(sortJson(value), null, 2);
}

function sortJson(value) {
  if (Array.isArray(value)) {
    return value.map(sortJson);
  }
  if (value && typeof value === "object") {
    const output = {};
    for (const key of Object.keys(value).sort()) {
      output[key] = sortJson(value[key]);
    }
    return output;
  }
  return value;
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}
