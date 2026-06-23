import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, "..", "..");
const dataDir = path.join(rootDir, "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const assetsDir = path.join(rootDir, "neoforge", "src", "main", "resources", "assets", "villagerretaliation");
const checkOnly = process.argv.includes("--check");

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function walkJson(dir) {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) return walkJson(file);
    return entry.name.endsWith(".json") ? [file] : [];
  });
}

function countDialogueTextLines(node, key = "") {
  if (node == null) return 0;
  if (typeof node === "string") {
    return (key === "line" || key === "text") && node.trim() ? 1 : 0;
  }
  if (Array.isArray(node)) {
    if (key === "lines") {
      return node.reduce((total, entry) => {
        if (typeof entry === "string") return total + (entry.trim() ? 1 : 0);
        return total + countDialogueTextLines(entry, "");
      }, 0);
    }
    return node.reduce((total, entry) => total + countDialogueTextLines(entry, ""), 0);
  }
  if (typeof node === "object") {
    return Object.entries(node).reduce((total, [childKey, childValue]) => (
      total + countDialogueTextLines(childValue, childKey)
    ), 0);
  }
  return 0;
}

function idTail(id = "") {
  return String(id).split(":").pop() || "";
}

function titleCase(value = "") {
  return idTail(value)
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function uniqueStrings(values) {
  return [...new Set(firstArray(values)
    .map((value) => String(value || "").trim())
    .filter(Boolean))];
}

function groupFromTags(tags = []) {
  for (const tag of tags) {
    const value = String(tag || "").trim();
    if (value.startsWith("group.")) return value.slice("group.".length);
    if (value.startsWith("group:")) return value.slice("group:".length);
    if (value.startsWith("group/")) return value.slice("group/".length);
  }
  return "";
}

function itemName(value = "") {
  if (!value) return "None";
  if (Array.isArray(value)) return value.map(itemName).join(" or ");
  return titleCase(String(value).replace(/^#/, ""));
}

function countText(count) {
  if (count == null) return "";
  if (typeof count === "number") return count === 1 ? "1" : String(count);
  if (typeof count === "object") {
    if (count.min != null && count.max != null) return count.min === count.max ? String(count.min) : `${count.min}-${count.max}`;
    if (count.value != null) return String(count.value);
  }
  return String(count);
}

function itemStackText(stack = {}) {
  const items = stack.item || stack.items || stack.tag || stack.tags;
  const count = countText(stack.count || stack.min_count || stack.max_count || 1);
  return `${count} ${itemName(items)}`.trim();
}

function firstArray(value) {
  return Array.isArray(value) ? value : value == null ? [] : [value];
}

function isQuestV2(quest) {
  return quest?.schema === "villagerretaliation:quest/v2";
}

function questMetadata(quest) {
  return isQuestV2(quest) ? quest.metadata || {} : quest.display || {};
}

function questAvailability(quest) {
  return isQuestV2(quest) ? quest.availability || {} : quest.rules || {};
}

function questProviderFilters(quest) {
  return isQuestV2(quest) ? quest.provider?.filters || {} : quest.offer || {};
}

function questParent(quest) {
  return questMetadata(quest).parent || quest.parent || "";
}

function questTags(quest) {
  const metadata = questMetadata(quest);
  return uniqueStrings([
    ...firstArray(metadata.tag),
    ...firstArray(metadata.tags),
    ...firstArray(quest.tag),
    ...firstArray(quest.tags)
  ]);
}

function questStagesArray(quest) {
  if (!quest?.stages) return [];
  return Array.isArray(quest.stages) ? quest.stages : Object.values(quest.stages);
}

function questObjectives(quest) {
  if (!isQuestV2(quest)) return firstArray(quest.objectives);
  return questStagesArray(quest).flatMap((stage) => firstArray(stage.objectives).map((objective) => ({
    ...objective,
    stage: stage.id || ""
  })));
}

function questTargetInfo(quest) {
  if (quest.target && (quest.target.structure || quest.target.proof_item)) {
    return {
      structure: quest.target.structure ? titleCase(quest.target.structure) : "",
      proofItem: quest.target.proof_item ? itemName(quest.target.proof_item) : "",
      searchRadius: quest.target.search_radius || null,
      discoveryRadius: quest.target.discovery_radius || null
    };
  }
  const structureObjective = questObjectives(quest)
    .find((objective) => String(objective.type || "").toLowerCase() === "structure_visit" || objective.structure);
  if (!structureObjective) return null;
  return {
    structure: structureObjective.structure ? titleCase(structureObjective.structure) : "",
    proofItem: "",
    searchRadius: structureObjective.search_radius || null,
    discoveryRadius: structureObjective.discovery_radius || null
  };
}

function groupBy(items, key) {
  return items.reduce((groups, item) => {
    const group = item[key] || "Other";
    if (!groups[group]) groups[group] = [];
    groups[group].push(item);
    return groups;
  }, {});
}

function lootEntries(lootTableId) {
  const tablePath = idTail(lootTableId);
  const slug = tablePath.split("/").pop() || tablePath;
  const file = path.join(dataDir, "loot_table", "quest", `${slug}.json`);
  if (!fs.existsSync(file)) return [];
  const loot = readJson(file);
  return firstArray(loot.pools).flatMap((pool) => firstArray(pool.entries).map((entry) => {
    const countFunction = firstArray(entry.functions).find((fn) => fn.function === "minecraft:set_count");
    const enchantFunction = firstArray(entry.functions).find((fn) => fn.function === "minecraft:set_enchantments");
    const count = countFunction ? countText(countFunction.count) : "1";
    const enchantments = enchantFunction?.enchantments
      ? Object.entries(enchantFunction.enchantments).map(([id, level]) => `${itemName(id)} ${level}`).join(", ")
      : "";
    return {
      item: itemName(entry.name || entry.item || entry.items),
      count,
      weight: entry.weight || 1,
      note: enchantments ? `Enchanted with ${enchantments}` : ""
    };
  }));
}

function compactDialogueLines(...sources) {
  return sources.flatMap((source) => firstArray(source))
    .filter((line) => typeof line === "string" && line.trim());
}

function responseTransitionStage(response = {}) {
  if (response.transition?.stage) return response.transition.stage;
  if (response.stage) return response.stage;
  if (typeof response.next === "string") return response.next;
  if (response.next?.stage) return response.next.stage;
  return "";
}

function responseDestinationLabel(response = {}) {
  const targetStageId = responseTransitionStage(response);
  if (targetStageId) return `Next: ${titleCase(targetStageId)}`;
  if (response.transition?.complete || response.complete) return "Completes quest";
  if (response.transition?.abandon || response.abandon) return "Abandons quest";
  if (response.transition?.fail || response.fail) return "Fails quest";
  const sceneId = response.transition?.scene || response.scene;
  return sceneId ? `Scene: ${titleCase(sceneId)}` : "";
}

function dialogueResponseInfo(response = {}) {
  return {
    id: response.id || "",
    label: response.label || titleCase(response.id || "option"),
    lines: compactDialogueLines(response.lines, response.text),
    targetStageId: responseTransitionStage(response),
    destination: responseDestinationLabel(response)
  };
}

function dialogueSlotLabel(slot = "") {
  return ({
    offer: "Offer",
    reminder: "Reminder",
    turn_in: "Turn-in",
    already_completed: "Already completed",
    unavailable: "Unavailable",
    inactive: "Inactive",
    missing_target: "Missing target",
    missing_proof: "Missing proof",
    locate_failed: "Locate failed"
  })[slot] || titleCase(slot);
}

function questActionLineLabel(action = "", key = "") {
  const prefix = ({
    start: "Start",
    remind: "Reminder",
    turn_in: "Turn-in",
    abandon: "Abandon"
  })[action] || titleCase(action || "Action");
  const state = ({
    already_completed: "Already completed",
    started: "Started",
    unavailable: "Unavailable",
    reminder: "Reminder",
    completed: "Completed",
    missing_target: "Missing target",
    missing_proof: "Missing proof",
    missing_objectives: "Missing objectives",
    abandoned: "Abandoned"
  })[key] || titleCase(key);
  return `${prefix}: ${state}`;
}

function stageNextId(stage = {}) {
  if (typeof stage.next === "string") return stage.next;
  if (stage.next?.stage) return stage.next.stage;
  if (stage.transition?.stage) return stage.transition.stage;
  return "";
}

function questDialogueActionGroups(stage = {}) {
  return firstArray(stage.scenes).flatMap((scene) => firstArray(scene.actions).flatMap((entry) => {
    const action = entry.type === "quest" ? entry.action || "" : entry.action || entry.type || "";
    return Object.entries(entry.lines || {}).map(([key, lines]) => ({
      sceneId: scene.id || "",
      action,
      key,
      label: questActionLineLabel(action, key),
      lines: compactDialogueLines(lines)
    })).filter((group) => group.lines.length);
  }));
}

function questDialogueSceneGroups(stage = {}) {
  return firstArray(stage.scenes).map((scene) => ({
    sceneId: scene.id || "",
    label: `Scene: ${titleCase(scene.id || "dialogue")}`,
    lines: compactDialogueLines(scene.lines, scene.text)
  })).filter((group) => group.lines.length);
}

function questDialogueStageBlock(stage = {}) {
  const dialogue = stage.dialogue || {};
  const slots = Object.entries(dialogue).map(([slot, entry]) => ({
    slot,
    title: dialogueSlotLabel(slot),
    label: entry.label || dialogueSlotLabel(slot),
    lines: compactDialogueLines(entry.lines, entry.text),
    responses: firstArray(entry.responses).map(dialogueResponseInfo)
  })).filter((entry) => entry.lines.length || entry.responses.length);
  return {
    stageId: stage.id || "",
    label: stage.title || stage.ui?.title || titleCase(stage.id || "stage"),
    trackerText: stage.ui?.tracker_text || stage.ui?.text || "",
    slots,
    choices: firstArray(stage.responses).map(dialogueResponseInfo),
    actions: questDialogueActionGroups(stage),
    scenes: questDialogueSceneGroups(stage)
  };
}

function stageHasDialogue(block = {}) {
  return block.slots?.length
    || block.choices?.length
    || block.actions?.length
    || block.scenes?.length;
}

function collectDialoguePathStageIds(stagesById, startStageId) {
  const ids = [];
  const seen = new Set();
  let nextId = startStageId || "";
  while (nextId && !seen.has(nextId) && ids.length < 16) {
    const stage = stagesById.get(nextId);
    if (!stage) break;
    seen.add(nextId);
    ids.push(nextId);
    if (firstArray(stage.responses).length) break;
    nextId = stageNextId(stage);
  }
  return ids;
}

function questDialogueBranches(stages, stageBlocksById) {
  const stagesById = new Map(stages.map((stage) => [stage.id || "", stage]));
  return stages.flatMap((stage) => {
    const responses = firstArray(stage.responses).map((response) => {
      const choice = dialogueResponseInfo(response);
      const stageIds = collectDialoguePathStageIds(stagesById, choice.targetStageId);
      return {
        ...choice,
        stageIds,
        stages: stageIds.map((stageId) => stageBlocksById.get(stageId)).filter(stageHasDialogue)
      };
    }).filter((choice) => choice.id || choice.label || choice.stageIds.length);
    if (!responses.length) return [];
    return [{
      stageId: stage.id || "",
      label: stage.title || stage.ui?.title || titleCase(stage.id || "branch"),
      choices: responses
    }];
  });
}

function actionLines(stageBlocks, action, keys) {
  const wanted = new Set(firstArray(keys));
  return stageBlocks.flatMap((block) => firstArray(block.actions)
    .filter((group) => group.action === action && wanted.has(group.key))
    .flatMap((group) => group.lines));
}

function questDialogue(quest, tree) {
  if (isQuestV2(quest)) return questInlineDialogue(quest);
  if (!tree?.nodes) return {};
  const actionLines = (nodeId, action, key) => firstArray(tree.nodes[nodeId]?.actions)
    .find((entry) => entry.action === action)?.lines?.[key] || [];
  return {
    offer: firstArray(tree.nodes.offer?.lines),
    accept: firstArray(tree.nodes.offer?.responses).find((response) => response.id === "accept")?.label || "Accept",
    decline: firstArray(tree.nodes.offer?.responses).find((response) => response.id === "decline")?.label || "Decline",
    started: actionLines("start_quest", "start", "started"),
    reminder: actionLines("reminder_details", "remind", "reminder"),
    completed: actionLines("complete_quest", "turn_in", "completed"),
    missing: [
      ...actionLines("complete_quest", "turn_in", "missing_target"),
      ...actionLines("complete_quest", "turn_in", "missing_proof"),
      ...actionLines("complete_quest", "turn_in", "missing_objectives")
    ]
  };
}

function questInlineDialogue(quest) {
  const stages = questStagesArray(quest);
  const stageBlocks = stages.map(questDialogueStageBlock);
  const stageBlocksById = new Map(stageBlocks.map((block) => [block.stageId, block]));
  const branches = questDialogueBranches(stages, stageBlocksById);
  const branchPathStageIds = new Set(branches.flatMap((branch) => branch.choices.flatMap((choice) => choice.stageIds)));
  const commonStages = stageBlocks.filter((block) => stageHasDialogue(block) && !branchPathStageIds.has(block.stageId));
  const offer = stageBlocks.flatMap((block) => block.slots).find((slot) => slot.slot === "offer") || {};
  const accept = firstArray(offer.responses).find((response) => response.id === "accept")?.label
    || firstArray(offer.responses)[0]?.label
    || "Accept";
  const decline = firstArray(offer.responses).find((response) => response.id === "decline")?.label || "Decline";
  return {
    offer: firstArray(offer.lines),
    accept,
    decline,
    started: actionLines(stageBlocks, "start", "started"),
    reminder: actionLines(stageBlocks, "remind", "reminder"),
    completed: actionLines(stageBlocks, "turn_in", "completed"),
    missing: actionLines(stageBlocks, "turn_in", ["missing_target", "missing_proof", "missing_objectives"]),
    stages: stageBlocks.filter(stageHasDialogue),
    commonStages,
    branches
  };
}

function questSteps(quest) {
  if (isQuestV2(quest)) {
    const steps = [];
    for (const stage of questStagesArray(quest)) {
      const stageText = stage.ui?.tracker_text || stage.ui?.text || "";
      if (stageText) {
        steps.push({
          id: stage.id || `stage_${steps.length + 1}`,
          label: titleCase(stage.id || "stage"),
          text: stageText,
          progress: stage.ui?.progress ?? null,
          hint: stage.ui?.metadata?.hint || ""
        });
      }
      for (const objective of firstArray(stage.objectives)) {
        const tracker = objective.tracker || {};
        if (!tracker.text) continue;
        steps.push({
          id: objective.id || `objective_${steps.length + 1}`,
          label: titleCase(objective.id || objective.type || "objective"),
          text: tracker.text,
          progress: tracker.progress ?? null,
          hint: tracker.metadata?.hint || ""
        });
      }
    }
    const seen = new Set();
    return steps.filter((step) => {
      const key = `${step.id}:${step.text}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }
  const steps = quest.tracker?.steps || {};
  const objectiveIds = firstArray(quest.objectives).map((objective) => objective.id);
  const preferred = ["travel", "proof", ...objectiveIds, "return"];
  const ordered = [...new Set([...preferred, ...Object.keys(steps).sort((a, b) => (steps[a].progress || 0) - (steps[b].progress || 0))])];
  return ordered.filter((id) => steps[id]).map((id) => ({
    id,
    label: titleCase(id),
    text: steps[id].text || "",
    progress: steps[id].progress ?? null,
    hint: steps[id].metadata?.hint || ""
  }));
}

function questRequirements(quest) {
  const offer = questProviderFilters(quest);
  return {
    minLevel: offer.min_villager_level ? titleCase(offer.min_villager_level) : "Any",
    professions: firstArray(offer.professions).map(titleCase),
    skills: Object.entries(offer.skills || {}).map(([skill, rule]) => ({
      skill: titleCase(skill),
      min: rule?.min ?? null,
      max: rule?.max ?? null
    }))
  };
}

function questConditionRefs(conditions) {
  const refs = [];
  for (const condition of firstArray(conditions)) {
    if (!condition || typeof condition !== "object") continue;
    const type = String(condition.type || "").toLowerCase();
    if ((type === "quest" || type === "quest_fact" || type === "quest_stage" || type === "quest_tag" || type === "quest_variable" || type === "quest_counter")
        && (condition.quest || condition.quest_id)) {
      refs.push(condition.quest || condition.quest_id);
    }
    refs.push(...questConditionRefs(condition.conditions));
    refs.push(...questConditionRefs(condition.condition));
  }
  return [...new Set(refs)];
}

function questObjectiveText(quest) {
  const proof = quest.target?.proof_item ? [`Proof: ${itemName(quest.target.proof_item)}`] : [];
  const objectives = questObjectives(quest).map((objective) => {
    const count = countText(objective.count || 1);
    const type = String(objective.type || "").toLowerCase();
    if (type === "item_check" || objective.item || objective.items || objective.item_tag || objective.item_tags || objective.tag || objective.tags) {
      return `${count} ${itemName(objective.item || objective.items || objective.item_tag || objective.item_tags || objective.tag || objective.tags)}`;
    }
    if (type === "mob_kill") {
      return `Defeat ${count} ${itemName(objective.entity || objective.entities || objective.entity_tag || objective.entity_tags)}`;
    }
    if (type === "structure_visit" || objective.structure) {
      return `Visit ${titleCase(objective.structure || objective.target || "marked structure")}`;
    }
    if (type === "memory_event") {
      return `Record memory: ${titleCase(objective.memory || objective.memory_tag || firstArray(objective.memory_tags)[0] || "village event")}`;
    }
    if (type === "choice") {
      const values = firstArray(objective.values || objective.choices).map(titleCase).join(" or ");
      return `Choose ${titleCase(objective.key || objective.id || "route")}${values ? `: ${values}` : ""}`;
    }
    if (type === "fact") {
      return `Confirm ${titleCase(objective.key || objective.tag || objective.id || "quest fact")}`;
    }
    return titleCase(objective.id || objective.type || "Objective");
  });
  return [...proof, ...objectives];
}

function questRules(quest) {
  const rules = questAvailability(quest);
  const details = [];
  details.push(rules.repeatable ? "Repeatable" : "One-time");
  if (rules.cross_villager_compatible) details.push("Can be completed with another valid villager");
  if (rules.locked_to_villager) details.push("Locked to the quest giver");
  if (rules.consume_on_completion === false) details.push("Turn-in items are not consumed on completion");
  if (rules.consume_on_completion === true) details.push("Turn-in items are consumed on completion");
  if (rules.completion_cooldown_days) details.push(`${rules.completion_cooldown_days} day completion cooldown`);
  if (rules.abandonment_cooldown_days) details.push(`${rules.abandonment_cooldown_days} day abandonment cooldown`);
  if (rules.abandonment_cooldown_seconds) details.push(`${Math.round(rules.abandonment_cooldown_seconds / 60)} minute abandonment cooldown`);
  if (rules.abandonment === "remove_forever") details.push("Abandoning closes it forever");
  if (rules.expiration?.after_days) details.push(`Expires after ${rules.expiration.after_days} days`);
  return details;
}

function questBranchChoices(quest) {
  const legacyChoices = Object.values(quest.stages || {}).flatMap((stage) => firstArray(stage.branches).map((branch) => ({
    id: branch.id || "",
    label: branch.label || titleCase(branch.id || "")
  })));
  const v2Choices = questStagesArray(quest).flatMap((stage) => firstArray(stage.responses).map((response) => ({
    id: response.id || "",
    label: response.label || titleCase(response.id || "")
  })));
  const seen = new Set();
  return [...legacyChoices, ...v2Choices].filter((branch) => {
    const key = `${branch.id}:${branch.label}`;
    if ((!branch.id && !branch.label) || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function buildQuests() {
  const questRoot = path.join(dataDir, "quests");
  const quests = walkJson(questRoot).map((file) => {
    const quest = readJson(file);
    const rel = path.relative(questRoot, file);
    const moduleGroup = rel.split(path.sep)[0] || "";
    const metadata = questMetadata(quest);
    const tags = questTags(quest);
    const questline = metadata.questline || quest.questline || "";
    const group = groupFromTags(tags) || moduleGroup;
    const treePath = path.join(dataDir, "dialogue_trees", "en_us", "quests", rel);
    const tree = fs.existsSync(treePath) ? readJson(treePath) : null;
    const rewards = quest.rewards || {};
    const rules = questAvailability(quest);
    const offer = questProviderFilters(quest);
    const parent = questParent(quest);
    const target = questTargetInfo(quest);
    const prerequisiteIds = [
      parent,
      ...firstArray(rules.prerequisites).map((entry) => typeof entry === "string" ? entry : entry?.quest || entry?.id || ""),
      ...questConditionRefs(offer.conditions),
      ...questConditionRefs(rules.conditions)
    ].filter(Boolean);
    return {
      id: quest.id,
      slug: idTail(quest.id),
      title: metadata.title || quest.display?.title || titleCase(quest.id),
      description: metadata.description || quest.display?.description || "",
      questline,
      questlineLabel: questline ? titleCase(questline) : "",
      group,
      groupLabel: titleCase(group),
      tags,
      relationKey: questline ? `questline:${questline}` : `group:${group}`,
      parent,
      parentSlug: parent ? idTail(parent) : "",
      prerequisites: [...new Set(prerequisiteIds)].map((id) => ({
        id,
        slug: idTail(id)
      })),
      branchGroup: rules.exclusive_group || rules.branch?.exclusive_group || "",
      branchChoices: questBranchChoices(quest),
      requirements: questRequirements(quest),
      target,
      objectives: questObjectiveText(quest),
      steps: questSteps(quest),
      rewards: {
        experience: rewards.experience || 0,
        reputation: rewards.reputation || 0,
        gossipReputation: rewards.gossip_reputation || 0,
        lootTable: rewards.loot_table || "",
        loot: lootEntries(rewards.loot_table)
      },
      rules: questRules(quest),
      dialogue: questDialogue(quest, tree)
    };
  });

  const byId = new Map(quests.map((quest) => [quest.id, quest]));
  const childMap = new Map();
  for (const quest of quests) {
    if (!quest.parent || !byId.has(quest.parent)) continue;
    if (!childMap.has(quest.parent)) childMap.set(quest.parent, []);
    childMap.get(quest.parent).push(quest);
  }

  const orderById = new Map();
  const relationGroups = groupBy(quests, "relationKey");
  for (const groupQuests of Object.values(relationGroups)) {
    let order = 0;
    const seen = new Set();
    const visit = (quest) => {
      if (!quest || seen.has(quest.id)) return;
      seen.add(quest.id);
      orderById.set(quest.id, order++);
      const children = (childMap.get(quest.id) || [])
        .filter((child) => child.relationKey === quest.relationKey)
        .sort((a, b) => a.title.localeCompare(b.title));
      children.forEach(visit);
    };
    const roots = groupQuests
      .filter((quest) => !quest.parent || !byId.has(quest.parent) || byId.get(quest.parent).relationKey !== quest.relationKey)
      .sort((a, b) => a.title.localeCompare(b.title));
    roots.forEach(visit);
    groupQuests
      .filter((quest) => !seen.has(quest.id))
      .sort((a, b) => a.title.localeCompare(b.title))
      .forEach(visit);
  }

  return quests
    .map((quest) => ({
      ...quest,
      questlineOrder: orderById.get(quest.id) ?? 0
    }))
    .sort((a, b) => (
      a.groupLabel.localeCompare(b.groupLabel)
      || a.questlineLabel.localeCompare(b.questlineLabel)
      || a.questlineOrder - b.questlineOrder
      || a.title.localeCompare(b.title)
    ));
}

function buildGifts() {
  const gifts = walkJson(path.join(dataDir, "gifts")).map(readJson);
  const preferences = gifts.flatMap((file) => firstArray(file.preferences));
  const rewards = gifts.flatMap((file) => firstArray(file.rewards));
  const professionGroups = new Map();
  for (const entry of preferences) {
    const professions = firstArray(entry.professions);
    if (!professions.length) continue;
    for (const profession of professions) {
      if (!professionGroups.has(profession)) professionGroups.set(profession, []);
      professionGroups.get(profession).push(entry);
    }
  }

  const globalEntries = preferences.filter((entry) => !firstArray(entry.professions).length);
  const globalPreferredItems = [...new Set(
    globalEntries
      .filter((entry) => entry.reaction === "liked" || entry.reaction === "loved")
      .flatMap((entry) => firstArray(entry.items || entry.item || entry.tags || entry.tag).map(itemName))
  )].sort((a, b) => a.localeCompare(b));
  const globalDislikedItems = [...new Set(
    globalEntries
      .filter((entry) => entry.reaction === "disliked" || entry.reaction === "hated")
      .flatMap((entry) => firstArray(entry.items || entry.item || entry.tags || entry.tag).map(itemName))
  )].sort((a, b) => a.localeCompare(b));
  const globalNeutralItems = [...new Set(
    globalEntries
      .filter((entry) => entry.reaction === "neutral")
      .flatMap((entry) => firstArray(entry.items || entry.item || entry.tags || entry.tag).map(itemName))
  )].sort((a, b) => a.localeCompare(b));

  return {
    totals: {
      preferences: preferences.length,
      rewards: rewards.length
    },
    globalPreferredItems,
    globalDislikedItems,
    globalNeutralItems,
    reactions: ["loved", "liked", "neutral", "disliked", "hated"].map((reaction) => {
      const reactionEntries = preferences.filter((entry) => entry.reaction === reaction);
      const allItems = [...new Set(
        reactionEntries.flatMap((entry) => firstArray(entry.items || entry.item || entry.tags || entry.tag).map(itemName))
      )].sort((a, b) => a.localeCompare(b));
      return {
        reaction: titleCase(reaction),
        count: reactionEntries.length,
        allItems,
        examples: reactionEntries.slice(0, 8).map((entry) => ({
          id: entry.id,
          professions: firstArray(entry.professions).map(titleCase),
          items: firstArray(entry.items || entry.item || entry.tags || entry.tag).slice(0, 14).map(itemName)
        }))
      };
    }),
    professionPreferences: [...professionGroups.entries()].map(([profession, entries]) => ({
      profession: titleCase(profession),
      entries: entries.map((entry) => ({
        reaction: titleCase(entry.reaction),
        items: [...new Set(firstArray(entry.items || entry.item || entry.tags || entry.tag).map(itemName))].sort((a, b) => a.localeCompare(b))
      }))
    })),
    rewards: rewards.map((entry) => ({
      professions: firstArray(entry.professions).map(titleCase),
      levels: firstArray(entry.reputation_levels).map(titleCase),
      item: itemName(entry.item),
      count: (() => {
        const min = entry.min_count || 1;
        const max = entry.max_count || min;
        return min === max ? `${min}` : `${min}-${max}`;
      })()
    }))
  };
}

function buildPacification() {
  return walkJson(path.join(dataDir, "pacification")).flatMap((file) => firstArray(readJson(file).payments).map((payment) => ({
    item: itemName(payment.item),
    min: payment.min_count || 1,
    max: payment.max_count || payment.min_count || 1,
    name: payment.plural_name || payment.name || itemName(payment.item)
  })));
}

function buildSkillTrades() {
  const entries = walkJson(path.join(dataDir, "skill_trades")).flatMap((file) => firstArray(readJson(file).entries));
  const professions = new Map();
  for (const entry of entries) {
    const profession = titleCase(firstArray(entry.professions)[0] || "global");
    if (!professions.has(profession)) professions.set(profession, []);
    professions.get(profession).push({
      id: entry.id,
      rank: [entry.min_rank, entry.max_rank].filter(Boolean).map(titleCase).join(" to ") || "Any",
      level: entry.villager_level || null,
      cost: itemStackText(entry.cost),
      result: itemStackText(entry.result),
      chance: entry.chance == null ? null : Math.round(entry.chance * 100),
      requestable: Boolean(entry.request?.targetable),
      minReputation: entry.request?.min_reputation ? titleCase(entry.request.min_reputation) : ""
    });
  }
  return [...professions.entries()].map(([profession, trades]) => ({
    profession,
    count: trades.length,
    trades: trades.sort((a, b) => (a.level || 0) - (b.level || 0) || a.result.localeCompare(b.result))
  })).sort((a, b) => a.profession.localeCompare(b.profession));
}

function buildAdvancements() {
  const langFile = path.join(assetsDir, "lang", "en_us.json");
  const lang = fs.existsSync(langFile) ? readJson(langFile) : {};
  return walkJson(path.join(dataDir, "advancement", "reputation")).map((file) => {
    const advancement = readJson(file);
    const slug = path.basename(file, ".json");
    const display = advancement.display || {};
    const titleKey = display.title?.translate;
    const descriptionKey = display.description?.translate;
    return {
      id: slug,
      title: lang[titleKey] || titleCase(slug),
      description: lang[descriptionKey] || "",
      frame: titleCase(display.frame || "task"),
      hidden: Boolean(display.hidden),
      icon: itemName(display.icon?.id || "minecraft:bell"),
      parent: advancement.parent ? idTail(advancement.parent) : ""
    };
  }).sort((a, b) => a.title.localeCompare(b.title));
}

function buildStats() {
  const dialogueLines = walkJson(path.join(dataDir, "dialogue"))
    .reduce((total, file) => total + countDialogueTextLines(readJson(file)), 0);
  const forcedDialogueLines = walkJson(path.join(dataDir, "forced_dialogue"))
    .reduce((total, file) => total + countDialogueTextLines(readJson(file)), 0);
  const dialogueTreeLines = walkJson(path.join(dataDir, "dialogue_trees"))
    .reduce((total, file) => total + countDialogueTextLines(readJson(file)), 0);
  const questModuleDialogueLines = walkJson(path.join(dataDir, "quests"))
    .reduce((total, file) => {
      const quest = readJson(file);
      return total + countDialogueTextLines(quest.dialogue) + questStagesArray(quest)
        .reduce((stageTotal, stage) => (
          stageTotal
          + countDialogueTextLines(stage.dialogue)
          + countDialogueTextLines(stage.scenes)
          + countDialogueTextLines(stage.responses)
        ), 0);
    }, 0);
  return {
    dialogueLinesEstimate: dialogueLines + forcedDialogueLines + dialogueTreeLines + questModuleDialogueLines,
    dialogueLineBreakdown: {
      dialogue: dialogueLines,
      forcedDialogue: forcedDialogueLines,
      dialogueTrees: dialogueTreeLines,
      questModules: questModuleDialogueLines
    }
  };
}

const data = {
  source: "neoforge/src/main/resources/data/villagerretaliation",
  reputation: [
    { level: "Royalty", threshold: "1000+", effect: "The highest trust tier. Villagers are extremely forgiving and dialogue stays warm longest." },
    { level: "Revered", threshold: "400+", effect: "Unlocks stronger trust behavior, trusted keepsakes, and high-reputation reward moments." },
    { level: "Respected", threshold: "250+", effect: "Needed by default for Special Orders and several high-skill trade requests." },
    { level: "Trusted", threshold: "75+", effect: "Villagers become warmer, more helpful, and may treat gifts as keepsakes." },
    { level: "Neutral", threshold: "-74 to 74", effect: "Default relationship. Most systems stay available unless other conditions block them." },
    { level: "Suspicious", threshold: "-75 or below", effect: "Villagers become colder and trade pressure can worsen." },
    { level: "Hostile", threshold: "-100 or below", effect: "Villagers may refuse interaction and can be pacified if the tier is not too low." },
    { level: "Despised", threshold: "-250 or below", effect: "Villagers can become dangerous, may refuse pacification, and may attack on sight when enabled." },
    { level: "Feared", threshold: "-750 or below", effect: "The worst tier. Nearby villagers visibly react and systems become least forgiving." }
  ],
  quests: buildQuests(),
  gifts: buildGifts(),
  pacification: buildPacification(),
  skillTrades: buildSkillTrades(),
  advancements: buildAdvancements(),
  stats: buildStats()
};

const output = `window.VR_WIKI_DATA = ${JSON.stringify(data, null, 2)};\n`;
const outputPath = path.join(scriptDir, "site-data.js");
const summary = `${data.quests.length} quests, ${data.advancements.length} advancements, ${data.skillTrades.reduce((sum, group) => sum + group.count, 0)} skill trades`;

if (checkOnly) {
  const current = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, "utf8") : "";
  if (current !== output) {
    console.error("tools/player-wiki/site-data.js is out of date. Run node tools/player-wiki/build-data.mjs.");
    process.exitCode = 1;
  } else {
    console.log(`Player wiki data is up to date: ${summary}.`);
  }
} else {
  fs.writeFileSync(outputPath, output, "utf8");
  console.log(`Generated player wiki data: ${summary}.`);
}
