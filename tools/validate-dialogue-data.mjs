import { createRequire } from "node:module";
import { validateContentSchema } from "./content-schema-validation.mjs";
import { readFile, readdir } from "node:fs/promises";
import { validateContextDirectory } from "./dialogue-context-validation.mjs";
import { inspectDialogueMarkup } from "./dialogue-markup-validation.mjs";
import { validateWordDirectory } from "./dialogue-word-validation.mjs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { validateConversation, validateConversationSchema, materializeConversationAction } from "./conversation-validation.mjs";
import { campaignDataRoot, validateCampaignPack } from "./campaign-pack-validation.mjs";
import { scenarioPath, scenarioContext, validateCampaignScenario } from "./campaign-scenario-validation.mjs";
import { socialProgressionPath, validateSocialProgression, collectSocialProgressions } from "./social-progression-validation.mjs";

const questV3Format = createRequire(import.meta.url)("./quest-v3-format.js");
const questV3Schema = JSON.parse(await readFile(new URL("./schemas/quest-v3.schema.json", import.meta.url), "utf8"));

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const textTokenPattern = /\{([a-zA-Z0-9_]+)\}/g;
const metadataTagPattern = /^[a-z0-9]+(?:[._:/-][a-z0-9]+)*$/;
const dialogueOptionIdMaxLength = 128;
const dialogueOptionLabelMaxLength = 128;

const roots = {
  dialogue: "neoforge/src/main/resources/data/villagerretaliation/dialogue/en_us",
  dialogueTrees: "neoforge/src/main/resources/data/villagerretaliation/dialogue_trees/en_us",
  forcedDialogue: "neoforge/src/main/resources/data/villagerretaliation/forced_dialogue",
  lootTables: "neoforge/src/main/resources/data/villagerretaliation/loot_table",
  notifications: "neoforge/src/main/resources/data/villagerretaliation/notifications/en_us",
  quests: "neoforge/src/main/resources/data/villagerretaliation/quests",
  skillTrades: "neoforge/src/main/resources/data/villagerretaliation/skill_trades",
  campaigns: "neoforge/src/main/resources/data/villagerretaliation/campaigns",
  scenarios: "neoforge/src/main/resources/data/villagerretaliation/campaign_scenarios"
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
  "quest_fact",
  "quest_tag",
  "quest_variable",
  "quest_counter",
  "quest_stage",
  "fact",
  "stage",
  "selected_choice",
  "choice_selected",
  "response_selected",
  "quest_choice_selected",
  "stage_history",
  "quest_stage_history",
  "visited_stage",
  "player_item",
  "item",
  "held_item",
  "villager_equipment",
  "equipment",
  "armed",
  "biome",
  "dimension",
  "advancement",
  "advancements",
  "scoreboard",
  "score",
  "nearby_entity",
  "nearby_entities",
  "entity_nearby",
  "village",
  "village_presence",
  "mood",
  "villager_mood",
  "weather",
  "time",
  "time_of_day"
]);

const questFactConditionKeys = new Set([
  "type",
  "quest",
  "quest_id",
  "scope",
  "tag",
  "tags",
  "fact_tag",
  "quest_tag",
  "key",
  "variable",
  "counter",
  "fact",
  "value",
  "values",
  "stage",
  "stages",
  "min",
  "max"
]);

const playerItemConditionKeys = new Set([
  "type", "item", "items", "item_tag", "item_tags", "slot", "slots",
  "player_item", "player_items", "player_item_tag", "player_item_tags", "player_item_slot", "player_item_slots",
  "enchantment", "enchantments", "player_item_enchantment", "player_item_enchantments",
  "min_player_item_durability", "max_player_item_durability",
  "min_player_item_durability_percent", "max_player_item_durability_percent",
  "min_player_item_enchantment_level", "max_player_item_enchantment_level",
  "components", "player_item_components", "custom_data", "player_item_custom_data", "nbt", "player_item_nbt"
]);
const selectedChoiceConditionKeys = new Set([
  "type", "quest", "quest_id", "scene", "scene_path", "response", "response_id", "prior_stage", "next_stage"
]);
const stageHistoryConditionKeys = new Set([
  "type", "quest", "quest_id", "stage", "prior_stage", "next_stage"
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
  quest_fact: questFactConditionKeys,
  quest_tag: questFactConditionKeys,
  quest_variable: questFactConditionKeys,
  quest_counter: questFactConditionKeys,
  quest_stage: questFactConditionKeys,
  fact: questFactConditionKeys,
  stage: questFactConditionKeys,
  selected_choice: selectedChoiceConditionKeys,
  choice_selected: selectedChoiceConditionKeys,
  response_selected: selectedChoiceConditionKeys,
  quest_choice_selected: selectedChoiceConditionKeys,
  stage_history: stageHistoryConditionKeys,
  quest_stage_history: stageHistoryConditionKeys,
  visited_stage: stageHistoryConditionKeys,
  player_item: playerItemConditionKeys,
  item: playerItemConditionKeys,
  held_item: playerItemConditionKeys,
  villager_equipment: new Set(["type", "armed", "unarmed", "requires_villager_armed", "villager_armed", "requires_villager_unarmed", "villager_unarmed"]),
  equipment: new Set(["type", "armed", "unarmed", "requires_villager_armed", "villager_armed", "requires_villager_unarmed", "villager_unarmed"]),
  armed: new Set(["type", "armed", "unarmed", "requires_villager_armed", "villager_armed", "requires_villager_unarmed", "villager_unarmed"]),
  biome: new Set(["type", "biome", "biomes", "tag", "tags", "biome_tag", "biome_tags"]),
  dimension: new Set(["type", "dimension", "dimensions", "value", "values"]),
  advancement: new Set(["type", "advancement", "advancements", "id", "ids", "all"]),
  advancements: new Set(["type", "advancement", "advancements", "id", "ids", "all"]),
  scoreboard: new Set(["type", "objective", "score", "value", "min", "max"]),
  score: new Set(["type", "objective", "score", "value", "min", "max"]),
  nearby_entity: new Set(["type", "entity", "entities", "entity_type", "entity_types", "entity_tag", "entity_tags", "radius", "min_count", "max_count", "origin"]),
  nearby_entities: new Set(["type", "entity", "entities", "entity_type", "entity_types", "entity_tag", "entity_tags", "radius", "min_count", "max_count", "origin"]),
  entity_nearby: new Set(["type", "entity", "entities", "entity_type", "entity_types", "entity_tag", "entity_tags", "radius", "min_count", "max_count", "origin"]),
  village: new Set(["type", "present", "key", "keys", "village", "villages"]),
  village_presence: new Set(["type", "present", "key", "keys", "village", "villages"]),
  mood: new Set(["type", "mood", "moods", "state", "states", "min", "min_intensity", "min_mood_intensity", "max", "max_intensity", "max_mood_intensity"]),
  villager_mood: new Set(["type", "mood", "moods", "state", "states", "min", "min_intensity", "min_mood_intensity", "max", "max_intensity", "max_mood_intensity"]),
  weather: new Set(["type", "state", "states", "weather", "weathers"]),
  time: new Set(["type", "value", "values", "time", "times"]),
  time_of_day: new Set(["type", "value", "values", "time", "times"])
};

const questLiveOnlyConditionTypes = new Set([
  "memory"
]);

const questSavedMemoryKinds = new Set([
  "recent_broken_bed",
  "recent_direct_hit",
  "gear_report_used_in_combat",
  "gear_report_unused_in_combat",
  "recruitment_memory"
]);

const questLiveContextActionTypes = new Set([
  "quest",
  "reputation",
  "gossip",
  "memory",
  "loot",
  "notification",
  "forced_dialogue"
]);

const questTriggerEventsThatMayLackLiveIssuer = new Set([
  "player_tick",
  "proximity",
  "progress"
]);

const dialogueMetadataKeys = new Set([
  "metadata"
]);

const nestedDialogueMetadataKeys = new Set([
  "topic",
  "tags",
  "routing_tags",
  "anti_repeat_groups",
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
  "failed",
  "failure",
  "expired",
  "timed_out",
  "time_out",
  "consumed",
  "removed",
  "removed_forever",
  "branch_locked",
  "branch_blocked",
  "blocked_branch",
  "unavailable",
  "not_completed"
]);
const savedQuestStates = new Set([
  "not_started",
  "locked",
  "active",
  "started",
  "completed",
  "complete",
  "abandoned",
  "dropped",
  "failed",
  "failure",
  "expired",
  "timed_out",
  "time_out",
  "consumed",
  "removed",
  "removed_forever",
  "branch_locked",
  "branch_blocked",
  "blocked_branch",
  "not_completed"
]);
const questDialogueTreeLifecycleStates = new Map([
  ["offer", new Set(["available", "not_started", "locked"])],
  ["reminder", new Set(["in_progress", "active", "started"])],
  ["turn_in", new Set(["ready", "turn_in", "turnin", "completeable", "completable"])]
]);
const questDialogueTreeLifecycleActions = new Map([
  ["offer", new Set(["start", "accept", "begin"])],
  ["reminder", new Set(["remind", "reminder", "details"])],
  ["turn_in", new Set(["turn_in", "turnin", "complete", "claim"])]
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
  "player_killed_villager",
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
const memorySources = new Set(["any", "self", "this_entity", "entity", "this_villager", "villager", "other", "other_entity", "another_entity", "other_villager", "another_villager"]);
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
const moodStates = new Set(["neutral", "content", "grateful", "afraid", "angry", "suspicious", "grieving", "protective", "hopeful", "stressed", "proud", "lonely"]);
const weatherStates = new Set(["clear", "rain", "thunder"]);
const timesOfDay = new Set(["morning", "afternoon", "evening", "night"]);
const vanillaVillagerProfessions = new Set([
  "none",
  "armorer",
  "butcher",
  "cartographer",
  "cleric",
  "farmer",
  "fisherman",
  "fletcher",
  "leatherworker",
  "librarian",
  "mason",
  "nitwit",
  "shepherd",
  "toolsmith",
  "weaponsmith"
]);
const dialogueTreeActionTypes = new Set(["quest", "experience", "reputation", "gossip", "memory", "loot", "notification", "tracker", "forced_dialogue", "set_tag", "clear_tag", "set_variable", "counter"]);
const questFactScopes = new Set(["player", "player_world", "per_player", "world", "global", "server", "quest", "quest_progress", "player_quest", "entity", "actor", "villager", "issuer", "quest_giver", "group", "village", "settlement"]);
const forcedDialogueTriggers = new Set(["container_theft", "container_opened", "container_broken", "retaliation_started", "low_guts_rally", "low_guts_pursuit_abandoned", "low_guts_counter_completed", "retaliation_target_escaped", "retaliation_search_expired", "player_raid_betrayal", "player_item_proximity", "trade_refresh", "quest"]);
const forcedDialogueOutputModes = new Set(["forced_dialogue", "chat"]);
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
  "set_tag",
  "clear_tag",
  "fact_tag",
  "quest_tag",
  "tag",
  "variable",
  "key",
  "value",
  "stage",
  "fact",
  "counter",
  "increment_counter",
  "scope",
  "fact_scope",
  "lines"
]);
const dialogueTreeRootKeys = new Set(["id", "replace", "remove", "display", "metadata", "conditions", "entries", "nodes"]);
const dialogueTreeDisplayKeys = new Set(["title", "description"]);
const dialogueTreeEntryKeys = new Set([
  "label_key",
  "id",
  "label",
  "metadata",
  "start",
  "request",
  "show_for_adults",
  "show_for_babies",
  "professions",
  "disposition",
  "dispositions",
  "conditions",
  "force_camera_towards_villager",
  "order"
]);
const dialogueTreeNodeKeys = new Set(["response_timer", "text_key", "variants", "usage", "id", "lines", "text", "exchanges", "camera", "metadata", "actions", "conditions", "responses", "end"]);
const dialogueTreeResponseKeys = new Set(["unavailable_behavior", "unavailable", "label_key", "text_key", "variants", "usage", "priority", "id", "label", "metadata", "next", "request", "lines", "text", "exchanges", "camera", "actions", "conditions", "end", "order"]);
const dialogueRequestTypes = new Set([
  "greeting",
  "question",
  "gift_preferences",
  "gift_advice_followup",
  "map_report",
  "story_hint_report",
  "combat_survival_report",
  "gear_report",
  "recruitment_followup",
  "cured_recognition",
  "village_event_report",
  "apology",
  "village_defense_report",
  "story",
  "share_story",
  "joke",
  "insult"
]);
const dialogueDispositions = new Set(["friendly", "respectful", "neutral", "cautious", "rude", "hostile", "fearful"]);
const dialogueTreeQuestActions = new Set(["start", "accept", "begin", "remind", "reminder", "details", "turn_in", "turnin", "complete", "claim", "abandon", "drop", "cancel", "remove", "block", "lock", "consume", "close", "close_branch", "branch_lock"]);
const questDialogueStages = [
  "start",
  "reminder",
  "turn_in",
  "already_completed",
  "unavailable",
  "inactive",
  "missing_target",
  "missing_proof",
  "locate_failed"
];
const questTextReferenceKeys = new Set([
  "title_key",
  "description_key",
  "text_key",
  "text_keys",
  "complete_text_key",
  "notification_text_key",
  "label_key",
  "reason_key",
  ...questDialogueStages.map((key) => `${key}_key`),
  ...questDialogueStages.map((key) => `${key}_keys`)
]);

const knownPlaceholders = new Set([
  "activity",
  "active_order_word",
  "active_orders",
  "alternative_gift",
  "amount",
  "ancestor",
  "ancestor_possessive",
  "attack_weapon",
  "auto_payment",
  "available_roles",
  "blocks",
  "blueprint_cost",
  "aunt_uncle",
  "aunt_uncle_possessive",
  "bounds",
  "cap",
  "shearing",
  "child",
  "child_possessive",
  "container",
  "container_theft_again_phrase",
  "container_theft_offense",
  "container_theft_time_word",
  "contract_cost",
  "cost",
  "cooldown_day_word",
  "cooldown_days",
  "count",
  "crop",
  "current_stage",
  "current_village",
  "currency",
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
  "deposited",
  "descendant",
  "descendant_possessive",
  "days_since_seen",
  "days_since_seen_phrase",
  "dimensions",
  "direction",
  "distance",
  "efficiency",
  "emerald_cost",
  "emeralds",
  "ex_partner",
  "ex_partner_possessive",
  "extended_relative",
  "extended_relative_possessive",
  "extra_cost",
  "filter",
  "fiance",
  "fiance_possessive",
  "follow_biome",
  "follow_distance",
  "gear_kind",
  "gift_item",
  "home_village",
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
  "issuer",
  "item",
  "item_or_items",
  "item_count",
  "item_id",
  "item_stack",
  "items",
  "late_partner",
  "late_partner_possessive",
  "level",
  "loot_table",
  "logs",
  "materials",
  "max",
  "max_order_count_word",
  "max_order_word",
  "max_orders",
  "maximum_wager",
  "mode",
  "mount",
  "new_time_remaining",
  "niece_nephew",
  "niece_nephew_possessive",
  "objective",
  "objective_complete",
  "objective_count",
  "objective_entity",
  "objective_block",
  "objective_block_id",
  "objective_memory",
  "objective_memory_id",
  "objective_gift_reaction",
  "objective_reputation",
  "objective_reputation_level",
  "objective_reputation_min",
  "objective_reputation_max",
  "objective_choice",
  "objective_choice_key",
  "objective_choice_value",
  "objective_fact",
  "objective_fact_id",
  "objective_fact_key",
  "objective_fact_scope",
  "objective_fact_value",
  "objective_id",
  "objective_item",
  "objective_item_id",
  "objective_progress",
  "objective_progress_count",
  "objective_radius",
  "objective_target_x",
  "objective_target_y",
  "objective_target_z",
  "objective_target_dimension",
  "objective_type",
  "offer_slot",
  "option",
  "order",
  "overflow_count",
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
  "plural",
  "placed",
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
  "quest_fact_counter",
  "quest_fact_key",
  "quest_fact_scope",
  "quest_fact_scope_key",
  "quest_fact_tag",
  "quest_fact_value",
  "quest_id",
  "quest_stage",
  "radius",
  "range",
  "reason",
  "reputation",
  "reputation_level",
  "refund_amount",
  "relative",
  "relative_possessive",
  "remaining",
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
  "role",
  "sibling",
  "sibling_possessive",
  "site",
  "skill",
  "study_cooldown",
  "spouse",
  "spouse_possessive",
  "state",
  "status_detail",
  "storage_radius",
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
  "structure",
  "target",
  "target_article",
  "target_kind",
  "target_name",
  "tool",
  "target_type",
  "target_dimension",
  "target_x",
  "target_z",
  "tested_villager",
  "theft_witness",
  "theft_witness_possessive",
  "time_remaining",
  "trade_count",
  "trade_cost",
  "trade_cost_count",
  "trade_cost_item",
  "trade_item",
  "trade_item_stack",
  "trade_items",
  "trade_offer_index",
  "trade_result",
  "trade_result_count",
  "trade_result_stack",
  "trade_word",
  "types",
  "vague_direction",
  "vertical",
  "victim",
  "victim_name",
  "victim_profession",
  "visited_target",
  "villager",
  "villager_losses",
  "villager_name",
  "villager_possessive",
  "villager_wins",
  "wait_day_word",
  "wait_days",
  "work_area",
  "witnessed_container_thefts",
  "x",
  "y",
  "z"
]);

const errors = [];
const warnings = [];
const questDefinitions = new Map();
const conversationDatapacks = new Map();
const socialProgressionDatapacks = new Map();
const campaignDatapacks = new Map();
const checkedCampaigns = new Set();
const dialogueTreeDefinitions = new Map();
const forcedDialogueDefinitions = new Map();
const dialogueMessageKeys = new Map();
const lootTableDefinitions = new Set();
const notificationTriggerDefinitions = new Set();
const forcedDialogueQuestModules = [];
const pendingQuestReferences = [];
const pendingQuestStageReferences = [];
const pendingDialogueTreeLinks = [];
const pendingForcedDialogueReferences = [];
const pendingDialogueMessageKeyReferences = [];
const pendingForcedDialogueMessageKeyReferences = [];
const pendingLootTableReferences = [];
const pendingNotificationTriggerReferences = [];
const dialogueIdScopes = {
  options: new Map(),
  lines: new Map(),
  messages: new Map(),
  openings: new Map(),
  closings: new Map(),
  pacify: new Map()
};

const questV2SchemaId = "villagerretaliation:quest/v2";
const resourceLocationPattern = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const questV2IdPattern = /^(?!__generated)(?!vr\$)[A-Za-z0-9_.:-]+$/;
const questV2Schema = await loadQuestV2Schema();
validateQuestV2Schema(questV2Schema);
const questV2RootKeys = questStructureKeys("root");
const questV2MetadataKeys = questStructureKeys("metadata");
const questV2MigrationKeys = questStructureKeys("migration");
const questV2TargetKeys = questStructureKeys("target");
const questV2ProviderKeys = questStructureKeys("provider");
const questV2ActiveStateKeys = questStructureKeys("active_state");
const questV2ExpirationKeys = questStructureKeys("expiration");
const questV2BranchKeys = questStructureKeys("branch");
const questV2AvailabilityKeys = questStructureKeys("availability");
const questV2LifecycleKeys = questStructureKeys("lifecycle");
const questV2LifecycleHookKeys = questStructureKeys("lifecycle_hook");
const questV2StageKeys = questStructureKeys("stage");
const questV2CompletionKeys = questStructureKeys("completion");
const questV2BonusKeys = questStructureKeys("bonus");
const questV2ObjectiveKeys = questStructureKeys("objective");
const questV2TrackerKeys = questStructureKeys("tracker");
const questV2DialogueSlotKeys = questStructureKeys("dialogue_slot");
const questV2SceneKeys = questStructureKeys("scene");
const questV2ResponseKeys = questStructureKeys("response");
const questV2TransitionKeys = questStructureKeys("transition");
const questV2EventKeys = questStructureKeys("event");
const questV2RewardsKeys = questStructureKeys("rewards");
const questV2UiKeys = questStructureKeys("ui");
const questV2LifecycleCoverageSlots = new Set(["offer", "reminder", "turn_in"]);

const cli = parseValidatorArgs(process.argv.slice(2));
await readValidatorFileLists(cli);
const questRegistryMetadata = await loadQuestRegistryMetadata();
validateQuestRegistryMetadata(questRegistryMetadata);
const skillTradeSchema = await loadSkillTradeSchema();
validateSkillTradeSchema(skillTradeSchema);
const questV2RegistryIndex = questRegistryIndex(questRegistryMetadata);
const conversationSchema = await parseJson(path.join(root, "tools/schemas/conversation-v1.schema.json"));
const socialProgressionSchema = await parseJson(path.join(root, "tools/schemas/social-progression-v1.schema.json"));
const campaignSchema = await parseJson(path.join(root, "tools/schemas/campaign-v1.schema.json"));
const scenarioSchema = await parseJson(path.join(root, "tools/schemas/campaign-scenario-v1.schema.json"));
const sceneSchema = await parseJson(path.join(root, "tools/schemas/scene-v1.schema.json"));

if (cli.help) {
  printValidatorHelp();
  process.exit(0);
}

if (cli.fileLists.length > 0 || cli.questFiles.length > 0 || cli.skillTradeFiles.length > 0 || cli.conversationFiles.length > 0 || cli.campaignFiles.length > 0 || cli.scenarioFiles.length > 0 || cli.socialProgressionFiles.length > 0) {
  if (cli.questFiles.length > 0) await validateQuestFiles(cli.questFiles);
  for (const file of cli.socialProgressionFiles) {
    const data = await parseJson(file);
    if (data !== undefined) await checkSocialProgressionFile(file, data);
  }
  if (cli.skillTradeFiles.length > 0) await validateSkillTradeFiles(cli.skillTradeFiles);
  if (cli.conversationFiles.length > 0) {
    if (cli.questFiles.length === 0) await indexBuiltInReferenceData();
    for (const file of cli.conversationFiles) {
      const data = await parseJson(file);
      if (data !== undefined) await checkConversationFile(file, data, true);
    }
  }
  if (cli.campaignFiles.length > 0) {
    if (cli.questFiles.length === 0 && cli.conversationFiles.length === 0) await indexBuiltInReferenceData();
    for (const file of cli.campaignFiles) {
      const data = await parseJson(file);
      if (data !== undefined) await checkCampaignFile(file, data);
    }
  }
  if (cli.scenarioFiles.length > 0) {
    if (cli.questFiles.length === 0 && cli.conversationFiles.length === 0 && cli.campaignFiles.length === 0) await indexBuiltInReferenceData();
    for (const file of cli.scenarioFiles) {
      const data = await parseJson(file);
      if (data !== undefined) await checkScenarioFile(file, data);
    }
  }
} else {
  await validateBuiltInData();
}

const contextRoots = new Map();
for (const file of [...cli.questFiles, ...cli.conversationFiles, ...cli.campaignFiles]) {
  const normalized = file.replaceAll("\\", "/");
  const index = normalized.lastIndexOf("/data/");
  if (index >= 0) {
    const directory = normalized.slice(0, index + 5);
    if (!contextRoots.has(directory)) contextRoots.set(directory, []);
    contextRoots.get(directory).push(file);
  }
}
if (!contextRoots.size) errors.push(...await validateContextDirectory());
else for (const [directory, files] of contextRoots) errors.push(...await validateContextDirectory(directory, files));
if (!contextRoots.size) errors.push(...await validateWordDirectory());
else for (const [directory, files] of contextRoots) errors.push(...await validateWordDirectory(directory, files));
validateCrossReferences();

if (errors.length > 0) process.exitCode = 1;
if (cli.json) {
  console.log(JSON.stringify({ errors, warnings }));
} else {
  for (const error of errors) console.error(formatIssue(error, "error"));
  if (errors.length === 0 && !cli.quiet) console.log("Built-in dialogue data validation passed.");
  for (const warning of warnings) console.warn(formatIssue(warning, "warning"));
}

function parseValidatorArgs(args) {
  const parsed = {
    help: false,
    quiet: false,
    json: false,
    fileLists: [],
    questFiles: [],
    skillTradeFiles: [],
    conversationFiles: [],
    campaignFiles: [],
    scenarioFiles: [],
    socialProgressionFiles: []
  };
  for (let index = 0; index < args.length; index++) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--quiet" || arg === "-q") {
      parsed.quiet = true;
    } else if (arg === "--json") {
      parsed.json = true;
    } else if (arg === "--file-list") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --file-list requires a JSON file path.");
      else parsed.fileLists.push(path.resolve(file));
    } else if (arg === "--quest" || arg === "--quest-file") {
      const file = args[++index];
      if (!file) {
        errors.push("tools/validate-dialogue-data.mjs: --quest requires a JSON file path.");
      } else {
        parsed.questFiles.push(path.resolve(file));
      }
    } else if (arg === "--skill-trade" || arg === "--skill-trade-file") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --skill-trade requires a JSON file path.");
      else parsed.skillTradeFiles.push(path.resolve(file));
    } else if (arg === "--conversation" || arg === "--conversation-file") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --conversation requires a JSON file path.");
      else parsed.conversationFiles.push(path.resolve(file));
    } else if (arg === "--campaign" || arg === "--campaign-file") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --campaign requires a JSON file path.");
      else parsed.campaignFiles.push(path.resolve(file));
    } else if (arg === "--social-progression") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --social-progression requires a JSON file path.");
      else parsed.socialProgressionFiles.push(path.resolve(file));
    } else if (arg === "--scenario" || arg === "--scenario-file") {
      const file = args[++index];
      if (!file) errors.push("tools/validate-dialogue-data.mjs: --scenario requires a JSON file path.");
      else parsed.scenarioFiles.push(path.resolve(file));
    } else if (arg.startsWith("--")) {
      errors.push(`tools/validate-dialogue-data.mjs: unsupported option "${arg}".`);
    } else {
      parsed.questFiles.push(path.resolve(arg));
    }
  }
  return parsed;
}

async function readValidatorFileLists(cli) {
  const fields = { quests: "questFiles", conversations: "conversationFiles", campaigns: "campaignFiles", scenarios: "scenarioFiles", social_progressions: "socialProgressionFiles" };
  for (const file of cli.fileLists) {
    const data = await parseJson(file);
    if (!data || typeof data !== "object" || Array.isArray(data) || Object.keys(data).some(key => !Object.hasOwn(fields, key))
        || Object.values(data).some(entries => !Array.isArray(entries) || entries.length > 4096
          || entries.some(entry => typeof entry !== "string" || !entry.trim()))) {
      errors.push(relative(file) + ": file list must contain quests, conversations, campaigns, scenarios, or social_progressions arrays of JSON file paths.");
      continue;
    }
    for (const [key, entries] of Object.entries(data)) {
      cli[fields[key]].push(...entries.map(entry => path.resolve(path.dirname(file), entry)));
    }
  }
  for (const field of Object.values(fields)) cli[field] = [...new Set(cli[field])];
}

function printValidatorHelp() {
  console.log([
    "Usage: node tools/validate-dialogue-data.mjs [--quiet] [--json] [--quest path/to/quest.json ...] [--skill-trade path/to/file.json ...] [--conversation path/to/file.json ...] [--campaign path/to/campaign.json ...] [--scenario path/to/scenario.json ...] [--social-progression path/to/profile.json ...]",
    "",
    "Without file arguments, validates the checked-in built-in datapack dialogue and quest data.",
    "With --quest, --skill-trade, --conversation, --campaign, --scenario, or --social-progression, validates the supplied resources and their authored references.",
    "--file-list accepts JSON with quests, conversations, campaigns, scenarios, and social_progressions arrays. Paths are relative to that file.",
    "--json emits errors and warnings as JSON."
  ].join("\n"));
}

async function validateBuiltInData() {
  for (const [kind, relativeRoot] of Object.entries(roots)) {
    for (const file of await jsonFiles(path.join(root, relativeRoot))) {
      const data = await parseJson(file);
      if (data === undefined) {
        continue;
      }
      if (kind === "scenarios") {
        await checkScenarioFile(file, data);
        continue;
      }
      if (kind === "campaigns") {
        if (path.basename(file) === "campaign.json") await checkCampaignFile(file, data);
        continue;
      }
      if (kind === "quests") {
        if (isQuestModuleV2(data)) {
          checkEquipmentPredicateFlags(file, data);
          indexQuestResource(file, data);
          checkQuestResource(file, data);
        } else if (stringValue(data?.schema) === "villagerretaliation:quest_locale/v1") {
          indexQuestBundleLocale(file, data);
        } else if (stringValue(data?.schema) === "villagerretaliation:quest_reward/v1") {
          indexBundledQuestReward(file, data);
        } else if (stringValue(data?.schema) === "villagerretaliation:social_progression/v1") {
          await checkSocialProgressionFile(file, data);
        } else if (stringValue(data?.schema) === "villagerretaliation:conversation/v1") {
          await checkConversationFile(file, data);
        } else if (!isQuestBundleCompanion(data)) {
          errors.push(`${relative(file)}: unsupported resource in the public quests bundle layout.`);
        }
        continue;
      }
      checkPlaceholders(file, data);
      checkEquipmentPredicateFlags(file, data);
      if (kind === "dialogue") {
        checkDialogue(file, data);
      } else if (kind === "dialogueTrees") {
        indexDialogueTree(file, data);
        checkDialogueTree(file, data);
      } else if (kind === "forcedDialogue") {
        indexForcedDialogue(file, data);
        checkForcedDialogue(file, data);
      } else if (kind === "lootTables") {
        indexLootTable(file);
      } else if (kind === "notifications") {
        indexNotifications(file, data);
      } else if (kind === "skillTrades") {
        checkSkillTrades(file, data);
      }
    }
  }
}

async function validateQuestFiles(files) {
  await indexBuiltInReferenceData();
  for (const file of files) {
    if (path.basename(file).toLowerCase() === "quest.json") {
      const localeFile = path.join(path.dirname(file), "locales", "en_us.json");
      try {
        const locale = JSON.parse(stripBom(await readFile(localeFile, "utf8")));
        indexQuestBundleLocale(localeFile, locale);
      } catch (error) {
        errors.push(`${relative(file)}: bundle quest requires readable locales/en_us.json (${error.message}).`);
      }
    }
    const data = await parseJson(file);
    if (data === undefined) {
      continue;
    }
    if (!isQuestModuleV2(data)) {
      checkPlaceholders(file, data);
    }
    checkEquipmentPredicateFlags(file, data);
    indexQuestResource(file, data);
    checkQuestResource(file, data);
  }
}

function conversationLocale(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) return null;
  const messages = data.messages && typeof data.messages === "object" && !Array.isArray(data.messages) ? data.messages : data;
  const entries = Object.entries(messages).filter(([key]) => messages !== data || key !== "schema");
  if (entries.some(([key, value]) => !key.trim() || value === null)) return null;
  return Object.fromEntries(entries);
}

async function conversationDatapack(dataRoot) {
  if (!conversationDatapacks.has(dataRoot)) {
    const resources = [];
    for (const file of await jsonFiles(dataRoot)) {
      const local = path.relative(dataRoot, file).replaceAll(path.sep, "/");
      if (!/^[^/]+\/(quests|campaigns)\//.test(local)) continue;
      const data = await parseJson(file);
      if (data && typeof data === "object" && !Array.isArray(data)) resources.push({ file, data });
    }
    conversationDatapacks.set(dataRoot, resources);
  }
  return conversationDatapacks.get(dataRoot);
}

async function socialProgressionCatalog(dataRoot) {
  if (!socialProgressionDatapacks.has(dataRoot)) {
    const resources = [...await conversationDatapack(dataRoot)];
    const indexed = new Set(resources.map(resource => path.resolve(resource.file)));
    for (const file of await jsonFiles(dataRoot)) {
      if (!socialProgressionPath(file) || indexed.has(path.resolve(file))) continue;
      const data = await parseJson(file);
      if (data !== undefined) resources.push({ file, data });
    }
    const report = collectSocialProgressions(resources, socialProgressionSchema);
    for (const issue of report.issues) errors.push(relative(issue.file) + ": " + (issue.pointer || "/") + ": " + issue.message + ".");
    socialProgressionDatapacks.set(dataRoot, report.progressions);
  }
  return socialProgressionDatapacks.get(dataRoot);
}

async function checkSocialProgressionFile(file, data) {
  const location = socialProgressionPath(file);
  if (location) {
    await socialProgressionCatalog(location.dataRoot);
    return;
  }
  for (const issue of validateSocialProgression(data, socialProgressionSchema))
    errors.push(relative(file) + ": " + (issue.pointer || "/") + ": " + issue.message + ".");
  errors.push(relative(file) + ": social progression belongs to quests/_shared/social_progression.");
}

async function checkConversationFile(file, data, indexReferences = false) {
  if (!conversationSchema) return;
  const structuralIssues = validateConversationSchema(data, conversationSchema);
  for (const issue of structuralIssues) errors.push(`${relative(file)}: ${issue.pointer || "/"}: ${issue.message}.`);
  if (structuralIssues.length) return;
  const normalized = path.resolve(file).replaceAll(path.sep, "/");
  const layout = normalized.match(/^(.*\/data\/([a-z0-9_.-]+))\/quests\/(_shared|[a-z0-9_.-]+\/[a-z0-9_.-]+)\/conversations\/[^/]+\.json$/);
  if (!layout) {
    errors.push(`${relative(file)}: conversation must belong to a public quest bundle or quests/_shared/conversations.`);
    return;
  }
  const namespaceRoot = layout[1];
  const owner = path.join(namespaceRoot, "quests", layout[3]);
  const shared = layout[3] === "_shared";
  const ownerQuestFile = path.join(owner, "quest.json");
  const ownerQuest = shared ? null : await parseJson(ownerQuestFile);
  if (!shared && !isQuestModuleV2(ownerQuest)) {
    errors.push(`${relative(file)}: conversation requires its owning quest.json.`);
    return;
  }
  const context = { prefix: ownerQuest?.localization_prefix ?? "", quest: ownerQuest?.id, messages: Object.create(null) };
  const dataRoot = path.dirname(namespaceRoot);
  context.progressions = await socialProgressionCatalog(dataRoot);
  const resources = await conversationDatapack(dataRoot);
  for (const { file: resource, data: definition } of resources) {
    const local = path.relative(dataRoot, resource).replaceAll(path.sep, "/");
    if (definition.schema === "villagerretaliation:conversation/v1" && definition.id === data.id && path.resolve(resource) !== path.resolve(file)) {
      errors.push(`${relative(file)}: /id: duplicate conversation id also defined in ${relative(resource)}.`);
    }
    if (local.endsWith("/quests/_shared/locales/en_us.json")) {
      const messages = conversationLocale(definition);
      if (messages === null) errors.push(`${relative(resource)}: invalid shared English locale.`);
      else Object.assign(context.messages, messages);
    }
    if (definition.schema === "villagerretaliation:campaign/v1" && definition.id === data.campaign) {
      if (context.campaign) errors.push(`${relative(resource)}: duplicate campaign id ${definition.id}.`);
      else context.campaign = definition;
    }
    if (indexReferences && isQuestModuleV2(definition)) {
      const previous = questDefinitions.get(definition.id);
      if (!previous || previous.file !== relative(resource)) indexQuestResource(resource, definition);
    }
    if (indexReferences && definition.schema === "villagerretaliation:quest_locale/v1") indexQuestBundleLocale(resource, definition);
    if (indexReferences && definition.schema === "villagerretaliation:quest_reward/v1") indexBundledQuestReward(resource, definition);
  }
  const localeFile = path.join(owner, "locales", "en_us.json");
  const locale = resources.find(entry => path.resolve(entry.file) === path.resolve(localeFile))?.data;
  const messages = conversationLocale(locale);
  if ((!shared || locale !== undefined) && messages === null) {
    errors.push(`${relative(file)}: conversation requires readable locales/en_us.json.`);
    return;
  }
  Object.assign(context.messages, messages);
  if (data.campaign && !context.campaign) errors.push(`${relative(file)}: /campaign: campaign content is unavailable: ${data.campaign}.`);
  context.campaigns = new Map(resources.filter(entry => entry.data?.schema === "villagerretaliation:campaign/v1").map(entry => [entry.data.id, entry.data]));
  context.quests = new Map(resources.filter(entry => isQuestModuleV2(entry.data)).map(entry => [entry.data.id, entry.data]));
  const issues = validateConversation(data, conversationSchema, context);
  for (const issue of issues) errors.push(`${relative(file)}: ${issue.pointer || "/"}: ${issue.message}.`);
  if (issues.length) return;
  if (!data.id.startsWith(layout[2] + ":")) errors.push(`${relative(file)}: /id: conversation id must use the bundle namespace.`);
  const questId = data.quest ?? context.quest ?? "";
  if (questId) pendingQuestReferences.push({ file, location: "/quest", id: questId, reason: "conversation quest context" });
  const checkEffects = (effects, pointer) => {
    for (const [index, effect] of (effects ?? []).entries()) {
      const actionPointer = `${pointer}/${index}/action`;
      const action = materializeConversationAction(effect.action, context);
      checkQuestV2Actions(file, [action], actionPointer, actionPointer, questId, { singleAction: true });
      const type = questV2RegistryIndex.actions.canonical.get(action.type ?? action.action);
      if (["campaign_fact", "campaign_outcome", "campaign_reward", "campaign_standing", "campaign_payment"].includes(type) && !action.quest && !action.quest_id && !questId) {
        errors.push(`${relative(file)}: ${actionPointer}/quest: campaign effect requires an owning quest.`);
      }
      if (type === "start_scene") {
        if (!firstString(action.operation_id, action.scene_operation_id).trim()) {
          errors.push(`${relative(file)}: ${actionPointer}/operation_id: scene action requires a stable operation id.`);
        }
        const authoredScene = firstString(action.scene_id, action.scene, action.start_scene);
        const scene = authoredScene.includes(":") ? authoredScene : `${layout[2]}:${authoredScene}`;
        const target = resources.find(entry => entry.data.schema === "villagerretaliation:scene/v1" && entry.data.id === scene);
        if (!target) errors.push(`${relative(file)}: ${actionPointer}/scene_id: unknown scene ${scene}.`);
        else {
          const sceneOwner = path.dirname(path.dirname(target.file));
          const publicScene = path.basename(sceneOwner) === "_shared";
          if (!publicScene && path.resolve(sceneOwner) !== path.resolve(owner)) {
            errors.push(`${relative(file)}: ${actionPointer}/scene_id: private scene belongs to another quest bundle.`);
          }
        }
      }
    }
  };
  for (const [index, topic] of data.topics.entries()) {
    const pointer = `/topics/${index}/conditions`;
    checkQuestV2Conditions(file, topic.conditions, pointer, pointer, questId, "conversation topic");
  }
  for (const [index, node] of data.nodes.entries()) {
    checkEffects(node.effects, `/nodes/${index}/effects`);
    for (const [responseIndex, response] of (node.responses ?? []).entries()) {
      const pointer = `/nodes/${index}/responses/${responseIndex}`;
      checkQuestV2Conditions(file, response.conditions, `${pointer}/conditions`, `${pointer}/conditions`, questId, "conversation response");
      checkEffects(response.effects, `${pointer}/effects`);
    }
  }
}

async function checkScenarioFile(file, data) {
  if (!scenarioSchema) return;
  const issues = validateCampaignScenario(data, scenarioSchema);
  for (const issue of issues) errors.push(relative(file) + ": " + (issue.pointer || "/") + ": " + issue.message + ".");
  if (issues.length) return;
  const location = scenarioPath(file);
  if (!location) {
    errors.push(relative(file) + ": scenario must belong to data/<namespace>/campaign_scenarios/<name>.json.");
    return;
  }
  if (data.id !== location.id) errors.push(relative(file) + ": /id: scenario ID must match its resource path.");
  const resources = await conversationDatapack(location.dataRoot);
  const campaign = resources.find(resource => resource.data.schema === "villagerretaliation:campaign/v1" && resource.data.id === data.campaign);
  const before = errors.length;
  if (campaign) await checkCampaignFile(campaign.file, campaign.data);
  if (errors.length !== before) return;
  for (const issue of validateCampaignScenario(data, scenarioSchema, scenarioContext(resources))) {
    errors.push(relative(file) + ": " + (issue.pointer || "/") + ": " + issue.message + ".");
  }
}

async function checkCampaignFile(file, data) {
  const absolute = path.resolve(file);
  if (checkedCampaigns.has(absolute)) return;
  checkedCampaigns.add(absolute);
  const dataRoot = campaignDataRoot(file);
  let resources = [];
  if (dataRoot) {
    if (!campaignDatapacks.has(dataRoot)) {
      const found = [];
      for (const resource of await jsonFiles(dataRoot)) {
        const local = path.relative(dataRoot, resource).replaceAll(path.sep, "/");
        if (!/^[^/]+\/(quests|campaigns)\//.test(local)) continue;
        const definition = await parseJson(resource);
        if (definition !== undefined) found.push({ file: resource, data: definition });
      }
      campaignDatapacks.set(dataRoot, found);
    }
    resources = campaignDatapacks.get(dataRoot);
  }
  if (dataRoot) await socialProgressionCatalog(dataRoot);
  if (!campaignSchema || !questV2Schema || !sceneSchema) return;
  const report = validateCampaignPack(file, data, resources, { campaign: campaignSchema, quest: questV2Schema, scene: sceneSchema });
  for (const problem of report.issues) errors.push(relative(problem.file) + ": " + (problem.pointer || "/") + ": " + problem.message + ".");
  for (const resource of resources) {
    const definition = resource.data;
    if (isQuestModuleV2(definition)) {
      const previous = questDefinitions.get(definition.id);
      if (!previous || previous.file !== relative(resource.file)) indexQuestResource(resource.file, definition);
    }
    if (definition?.schema === "villagerretaliation:quest_locale/v1") indexQuestBundleLocale(resource.file, definition);
    if (definition?.schema === "villagerretaliation:quest_reward/v1") indexBundledQuestReward(resource.file, definition);
  }
  for (const resource of report.quests) {
    checkEquipmentPredicateFlags(resource.file, resource.data);
    checkQuestResource(resource.file, resource.data);
  }
  for (const resource of report.conversations) await checkConversationFile(resource.file, resource.data, true);
}

async function validateSkillTradeFiles(files) {
  for (const file of files) {
    const data = await parseJson(file);
    if (data !== undefined) checkSkillTrades(file, data);
  }
}

function checkSkillTrades(file, data) {
  const resource = relative(file);
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${resource}: root must be an object.`);
    return;
  }
  if (!Array.isArray(data.entries)) {
    errors.push(`${resource}: entries must be an array.`);
    return;
  }
  const ids = new Set();
  const resourceId = new RegExp(skillTradeSchema?.$defs?.resource?.pattern || "^[a-z0-9_.-]+:[a-z0-9_./-]+$");
  const reputations = new Set(skillTradeSchema?.$defs?.request?.properties?.min_reputation?.enum || []);
  const supportedSkills = new Set(skillTradeSchema?.$defs?.skill?.enum || []);
  const ranks = skillTradeSchema?.$defs?.rank?.enum || [];
  const pools = new Set(skillTradeSchema?.$defs?.entry?.properties?.pool?.enum || []);
  for (const [index, entry] of data.entries.entries()) {
    const at = `${resource}: entries[${index}]`;
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      errors.push(`${at} must be an object.`);
      continue;
    }
    if (!resourceId.test(String(entry.id || ""))) errors.push(`${at}.id must be a namespaced resource location.`);
    else if (ids.has(entry.id)) errors.push(`${at}.id duplicates ${entry.id}.`);
    else ids.add(entry.id);
    if (entry.remove === true) continue;
    const skills = Array.isArray(entry.skills) ? entry.skills : entry.skill ? [entry.skill] : [];
    if (!skills.length || skills.some((id) => !resourceId.test(String(id)) || (supportedSkills.size && !supportedSkills.has(id)))) errors.push(`${at}.skills must contain supported namespaced skill ids.`);
    const professions = Array.isArray(entry.professions) ? entry.professions : entry.profession ? [entry.profession] : [];
    if (professions.some((id) => !resourceId.test(String(id)))) errors.push(`${at}.professions contains an invalid id.`);
    const resultItems = Array.isArray(entry.result?.items) ? entry.result.items : entry.result?.item ? [entry.result.item] : [];
    if (!resultItems.length || resultItems.some((id) => !resourceId.test(String(id)))) errors.push(`${at}.result needs a valid item or items list.`);
    const bounded = (owner, key, min, max) => {
      if (owner?.[key] === undefined) return;
      if (!Number.isInteger(owner[key]) || owner[key] < min || owner[key] > max) errors.push(`${at}.${key} must be an integer from ${min} to ${max}.`);
    };
    bounded(entry, "villager_level", 1, 5); bounded(entry, "weight", 1, 10000); bounded(entry, "xp", 0, 10000);
    bounded(entry.result, "count", 1, 64); bounded(entry.cost, "count", 1, 64);
    if (entry.chance !== undefined && (!Number.isFinite(entry.chance) || entry.chance < 0 || entry.chance > 1)) errors.push(`${at}.chance must be from 0 to 1.`);
    if (entry.price_multiplier !== undefined && (!Number.isFinite(entry.price_multiplier) || entry.price_multiplier < 0 || entry.price_multiplier > 1)) errors.push(`${at}.price_multiplier must be from 0 to 1.`);
    if (entry.min_rank !== undefined && !ranks.includes(entry.min_rank)) errors.push(`${at}.min_rank is not supported.`);
    if (entry.max_rank !== undefined && !ranks.includes(entry.max_rank)) errors.push(`${at}.max_rank is not supported.`);
    if (entry.min_rank && entry.max_rank && ranks.indexOf(entry.max_rank) < ranks.indexOf(entry.min_rank)) errors.push(`${at}.max_rank must not be below min_rank.`);
    if (entry.pool !== undefined && !pools.has(entry.pool)) errors.push(`${at}.pool is not supported.`);
    if (entry.request !== undefined) {
      const request = entry.request;
      if (!request || typeof request !== "object" || Array.isArray(request)) {
        errors.push(`${at}.request must be an object.`);
        continue;
      }
      if (request.targetable !== undefined && typeof request.targetable !== "boolean") errors.push(`${at}.request.targetable must be boolean.`);
      if (request.min_reputation !== undefined && !reputations.has(request.min_reputation)) errors.push(`${at}.request.min_reputation is not a supported reputation level.`);
      bounded(request, "wait_days", 0, 3650); bounded(request, "cooldown_days", 0, 3650);
      if (request.extra_cost && (!resourceId.test(String(request.extra_cost.item || "")) || !Number.isInteger(request.extra_cost.count) || request.extra_cost.count < 1 || request.extra_cost.count > 64)) errors.push(`${at}.request.extra_cost needs an item and count from 1 to 64.`);
    }
  }
}

async function indexBuiltInReferenceData() {
  for (const [kind, relativeRoot] of Object.entries(roots)) {
    if (kind === "quests") {
      continue;
    }
    for (const file of await jsonFiles(path.join(root, relativeRoot))) {
      const data = await parseJson(file);
      if (data === undefined) {
        continue;
      }
      checkPlaceholders(file, data);
      checkEquipmentPredicateFlags(file, data);
      if (kind === "dialogue") {
        checkDialogue(file, data);
      } else if (kind === "dialogueTrees") {
        indexDialogueTree(file, data);
      } else if (kind === "forcedDialogue") {
        indexForcedDialogueDefinitionsOnly(file, data);
      } else if (kind === "lootTables") {
        indexLootTable(file);
      } else if (kind === "notifications") {
        indexNotifications(file, data);
      }
    }
  }
}

function indexForcedDialogueDefinitionsOnly(file, data) {
  for (const entry of entriesFor(data)) {
    const entryId = stringValue(entry?.id);
    const trigger = normalizedString(entry?.trigger || entry?.event);
    if (entryId) {
      registerForcedDialogueDefinition(file, entryId, trigger, true);
    }
    registerForcedDialogueDefinition(file, forcedDialogueSourceIdForFile(file), trigger, false);
  }
}

function formatIssue(issue, fallbackSeverity) {
  if (!issue || typeof issue !== "object" || Array.isArray(issue)) {
    return `[${fallbackSeverity.toUpperCase()}] ${issue}`;
  }
  const severity = String(issue.severity || fallbackSeverity).toUpperCase();
  const parts = [
    `[${severity}]`,
    `resource=${issue.resource || "unknown"}`,
    `path=${issue.path || "root"}`,
    `pointer=${issue.pointer || ""}`,
    issue.message || ""
  ];
  if (issue.suggestion) {
    parts.push(`suggestion=${issue.suggestion}`);
  }
  return parts.filter(Boolean).join(" ");
}

async function loadQuestRegistryMetadata() {
  const metadataPath = path.join(root, "tools/schemas/quest-registry-metadata.json");
  try {
    return JSON.parse(await readFile(metadataPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(metadataPath)}: could not read quest registry metadata: ${error.message}`);
    return null;
  }
}

function validateQuestRegistryMetadata(metadata) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    errors.push("tools/schemas/quest-registry-metadata.json: root must be an object.");
    return;
  }
  if (![1, 2].includes(metadata.format_version)) {
    errors.push("tools/schemas/quest-registry-metadata.json: format_version must be 1 or 2.");
  }
  const registries = metadata.registries;
  if (!registries || typeof registries !== "object" || Array.isArray(registries)) {
    errors.push("tools/schemas/quest-registry-metadata.json: registries must be an object.");
    return;
  }
  for (const registry of ["conditions", "actions", "objectives", "triggers", "providers"]) {
    if (!Array.isArray(registries[registry]) || registries[registry].length === 0) {
      errors.push(`tools/schemas/quest-registry-metadata.json: registries.${registry} must be a non-empty array.`);
    }
  }
  if (metadata.format_version >= 2) {
    for (const registry of ["actor_types", "scene_steps", "encounter_templates", "public_quest_providers", "public_quest_objectives", "public_quest_actions", "public_quest_conditions", "public_quest_triggers"]) {
      if (!Array.isArray(registries[registry]) || registries[registry].length === 0) {
        errors.push(`tools/schemas/quest-registry-metadata.json: registries.${registry} must be a non-empty array in format v2.`);
      }
    }
  }
  const fragments = metadata.schema_fragments;
  if (!fragments || typeof fragments !== "object" || Array.isArray(fragments)) {
    errors.push("tools/schemas/quest-registry-metadata.json: schema_fragments must be an object.");
    return;
  }
  for (const fragment of ["condition_type", "action_type", "objective_type", "trigger_event", "provider_type"]) {
    if (!fragments[fragment] || !Array.isArray(fragments[fragment].enum) || fragments[fragment].enum.length === 0) {
      errors.push(`tools/schemas/quest-registry-metadata.json: schema_fragments.${fragment}.enum must be a non-empty array.`);
    }
  }
}

async function loadQuestV2Schema() {
  const schemaPath = path.join(root, "tools/schemas/quest-v2.schema.json");
  try {
    return JSON.parse(await readFile(schemaPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(schemaPath)}: could not read quest module v2 schema: ${error.message}`);
    return null;
  }
}

async function loadSkillTradeSchema() {
  const schemaPath = path.join(root, "tools/schemas/skill-trades.schema.json");
  try {
    return JSON.parse(await readFile(schemaPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(schemaPath)}: could not read skill-trade schema: ${error.message}`);
    return null;
  }
}

function validateSkillTradeSchema(schema) {
  const schemaPath = "tools/schemas/skill-trades.schema.json";
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    errors.push(`${schemaPath}: root must be an object.`);
    return;
  }
  if (schema.$schema !== "https://json-schema.org/draft/2020-12/schema") errors.push(`${schemaPath}: $schema must be draft 2020-12.`);
  if (!schema?.$defs?.entry || !schema?.$defs?.request || !schema?.$defs?.resource?.pattern) {
    errors.push(`${schemaPath}: entry, request, and resource definitions are required.`);
  }
}

function validateQuestV2Schema(schema) {
  const schemaPath = "tools/schemas/quest-v2.schema.json";
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    errors.push(`${schemaPath}: root must be an object.`);
    return;
  }
  if (schema.$schema !== "https://json-schema.org/draft/2020-12/schema") {
    errors.push(`${schemaPath}: $schema must be draft 2020-12.`);
  }
  if (schema?.properties?.schema?.const !== "villagerretaliation:quest/v2") {
    errors.push(`${schemaPath}: properties.schema.const must be villagerretaliation:quest/v2.`);
  }
  const defs = schema.$defs;
  if (!defs || typeof defs !== "object" || Array.isArray(defs)) {
    errors.push(`${schemaPath}: $defs must be an object.`);
    return;
  }
  for (const [definition, property] of [
    ["provider", "type"],
    ["objective", "type"],
    ["event", "event"],
    ["condition", "type"],
    ["action", "type"]
  ]) {
    const enumValues = defs?.[definition]?.properties?.[property]?.enum;
    if (!Array.isArray(enumValues) || enumValues.length === 0) {
      errors.push(`${schemaPath}: $defs.${definition}.properties.${property}.enum must be a non-empty array.`);
    }
  }
}

async function jsonFiles(directory) {
  const files = [];
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch (error) {
    if (error?.code === "ENOENT") {
      return files;
    }
    throw error;
  }
  for (const entry of entries) {
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

function dialogueTreeIdForFile(file, data) {
  return stringValue(data?.id) || dialogueTreeFallbackId(file);
}

function dialogueTreeDefaultQuestId(file, data) {
  const metadataQuest = stringValue(metadataObject(data).quest);
  if (metadataQuest) {
    return metadataQuest;
  }
  return isQuestDialogueTreeFile(file) ? dialogueTreeIdForFile(file, data) : "";
}

function resourceIdForFile(file, relativeRoot) {
  const base = path.join(root, relativeRoot);
  let relativePath = path.relative(base, file).replaceAll(path.sep, "/");
  if (!relativePath.endsWith(".json")) {
    return "";
  }
  relativePath = relativePath.slice(0, -".json".length);
  return relativePath ? `villagerretaliation:${relativePath}` : "";
}

function dialogueTreeFallbackId(file) {
  const questModule = dialogueTreeQuestModule(file);
  if (questModule?.questId) {
    return questModule.questId;
  }
  return resourceIdForFile(file, roots.dialogueTrees);
}

function isQuestDialogueTreeFile(file) {
  return dialogueTreeQuestModule(file) != null;
}

function dialogueTreeQuestModule(file) {
  const relativePath = path.relative(path.join(root, roots.dialogueTrees), file).replaceAll(path.sep, "/");
  const parts = relativePath.split("/");
  if (parts.length < 3 || parts[0] !== "quests" || !parts.at(-1).endsWith(".json")) {
    return null;
  }
  const questline = parts[1];
  const questName = parts.at(-1).slice(0, -".json".length);
  return {
    questline,
    questId: questName ? `villagerretaliation:${questName}` : ""
  };
}

function checkDialogue(file, data) {
  checkDialogueMetadata(file, data, "root");
  const sections = dialogueSectionsFor(file, data);
  checkDialogueIds(file, sections.options, "dialogue option", dialogueIdScopes.options);
  checkDialogueIds(file, sections.lines, "dialogue line", dialogueIdScopes.lines);
  checkDialogueIds(file, sections.messages, "dialogue message", dialogueIdScopes.messages);
  indexDialogueMessageKeys(file, sections.messages);
  checkDialogueIds(file, sections.openings, "opening", dialogueIdScopes.openings);
  checkDialogueIds(file, sections.closings, "closing", dialogueIdScopes.closings);
  checkDialogueIds(file, sections.pacify, "pacify line", dialogueIdScopes.pacify);

  for (const [section, entries] of Object.entries(sections)) {
    for (const [index, entry] of entries.entries()) {
      checkDialogueMetadata(file, entry, `${section}[${index}]`);
      checkDialogueCamera(file, entry, `${section}[${index}]`);
      for (const [variantIndex, variant] of (Array.isArray(entry.variants ?? entry.lines) ? entry.variants ?? entry.lines : []).entries())
        checkDialogueCamera(file, variant, `${section}[${index}].variants[${variantIndex}]`);
      checkOptionalInteger(file, entry, `${section}[${index}]`, "weight", { min: 0 });
    }
  }

  for (const [index, option] of sections.options.entries()) {
    checkDialogueOptionNetworkBounds(
      file,
      `options[${index}]`,
      stringValue(option?.id),
      stringValue(option?.label));
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
    checkResourceIdValues(file, line, `lines[${index}]`, ["retaliation_target_entity_types", "retaliation_target_entities"], "retaliation target entity id");
    checkResourceIdValues(file, line, `lines[${index}]`, ["story_structure", "story_structures"], "story structure id");
    checkResourceIdValues(file, line, `lines[${index}]`, ["story_biome", "story_biomes"], "story biome id");
    checkConditions(file, line, `lines[${index}]`);
  }
}

function checkDialogueOptionNetworkBounds(file, location, id, label) {
  if (id && id.length > dialogueOptionIdMaxLength) {
    errors.push(`${relative(file)}: ${location}.id exceeds the ${dialogueOptionIdMaxLength}-character network limit.`);
  }
  if (label.length > dialogueOptionLabelMaxLength) {
    errors.push(`${relative(file)}: ${location}.label exceeds the ${dialogueOptionLabelMaxLength}-character network limit.`);
  }
}

function indexDialogueMessageKeys(file, messages) {
  for (const [index, message] of messages.entries()) {
    const key = stringValue(message?.key);
    if (!key) {
      continue;
    }
    if (!dialogueMessageKeys.has(key)) {
      dialogueMessageKeys.set(key, `${relative(file)}: messages[${index}]`);
    }
  }
}

function checkEquipmentPredicateFlags(file, value, location = "root") {
  if (Array.isArray(value)) {
    value.forEach((child, index) => checkEquipmentPredicateFlags(file, child, `${location}[${index}]`));
    return;
  }
  if (!value || typeof value !== "object") {
    return;
  }

  checkEquipmentPredicateSubjectFlags(file, value, location, "villager");
  checkEquipmentPredicateSubjectFlags(file, value, location, "witness");

  for (const [key, child] of Object.entries(value)) {
    if (child && typeof child === "object") {
      checkEquipmentPredicateFlags(file, child, `${location}.${key}`);
    }
  }
}

function checkEquipmentPredicateSubjectFlags(file, entry, location, subject) {
  const armedKeys = [`requires_${subject}_armed`, `${subject}_armed`];
  const unarmedKeys = [`requires_${subject}_unarmed`, `${subject}_unarmed`];
  for (const key of [...armedKeys, ...unarmedKeys]) {
    checkOptionalBoolean(file, entry, location, key);
  }

  const requiresArmed = armedKeys.some((key) => entry[key] === true);
  const requiresUnarmed = unarmedKeys.some((key) => entry[key] === true);
  if (requiresArmed && requiresUnarmed) {
    errors.push(`${relative(file)}: ${location} requires ${subject} to be both armed and unarmed.`);
  }
}

function collectQuestMessageKeyReferences(file, value, location = "root") {
  if (Array.isArray(value)) {
    value.forEach((child, index) => collectQuestMessageKeyReferences(file, child, `${location}[${index}]`));
    return;
  }
  if (!value || typeof value !== "object") {
    return;
  }

  for (const [key, child] of Object.entries(value)) {
    const childLocation = `${location}.${key}`;
    if (questTextReferenceKeys.has(key)) {
      collectQuestMessageKeyReference(file, child, childLocation);
    }
    if (child && typeof child === "object") {
      collectQuestMessageKeyReferences(file, child, childLocation);
    }
  }
}

function collectQuestMessageKeyReference(file, value, location) {
  if (typeof value === "string") {
    const key = value.trim();
    if (key) {
      pendingDialogueMessageKeyReferences.push({ file, location, key });
    }
    return;
  }
  if (!Array.isArray(value)) {
    return;
  }
  value.forEach((child, index) => {
    if (typeof child !== "string") {
      return;
    }
    const key = child.trim();
    if (key) {
      pendingDialogueMessageKeyReferences.push({ file, location: `${location}[${index}]`, key });
    }
  });
}

function isLocalizedReference(value) {
  return value && typeof value === "object" && !Array.isArray(value)
    && Object.keys(value).length === 1 && typeof value.key === "string" && value.key.trim();
}

function indexQuestBundleLocale(file, data) {
  if (!data.messages || typeof data.messages !== "object" || Array.isArray(data.messages)) {
    errors.push(`${relative(file)}: quest locale messages must be an object.`);
    return;
  }
  for (const key of Object.keys(data.messages)) {
    if (!key.trim()) errors.push(`${relative(file)}: quest locale contains a blank message id.`);
    else if (!dialogueMessageKeys.has(key)) dialogueMessageKeys.set(key, relative(file));
  }
}

function indexBundledQuestReward(file, data) {
  const id = stringValue(data.id);
  if (!isResourceLocation(id)) errors.push(`${relative(file)}: bundled reward requires a namespaced stable id.`);
  else lootTableDefinitions.add(id);
  if (!data.table || typeof data.table !== "object" || Array.isArray(data.table)) {
    errors.push(`${relative(file)}: bundled reward table must be an object.`);
  }
}

function isQuestBundleCompanion(data) {
  return new Set([
    "villagerretaliation:scene/v1",
    "villagerretaliation:encounter/v1",
    "villagerretaliation:quest_pool/v1"
  ]).has(stringValue(data?.schema));
}

function collectQuestLocalizedReferences(file, value, prefix, location = "root") {
  if (Array.isArray(value)) {
    value.forEach((child, index) => collectQuestLocalizedReferences(file, child, prefix, `${location}[${index}]`));
    return;
  }
  if (!value || typeof value !== "object") return;
  if (isLocalizedReference(value)) {
    const raw = value.key.trim();
    const key = raw.startsWith("#") ? prefix + "." + raw.slice(1) : raw;
    pendingDialogueMessageKeyReferences.push({ file, location, key });
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    collectQuestLocalizedReferences(file, child, prefix, `${location}.${key}`);
  }
}

function indexQuestResource(file, data) {
  if (data?.schema === questV3Format.V3) data = { ...questV3Format.runtime(data), schema: questV3Format.V2 };
  if (isQuestModuleV2(data)) {
    indexQuestV2(file, data);
  }
}

function checkQuestResource(file, data) {
  if (data?.schema === questV3Format.V3) {
    for (const issue of validateContentSchema(data, questV3Schema)) errors.push(`${relative(file)}${issue.pointer}: ${issue.message}`);
    data = { ...questV3Format.runtime(data), schema: questV3Format.V2 };
  }
  if (isQuestModuleV2(data)) {
    checkQuestV2(file, data);
  } else {
    questV2Error(file, "/schema", "schema", "quest schema must be an explicit V2 or V3 identifier.", "Use villagerretaliation:quest/v3 for new quests.");
  }
}

function isQuestModuleV2(data) {
  return data
    && typeof data === "object"
    && !Array.isArray(data)
    && [questV2SchemaId, questV3Format.V3].includes(stringValue(data.schema));
}

function indexQuestV2(file, data) {
  const questId = stringValue(data?.id) || resourceIdForFile(file, roots.quests);
  if (!questId) {
    return;
  }
  const previous = questDefinitions.get(questId);
  if (previous) {
    errors.push(`${relative(file)}: duplicate quest id "${questId}" also defined in ${previous.file}.`);
    return;
  }
  const metadata = metadataObject(data);
  questDefinitions.set(questId, {
    file: relative(file),
    parent: stringValue(metadata.parent),
    questline: stringValue(metadata.questline),
    pathQuestline: questPathQuestline(file),
    stageIds: questV2StageIds(data.stages)
  });
}

function checkQuestV2(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    questV2Error(file, "", "root", "quest module v2 root must be an object.", "Wrap the resource in a JSON object.");
    return;
  }

  const questId = stringValue(data.id);
  if (data.external_scenes !== undefined) {
    questV2Error(file, "/external_scenes", "external_scenes",
      "external dialogue roots are unsupported in beta.13 quest bundles.",
      "Keep structural dialogue in quest.json and use bundle scene companions only for persistent orchestration.");
  }
  checkQuestV2UnknownKeys(file, data, "", "root", questV2RootKeys);
  if (data.discovery !== undefined) {
    for (const issue of validateConversationSchema(data.discovery, questV2Schema.properties.discovery))
      questV2Error(file, "/discovery" + issue.pointer, "discovery", issue.message, "Use the discovery schema.");
    const ids = new Set();
    for (const entry of Array.isArray(data.discovery) ? data.discovery : []) {
      if (ids.has(entry?.id)) questV2Error(file, "/discovery", "discovery", "duplicate discovery id", "Use unique discovery ids.");
      ids.add(entry?.id);
    }
  }
  if (!stringValue(data.localization_prefix)) {
    questV2Error(file, "/localization_prefix", "localization_prefix", "localization_prefix is required.", "Use an immutable globally unique prefix beginning with the datapack namespace.");
  }
  if (stringValue(data.schema) !== questV2SchemaId) {
    questV2Error(file, "/schema", "schema", `schema must be "${questV2SchemaId}".`, "Use the quest module v2 schema marker.");
  }
  if (!questId) {
    questV2Error(file, "/id", "id", "quest id is required.", "Add a namespaced id such as villagerretaliation:example_quest.");
  } else if (!isResourceLocation(questId)) {
    questV2Error(file, "/id", "id", `quest id "${questId}" is not a valid resource location.`, "Use namespace:path with lowercase characters.");
  }

  checkQuestV2Metadata(file, data.metadata, "/metadata", "metadata", questId);
  checkQuestV2Provider(file, data.provider, "/provider", "provider");
  if (data.provider?.campaign_role !== undefined && !stringValue(data.metadata?.campaign)) {
    questV2Error(file, "/provider/campaign_role", "provider.campaign_role", "campaign character providers require metadata.campaign.", "Declare the owning campaign.");
  }
  checkQuestV2Availability(file, data.availability, "/availability", "availability", questId);
  checkQuestV2Lifecycle(file, data.lifecycle, "/lifecycle", "lifecycle", questId);
  checkQuestV2Target(file, data.target, "/target", "target");
  checkQuestV2RootDialogue(file, data.dialogue, "/dialogue", "dialogue", questId);
  const model = checkQuestV2Stages(file, data.stages, "/stages", "stages", questId);
  if (!stringValue(data.metadata?.campaign)) {
    for (const [stageIndex, stage] of (Array.isArray(data.stages) ? data.stages : []).entries()) {
      for (const [objectiveIndex, objective] of (Array.isArray(stage?.objectives) ? stage.objectives : []).entries()) {
        if (objective?.story !== undefined) questV2Error(file, `/stages/${stageIndex}/objectives/${objectiveIndex}/story`,
          "story", "story objectives require metadata.campaign.", "Declare the owning campaign.");
        if (objective?.campaign_location !== undefined) questV2Error(file, `/stages/${stageIndex}/objectives/${objectiveIndex}/campaign_location`,
          "campaign_location", "campaign location objectives require metadata.campaign.", "Declare the owning campaign.");
      }
    }
  }
  checkQuestV2Events(file, data.events, "/events", "events", questId, model.stageIds, "");
  checkQuestV2Rewards(file, data.rewards, "/rewards", "rewards", questId);
  checkQuestV2Ui(file, data.ui, "/ui", "ui");
  validateQuestV2Graph(file, data, model, questId);
  warnQuestV2LifecycleCoverage(file, data, model);
  collectQuestMessageKeyReferences(file, data);
  collectQuestLocalizedReferences(file, data, stringValue(data.localization_prefix));
}

function checkQuestV2Metadata(file, metadata, pointer, location, questId) {
  if (metadata === undefined) {
    return;
  }
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    questV2Error(file, pointer, location, "metadata must be an object.", "Use metadata.title, metadata.questline, and related fields inside an object.");
    return;
  }
  checkQuestV2UnknownKeys(file, metadata, pointer, location, questV2MetadataKeys);
  checkQuestV2OptionalString(file, metadata, pointer, location, "title");
  checkQuestV2OptionalString(file, metadata, pointer, location, "description");
  checkQuestV2OptionalString(file, metadata, pointer, location, "title_key");
  checkQuestV2OptionalString(file, metadata, pointer, location, "description_key");
  checkQuestV2OptionalString(file, metadata, pointer, location, "questline");
  if (metadata.campaign !== undefined) {
    const descriptor = questV2Schema.$defs.metadata.properties.campaign;
    if (typeof metadata.campaign !== "string" || metadata.campaign.length > descriptor.maxLength
        || !new RegExp(descriptor.pattern).test(metadata.campaign)) {
      questV2Error(file, `${pointer}/campaign`, `${location}.campaign`, "campaign must be a namespaced resource id.", "Use the owning campaign's namespaced id.");
    }
  }
  checkQuestV2OptionalString(file, metadata, pointer, location, "parent");
  checkQuestV2OptionalString(file, metadata, pointer, location, "author");
  checkQuestV2OptionalString(file, metadata, pointer, location, "version");
  checkQuestV2OptionalBoolean(file, metadata, pointer, location, "show_locked_adventure_hint");
  checkQuestV2OptionalInteger(file, metadata, pointer, location, "revision", { min: 1 });
  if (metadata.migration !== undefined) {
    if (!metadata.migration || typeof metadata.migration !== "object" || Array.isArray(metadata.migration)) {
      questV2Error(file, `${pointer}/migration`, `${location}.migration`, "migration must be an object.", "Use active_policy plus optional stage_aliases and objective_aliases maps.");
    } else {
      checkQuestV2UnknownKeys(file, metadata.migration, `${pointer}/migration`, `${location}.migration`, questV2MigrationKeys);
      const policy = normalizedString(firstDefined(metadata.migration.active_policy, metadata.migration.on_active_change));
      if (policy && !["keep", "reset", "reset_stage", "stage_reset", "restart", "restart_quest", "fail", "fail_quest"].includes(policy)) {
        questV2Error(file, `${pointer}/migration/active_policy`, `${location}.migration.active_policy`, `unknown active migration policy "${policy}".`, "Use keep, reset_stage, restart, or fail.");
      }
      for (const key of ["stage_aliases", "objective_aliases"]) {
        const aliases = metadata.migration[key];
        if (aliases === undefined) continue;
        if (!aliases || typeof aliases !== "object" || Array.isArray(aliases)
            || Object.values(aliases).some((value) => typeof value !== "string" || !value.trim())) {
          questV2Error(file, `${pointer}/migration/${key}`, `${location}.migration.${key}`, `${key} must map old ids to new id strings.`, "Use an object whose values are non-empty strings.");
        }
      }
    }
  }
  checkStringList(file, metadata, location, ["tags"], "quest metadata tag");
  checkResourceIdValues(file, metadata, location, ["parent"], "quest parent id");
  const parent = stringValue(metadata.parent);
  if (parent) {
    pendingQuestReferences.push({ file, location: `${location}.parent`, id: parent, reason: "quest module v2 metadata parent" });
  }
  if (questId && parent === questId) {
    questV2Error(file, `${pointer}/parent`, `${location}.parent`, "metadata.parent must not reference this quest's own id.", "Point parent at another quest or remove it.");
  }
}

function checkQuestV2Provider(file, provider, pointer, location) {
  if (!provider || typeof provider !== "object" || Array.isArray(provider)) {
    questV2Error(file, pointer, location, "provider must be an object.", "Set provider.type to a registered provider.");
    return;
  }
  checkQuestV2UnknownKeys(file, provider, pointer, location, questV2ProviderKeys);
  const type = stringValue(provider.type);
  if (!type) {
    questV2Error(file, `${pointer}/type`, `${location}.type`, "provider type is required.", "Set provider.type to a registered provider such as villagerretaliation:villager.");
  } else if (!questV2RegistryIndex.providers.values.has(type)) {
    questV2Error(file, `${pointer}/type`, `${location}.type`, `unknown provider type "${type}".`, "Use one of the providers in quest-registry-metadata.json.");
  }
  for (const key of ["capabilities", "required_capabilities"]) {
    checkQuestV2StringArray(file, provider[key], `${pointer}/${key}`, `${location}.${key}`, "provider capability");
    for (const capability of readValues(provider, [key])) {
      if (typeof capability !== "string" || !capability.trim()) {
        continue;
      }
      const normalized = capability.trim();
      if (!isResourceLocation(normalized)) {
        questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `provider capability "${normalized}" is not a valid resource location.`, "Use namespaced capability ids from quest-registry-metadata.json.");
      }
      const supported = questV2RegistryIndex.providers.capabilities.get(type);
      if (supported && !supported.has(normalized)) {
        questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `provider "${type}" does not support capability "${normalized}".`, "Remove the capability or choose a provider that exports it.");
      }
    }
  }
  if (provider.campaign_role !== undefined) {
    const descriptor = questV2Schema.$defs.provider.properties.campaign_role;
    if (typeof provider.campaign_role !== "string" || !new RegExp(descriptor.pattern).test(provider.campaign_role)
        || provider.campaign_role.length > descriptor.maxLength || type !== "villagerretaliation:villager") {
      questV2Error(file, `${pointer}/campaign_role`, `${location}.campaign_role`, "campaign_role requires a stable character alias on a villager provider.", "Use a role from the owning campaign.");
    }
  }
  checkQuestV2OptionalString(file, provider, pointer, location, "death_protection");
  if (provider.death_protection !== undefined && !["none", "while_active", "after_start"].includes(normalizedString(provider.death_protection))) {
    questV2Error(file, `${pointer}/death_protection`, `${location}.death_protection`, `unknown death protection mode "${provider.death_protection}".`, "Use none, while_active, or after_start.");
  }
  checkQuestV2OptionalObject(file, provider.filters, `${pointer}/filters`, `${location}.filters`, "provider filters");
  checkQuestV2OptionalObject(file, provider.data, `${pointer}/data`, `${location}.data`, "provider data");
}

function checkQuestV2Target(file, target, pointer, location) {
  if (target === undefined) {
    return;
  }
  if (!target || typeof target !== "object" || Array.isArray(target)) {
    questV2Error(file, pointer, location, "target must be an object.", "Use structure, dimension, search_radius, discovery_radius, or proof_item fields.");
    return;
  }
  checkQuestV2UnknownKeys(file, target, pointer, location, questV2TargetKeys);
  for (const key of ["structure", "dimension", "proof_item"]) {
    checkQuestV2OptionalString(file, target, pointer, location, key);
    const value = stringValue(target[key]);
    if (value && !isResourceLocation(value)) {
      questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} "${value}" is not a valid resource location.`, "Use namespace:path with lowercase characters.");
    }
  }
  checkQuestV2StringArray(file, target.pieces, `${pointer}/pieces`, `${location}.pieces`, "target piece");
  checkQuestV2OptionalInteger(file, target, pointer, location, "search_radius", { min: 1 });
  checkQuestV2OptionalInteger(file, target, pointer, location, "discovery_radius", { min: 1 });
  for (const key of ["proof_item_components", "proof_item_durability", "proof_item_custom_data", "proof_item_nbt"]) {
    checkQuestV2OptionalObject(file, target[key], `${pointer}/${key}`, `${location}.${key}`, key);
  }
}

function checkQuestV2Availability(file, availability, pointer, location, questId) {
  if (availability === undefined) {
    return;
  }
  if (!availability || typeof availability !== "object" || Array.isArray(availability)) {
    questV2Error(file, pointer, location, "availability must be an object.", "Use availability.conditions, prerequisites, or repeatable fields.");
    return;
  }
  checkQuestV2UnknownKeys(file, availability, pointer, location, questV2AvailabilityKeys);
  checkQuestV2Conditions(file, availability.conditions, `${pointer}/conditions`, `${location}.conditions`, questId, "quest module v2 availability");
  if (availability.active !== undefined) {
    if (!availability.active || typeof availability.active !== "object" || Array.isArray(availability.active)) {
      questV2Error(file, `${pointer}/active`, `${location}.active`, "active must be an object.", "Use active.conditions and active pause/display controls.");
    } else {
      checkQuestV2UnknownKeys(file, availability.active, `${pointer}/active`, `${location}.active`, questV2ActiveStateKeys);
      checkQuestV2Conditions(file, availability.active.conditions, `${pointer}/active/conditions`, `${location}.active.conditions`, questId, "quest module v2 active state");
      checkQuestV2OptionalBoolean(file, availability.active, `${pointer}/active`, `${location}.active`, "hide_when_unmet");
      checkQuestV2OptionalBoolean(file, availability.active, `${pointer}/active`, `${location}.active`, "pause_progress_when_unmet");
    }
  }
  if (availability.expiration !== undefined) {
    if (!availability.expiration || typeof availability.expiration !== "object" || Array.isArray(availability.expiration)) {
      questV2Error(file, `${pointer}/expiration`, `${location}.expiration`, "expiration must be an object.", "Use expiration.after_ticks, conditions, and outcome controls.");
    } else {
      checkQuestV2UnknownKeys(file, availability.expiration, `${pointer}/expiration`, `${location}.expiration`, questV2ExpirationKeys);
      checkQuestV2Conditions(file, availability.expiration.conditions, `${pointer}/expiration/conditions`, `${location}.expiration.conditions`, questId, "quest module v2 expiration");
      checkQuestV2OptionalInteger(file, availability.expiration, `${pointer}/expiration`, `${location}.expiration`, "after_ticks", { min: 0 });
      checkQuestV2OptionalBoolean(file, availability.expiration, `${pointer}/expiration`, `${location}.expiration`, "consume");
      checkQuestV2OptionalBoolean(file, availability.expiration, `${pointer}/expiration`, `${location}.expiration`, "allow_repickup");
      checkQuestV2OptionalBoolean(file, availability.expiration, `${pointer}/expiration`, `${location}.expiration`, "notify");
    }
  }
  if (availability.branch !== undefined) {
    if (!availability.branch || typeof availability.branch !== "object" || Array.isArray(availability.branch)) {
      questV2Error(file, `${pointer}/branch`, `${location}.branch`, "branch must be an object.", "Use exclusive_group and optional branch lock arrays.");
    } else {
      checkQuestV2UnknownKeys(file, availability.branch, `${pointer}/branch`, `${location}.branch`, questV2BranchKeys);
      checkQuestV2OptionalString(file, availability.branch, `${pointer}/branch`, `${location}.branch`, "exclusive_group");
      checkQuestV2OptionalString(file, availability.branch, `${pointer}/branch`, `${location}.branch`, "exclusive_on");
      for (const key of ["blocks_on_start", "blocks_on_completion", "blocks"]) {
        checkQuestV2StringArray(file, availability.branch[key], `${pointer}/branch/${key}`, `${location}.branch.${key}`, "branch quest id");
      }
    }
  }
  checkQuestV2OptionalString(file, availability, pointer, location, "cooldown");
  checkQuestV2OptionalInteger(file, availability, pointer, location, "cooldown_ticks", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "cooldown_days", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "cooldown_seconds", { min: 0 });
  checkQuestV2OptionalString(file, availability, pointer, location, "completion_cooldown");
  checkQuestV2OptionalInteger(file, availability, pointer, location, "completion_cooldown_ticks", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "completion_cooldown_days", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "completion_cooldown_seconds", { min: 0 });
  checkQuestV2OptionalString(file, availability, pointer, location, "prerequisite_cooldown");
  checkQuestV2OptionalInteger(file, availability, pointer, location, "prerequisite_cooldown_ticks", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "prerequisite_cooldown_days", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "prerequisite_cooldown_seconds", { min: 0 });
  checkQuestV2OptionalString(file, availability, pointer, location, "exclusive_group");
  checkQuestV2OptionalString(file, availability, pointer, location, "exclusive_on");
  if (availability.exclusive_on !== undefined && !["started", "completed"].includes(normalizedString(availability.exclusive_on))) {
    questV2Error(file, `${pointer}/exclusive_on`, `${location}.exclusive_on`, `unknown branch trigger "${availability.exclusive_on}".`, "Use started or completed.");
  }
  for (const key of ["blocks_on_start", "blocks_on_completion"]) {
    checkQuestV2StringArray(file, availability[key], `${pointer}/${key}`, `${location}.${key}`, "branch quest id");
  }
  checkQuestV2OptionalBoolean(file, availability, pointer, location, "repeatable");
  checkQuestV2OptionalInteger(file, availability, pointer, location, "max_starts", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "max_completions", { min: 0 });
  checkQuestV2OptionalString(file, availability, pointer, location, "completion_scope");
  checkQuestV2OptionalString(file, availability, pointer, location, "scope");
  checkQuestV2OptionalString(file, availability, pointer, location, "abandonment");
  checkQuestV2OptionalString(file, availability, pointer, location, "abandonment_cooldown");
  checkQuestV2OptionalInteger(file, availability, pointer, location, "abandonment_cooldown_ticks", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "abandonment_cooldown_days", { min: 0 });
  checkQuestV2OptionalInteger(file, availability, pointer, location, "abandonment_cooldown_seconds", { min: 0 });
  checkQuestV2OptionalBoolean(file, availability, pointer, location, "consume_on_completion");
  checkQuestV2OptionalBoolean(file, availability, pointer, location, "consume_on_abandonment");
  checkQuestV2OptionalBoolean(file, availability, pointer, location, "locked_to_villager");
  checkQuestV2OptionalBoolean(file, availability, pointer, location, "cross_villager_compatible");
  checkQuestV2StringArray(file, availability.prerequisites, `${pointer}/prerequisites`, `${location}.prerequisites`, "quest prerequisite");
  for (const prerequisite of readValues(availability, ["prerequisites"])) {
    if (typeof prerequisite === "string" && prerequisite.trim()) {
      pendingQuestReferences.push({
        file,
        location: `${location}.prerequisites`,
        id: prerequisite.trim(),
        reason: "quest module v2 availability prerequisite"
      });
    }
  }
}

function checkQuestV2Lifecycle(file, lifecycle, pointer, location, questId) {
  if (lifecycle === undefined) {
    return;
  }
  if (!lifecycle || typeof lifecycle !== "object" || Array.isArray(lifecycle)) {
    questV2Error(file, pointer, location, "lifecycle must be an object.", "Use lifecycle hooks such as on_start or on_complete.");
    return;
  }
  checkQuestV2UnknownKeys(file, lifecycle, pointer, location, questV2LifecycleKeys);
  for (const hook of questV2LifecycleKeys) {
    if (hook === "dialogue" || lifecycle[hook] === undefined) {
      continue;
    }
    checkQuestV2LifecycleHook(file, lifecycle[hook], `${pointer}/${hook}`, `${location}.${hook}`, questId);
  }
  checkQuestV2Scenes(file, lifecycle.dialogue, `${pointer}/dialogue`, `${location}.dialogue`, questId);
}

function checkQuestV2LifecycleHook(file, hook, pointer, location, questId) {
  if (!hook || typeof hook !== "object" || Array.isArray(hook)) {
    questV2Error(file, pointer, location, "lifecycle hook must be an object.", "Use actions or one transition target inside the hook.");
    return;
  }
  checkQuestV2UnknownKeys(file, hook, pointer, location, questV2LifecycleHookKeys);
  checkQuestV2Actions(file, hook.actions, `${pointer}/actions`, `${location}.actions`, questId, {
    liveContextWarningUsage: "quest module v2 lifecycle hook"
  });
  const transition = readQuestV2WrapperTransition(file, hook, pointer, location);
  if (Array.isArray(hook.actions) && hook.actions.length > 0 && !transition.empty) {
    questV2Error(file, pointer, location, "lifecycle hook cannot define both actions and a transition.", "Choose actions for side effects or transition for flow control, not both.");
  }
}

function checkQuestV2RootDialogue(file, dialogue, pointer, location, questId) {
  if (dialogue === undefined) {
    return;
  }
  if (!dialogue || typeof dialogue !== "object" || Array.isArray(dialogue)) {
    questV2Error(file, pointer, location, "dialogue must be an object keyed by slot id.", "Move dialogue entries under named slots.");
    return;
  }
  for (const [slot, value] of Object.entries(dialogue)) {
    checkQuestV2DialogueSlot(file, value, `${pointer}/${escapeJsonPointer(slot)}`, `${location}.${slot}`, questId, slot, new Set());
  }
}

function checkQuestV2Stages(file, stages, pointer, location, questId) {
  const model = {
    stages: [],
    stageIds: new Set(),
    stagePointers: new Map(),
    objectiveIdsByStage: new Map(),
    sceneIdsByStage: new Map(),
    responseIdsByStage: new Map(),
    transitions: []
  };
  if (!Array.isArray(stages)) {
    questV2Error(file, pointer, location, "stages must be an array.", "Add an ordered stages array.");
    return model;
  }
  const declaredStageIds = questV2StageIds(stages);
  for (const [index, stage] of stages.entries()) {
    const stagePointer = `${pointer}/${index}`;
    const stageLocation = `${location}[${index}]`;
    if (!stage || typeof stage !== "object" || Array.isArray(stage)) {
      questV2Error(file, stagePointer, stageLocation, "stage must be an object.", "Each stage entry needs id and objectives.");
      continue;
    }
    checkQuestV2UnknownKeys(file, stage, stagePointer, stageLocation, questV2StageKeys);
    if (stage.followers !== undefined) {
      for (const issue of validateConversationSchema(stage.followers, { type: "array", items: questV2Schema.$defs.follower }))
        questV2Error(file, stagePointer + "/followers" + issue.pointer, stageLocation + ".followers", issue.message, "Use the follower directive schema.");
    }
    const stageId = stringValue(stage.id);
    checkQuestV2Id(file, stageId, `${stagePointer}/id`, `${stageLocation}.id`, "stage id");
    if (stageId) {
      if (model.stageIds.has(stageId)) {
        questV2Error(file, `${stagePointer}/id`, `${stageLocation}.id`, `duplicate stage id "${stageId}".`, "Use unique stage ids within the quest.");
      }
      model.stageIds.add(stageId);
      model.stagePointers.set(stageId, stagePointer);
      model.stages.push({ id: stageId, pointer: stagePointer, location: stageLocation, raw: stage });
    }
    checkQuestV2OptionalString(file, stage, stagePointer, stageLocation, "title");
    checkQuestV2OptionalString(file, stage, stagePointer, stageLocation, "title_key");
    checkQuestV2OptionalString(file, stage, stagePointer, stageLocation, "description");
    checkQuestV2OptionalString(file, stage, stagePointer, stageLocation, "description_key");
    const objectiveIds = checkQuestV2Objectives(file, stage.objectives, `${stagePointer}/objectives`, `${stageLocation}.objectives`, questId);
    model.objectiveIdsByStage.set(stageId, objectiveIds);
    const completeWhenRefs = readQuestV2ObjectiveReferences(stage.complete_when);
    for (const ref of completeWhenRefs) {
      if (!objectiveIds.has(ref)) {
        questV2Error(file, `${stagePointer}/complete_when`, `${stageLocation}.complete_when`, `complete_when references missing objective "${ref}".`, "Reference an objective id defined in the same stage.");
      }
    }
    checkQuestV2Composition(file, stage, stagePointer, stageLocation);
    checkQuestV2Bonuses(file, stage.bonuses, `${stagePointer}/bonuses`, `${stageLocation}.bonuses`, questId, objectiveIds);
    checkQuestTimer(file, stage.timer, `${stagePointer}/timer`, `${stageLocation}.timer`, model, stageId);
    for (const [index, objective] of (Array.isArray(stage.objectives) ? stage.objectives : []).entries())
      checkQuestTimer(file, objective?.timer, `${stagePointer}/objectives/${index}/timer`, `${stageLocation}.objectives[${index}].timer`, model, stageId);
    const next = readQuestV2Transition(file, stage.next, `${stagePointer}/next`, `${stageLocation}.next`);
    if (!next.empty) {
      model.transitions.push({ pointer: `${stagePointer}/next`, location: `${stageLocation}.next`, transition: next, stageId });
    }
    const sceneIds = checkQuestV2Scenes(file, stage.scenes, `${stagePointer}/scenes`, `${stageLocation}.scenes`, questId, model.transitions, stageId);
    model.sceneIdsByStage.set(stageId, sceneIds);
    checkQuestV2DialogueSlots(file, stage.dialogue, `${stagePointer}/dialogue`, `${stageLocation}.dialogue`, questId, sceneIds, model.transitions, stageId);
    const responseIds = checkQuestV2Responses(file, stage.responses, `${stagePointer}/responses`, `${stageLocation}.responses`, questId, model.transitions, stageId);
    model.responseIdsByStage.set(stageId, responseIds);
    checkQuestV2Events(file, stage.events, `${stagePointer}/events`, `${stageLocation}.events`, questId, declaredStageIds, stageId, model.transitions);
    checkQuestV2Actions(file, firstDefined(stage.on_enter, stage.entry_actions), `${stagePointer}/on_enter`, `${stageLocation}.on_enter`, questId, {
      liveContextWarningUsage: "quest module v2 stage entry action"
    });
    checkQuestV2Actions(file, firstDefined(stage.on_exit, stage.exit_actions), `${stagePointer}/on_exit`, `${stageLocation}.on_exit`, questId, {
      liveContextWarningUsage: "quest module v2 stage exit action"
    });
    checkQuestV2Rewards(file, stage.rewards, `${stagePointer}/rewards`, `${stageLocation}.rewards`, questId);
    checkQuestV2Ui(file, stage.ui, `${stagePointer}/ui`, `${stageLocation}.ui`);
  }
  return model;
}

function checkQuestV2Composition(file, stage, pointer, location) {
  const completion = stage.completion;
  if (completion !== undefined && (!completion || typeof completion !== "object" || Array.isArray(completion))) {
    questV2Error(file, `${pointer}/completion`, `${location}.completion`, "completion must be an object.", "Use completion.mode and completion.count.");
    return;
  }
  if (completion) {
    checkQuestV2UnknownKeys(file, completion, `${pointer}/completion`, `${location}.completion`, questV2CompletionKeys);
  }
  const mode = normalizedString(firstDefined(completion?.mode, stage.completion_mode));
  if (mode && !["all", "any", "at_least", "atleast", "count", "k_of_n"].includes(mode)) {
    questV2Error(file, `${pointer}/completion`, `${location}.completion`, `unknown completion mode "${mode}".`, "Use all, any, or at_least.");
  }
  const count = firstDefined(completion?.count, stage.completion_count);
  if (count !== undefined && (!Number.isInteger(count) || count < 1)) {
    questV2Error(file, `${pointer}/completion`, `${location}.completion`, "completion count must be a positive integer.", "Use a count of at least 1.");
  }
}

function checkQuestV2Bonuses(file, bonuses, pointer, location, questId, objectiveIds) {
  if (bonuses === undefined) return;
  if (!Array.isArray(bonuses)) {
    questV2Error(file, pointer, location, "bonuses must be an array.", "Add one or more one-shot bonus outcome objects.");
    return;
  }
  const ids = new Set();
  for (const [index, bonus] of bonuses.entries()) {
    const bonusPointer = `${pointer}/${index}`;
    const bonusLocation = `${location}[${index}]`;
    if (!bonus || typeof bonus !== "object" || Array.isArray(bonus)) {
      questV2Error(file, bonusPointer, bonusLocation, "bonus must be an object.", "Each bonus needs id, when, and actions.");
      continue;
    }
    checkQuestV2UnknownKeys(file, bonus, bonusPointer, bonusLocation, questV2BonusKeys);
    const id = stringValue(bonus.id) || `bonus_${index}`;
    checkQuestV2Id(file, id, `${bonusPointer}/id`, `${bonusLocation}.id`, "bonus id");
    if (ids.has(id)) questV2Error(file, `${bonusPointer}/id`, `${bonusLocation}.id`, `duplicate bonus id "${id}".`, "Use unique bonus ids within the stage.");
    ids.add(id);
    const refs = readQuestV2ObjectiveReferences(bonus.when);
    if (bonus.when === undefined || bonus.when === null || (Array.isArray(bonus.when) && bonus.when.length === 0)) {
      questV2Error(file, `${bonusPointer}/when`, `${bonusLocation}.when`, "bonus when predicates are required.", "Reference objectives or add condition predicates.");
    }
    for (const ref of refs) {
      if (!objectiveIds.has(ref)) questV2Error(file, `${bonusPointer}/when`, `${bonusLocation}.when`, `bonus references missing objective "${ref}".`, "Reference an objective in the same stage.");
    }
    const mode = normalizedString(bonus.mode);
    if (mode && !["all", "any", "at_least", "atleast", "count", "k_of_n"].includes(mode)) {
      questV2Error(file, `${bonusPointer}/mode`, `${bonusLocation}.mode`, `unknown bonus mode "${mode}".`, "Use all, any, or at_least.");
    }
    checkQuestV2OptionalInteger(file, bonus, bonusPointer, bonusLocation, "count", { min: 1 });
    checkQuestV2Actions(file, bonus.actions, `${bonusPointer}/actions`, `${bonusLocation}.actions`, questId, { liveContextWarningUsage: "quest module v2 bonus" });
  }
}

function checkQuestV2Objectives(file, objectives, pointer, location, questId) {
  const ids = new Set();
  if (!Array.isArray(objectives)) {
    questV2Error(file, pointer, location, "objectives must be an array.", "Add stage objectives as an array.");
    return ids;
  }
  for (const [index, objective] of objectives.entries()) {
    const objectivePointer = `${pointer}/${index}`;
    const objectiveLocation = `${location}[${index}]`;
    if (!objective || typeof objective !== "object" || Array.isArray(objective)) {
      questV2Error(file, objectivePointer, objectiveLocation, "objective must be an object.", "Each objective needs id and type.");
      continue;
    }
    checkQuestV2UnknownKeys(file, objective, objectivePointer, objectiveLocation, questV2ObjectiveKeys);
    const id = stringValue(objective.id);
    checkQuestV2Id(file, id, `${objectivePointer}/id`, `${objectiveLocation}.id`, "objective id");
    if (id) {
      if (ids.has(id)) {
        questV2Error(file, `${objectivePointer}/id`, `${objectiveLocation}.id`, `duplicate objective id "${id}" in stage.`, "Use unique objective ids within each stage.");
      }
      ids.add(id);
    }
    const type = stringValue(objective.type);
    if (!type) {
      questV2Error(file, `${objectivePointer}/type`, `${objectiveLocation}.type`, "objective type is required.", "Use one of the objective types in quest-registry-metadata.json.");
    } else if (!questV2RegistryIndex.objectives.values.has(type)) {
      questV2Error(file, `${objectivePointer}/type`, `${objectiveLocation}.type`, `unknown objective type "${type}".`, "Use one of the objective types in quest-registry-metadata.json.");
    }
    checkQuestV2OptionalBoolean(file, objective, objectivePointer, objectiveLocation, "optional");
    checkQuestV2OptionalInteger(file, objective, objectivePointer, objectiveLocation, "count", { min: 1 });
    checkQuestV2OptionalBoolean(file, objective, objectivePointer, objectiveLocation, "consume");
    checkQuestV2RandomItemSelection(file, objective, objectivePointer, objectiveLocation, type);
    checkQuestV2Tracker(file, objective.tracker, `${objectivePointer}/tracker`, `${objectiveLocation}.tracker`);
    checkQuestV2OptionalObject(file, objective.target, `${objectivePointer}/target`, `${objectiveLocation}.target`, "objective target");
    checkQuestV2OptionalObject(file, objective.location, `${objectivePointer}/location`, `${objectiveLocation}.location`, "objective location");
    for (const key of ["components", "durability", "custom_data", "nbt"]) {
      checkQuestV2OptionalObject(file, objective[key], `${objectivePointer}/${key}`, `${objectiveLocation}.${key}`, key);
    }
    checkQuestV2Conditions(file, objective.conditions, `${objectivePointer}/conditions`, `${objectiveLocation}.conditions`, questId, "quest module v2 objective");
    checkQuestV2Ui(file, objective.ui, `${objectivePointer}/ui`, `${objectiveLocation}.ui`);
    checkQuestV2ObjectiveRuntimeRequirements(file, objective, objectivePointer, objectiveLocation, type);
  }
  return ids;
}

function checkQuestV2RandomItemSelection(file, objective, pointer, location, type) {
  if (objective.selection === undefined && objective.items === undefined) return;
  const canonical = questV2RegistryIndex.objectives.canonical.get(type) || type;
  if (canonical !== "item_check") {
    questV2Error(file, pointer, location, "selection and items are only supported by item_check objectives.", "Remove the random item fields.");
    return;
  }
  if (stringValue(objective.item)) {
    questV2Error(file, pointer, location, "random item_check objectives cannot combine item with items.", "Use either item or selection with items.");
  }
  if (normalizedString(objective.selection) !== "random") {
    questV2Error(file, `${pointer}/selection`, `${location}.selection`, "items requires selection to be random.", "Set selection to random.");
  }
  validateRandomItemEntries(
    objective.items,
    (suffix, message) => questV2Error(file, `${pointer}/items${suffix}`, `${location}.items${suffix}`, message, "Use a namespaced item id, #tag string, or one weighted item/tag object.")
  );
}

function validateRandomItemEntries(items, report) {
  if (!Array.isArray(items) || items.length === 0) {
    report("", "random item selection requires a non-empty items array.");
    return;
  }
  items.forEach((entry, index) => {
    const suffix = `[${index}]`;
    if (typeof entry === "string") {
      const selector = entry.trim();
      const id = selector.startsWith("#") ? selector.slice(1) : selector;
      if (!selector || !isResourceLocation(id)) report(suffix, `invalid item selector "${entry}".`);
      return;
    }
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      report(suffix, "random item entry must be a string or object.");
      return;
    }
    for (const key of Object.keys(entry)) {
      if (!["item", "tag", "weight"].includes(key)) report(`${suffix}.${key}`, `unsupported random item entry field "${key}".`);
    }
    const item = stringValue(entry.item);
    const tag = stringValue(entry.tag);
    if ((!item && !tag) || (item && tag)) {
      report(suffix, "weighted random item entry must define exactly one of item or tag.");
    }
    const selector = (item || tag).replace(/^#/, "");
    if (selector && !isResourceLocation(selector)) report(suffix, `invalid item selector "${selector}".`);
    if (entry.weight !== undefined && (!Number.isInteger(entry.weight) || entry.weight < 1)) {
      report(`${suffix}.weight`, "weight must be a positive integer.");
    }
  });
}

function checkRegisteredObjectiveSchema(file, objective, pointer, location) {
  for (const issue of validateConversationSchema(objective, { allOf: questV2Schema.$defs.objective.allOf ?? [] })) {
    if (pointer) questV2Error(file, pointer + issue.pointer, location, issue.message, "Use the registered objective schema.");
    else errors.push(`${relative(file)}: ${location}${issue.pointer}: ${issue.message}`);
  }
}

function checkQuestV2ObjectiveRuntimeRequirements(file, objective, pointer, location, type) {
  checkRegisteredObjectiveSchema(file, objective, pointer, location);
  const canonical = questV2RegistryIndex.objectives.canonical.get(type) || type;
  if (objective.campaign_location !== undefined) {
    const descriptor = questV2Schema.$defs.objective.properties.campaign_location;
    if (typeof objective.campaign_location !== "string" || !new RegExp(descriptor.pattern).test(objective.campaign_location)
        || objective.campaign_location.length > descriptor.maxLength || canonical !== "location_visit") {
      questV2Error(file, `${pointer}/campaign_location`, location, "campaign_location requires a stable location alias on a location_visit objective.", "Use an alias from the owning campaign.");
    }
    for (const field of ["location", "pos", "x", "y", "z", "dimension", "structure", "pieces", "search_radius", "discovery_radius", "target", "targets"]) {
      if (objective[field] !== undefined) questV2Error(file, `${pointer}/${field}`, location, "campaign_location cannot override its campaign binding.", "Remove the local destination fields.");
    }
  }
  if (canonical === "criterion") {
    const criterion = stringValue(objective.criterion);
    if (!criterion || !isResourceLocation(criterion)) {
      questV2Error(file, `${pointer}/criterion`, `${location}.criterion`, "criterion objective requires a namespaced criterion id.", "Set criterion to the event id emitted through QuestCriterionApi.");
    }
    if (objective.match !== undefined && (!objective.match || typeof objective.match !== "object" || Array.isArray(objective.match)
        || Object.values(objective.match).some((value) => !["string", "number", "boolean"].includes(typeof value)))) {
      questV2Error(file, `${pointer}/match`, `${location}.match`, "criterion match must map keys to string, number, or boolean values.", "Use a flat object of exact event-field matches.");
    }
  }
  if (canonical === "structure_visit" && !hasStringValues(objective, ["structure"]) && !hasStringValues(objective.target, ["structure"])) {
    questV2Error(file, pointer, location, "structure_visit objective is missing structure.", "Set objective.structure or objective.target.structure.");
  }
  if (canonical === "item_check" && !stringValue(objective.item)
      && !(normalizedString(objective.selection) === "random" && Array.isArray(objective.items) && objective.items.length > 0)) {
    questV2Error(file, pointer, location, "item_check objective is missing an item selector.", "Set item, or set selection to random with a non-empty items array.");
  }
  if (["mob_kill"].includes(canonical) && !hasStringValues(objective, ["entity", "entities", "entity_tag", "entity_tags"])) {
    questV2Error(file, pointer, location, "mob_kill objective is missing an entity selector.", "Set entity, entities, entity_tag, or entity_tags.");
  }
  if (["block_break", "block_place", "block_interact"].includes(canonical) && !hasStringValues(objective, ["block", "blocks", "block_tag", "block_tags"])) {
    questV2Error(file, pointer, location, "block objective is missing a block selector.", "Set block, blocks, block_tag, or block_tags.");
  }
}

function checkQuestV2DialogueSlots(file, dialogue, pointer, location, questId, sceneIds, transitions = [], stageId = "") {
  if (dialogue === undefined) {
    return;
  }
  if (!dialogue || typeof dialogue !== "object" || Array.isArray(dialogue)) {
    questV2Error(file, pointer, location, "dialogue must be an object keyed by slot id.", "Use dialogue.slot_name objects.");
    return;
  }
  for (const [slot, value] of Object.entries(dialogue)) {
    checkQuestV2DialogueSlot(file, value, `${pointer}/${escapeJsonPointer(slot)}`, `${location}.${slot}`, questId, slot, sceneIds, transitions, stageId);
  }
}

function checkQuestV2DialogueSlot(file, slot, pointer, location, questId, slotId, sceneIds, transitions = [], stageId = "") {
  if (!slot || typeof slot !== "object" || Array.isArray(slot)) {
    questV2Error(file, pointer, location, "dialogue slot must be an object.", "Use a local scene, scene_ref, or inline lines/responses.");
    return;
  }
  checkQuestV2UnknownKeys(file, slot, pointer, location, questV2DialogueSlotKeys);
  if (hasAnyKey(slot, ["external", "external_scene", "external_entry"])) {
    questV2Error(file, pointer, location,
      "external dialogue references are unsupported in beta.13 quest bundles.",
      "Keep the complete offer, reminder, or turn-in structure in quest.json.");
  }
  const scene = firstString(slot.scene, slot.scene_ref);
  if (scene && sceneIds.size > 0 && !sceneIds.has(scene)) {
    questV2Error(file, `${pointer}/scene`, `${location}.scene`, `dialogue slot references missing local scene "${scene}".`, "Reference a scene id from this stage's scenes array.");
  }
  checkQuestV2OptionalString(file, slot, pointer, location, "label");
  checkQuestV2OptionalString(file, slot, pointer, location, "request");
  checkQuestV2OptionalBoolean(file, slot, pointer, location, "show_for_babies");
  checkQuestV2OptionalInteger(file, slot, pointer, location, "order");
  const hasInline = hasAnyKey(slot, ["text", "text_key", "lines", "exchanges", "responses"]);
  if (hasInline && scene) {
    questV2Error(file, pointer, location, "dialogue slot cannot mix inline scene content with scene_ref.", "Use either inline content or a bundled scene reference.");
  }
  if (hasInline) {
    checkQuestV2InlineScene(file, slot, pointer, location, questId, slotId, transitions, stageId);
  }
}

function checkQuestV2Scenes(file, scenes, pointer, location, questId, transitions = [], stageId = "") {
  const ids = new Set();
  if (scenes === undefined) {
    return ids;
  }
  if (!Array.isArray(scenes)) {
    questV2Error(file, pointer, location, "scenes must be an array.", "Use an array of scene objects.");
    return ids;
  }
  for (const [index, scene] of scenes.entries()) {
    const scenePointer = `${pointer}/${index}`;
    const sceneLocation = `${location}[${index}]`;
    const id = checkQuestV2InlineScene(file, scene, scenePointer, sceneLocation, questId, `scene_${index}`, transitions, stageId);
    if (id) {
      if (ids.has(id)) {
        questV2Error(file, `${scenePointer}/id`, `${sceneLocation}.id`, `duplicate scene id "${id}".`, "Use unique scene ids within the stage.");
      }
      ids.add(id);
    }
  }
  return ids;
}

function checkQuestV2InlineScene(file, scene, pointer, location, questId, fallbackId, transitions = [], stageId = "") {
  if (!scene || typeof scene !== "object" || Array.isArray(scene)) {
    questV2Error(file, pointer, location, "scene must be an object.", "Use lines, text_key, responses, or a bundled scene_ref inside the scene.");
    return "";
  }
  checkQuestV2UnknownKeys(file, scene, pointer, location, questV2SceneKeys);
  checkDialogueExchanges(file, scene, location);
  if (hasAnyKey(scene, ["external", "external_scene", "external_entry"])) {
    questV2Error(file, pointer, location,
      "external dialogue references are unsupported in beta.13 quest bundles.",
      "Keep structural dialogue in quest.json or reference a bundled persistent scene with scene_ref.");
  }
  const id = stringValue(scene.id) || fallbackId || "";
  checkQuestV2Id(file, id, `${pointer}/id`, `${location}.id`, "scene id");
  checkQuestV2OptionalString(file, scene, pointer, location, "label");
  checkQuestV2OptionalString(file, scene, pointer, location, "request");
  checkQuestV2OptionalBoolean(file, scene, pointer, location, "show_for_babies");
  checkQuestV2OptionalInteger(file, scene, pointer, location, "order");
  checkQuestV2StringArray(file, scene.lines, `${pointer}/lines`, `${location}.lines`, "scene line");
  checkQuestV2OptionalString(file, scene, pointer, location, "text");
  checkQuestV2OptionalString(file, scene, pointer, location, "text_key");
  checkQuestV2Conditions(file, scene.conditions, `${pointer}/conditions`, `${location}.conditions`, questId, "quest module v2 scene");
  checkQuestV2Actions(file, scene.actions, `${pointer}/actions`, `${location}.actions`, questId);
  const hasInline = hasAnyKey(scene, ["text", "text_key", "lines", "exchanges", "responses"]);
  const hasSceneRef = Object.hasOwn(scene, "scene_ref");
  if (hasSceneRef && !isResourceLocation(stringValue(scene.scene_ref))) {
    questV2Error(file, pointer + "/scene_ref", location + ".scene_ref",
      "scene_ref must be a namespaced bundled scene id.", "Use namespace:path for a scene in this bundle or _shared.");
  }
  if (hasInline && hasSceneRef) {
    questV2Error(file, pointer, location, "scene cannot mix inline dialogue content with scene_ref.", "Use either inline lines/responses or a bundled persistent scene reference.");
  }
  checkQuestV2Responses(file, scene.responses, `${pointer}/responses`, `${location}.responses`, questId, transitions, stageId);
  return id;
}

function checkQuestV2Responses(file, responses, pointer, location, questId, transitions, stageId) {
  const ids = new Set();
  if (responses === undefined) {
    return ids;
  }
  if (!Array.isArray(responses)) {
    questV2Error(file, pointer, location, "responses must be an array.", "Use an array of response objects.");
    return ids;
  }
  for (const [index, response] of responses.entries()) {
    const responsePointer = `${pointer}/${index}`;
    const responseLocation = `${location}[${index}]`;
    if (!response || typeof response !== "object" || Array.isArray(response)) {
      questV2Error(file, responsePointer, responseLocation, "response must be an object.", "Each response needs an id and optional label/actions/transition.");
      continue;
    }
    checkQuestV2UnknownKeys(file, response, responsePointer, responseLocation, questV2ResponseKeys);
    checkDialogueExchanges(file, response, responseLocation);
    const id = stringValue(response.id);
    checkQuestV2Id(file, id, `${responsePointer}/id`, `${responseLocation}.id`, "response id");
    if (id) {
      if (ids.has(id)) {
        questV2Error(file, `${responsePointer}/id`, `${responseLocation}.id`, `duplicate response id "${id}" in scene.`, "Use unique response ids within the response list.");
      }
      ids.add(id);
    }
    checkQuestV2OptionalString(file, response, responsePointer, responseLocation, "label");
    checkQuestV2OptionalString(file, response, responsePointer, responseLocation, "label_key");
    checkQuestV2OptionalString(file, response, responsePointer, responseLocation, "text");
    checkQuestV2OptionalString(file, response, responsePointer, responseLocation, "text_key");
    checkQuestV2StringArray(file, response.lines, `${responsePointer}/lines`, `${responseLocation}.lines`, "response line");
    checkQuestV2OptionalInteger(file, response, responsePointer, responseLocation, "order");
    checkQuestV2Conditions(file, response.conditions, `${responsePointer}/conditions`, `${responseLocation}.conditions`, questId, "quest module v2 response");
    checkQuestV2Actions(file, response.actions, `${responsePointer}/actions`, `${responseLocation}.actions`, questId);
    const transition = readQuestV2WrapperTransition(file, response, responsePointer, responseLocation);
    if (!transition.empty) {
      transitions?.push({ pointer: responsePointer, location: responseLocation, transition, stageId });
      if (transitionChangesStage(transition) && Array.isArray(response.actions)) {
        response.actions.forEach((action, actionIndex) => {
          if (isQuestV2StageChangingAction(action)) {
            questV2Error(file, `${responsePointer}/actions/${actionIndex}`, `${responseLocation}.actions[${actionIndex}]`, "response cannot define both a transition and a stage-changing action.", "Use transition or next for branch flow, and keep actions for side effects.");
          }
        });
      }
    }
  }
  return ids;
}

function checkQuestV2Events(file, events, pointer, location, questId, stageIds, localStageId, transitions = []) {
  if (events === undefined) {
    return;
  }
  if (!Array.isArray(events)) {
    questV2Error(file, pointer, location, "events must be an array.", "Use an array of event trigger objects.");
    return;
  }
  const ids = new Set();
  for (const [index, event] of events.entries()) {
    const eventPointer = `${pointer}/${index}`;
    const eventLocation = `${location}[${index}]`;
    if (!event || typeof event !== "object" || Array.isArray(event)) {
      questV2Error(file, eventPointer, eventLocation, "event must be an object.", "Each event needs id, event/trigger, and actions or transition.");
      continue;
    }
    checkQuestV2UnknownKeys(file, event, eventPointer, eventLocation, questV2EventKeys);
    const id = stringValue(event.id);
    checkQuestV2Id(file, id, `${eventPointer}/id`, `${eventLocation}.id`, "event id");
    if (id) {
      if (ids.has(id)) {
        questV2Error(file, `${eventPointer}/id`, `${eventLocation}.id`, `duplicate event id "${id}".`, "Use unique event ids in this event list.");
      }
      ids.add(id);
    }
    const trigger = firstString(event.event, event.trigger, event.type);
    if (!trigger) {
      questV2Error(file, `${eventPointer}/event`, `${eventLocation}.event`, "event trigger is required.", "Use a trigger event from quest-registry-metadata.json.");
    } else if (!questV2RegistryIndex.triggers.values.has(trigger)) {
      questV2Error(file, `${eventPointer}/event`, `${eventLocation}.event`, `unknown trigger event "${trigger}".`, "Use one of the trigger events in quest-registry-metadata.json.");
    }
    for (const stageRef of readValues(event, ["stage", "stages"])) {
      if (typeof stageRef === "string" && stageRef.trim() && stageIds.size > 0 && !stageIds.has(stageRef.trim())) {
        questV2Error(file, `${eventPointer}/stages`, `${eventLocation}.stages`, `event references missing stage "${stageRef.trim()}".`, "Reference a stage id from stages[].id.");
      }
    }
    checkQuestV2Conditions(file, event.conditions, `${eventPointer}/conditions`, `${eventLocation}.conditions`, questId, "quest module v2 event");
    checkQuestV2Actions(file, event.actions, `${eventPointer}/actions`, `${eventLocation}.actions`, questId, {
      liveContextWarningUsage: questTriggerEventsThatMayLackLiveIssuer.has(normalizedString(trigger)) ? "quest module v2 event action" : ""
    });
    checkQuestV2OptionalInteger(file, event, eventPointer, eventLocation, "cooldown_ticks", { min: 0 });
    checkQuestV2OptionalInteger(file, event, eventPointer, eventLocation, "cooldown_seconds", { min: 0 });
    checkQuestV2OptionalInteger(file, event, eventPointer, eventLocation, "cooldown_days", { min: 0 });
    checkQuestV2OptionalNumber(file, event, eventPointer, eventLocation, "radius", { min: 0 });
    checkQuestV2OptionalBoolean(file, event, eventPointer, eventLocation, "repeatable");
    const transition = readQuestV2WrapperTransition(file, event, eventPointer, eventLocation);
    if (!transition.empty) {
      transitions.push({ pointer: `${eventPointer}/transition`, location: `${eventLocation}.transition`, transition, stageId: localStageId || "" });
    }
  }
}

function checkQuestV2Rewards(file, rewards, pointer, location, questId) {
  if (rewards === undefined) {
    return;
  }
  if (!rewards || typeof rewards !== "object" || Array.isArray(rewards)) {
    questV2Error(file, pointer, location, "rewards must be an object.", "Use reward fields or rewards.actions.");
    return;
  }
  checkQuestV2UnknownKeys(file, rewards, pointer, location, questV2RewardsKeys);
  checkQuestV2Actions(file, rewards.actions, `${pointer}/actions`, `${location}.actions`, questId);
  checkQuestV2OptionalInteger(file, rewards, pointer, location, "experience");
  checkQuestV2OptionalInteger(file, rewards, pointer, location, "reputation");
  checkQuestV2OptionalInteger(file, rewards, pointer, location, "gossip_reputation");
  checkQuestV2OptionalString(file, rewards, pointer, location, "loot_table");
  checkQuestV2OptionalString(file, rewards, pointer, location, "memory_event");
  collectLootTableReferences(file, rewards, location, ["loot_table"], "quest module v2 rewards");
  checkMemoryTagReferences(file, rewards, location, ["memory_event"], "quest module v2 rewards");
}

function checkQuestV2Ui(file, ui, pointer, location) {
  if (ui === undefined) {
    return;
  }
  if (!ui || typeof ui !== "object" || Array.isArray(ui)) {
    questV2Error(file, pointer, location, "ui must be an object.", "Use tracker_text, title, or placeholders inside ui.");
    return;
  }
  checkQuestV2UnknownKeys(file, ui, pointer, location, questV2UiKeys);
  checkQuestV2OptionalString(file, ui, pointer, location, "title");
  checkQuestV2OptionalString(file, ui, pointer, location, "title_key");
  checkQuestV2OptionalString(file, ui, pointer, location, "description");
  checkQuestV2OptionalString(file, ui, pointer, location, "description_key");
  checkQuestV2OptionalString(file, ui, pointer, location, "tracker_text");
  checkQuestV2OptionalString(file, ui, pointer, location, "tracker_text_key");
  checkQuestV2OptionalString(file, ui, pointer, location, "tracker_complete_text");
  checkQuestV2OptionalString(file, ui, pointer, location, "tracker_complete_text_key");
  checkQuestV2OptionalBoolean(file, ui, pointer, location, "show_progress");
  checkQuestV2OptionalNumber(file, ui, pointer, location, "progress", { min: 0 });
  const placeholders = new Set();
  if (ui.placeholders !== undefined) {
    if (!ui.placeholders || typeof ui.placeholders !== "object" || Array.isArray(ui.placeholders)) {
      questV2Error(file, `${pointer}/placeholders`, `${location}.placeholders`, "ui.placeholders must be an object.", "Use placeholder names mapped to string expressions.");
    } else {
      for (const [name, value] of Object.entries(ui.placeholders)) {
        const placeholderPointer = `${pointer}/placeholders/${escapeJsonPointer(name)}`;
        if (!/^[a-zA-Z0-9_]+$/.test(name)) {
          questV2Error(file, placeholderPointer, `${location}.placeholders.${name}`, `placeholder name "${name}" is invalid.`, "Use letters, numbers, and underscores.");
        }
        if (typeof value !== "string") {
          questV2Error(file, placeholderPointer, `${location}.placeholders.${name}`, "placeholder value must be a string expression.", "Use a string expression for the placeholder source.");
        }
        placeholders.add(name);
      }
    }
  }
  for (const key of ["title", "description", "tracker_text", "tracker_complete_text"]) {
    for (const token of placeholderTokens(ui[key])) {
      if (!placeholders.has(token)) {
        questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `text references undefined UI placeholder "{${token}}".`, `Add ui.placeholders.${token} or remove the placeholder.`);
      }
    }
  }
}

function checkQuestV2Tracker(file, tracker, pointer, location) {
  if (tracker === undefined) return;
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)) {
    questV2Error(file, pointer, location, "objective tracker must be an object.", "Use text, complete_text, progress, or metadata fields.");
    return;
  }
  checkQuestV2UnknownKeys(file, tracker, pointer, location, questV2TrackerKeys);
  for (const key of ["text", "text_key", "complete_text", "complete_text_key"]) {
    checkQuestV2OptionalString(file, tracker, pointer, location, key);
  }
  checkQuestV2OptionalBoolean(file, tracker, pointer, location, "show_progress");
  checkQuestV2OptionalNumber(file, tracker, pointer, location, "progress", { min: 0 });
  checkQuestV2OptionalObject(file, tracker.metadata, `${pointer}/metadata`, `${location}.metadata`, "tracker metadata");
}

function checkQuestV2Conditions(file, conditions, pointer, location, questId, usage) {
  if (conditions === undefined) {
    return;
  }
  const entries = [];
  if (conditions && typeof conditions === "object" && !Array.isArray(conditions)) {
    entries.push({ value: conditions, pointer, location });
  } else if (Array.isArray(conditions)) {
    conditions.forEach((condition, index) => entries.push({
      value: condition,
      pointer: `${pointer}/${index}`,
      location: `${location}[${index}]`
    }));
  } else {
    questV2Error(file, pointer, location, "conditions must be an object or array.", "Use condition objects with a type field.");
    return;
  }
  for (const entry of entries) {
    const condition = entry.value;
    if (!condition || typeof condition !== "object" || Array.isArray(condition)) {
      questV2Error(file, entry.pointer, entry.location, "condition must be an object.", "Use a condition object with a type field.");
      continue;
    }
    const type = normalizedString(condition.type);
    if (!type) {
      questV2Error(file, `${entry.pointer}/type`, `${entry.location}.type`, "condition type is required.", "Use a condition type from quest-registry-metadata.json.");
      continue;
    }
    if (!questV2RegistryIndex.conditions.values.has(type)) {
      questV2Error(file, `${entry.pointer}/type`, `${entry.location}.type`, `unknown condition type "${condition.type}".`, "Use one of the condition types in quest-registry-metadata.json.");
    }
    if (conditionTypes.has(type) || questV2RegistryIndex.conditions.schemas.has(type)) {
      checkCondition(file, condition, entry.location, questId);
    }
    checkRegisteredFields(file, condition, entry.location, type, entry.pointer, "conditions");
    warnLiveOnlyQuestCondition(file, condition, entry.location, usage);
    if (["all", "all_of", "and", "any", "any_of", "or"].includes(type)) {
      checkQuestV2Conditions(file, condition.conditions, `${entry.pointer}/conditions`, `${entry.location}.conditions`, questId, usage);
    } else if (type === "not") {
      checkQuestV2Conditions(file, condition.condition, `${entry.pointer}/condition`, `${entry.location}.condition`, questId, usage);
    }
  }
}

function checkRegisteredFields(file, action, location, type, pointer = "", registry = "actions") {
  const schema = questV2RegistryIndex[registry].schemas.get(type);
  if (!schema) return;
  const kind = registry === "actions" ? "action" : "condition";
  const report = (field, message) => {
    if (pointer) questV2Error(file, `${pointer}/${field}`, `${location}.${field}`, message, `Use the registered ${kind} schema.`);
    else errors.push(`${relative(file)}: ${location}.${field} ${message}`);
  };
  if (schema.additionalProperties === false) {
    for (const field of Object.keys(action)) {
      if (!Object.hasOwn(schema.properties ?? {}, field)) report(field, `is not allowed by the registered ${kind} schema.`);
    }
  }
  for (const field of schema.required ?? []) {
    if (!Object.hasOwn(action, field)) report(field, `is required by the registered ${kind} schema.`);
  }
  for (const [field, rule] of Object.entries(schema.properties ?? {})) {
    if (!Object.hasOwn(action, field)) continue;
    const value = action[field];
    if (registry === "actions" && ["text", "title", "description", "label", "tracker_text", "tracker_complete_text"].includes(field)
        && isLocalizedReference(value)) continue;
    const matchesType = rule.type === "array" ? Array.isArray(value)
      : rule.type === "object" ? value !== null && typeof value === "object" && !Array.isArray(value)
      : rule.type === "integer" ? Number.isInteger(value)
      : rule.type === "null" ? value === null : typeof value === rule.type;
    if ((rule.type && !matchesType)
        || (rule.minItems !== undefined && (!Array.isArray(value) || value.length < rule.minItems))
        || (rule.maxItems !== undefined && (!Array.isArray(value) || value.length > rule.maxItems))
        || (rule.minimum !== undefined && (typeof value !== "number" || value < rule.minimum))
        || (rule.maximum !== undefined && (typeof value !== "number" || value > rule.maximum))
        || (rule.pattern && (typeof value !== "string" || !new RegExp(rule.pattern).test(value)))
        || (rule.maxLength !== undefined && (typeof value !== "string" || value.length > rule.maxLength))
        || (rule.enum && !rule.enum.includes(value))
        || (Object.hasOwn(rule, "const") && value !== rule.const)) {
      report(field, `does not match the registered ${kind} schema.`);
    }
  }
}

function checkQuestV2Actions(file, actions, pointer, location, questId, options = {}) {
  if (actions === undefined) {
    return;
  }
  if (!Array.isArray(actions)) {
    questV2Error(file, pointer, location, "actions must be an array.", "Use an array of action objects.");
    return;
  }
  for (const [index, action] of actions.entries()) {
    const actionPointer = options.singleAction ? pointer : `${pointer}/${index}`;
    const actionLocation = `${location}[${index}]`;
    if (!action || typeof action !== "object" || Array.isArray(action)) {
      questV2Error(file, actionPointer, actionLocation, "action must be an object.", "Use an action object with a type field.");
      continue;
    }
    const rawType = firstString(action.type, action.action);
    if (!rawType) {
      questV2Error(file, `${actionPointer}/type`, `${actionLocation}.type`, "action type is required.", "Use an action type from quest-registry-metadata.json.");
      continue;
    }
    const type = questV2RegistryIndex.actions.canonical.get(rawType) || rawType;
    if (!questV2RegistryIndex.actions.values.has(rawType)) {
      questV2Error(file, `${actionPointer}/type`, `${actionLocation}.type`, `unknown action type "${rawType}".`, "Use one of the action types in quest-registry-metadata.json.");
    }
    checkRegisteredFields(file, action, actionLocation, type, actionPointer);
    warnLiveContextQuestAction(file, actionLocation, type, options.liveContextWarningUsage);
    if (type === "quest") {
      checkStringList(file, action, actionLocation, ["quest", "quest_id", "id"], "quest id");
      checkStringValues(file, action, actionLocation, ["action"], dialogueTreeQuestActions, "quest action", { requireAny: true });
      const questIds = readValues(action, ["quest", "quest_id", "id"]);
      if (questIds.length === 0 && !questId) {
        questV2Error(file, actionPointer, actionLocation, "quest action must define quest or quest_id unless a default quest is available.", "Set quest to the target quest id.");
      }
      for (const targetQuestId of questIds.length === 0 && questId ? [questId] : questIds) {
        if (typeof targetQuestId === "string" && targetQuestId.trim()) {
          pendingQuestReferences.push({ file, location: actionLocation, id: targetQuestId.trim(), reason: "quest module v2 action" });
        }
      }
    }
    if (isQuestFactActionType(type)) {
      checkQuestFactAction(file, action, actionLocation, type, questId);
    }
    if (type === "loot") {
      checkOptionalString(file, action, actionLocation, "loot_table");
      if (!hasStringValues(action, ["loot_table"])) {
        questV2Error(file, `${actionPointer}/loot_table`, `${actionLocation}.loot_table`, "loot action requires loot_table.", "Set loot_table to a loot table resource id.");
      }
      collectLootTableReferences(file, action, actionLocation, ["loot_table"], "quest module v2 loot action");
    }
    if (type === "memory") {
      checkOptionalString(file, action, actionLocation, "memory_event");
      if (!hasStringValues(action, ["memory_event"])) {
        questV2Error(file, `${actionPointer}/memory_event`, `${actionLocation}.memory_event`, "memory action requires memory_event.", "Set memory_event to a village memory tag.");
      }
      checkMemoryTagReferences(file, action, actionLocation, ["memory_event"], "quest module v2 memory action");
    }
    if (type === "forced_dialogue") {
      checkStringList(file, action, actionLocation, ["forced_dialogue"], "forced dialogue id");
      for (const forcedDialogueId of readValues(action, ["forced_dialogue"])) {
        if (typeof forcedDialogueId === "string" && forcedDialogueId.trim()) {
          pendingForcedDialogueReferences.push({ file, location: actionLocation, id: forcedDialogueId.trim(), reason: "dialogue or trigger action" });
        }
      }
    }
    if (type === "notification") {
      checkOptionalString(file, action, actionLocation, "notification");
      checkOptionalString(file, action, actionLocation, "trigger");
      checkOptionalString(file, action, actionLocation, "text");
      if (!hasStringValues(action, ["notification", "trigger", "text"])) {
        questV2Error(file, actionPointer, actionLocation, "notification action must define notification, trigger, or text.", "Set notification to a notification trigger or provide inline text.");
      }
      collectNotificationTriggerReference(
        file,
        stringValue(action.notification) ? `${actionLocation}.notification` : `${actionLocation}.trigger`,
        stringValue(action.notification) || stringValue(action.trigger),
        "quest module v2 notification action"
      );
    }
  }
}

function checkQuestTimer(file, timer, pointer, location, model, stageId) {
  if (timer === undefined) return;
  if (!timer || typeof timer !== "object" || Array.isArray(timer)) {
    questV2Error(file, pointer, location, "timer must be an object.", "Declare duration_ticks and optional timer settings.");
    return;
  }
  if (!Number.isInteger(timer.duration_ticks) || timer.duration_ticks < 1 || timer.duration_ticks > 2147483647)
    questV2Error(file, pointer, location, "timer duration_ticks must be a positive bounded integer.", "Use 1 to 2147483647 ticks.");
  if (timer.warning_ticks !== undefined && (!Number.isInteger(timer.warning_ticks) || timer.warning_ticks < 0 || timer.warning_ticks >= timer.duration_ticks))
    questV2Error(file, pointer, location, "timer warning_ticks must be smaller than duration_ticks.", "Use a nonnegative warning interval.");
  if (timer.mode === "duration" && timer.timeout_stage !== undefined)
    questV2Error(file, pointer, location, "duration timers cannot have timeout_stage.", "Use a deadline timer for timeouts.");
  if (timer.timeout_stage === stageId)
    questV2Error(file, pointer, location, "timer timeout_stage must leave its current stage.", "Use a different stage or omit timeout_stage to fail the quest.");
  if (typeof timer.timeout_stage === "string")
    model.transitions.push({ pointer: `${pointer}/timeout_stage`, location: `${location}.timeout_stage`, stageId,
      transition: { stage: timer.timeout_stage } });
}

function validateQuestParallel(file, model) {
  const ownership = new Map();
  for (const parent of model.stages) {
    const branches = parent.raw.parallel;
    if (branches === undefined) continue;
    const pointer = `${parent.pointer}/parallel`;
    if (!Array.isArray(branches) || branches.length < 2 || branches.length > 16 || new Set(branches).size !== branches.length) {
      questV2Error(file, pointer, parent.location, "parallel requires 2 to 16 unique branch entries.", "Declare distinct stage ids.");
      continue;
    }
    const join = typeof parent.raw.next === "string" ? parent.raw.next : parent.raw.next?.stage;
    if (!model.stageIds.has(join)) {
      questV2Error(file, pointer, parent.location, "parallel requires a named next stage as its join.", "Declare the join stage.");
      continue;
    }
    for (const branch of branches) {
      if (!model.stageIds.has(branch) || branch === parent.id || branch === join) {
        questV2Error(file, pointer, parent.location, "parallel branch entry must be a stage separate from its parent and join.", "Declare a valid branch entry.");
        continue;
      }
      model.transitions.push({ pointer, location: parent.location, stageId: parent.id, transition: { stage: branch } });
      const pending = [branch], visited = new Set();
      let joined = false;
      while (pending.length) {
        const id = pending.pop();
        if (id === join) { joined = true; continue; }
        if (visited.has(id)) continue;
        visited.add(id);
        const stage = model.stages.find(candidate => candidate.id === id);
        if (!stage) continue;
        const owner = `${parent.id}/${branch}`;
        if (id === parent.id || ownership.has(id) && ownership.get(id) !== owner || stage.raw.parallel !== undefined) {
          questV2Error(file, pointer, parent.location, "parallel paths cannot overlap, reenter their parent, or contain nested forks.", "Keep paths separate until the join.");
          break;
        }
        ownership.set(id, owner);
        const targets = model.transitions.filter(edge => edge.stageId === id && edge.transition.stage).map(edge => edge.transition.stage);
        if (!targets.length) questV2Error(file, pointer, parent.location, "parallel path has no transition to its join.", "Connect every path stage to its next stage.");
        pending.push(...targets);
      }
      if (!joined) questV2Error(file, pointer, parent.location, "parallel path cannot reach its join.", "Connect the path to its join stage.");
    }
  }
}

function checkResponseTimer(file, owner, destinations, pointer, location) {
  const timer = owner?.response_timer;
  if (timer === undefined) return;
  const issues = validateConversationSchema(timer, conversationSchema.$defs.node.properties.response_timer);
  for (const issue of issues) questV2Error(file, `${pointer}/response_timer${issue.pointer}`, location, issue.message, "Declare duration_ticks and one response or next.");
  if (issues.length) return;
  if (!Array.isArray(owner.responses) || !owner.responses.length || owner.end === true)
    questV2Error(file, `${pointer}/response_timer`, location, "response timers require nonterminal response choices.", "Declare response choices.");
  if (timer.response && !(owner.responses ?? []).some(response => response.id === timer.response))
    questV2Error(file, `${pointer}/response_timer/response`, location, "unknown default response.", "Reference a response in this node.");
  if (timer.next && !destinations.has(timer.next))
    questV2Error(file, `${pointer}/response_timer/next`, location, "unknown silence destination.", "Reference an existing scene or node.");
}

function validateQuestV2Graph(file, data, model) {
  validateQuestParallel(file, model);
  for (const stage of model.stages) {
    const scenes = model.sceneIdsByStage.get(stage.id) ?? new Set();
    checkResponseTimer(file, stage.raw, scenes, stage.pointer, stage.location);
    for (const [index, scene] of (stage.raw.scenes ?? []).entries())
      checkResponseTimer(file, scene, scenes, `${stage.pointer}/scenes/${index}`, stage.location);
    for (const [slot, value] of Object.entries(stage.raw.dialogue ?? {}))
      checkResponseTimer(file, value, scenes, `${stage.pointer}/dialogue/${slot}`, stage.location);
  }
  const entryStage = stringValue(data.entry_stage);
  if (!entryStage) {
    questV2Error(file, "/entry_stage", "entry_stage", "entry_stage is required.", "Set entry_stage to the first stage id.");
  } else if (!model.stageIds.has(entryStage)) {
    questV2Error(file, "/entry_stage", "entry_stage", `entry_stage references missing stage "${entryStage}".`, "Set entry_stage to an id from stages[].id.");
  }
  for (const entry of model.transitions) {
    const transition = entry.transition;
    if (transition.stage && !model.stageIds.has(transition.stage)) {
      questV2Error(file, entry.pointer, entry.location, `transition references missing stage "${transition.stage}".`, "Reference a stage id from stages[].id.");
    }
    if (transition.scene && entry.stageId) {
      const scenes = model.sceneIdsByStage.get(entry.stageId) || new Set();
      if (!scenes.has(transition.scene)) {
        questV2Error(file, entry.pointer, entry.location, `transition references missing local scene "${transition.scene}".`, "Reference a scene id from the current stage.");
      }
    }
    if (transition.response && entry.stageId) {
      const responses = model.responseIdsByStage.get(entry.stageId) || new Set();
      if (responses.size > 0 && !responses.has(transition.response)) {
        questV2Warning(file, entry.pointer, entry.location, `transition references response "${transition.response}" that is not visible from this stage response list.`, "Move the response to this scene/stage or remove the transition.");
      }
    }
  }
  validateQuestV2Reachability(file, entryStage, model);
  warnQuestV2UnreachableScenes(file, model);
}

function validateQuestV2Reachability(file, entryStage, model) {
  if (!entryStage || !model.stageIds.has(entryStage)) {
    return;
  }
  const reachable = new Set();
  const pending = [entryStage];
  while (pending.length > 0) {
    const stageId = pending.pop();
    if (reachable.has(stageId)) {
      continue;
    }
    reachable.add(stageId);
    for (const entry of model.transitions) {
      if (entry.stageId === stageId && entry.transition.stage && model.stageIds.has(entry.transition.stage)) {
        pending.push(entry.transition.stage);
      }
    }
  }
  for (const stage of model.stages) {
    if (!reachable.has(stage.id)) {
      questV2Error(file, `${stage.pointer}/id`, `${stage.location}.id`, `stage "${stage.id}" is unreachable from entry_stage.`, "Link to it from an earlier transition or remove it.");
    }
  }
}

function warnQuestV2UnreachableScenes(file, model) {
  for (const stage of model.stages) {
    const scenes = model.sceneIdsByStage.get(stage.id) || new Set();
    if (scenes.size === 0) {
      continue;
    }
    const reachable = new Set();
    const queue = [];
    const rawScenes = new Map();
    if (Array.isArray(stage.raw.scenes)) {
      for (const scene of stage.raw.scenes) {
        const id = stringValue(scene?.id);
        if (id) {
          rawScenes.set(id, scene);
        }
      }
    }
    const enqueueScene = (scene) => {
      if (scene && scenes.has(scene) && !reachable.has(scene)) {
        reachable.add(scene);
        queue.push(scene);
      }
    };
    const enqueueResponseScenes = (responses, timer) => {
      enqueueScene(timer?.next);
      if (!Array.isArray(responses)) {
        return;
      }
      for (const response of responses) {
        if (!response || typeof response !== "object" || Array.isArray(response)) {
          continue;
        }
        enqueueScene(questV2ResponseSceneTarget(response));
      }
    };
    const dialogue = stage.raw.dialogue && typeof stage.raw.dialogue === "object" && !Array.isArray(stage.raw.dialogue) ? stage.raw.dialogue : {};
    for (const slot of Object.values(dialogue)) {
      const scene = firstString(slot?.scene, slot?.scene_ref);
      enqueueScene(scene);
      enqueueResponseScenes(slot?.responses, slot?.response_timer);
    }
    for (const entry of model.transitions) {
      if (entry.stageId === stage.id && entry.transition.scene && scenes.has(entry.transition.scene)) {
        enqueueScene(entry.transition.scene);
      }
    }
    while (queue.length > 0) {
      const sceneId = queue.shift();
      enqueueResponseScenes(rawScenes.get(sceneId)?.responses, rawScenes.get(sceneId)?.response_timer);
    }
    for (const scene of scenes) {
      if (!reachable.has(scene)) {
        questV2Warning(file, `${stage.pointer}/scenes`, `${stage.location}.scenes`, `scene "${scene}" is not reachable from any dialogue slot or scene transition.`, "Reference it from stage dialogue or a response transition, or remove it.");
      }
    }
  }
}

function warnQuestV2LifecycleCoverage(file, data, model) {
  const covered = new Set();
  for (const hook of ["on_start", "on_complete", "on_abandon", "on_expire", "on_fail"]) {
    if (data.lifecycle && typeof data.lifecycle === "object" && data.lifecycle[hook] !== undefined) {
      covered.add(hook);
    }
  }
  for (const stage of model.stages) {
    const dialogue = stage.raw.dialogue && typeof stage.raw.dialogue === "object" && !Array.isArray(stage.raw.dialogue) ? stage.raw.dialogue : {};
    for (const slot of Object.keys(dialogue)) {
      if (questV2LifecycleCoverageSlots.has(slot)) {
        covered.add(slot);
      }
    }
  }
  if (Array.isArray(data.discovery) && data.discovery.length) covered.add("on_start");
  if (model.stages.some((stage) => stage.raw.next?.complete === true)) covered.add("on_complete");
  if (!covered.has("on_start") && !covered.has("offer")) {
    questV2Warning(file, "/lifecycle", "lifecycle", "quest module v2 has no start lifecycle hook or offer dialogue slot.", "Add lifecycle.on_start or a dialogue.offer slot so authors can verify start behavior.");
  }
  if (!covered.has("on_complete") && !covered.has("turn_in")) {
    questV2Warning(file, "/lifecycle", "lifecycle", "quest module v2 has no completion lifecycle hook or turn_in dialogue slot.", "Add lifecycle.on_complete or a dialogue.turn_in slot so completion behavior is explicit.");
  }
}

function questV2ResponseSceneTarget(response) {
  if (!response || typeof response !== "object" || Array.isArray(response)) {
    return "";
  }
  if (response.transition && typeof response.transition === "object" && !Array.isArray(response.transition)) {
    return stringValue(response.transition.scene);
  }
  return stringValue(response.scene);
}

function readQuestV2Transition(file, value, pointer, location) {
  const empty = { empty: true, stage: "", scene: "", response: "", complete: false, abandon: false, fail: false };
  if (value === undefined || value === null) {
    return empty;
  }
  if (typeof value === "string") {
    if (!value.trim()) {
      questV2Error(file, pointer, location, "transition target stage must not be blank.", "Reference a stage id or remove the transition.");
      return empty;
    }
    return { ...empty, empty: false, stage: value.trim() };
  }
  let object = value;
  if (value && typeof value === "object" && !Array.isArray(value) && value.transition !== undefined) {
    return readQuestV2Transition(file, value.transition, `${pointer}/transition`, `${location}.transition`);
  }
  if (value && typeof value === "object" && !Array.isArray(value) && hasAnyKey(value, ["next", "stage", "scene", "response", "complete", "abandon", "fail"])) {
    object = {
      stage: firstDefined(value.next, value.stage),
      scene: value.scene,
      response: value.response,
      complete: value.complete,
      abandon: value.abandon,
      fail: value.fail
    };
  }
  if (!object || typeof object !== "object" || Array.isArray(object)) {
    questV2Error(file, pointer, location, "transition must be a string or object.", "Use a stage id string or one transition object.");
    return empty;
  }
  checkQuestV2UnknownKeys(file, object, pointer, location, questV2TransitionKeys);
  const transition = {
    empty: false,
    stage: stringValue(object.stage),
    scene: stringValue(object.scene),
    response: stringValue(object.response),
    complete: object.complete === true,
    abandon: object.abandon === true,
    fail: object.fail === true
  };
  const targetCount = [transition.stage, transition.scene, transition.response].filter(Boolean).length
    + (transition.complete ? 1 : 0)
    + (transition.abandon ? 1 : 0)
    + (transition.fail ? 1 : 0);
  if (targetCount === 0) {
    questV2Error(file, pointer, location, "transition must choose a target.", "Set exactly one of stage, scene, response, complete, abandon, or fail.");
    return empty;
  }
  if (targetCount > 1) {
    questV2Error(file, pointer, location, "transition must choose only one target.", "Keep exactly one transition target.");
  }
  return transition;
}

function readQuestV2WrapperTransition(file, object, pointer, location) {
  if (!object || typeof object !== "object" || Array.isArray(object)) {
    return emptyQuestV2Transition();
  }
  if (!Object.hasOwn(object, "transition") && !hasAnyKey(object, ["next", "stage", "scene", "response", "complete", "abandon", "fail"])) {
    return emptyQuestV2Transition();
  }
  return readQuestV2Transition(file, object, pointer, location);
}

function emptyQuestV2Transition() {
  return { empty: true, stage: "", scene: "", response: "", complete: false, abandon: false, fail: false };
}

function transitionChangesStage(transition) {
  return transition && (transition.stage || transition.complete || transition.abandon || transition.fail);
}

function isQuestV2StageChangingAction(action) {
  if (!action || typeof action !== "object" || Array.isArray(action)) {
    return false;
  }
  const type = questV2RegistryIndex.actions.canonical.get(firstString(action.type, action.action)) || firstString(action.type, action.action);
  if (type === "quest_transition") {
    return true;
  }
  if (type === "set_variable") {
    const key = firstString(action.variable, action.key, action.fact);
    return Object.hasOwn(action, "stage") || key === "stage";
  }
  if (type !== "quest") {
    return false;
  }
  return ["start", "turn_in", "abandon", "block"].includes(normalizedString(action.action));
}

function readQuestV2ObjectiveReferences(value) {
  if (value === undefined) {
    return [];
  }
  if (typeof value === "string") {
    return value.trim() ? [value.trim()] : [];
  }
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const ref = firstString(
      value.objective,
      value.objective_id,
      ["objective", "objectives"].includes(normalizedString(value.type)) ? value.id : ""
    );
    return ref ? [ref] : [];
  }
  if (!Array.isArray(value)) {
    return [];
  }
  const refs = [];
  for (const entry of value) {
    if (typeof entry === "string" && entry.trim()) {
      refs.push(entry.trim());
    } else if (entry && typeof entry === "object" && !Array.isArray(entry)) {
      const ref = firstString(
        entry.objective,
        entry.objective_id,
        ["objective", "objectives"].includes(normalizedString(entry.type)) ? entry.id : ""
      );
      if (ref) {
        refs.push(ref);
      }
    }
  }
  return refs;
}

function questStructureKeys(definition) {
  const schema = definition === "root" ? questV2Schema : questV2Schema?.$defs?.[definition];
  if (!schema?.properties) throw new Error(`Quest schema is missing structure "${definition}".`);
  return new Set(Object.keys(schema.properties));
}

function checkQuestV2UnknownKeys(file, object, pointer, location, allowedKeys) {
  if (!object || typeof object !== "object" || Array.isArray(object)) {
    return;
  }
  for (const key of Object.keys(object)) {
    if (!allowedKeys.has(key)) {
      questV2Error(file, childJsonPointer(pointer, key), `${location}.${key}`, `unsupported field "${key}".`, "Remove the field or move it to a supported quest module v2 location.");
    }
  }
}

function checkQuestV2Id(file, id, pointer, location, label) {
  if (!id) {
    questV2Error(file, pointer, location, `${label} is required.`, "Add a stable non-empty id.");
    return;
  }
  if (!questV2IdPattern.test(id)) {
    questV2Error(file, pointer, location, `${label} "${id}" uses an invalid or reserved id.`, "Use letters, numbers, underscores, dots, colons, or hyphens, and avoid __generated or vr$ prefixes.");
  }
}

function checkQuestV2OptionalString(file, object, pointer, location, key) {
  const value = object?.[key];
  if (value === undefined || typeof value === "string") return;
  const localizedFields = new Set(["title", "description", "text", "label", "tracker_text", "tracker_complete_text", "complete_text"]);
  if (localizedFields.has(key) && isLocalizedReference(value)) return;
  questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be a string${localizedFields.has(key) ? " or localized reference" : ""}.`, "Use a supported value or remove the field.");
}

function checkQuestV2StringArray(file, value, pointer, location, label) {
  if (value === undefined) {
    return;
  }
  if (typeof value === "string") {
    if (!value.trim()) {
      questV2Error(file, pointer, location, `${label} must not be blank.`, "Use a nonblank string or remove it.");
    }
    return;
  }
  if (!Array.isArray(value)) {
    if (label.includes("line") && isLocalizedReference(value)) return;
    questV2Error(file, pointer, location, `${label} must be a string, localized reference, or array of strings.`, "Use supported text values.");
    return;
  }
  value.forEach((entry, index) => {
    if (typeof entry !== "string" || !entry.trim()) {
      questV2Error(file, `${pointer}/${index}`, `${location}[${index}]`, `${label} must be a nonblank string.`, "Use a nonblank string.");
    }
  });
}

function checkQuestV2OptionalBoolean(file, object, pointer, location, key) {
  if (object?.[key] !== undefined && typeof object[key] !== "boolean") {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be a boolean.`, "Use true or false.");
  }
}

function checkQuestV2OptionalInteger(file, object, pointer, location, key, options = {}) {
  if (object?.[key] === undefined) {
    return;
  }
  if (!Number.isInteger(object[key])) {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be an integer.`, "Use a whole number.");
    return;
  }
  if (options.min !== undefined && object[key] < options.min) {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be at least ${options.min}.`, "Raise the value or remove it.");
  }
}

function checkQuestV2OptionalNumber(file, object, pointer, location, key, options = {}) {
  if (object?.[key] === undefined) {
    return;
  }
  if (typeof object[key] !== "number" || Number.isNaN(object[key])) {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be a number.`, "Use a numeric value.");
    return;
  }
  if (options.min !== undefined && object[key] < options.min) {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be at least ${options.min}.`, "Raise the value or remove it.");
  }
}

function checkQuestV2OptionalObject(file, value, pointer, location, label) {
  if (value !== undefined && (!value || typeof value !== "object" || Array.isArray(value))) {
    questV2Error(file, pointer, location, `${label} must be an object.`, "Use an object or remove the field.");
  }
}

function questV2Error(file, pointer, location, message, suggestion) {
  errors.push(questV2Issue(file, "error", pointer, location, message, suggestion));
}

function questV2Warning(file, pointer, location, message, suggestion) {
  warnings.push(questV2Issue(file, "warning", pointer, location, message, suggestion));
}

function questV2Issue(file, severity, pointer, location, message, suggestion) {
  return {
    severity,
    resource: relative(file),
    path: location || pointerToPath(pointer),
    pointer: pointer || "",
    message,
    suggestion
  };
}

function questV2StageIds(stages) {
  if (!Array.isArray(stages)) {
    return new Set();
  }
  return new Set(stages
    .filter((stage) => stage && typeof stage === "object" && !Array.isArray(stage))
    .map((stage) => stringValue(stage.id))
    .filter(Boolean));
}

function questRegistryIndex(metadata) {
  const empty = () => ({ values: new Set(), canonical: new Map(), capabilities: new Map(), schemas: new Map() });
  const index = {
    conditions: empty(),
    actions: empty(),
    objectives: empty(),
    triggers: empty(),
    providers: empty()
  };
  const registries = metadata?.registries;
  if (!registries || typeof registries !== "object" || Array.isArray(registries)) {
    return index;
  }
  addRegistryAliases(index.conditions, registries.conditions);
  addRegistryAliases(index.actions, registries.actions);
  addRegistryAliases(index.objectives, registries.objectives);
  addRegistryAliases(index.triggers, registries.triggers, "event");
  addRegistryAliases(index.providers, registries.providers);
  for (const provider of Array.isArray(registries.providers) ? registries.providers : []) {
    const id = stringValue(provider?.id);
    if (id) {
      index.providers.capabilities.set(id, new Set(readValues(provider, ["capabilities"]).filter((value) => typeof value === "string")));
    }
  }
  return index;
}

function addRegistryAliases(target, entries, canonicalKey = "id") {
  if (!Array.isArray(entries)) {
    return;
  }
  for (const entry of entries) {
    const canonical = stringValue(entry?.[canonicalKey]) || stringValue(entry?.id);
    if (!canonical) {
      continue;
    }
    target.values.add(canonical);
    target.canonical.set(canonical, canonical);
    if (entry.schema) target.schemas.set(canonical, entry.schema);
    for (const alias of readValues(entry, ["aliases"])) {
      if (typeof alias === "string" && alias.trim()) {
        target.values.add(alias.trim());
        target.canonical.set(alias.trim(), canonical);
      }
    }
  }
}

function pointerToPath(pointer) {
  if (!pointer || pointer === "/") {
    return "root";
  }
  return pointer.split("/").slice(1).reduce((pathText, part) => {
    const value = unescapeJsonPointer(part);
    return /^\d+$/.test(value) ? `${pathText}[${value}]` : `${pathText}.${value}`;
  }, "root");
}

function childJsonPointer(pointer, key) {
  const escaped = escapeJsonPointer(key);
  return pointer ? `${pointer}/${escaped}` : `/${escaped}`;
}

function escapeJsonPointer(value) {
  return String(value).replaceAll("~", "~0").replaceAll("/", "~1");
}

function unescapeJsonPointer(value) {
  return String(value).replaceAll("~1", "/").replaceAll("~0", "~");
}

function placeholderTokens(value) {
  if (typeof value !== "string") {
    return [];
  }
  return [...value.matchAll(textTokenPattern)].map((match) => match[1]);
}

function isResourceLocation(value) {
  return typeof value === "string" && resourceLocationPattern.test(value.trim());
}

function firstString(...values) {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

function firstDefined(...values) {
  for (const value of values) {
    if (value !== undefined) {
      return value;
    }
  }
  return undefined;
}

function hasAnyKey(object, keys) {
  return Boolean(object && typeof object === "object" && !Array.isArray(object) && keys.some((key) => Object.hasOwn(object, key)));
}

function checkDialogueTree(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: dialogue tree root must be an object.`);
    return;
  }
  const defaultQuestId = dialogueTreeDefaultQuestId(file, data);
  const treeId = dialogueTreeIdForFile(file, data);
  checkUnknownObjectKeys(file, data, "root", dialogueTreeRootKeys);
  checkOptionalBoolean(file, data, "root", "replace");
  checkOptionalBoolean(file, data, "root", "remove");
  checkOptionalString(file, data, "root", "id");
  checkResourceIdValues(file, data, "root", ["id"], "dialogue tree resource id");
  checkDialogueTreeDisplay(file, data.display, "display");
  checkDialogueMetadata(file, data, "root");
  checkDialogueTreeMetadataConsistency(file, data, "root");
  checkConditions(file, data, "root", defaultQuestId);
  if (data.remove === true || (data.replace === true && isControlOnly(data, ["replace", "metadata"]))) {
    return;
  }

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
    checkDialogueOptionNetworkBounds(
      file,
      `entries[${index}]`,
      `dt:${treeId}:${stringValue(entry.id) || `entry_${index}`}`,
      stringValue(entry.label));
    checkUnknownObjectKeys(file, entry, `entries[${index}]`, dialogueTreeEntryKeys);
    checkDialogueTreeEntryFields(file, entry, `entries[${index}]`);
    warnInvisibleDialogueTreeEntry(file, entry, `entries[${index}]`);
    checkDialogueMetadata(file, entry, `entries[${index}]`);
    checkConditions(file, entry, `entries[${index}]`, stringValue(metadataObject(entry).quest) || defaultQuestId);
    checkStringList(file, entry, `entries[${index}]`, ["professions"], "profession id");
    checkProfessionReferences(file, entry, `entries[${index}]`, ["professions"], "dialogue tree entry");
    warnQuestDialogueTreeLifecycleEntryState(file, entry, `entries[${index}]`, defaultQuestId);
  }
  warnQuestDialogueTreeLifecycleEntryCoverage(file, entries, defaultQuestId);

  const nodes = dialogueTreeNodes(data.nodes);
  if (nodes.length === 0) {
    errors.push(`${relative(file)}: dialogue tree must define nodes.`);
    return;
  }

  const nodeIds = new Set(nodes.map((node) => node.id).filter(Boolean));
  const nodeById = new Map(nodes
    .filter((node) => typeof node.id === "string" && node.id.trim())
    .map((node) => [node.id, node]));
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
    const rawNode = node.raw ?? node;
    const nodeQuestId = stringValue(metadataObject(rawNode).quest) || defaultQuestId;
    checkUnknownObjectKeys(file, rawNode, location, dialogueTreeNodeKeys);
    checkDialogueTreeNodeFields(file, rawNode, location);
    checkResponseTimer(file, rawNode, nodeIds, `/nodes/${node.id}`, location);
    warnTerminalDialogueTreeNode(file, rawNode, location);
    checkDialogueMetadata(file, rawNode, location);
    checkConditions(file, rawNode, location, nodeQuestId);
    checkDialogueTreeActions(file, rawNode.actions, `${location}.actions`, nodeQuestId);
    if (Array.isArray(rawNode.responses)) {
      checkIds(file, rawNode.responses, "dialogue tree response");
      for (const [responseIndex, response] of rawNode.responses.entries()) {
        const responseLocation = `${location}.responses[${responseIndex}]`;
        if (!response || typeof response !== "object" || Array.isArray(response)) {
          errors.push(`${relative(file)}: ${responseLocation} must be an object.`);
          continue;
        }
        checkDialogueOptionNetworkBounds(
          file,
          responseLocation,
          `dr:${treeId}:${stringValue(response.id) || `response_${responseIndex}`}`,
          stringValue(response.label));
        checkUnknownObjectKeys(file, response, responseLocation, dialogueTreeResponseKeys);
        checkDialogueTreeResponseFields(file, response, responseLocation);
        warnInvisibleDialogueTreeResponse(file, response, responseLocation);
        warnDialogueTreeResponseFlow(file, response, responseLocation);
        checkDialogueMetadata(file, response, responseLocation);
        const responseQuestId = stringValue(metadataObject(response).quest) || nodeQuestId;
        checkConditions(file, response, responseLocation, responseQuestId);
        checkDialogueTreeActions(file, response.actions, `${responseLocation}.actions`, responseQuestId);
        if (typeof response.next === "string" && response.next.trim() && !nodeIds.has(response.next)) {
          errors.push(`${relative(file)}: ${responseLocation}.next references unknown node "${response.next}".`);
        }
      }
    } else if (rawNode.responses !== undefined) {
      errors.push(`${relative(file)}: ${location}.responses must be an array.`);
    }
  }
  checkUnreachableDialogueTreeNodes(file, nodes, entries, nodeIds);
  warnQuestDialogueTreeLifecycleActionReachability(file, entries, nodeById, defaultQuestId);
}

function checkDialogueTreeEntryFields(file, entry, location) {
  checkOptionalString(file, entry, location, "id");
  checkOptionalString(file, entry, location, "label");
  checkOptionalString(file, entry, location, "start");
  checkStringValues(file, entry, location, ["request"], dialogueRequestTypes, "dialogue request type");
  checkStringValues(file, entry, location, ["disposition", "dispositions"], dialogueDispositions, "dialogue disposition");
  for (const key of ["show_for_adults", "show_for_babies", "force_camera_towards_villager"]) {
    checkOptionalBoolean(file, entry, location, key);
  }
  checkOptionalInteger(file, entry, location, "order");
}

function checkDialogueCamera(file, value, location) {
  if (value?.camera === undefined) return;
  for (const issue of validateConversationSchema(value.camera, questV2Schema.$defs.scene.properties.camera))
    errors.push(`${relative(file)}: ${location}.camera${issue.pointer}: ${issue.message}`);
}

function checkDialogueExchanges(file, value, location) {
  checkDialogueCamera(file, value, location);
  if (value.exchanges === undefined) return;
  const schema = questV2Schema.$defs.scene.properties.exchanges;
  for (const issue of validateConversationSchema(value.exchanges, schema)) {
    errors.push(`${relative(file)}: ${location}.exchanges${issue.pointer}: ${issue.message}`);
  }
}

function checkDialogueTreeNodeFields(file, node, location) {
  checkDialogueExchanges(file, node, location);
  checkOptionalString(file, node, location, "id");
  checkStringList(file, node, location, ["lines"], "dialogue tree node line");
  checkOptionalString(file, node, location, "text");
  checkOptionalBoolean(file, node, location, "end");
}

function checkDialogueTreeResponseFields(file, response, location) {
  checkChoiceAvailability(file, response, location);
  checkDialogueExchanges(file, response, location);
  checkOptionalString(file, response, location, "id");
  checkOptionalString(file, response, location, "label");
  checkOptionalString(file, response, location, "next");
  checkStringValues(file, response, location, ["request"], dialogueRequestTypes, "dialogue request type");
  checkStringList(file, response, location, ["lines"], "dialogue tree response line");
  checkOptionalString(file, response, location, "text");
  checkOptionalBoolean(file, response, location, "end");
  checkOptionalInteger(file, response, location, "order");
}

function warnInvisibleDialogueTreeEntry(file, entry, location) {
  if (entry.label === undefined && entry.label_key === undefined) {
    warnings.push(`${relative(file)}: ${location}.label is missing; dialogue tree entry will not appear as a player option.`);
  }
  if (entry.show_for_adults === false && entry.show_for_babies === false) {
    warnings.push(`${relative(file)}: ${location} disables both adult and baby audiences and can never match.`);
  }
}

function warnInvisibleDialogueTreeResponse(file, response, location) {
  if (response.label === undefined && response.label_key === undefined) {
    warnings.push(`${relative(file)}: ${location}.label is missing; dialogue tree response will not appear as an active option.`);
  }
}

function warnTerminalDialogueTreeNode(file, node, location) {
  if (node.end === true && Array.isArray(node.responses) && node.responses.length > 0) {
    warnings.push(`${relative(file)}: ${location} is marked end=true; authored responses will never be offered.`);
  }
}

function warnDialogueTreeResponseFlow(file, response, location) {
  if (response.end === true && stringValue(response.next)) {
    warnings.push(`${relative(file)}: ${location}.next is ignored because end=true closes the conversation.`);
  }
}

function checkDialogueTreeDisplay(file, display, location) {
  if (display === undefined) {
    return;
  }
  if (!display || typeof display !== "object" || Array.isArray(display)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, display, location, dialogueTreeDisplayKeys);
  checkOptionalString(file, display, location, "title");
  checkOptionalString(file, display, location, "description");
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
    .map(([id, node]) => ({ ...node, id: node.id ?? id, location: `nodes.${id}`, raw: node }));
}

function checkUnreachableDialogueTreeNodes(file, nodes, entries, nodeIds) {
  const nodeById = new Map(nodes
    .filter((node) => typeof node.id === "string" && node.id.trim())
    .map((node) => [node.id, node]));
  const pending = [];
  for (const entry of entries) {
    if (!structurallySelectableDialogueTreeEntry(entry)) {
      continue;
    }
    const start = stringValue(entry?.start);
    if (start && nodeIds.has(start)) {
      pending.push(start);
    }
  }
  if (pending.length === 0) {
    return;
  }

  const reachable = new Set();
  while (pending.length > 0) {
    const nodeId = pending.pop();
    if (reachable.has(nodeId)) {
      continue;
    }
    reachable.add(nodeId);
    const node = nodeById.get(nodeId);
    const rawNode = node?.raw ?? node;
    if (!rawNode || !dialogueTreeNodeCanOfferResponses(rawNode)) {
      continue;
    }
    const silence = stringValue(rawNode.response_timer?.next);
    if (silence && nodeIds.has(silence) && !reachable.has(silence)) pending.push(silence);
    for (const response of rawNode.responses) {
      if (!structurallySelectableDialogueTreeResponse(response)) {
        continue;
      }
      const next = stringValue(response?.next);
      if (next && nodeIds.has(next) && !reachable.has(next)) {
        pending.push(next);
      }
    }
  }

  for (const node of nodes) {
    if (typeof node.id === "string" && node.id.trim() && !reachable.has(node.id)) {
      warnings.push(`${relative(file)}: ${node.location} is not reachable from any dialogue tree entry start.`);
    }
  }
}

function warnQuestDialogueTreeLifecycleEntryState(file, entry, location, defaultQuestId) {
  if (!defaultQuestId || !isQuestDialogueTreeFile(file)) {
    return;
  }
  const expectedStates = questDialogueTreeLifecycleStates.get(stringValue(entry?.id));
  if (!expectedStates) {
    return;
  }
  if (!conditionListHasQuestState(entry.conditions, expectedStates, defaultQuestId)) {
    warnings.push(`${relative(file)}: ${location} has lifecycle entry id "${entry.id}" without a matching quest state condition.`);
  }
}

function warnQuestDialogueTreeLifecycleEntryCoverage(file, entries, defaultQuestId) {
  if (!defaultQuestId || !isQuestDialogueTreeFile(file)) {
    return;
  }
  const entryIds = new Set(entries
    .filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry))
    .map((entry) => stringValue(entry.id))
    .filter(Boolean));
  for (const entryId of questDialogueTreeLifecycleStates.keys()) {
    if (!entryIds.has(entryId)) {
      warnings.push(`${relative(file)}: quest dialogue tree is missing lifecycle entry "${entryId}".`);
    }
  }
}

function structurallySelectableDialogueTreeEntry(entry) {
  return entry
    && typeof entry === "object"
    && !Array.isArray(entry)
    && stringValue(entry.label)
    && !(entry.show_for_adults === false && entry.show_for_babies === false);
}

function structurallySelectableDialogueTreeResponse(response) {
  return response
    && typeof response === "object"
    && !Array.isArray(response)
    && stringValue(response.label);
}

function dialogueTreeNodeCanOfferResponses(node) {
  return node?.end !== true && Array.isArray(node?.responses);
}

function conditionListHasQuestState(conditions, expectedStates, defaultQuestId) {
  if (!Array.isArray(conditions)) {
    return false;
  }
  return conditions.some((condition) => conditionHasQuestState(condition, expectedStates, defaultQuestId));
}

function conditionHasQuestState(condition, expectedStates, defaultQuestId) {
  if (!condition || typeof condition !== "object" || Array.isArray(condition)) {
    return false;
  }
  const type = normalizedString(condition.type);
  if (["all", "all_of", "and", "any", "any_of", "or"].includes(type)) {
    return conditionListHasQuestState(condition.conditions, expectedStates, defaultQuestId);
  }
  if (type === "not") {
    return false;
  }
  if (type !== "quest") {
    return false;
  }

  const questIds = readValues(condition, ["quest", "quest_id", "id"])
    .filter((value) => typeof value === "string" && value.trim())
    .map((value) => value.trim());
  if (questIds.length > 0 && !questIds.includes(defaultQuestId)) {
    return false;
  }
  return readValues(condition, ["state", "states"])
    .some((state) => typeof state === "string" && expectedStates.has(normalizedString(state)));
}

function warnQuestDialogueTreeLifecycleActionReachability(file, entries, nodeById, defaultQuestId) {
  if (!defaultQuestId || !isQuestDialogueTreeFile(file)) {
    return;
  }
  for (const [index, entry] of entries.entries()) {
    const entryId = stringValue(entry?.id);
    const expectedActions = questDialogueTreeLifecycleActions.get(entryId);
    if (!expectedActions) {
      continue;
    }
    const start = stringValue(entry?.start);
    if (!start || !nodeById.has(start)) {
      continue;
    }
    if (!hasReachableQuestAction(nodeById, start, expectedActions, defaultQuestId)) {
      warnings.push(`${relative(file)}: entries[${index}] lifecycle entry "${entryId}" has no reachable ${lifecycleActionDescription(entryId)} quest action.`);
    }
  }
}

function hasReachableQuestAction(nodeById, startNodeId, expectedActions, defaultQuestId) {
  const pending = [startNodeId];
  const visited = new Set();
  while (pending.length > 0) {
    const nodeId = pending.pop();
    if (visited.has(nodeId)) {
      continue;
    }
    visited.add(nodeId);
    const node = nodeById.get(nodeId);
    const rawNode = node?.raw ?? node;
    if (!rawNode) {
      continue;
    }
    if (actionsIncludeQuestLifecycleAction(rawNode.actions, expectedActions, defaultQuestId)) {
      return true;
    }
    if (!dialogueTreeNodeCanOfferResponses(rawNode)) {
      continue;
    }
    for (const response of rawNode.responses) {
      if (!structurallySelectableDialogueTreeResponse(response)) {
        continue;
      }
      if (actionsIncludeQuestLifecycleAction(response?.actions, expectedActions, defaultQuestId)) {
        return true;
      }
      const next = stringValue(response?.next);
      if (next && nodeById.has(next) && !visited.has(next)) {
        pending.push(next);
      }
    }
  }
  return false;
}

function actionsIncludeQuestLifecycleAction(actions, expectedActions, defaultQuestId) {
  if (!Array.isArray(actions)) {
    return false;
  }
  return actions.some((action) => actionTargetsQuest(action, defaultQuestId)
    && actionType(action, defaultQuestId) === "quest"
    && expectedActions.has(normalizedString(action.action)));
}

function actionTargetsQuest(action, defaultQuestId) {
  if (!action || typeof action !== "object" || Array.isArray(action)) {
    return false;
  }
  const questIds = readValues(action, ["quest", "quest_id", "id"])
    .filter((value) => typeof value === "string" && value.trim())
    .map((value) => value.trim());
  return questIds.length === 0 || questIds.includes(defaultQuestId);
}

function lifecycleActionDescription(entryId) {
  if (entryId === "offer") {
    return "start";
  }
  if (entryId === "turn_in") {
    return "turn-in";
  }
  return "reminder";
}

function checkDialogueTreeActions(file, actions, location, defaultQuestId = "", options = {}) {
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
    const schema = questV2RegistryIndex.actions.schemas.get(actionType(action, defaultQuestId));
    for (const key of Object.keys(action)) {
      if (!dialogueTreeActionKeys.has(key) && !Object.hasOwn(schema?.properties ?? {}, key)) {
        errors.push(`${relative(file)}: ${actionLocation}.${key} is not a supported dialogue action field.`);
      }
    }
    const type = actionType(action, defaultQuestId);
    if (!dialogueTreeActionTypes.has(type) && !questV2RegistryIndex.actions.schemas.has(type)) {
      errors.push(`${relative(file)}: ${actionLocation}.type must be one of ${[...dialogueTreeActionTypes, ...questV2RegistryIndex.actions.schemas.keys()].join(", ")}.`);
    }
    checkRegisteredFields(file, action, actionLocation, type);
    warnLiveContextQuestAction(file, actionLocation, type, options.liveContextWarningUsage);
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
    if (isQuestFactActionType(type)) {
      checkQuestFactAction(file, action, actionLocation, type, defaultQuestId);
    }
    if (type === "loot") {
      checkOptionalString(file, action, actionLocation, "loot_table");
      if (!hasStringValues(action, ["loot_table"])) {
        errors.push(`${relative(file)}: ${actionLocation}.loot_table is required for a loot action.`);
      }
      collectLootTableReferences(file, action, actionLocation, ["loot_table"], "dialogue or trigger loot action");
    }
    if (type === "memory") {
      checkOptionalString(file, action, actionLocation, "memory_event");
      if (!hasStringValues(action, ["memory_event"])) {
        errors.push(`${relative(file)}: ${actionLocation}.memory_event is required for a memory action.`);
      }
      checkMemoryTagReferences(file, action, actionLocation, ["memory_event"], "dialogue or trigger memory action");
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
    if (type === "notification") {
      checkOptionalString(file, action, actionLocation, "notification");
      checkOptionalString(file, action, actionLocation, "trigger");
      checkOptionalString(file, action, actionLocation, "text");
      if (!hasStringValues(action, ["notification", "trigger", "text"])) {
        errors.push(`${relative(file)}: ${actionLocation} must define notification, trigger, or text for a notification action.`);
      }
      collectNotificationTriggerReference(
        file,
        stringValue(action.notification) ? `${actionLocation}.notification` : `${actionLocation}.trigger`,
        stringValue(action.notification) || stringValue(action.trigger),
        "dialogue or trigger notification action"
      );
    }
    if (action.lines !== undefined && (!action.lines || typeof action.lines !== "object" || Array.isArray(action.lines))) {
      errors.push(`${relative(file)}: ${actionLocation}.lines must be an object keyed by action status.`);
    }
  }
}

function warnLiveContextQuestAction(file, location, type, usage) {
  if (!usage || !questLiveContextActionTypes.has(type)) {
    return;
  }
  warnings.push(`${relative(file)}: ${location} uses ${type} in ${usage}; this action needs live issuer context and will wait if the quest issuer is unloaded.`);
}

function checkForcedDialogue(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: forced dialogue root must be an object.`);
    return;
  }
  checkOptionalBoolean(file, data, "root", "replace");
  checkOptionalBoolean(file, data, "root", "remove");
  checkDialogueMetadata(file, data, "root");
  if (data.remove === true || (data.replace === true && isControlOnly(data, ["replace", "metadata"]))) {
    return;
  }
  const defaultQuestId = stringValue(metadataObject(data).quest);
  const rootMessagePrefix = readForcedDialogueMessagePrefix(data);
  const entries = entriesFor(data);
  checkIds(file, entries, "forced dialogue entry");
  for (const [entryIndex, entry] of entries.entries()) {
    const entryLocation = `entries[${entryIndex}]`;
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      errors.push(`${relative(file)}: ${entryLocation} must be an object.`);
      continue;
    }
    checkOptionalString(file, entry, entryLocation, "trigger");
    checkOptionalString(file, entry, entryLocation, "event");
    const trigger = stringValue(entry.trigger) || stringValue(entry.event);
    if (!trigger) {
      errors.push(`${relative(file)}: ${entryLocation}.trigger is required for a forced dialogue entry.`);
    } else if (!forcedDialogueTriggers.has(normalizedString(trigger))) {
      errors.push(`${relative(file)}: ${entryLocation}.trigger has unsupported forced dialogue trigger "${trigger}".`);
    }
    if (Object.hasOwn(entry, "event") && !Object.hasOwn(entry, "trigger")) {
      warnings.push(`${relative(file)}: entries[${entryIndex}].event is a legacy alias for trigger; prefer trigger in new data.`);
    }
    const entryId = stringValue(entry?.id) || forcedDialogueFallbackEntryId(file, entryIndex);
    const entryMessagePrefix = readForcedDialogueMessagePrefix(entry, childMessagePrefix(rootMessagePrefix, entryId));
    checkForcedDialogueEntryText(file, entry, entryLocation, entryMessagePrefix);
    checkDialogueMetadata(file, entry, entryLocation);
    const entryQuestId = stringValue(metadataObject(entry).quest) || defaultQuestId;
    checkForcedDialogueOptions(file, entry.options, `${entryLocation}.options`, entryQuestId, entryMessagePrefix);
    checkForcedDialogueOptions(file, entry.leave_options, `${entryLocation}.leave_options`, entryQuestId, entryMessagePrefix, "leave");
    checkForcedDialogueOption(
      file,
      entry.leave_option,
      `${entryLocation}.leave_option`,
      entryQuestId,
      readForcedDialogueMessagePrefix(entry.leave_option, childMessagePrefix(entryMessagePrefix, "leave"))
    );
  }
}

function checkQuestFactAction(file, action, location, type, defaultQuestId = "") {
  checkStringList(file, action, location, ["quest", "quest_id", "id"], "quest id");
  checkStringValues(file, action, location, ["scope", "fact_scope"], questFactScopes, "quest fact scope");
  const questIds = readValues(action, ["quest", "quest_id", "id"]);
  for (const questId of questIds.length === 0 && defaultQuestId ? [defaultQuestId] : questIds) {
    if (typeof questId === "string" && questId.trim()) {
      pendingQuestReferences.push({
        file,
        location,
        id: questId.trim(),
        reason: "quest fact action"
      });
    }
  }

  if (type === "set_tag") {
    checkStringList(file, action, location, ["set_tag", "fact_tag", "quest_tag", "tag"], "quest fact tag");
    checkResourceIdValues(file, action, location, ["set_tag", "fact_tag", "quest_tag", "tag"], "quest fact tag");
    if (readValues(action, ["set_tag", "fact_tag", "quest_tag", "tag"]).length === 0) {
      errors.push(`${relative(file)}: ${location} must define set_tag, fact_tag, quest_tag, or tag for a set_tag action.`);
    }
  } else if (type === "clear_tag") {
    checkStringList(file, action, location, ["clear_tag", "fact_tag", "quest_tag", "tag"], "quest fact tag");
    checkResourceIdValues(file, action, location, ["clear_tag", "fact_tag", "quest_tag", "tag"], "quest fact tag");
    if (readValues(action, ["clear_tag", "fact_tag", "quest_tag", "tag"]).length === 0) {
      errors.push(`${relative(file)}: ${location} must define clear_tag, fact_tag, quest_tag, or tag for a clear_tag action.`);
    }
  } else if (type === "set_variable") {
    checkStringList(file, action, location, ["variable", "key", "fact"], "quest fact key");
    checkOptionalString(file, action, location, "value");
    checkOptionalString(file, action, location, "stage");
    if (readValues(action, ["variable", "key", "fact"]).length === 0 && !Object.hasOwn(action, "stage")) {
      errors.push(`${relative(file)}: ${location} must define variable, key, fact, or stage for a set_variable action.`);
    }
    if (!Object.hasOwn(action, "value") && !Object.hasOwn(action, "stage")) {
      errors.push(`${relative(file)}: ${location}.value or ${location}.stage is required for a set_variable action.`);
    }
    const explicitType = normalizedString(action.type);
    collectQuestStageReferences(
      file,
      location,
      action,
      ["quest", "quest_id", "id"],
      questFactStageReferenceKeys(action, explicitType, {
        stageKeys: ["stage"],
        valueKeys: ["value"],
        factKeyFields: ["variable", "key", "fact"],
        typeAliases: ["set_stage", "quest_stage", "stage"]
      }),
      defaultQuestId,
      "quest fact action"
    );
  } else if (type === "counter") {
    checkStringList(file, action, location, ["counter", "increment_counter", "key", "fact"], "quest fact counter");
    checkOptionalInteger(file, action, location, "amount");
    if (readValues(action, ["counter", "increment_counter", "key", "fact"]).length === 0) {
      errors.push(`${relative(file)}: ${location} must define counter, increment_counter, key, or fact for a counter action.`);
    }
  }
}

function checkForcedDialogueEntryText(file, entry, location, messagePrefix = "") {
  if (entry && typeof entry === "object") checkDialogueExchanges(file, entry, location);
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    return;
  }
  checkOptionalBoolean(file, entry, location, "remove");
  checkOptionalString(file, entry, location, "message_prefix");
  checkOptionalString(file, entry, location, "text_prefix");
  for (const key of ["initiate_dialogue", "aggro_immediately", "force_camera_towards_villager", "requires_line_of_sight", "requires_held_trade_item", "requires_trade_item", "requires_matching_trade_item", "requires_player_aiming_at_witness"]) {
    checkOptionalBoolean(file, entry, location, key);
  }
  checkOptionalInteger(file, entry, location, "priority");
  checkOptionalInteger(file, entry, location, "reputation");
  checkOptionalInteger(file, entry, location, "min_trade_level", { min: 1, max: 5 });
  checkOptionalInteger(file, entry, location, "max_trade_level", { min: 1, max: 5 });
  checkOptionalInteger(file, entry, location, "min_villager_trade_level", { min: 1, max: 5 });
  checkOptionalInteger(file, entry, location, "max_villager_trade_level", { min: 1, max: 5 });
  checkOptionalInteger(file, entry, location, "min_recent_container_thefts", { min: 0 });
  checkOptionalInteger(file, entry, location, "max_recent_container_thefts", { min: 0 });
  checkOptionalInteger(file, entry, location, "min_recent_retaliations", { min: 0 });
  checkOptionalInteger(file, entry, location, "max_recent_retaliations", { min: 0 });
  checkOptionalNumber(file, entry, location, "chance", { min: 0, max: 1 });
  checkOptionalNumber(file, entry, location, "witness_radius", { min: 1 });
  checkOptionalInteger(file, entry, location, "required_aim_duration_ticks", { min: 0 });
  checkOptionalInteger(file, entry, location, "required_aim_duration_seconds", { min: 0 });
  checkOptionalInteger(file, entry, location, "required_aim_duration_days", { min: 0 });
  checkForcedDialogueOutput(file, entry.output, `${location}.output`);
  checkStringList(file, entry, location, ["line", "lines"], "forced dialogue line");
  checkStringList(file, entry, location, ["line_key", "line_keys", "text_key", "text_keys"], "forced dialogue text key");
  checkOptionalString(file, entry, location, "loot_table");
  checkStringList(file, entry, location, ["loot_tables"], "forced dialogue loot table id");
  checkResourceIdValues(file, entry, location, ["loot_table", "loot_tables"], "forced dialogue loot table id");
  checkOptionalString(file, entry, location, "target_entity_type");
  checkStringList(file, entry, location, ["target_entity_types", "target_entities"], "forced dialogue target entity id");
  checkResourceIdValues(file, entry, location, ["target_entity_type", "target_entity_types", "target_entities"], "forced dialogue target entity id");
  collectLootTableReferences(file, entry, location, ["loot_table", "loot_tables"], "forced dialogue loot-table filter");
  collectForcedDialogueMessageKeys(file, entry, location, ["line_key", "line_keys", "text_key", "text_keys"], "forced dialogue text key");
  if (!hasStringValues(entry, ["line_key", "line_keys", "text_key", "text_keys"]) && messagePrefix && hasStringValues(entry, ["line", "lines"])) {
    collectForcedDialogueMessageKey(file, `${location}.message_prefix`, `${messagePrefix}.line`, "derived forced dialogue line key");
  }
}

function checkForcedDialogueOutput(file, output, location) {
  if (output === undefined) {
    return;
  }
  if (!output || typeof output !== "object" || Array.isArray(output)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, output, location, new Set(["mode", "radius", "look_at_player"]));
  checkStringValues(file, output, location, ["mode"], forcedDialogueOutputModes, "forced dialogue output mode");
  checkOptionalNumber(file, output, location, "radius", { min: 0 });
  checkOptionalBoolean(file, output, location, "look_at_player");
}

function checkForcedDialogueOptions(file, options, location, defaultQuestId = "", messagePrefix = "", kind = "option") {
  if (!Array.isArray(options)) {
    return;
  }
  for (const [index, option] of options.entries()) {
    const optionPrefix = kind === "leave"
      ? childMessagePrefix(messagePrefix, `leave.${index}`)
      : childMessagePrefix(childMessagePrefix(messagePrefix, "option"), stringValue(option?.id));
    checkForcedDialogueOption(
      file,
      option,
      `${location}[${index}]`,
      defaultQuestId,
      readForcedDialogueMessagePrefix(option, optionPrefix)
    );
  }
}

function checkForcedDialogueOption(file, option, location, defaultQuestId = "", messagePrefix = "") {
  if (!option || typeof option !== "object" || Array.isArray(option)) {
    return;
  }
  checkDialogueExchanges(file, option, location);
  checkOptionalString(file, option, location, "message_prefix");
  checkOptionalString(file, option, location, "text_prefix");
  checkOptionalString(file, option, location, "label_key");
  checkStringList(file, option, location, ["response", "responses"], "forced dialogue response");
  checkStringList(file, option, location, ["response_key", "response_keys"], "forced dialogue response key");
  collectForcedDialogueMessageKey(file, `${location}.label_key`, stringValue(option.label_key), "forced dialogue option label key");
  if (!stringValue(option.label_key) && messagePrefix && hasStringValues(option, ["label"])) {
    collectForcedDialogueMessageKey(file, `${location}.message_prefix`, `${messagePrefix}.label`, "derived forced dialogue option label key");
  }
  collectForcedDialogueMessageKeys(file, option, location, ["response_key", "response_keys"], "forced dialogue response key");
  if (!hasStringValues(option, ["response_key", "response_keys"]) && messagePrefix && hasStringValues(option, ["response", "responses"])) {
    collectForcedDialogueMessageKey(file, `${location}.message_prefix`, childMessagePrefix(messagePrefix, "response"), "derived forced dialogue response key");
  }
  checkForcedDialogueResultText(file, option.take_items, `${location}.take_items`, childMessagePrefix(messagePrefix, "take_items"));
  checkForcedDialogueResultText(file, option.payment, `${location}.payment`, childMessagePrefix(messagePrefix, "take_items"));
  checkForcedDialogueResultText(file, option.take_stolen_items, `${location}.take_stolen_items`, childMessagePrefix(messagePrefix, "take_stolen_items"));
  checkForcedDialogueResultText(file, option.return_stolen_items, `${location}.return_stolen_items`, childMessagePrefix(messagePrefix, "take_stolen_items"));
  checkConditions(file, option, location, defaultQuestId);
  if (option.follow_up && typeof option.follow_up === "object" && !Array.isArray(option.follow_up)) {
    const followUpPrefix = readForcedDialogueMessagePrefix(option.follow_up, childMessagePrefix(messagePrefix, "follow_up"));
    checkForcedDialogueEntryText(file, option.follow_up, `${location}.follow_up`, followUpPrefix);
    checkForcedDialogueOptions(file, option.follow_up.options, `${location}.follow_up.options`, defaultQuestId, followUpPrefix);
    checkForcedDialogueOptions(file, option.follow_up.leave_options, `${location}.follow_up.leave_options`, defaultQuestId, followUpPrefix, "leave");
    checkForcedDialogueOption(
      file,
      option.follow_up.leave_option,
      `${location}.follow_up.leave_option`,
      defaultQuestId,
      readForcedDialogueMessagePrefix(option.follow_up.leave_option, childMessagePrefix(followUpPrefix, "leave"))
    );
  }
}

function checkForcedDialogueResultText(file, value, location, messagePrefix = "") {
  if (value === undefined || value === true || value === false) {
    return;
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    errors.push(`${relative(file)}: ${location} must be a boolean or object.`);
    return;
  }
  checkStringList(file, value, location, ["success_response", "success_responses"], "forced dialogue success response");
  checkStringList(file, value, location, ["failure_response", "failure_responses"], "forced dialogue failure response");
  checkStringList(file, value, location, ["success_response_key", "success_response_keys"], "forced dialogue success response key");
  checkStringList(file, value, location, ["failure_response_key", "failure_response_keys"], "forced dialogue failure response key");
  collectForcedDialogueMessageKeys(file, value, location, ["success_response_key", "success_response_keys"], "forced dialogue success response key");
  collectForcedDialogueMessageKeys(file, value, location, ["failure_response_key", "failure_response_keys"], "forced dialogue failure response key");
  if (!hasStringValues(value, ["success_response_key", "success_response_keys"]) && messagePrefix && hasStringValues(value, ["success_response", "success_responses"])) {
    collectForcedDialogueMessageKey(file, `${location}.message_prefix`, childMessagePrefix(messagePrefix, "success"), "derived forced dialogue success response key");
  }
  if (!hasStringValues(value, ["failure_response_key", "failure_response_keys"]) && messagePrefix && hasStringValues(value, ["failure_response", "failure_responses"])) {
    collectForcedDialogueMessageKey(file, `${location}.message_prefix`, childMessagePrefix(messagePrefix, "failure"), "derived forced dialogue failure response key");
  }
}

function collectForcedDialogueMessageKeys(file, entry, location, keys, reason) {
  for (const key of keys) {
    collectForcedDialogueMessageKey(file, `${location}.${key}`, entry?.[key], reason);
  }
}

function collectForcedDialogueMessageKey(file, location, value, reason) {
  if (typeof value === "string") {
    const key = value.trim();
    if (key) {
      pendingForcedDialogueMessageKeyReferences.push({ file, location, key, reason });
    }
    return;
  }
  if (!Array.isArray(value)) {
    return;
  }
  value.forEach((child, index) => {
    if (typeof child !== "string") {
      return;
    }
    const key = child.trim();
    if (key) {
      pendingForcedDialogueMessageKeyReferences.push({ file, location: `${location}[${index}]`, key, reason });
    }
  });
}

function collectLootTableReferences(file, entry, location, keys, reason) {
  for (const key of keys) {
    collectLootTableReference(file, `${location}.${key}`, entry?.[key], reason);
  }
}

function collectLootTableReference(file, location, value, reason) {
  if (typeof value === "string") {
    const id = value.trim();
    if (id) {
      pendingLootTableReferences.push({ file, location, id, reason });
    }
    return;
  }
  if (!Array.isArray(value)) {
    return;
  }
  value.forEach((child, index) => {
    if (typeof child !== "string") {
      return;
    }
    const id = child.trim();
    if (id) {
      pendingLootTableReferences.push({ file, location: `${location}[${index}]`, id, reason });
    }
  });
}

function checkMemoryTagReferences(file, entry, location, keys, reason) {
  for (const key of keys) {
    checkMemoryTagReference(file, `${location}.${key}`, entry?.[key], reason);
  }
}

function checkMemoryTagReference(file, location, value, reason) {
  if (typeof value === "string") {
    checkMemoryTagId(file, location, value, reason);
    return;
  }
  if (!Array.isArray(value)) {
    return;
  }
  value.forEach((child, index) => {
    if (typeof child === "string") {
      checkMemoryTagId(file, `${location}[${index}]`, child, reason);
    }
  });
}

function checkMemoryTagId(file, location, value, reason) {
  const id = stringValue(value);
  if (!id) {
    return;
  }
  const parsed = parseResourceId(id);
  if (id.includes(":")) {
    if (!parsed || !parsed.valid) {
      errors.push(`${relative(file)}: ${location} references invalid village memory tag "${id}" from ${reason}.`);
    }
    return;
  }
  if (!memoryTags.has(normalizedString(id))) {
    errors.push(`${relative(file)}: ${location} references unknown legacy village memory tag "${id}" from ${reason}; use a known built-in tag or a namespaced custom tag id.`);
  }
}

function checkProfessionReferences(file, entry, location, keys, reason) {
  for (const key of keys) {
    checkProfessionReference(file, `${location}.${key}`, entry?.[key], reason);
  }
}

function checkProfessionReference(file, location, value, reason) {
  if (typeof value === "string") {
    checkProfessionId(file, location, value, reason);
    return;
  }
  if (!Array.isArray(value)) {
    return;
  }
  value.forEach((child, index) => {
    if (typeof child === "string") {
      checkProfessionId(file, `${location}[${index}]`, child, reason);
    }
  });
}

function checkProfessionId(file, location, value, reason) {
  const raw = stringValue(value);
  if (!raw) {
    return;
  }
  let normalized = raw.toLowerCase();
  if (normalized === "unemployed" || normalized === "minecraft:unemployed") {
    normalized = "minecraft:none";
  }
  const namespaced = normalized.includes(":") ? normalized : `minecraft:${normalized}`;
  const parsed = parseResourceId(namespaced);
  if (!parsed || !parsed.valid || !parsed.namespace || !parsed.path) {
    errors.push(`${relative(file)}: ${location} references invalid profession id "${value}" from ${reason}.`);
    return;
  }
  if (parsed.namespace === "minecraft" && !vanillaVillagerProfessions.has(parsed.path)) {
    errors.push(`${relative(file)}: ${location} references unknown vanilla profession "${value}" from ${reason}; use a known profession or a full modded profession id.`);
  }
}

function collectNotificationTriggerReference(file, location, value, reason) {
  const trigger = stringValue(value);
  if (!trigger) {
    return;
  }
  pendingNotificationTriggerReferences.push({
    file,
    location,
    trigger,
    reason
  });
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
  if (Array.isArray(data)) {
    errors.push(`${relative(file)}: dialogue root must be an object; wrap entry arrays in a section key such as "lines", "options", or "messages".`);
    return sections;
  }
  if (!data || typeof data !== "object") {
    errors.push(`${relative(file)}: dialogue root must be an object.`);
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

function checkChoiceAvailability(file, entry, location) {
  if (entry.unavailable_behavior !== undefined && !["hide", "disable"].includes(entry.unavailable_behavior))
    errors.push(`${relative(file)}: ${location}.unavailable_behavior must be hide or disable.`);
  if (entry.unavailable !== undefined) {
    const value = entry.unavailable;
    if (typeof value !== "string" && !(value && typeof value === "object" && !Array.isArray(value)
        && Object.keys(value).length === 1 && typeof value.key === "string" && value.key.trim()))
      errors.push(`${relative(file)}: ${location}.unavailable must be text or a localized reference.`);
  }
}

function checkConditions(file, entry, location, defaultQuestId = "") {
  checkChoiceAvailability(file, entry, location);
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

function warnLiveOnlyQuestConditions(file, conditions, location, usage) {
  if (!Array.isArray(conditions)) {
    return;
  }
  conditions.forEach((condition, index) => warnLiveOnlyQuestCondition(file, condition, `${location}[${index}]`, usage));
}

function warnLiveOnlyQuestCondition(file, condition, location, usage) {
  if (!condition || typeof condition !== "object" || Array.isArray(condition)) {
    return;
  }

  const type = normalizedString(condition.type);
  if (["all", "all_of", "and", "any", "any_of", "or"].includes(type)) {
    warnLiveOnlyQuestConditions(file, condition.conditions, `${location}.conditions`, usage);
    return;
  }
  if (type === "not") {
    warnLiveOnlyQuestCondition(file, condition.condition, `${location}.condition`, usage);
    return;
  }
  if (isSavedQuestMemoryCondition(condition)) {
    return;
  }
  if (type === "quest") {
    warnLiveOnlyQuestStateAliases(file, condition, location, usage);
  }
  if (questLiveOnlyConditionTypes.has(type)) {
    warnings.push(`${relative(file)}: ${location} uses live-only ${type} condition in ${usage}; if the quest issuer is unloaded, evaluation stays unknown until that villager is loaded.`);
  }
}

function warnLiveOnlyQuestStateAliases(file, condition, location, usage) {
  for (const state of readValues(condition, ["state", "states"])) {
    if (typeof state !== "string" || !state.trim()) {
      continue;
    }
    const normalized = normalizedString(state);
    if (questStates.has(normalized) && !savedQuestStates.has(normalized)) {
      warnings.push(`${relative(file)}: ${location} uses live-only quest state "${state}" in ${usage}; if the quest issuer is unloaded, saved-data evaluation stays unknown until that villager is loaded.`);
    }
  }
}

function isSavedQuestMemoryCondition(condition) {
  if (normalizedString(condition.type) !== "memory") {
    return false;
  }
  if (questSavedMemoryKinds.has(normalizedString(condition.kind))) {
    return true;
  }
  return readValues(condition, ["tag", "tags"])
    .some((tag) => typeof tag === "string" && tag.trim());
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
  if (!conditionTypes.has(type) && !questV2RegistryIndex.conditions.schemas.has(type)) {
    errors.push(`${relative(file)}: ${location}.type "${condition.type}" is not a supported condition type.`);
    return;
  }

  if (type === "health" && (condition.min ?? 0) > (condition.max ?? 1))
    errors.push(`${relative(file)}: ${location} health requires min <= max.`);
  const schema = questV2RegistryIndex.conditions.schemas.get(type);
  const allowedKeys = conditionKeys[type] ?? new Set(["type", ...Object.keys(schema?.properties ?? {})]);
  checkRegisteredFields(file, condition, location, type, "", "conditions");
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
  } else if (type === "quest_fact" || type === "quest_tag" || type === "quest_variable" || type === "quest_counter" || type === "quest_stage" || type === "fact" || type === "stage") {
    checkQuestFactCondition(file, condition, location, defaultQuestId);
  } else if (["villager_equipment", "equipment", "armed"].includes(type)) {
    for (const key of ["armed", "unarmed", "requires_villager_armed", "villager_armed", "requires_villager_unarmed", "villager_unarmed"]) {
      checkOptionalBoolean(file, condition, location, key);
    }
  } else if (["advancement", "advancements"].includes(type)) {
    checkStringList(file, condition, location, ["advancement", "advancements", "id", "ids"], "advancement id");
    checkOptionalBoolean(file, condition, location, "all");
  } else if (["scoreboard", "score"].includes(type)) {
    for (const key of ["value", "min", "max"]) checkOptionalInteger(file, condition, location, key);
  } else if (["nearby_entity", "nearby_entities", "entity_nearby"].includes(type)) {
    for (const key of ["radius", "min_count", "max_count"]) checkOptionalInteger(file, condition, location, key, { min: 0 });
  } else if (["village", "village_presence"].includes(type)) {
    checkOptionalBoolean(file, condition, location, "present");
  } else if (type === "mood" || type === "villager_mood") {
    checkStringValues(file, condition, location, ["mood", "moods", "state", "states"], moodStates, "villager mood", { requireAny: true });
    checkOptionalInteger(file, condition, location, "min", { min: 0, max: 100 });
    checkOptionalInteger(file, condition, location, "min_intensity", { min: 0, max: 100 });
    checkOptionalInteger(file, condition, location, "min_mood_intensity", { min: 0, max: 100 });
    checkOptionalInteger(file, condition, location, "max", { min: 0, max: 100 });
    checkOptionalInteger(file, condition, location, "max_intensity", { min: 0, max: 100 });
    checkOptionalInteger(file, condition, location, "max_mood_intensity", { min: 0, max: 100 });
  } else if (type === "weather") {
    checkStringValues(file, condition, location, ["state", "states", "weather", "weathers"], weatherStates, "weather state", { requireAny: true });
  } else if (type === "time" || type === "time_of_day") {
    checkStringValues(file, condition, location, ["value", "values", "time", "times"], timesOfDay, "time of day", { requireAny: true });
  }
}

function checkQuestFactCondition(file, condition, location, defaultQuestId = "") {
  checkStringList(file, condition, location, ["quest", "quest_id"], "quest id");
  checkStringValues(file, condition, location, ["scope"], questFactScopes, "quest fact scope");
  checkStringList(file, condition, location, ["tag", "tags", "fact_tag", "quest_tag"], "quest fact tag");
  checkResourceIdValues(file, condition, location, ["tag", "tags", "fact_tag", "quest_tag"], "quest fact tag");
  checkStringList(file, condition, location, ["key", "variable", "counter", "fact"], "quest fact key");
  checkStringList(file, condition, location, ["value", "values", "stage", "stages"], "quest fact value");
  checkOptionalInteger(file, condition, location, "min");
  checkOptionalInteger(file, condition, location, "max");

  const questIds = readValues(condition, ["quest", "quest_id"]);
  for (const questId of questIds.length === 0 && defaultQuestId ? [defaultQuestId] : questIds) {
    if (typeof questId === "string" && questId.trim()) {
      pendingQuestReferences.push({
        file,
        location,
        id: questId.trim(),
        reason: "quest fact condition"
      });
    }
  }
  const type = normalizedString(condition.type);
  collectQuestStageReferences(
    file,
    location,
    condition,
    ["quest", "quest_id"],
    questFactStageReferenceKeys(condition, type),
    defaultQuestId,
    "quest fact condition"
  );
  if (readValues(condition, ["tag", "tags", "fact_tag", "quest_tag", "key", "variable", "counter", "fact", "stage", "stages"]).length === 0) {
    errors.push(`${relative(file)}: ${location} must define tag, tags, fact_tag, quest_tag, key, variable, counter, fact, stage, or stages.`);
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
  checkStringList(file, condition, location, ["tag", "tags"], "memory tag");
  checkMemoryTagReferences(file, condition, location, ["tag", "tags"], "memory condition");
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

function checkResourceIdValues(file, entry, location, keys, label) {
  for (const value of readValues(entry, keys)) {
    if (typeof value !== "string" || !value.trim()) {
      continue;
    }
    const parsed = parseResourceId(value);
    if (!parsed || !parsed.valid) {
      errors.push(`${relative(file)}: ${location} has invalid ${label} "${value}".`);
    }
  }
}

function checkOptionalString(file, entry, location, key) {
  const value = entry[key];
  if (value !== undefined && (typeof value !== "string" || !value.trim())
      && !(["title", "description", "text", "label", "tracker_text", "tracker_complete_text", "complete_text"].includes(key)
        && isLocalizedReference(value))) {
    errors.push(`${relative(file)}: ${location}.${key} must be a nonblank string or localized reference.`);
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

function checkUnknownObjectKeys(file, object, location, allowedKeys) {
  for (const key of Object.keys(object)) {
    if (!allowedKeys.has(key)) {
      errors.push(`${relative(file)}: ${location}.${key} is not a supported field.`);
    }
  }
}

function isControlOnly(object, allowedKeys) {
  return Object.keys(object).every((key) => allowedKeys.includes(key));
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

function collectQuestStageReferences(file, location, entry, questKeys, stageKeys, defaultQuestId = "", reason = "quest stage reference") {
  if (!stageKeys || stageKeys.length === 0) {
    return;
  }
  const stageIds = readValues(entry, [...new Set(stageKeys)])
    .filter((value) => typeof value === "string")
    .map((value) => value.trim())
    .filter(Boolean);
  if (stageIds.length === 0) {
    return;
  }
  const explicitQuestIds = readValues(entry, questKeys)
    .filter((value) => typeof value === "string")
    .map((value) => value.trim())
    .filter(Boolean);
  const questIds = explicitQuestIds.length === 0 && defaultQuestId ? [defaultQuestId] : explicitQuestIds;
  for (const questId of questIds) {
    for (const stageId of stageIds) {
      pendingQuestStageReferences.push({ file, location, questId, stageId, reason });
    }
  }
}

function questFactStageReferenceKeys(entry, type, options = {}) {
  const stageKeys = [...(options.stageKeys ?? ["stage", "stages"])];
  const valueKeys = options.valueKeys ?? ["value", "values"];
  const factKeyFields = options.factKeyFields ?? ["key", "variable", "counter", "fact"];
  const typeAliases = options.typeAliases ?? ["stage", "quest_stage"];
  const normalizedType = normalizedString(type);
  const factKeys = readValues(entry, factKeyFields)
    .filter((value) => typeof value === "string")
    .map(normalizedString);
  if (typeAliases.includes(normalizedType) || factKeys.includes("stage")) {
    stageKeys.push(...valueKeys);
  }
  return [...new Set(stageKeys)];
}

function hasStringValues(entry, keys) {
  return readValues(entry ?? {}, keys).some((value) => typeof value === "string" && value.trim());
}

function readForcedDialogueMessagePrefix(entry, fallback = "") {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    return fallback;
  }
  return stringValue(entry.message_prefix) || stringValue(entry.text_prefix) || fallback;
}

function childMessagePrefix(parent, child) {
  if (!parent) {
    return "";
  }
  const part = messageKeyPart(child);
  return part ? `${parent}.${part}` : parent;
}

function messageKeyPart(value) {
  return stringValue(value)
    .toLowerCase()
    .replaceAll(":", ".")
    .replaceAll("/", ".")
    .replace(/[^a-z0-9_.-]+/g, "_")
    .replace(/^[._-]+|[._-]+$/g, "");
}

function forcedDialogueFallbackEntryId(file, index) {
  const id = resourceIdForFile(file, roots.forcedDialogue)
    .replace(/^villagerretaliation:/, "")
    .replaceAll("/", "_");
  return `${id}_${index}`;
}

function forcedDialogueSourceIdForFile(file) {
  const base = path.dirname(path.join(root, roots.forcedDialogue));
  const relativePath = path.relative(base, file).replaceAll(path.sep, "/");
  return relativePath ? `villagerretaliation:${relativePath}` : "";
}

function parseResourceId(value) {
  const id = stringValue(value);
  if (!id) {
    return null;
  }
  if (!/^([a-z0-9_.-]+:)?[a-z0-9/._-]+$/.test(id)) {
    return { id, valid: false, namespace: "", path: "" };
  }
  const separator = id.indexOf(":");
  if (separator < 0) {
    return { id, valid: true, namespace: "", path: id };
  }
  const namespace = id.slice(0, separator);
  const resourcePath = id.slice(separator + 1);
  return namespace && resourcePath
    ? { id, valid: true, namespace, path: resourcePath }
    : { id, valid: false, namespace, path: resourcePath };
}

function normalizedString(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function canonicalActionType(value) {
  const normalized = normalizedString(value);
  if (!normalized) {
    return "";
  }
  const registered = questV2RegistryIndex.actions.canonical.get(normalized);
  if (registered && questV2RegistryIndex.actions.schemas.has(registered)) return registered;
  if (["notification", "notify", "hud", "message"].includes(normalized)) return "notification";
  if (["tracker", "quest_tracker", "flash_tracker"].includes(normalized)) return "tracker";
  if (["forced_dialogue", "force_dialogue", "dialogue"].includes(normalized)) return "forced_dialogue";
  if (["quest", "quest_action"].includes(normalized)) return "quest";
  if (["experience", "xp"].includes(normalized)) return "experience";
  if (["reputation", "rep"].includes(normalized)) return "reputation";
  if (["gossip", "gossip_reputation"].includes(normalized)) return "gossip";
  if (["memory", "memory_event"].includes(normalized)) return "memory";
  if (["loot", "loot_table"].includes(normalized)) return "loot";
  if (["set_tag", "quest_tag", "add_tag", "tag"].includes(normalized)) return "set_tag";
  if (["clear_tag", "remove_tag", "unset_tag"].includes(normalized)) return "clear_tag";
  if (["set_variable", "variable", "set_fact", "fact", "set_stage", "quest_stage", "stage"].includes(normalized)) return "set_variable";
  if (["counter", "increment_counter", "add_counter"].includes(normalized)) return "counter";
  return "";
}

function isQuestFactActionType(type) {
  return type === "set_tag" || type === "clear_tag" || type === "set_variable" || type === "counter";
}

function actionType(action, defaultQuestId = "") {
  const explicit = canonicalActionType(action.type);
  if (explicit) {
    return explicit;
  }
  if (Object.hasOwn(action, "set_tag") || Object.hasOwn(action, "fact_tag") || Object.hasOwn(action, "quest_tag")) {
    return "set_tag";
  }
  if (Object.hasOwn(action, "clear_tag")) {
    return "clear_tag";
  }
  if (Object.hasOwn(action, "variable") || (Object.hasOwn(action, "key") && Object.hasOwn(action, "value")) || Object.hasOwn(action, "stage")) {
    return "set_variable";
  }
  if (Object.hasOwn(action, "counter") || Object.hasOwn(action, "increment_counter")) {
    return "counter";
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
  if (Object.hasOwn(action, "quest") || Object.hasOwn(action, "quest_id") || Object.hasOwn(action, "id")) {
    return "quest";
  }
  if (defaultQuestId && Object.hasOwn(action, "action")) {
    return "quest";
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
  warnings.push(...inspectDialogueMarkup(value, relative(file)));
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

function indexNotifications(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return;
  }
  const notifications = Array.isArray(data.notifications) ? data.notifications : [];
  checkIds(file, notifications, "notification");
  for (const entry of notifications) {
    const trigger = stringValue(entry?.trigger);
    if (trigger) {
      notificationTriggerDefinitions.add(trigger);
    }
  }
}

function metadataObject(entry) {
  return entry && typeof entry === "object" && !Array.isArray(entry) && entry.metadata && typeof entry.metadata === "object" && !Array.isArray(entry.metadata)
    ? entry.metadata
    : {};
}

function indexDialogueTree(file, data) {
  const treeId = dialogueTreeIdForFile(file, data);
  if (!treeId) {
    return;
  }
  const previous = dialogueTreeDefinitions.get(treeId);
  if (previous) {
    errors.push(`${relative(file)}: duplicate dialogue tree id "${treeId}" also defined in ${previous.file}.`);
    return;
  }
  const entries = Array.isArray(data?.entries) ? data.entries : [];
  const nodes = dialogueTreeNodes(data?.nodes);
  const nodeById = new Map(nodes
    .filter((node) => typeof node.id === "string" && node.id.trim())
    .map((node) => [node.id, node]));
  dialogueTreeDefinitions.set(treeId, {
    file: relative(file),
    entryIds: new Set(entries
      .filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry) && stringValue(entry.id))
      .map((entry) => stringValue(entry.id))),
    entryStarts: new Map(entries
      .filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry) && stringValue(entry.id))
      .map((entry) => [stringValue(entry.id), stringValue(entry.start)])),
    entryConditions: new Map(entries
      .filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry) && stringValue(entry.id))
      .map((entry) => [stringValue(entry.id), entry.conditions])),
    nodeById,
    metadataQuest: dialogueTreeDefaultQuestId(file, data),
    metadataQuestline: stringValue(metadataObject(data).questline),
    pathQuestline: dialogueTreePathQuestline(file)
  });
}

function questPathQuestline(file) {
  const relativePath = path.relative(path.join(root, roots.quests), file).replaceAll(path.sep, "/");
  if (relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
    return "";
  }
  const parts = relativePath.split("/");
  return parts.length > 1 ? parts[0] : "";
}

function dialogueTreePathQuestline(file) {
  return dialogueTreeQuestModule(file)?.questline ?? "";
}

function indexForcedDialogue(file, data) {
  const questModule = forcedDialogueQuestModule(file);
  const metadataQuest = questModule ? stringValue(metadataObject(data).quest) : "";
  let hasQuestTrigger = false;
  for (const entry of entriesFor(data)) {
    const entryId = stringValue(entry?.id);
    const trigger = normalizedString(entry?.trigger || entry?.event);
    hasQuestTrigger ||= trigger === "quest";
    if (entryId) {
      registerForcedDialogueDefinition(file, entryId, trigger, true);
    }
    registerForcedDialogueDefinition(file, forcedDialogueSourceIdForFile(file), trigger, false);
  }
  if (questModule) {
    forcedDialogueQuestModules.push({
      file,
      questline: questModule.questline,
      questId: metadataQuest || questModule.questId,
      inferredQuestId: questModule.questId,
      metadataQuest,
      hasQuestTrigger
    });
  }
}

function registerForcedDialogueDefinition(file, id, trigger, requireUnique) {
  if (!id) {
    return;
  }
  const existing = forcedDialogueDefinitions.get(id);
  if (existing) {
    if (requireUnique) {
      errors.push(`${relative(file)}: duplicate forced dialogue id "${id}" also defined in ${existing.file}.`);
    }
    if (trigger) {
      existing.triggers.add(trigger);
    }
    return;
  }
  forcedDialogueDefinitions.set(id, {
    file: relative(file),
    triggers: new Set(trigger ? [trigger] : [])
  });
}

function indexLootTable(file) {
  const id = resourceIdForFile(file, roots.lootTables);
  if (id) {
    lootTableDefinitions.add(id);
  }
}

function forcedDialogueQuestModule(file) {
  const relativePath = path.relative(path.join(root, roots.forcedDialogue), file).replaceAll(path.sep, "/");
  const parts = relativePath.split("/");
  if (parts.length < 3 || parts[0] !== "quests" || !parts.at(-1).endsWith(".json")) {
    return null;
  }
  const questline = parts[1];
  const questName = parts.at(-1).slice(0, -".json".length);
  return {
    questline,
    questId: questName ? `villagerretaliation:${questName}` : ""
  };
}

function checkDialogueTreeMetadataConsistency(file, data, location) {
  const metadata = metadataObject(data);
  const metadataQuestline = stringValue(metadata.questline);
  const metadataQuest = stringValue(metadata.quest);
  const explicitId = stringValue(data.id);
  const inferredQuestTreeId = dialogueTreeQuestModule(file)?.questId ?? "";
  if (metadataQuestline && !metadataQuest) {
    warnings.push(`${relative(file)}: ${location}.metadata.questline is set without metadata.quest; quest-scoped dialogue trees infer it from their module path or id.`);
  }
  if (metadataQuest && !metadataQuestline) {
    errors.push(`${relative(file)}: ${location}.metadata.questline is required when metadata.quest is set.`);
  }
  if (explicitId && inferredQuestTreeId && explicitId !== inferredQuestTreeId) {
    warnings.push(`${relative(file)}: ${location}.id "${explicitId}" differs from inferred quest dialogue tree id "${inferredQuestTreeId}".`);
  }
  const defaultQuestId = dialogueTreeDefaultQuestId(file, data);
  if (defaultQuestId) {
    pendingQuestReferences.push({
      file,
      location: metadataQuest ? `${location}.metadata.quest` : location,
      id: defaultQuestId,
      reason: metadataQuest ? "dialogue tree metadata" : "quest dialogue tree module path"
    });
  }
}

function validateCrossReferences() {
  for (const reference of pendingQuestReferences) {
    if (!questDefinitions.has(reference.id)) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing quest id "${reference.id}" from ${reference.reason}.`);
    }
  }

  validateQuestStageReferences();
  validateQuestParentGraph();
  validateQuestlineFolders();
  validateDialogueTreeQuestlineMetadata();
  validateForcedDialogueQuestModules();
  validateDialogueMessageKeyReferences();
  validateForcedDialogueMessageKeyReferences();
  validateLootTableReferences();
  validateNotificationTriggerReferences();

  for (const reference of pendingForcedDialogueReferences) {
    const definition = forcedDialogueDefinitions.get(reference.id);
    if (!definition) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing forced dialogue id "${reference.id}" from ${reference.reason}.`);
      continue;
    }
    if (forcedDialogueReferenceRequiresQuestTrigger(reference.reason) && !definition.triggers.has("quest")) {
      const triggers = [...definition.triggers].sort().join(", ") || "none";
      errors.push(`${relative(reference.file)}: ${reference.location} references forced dialogue "${reference.id}" from ${reference.reason}, but it is not available to quest forced-dialogue contexts (triggers: ${triggers}).`);
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
    warnDialogueTreeLinkLifecycleActions(link, tree);
    warnDialogueTreeLinkLifecycleStateGates(link, tree);

    if (link.metadataQuest && tree.metadataQuest && tree.metadataQuest !== link.metadataQuest) {
      errors.push(`${relative(link.file)}: ${link.location}.dialogue_tree points to "${link.treeId}" but its metadata.quest is "${tree.metadataQuest}" instead of "${link.metadataQuest}".`);
    }
  }
}

function forcedDialogueReferenceRequiresQuestTrigger(reason) {
  return reason === "dialogue or trigger action" || reason === "quest links";
}

function warnDialogueTreeLinkLifecycleActions(link, tree) {
  const targetQuestId = link.metadataQuest || link.questId;
  if (!targetQuestId) {
    return;
  }
  for (const [field, entryId] of [["offer", link.offer], ["reminder", link.reminder], ["turn_in", link.turnIn]]) {
    if (!entryId || !tree.entryIds.has(entryId)) {
      continue;
    }
    const expectedActions = questDialogueTreeLifecycleActions.get(field);
    if (!expectedActions) {
      continue;
    }
    const start = tree.entryStarts.get(entryId);
    if (!start || !tree.nodeById.has(start)) {
      continue;
    }
    if (!hasReachableQuestAction(tree.nodeById, start, expectedActions, targetQuestId)) {
      warnings.push(`${relative(link.file)}: ${link.location}.${field} points to "${entryId}" in "${link.treeId}", but no reachable ${lifecycleActionDescription(field)} quest action for "${targetQuestId}" was found.`);
    }
  }
}

function warnDialogueTreeLinkLifecycleStateGates(link, tree) {
  const targetQuestId = link.metadataQuest || link.questId;
  if (!targetQuestId) {
    return;
  }
  for (const [field, entryId] of [["offer", link.offer], ["reminder", link.reminder], ["turn_in", link.turnIn]]) {
    if (!entryId || !tree.entryIds.has(entryId)) {
      continue;
    }
    const expectedStates = questDialogueTreeLifecycleStates.get(field);
    if (!expectedStates) {
      continue;
    }
    if (!conditionListHasQuestState(tree.entryConditions.get(entryId), expectedStates, targetQuestId)) {
      warnings.push(`${relative(link.file)}: ${link.location}.${field} points to "${entryId}" in "${link.treeId}", but that entry lacks a matching ${lifecycleActionDescription(field)} quest state condition for "${targetQuestId}".`);
    }
  }
}

function validateDialogueMessageKeyReferences() {
  for (const reference of pendingDialogueMessageKeyReferences) {
    if (!dialogueMessageKeys.has(reference.key)) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing dialogue message key "${reference.key}".`);
    }
  }
}

function validateForcedDialogueMessageKeyReferences() {
  for (const reference of pendingForcedDialogueMessageKeyReferences) {
    if (!dialogueMessageKeys.has(reference.key)) {
      warnings.push(`${relative(reference.file)}: ${reference.location} references missing dialogue message key "${reference.key}" from ${reference.reason}; forced dialogue will use inline fallback text if present.`);
    }
  }
}

function validateLootTableReferences() {
  for (const reference of pendingLootTableReferences) {
    const id = parseResourceId(reference.id);
    if (!id || !id.valid) {
      errors.push(`${relative(reference.file)}: ${reference.location} references invalid loot table id "${reference.id}" from ${reference.reason}.`);
      continue;
    }
    if (id.namespace === "villagerretaliation" && !lootTableDefinitions.has(id.id)) {
      errors.push(`${relative(reference.file)}: ${reference.location} references missing built-in loot table "${id.id}" from ${reference.reason}.`);
    }
  }
}

function validateNotificationTriggerReferences() {
  for (const reference of pendingNotificationTriggerReferences) {
    if (!notificationTriggerDefinitions.has(reference.trigger)) {
      warnings.push(`${relative(reference.file)}: ${reference.location} references missing notification trigger "${reference.trigger}" from ${reference.reason}; live notification resolution will fall back to authored text.`);
    }
  }
}

function validateForcedDialogueQuestModules() {
  for (const module of forcedDialogueQuestModules) {
    if (!module.questId) {
      continue;
    }
    const quest = questDefinitions.get(module.questId);
    if (!quest) {
      errors.push(`${relative(module.file)}: quest forced-dialogue module references missing quest id "${module.questId}".`);
      continue;
    }
    if (module.metadataQuest && module.metadataQuest !== module.inferredQuestId) {
      warnings.push(`${relative(module.file)}: metadata.quest "${module.metadataQuest}" differs from inferred quest module id "${module.inferredQuestId}".`);
    }
    if (quest.questline && module.questline && quest.questline !== module.questline) {
      errors.push(`${relative(module.file)}: quest forced-dialogue folder "${module.questline}" does not match quest "${module.questId}" questline "${quest.questline}".`);
    }
    if (!module.hasQuestTrigger) {
      errors.push(`${relative(module.file)}: quest forced-dialogue module must define at least one entry with trigger "quest".`);
    }
  }
}

function validateQuestlineFolders() {
  for (const [questId, quest] of questDefinitions) {
    if (quest.pathQuestline && quest.questline && quest.pathQuestline !== quest.questline) {
      errors.push(`${quest.file}: root.questline "${quest.questline}" does not match quest folder "${quest.pathQuestline}" for "${questId}".`);
    }
  }
}

function validateQuestParentGraph() {
  for (const [questId, quest] of questDefinitions) {
    if (!quest.parent) {
      continue;
    }
    const parent = questDefinitions.get(quest.parent);
    if (quest.parent === questId) {
      errors.push(`${quest.file}: root.parent must not reference this quest's own id "${questId}".`);
      continue;
    }
    if (parent && quest.questline && parent.questline && quest.questline !== parent.questline) {
      warnings.push(`${quest.file}: root.parent references quest "${quest.parent}" in questline "${parent.questline}" while this quest uses "${quest.questline}".`);
    }
  }

  const state = new Map();
  const reportedCycles = new Set();
  for (const questId of questDefinitions.keys()) {
    visitQuestParentChain(questId, [], state, reportedCycles);
  }
}

function validateQuestStageReferences() {
  const reported = new Set();
  for (const reference of pendingQuestStageReferences) {
    const quest = questDefinitions.get(reference.questId);
    if (!quest) {
      continue;
    }
    if (quest.stageIds.size === 0) {
      continue;
    }
    if (quest.stageIds.has(reference.stageId)) {
      continue;
    }
    const key = [
      relative(reference.file),
      reference.location,
      reference.questId,
      reference.stageId,
      reference.reason
    ].join("\u0000");
    if (reported.has(key)) {
      continue;
    }
    reported.add(key);
    warnings.push(`${relative(reference.file)}: ${reference.location} references stage "${reference.stageId}" on quest "${reference.questId}" from ${reference.reason}, but that quest does not define that stage.`);
  }
}

function visitQuestParentChain(questId, path, state, reportedCycles) {
  const visitState = state.get(questId);
  if (visitState === "visited") {
    return;
  }
  if (visitState === "visiting") {
    reportQuestParentCycle(questId, path, reportedCycles);
    return;
  }

  state.set(questId, "visiting");
  const quest = questDefinitions.get(questId);
  const parentId = quest?.parent;
  if (parentId && questDefinitions.has(parentId)) {
    visitQuestParentChain(parentId, [...path, questId], state, reportedCycles);
  }
  state.set(questId, "visited");
}

function reportQuestParentCycle(repeatedQuestId, path, reportedCycles) {
  const startIndex = path.indexOf(repeatedQuestId);
  const cycle = startIndex < 0 ? [repeatedQuestId, repeatedQuestId] : [...path.slice(startIndex), repeatedQuestId];
  const cycleKey = canonicalCycleKey(cycle);
  if (reportedCycles.has(cycleKey)) {
    return;
  }
  reportedCycles.add(cycleKey);
  const owner = questDefinitions.get(cycle[0]);
  const location = owner == null ? "quest parent graph" : `${owner.file}: root.parent`;
  errors.push(`${location} creates a quest parent cycle: ${cycle.join(" -> ")}.`);
}

function canonicalCycleKey(cycle) {
  const uniqueCycle = cycle.length > 1 && cycle[0] === cycle[cycle.length - 1]
    ? cycle.slice(0, -1)
    : cycle;
  if (uniqueCycle.length === 0) {
    return "";
  }
  let best = uniqueCycle;
  for (let index = 1; index < uniqueCycle.length; index++) {
    const rotated = uniqueCycle.slice(index).concat(uniqueCycle.slice(0, index));
    if (rotated.join("\u0000") < best.join("\u0000")) {
      best = rotated;
    }
  }
  return best.join("\u0000");
}

function validateDialogueTreeQuestlineMetadata() {
  for (const [treeId, tree] of dialogueTreeDefinitions) {
    if (!tree.metadataQuest) {
      continue;
    }
    const quest = questDefinitions.get(tree.metadataQuest);
    if (quest && quest.questline && tree.metadataQuestline && quest.questline !== tree.metadataQuestline) {
      errors.push(`${tree.file}: metadata.questline "${tree.metadataQuestline}" does not match quest "${tree.metadataQuest}" questline "${quest.questline}".`);
    }
    if (quest && quest.questline && tree.pathQuestline && tree.pathQuestline !== quest.questline) {
      errors.push(`${tree.file}: quest dialogue tree folder "${tree.pathQuestline}" does not match quest "${tree.metadataQuest}" questline "${quest.questline}".`);
    }
  }
}
