(function questModelFactory(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.QuestBuilderModel = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createQuestModel() {
  "use strict";

  const SCHEMA_ID = "villagerretaliation:quest/v2";
  const RESOURCE_LOCATION = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
  const STAGE_ID = /^(?!__generated)(?!vr\$)[A-Za-z0-9_.:-]+$/;
  const STORAGE_VERSION = 1;
  const SEVERITY_ORDER = { error: 0, warning: 1, info: 2 };
  const ALLOWED_FIELDS = {
    quest: new Set(["schema", "id", "metadata", "provider", "availability", "lifecycle", "dialogue", "target", "entry_stage", "stages", "events", "rewards", "ui", "external_scenes"]),
    metadata: new Set(["title", "description", "title_key", "description_key", "questline", "tags", "parent", "show_locked_adventure_hint", "author", "version"]),
    provider: new Set(["type", "required_capabilities", "capabilities", "filters", "data"]),
    availability: new Set(["conditions", "active", "cooldown", "cooldown_ticks", "cooldown_days", "cooldown_seconds", "completion_cooldown", "completion_cooldown_ticks", "completion_cooldown_days", "completion_cooldown_seconds", "exclusive_group", "repeatable", "max_starts", "max_completions", "completion_scope", "scope", "abandonment", "abandonment_cooldown", "abandonment_cooldown_ticks", "abandonment_cooldown_days", "abandonment_cooldown_seconds", "consume_on_completion", "consume_on_abandonment", "locked_to_villager", "cross_villager_compatible", "prerequisites"]),
    stage: new Set(["id", "title", "title_key", "description", "description_key", "objectives", "complete_when", "next", "dialogue", "scenes", "responses", "events", "on_enter", "on_exit", "entry_actions", "exit_actions", "rewards", "ui", "metadata"]),
    objective: new Set(["id", "type", "optional", "count", "consume", "tracker", "conditions", "target", "targets", "structure", "dimension", "location", "radius", "search_radius", "discovery_radius", "item", "items", "item_tag", "item_tags", "entity", "entities", "entity_tag", "entity_tags", "block", "blocks", "block_tag", "block_tags", "memory", "memory_tag", "memory_tags", "gift_reaction", "gift_reactions", "reputation_level", "reputation_levels", "min", "max", "scope", "quest", "quest_id", "tag", "tags", "key", "value", "values", "stage", "stages", "choices", "metadata", "ui", "x", "y", "z", "pos", "pieces", "event", "events", "memory_event", "memory_events", "reaction", "reactions", "level", "levels", "min_reputation", "max_reputation", "fact", "variable", "counter", "custom_data", "nbt"]),
    dialogue: new Set(["scene", "scene_ref", "external", "external_scene", "external_entry", "label", "request", "show_for_babies", "order", "text", "text_key", "lines", "responses", "conditions", "actions", "metadata"]),
    response: new Set(["id", "label", "label_key", "text", "text_key", "lines", "conditions", "actions", "transition", "next", "stage", "scene", "response", "complete", "abandon", "fail", "request", "order", "metadata"]),
    rewards: new Set(["actions", "experience", "reputation", "gossip_reputation", "loot_table", "memory_event"]),
    ui: new Set(["title", "title_key", "description", "description_key", "tracker_text", "tracker_text_key", "show_progress", "progress", "placeholders", "icon", "color", "priority", "hidden"])
  };

  const OBJECTIVE_LABELS = {
    item_check: "Collect items",
    mob_kill: "Defeat mobs",
    block_break: "Break blocks",
    block_place: "Place blocks",
    block_interact: "Use a block",
    structure_visit: "Visit a structure",
    location_visit: "Reach a location",
    memory_event: "Witness an event",
    trade: "Complete trades",
    gift: "Give a gift",
    reputation: "Reach reputation",
    choice: "Make a dialogue choice",
    fact: "Match a quest fact",
    condition: "Meet a condition"
  };

  function clone(value) {
    return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
  }

  function slugify(value, fallback = "untitled") {
    const result = String(value || "")
      .trim()
      .toLowerCase()
      .replace(/['"]/g, "")
      .replace(/[^a-z0-9_./-]+/g, "_")
      .replace(/_+/g, "_")
      .replace(/^[_./-]+|[_./-]+$/g, "");
    return result || fallback;
  }

  function namespaceify(value, fallback = "my_pack") {
    return slugify(value, fallback).replace(/[/]/g, "_").replace(/[^a-z0-9_.-]/g, "_") || fallback;
  }

  function titleFromId(value) {
    return String(value || "")
      .replace(/^[^:]+:/, "")
      .split(/[./_-]+/)
      .filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(" ") || "Untitled Quest";
  }

  function uniqueId(base, existing) {
    const used = new Set((existing || []).map(String));
    if (!used.has(base)) return base;
    let index = 2;
    while (used.has(`${base}_${index}`)) index += 1;
    return `${base}_${index}`;
  }

  function createObjective(type = "item_check", index = 0) {
    const objective = {
      id: `${slugify(type, "objective")}_${index + 1}`,
      type,
      tracker: {
        text: OBJECTIVE_LABELS[type] || "Complete the objective.",
        complete_text: "Objective complete.",
        show_progress: true
      }
    };
    if (["item_check", "gift"].includes(type)) Object.assign(objective, { item: "minecraft:bread", count: 1 });
    if (type === "mob_kill") Object.assign(objective, { entity: "minecraft:zombie", count: 1 });
    if (["block_break", "block_place", "block_interact"].includes(type)) Object.assign(objective, { block: "minecraft:stone", count: 1 });
    if (type === "structure_visit") objective.structure = "minecraft:village_plains";
    if (type === "location_visit") Object.assign(objective, { x: 0, y: 64, z: 0, radius: 8 });
    if (type === "memory_event") Object.assign(objective, { event: "player_helped_villager", count: 1 });
    if (type === "trade") objective.count = 1;
    if (type === "reputation") objective.min = 10;
    if (type === "choice") Object.assign(objective, { key: "choice", values: ["accept"] });
    if (type === "fact") Object.assign(objective, { key: "progress", value: 1 });
    if (type === "condition") objective.conditions = [{ type: "reputation", min: 0 }];
    return objective;
  }

  function createStage(id = "started", options = {}) {
    const objective = options.empty ? null : createObjective(options.objectiveType || "item_check", 0);
    return {
      id,
      title: options.title || titleFromId(id),
      description: options.description || "",
      objectives: objective ? [objective] : [],
      ...(objective ? { complete_when: [objective.id] } : {}),
      dialogue: {},
      scenes: [],
      ui: {
        tracker_text: objective?.tracker?.text || "Return to the quest giver.",
        show_progress: Boolean(objective)
      }
    };
  }

  function createDialogueSlot(kind, title) {
    const slot = {
      label: title,
      request: "question",
      lines: [kind === "offer" ? "I could use your help." : kind === "turn_in" ? "You have returned." : "How is the task going?"],
      responses: []
    };
    if (kind === "offer") slot.responses.push({ id: "accept", label: "I can help.", scene: "start_quest" });
    if (kind === "turn_in") slot.responses.push({ id: "complete", label: "Complete the quest.", scene: "complete_quest" });
    return slot;
  }

  function createLinearQuest(namespace = "my_pack") {
    const safeNamespace = namespaceify(namespace);
    const title = "A Helping Hand";
    const first = createStage("gather_supplies", { title: "Gather Supplies" });
    first.next = "return_to_villager";
    first.dialogue.offer = createDialogueSlot("offer", title);
    first.dialogue.reminder = createDialogueSlot("reminder", title);
    first.scenes = [{
      id: "start_quest",
      actions: [{ type: "quest", action: "start", lines: { started: ["Bring me one loaf of bread."], unavailable: ["I cannot offer that task right now."] } }]
    }];
    const last = createStage("return_to_villager", { title: "Return", empty: true });
    last.dialogue.turn_in = createDialogueSlot("turn_in", title);
    last.scenes = [{
      id: "complete_quest",
      actions: [{ type: "quest", action: "turn_in", lines: { completed: ["Thank you. This will help."], missing_objectives: ["There is still work to finish."], unavailable: ["I cannot complete that task right now."] } }]
    }];
    return {
      schema: SCHEMA_ID,
      id: `${safeNamespace}:a_helping_hand`,
      metadata: { title, description: "Bring a villager one loaf of bread.", questline: "village_errands", tags: ["example"] },
      provider: { type: "villagerretaliation:villager" },
      availability: { repeatable: false, max_completions: 1, locked_to_villager: true },
      entry_stage: first.id,
      stages: [first, last],
      rewards: { experience: 25, reputation: 2 },
      ui: { title, tracker_text: "Bring one loaf of bread.", icon: "minecraft:bread", color: "#6f9e45" }
    };
  }

  function createBranchingQuest(namespace = "my_pack") {
    const quest = createLinearQuest(namespace);
    quest.id = `${namespaceify(namespace)}:the_crossroads`;
    quest.metadata.title = "The Crossroads";
    quest.metadata.description = "Let the player choose how to help the village.";
    quest.ui.title = quest.metadata.title;
    const choice = createStage("choose_a_path", { title: "Choose a Path", objectiveType: "choice" });
    choice.objectives[0] = { id: "choose_help", type: "choice", key: "help_path", values: ["supplies", "defense"], tracker: { text: "Choose how you will help.", complete_text: "A path has been chosen.", show_progress: false } };
    choice.complete_when = ["choose_help"];
    choice.dialogue.offer = {
      label: quest.metadata.title,
      request: "question",
      lines: ["Two problems need attention. Which one will you handle?"],
      responses: [
        { id: "supplies", label: "I will gather supplies.", stage: "gather_supplies" },
        { id: "defense", label: "I will defend the road.", stage: "defend_the_road" }
      ]
    };
    const supplies = createStage("gather_supplies", { title: "Gather Supplies" });
    supplies.next = "return_to_villager";
    const defense = createStage("defend_the_road", { title: "Defend the Road", objectiveType: "mob_kill" });
    defense.next = "return_to_villager";
    const end = quest.stages[1];
    quest.entry_stage = choice.id;
    quest.stages = [choice, supplies, defense, end];
    quest.ui.tracker_text = "Choose a way to help the village.";
    return quest;
  }

  function createProject(template = "linear", namespace = "my_pack") {
    const quest = template === "branching" ? createBranchingQuest(namespace) : createLinearQuest(namespace);
    return {
      version: STORAGE_VERSION,
      name: "Quest Project",
      namespace: namespaceify(namespace),
      selectedQuestId: quest.id,
      quests: [quest],
      updatedAt: new Date().toISOString()
    };
  }

  function normalizeProject(input) {
    const project = input && typeof input === "object" ? clone(input) : createProject();
    project.version = STORAGE_VERSION;
    project.name = String(project.name || "Quest Project");
    project.namespace = namespaceify(project.namespace || project.quests?.[0]?.id?.split(":")[0] || "my_pack");
    project.quests = Array.isArray(project.quests) ? project.quests.filter((quest) => quest && typeof quest === "object") : [];
    if (!project.quests.length) project.quests.push(createLinearQuest(project.namespace));
    project.selectedQuestId = project.quests.some((quest) => quest.id === project.selectedQuestId) ? project.selectedQuestId : project.quests[0].id;
    project.updatedAt = new Date().toISOString();
    return project;
  }

  function questFilePath(quest) {
    const match = String(quest?.id || "").match(/^([a-z0-9_.-]+):([a-z0-9_./-]+)$/);
    if (!match) return "data/my_pack/quests/untitled.json";
    return `data/${match[1]}/quests/${match[2]}.json`;
  }

  function collectEdges(quest) {
    const edges = [];
    const add = (from, target, kind, label = "") => {
      const to = typeof target === "string" ? target : target?.stage || target?.next || "";
      if (from && to) edges.push({ from, to, kind, label });
    };
    for (const stage of quest?.stages || []) {
      add(stage.id, stage.next, "next");
      for (const response of collectNamedArrays(stage, "responses")) {
        add(stage.id, response.stage || response.next || response.transition, "response", response.label || response.id);
      }
      for (const event of stage.events || []) add(stage.id, event.next || event.transition, "event", event.id || event.event || event.trigger);
    }
    return edges;
  }

  function collectNamedArrays(value, key) {
    const entries = [];
    const visit = (node) => {
      if (Array.isArray(node)) return node.forEach(visit);
      if (!node || typeof node !== "object") return;
      if (Array.isArray(node[key])) entries.push(...node[key].filter((entry) => entry && typeof entry === "object"));
      Object.values(node).forEach(visit);
    };
    visit(value);
    return entries;
  }

  function renameStage(quest, previousId, nextId) {
    if (!quest || previousId === nextId) return quest;
    const replace = (value) => value === previousId ? nextId : value;
    for (const stage of quest.stages || []) {
      if (stage.id === previousId) stage.id = nextId;
      if (typeof stage.next === "string") stage.next = replace(stage.next);
      else if (stage.next && typeof stage.next === "object") {
        if (stage.next.stage) stage.next.stage = replace(stage.next.stage);
        if (stage.next.next) stage.next.next = replace(stage.next.next);
      }
      for (const response of collectNamedArrays(stage, "responses")) {
        if (response.stage) response.stage = replace(response.stage);
        if (response.next) response.next = replace(response.next);
        if (response.transition?.stage) response.transition.stage = replace(response.transition.stage);
      }
      for (const event of stage.events || []) {
        if (event.stage) event.stage = replace(event.stage);
        if (event.next) event.next = replace(event.next);
        if (event.transition?.stage) event.transition.stage = replace(event.transition.stage);
      }
    }
    quest.entry_stage = replace(quest.entry_stage);
    return quest;
  }

  function removeStage(quest, stageId) {
    if (!quest || !Array.isArray(quest.stages)) return quest;
    quest.stages = quest.stages.filter((stage) => stage.id !== stageId);
    const fallback = quest.stages[0]?.id || "";
    if (quest.entry_stage === stageId) quest.entry_stage = fallback;
    for (const stage of quest.stages) {
      if (stage.next === stageId) delete stage.next;
      for (const response of collectNamedArrays(stage, "responses")) {
        if (response.stage === stageId) delete response.stage;
        if (response.next === stageId) delete response.next;
        if (response.transition?.stage === stageId) delete response.transition.stage;
      }
    }
    return quest;
  }

  function validateQuest(quest, registries = {}) {
    const issues = [];
    const issue = (severity, code, path, message, hint) => issues.push({ severity, code, path, message, hint });
    if (!quest || typeof quest !== "object" || Array.isArray(quest)) {
      issue("error", "quest.invalid", "$", "Quest data must be a JSON object.", "Import a Quest v2 JSON file or start from a template.");
      return issues;
    }
    validateUnknownFields(quest, ALLOWED_FIELDS.quest, "", "quest", issue);
    validateUnknownFields(quest.metadata, ALLOWED_FIELDS.metadata, "/metadata", "quest metadata", issue);
    validateUnknownFields(quest.provider, ALLOWED_FIELDS.provider, "/provider", "provider", issue);
    validateUnknownFields(quest.availability, ALLOWED_FIELDS.availability, "/availability", "availability", issue);
    validateUnknownFields(quest.rewards, ALLOWED_FIELDS.rewards, "/rewards", "rewards", issue);
    validateUnknownFields(quest.ui, ALLOWED_FIELDS.ui, "/ui", "quest UI", issue);
    if (quest.schema !== SCHEMA_ID) issue("error", "quest.schema", "/schema", `Schema must be ${SCHEMA_ID}.`, "Set the schema field to the supported Quest v2 id.");
    if (!RESOURCE_LOCATION.test(String(quest.id || ""))) issue("error", "quest.id", "/id", "Quest id must be a namespaced resource location.", "Use lowercase text such as my_pack:first_steps.");
    if (!quest.provider || typeof quest.provider !== "object") issue("error", "provider.missing", "/provider", "A quest provider is required.", "Choose the Villager provider in Quest setup.");
    if (quest.provider?.type !== "villagerretaliation:villager") issue("error", "provider.type", "/provider/type", "Provider type is not supported by this version.", "Use villagerretaliation:villager.");
    const stages = Array.isArray(quest.stages) ? quest.stages : [];
    if (!stages.length) issue("error", "stages.empty", "/stages", "The quest needs at least one stage.", "Add a stage to define what the player does.");
    const stageIds = stages.map((stage) => String(stage?.id || ""));
    const stageSet = new Set(stageIds.filter(Boolean));
    const duplicates = stageIds.filter((id, index) => id && stageIds.indexOf(id) !== index);
    if (!quest.entry_stage) issue("error", "entry.missing", "/entry_stage", "Choose where the quest begins.", "Set an entry stage in Quest setup.");
    else if (!stageSet.has(quest.entry_stage)) issue("error", "entry.unknown", "/entry_stage", `Entry stage “${quest.entry_stage}” does not exist.`, "Choose one of the stages in this quest.");
    for (const duplicate of new Set(duplicates)) issue("error", "stage.duplicate", "/stages", `Stage id “${duplicate}” is used more than once.`, "Give every stage a unique id.");

    const objectiveIds = new Set();
    const objectiveRegistry = registrySet(registries, "objectives");
    const actionRegistry = registrySet(registries, "actions");
    const conditionRegistry = registrySet(registries, "conditions");
    const triggerRegistry = registrySet(registries, "triggers");
    stages.forEach((stage, stageIndex) => {
      const base = `/stages/${stageIndex}`;
      validateUnknownFields(stage, ALLOWED_FIELDS.stage, base, "stage", issue);
      validateUnknownFields(stage?.rewards, ALLOWED_FIELDS.rewards, `${base}/rewards`, "stage rewards", issue);
      validateUnknownFields(stage?.ui, ALLOWED_FIELDS.ui, `${base}/ui`, "stage UI", issue);
      if (!stage?.id) issue("error", "stage.id.missing", `${base}/id`, `Stage ${stageIndex + 1} needs an id.`, "Use a short id such as gather_supplies.");
      else if (!STAGE_ID.test(stage.id)) issue("error", "stage.id.invalid", `${base}/id`, `Stage id “${stage.id}” contains unsupported characters.`, "Use letters, numbers, dots, underscores, colons, or hyphens.");
      if (!Array.isArray(stage?.objectives)) issue("error", "objectives.missing", `${base}/objectives`, `Stage “${stage?.id || stageIndex + 1}” needs an objectives array.`, "Use an empty list for a return-only stage.");
      (stage?.objectives || []).forEach((objective, objectiveIndex) => {
        const path = `${base}/objectives/${objectiveIndex}`;
        validateUnknownFields(objective, ALLOWED_FIELDS.objective, path, "objective", issue);
        if (!objective?.id) issue("error", "objective.id.missing", `${path}/id`, "Objective needs an id.", "Use a unique id such as bring_bread.");
        else if (objectiveIds.has(objective.id)) issue("error", "objective.id.duplicate", `${path}/id`, `Objective id “${objective.id}” is duplicated.`, "Objective ids must be unique across the quest.");
        else objectiveIds.add(objective.id);
        if (!objective?.type) issue("error", "objective.type.missing", `${path}/type`, `Objective “${objective?.id || objectiveIndex + 1}” needs a type.`, "Choose the action the player must complete.");
        else if (objectiveRegistry.size && !objectiveRegistry.has(objective.type)) issue("error", "objective.type.unknown", `${path}/type`, `Objective type “${objective.type}” is not registered.`, "Choose a supported objective type.");
        validateObjectiveFields(objective, path, issue);
      });
      const localObjectiveIds = new Set((stage?.objectives || []).map((objective) => objective?.id).filter(Boolean));
      for (const id of stage?.complete_when || []) {
        if (!localObjectiveIds.has(id)) issue("error", "complete_when.unknown", `${base}/complete_when`, `Completion rule references missing objective “${id}”.`, "Select an objective from this stage.");
      }
      validateDialogue(stage, base, stageSet, issue);
    });

    for (const edge of collectEdges(quest)) {
      if (!stageSet.has(edge.to)) issue("error", "transition.unknown", `/stages/${Math.max(0, stageIds.indexOf(edge.from))}`, `Transition from “${edge.from}” points to missing stage “${edge.to}”.`, "Choose an existing destination or add the missing stage.");
      if (edge.from === edge.to) issue("warning", "transition.self", `/stages/${Math.max(0, stageIds.indexOf(edge.from))}`, `Stage “${edge.from}” loops to itself.`, "Keep this only if the loop is intentional and can eventually exit.");
    }

    const reachable = reachableStages(quest);
    for (const stage of stages) {
      if (stage.id && quest.entry_stage && !reachable.has(stage.id)) issue("warning", "stage.unreachable", `/stages/${stageIds.indexOf(stage.id)}`, `Stage “${stage.id}” cannot be reached from the entry stage.`, "Connect it from another stage or remove it.");
      const hasTransition = collectEdges({ stages: [stage] }).length > 0;
      const hasTurnIn = Boolean(stage.dialogue?.turn_in) || collectNamedArrays(stage, "actions").some((action) => action.type === "quest" && ["turn_in", "complete"].includes(action.action));
      if (stage.id && !hasTransition && !hasTurnIn && (stage.objectives || []).length) issue("warning", "stage.dead_end", `/stages/${stageIds.indexOf(stage.id)}`, `Stage “${stage.id}” has no next stage or completion dialogue.`, "Choose a next stage or add turn-in dialogue.");
    }
    validateRegistryBlocks(quest, "actions", ["type", "action"], actionRegistry, issue);
    validateRegistryBlocks(quest, "conditions", ["type"], conditionRegistry, issue);
    validateRegistryBlocks(quest, "events", ["event", "trigger", "type"], triggerRegistry, issue);
    if (quest.rewards?.loot_table && !RESOURCE_LOCATION.test(quest.rewards.loot_table)) issue("error", "rewards.loot_table", "/rewards/loot_table", "Loot table must be a namespaced resource location.", "Use a value such as my_pack:quest/reward.");
    if (quest.ui?.icon && !RESOURCE_LOCATION.test(quest.ui.icon)) issue("error", "ui.icon", "/ui/icon", "Quest icon must be a namespaced item id.", "Use a value such as minecraft:book.");
    return issues.sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || a.path.localeCompare(b.path));
  }

  function validateObjectiveFields(objective, path, issue) {
    const resourceFields = {
      item_check: ["item"], gift: ["item"], mob_kill: ["entity"],
      block_break: ["block"], block_place: ["block"], block_interact: ["block"],
      structure_visit: ["structure"]
    };
    for (const field of resourceFields[objective?.type] || []) {
      if (!objective[field]) issue("error", "objective.target.missing", `${path}/${field}`, `Objective “${objective.id || "untitled"}” needs a ${field}.`, `Enter a namespaced id such as minecraft:${field === "entity" ? "zombie" : field === "item" ? "bread" : "stone"}.`);
      else if (!RESOURCE_LOCATION.test(String(objective[field]))) issue("error", "objective.target.invalid", `${path}/${field}`, `“${objective[field]}” is not a valid namespaced id.`, "Use lowercase namespace:path format.");
    }
    if (["item_check", "gift", "mob_kill", "block_break", "block_place", "trade"].includes(objective?.type) && (!Number.isInteger(Number(objective.count)) || Number(objective.count) < 1)) {
      issue("error", "objective.count", `${path}/count`, `Objective “${objective.id || "untitled"}” needs a count of at least 1.`, "Enter a whole number.");
    }
    if (objective?.type === "choice" && !objective.key && !(Array.isArray(objective.values) && objective.values.length)) {
      issue("error", "objective.choice", path, `Choice objective “${objective.id || "untitled"}” needs a fact key or allowed values.`, "Set a key such as route and list the response values players may choose.");
    }
  }

  function validateDialogue(stage, base, stageSet, issue) {
    for (const [slotName, slot] of Object.entries(stage?.dialogue || {})) {
      const path = `${base}/dialogue/${slotName}`;
      if (!slot || typeof slot !== "object") {
        issue("error", "dialogue.invalid", path, `Dialogue slot “${slotName}” must be an object.`, "Edit or remove the invalid slot.");
        continue;
      }
      validateUnknownFields(slot, ALLOWED_FIELDS.dialogue, path, "dialogue slot", issue);
      const hasContent = Boolean(slot.text || slot.text_key || (Array.isArray(slot.lines) && slot.lines.some(Boolean)) || slot.external || slot.external_scene || slot.scene || slot.scene_ref);
      if (!hasContent) issue("warning", "dialogue.empty", path, `Dialogue slot “${slotName}” has no lines or scene reference.`, "Add what the villager should say.");
      const responseIds = new Set();
      for (const [index, response] of (slot.responses || []).entries()) {
        validateUnknownFields(response, ALLOWED_FIELDS.response, `${path}/responses/${index}`, "dialogue response", issue);
        if (!response.id) issue("error", "response.id.missing", `${path}/responses/${index}/id`, "Dialogue response needs an id.", "Use an id such as accept or decline.");
        else if (responseIds.has(response.id)) issue("error", "response.id.duplicate", `${path}/responses/${index}/id`, `Response id “${response.id}” is duplicated in this dialogue slot.`, "Give each response a unique id.");
        else responseIds.add(response.id);
        const target = response.stage || response.next || response.transition?.stage;
        if (target && !stageSet.has(target)) issue("error", "response.stage.unknown", `${path}/responses/${index}`, `Response points to missing stage “${target}”.`, "Choose an existing stage.");
        if ((response.stage || response.next || response.scene) && response.actions?.some((action) => action.type === "quest_transition" || action.transition || action.stage || action.next)) {
          issue("warning", "response.transition.conflict", `${path}/responses/${index}`, `Response “${response.id || index + 1}” has two transition sources.`, "Keep either the direct destination or the transition action.");
        }
      }
    }
  }

  function validateUnknownFields(value, allowed, path, label, issue) {
    if (!value || typeof value !== "object" || Array.isArray(value)) return;
    for (const key of Object.keys(value)) {
      if (!key.startsWith("__") && !allowed.has(key)) {
        issue("error", `${label.replaceAll(" ", ".")}.field.unsupported`, `${path}/${key}`, `${label.charAt(0).toUpperCase() + label.slice(1)} field “${key}” is not supported by Quest v2.`, "Remove the field or move its data to a supported field.");
      }
    }
  }

  function registrySet(registries, name) {
    const entries = Array.isArray(registries?.[name]) ? registries[name] : Array.isArray(registries?.registries?.[name]) ? registries.registries[name] : [];
    return new Set(entries.flatMap((entry) => typeof entry === "string" ? [entry] : [entry.id, ...(entry.aliases || [])]).filter(Boolean));
  }

  function validateRegistryBlocks(quest, key, candidateKeys, allowed, issue) {
    if (!allowed.size) return;
    for (const block of collectNamedArrays(quest, key)) {
      const value = candidateKeys.map((candidate) => block?.[candidate]).find(Boolean);
      if (value && !allowed.has(value)) issue("error", `${key}.unknown`, `/${key}`, `Unknown ${key.slice(0, -1)} type “${value}”.`, "Choose a type from the loaded Quest registry.");
    }
  }

  function reachableStages(quest) {
    const visited = new Set();
    const outgoing = new Map();
    for (const edge of collectEdges(quest)) {
      if (!outgoing.has(edge.from)) outgoing.set(edge.from, []);
      outgoing.get(edge.from).push(edge.to);
    }
    const queue = quest?.entry_stage ? [quest.entry_stage] : [];
    while (queue.length) {
      const current = queue.shift();
      if (visited.has(current)) continue;
      visited.add(current);
      for (const next of outgoing.get(current) || []) if (!visited.has(next)) queue.push(next);
    }
    return visited;
  }

  function summarizeIssues(issues) {
    return (issues || []).reduce((summary, issue) => {
      summary[issue.severity] = (summary[issue.severity] || 0) + 1;
      return summary;
    }, { error: 0, warning: 0, info: 0 });
  }

  function validateProject(project, registries = {}) {
    const quests = Array.isArray(project?.quests) ? project.quests : [];
    const issues = quests.flatMap((quest, questIndex) => validateQuest(quest, registries).map((issue) => ({
      ...issue,
      questIndex,
      questId: quest?.id || ""
    })));
    const paths = quests.map(questFilePath);
    paths.forEach((path, questIndex) => {
      if (paths.indexOf(path) === questIndex) return;
      issues.push({
        severity: "error",
        code: "project.path.duplicate",
        path: "/id",
        message: `More than one quest exports to ${path}.`,
        hint: "Give every quest a unique namespaced id.",
        questIndex,
        questId: quests[questIndex]?.id || ""
      });
    });
    return issues.sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || a.questIndex - b.questIndex || a.path.localeCompare(b.path));
  }

  function stripBuilderFields(value) {
    if (Array.isArray(value)) return value.map(stripBuilderFields);
    if (value && typeof value === "object") {
      return Object.fromEntries(Object.entries(value).filter(([key]) => !key.startsWith("__")).map(([key, child]) => [key, stripBuilderFields(child)]));
    }
    return value;
  }

  return {
    SCHEMA_ID,
    STORAGE_VERSION,
    OBJECTIVE_LABELS,
    RESOURCE_LOCATION,
    STAGE_ID,
    clone,
    slugify,
    namespaceify,
    titleFromId,
    uniqueId,
    createObjective,
    createStage,
    createDialogueSlot,
    createLinearQuest,
    createBranchingQuest,
    createProject,
    normalizeProject,
    questFilePath,
    collectEdges,
    collectNamedArrays,
    renameStage,
    removeStage,
    reachableStages,
    validateQuest,
    validateProject,
    summarizeIssues,
    stripBuilderFields
  };
});
