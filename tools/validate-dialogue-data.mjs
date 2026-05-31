import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const textTokenPattern = /\{([a-zA-Z0-9_]+)\}/g;
const metadataTagPattern = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/;

const roots = {
  dialogue: "neoforge/src/main/resources/data/villagerretaliation/dialogue/en_us",
  dialogueTrees: "neoforge/src/main/resources/data/villagerretaliation/dialogue_trees/en_us",
  forcedDialogue: "neoforge/src/main/resources/data/villagerretaliation/forced_dialogue",
  notifications: "neoforge/src/main/resources/data/villagerretaliation/notifications/en_us",
  quests: "neoforge/src/main/resources/data/villagerretaliation/quests"
};

const legacyLineFields = new Set([
  "requires_known_family",
  "requires_known_parent",
  "requires_known_sibling",
  "requires_known_spouse",
  "requires_known_child",
  "requires_known_grandparent",
  "requires_known_grandchild",
  "requires_known_descendant",
  "requires_known_aunt_uncle",
  "requires_known_cousin",
  "requires_known_niece_nephew",
  "requires_known_extended_family",
  "requires_known_deceased_family",
  "requires_known_relationship",
  "requires_known_current_relationship",
  "requires_known_past_relationship",
  "requires_known_crush",
  "requires_known_dating_partner",
  "requires_known_fiance",
  "requires_known_romantic_spouse",
  "requires_known_separated_partner",
  "requires_known_widowed_partner",
  "requires_recent_broken_bed_memory",
  "requires_recent_direct_hit_memory",
  "requires_gear_report_used_in_combat",
  "requires_gear_report_unused_in_combat",
  "requires_recruitment_memory",
  "requires_recruitment_boat_trip",
  "requires_recruitment_ocean_crossing",
  "requires_recruitment_swim_trip",
  "excludes_recruitment_ocean_crossing",
  "requires_container_theft_to_self",
  "requires_container_theft_from_other",
  "requires_retaliation_to_self",
  "requires_retaliation_from_other"
]);

const legacyOptionFields = new Set([
  "requires_known_family",
  "requires_known_parent",
  "requires_known_sibling",
  "requires_known_spouse",
  "requires_known_child",
  "requires_known_grandparent",
  "requires_known_grandchild",
  "requires_known_descendant",
  "requires_known_aunt_uncle",
  "requires_known_cousin",
  "requires_known_niece_nephew",
  "requires_known_extended_family",
  "requires_known_deceased_family",
  "requires_known_relationship",
  "requires_known_current_relationship",
  "requires_known_past_relationship",
  "requires_known_crush",
  "requires_known_dating_partner",
  "requires_known_fiance",
  "requires_known_romantic_spouse",
  "requires_known_separated_partner",
  "requires_known_widowed_partner"
]);

const conditionTypes = new Set([
  "all",
  "all_of",
  "and",
  "any",
  "any_of",
  "or",
  "not",
  "reputation",
  "memory",
  "family",
  "relationship",
  "recruitment_memory",
  "villager_age",
  "social_attribute",
  "attribute",
  "stat",
  "skill",
  "villager_level",
  "trade_level",
  "quest",
  "weather",
  "time",
  "time_of_day"
]);

const conditionKeys = {
  all: new Set(["type", "conditions"]),
  all_of: new Set(["type", "conditions"]),
  and: new Set(["type", "conditions"]),
  any: new Set(["type", "conditions"]),
  any_of: new Set(["type", "conditions"]),
  or: new Set(["type", "conditions"]),
  not: new Set(["type", "condition"]),
  reputation: new Set(["type", "level", "levels", "reputation_level", "reputation_levels", "min", "min_reputation", "max", "max_reputation"]),
  memory: new Set(["type", "kind", "tag", "tags", "source", "player"]),
  family: new Set(["type", "relation", "relations"]),
  relationship: new Set(["type", "state", "states", "relation", "relations"]),
  recruitment_memory: new Set([
    "type",
    "scenario",
    "scenarios",
    "biome",
    "biomes",
    "min_follow_distance",
    "min_recruitment_follow_distance",
    "boat_trip",
    "ocean_crossing",
    "swim_trip",
    "excludes_ocean_crossing"
  ]),
  villager_age: new Set(["type", "baby", "adult"]),
  social_attribute: new Set(["type", "attribute", "attributes", "stat", "stats", "min", "max"]),
  attribute: new Set(["type", "attribute", "attributes", "stat", "stats", "min", "max"]),
  stat: new Set(["type", "attribute", "attributes", "stat", "stats", "min", "max"]),
  skill: new Set(["type", "skill", "skills", "min", "max", "min_rank", "max_rank"]),
  villager_level: new Set(["type", "level", "levels", "min", "min_level", "max", "max_level"]),
  trade_level: new Set(["type", "level", "levels", "min", "min_level", "max", "max_level"]),
  quest: new Set(["type", "quest", "quest_id", "id", "state", "states"]),
  weather: new Set(["type", "state", "states", "weather", "weathers"]),
  time: new Set(["type", "value", "values", "time", "times"]),
  time_of_day: new Set(["type", "value", "values", "time", "times"])
};

const dialogueMetadataKeys = new Set([
  "metadata"
]);

const nestedDialogueMetadataKeys = new Set([
  "topic",
  "tags",
  "questline",
  "quest",
  "stage",
  "notes"
]);

const reputationLevels = new Set(["royalty", "revered", "respected", "trusted", "neutral", "suspicious", "hostile", "despised", "feared"]);
const socialAttributes = new Set(["knowledge", "intellect", "intelligence", "guts", "proficiency", "kindness", "charm"]);
const villagerSkills = new Set([
  "farming",
  "fishing",
  "smithing",
  "crafting",
  "trading",
  "medicine",
  "archery",
  "guarding",
  "cooking",
  "animal_handling",
  "cartography",
  "scholarship",
  "gathering",
  "masonry",
  "mining",
  "leatherworking",
  "diplomacy",
  "survival"
]);
const skillRanks = new Set(["novice", "apprentice", "skilled", "expert", "master"]);
const villagerLevels = new Set(["novice", "apprentice", "journeyman", "expert", "master", "1", "2", "3", "4", "5"]);
const questStates = new Set([
  "available",
  "not_started",
  "locked",
  "active",
  "started",
  "active_visible",
  "active_available",
  "active_conditions_met",
  "active_hidden",
  "active_unavailable",
  "inactive",
  "paused",
  "active_conditions_unmet",
  "in_progress",
  "incomplete",
  "ready",
  "turn_in",
  "turnin",
  "completeable",
  "completable",
  "completed",
  "complete",
  "abandoned",
  "dropped",
  "expired",
  "timed_out",
  "time_out",
  "consumed",
  "removed",
  "removed_forever",
  "unavailable",
  "not_completed"
]);
const memoryKinds = new Set([
  "recent_broken_bed",
  "recent_direct_hit",
  "gear_report_used_in_combat",
  "gear_report_unused_in_combat",
  "recruitment_memory"
]);
const memoryTags = new Set([
  "baby_born",
  "iron_golem_defeated_mob",
  "thunderstorm",
  "sandstorm",
  "snowstorm",
  "village_fire",
  "night_attack",
  "raid",
  "villager_death",
  "villager_attacked",
  "baby_villager_attacked",
  "player_attacked_villager",
  "player_defended_village",
  "player_defended_raid",
  "player_cured_villager",
  "golem_created",
  "golem_killed",
  "nearby_hostile_mob",
  "reputation_changed",
  "player_gave_loved_gift",
  "player_gave_liked_gift",
  "player_gave_neutral_gift",
  "player_gave_disliked_gift",
  "player_gave_hated_gift",
  "player_container_theft",
  "player_completed_quest",
  "villager_retaliation_started"
]);
const memorySources = new Set(["any", "self", "this_villager", "villager", "other", "other_villager", "another_villager"]);
const familyRelations = new Set([
  "family",
  "any",
  "parent",
  "sibling",
  "spouse",
  "child",
  "grandparent",
  "grandchild",
  "descendant",
  "aunt_uncle",
  "aunt_or_uncle",
  "cousin",
  "niece_nephew",
  "niece_or_nephew",
  "extended_family",
  "deceased_family"
]);
const relationshipStates = new Set([
  "relationship",
  "any",
  "current",
  "current_relationship",
  "past",
  "past_relationship",
  "crush",
  "dating",
  "dating_partner",
  "fiance",
  "fiancee",
  "romantic_spouse",
  "spouse",
  "separated",
  "separated_partner",
  "widowed",
  "widowed_partner"
]);
const recruitmentScenarios = new Set(["betrayed", "injured", "left_behind"]);
const weatherStates = new Set(["clear", "rain", "thunder"]);
const timesOfDay = new Set(["morning", "afternoon", "evening", "night"]);
const dialogueTreeActionTypes = new Set(["quest", "experience", "reputation", "gossip", "memory", "loot", "notification", "tracker", "forced_dialogue"]);
const dialogueTreeActionKeys = new Set([
  "type",
  "quest",
  "quest_id",
  "id",
  "action",
  "amount",
  "experience",
  "reputation",
  "gossip",
  "gossip_reputation",
  "loot_table",
  "memory_event",
  "notification",
  "trigger",
  "text",
  "forced_dialogue",
  "flash_tracker",
  "lines"
]);
const dialogueTreeQuestActions = new Set(["start", "remind", "turn_in", "abandon"]);
const questObjectiveTypes = new Set(["structure_visit", "item_check", "condition"]);
const questTriggerEvents = new Set(["player_tick", "proximity", "started", "progress", "completed", "abandoned", "expired"]);
const questAbandonmentModes = new Set(["remove_forever", "allow_repickup", "cooldown"]);

const knownPlaceholders = new Set([
  "active_order_word",
  "active_orders",
  "alternative_gift",
  "ancestor",
  "ancestor_possessive",
  "attack_weapon",
  "aunt_uncle",
  "aunt_uncle_possessive",
  "child",
  "child_possessive",
  "container",
  "container_theft_again_phrase",
  "container_theft_offense",
  "container_theft_time_word",
  "cooldown_day_word",
  "cooldown_days",
  "count",
  "current_villager",
  "cousin",
  "cousin_possessive",
  "crush",
  "crush_possessive",
  "cured_villager",
  "cured_villager_possessive",
  "day_or_days",
  "dating_partner",
  "dating_partner_possessive",
  "deceased_family",
  "deceased_family_possessive",
  "descendant",
  "descendant_possessive",
  "days_since_seen",
  "days_since_seen_phrase",
  "direction",
  "distance",
  "emerald_cost",
  "emeralds",
  "ex_partner",
  "ex_partner_possessive",
  "extended_relative",
  "extended_relative_possessive",
  "extra_cost",
  "fiance",
  "fiance_possessive",
  "follow_biome",
  "follow_distance",
  "gear_kind",
  "gift_item",
  "owner_villager",
  "gift_subject",
  "given_count",
  "given_item",
  "given_item_id",
  "given_items",
  "given_stack",
  "grandchild",
  "grandchild_possessive",
  "grandparent",
  "grandparent_possessive",
  "held_item",
  "held_item_damage",
  "held_item_durability",
  "held_item_durability_percent",
  "held_item_enchantment",
  "held_item_enchantment_full",
  "held_item_enchantment_id",
  "held_item_enchantment_level",
  "held_item_id",
  "held_item_max_durability",
  "held_item_slot",
  "has_proof",
  "interrupted_villager",
  "item",
  "item_count",
  "item_id",
  "item_stack",
  "items",
  "late_partner",
  "late_partner_possessive",
  "loot_table",
  "max_order_count_word",
  "max_order_word",
  "max_orders",
  "niece_nephew",
  "niece_nephew_possessive",
  "objective",
  "objective_complete",
  "objective_count",
  "objective_id",
  "objective_item",
  "objective_item_id",
  "objective_progress",
  "objective_target_x",
  "objective_target_y",
  "objective_target_z",
  "objective_type",
  "offer_slot",
  "parent",
  "parent_possessive",
  "partner",
  "partner_possessive",
  "payment_count",
  "payment_item",
  "payment_item_id",
  "payment_items",
  "payment_stack",
  "player",
  "profession",
  "proof_item",
  "player_item",
  "player_item_damage",
  "player_item_durability",
  "player_item_durability_percent",
  "player_item_enchantment",
  "player_item_enchantment_full",
  "player_item_enchantment_id",
  "player_item_enchantment_level",
  "player_item_id",
  "player_item_max_durability",
  "player_item_slot",
  "prior_container_thefts",
  "prior_retaliations",
  "previous_villager",
  "quest",
  "quest_id",
  "reputation",
  "reputation_level",
  "relative",
  "relative_possessive",
  "restocked_summary",
  "retaliation_offense",
  "retaliation_target",
  "retaliation_target_kind",
  "retaliation_target_name",
  "retaliation_target_type",
  "retaliation_witness",
  "retaliation_witness_possessive",
  "romantic_spouse",
  "romantic_spouse_possessive",
  "sibling",
  "sibling_possessive",
  "spouse",
  "spouse_possessive",
  "stolen_container",
  "stolen_count",
  "stolen_count_word",
  "stolen_item",
  "stolen_item_count",
  "stolen_item_id",
  "stolen_item_pronoun",
  "stolen_item_reference",
  "stolen_items",
  "stolen_stack",
  "target",
  "target_article",
  "target_kind",
  "target_name",
  "target_type",
  "target_x",
  "target_z",
  "tested_villager",
  "theft_witness",
  "theft_witness_possessive",
  "time_remaining",
  "trade_count",
  "trade_item",
  "trade_items",
  "trade_word",
  "vague_direction",
  "vertical",
  "victim",
  "victim_name",
  "victim_profession",
  "visited_target",
  "villager",
  "villager_name",
  "villager_possessive",
  "wait_day_word",
  "wait_days",
  "witnessed_container_thefts",
  "x",
  "y",
  "z"
]);

const errors = [];
const warnings = [];
const questDefinitions = new Map();
const dialogueTreeDefinitions = new Map();
const forcedDialogueDefinitions = new Map();
const pendingQuestReferences = [];
const pendingDialogueTreeLinks = [];
const pendingForcedDialogueReferences = [];
const dialogueIdScopes = {
  options: new Map(),
  lines: new Map(),
  messages: new Map(),
  openings: new Map(),
  closings: new Map(),
  pacify: new Map()
};

for (const [kind, relativeRoot] of Object.entries(roots)) {
  for (const file of await jsonFiles(path.join(root, relativeRoot))) {
    const data = await parseJson(file);
    if (data === undefined) {
      continue;
    }
    checkPlaceholders(file, data);
    if (kind === "dialogue") {
      checkDialogue(file, data);
    } else if (kind === "dialogueTrees") {
      indexDialogueTree(file, data);
      checkDialogueTree(file, data);
    } else if (kind === "forcedDialogue") {
      indexForcedDialogue(file, data);
      checkForcedDialogue(file, data);
    } else if (kind === "notifications") {
      checkIds(file, data.notifications ?? [], "notification");
    } else if (kind === "quests") {
      indexQuest(file, data);
      checkQuest(file, data);
    }
  }
}

validateCrossReferences();

if (errors.length > 0) {
  for (const error of errors) {
    console.error(error);
  }
  process.exitCode = 1;
} else {
  console.log("Built-in dialogue data validation passed.");
}

if (warnings.length > 0) {
  for (const warning of warnings) {
    console.warn(warning);
  }
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
  try {
    return JSON.parse(stripBom(await readFile(file, "utf8")));
  } catch (error) {
    errors.push(`${relative(file)}: invalid JSON: ${error.message}`);
    return undefined;
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function checkDialogue(file, data) {
  checkDialogueMetadata(file, data, "root");
  const sections = dialogueSectionsFor(file, data);
  checkDialogueIds(file, sections.options, "dialogue option", dialogueIdScopes.options);
  checkDialogueIds(file, sections.lines, "dialogue line", dialogueIdScopes.lines);
  checkDialogueIds(file, sections.messages, "dialogue message", dialogueIdScopes.messages);
  checkDialogueIds(file, sections.openings, "opening", dialogueIdScopes.openings);
  checkDialogueIds(file, sections.closings, "closing", dialogueIdScopes.closings);
  checkDialogueIds(file, sections.pacify, "pacify line", dialogueIdScopes.pacify);

  for (const [section, entries] of Object.entries(sections)) {
    for (const [index, entry] of entries.entries()) {
      checkDialogueMetadata(file, entry, `${section}[${index}]`);
    }
  }

  for (const [index, option] of sections.options.entries()) {
    for (const field of legacyOptionFields) {
      if (Object.hasOwn(option, field)) {
        errors.push(`${relative(file)}: options[${index}] uses legacy migrated field "${field}"; use conditions instead for built-in data.`);
      }
    }
    checkConditions(file, option, `options[${index}]`);
  }

  for (const [index, line] of sections.lines.entries()) {
    for (const field of legacyLineFields) {
      if (Object.hasOwn(line, field)) {
        errors.push(`${relative(file)}: lines[${index}] uses legacy migrated field "${field}"; use conditions instead for built-in data.`);
      }
    }
    checkConditions(file, line, `lines[${index}]`);
  }
}

function checkQuest(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: quest root must be an object.`);
    return;
  }

  checkUnknownObjectKeys(file, data, "root", new Set([
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
  ]));

  checkDialogueMetadata(file, data, "root");
  checkQuestMetadataConsistency(file, data, "root");
  checkDisplayObject(file, data.display, "display");
  checkQuestLinks(file, data, "links");
  checkQuestOffer(file, data.offer, "offer");
  checkQuestTarget(file, data.target, "target");
  checkQuestObjectives(file, data.objectives, "objectives");
  checkQuestRules(file, data.rules, "rules");
  checkQuestTracker(file, data.tracker, "tracker");
  checkQuestTriggers(file, data.triggers, "triggers", stringValue(data.id));
  checkQuestRewards(file, data.rewards, "rewards");
  checkQuestDialogue(file, data.dialogue, "dialogue");
  warnQuestDialogueLinkCoexistence(file, data);
}

function checkDisplayObject(file, display, location) {
  if (display === undefined) {
    return;
  }
  if (!display || typeof display !== "object" || Array.isArray(display)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, display, location, new Set(["title", "description"]));
  checkOptionalString(file, display, location, "title");
  checkOptionalString(file, display, location, "description");
}

function checkQuestOffer(file, offer, location) {
  if (offer === undefined) {
    return;
  }
  if (!offer || typeof offer !== "object" || Array.isArray(offer)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, offer, location, new Set(["professions", "min_villager_level", "skills"]));
  checkStringList(file, offer, location, ["professions"], "profession id");
  checkStringValues(file, offer, location, ["min_villager_level"], villagerLevels, "villager trade level");
  if (offer.skills !== undefined) {
    if (!offer.skills || typeof offer.skills !== "object" || Array.isArray(offer.skills)) {
      errors.push(`${relative(file)}: ${location}.skills must be an object keyed by skill id.`);
    } else {
      for (const [skill, requirement] of Object.entries(offer.skills)) {
        if (!villagerSkills.has(normalizedString(skill))) {
          errors.push(`${relative(file)}: ${location}.skills has unsupported villager skill "${skill}".`);
        }
        if (!requirement || typeof requirement !== "object" || Array.isArray(requirement)) {
          errors.push(`${relative(file)}: ${location}.skills.${skill} must be an object.`);
          continue;
        }
        checkUnknownObjectKeys(file, requirement, `${location}.skills.${skill}`, new Set(["min"]));
        checkOptionalInteger(file, requirement, `${location}.skills.${skill}`, "min", { min: 1, max: 100 });
      }
    }
  }
}

function checkQuestTarget(file, target, location) {
  if (target === undefined) {
    return;
  }
  if (!target || typeof target !== "object" || Array.isArray(target)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, target, location, new Set([
    "structure",
    "pieces",
    "search_radius",
    "discovery_radius",
    "proof_item"
  ]));
  checkOptionalString(file, target, location, "structure");
  checkStringList(file, target, location, ["pieces"], "structure piece");
  checkOptionalInteger(file, target, location, "search_radius", { min: 1 });
  checkOptionalInteger(file, target, location, "discovery_radius", { min: 1 });
  checkOptionalString(file, target, location, "proof_item");
}

function checkQuestObjectives(file, objectives, location) {
  if (objectives === undefined) {
    return;
  }
  if (!Array.isArray(objectives)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  checkIds(file, objectives, "quest objective");
  for (const [index, objective] of objectives.entries()) {
    const objectiveLocation = `${location}[${index}]`;
    if (!objective || typeof objective !== "object" || Array.isArray(objective)) {
      errors.push(`${relative(file)}: ${objectiveLocation} must be an object.`);
      continue;
    }
    checkUnknownObjectKeys(file, objective, objectiveLocation, new Set([
      "id",
      "type",
      "optional",
      "structure",
      "pieces",
      "search_radius",
      "discovery_radius",
      "item",
      "count",
      "conditions",
      "tracker"
    ]));
    checkStringValues(file, objective, objectiveLocation, ["type"], questObjectiveTypes, "quest objective type", { requireAny: true });
    const type = normalizedString(objective.type);
    checkOptionalBoolean(file, objective, objectiveLocation, "optional");
    checkOptionalString(file, objective, objectiveLocation, "structure");
    checkStringList(file, objective, objectiveLocation, ["pieces"], "structure piece");
    checkOptionalInteger(file, objective, objectiveLocation, "search_radius", { min: 1 });
    checkOptionalInteger(file, objective, objectiveLocation, "discovery_radius", { min: 1 });
    checkOptionalString(file, objective, objectiveLocation, "item");
    checkOptionalInteger(file, objective, objectiveLocation, "count", { min: 1 });
    checkConditions(file, objective, objectiveLocation);
    checkQuestObjectiveTracker(file, objective.tracker, `${objectiveLocation}.tracker`);

    if (type === "structure_visit" && !stringValue(objective.structure) && readValues(objective, ["pieces"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define structure or pieces for a structure_visit objective.`);
    }
    if (type === "item_check" && !stringValue(objective.item)) {
      errors.push(`${relative(file)}: ${objectiveLocation}.item is required for an item_check objective.`);
    }
    if (type === "condition" && (!Array.isArray(objective.conditions) || objective.conditions.length === 0)) {
      errors.push(`${relative(file)}: ${objectiveLocation}.conditions is required for a condition objective.`);
    }
  }
}

function checkQuestObjectiveTracker(file, tracker, location) {
  if (tracker === undefined) {
    return;
  }
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, tracker, location, new Set(["text", "complete_text", "show_progress", "progress", "metadata"]));
  checkOptionalString(file, tracker, location, "text");
  checkOptionalString(file, tracker, location, "complete_text");
  checkOptionalBoolean(file, tracker, location, "show_progress");
  checkOptionalNumber(file, tracker, location, "progress", { min: 0, max: 1 });
  checkStringMap(file, tracker.metadata, `${location}.metadata`);
}

function checkQuestRules(file, rules, location) {
  if (rules === undefined) {
    return;
  }
  if (!rules || typeof rules !== "object" || Array.isArray(rules)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, rules, location, new Set([
    "repeatable",
    "locked_to_villager",
    "cross_villager_compatible",
    "max_starts",
    "max_completions",
    "completion_cooldown_ticks",
    "completion_cooldown_seconds",
    "completion_cooldown_days",
    "abandonment",
    "abandonment_cooldown_ticks",
    "abandonment_cooldown_seconds",
    "abandonment_cooldown_days",
    "consume_on_completion",
    "consume_on_abandonment",
    "active",
    "expiration"
  ]));
  for (const key of ["repeatable", "locked_to_villager", "cross_villager_compatible", "consume_on_completion", "consume_on_abandonment"]) {
    checkOptionalBoolean(file, rules, location, key);
  }
  for (const key of ["max_starts", "max_completions", "completion_cooldown_ticks", "completion_cooldown_seconds", "completion_cooldown_days", "abandonment_cooldown_ticks", "abandonment_cooldown_seconds", "abandonment_cooldown_days"]) {
    checkOptionalInteger(file, rules, location, key, { min: 0 });
  }
  checkStringValues(file, rules, location, ["abandonment"], questAbandonmentModes, "quest abandonment mode");
  checkQuestActive(file, rules.active, `${location}.active`);
  checkQuestExpiration(file, rules.expiration, `${location}.expiration`);
}

function checkQuestActive(file, active, location) {
  if (active === undefined) {
    return;
  }
  if (!active || typeof active !== "object" || Array.isArray(active)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, active, location, new Set(["conditions", "hide_when_unmet", "pause_progress_when_unmet"]));
  checkConditions(file, active, location);
  checkOptionalBoolean(file, active, location, "hide_when_unmet");
  checkOptionalBoolean(file, active, location, "pause_progress_when_unmet");
}

function checkQuestExpiration(file, expiration, location) {
  if (expiration === undefined) {
    return;
  }
  if (!expiration || typeof expiration !== "object" || Array.isArray(expiration)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, expiration, location, new Set([
    "after_ticks",
    "after_seconds",
    "after_days",
    "conditions",
    "consume",
    "allow_repickup",
    "notify",
    "notification",
    "text"
  ]));
  for (const key of ["after_ticks", "after_seconds", "after_days"]) {
    checkOptionalInteger(file, expiration, location, key, { min: 0 });
  }
  checkConditions(file, expiration, location);
  for (const key of ["consume", "allow_repickup", "notify"]) {
    checkOptionalBoolean(file, expiration, location, key);
  }
  checkOptionalString(file, expiration, location, "notification");
  checkOptionalString(file, expiration, location, "text");
}

function checkQuestTracker(file, tracker, location) {
  if (tracker === undefined) {
    return;
  }
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, tracker, location, new Set(["title", "steps", "metadata"]));
  checkOptionalString(file, tracker, location, "title");
  checkStringMap(file, tracker.metadata, `${location}.metadata`);
  if (tracker.steps !== undefined) {
    if (!tracker.steps || typeof tracker.steps !== "object" || Array.isArray(tracker.steps)) {
      errors.push(`${relative(file)}: ${location}.steps must be an object keyed by step id.`);
    } else {
      for (const [stepId, step] of Object.entries(tracker.steps)) {
        const stepLocation = `${location}.steps.${stepId}`;
        if (!step || typeof step !== "object" || Array.isArray(step)) {
          errors.push(`${relative(file)}: ${stepLocation} must be an object.`);
          continue;
        }
        checkUnknownObjectKeys(file, step, stepLocation, new Set(["text", "show_progress", "progress", "metadata"]));
        checkOptionalString(file, step, stepLocation, "text");
        checkOptionalBoolean(file, step, stepLocation, "show_progress");
        checkOptionalNumber(file, step, stepLocation, "progress", { min: 0, max: 1 });
        checkStringMap(file, step.metadata, `${stepLocation}.metadata`);
      }
    }
  }
}

function checkQuestTriggers(file, triggers, location, defaultQuestId = "") {
  if (triggers === undefined) {
    return;
  }
  if (!Array.isArray(triggers)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  checkIds(file, triggers, "quest trigger");
  for (const [index, trigger] of triggers.entries()) {
    const triggerLocation = `${location}[${index}]`;
    if (!trigger || typeof trigger !== "object" || Array.isArray(trigger)) {
      errors.push(`${relative(file)}: ${triggerLocation} must be an object.`);
      continue;
    }
    checkUnknownObjectKeys(file, trigger, triggerLocation, new Set([
      "id",
      "event",
      "conditions",
      "actions",
      "cooldown_ticks",
      "cooldown_seconds",
      "cooldown_days",
      "radius",
      "repeatable"
    ]));
    checkStringValues(file, trigger, triggerLocation, ["event"], questTriggerEvents, "quest trigger event", { requireAny: true });
    const event = normalizedString(trigger.event);
    checkConditions(file, trigger, triggerLocation, defaultQuestId);
    checkDialogueTreeActions(file, trigger.actions, `${triggerLocation}.actions`, defaultQuestId);
    for (const key of ["cooldown_ticks", "cooldown_seconds", "cooldown_days"]) {
      checkOptionalInteger(file, trigger, triggerLocation, key, { min: 0 });
    }
    checkOptionalNumber(file, trigger, triggerLocation, "radius", { min: 0 });
    checkOptionalBoolean(file, trigger, triggerLocation, "repeatable");

    if ((event === "player_tick" || event === "proximity") && (!Array.isArray(trigger.actions) || trigger.actions.length === 0)) {
      errors.push(`${relative(file)}: ${triggerLocation}.actions must not be empty for continuous quest triggers.`);
    }
    if (event === "proximity" && typeof trigger.radius !== "number") {
      errors.push(`${relative(file)}: ${triggerLocation}.radius is required for a proximity quest trigger.`);
    }
  }
}

function checkQuestRewards(file, rewards, location) {
  if (rewards === undefined) {
    return;
  }
  if (!rewards || typeof rewards !== "object" || Array.isArray(rewards)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, rewards, location, new Set(["experience", "reputation", "gossip_reputation", "loot_table", "memory_event"]));
  for (const key of ["experience", "reputation", "gossip_reputation"]) {
    checkOptionalInteger(file, rewards, location, key);
  }
  checkOptionalString(file, rewards, location, "loot_table");
  checkOptionalString(file, rewards, location, "memory_event");
}

function checkQuestLinks(file, data, location) {
  const links = data.links;
  if (links === undefined) {
    return;
  }
  if (!links || typeof links !== "object" || Array.isArray(links)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, links, location, new Set([
    "dialogue_tree",
    "offer",
    "reminder",
    "turn_in",
    "forced_dialogue"
  ]));
  checkOptionalString(file, links, location, "dialogue_tree");
  checkOptionalString(file, links, location, "offer");
  checkOptionalString(file, links, location, "reminder");
  checkOptionalString(file, links, location, "turn_in");
  checkStringList(file, links, location, ["forced_dialogue"], "forced dialogue id");

  const treeId = stringValue(links.dialogue_tree);
  const offer = stringValue(links.offer);
  const reminder = stringValue(links.reminder);
  const turnIn = stringValue(links.turn_in);
  if ((offer || reminder || turnIn) && !treeId) {
    errors.push(`${relative(file)}: ${location}.dialogue_tree is required when offer, reminder, or turn_in is set.`);
  }
  if (treeId) {
    pendingDialogueTreeLinks.push({
      file,
      questId: stringValue(data.id),
      location,
      treeId,
      offer,
      reminder,
      turnIn,
      metadataQuest: stringValue(metadataObject(data).quest)
    });
  }
  for (const forcedDialogueId of readValues(links, ["forced_dialogue"])) {
    if (typeof forcedDialogueId === "string" && forcedDialogueId.trim()) {
      pendingForcedDialogueReferences.push({
        file,
        location: `${location}.forced_dialogue`,
        id: forcedDialogueId.trim(),
        reason: "quest links"
      });
    }
  }
}

function checkQuestDialogue(file, dialogue, location) {
  if (dialogue === undefined) {
    return;
  }
  if (!dialogue || typeof dialogue !== "object" || Array.isArray(dialogue)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, dialogue, location, new Set([
    "start",
    "reminder",
    "turn_in",
    "already_completed",
    "unavailable",
    "inactive",
    "missing_target",
    "missing_proof",
    "locate_failed"
  ]));

  for (const key of ["start", "reminder", "turn_in", "already_completed", "unavailable", "inactive", "missing_target", "missing_proof", "locate_failed"]) {
    checkStringList(file, dialogue, location, [key], "quest dialogue line");
  }
}

function checkDialogueTree(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: dialogue tree root must be an object.`);
    return;
  }
  const defaultQuestId = stringValue(metadataObject(data).quest);
  checkDialogueMetadata(file, data, "root");
  checkDialogueTreeMetadataConsistency(file, data, "root");
  checkConditions(file, data, "root", defaultQuestId);

  if (!Array.isArray(data.entries) || data.entries.length === 0) {
    errors.push(`${relative(file)}: dialogue tree must define at least one entry.`);
  }
  const entries = Array.isArray(data.entries) ? data.entries : [];
  checkIds(file, entries, "dialogue tree entry");
  for (const [index, entry] of entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      errors.push(`${relative(file)}: entries[${index}] must be an object.`);
      continue;
    }
    checkDialogueMetadata(file, entry, `entries[${index}]`);
    checkConditions(file, entry, `entries[${index}]`, defaultQuestId);
    checkStringList(file, entry, `entries[${index}]`, ["professions"], "profession id");
  }

  const nodes = dialogueTreeNodes(data.nodes);
  if (nodes.length === 0) {
    errors.push(`${relative(file)}: dialogue tree must define nodes.`);
    return;
  }

  const nodeIds = new Set(nodes.map((node) => node.id).filter(Boolean));
  checkIds(file, nodes, "dialogue tree node");
  for (const [index, entry] of entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      continue;
    }
    if (typeof entry.start !== "string" || !entry.start.trim()) {
      errors.push(`${relative(file)}: entries[${index}].start must reference a dialogue tree node.`);
    } else if (!nodeIds.has(entry.start)) {
      errors.push(`${relative(file)}: entries[${index}].start references unknown node "${entry.start}".`);
    }
  }
  for (const node of nodes) {
    const location = node.location;
    checkDialogueMetadata(file, node, location);
    checkConditions(file, node, location, defaultQuestId);
    checkDialogueTreeActions(file, node.actions, `${location}.actions`, defaultQuestId);
    if (Array.isArray(node.responses)) {
      checkIds(file, node.responses, "dialogue tree response");
      for (const [responseIndex, response] of node.responses.entries()) {
        const responseLocation = `${location}.responses[${responseIndex}]`;
        if (!response || typeof response !== "object" || Array.isArray(response)) {
          errors.push(`${relative(file)}: ${responseLocation} must be an object.`);
          continue;
        }
        checkDialogueMetadata(file, response, responseLocation);
        checkConditions(file, response, responseLocation, defaultQuestId);
        checkDialogueTreeActions(file, response.actions, `${responseLocation}.actions`, defaultQuestId);
        if (typeof response.next === "string" && response.next.trim() && !nodeIds.has(response.next)) {
          errors.push(`${relative(file)}: ${responseLocation}.next references unknown node "${response.next}".`);
        }
      }
    } else if (node.responses !== undefined) {
      errors.push(`${relative(file)}: ${location}.responses must be an array.`);
    }
  }
}

function dialogueTreeNodes(nodes) {
  if (!nodes || typeof nodes !== "object") {
    return [];
  }
  if (Array.isArray(nodes)) {
    return [];
  }
  return Object.entries(nodes)
    .filter(([, node]) => node && typeof node === "object" && !Array.isArray(node))
    .map(([id, node]) => ({ ...node, id: node.id ?? id, location: `nodes.${id}` }));
}

function checkDialogueTreeActions(file, actions, location, defaultQuestId = "") {
  if (actions === undefined) {
    return;
  }
  if (!Array.isArray(actions)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  for (const [index, action] of actions.entries()) {
    const actionLocation = `${location}[${index}]`;
    if (!action || typeof action !== "object" || Array.isArray(action)) {
      errors.push(`${relative(file)}: ${actionLocation} must be an object.`);
      continue;
    }
    for (const key of Object.keys(action)) {
      if (!dialogueTreeActionKeys.has(key)) {
        errors.push(`${relative(file)}: ${actionLocation}.${key} is not a supported dialogue action field.`);
      }
    }
    const type = actionType(action, defaultQuestId);
    if (!dialogueTreeActionTypes.has(type)) {
      errors.push(`${relative(file)}: ${actionLocation}.type must be one of ${[...dialogueTreeActionTypes].join(", ")}.`);
    }
    if (type === "quest") {
      checkStringList(file, action, actionLocation, ["quest", "quest_id", "id"], "quest id");
      checkStringValues(file, action, actionLocation, ["action"], dialogueTreeQuestActions, "quest action", { requireAny: true });
      const questIds = readValues(action, ["quest", "quest_id", "id"]);
      if (questIds.length === 0 && !defaultQuestId) {
        errors.push(`${relative(file)}: ${actionLocation} must define quest or quest_id unless a default quest is available.`);
      }
      for (const questId of questIds.length === 0 && defaultQuestId ? [defaultQuestId] : questIds) {
        if (typeof questId === "string" && questId.trim()) {
          pendingQuestReferences.push({
            file,
            location: actionLocation,
            id: questId.trim(),
            reason: "dialogue or trigger action"
          });
        }
      }
    }
    if (type === "forced_dialogue") {
      checkStringList(file, action, actionLocation, ["forced_dialogue"], "forced dialogue id");
      for (const forcedDialogueId of readValues(action, ["forced_dialogue"])) {
        if (typeof forcedDialogueId === "string" && forcedDialogueId.trim()) {
          pendingForcedDialogueReferences.push({
            file,
            location: actionLocation,
            id: forcedDialogueId.trim(),
            reason: "dialogue or trigger action"
          });
        }
      }
    }
    if (action.lines !== undefined && (!action.lines || typeof action.lines !== "object" || Array.isArray(action.lines))) {
      errors.push(`${relative(file)}: ${actionLocation}.lines must be an object keyed by action status.`);
    }
  }
}

function checkForcedDialogue(file, data) {
  checkDialogueMetadata(file, data, "root");
  const entries = entriesFor(data);
  checkIds(file, entries, "forced dialogue entry");
  for (const [entryIndex, entry] of entries.entries()) {
    if (Object.hasOwn(entry, "event") && !Object.hasOwn(entry, "trigger")) {
      warnings.push(`${relative(file)}: entries[${entryIndex}].event is a legacy alias for trigger; prefer trigger in new data.`);
    }
    checkDialogueMetadata(file, entry, `entries[${entryIndex}]`);
    checkForcedDialogueOptions(file, entry.options, `entries[${entryIndex}].options`);
    checkForcedDialogueOptions(file, entry.leave_options, `entries[${entryIndex}].leave_options`);
    checkForcedDialogueOption(file, entry.leave_option, `entries[${entryIndex}].leave_option`);
  }
}

function checkForcedDialogueOptions(file, options, location) {
  if (!Array.isArray(options)) {
    return;
  }
  for (const [index, option] of options.entries()) {
    checkForcedDialogueOption(file, option, `${location}[${index}]`);
  }
}

function checkForcedDialogueOption(file, option, location) {
  if (!option || typeof option !== "object" || Array.isArray(option)) {
    return;
  }
  checkConditions(file, option, location);
  if (option.follow_up && typeof option.follow_up === "object" && !Array.isArray(option.follow_up)) {
    checkForcedDialogueOptions(file, option.follow_up.options, `${location}.follow_up.options`);
    checkForcedDialogueOptions(file, option.follow_up.leave_options, `${location}.follow_up.leave_options`);
    checkForcedDialogueOption(file, option.follow_up.leave_option, `${location}.follow_up.leave_option`);
  }
}

function dialogueSectionsFor(file, data) {
  const sections = {
    options: [],
    lines: [],
    messages: [],
    openings: [],
    closings: [],
    pacify: []
  };
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return sections;
  }

  if (hasDialogueBundle(data)) {
    for (const key of Object.keys(sections)) {
      if (Array.isArray(data[key]) && (key !== "lines" || isBundledLinesSection(data))) {
        sections[key] = data[key].filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry));
      }
    }
    return sections;
  }

  const section = dialogueSectionFromPath(file) ?? inferDialogueSection(data);
  if (section) {
    sections[section] = [data];
  }
  return sections;
}

function hasDialogueBundle(data) {
  for (const key of ["options", "messages", "openings", "closings", "pacify"]) {
    if (Array.isArray(data[key])) {
      return true;
    }
  }
  return Array.isArray(data.lines) && isBundledLinesSection(data);
}

function isBundledLinesSection(data) {
  if (Object.hasOwn(data, "request") || Object.hasOwn(data, "key") || Object.hasOwn(data, "label") || Object.hasOwn(data, "outcomes")) {
    return false;
  }
  return data.lines.length === 0 || (data.lines[0] && typeof data.lines[0] === "object" && !Array.isArray(data.lines[0]));
}

function dialogueSectionFromPath(file) {
  const segments = relative(file).split("/");
  for (const segment of segments) {
    if (segment === "option" || segment === "options") {
      return "options";
    }
    if (segment === "line" || segment === "lines") {
      return "lines";
    }
    if (segment === "message" || segment === "messages") {
      return "messages";
    }
    if (segment === "opening" || segment === "openings") {
      return "openings";
    }
    if (segment === "closing" || segment === "closings") {
      return "closings";
    }
    if (segment === "pacify" || segment === "pacification") {
      return "pacify";
    }
  }
  return undefined;
}

function inferDialogueSection(data) {
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
  return undefined;
}

function checkDialogueMetadata(file, entry, location) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    return;
  }
  for (const key of dialogueMetadataKeys) {
    if (!Object.hasOwn(entry, key)) {
      continue;
    }
    if (key === "metadata") {
      checkNestedDialogueMetadata(file, entry.metadata, `${location}.metadata`);
    } else {
      checkDialogueMetadataField(file, entry, location, key);
    }
  }
}

function checkNestedDialogueMetadata(file, metadata, location) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  for (const key of Object.keys(metadata)) {
    if (!nestedDialogueMetadataKeys.has(key)) {
      errors.push(`${relative(file)}: ${location}.${key} is not supported dialogue metadata.`);
      continue;
    }
    checkDialogueMetadataField(file, metadata, location, key);
  }
}

function checkDialogueMetadataField(file, entry, location, key) {
  if (key === "tags") {
    checkStringList(file, entry, location, [key], "metadata tag");
    for (const tag of readValues(entry, [key])) {
      if (typeof tag !== "string") {
        continue;
      }
      const normalizedTag = tag.trim();
      if (normalizedTag && !metadataTagPattern.test(normalizedTag)) {
        errors.push(`${relative(file)}: ${location}.${key} has invalid metadata tag "${tag}". Use lowercase dotted, dashed, or underscored tags.`);
      }
    }
    return;
  }
  const value = entry[key];
  if (value !== undefined && typeof value !== "string") {
    errors.push(`${relative(file)}: ${location}.${key} must be a string.`);
  }
}

function checkConditions(file, entry, location, defaultQuestId = "") {
  if (!Object.hasOwn(entry, "conditions")) {
    return;
  }
  if (!Array.isArray(entry.conditions)) {
    errors.push(`${relative(file)}: ${location}.conditions must be an array.`);
    return;
  }
  if (entry.conditions.length === 0) {
    errors.push(`${relative(file)}: ${location}.conditions must not be empty.`);
    return;
  }
  entry.conditions.forEach((condition, index) => checkCondition(file, condition, `${location}.conditions[${index}]`, defaultQuestId));
}

function checkCondition(file, condition, location, defaultQuestId = "") {
  if (!condition || typeof condition !== "object" || Array.isArray(condition)) {
    errors.push(`${relative(file)}: ${location} must be a condition object.`);
    return;
  }

  const type = normalizedString(condition.type);
  if (!type) {
    errors.push(`${relative(file)}: ${location}.type is required.`);
    return;
  }
  if (!conditionTypes.has(type)) {
    errors.push(`${relative(file)}: ${location}.type "${condition.type}" is not a supported condition type.`);
    return;
  }

  const allowedKeys = conditionKeys[type];
  for (const key of Object.keys(condition)) {
    if (!allowedKeys.has(key)) {
      errors.push(`${relative(file)}: ${location}.${key} is not valid for ${type} conditions.`);
    }
  }

  if (["all", "all_of", "and", "any", "any_of", "or"].includes(type)) {
    checkConditionArray(file, condition.conditions, `${location}.conditions`, defaultQuestId);
  } else if (type === "not") {
    checkCondition(file, condition.condition, `${location}.condition`, defaultQuestId);
  } else if (type === "reputation") {
    checkStringValues(file, condition, location, ["level", "levels", "reputation_level", "reputation_levels"], reputationLevels, "reputation level");
    checkOptionalInteger(file, condition, location, "min");
    checkOptionalInteger(file, condition, location, "min_reputation");
    checkOptionalInteger(file, condition, location, "max");
    checkOptionalInteger(file, condition, location, "max_reputation");
  } else if (type === "memory") {
    checkMemoryCondition(file, condition, location);
  } else if (type === "family") {
    checkStringValues(file, condition, location, ["relation", "relations"], familyRelations, "family relation");
  } else if (type === "relationship") {
    checkStringValues(file, condition, location, ["state", "states", "relation", "relations"], relationshipStates, "relationship state");
  } else if (type === "recruitment_memory") {
    checkStringValues(file, condition, location, ["scenario", "scenarios"], recruitmentScenarios, "recruitment scenario");
    checkStringList(file, condition, location, ["biome", "biomes"], "biome id");
    checkOptionalInteger(file, condition, location, "min_follow_distance", { min: 0 });
    checkOptionalInteger(file, condition, location, "min_recruitment_follow_distance", { min: 0 });
    checkOptionalBoolean(file, condition, location, "boat_trip");
    checkOptionalBoolean(file, condition, location, "ocean_crossing");
    checkOptionalBoolean(file, condition, location, "swim_trip");
    checkOptionalBoolean(file, condition, location, "excludes_ocean_crossing");
  } else if (type === "villager_age") {
    checkOptionalBoolean(file, condition, location, "baby");
    checkOptionalBoolean(file, condition, location, "adult");
  } else if (type === "social_attribute" || type === "attribute" || type === "stat") {
    checkStringValues(file, condition, location, ["attribute", "attributes", "stat", "stats"], socialAttributes, "social attribute", { requireAny: true });
    checkOptionalInteger(file, condition, location, "min", { min: 1, max: 100 });
    checkOptionalInteger(file, condition, location, "max", { min: 1, max: 100 });
  } else if (type === "skill") {
    checkStringValues(file, condition, location, ["skill", "skills"], villagerSkills, "villager skill", { requireAny: true });
    checkOptionalInteger(file, condition, location, "min", { min: 1, max: 100 });
    checkOptionalInteger(file, condition, location, "max", { min: 1, max: 100 });
    checkStringValues(file, condition, location, ["min_rank", "max_rank"], skillRanks, "villager skill rank");
  } else if (type === "villager_level" || type === "trade_level") {
    checkStringValues(file, condition, location, ["level", "levels", "min", "min_level", "max", "max_level"], villagerLevels, "villager trade level");
  } else if (type === "quest") {
    checkStringList(file, condition, location, ["quest", "quest_id", "id"], "quest id");
    checkStringValues(file, condition, location, ["state", "states"], questStates, "quest state", { requireAny: true });
    const questIds = readValues(condition, ["quest", "quest_id", "id"]);
    if (questIds.length === 0 && !defaultQuestId) {
      errors.push(`${relative(file)}: ${location} must define quest or quest_id unless a default quest is available.`);
    }
    for (const questId of questIds.length === 0 && defaultQuestId ? [defaultQuestId] : questIds) {
      if (typeof questId === "string" && questId.trim()) {
        pendingQuestReferences.push({
          file,
          location,
          id: questId.trim(),
          reason: "quest condition"
        });
      }
    }
  } else if (type === "weather") {
    checkStringValues(file, condition, location, ["state", "states", "weather", "weathers"], weatherStates, "weather state", { requireAny: true });
  } else if (type === "time" || type === "time_of_day") {
    checkStringValues(file, condition, location, ["value", "values", "time", "times"], timesOfDay, "time of day", { requireAny: true });
  }
}

function checkConditionArray(file, conditions, location, defaultQuestId = "") {
  if (!Array.isArray(conditions)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  if (conditions.length === 0) {
    errors.push(`${relative(file)}: ${location} must not be empty.`);
    return;
  }
  conditions.forEach((condition, index) => checkCondition(file, condition, `${location}[${index}]`, defaultQuestId));
}

function checkMemoryCondition(file, condition, location) {
  const kind = normalizedString(condition.kind);
  if (kind) {
    if (!memoryKinds.has(kind)) {
      errors.push(`${relative(file)}: ${location}.kind "${condition.kind}" is not a supported memory kind.`);
    }
    return;
  }

  const tags = readValues(condition, ["tag", "tags"]);
  if (tags.length === 0) {
    errors.push(`${relative(file)}: ${location} must define kind, tag, or tags.`);
  }
  checkStringValues(file, condition, location, ["tag", "tags"], memoryTags, "memory tag");
  checkStringValues(file, condition, location, ["source"], memorySources, "memory source");
  checkOptionalBoolean(file, condition, location, "player");
}

function checkStringValues(file, entry, location, keys, allowedValues, label, options = {}) {
  const values = readValues(entry, keys);
  if (options.requireAny && values.length === 0) {
    errors.push(`${relative(file)}: ${location} must define ${keys.join(" or ")}.`);
  }
  for (const value of values) {
    const normalized = normalizedString(value);
    if (!normalized || !allowedValues.has(normalized)) {
      errors.push(`${relative(file)}: ${location} has unsupported ${label} "${value}".`);
    }
  }
}

function checkStringList(file, entry, location, keys, label) {
  for (const key of keys) {
    const value = entry[key];
    if (value === undefined) {
      continue;
    }
    if (typeof value === "string") {
      if (!value.trim()) {
        errors.push(`${relative(file)}: ${location}.${key} must not be blank.`);
      }
      continue;
    }
    if (!Array.isArray(value)) {
      errors.push(`${relative(file)}: ${location}.${key} must be a string or array of strings.`);
      continue;
    }
    value.forEach((child, index) => {
      if (typeof child !== "string" || !child.trim()) {
        errors.push(`${relative(file)}: ${location}.${key}[${index}] must be a nonblank string ${label}.`);
      }
    });
  }
}

function checkOptionalString(file, entry, location, key) {
  const value = entry[key];
  if (value !== undefined && (typeof value !== "string" || !value.trim())) {
    errors.push(`${relative(file)}: ${location}.${key} must be a nonblank string.`);
  }
}

function checkOptionalInteger(file, entry, location, key, options = {}) {
  const value = entry[key];
  if (value === undefined) {
    return;
  }
  if (!Number.isInteger(value)) {
    errors.push(`${relative(file)}: ${location}.${key} must be an integer.`);
    return;
  }
  if (options.min !== undefined && value < options.min) {
    errors.push(`${relative(file)}: ${location}.${key} must be at least ${options.min}.`);
  }
  if (options.max !== undefined && value > options.max) {
    errors.push(`${relative(file)}: ${location}.${key} must be at most ${options.max}.`);
  }
}

function checkOptionalNumber(file, entry, location, key, options = {}) {
  const value = entry[key];
  if (value === undefined) {
    return;
  }
  if (typeof value !== "number" || !Number.isFinite(value)) {
    errors.push(`${relative(file)}: ${location}.${key} must be a number.`);
    return;
  }
  if (options.min !== undefined && value < options.min) {
    errors.push(`${relative(file)}: ${location}.${key} must be at least ${options.min}.`);
  }
  if (options.max !== undefined && value > options.max) {
    errors.push(`${relative(file)}: ${location}.${key} must be at most ${options.max}.`);
  }
}

function checkOptionalBoolean(file, entry, location, key) {
  const value = entry[key];
  if (value !== undefined && typeof value !== "boolean") {
    errors.push(`${relative(file)}: ${location}.${key} must be a boolean.`);
  }
}

function checkStringMap(file, value, location) {
  if (value === undefined) {
    return;
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (typeof child !== "string") {
      errors.push(`${relative(file)}: ${location}.${key} must be a string.`);
    }
  }
}

function checkUnknownObjectKeys(file, object, location, allowedKeys) {
  for (const key of Object.keys(object)) {
    if (!allowedKeys.has(key)) {
      errors.push(`${relative(file)}: ${location}.${key} is not a supported field.`);
    }
  }
}

function readValues(entry, keys) {
  const values = [];
  for (const key of keys) {
    const value = entry[key];
    if (value === undefined) {
      continue;
    }
    if (typeof value === "string") {
      values.push(value);
    } else if (Array.isArray(value)) {
      values.push(...value);
    } else {
      values.push(value);
    }
  }
  return values;
}

function normalizedString(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function actionType(action, defaultQuestId = "") {
  const explicit = normalizedString(action.type);
  if (explicit) {
    return explicit;
  }
  if (Object.hasOwn(action, "quest") || Object.hasOwn(action, "quest_id") || Object.hasOwn(action, "id")) {
    return "quest";
  }
  if (defaultQuestId && Object.hasOwn(action, "action")) {
    return "quest";
  }
  if (Object.hasOwn(action, "forced_dialogue")) {
    return "forced_dialogue";
  }
  if (Object.hasOwn(action, "flash_tracker")) {
    return "tracker";
  }
  if (Object.hasOwn(action, "memory_event")) {
    return "memory";
  }
  if (Object.hasOwn(action, "loot_table")) {
    return "loot";
  }
  if (Object.hasOwn(action, "gossip") || Object.hasOwn(action, "gossip_reputation")) {
    return "gossip";
  }
  if (Object.hasOwn(action, "reputation")) {
    return "reputation";
  }
  if (Object.hasOwn(action, "experience")) {
    return "experience";
  }
  if (Object.hasOwn(action, "notification") || Object.hasOwn(action, "trigger") || Object.hasOwn(action, "text")) {
    return "notification";
  }
  return "";
}

function checkIds(file, entries, label) {
  const seen = new Map();
  for (const [index, entry] of entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry) || !entry.id) {
      continue;
    }
    const previous = seen.get(entry.id);
    if (previous !== undefined) {
      errors.push(`${relative(file)}: duplicate ${label} id "${entry.id}" at entries ${previous} and ${index}.`);
    }
    seen.set(entry.id, index);
  }
}

function checkDialogueIds(file, entries, label, globalSeen) {
  checkIds(file, entries, label);
  for (const [index, entry] of entries.entries()) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry) || !entry.id) {
      continue;
    }
    const previous = globalSeen.get(entry.id);
    if (previous !== undefined) {
      errors.push(`${relative(file)}: duplicate ${label} id "${entry.id}" also defined in ${previous.file} entry ${previous.index}.`);
    }
    globalSeen.set(entry.id, { file: relative(file), index });
  }
}

function entriesFor(data) {
  if (Array.isArray(data.entries)) {
    return data.entries;
  }
  return data && typeof data === "object" ? [data] : [];
}

function checkPlaceholders(file, value) {
  walkStrings(value, (text) => {
    for (const match of text.matchAll(textTokenPattern)) {
      const token = match[1];
      if (!knownPlaceholders.has(token)) {
        errors.push(`${relative(file)}: unknown placeholder "{${token}}".`);
      }
    }
  });
}

function walkStrings(value, visit) {
  if (typeof value === "string") {
    visit(value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((child) => walkStrings(child, visit));
    return;
  }
  if (value && typeof value === "object") {
    Object.values(value).forEach((child) => walkStrings(child, visit));
  }
}

function relative(file) {
  return path.relative(root, file).replaceAll(path.sep, "/");
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function metadataObject(entry) {
  return entry && typeof entry === "object" && !Array.isArray(entry) && entry.metadata && typeof entry.metadata === "object" && !Array.isArray(entry.metadata)
    ? entry.metadata
    : {};
}

function indexQuest(file, data) {
  const questId = stringValue(data?.id);
  if (!questId) {
    return;
  }
  const previous = questDefinitions.get(questId);
  if (previous) {
    errors.push(`${relative(file)}: duplicate quest id "${questId}" also defined in ${previous.file}.`);
    return;
  }
  questDefinitions.set(questId, { file: relative(file) });
}

function indexDialogueTree(file, data) {
  const treeId = stringValue(data?.id);
  if (!treeId) {
    return;
  }
  const previous = dialogueTreeDefinitions.get(treeId);
  if (previous) {
    errors.push(`${relative(file)}: duplicate dialogue tree id "${treeId}" also defined in ${previous.file}.`);
    return;
  }
  dialogueTreeDefinitions.set(treeId, {
    file: relative(file),
    entryIds: new Set((Array.isArray(data?.entries) ? data.entries : [])
      .filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry) && stringValue(entry.id))
      .map((entry) => stringValue(entry.id))),
    metadataQuest: stringValue(metadataObject(data).quest)
  });
}

function indexForcedDialogue(file, data) {
  for (const entry of entriesFor(data)) {
    const entryId = stringValue(entry?.id);
    if (!entryId) {
      continue;
    }
    const previous = forcedDialogueDefinitions.get(entryId);
    if (previous) {
      errors.push(`${relative(file)}: duplicate forced dialogue id "${entryId}" also defined in ${previous.file}.`);
      continue;
    }
    forcedDialogueDefinitions.set(entryId, { file: relative(file) });
  }
}

function checkQuestMetadataConsistency(file, data, location) {
  const metadata = metadataObject(data);
  const metadataQuestline = stringValue(metadata.questline);
  const metadataQuest = stringValue(metadata.quest);
  const questline = stringValue(data.questline);
  const questId = stringValue(data.id);

  if (metadataQuestline && !metadataQuest) {
    errors.push(`${relative(file)}: ${location}.metadata.quest is required when metadata.questline is set.`);
  }
  if (metadataQuest && !metadataQuestline) {
    errors.push(`${relative(file)}: ${location}.metadata.questline is required when metadata.quest is set.`);
  }
  if (questId && metadataQuest && metadataQuest !== questId) {
    errors.push(`${relative(file)}: ${location}.metadata.quest must match id "${questId}".`);
  }
  if (questline && metadataQuestline && metadataQuestline !== questline) {
    errors.push(`${relative(file)}: ${location}.metadata.questline must match questline "${questline}".`);
  }
}

function checkDialogueTreeMetadataConsistency(file, data, location) {
  const metadata = metadataObject(data);
  const metadataQuestline = stringValue(metadata.questline);
  const metadataQuest = stringValue(metadata.quest);
  if (metadataQuestline && !metadataQuest) {
    errors.push(`${relative(file)}: ${location}.metadata.quest is required when metadata.questline is set.`);
  }
  if (metadataQuest && !metadataQuestline) {
    errors.push(`${relative(file)}: ${location}.metadata.questline is required when metadata.quest is set.`);
  }
  if (metadataQuest) {
    pendingQuestReferences.push({
      file,
      location: `${location}.metadata.quest`,
      id: metadataQuest,
      reason: "dialogue tree metadata"
    });
  }
}

function validateCrossReferences() {
  for (const reference of pendingQuestReferences) {
    if (!questDefinitions.has(reference.id)) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing quest id "${reference.id}" from ${reference.reason}.`);
    }
  }

  for (const reference of pendingForcedDialogueReferences) {
    if (!forcedDialogueDefinitions.has(reference.id)) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing forced dialogue id "${reference.id}" from ${reference.reason}.`);
    }
  }

  for (const link of pendingDialogueTreeLinks) {
    const tree = dialogueTreeDefinitions.get(link.treeId);
    if (!tree) {
      errors.push(`${relative(link.file)}: ${link.location}.dialogue_tree references missing dialogue tree id "${link.treeId}".`);
      continue;
    }

    for (const [field, entryId] of [["offer", link.offer], ["reminder", link.reminder], ["turn_in", link.turnIn]]) {
      if (entryId && !tree.entryIds.has(entryId)) {
        errors.push(`${relative(link.file)}: ${link.location}.${field} points to missing dialogue tree entry id "${entryId}" in "${link.treeId}".`);
      }
    }

    if (link.metadataQuest && tree.metadataQuest && tree.metadataQuest !== link.metadataQuest) {
      errors.push(`${relative(link.file)}: ${link.location}.dialogue_tree points to "${link.treeId}" but its metadata.quest is "${tree.metadataQuest}" instead of "${link.metadataQuest}".`);
    }
  }
}

function warnQuestDialogueLinkCoexistence(file, data) {
  const links = data && typeof data === "object" && !Array.isArray(data) && data.links && typeof data.links === "object" && !Array.isArray(data.links)
    ? data.links
    : null;
  const dialogue = data && typeof data === "object" && !Array.isArray(data) && data.dialogue && typeof data.dialogue === "object" && !Array.isArray(data.dialogue)
    ? data.dialogue
    : null;
  if (!links || !dialogue) {
    return;
  }

  const hasLifecycleLinks = ["offer", "reminder", "turn_in"].some((key) => stringValue(links[key]));
  const hasInlineLifecycleDialogue = ["start", "reminder", "turn_in"].some((key) => readValues(dialogue, [key]).length > 0);

  if (hasLifecycleLinks && hasInlineLifecycleDialogue) {
    warnings.push(`${relative(file)}: quest defines both links and inline dialogue lifecycle text. This is supported, but keep them in sync because runtime quest speech still comes from inline dialogue and quest actions.`);
  }
  if (hasLifecycleLinks && !hasInlineLifecycleDialogue) {
    warnings.push(`${relative(file)}: quest defines dialogue links without inline lifecycle dialogue text. Runtime will fall back to default quest speech for missing stages.`);
  }
}
