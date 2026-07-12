import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dataRoot = path.join(root, "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const questRoot = path.join(dataRoot, "quests");
const lootRoot = path.join(dataRoot, "loot_table", "quest");
const sceneRoot = path.join(dataRoot, "quest_scenes");
const encounterRoot = path.join(dataRoot, "quest_encounters");

const expectedExpansionRepeatables = new Set([
  "apiary_smoke",
  "compost_turn",
  "bell_rope",
  "pond_restock",
  "market_day",
  "road_mending",
  "spider_silk",
  "powder_run",
  "ocean_glass",
  "copper_weather",
  "wither_ash",
  "echo_trade",
  "ender_freight",
  "dragon_sample",
  "beacon_polish"
]);
const expectedBranchingQuestlines = new Set([
  "green_thumb",
  "deep_delvers",
  "redstone_works",
  "nether_routes",
  "end_survey"
]);

const questFiles = await jsonFiles(questRoot);
const lootFiles = await jsonFiles(lootRoot);
const sceneFiles = await jsonFiles(sceneRoot);
const encounterFiles = await jsonFiles(encounterRoot);
const lootIds = new Set(lootFiles.map((file) => `villagerretaliation:quest/${withoutJson(path.relative(lootRoot, file))}`));
const quests = new Map();
const scenes = new Map();
const encounters = new Map();
const errors = [];
let stageCount = 0;
let choiceRouteCount = 0;
let sceneReferenceCount = 0;
const sceneQuestIds = new Set();

for (const file of questFiles) {
  const data = await readJson(file);
  if (!data || typeof data !== "object") {
    continue;
  }
  assert(typeof data.id === "string" && data.id.length > 0, file, "quest id is missing");
  assert(!quests.has(data.id), file, `duplicate quest id ${data.id}`);
  quests.set(data.id, { file, data });
}

for (const file of sceneFiles) {
  const data = await readJson(file);
  if (!data || typeof data !== "object") continue;
  assert(data.schema === "villagerretaliation:scene/v1", file, "scene must use the scene/v1 schema");
  assert(typeof data.id === "string" && data.id.length > 0, file, "scene id is missing");
  assert(!scenes.has(data.id), file, `duplicate scene id ${data.id}`);
  scenes.set(data.id, { file, data });
}

for (const file of encounterFiles) {
  const data = await readJson(file);
  if (!data || typeof data !== "object") continue;
  assert(data.schema === "villagerretaliation:encounter/v1", file, "encounter must use the encounter/v1 schema");
  assert(typeof data.id === "string" && data.id.length > 0, file, "encounter id is missing");
  assert(!encounters.has(data.id), file, `duplicate encounter id ${data.id}`);
  encounters.set(data.id, { file, data });
}

for (const { file, data } of quests.values()) {
  const v2 = data.schema === "villagerretaliation:quest/v2";
  const title = v2 ? data.metadata?.title : data.display?.title;
  const titleWords = typeof title === "string" ? title.trim().split(/\s+/).filter(Boolean).length : 0;
  assert(titleWords >= 1 && titleWords <= 4, file, `title must contain 1-4 words; found ${JSON.stringify(title)}`);

  const rewards = data.rewards ?? {};
  assert(Number(rewards.experience) > 0, file, "quest experience reward must be positive");
  assert(Number(rewards.reputation) > 0, file, "quest reputation reward must be positive");
  assert(typeof rewards.loot_table === "string", file, "quest reward loot table is missing");
  if (typeof rewards.loot_table === "string") {
    assert(lootIds.has(rewards.loot_table), file, `missing reward loot table ${rewards.loot_table}`);
  }

  if (v2) {
    validateV2Quest(file, data);
  }

  const repeatable = v2 ? data.availability?.repeatable === true : data.rules?.repeatable === true;
  if (repeatable) {
    validateRepeatable(file, data, v2);
  }
}

validateParentGraph();
validateExpansionRoster();
validateExpansionBalance();
validateBranchingQuestlines();
validateUniqueExpansionMechanics();
validateCinematicResources();

if (errors.length > 0) {
  for (const error of errors) {
    console.error(`[ERROR] ${error}`);
  }
  process.exitCode = 1;
} else {
  const repeatableCount = [...quests.values()].filter(({ data }) =>
    data.schema === "villagerretaliation:quest/v2"
      ? data.availability?.repeatable === true
      : data.rules?.repeatable === true
  ).length;
  console.log(
    `Built-in quest content passed: ${quests.size} quests, ${stageCount} v2 stages, `
      + `${repeatableCount} repeatables, ${choiceRouteCount} validated choice routes, `
      + `${expectedBranchingQuestlines.size} new branching questlines, `
      + `${sceneQuestIds.size} quests with persistent scenes across ${sceneReferenceCount} launch points.`
  );
}

function validateV2Quest(file, data) {
  const stages = Array.isArray(data.stages) ? data.stages : [];
  assert(stages.length > 0, file, "v2 quest has no stages");
  stageCount += stages.length;
  const stageById = new Map();
  for (const stage of stages) {
    assert(typeof stage.id === "string" && stage.id.length > 0, file, "stage id is missing");
    assert(!stageById.has(stage.id), file, `duplicate stage id ${stage.id}`);
    stageById.set(stage.id, stage);

    const objectives = Array.isArray(stage.objectives) ? stage.objectives : [];
    const objectiveIds = new Set();
    for (const objective of objectives) {
      assert(typeof objective.id === "string" && objective.id.length > 0, file, `stage ${stage.id} has an objective without an id`);
      assert(!objectiveIds.has(objective.id), file, `stage ${stage.id} repeats objective id ${objective.id}`);
      objectiveIds.add(objective.id);
      assert(typeof objective.type === "string" && objective.type.length > 0, file, `objective ${objective.id} has no type`);
    }
    for (const objectiveId of stage.complete_when ?? []) {
      assert(objectiveIds.has(objectiveId), file, `stage ${stage.id} complete_when references missing objective ${objectiveId}`);
    }
  }

  assert(stageById.has(data.entry_stage), file, `entry stage ${JSON.stringify(data.entry_stage)} does not exist`);
  const graph = new Map([...stageById.keys()].map((id) => [id, new Set()]));
  for (const stage of stages) {
    if (typeof stage.next === "string") {
      graph.get(stage.id).add(stage.next);
    }
    for (const target of transitionTargets(stage)) {
      graph.get(stage.id).add(target);
    }
    for (const target of graph.get(stage.id)) {
      assert(stageById.has(target), file, `stage ${stage.id} transitions to missing stage ${target}`);
    }
  }

  const reachable = reachableStages(data.entry_stage, graph);
  for (const stageId of stageById.keys()) {
    assert(reachable.has(stageId), file, `stage ${stageId} is unreachable from ${data.entry_stage}`);
  }

  const terminals = [...stageById.values()].filter((stage) => graph.get(stage.id).size === 0 && hasTurnIn(stage));
  assert(terminals.length > 0, file, "quest has no reachable terminal turn-in stage");

  for (const stage of stages) {
    const choiceObjectives = (stage.objectives ?? []).filter((objective) => objective.type === "choice");
    for (const objective of choiceObjectives) {
      validateChoiceObjective(file, data, stage, objective, graph, terminals.map((terminal) => terminal.id));
    }
  }
}

function validateChoiceObjective(file, data, stage, objective, graph, terminalIds) {
  const values = objective.values ?? objective.choices ?? [];
  assert(Array.isArray(values) && values.length >= 2, file, `choice objective ${objective.id} must define at least two values`);
  const key = objective.key ?? objective.choice ?? objective.id;
  const mappings = choiceMappings(stage, key);
  for (const value of values) {
    const targets = mappings.get(value) ?? new Set();
    assert(targets.size > 0, file, `choice ${objective.id} value ${value} has no set_variable response with a stage transition`);
    for (const target of targets) {
      const reachable = reachableStages(target, graph);
      assert(terminalIds.some((terminal) => reachable.has(terminal)), file,
        `choice ${objective.id} value ${value} cannot reach a terminal turn-in stage`);
      choiceRouteCount++;
    }
  }
}

function validateRepeatable(file, data, v2) {
  const availability = v2 ? data.availability ?? {} : data.rules ?? {};
  const cooldown = availability.completion_cooldown_days ?? availability.cooldown_days ?? 0;
  assert(availability.consume_on_completion === true, file, "repeatable quest must consume hand-in items");
  assert(Number(cooldown) > 0, file, "repeatable quest must have a positive completion cooldown");

  const objectives = v2
    ? (data.stages ?? []).flatMap((stage) => stage.objectives ?? [])
    : data.objectives ?? [];
  for (const objective of objectives) {
    if (objective.type === "item_check") {
      assert(objective.consume !== false, file, `repeatable item objective ${objective.id} disables consumption`);
    }
  }

  if (!v2) {
    for (const slot of ["start", "reminder", "turn_in"]) {
      assert((data.dialogue?.[slot] ?? []).length >= 3, file, `repeatable dialogue ${slot} needs at least 3 variations`);
    }
    return;
  }

  const offerLines = collectDialogueSlotLines(data, "offer");
  const reminderLines = collectDialogueSlotLines(data, "reminder");
  const turnInLines = collectDialogueSlotLines(data, "turn_in");
  assert(offerLines.some((lines) => lines.length >= 3), file, "repeatable offer needs at least 3 dialogue variations");
  assert(reminderLines.some((lines) => lines.length >= 3), file, "repeatable reminder needs at least 3 dialogue variations");
  assert(turnInLines.some((lines) => lines.length >= 3), file, "repeatable turn-in needs at least 3 dialogue variations");
  assert(collectArraysByKey(data, "started").some((lines) => lines.length >= 3), file,
    "repeatable start action needs at least 3 dialogue variations");
  assert(collectArraysByKey(data, "completed").some((lines) => lines.length >= 3), file,
    "repeatable completion action needs at least 3 dialogue variations");
}

function validateParentGraph() {
  for (const [questId, { file, data }] of quests) {
    const parent = data.schema === "villagerretaliation:quest/v2" ? data.metadata?.parent : data.parent;
    if (parent) {
      assert(quests.has(parent), file, `parent quest ${parent} does not exist`);
    }
    const seen = new Set([questId]);
    let current = parent;
    while (current && quests.has(current)) {
      assert(!seen.has(current), file, `parent cycle reaches ${current}`);
      if (seen.has(current)) {
        break;
      }
      seen.add(current);
      const next = quests.get(current).data;
      current = next.schema === "villagerretaliation:quest/v2" ? next.metadata?.parent : next.parent;
    }
  }
}

function validateExpansionRoster() {
  const titleLengths = new Set();
  for (const id of expectedExpansionRepeatables) {
    const quest = quests.get(`villagerretaliation:${id}`);
    assert(Boolean(quest), questRoot, `missing expansion repeatable ${id}`);
    if (quest) {
      assert(quest.data.metadata?.questline === "village_commissions", quest.file,
        `${id} is not in the village_commissions folder/questline`);
      assert(quest.data.availability?.repeatable === true, quest.file, `${id} is not repeatable`);
      titleLengths.add(quest.data.metadata.title.trim().split(/\s+/).length);
    }
  }
  assert(expectedExpansionRepeatables.size === 15, questRoot, "expansion roster must contain exactly 15 repeatables");
  for (const length of [1, 2, 3, 4]) {
    assert(titleLengths.has(length), questRoot,
      `expansion repeatable titles must naturally span 1-4 words; no ${length}-word title was found`);
  }
}

function validateExpansionBalance() {
  const tiers = new Map([["early", []], ["mid", []], ["late", []]]);
  const objectiveTypes = new Set();
  for (const id of expectedExpansionRepeatables) {
    const quest = quests.get(`villagerretaliation:${id}`);
    if (!quest) {
      continue;
    }
    const tierTag = (quest.data.metadata?.tags ?? []).find((tag) => tag.startsWith("tier."));
    const tier = tierTag?.slice("tier.".length);
    assert(tiers.has(tier), quest.file, `${id} has no valid early/mid/late tier tag`);
    if (tiers.has(tier)) {
      tiers.get(tier).push(quest.data);
    }
    for (const objective of quest.data.stages.flatMap((stage) => stage.objectives ?? [])) {
      objectiveTypes.add(objective.type);
    }
  }

  for (const [tier, members] of tiers) {
    assert(members.length === 5, questRoot, `${tier} expansion tier must contain exactly 5 repeatables`);
  }
  const averages = Object.fromEntries([...tiers].map(([tier, members]) => [tier, {
    experience: average(members.map((quest) => quest.rewards.experience)),
    reputation: average(members.map((quest) => quest.rewards.reputation)),
    cooldown: average(members.map((quest) => quest.availability.completion_cooldown_days))
  }]));
  assert(averages.early.experience < averages.mid.experience && averages.mid.experience < averages.late.experience,
    questRoot, "repeatable XP tiers are not strictly increasing from early to late");
  assert(averages.early.reputation < averages.mid.reputation && averages.mid.reputation < averages.late.reputation,
    questRoot, "repeatable reputation tiers are not strictly increasing from early to late");
  assert(averages.early.cooldown < averages.mid.cooldown && averages.mid.cooldown < averages.late.cooldown,
    questRoot, "repeatable cooldown tiers are not strictly increasing from early to late");
  for (const type of ["item_check", "trade", "block_interact", "block_break", "block_place", "mob_kill", "structure_visit"]) {
    assert(objectiveTypes.has(type), questRoot, `expansion repeatables do not exercise ${type}`);
  }
}

function validateBranchingQuestlines() {
  for (const questline of expectedBranchingQuestlines) {
    const members = [...quests.values()].filter(({ data }) => data.metadata?.questline === questline);
    assert(members.length >= 4, questRoot, `branching questline ${questline} must contain at least 4 quests`);
    const choiceMembers = members.filter(({ data }) =>
      (data.stages ?? []).some((stage) => (stage.objectives ?? []).some((objective) => objective.type === "choice"))
    );
    assert(choiceMembers.length >= 1, questRoot, `branching questline ${questline} has no choice quest`);

    for (const choice of choiceMembers) {
      const choiceObjectives = choice.data.stages.flatMap((stage) => stage.objectives ?? [])
        .filter((objective) => objective.type === "choice");
      for (const objective of choiceObjectives) {
        const values = new Set(objective.values ?? objective.choices ?? []);
        const gatedChildren = members.flatMap((member) =>
          questFactConditions(member.data.availability?.conditions)
            .filter((condition) => condition.quest === choice.data.id && condition.key === (objective.key ?? objective.choice ?? objective.id))
            .map((condition) => ({ member, value: condition.value }))
        );
        const childValues = new Set(gatedChildren.map(({ value }) => value));
        for (const value of values) {
          assert(childValues.has(value), choice.file,
            `questline ${questline} choice ${value} has no fact-gated child quest`);
        }
        assert(gatedChildren.length >= 2, choice.file,
          `questline ${questline} must have at least two fact-gated branch quests`);
      }
    }
  }
}

function validateUniqueExpansionMechanics() {
  const signatures = new Map();
  for (const id of expectedExpansionRepeatables) {
    const quest = quests.get(`villagerretaliation:${id}`);
    if (!quest) {
      continue;
    }
    const objectives = quest.data.stages.flatMap((stage) => stage.objectives ?? []);
    const signature = objectives.map(objectiveSignature).sort().join("|");
    const other = signatures.get(signature);
    assert(!other, quest.file, `${id} duplicates the complete objective signature of ${other ?? "another quest"}`);
    signatures.set(signature, id);
  }
}

function validateCinematicResources() {
  for (const { file, data } of quests.values()) {
    walk(data, (node) => {
      if (node.type !== "start_scene") return;
      const sceneId = node.scene ?? node.scene_id ?? node.start_scene;
      sceneReferenceCount++;
      sceneQuestIds.add(data.id);
      assert(typeof sceneId === "string" && sceneId.length > 0, file, "start_scene action has no scene id");
      assert(scenes.has(sceneId), file, `start_scene action references missing scene ${sceneId}`);
      assert(typeof node.operation_id === "string" && node.operation_id.length > 0, file,
        `start_scene action for ${sceneId} has no stable operation_id`);
    });
  }

  for (const { file, data } of scenes.values()) {
    assert(typeof data.metadata?.quest === "string" && quests.has(data.metadata.quest), file,
      `scene metadata references missing quest ${data.metadata?.quest}`);
    const hasControlledEncounter = (data.steps ?? [])
      .some((step) => step.type === "villagerretaliation:start_encounter");
    if (hasControlledEncounter) {
      const quest = quests.get(data.metadata?.quest)?.data;
      assert(quest?.availability?.cross_villager_compatible === true, file,
        "combat scene quest must allow a compatible replacement provider");
      for (const provider of (data.actors ?? []).filter((actor) => actor.binding_source === "quest_provider")) {
        assert(provider.required === false, file, `combat scene provider ${provider.alias} must be optional`);
        assert(provider.replacement_policy === "compatible_replacement", file,
          `combat scene provider ${provider.alias} must allow compatible replacement`);
        assert(provider.missing_actor_policy === "skip", file,
          `combat scene provider ${provider.alias} must let post-fight presentation skip after a casualty`);
        assert(provider.death_policy === "continue_with_snapshot", file,
          `combat scene provider ${provider.alias} must retain its encounter anchor after death`);
      }
    }
    const actors = new Set();
    for (const actor of data.actors ?? []) {
      assert(typeof actor.alias === "string" && actor.alias.length > 0, file, "scene actor alias is missing");
      assert(!actors.has(actor.alias), file, `scene repeats actor alias ${actor.alias}`);
      actors.add(actor.alias);
    }

    const steps = new Map();
    for (const step of data.steps ?? []) {
      assert(typeof step.id === "string" && step.id.length > 0, file, "scene step id is missing");
      assert(!steps.has(step.id), file, `scene repeats stable step id ${step.id}`);
      steps.set(step.id, step);
      for (const actor of step.actors ?? []) {
        assert(actors.has(actor), file, `scene step ${step.id} references missing actor ${actor}`);
      }
      if (step.type === "villagerretaliation:start_encounter") {
        const template = step.data?.template ?? step.data?.encounter_template;
        assert(encounters.has(template), file, `scene step ${step.id} references missing encounter ${template}`);
      }
      if (step.type === "villagerretaliation:action_batch") {
        for (const action of step.data?.actions ?? []) {
          assert(typeof action.id === "string" && action.id.length > 0, file,
            `scene action batch ${step.id} contains an action without a stable id`);
        }
      }
    }
    assert(steps.has(data.entry_step), file, `scene entry step ${JSON.stringify(data.entry_step)} does not exist`);
    for (const step of steps.values()) {
      const targets = [step.next, step.failure_step, ...Object.values(step.transitions ?? {})]
        .filter((target) => typeof target === "string" && target.length > 0);
      for (const target of targets) {
        assert(steps.has(target), file, `scene step ${step.id} transitions to missing step ${target}`);
      }
    }
  }

  for (const { file, data } of encounters.values()) {
    assert(Array.isArray(data.members) && data.members.length > 0, file, "encounter has no members");
    for (const member of data.members ?? []) {
      assert(typeof member.entity === "string" && member.entity.length > 0, file, "encounter member entity is missing");
      assert(Number(member.count ?? 1) > 0, file, `encounter member ${member.entity} has a non-positive count`);
    }
  }
}

function objectiveSignature(objective) {
  return [
    objective.type,
    objective.item,
    objective.entity,
    ...(objective.entities ?? []),
    objective.block,
    ...(objective.blocks ?? []),
    objective.structure,
    objective.count
  ].filter((value) => value !== undefined).join(":");
}

function transitionTargets(stage) {
  const targets = new Set();
  walk(stage, (node) => {
    if (node.transition && typeof node.transition.stage === "string") {
      targets.add(node.transition.stage);
    }
    if (typeof node.stage === "string" && ["quest_transition", "set_stage"].includes(node.type)) {
      targets.add(node.stage);
    }
    if (typeof node.stage === "string" && node.action === "set_stage") {
      targets.add(node.stage);
    }
  });
  return targets;
}

function choiceMappings(stage, key) {
  const mappings = new Map();
  walk(stage, (node) => {
    if (!node.transition || typeof node.transition.stage !== "string" || !Array.isArray(node.actions)) {
      return;
    }
    for (const action of node.actions) {
      if (action.type === "set_variable" && action.key === key && typeof action.value === "string") {
        const targets = mappings.get(action.value) ?? new Set();
        targets.add(node.transition.stage);
        mappings.set(action.value, targets);
      }
    }
  });
  return mappings;
}

function reachableStages(start, graph) {
  const seen = new Set();
  const pending = [start];
  while (pending.length > 0) {
    const current = pending.pop();
    if (!current || seen.has(current) || !graph.has(current)) {
      continue;
    }
    seen.add(current);
    pending.push(...graph.get(current));
  }
  return seen;
}

function hasTurnIn(stage) {
  let found = Boolean(stage.dialogue?.turn_in);
  walk(stage, (node) => {
    if (node.type === "quest" && node.action === "turn_in") {
      found = true;
    }
    if (node.complete === true || node.transition?.complete === true) {
      found = true;
    }
  });
  return found;
}

function collectDialogueSlotLines(data, slot) {
  return (data.stages ?? [])
    .map((stage) => stage.dialogue?.[slot]?.lines)
    .filter(Array.isArray);
}

function collectArraysByKey(value, key) {
  const arrays = [];
  walk(value, (node) => {
    if (Array.isArray(node[key])) {
      arrays.push(node[key]);
    }
  });
  return arrays;
}

function questFactConditions(conditions) {
  const found = [];
  walk(conditions, (node) => {
    if (node.type === "quest_fact") {
      found.push(node);
    }
  });
  return found;
}

function walk(value, visitor) {
  if (!value || typeof value !== "object") {
    return;
  }
  if (!Array.isArray(value)) {
    visitor(value);
  }
  for (const child of Array.isArray(value) ? value : Object.values(value)) {
    walk(child, visitor);
  }
}

function assert(condition, file, message) {
  if (!condition) {
    errors.push(`${relative(file)}: ${message}.`);
  }
}

async function jsonFiles(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await jsonFiles(file));
    } else if (entry.isFile() && entry.name.endsWith(".json")) {
      files.push(file);
    }
  }
  return files.sort();
}

async function readJson(file) {
  try {
    return JSON.parse(await readFile(file, "utf8"));
  } catch (error) {
    errors.push(`${relative(file)}: invalid JSON (${error.message}).`);
    return null;
  }
}

function withoutJson(file) {
  return file.replace(/\.json$/i, "").split(path.sep).join("/");
}

function average(values) {
  return values.length === 0 ? 0 : values.reduce((sum, value) => sum + Number(value), 0) / values.length;
}

function relative(file) {
  return path.relative(root, file).split(path.sep).join("/");
}
