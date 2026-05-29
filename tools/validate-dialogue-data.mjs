import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const textTokenPattern = /\{([a-zA-Z0-9_]+)\}/g;

const roots = {
  dialogue: "neoforge/src/main/resources/data/villagerretaliation/dialogue/en_us",
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
  "metadata",
  "topic",
  "tags",
  "questline",
  "questline_id",
  "quest",
  "quest_id",
  "stage",
  "chapter",
  "notes",
  "author_notes"
]);

const nestedDialogueMetadataKeys = new Set([
  "topic",
  "tags",
  "questline",
  "questline_id",
  "quest",
  "quest_id",
  "stage",
  "chapter",
  "notes",
  "author_notes"
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
  "in_progress",
  "incomplete",
  "ready",
  "turn_in",
  "turnin",
  "completeable",
  "completable",
  "completed",
  "complete",
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
    } else if (kind === "forcedDialogue") {
      checkForcedDialogue(file, data);
    } else if (kind === "notifications") {
      checkIds(file, data.notifications ?? [], "notification");
    }
  }
}

if (errors.length > 0) {
  for (const error of errors) {
    console.error(error);
  }
  process.exitCode = 1;
} else {
  console.log("Built-in dialogue data validation passed.");
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

function checkForcedDialogue(file, data) {
  const entries = entriesFor(data);
  checkIds(file, entries, "forced dialogue entry");
  for (const [entryIndex, entry] of entries.entries()) {
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
    return;
  }
  const value = entry[key];
  if (value !== undefined && typeof value !== "string") {
    errors.push(`${relative(file)}: ${location}.${key} must be a string.`);
  }
}

function checkConditions(file, entry, location) {
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
  entry.conditions.forEach((condition, index) => checkCondition(file, condition, `${location}.conditions[${index}]`));
}

function checkCondition(file, condition, location) {
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
    checkConditionArray(file, condition.conditions, `${location}.conditions`);
  } else if (type === "not") {
    checkCondition(file, condition.condition, `${location}.condition`);
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
  } else if (type === "weather") {
    checkStringValues(file, condition, location, ["state", "states", "weather", "weathers"], weatherStates, "weather state", { requireAny: true });
  } else if (type === "time" || type === "time_of_day") {
    checkStringValues(file, condition, location, ["value", "values", "time", "times"], timesOfDay, "time of day", { requireAny: true });
  }
}

function checkConditionArray(file, conditions, location) {
  if (!Array.isArray(conditions)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  if (conditions.length === 0) {
    errors.push(`${relative(file)}: ${location} must not be empty.`);
    return;
  }
  conditions.forEach((condition, index) => checkCondition(file, condition, `${location}[${index}]`));
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
}

function checkOptionalBoolean(file, entry, location, key) {
  const value = entry[key];
  if (value !== undefined && typeof value !== "boolean") {
    errors.push(`${relative(file)}: ${location}.${key} must be a boolean.`);
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
