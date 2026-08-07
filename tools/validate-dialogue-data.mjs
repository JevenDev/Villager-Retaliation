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
  lootTables: "neoforge/src/main/resources/data/villagerretaliation/loot_table",
  notifications: "neoforge/src/main/resources/data/villagerretaliation/notifications/en_us",
  quests: "neoforge/src/main/resources/data/villagerretaliation/quests",
  skillTrades: "neoforge/src/main/resources/data/villagerretaliation/skill_trades"
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
const questFactScopes = new Set(["player", "player_world", "per_player", "world", "global", "server", "quest", "quest_progress", "player_quest", "villager", "issuer", "quest_giver", "village", "settlement"]);
const questCompletionScopes = new Set(["player", "player_world", "per_player", "world", "global", "server", "villager", "issuer", "quest_giver", "village", "settlement"]);
const questBranchLockEvents = new Set(["started", "start", "accepted", "begin", "begun", "completed", "complete", "turn_in", "turnin", "finish", "finished"]);
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
const dialogueTreeNodeKeys = new Set(["id", "lines", "text", "metadata", "actions", "conditions", "responses", "end"]);
const dialogueTreeResponseKeys = new Set(["id", "label", "metadata", "next", "request", "lines", "text", "actions", "conditions", "end", "order"]);
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
const questObjectiveTypes = new Set(["structure_visit", "location_visit", "coordinate", "coordinates", "coords", "region_visit", "item_check", "mob_kill", "entity_kill", "kill", "block_break", "break_block", "mine_block", "mine", "block_place", "place_block", "place", "block_interact", "interact_block", "right_click_block", "use_block", "block_use", "memory_event", "village_event", "village_memory", "memory", "event", "trade", "villager_trade", "trading", "merchant_trade", "gift", "give_gift", "gift_given", "reputation", "rep", "reputation_level", "trust", "choice", "dialogue_choice", "branch_choice", "quest_choice", "fact", "quest_fact", "quest_tag", "quest_variable", "quest_counter", "quest_stage", "stage", "condition"]);
const questLocationObjectiveTypes = new Set(["location_visit", "coordinate", "coordinates", "coords", "region_visit"]);
const questMobKillObjectiveTypes = new Set(["mob_kill", "entity_kill", "kill"]);
const questBlockObjectiveTypes = new Set(["block_break", "break_block", "mine_block", "mine", "block_place", "place_block", "place", "block_interact", "interact_block", "right_click_block", "use_block", "block_use"]);
const questMemoryObjectiveTypes = new Set(["memory_event", "village_event", "village_memory", "memory", "event"]);
const questReputationObjectiveTypes = new Set(["reputation", "rep", "reputation_level", "trust"]);
const questChoiceObjectiveTypes = new Set(["choice", "dialogue_choice", "branch_choice", "quest_choice"]);
const questFactObjectiveTypes = new Set(["fact", "quest_fact", "quest_tag", "quest_variable", "quest_counter", "quest_stage", "stage"]);
const questTrackerStepKeys = new Set([
  "inactive",
  "proof",
  "travel",
  "hunt",
  "break",
  "build",
  "interact",
  "event",
  "trade",
  "gift",
  "reputation",
  "choice",
  "fact",
  "return",
  "abandoned",
  "abandoned_cooldown",
  "expired",
  "completed",
  "branch_locked",
  "consumed",
  "not_started"
]);
const questGiftReactions = new Set(["loved", "liked", "neutral", "disliked", "hated"]);
const questTriggerEvents = new Set(["player_tick", "proximity", "started", "progress", "stage", "stage_changed", "stage_entered", "stage_set", "completed", "abandoned", "expired"]);
const questAbandonmentModes = new Set(["remove_forever", "allow_repickup", "cooldown"]);
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
const dialogueTreeDefinitions = new Map();
const forcedDialogueDefinitions = new Map();
const dialogueMessageKeys = new Map();
const lootTableDefinitions = new Set();
const notificationTriggerDefinitions = new Set();
const forcedDialogueQuestModules = [];
const pendingQuestReferences = [];
const pendingQuestStageReferences = [];
const pendingDialogueTreeLinks = [];
const pendingExternalDialogueSceneReferences = [];
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
const questV2RootKeys = new Set(["schema", "id", "metadata", "provider", "availability", "lifecycle", "dialogue", "target", "entry_stage", "stages", "events", "rewards", "ui", "external_scenes"]);
const questV2MetadataKeys = new Set(["title", "description", "title_key", "description_key", "questline", "tags", "parent", "show_locked_adventure_hint", "author", "version", "revision", "migration"]);
const questV2MigrationKeys = new Set(["active_policy", "on_active_change", "stage_aliases", "objective_aliases"]);
const questV2TargetKeys = new Set(["structure", "dimension", "pieces", "search_radius", "discovery_radius", "proof_item"]);
const questV2ProviderKeys = new Set(["type", "capabilities", "required_capabilities", "death_protection", "filters", "data"]);
const questV2ActiveStateKeys = new Set(["conditions", "hide_when_unmet", "pause_progress_when_unmet"]);
const questV2ExpirationKeys = new Set(["after_ticks", "conditions", "consume", "allow_repickup", "notify"]);
const questV2BranchKeys = new Set(["exclusive_group", "exclusive_on", "blocks_on_start", "blocks_on_completion", "blocks"]);
const questV2AvailabilityKeys = new Set(["conditions", "active", "expiration", "branch", "cooldown", "cooldown_ticks", "cooldown_days", "cooldown_seconds", "completion_cooldown", "completion_cooldown_ticks", "completion_cooldown_days", "completion_cooldown_seconds", "prerequisite_cooldown", "prerequisite_cooldown_ticks", "prerequisite_cooldown_days", "prerequisite_cooldown_seconds", "exclusive_group", "exclusive_on", "blocks_on_start", "blocks_on_completion", "repeatable", "max_starts", "max_completions", "completion_scope", "scope", "abandonment", "abandonment_cooldown", "abandonment_cooldown_ticks", "abandonment_cooldown_days", "abandonment_cooldown_seconds", "consume_on_completion", "consume_on_abandonment", "locked_to_villager", "cross_villager_compatible", "prerequisites"]);
const questV2LifecycleKeys = new Set(["on_start", "on_complete", "on_abandon", "on_expire", "on_fail", "on_stage_enter", "on_stage_exit", "dialogue"]);
const questV2LifecycleHookKeys = new Set(["actions", "transition", "next", "stage", "scene", "complete", "abandon", "fail"]);
const questV2StageKeys = new Set(["id", "title", "title_key", "description", "description_key", "objectives", "complete_when", "completion", "completion_mode", "completion_count", "bonuses", "next", "dialogue", "scenes", "responses", "events", "on_enter", "on_exit", "entry_actions", "exit_actions", "rewards", "ui", "metadata"]);
const questV2CompletionKeys = new Set(["mode", "count"]);
const questV2BonusKeys = new Set(["id", "when", "mode", "count", "actions"]);
const questV2ObjectiveKeys = new Set(["id", "type", "optional", "count", "consume", "tracker", "conditions", "criterion", "match", "target", "targets", "structure", "dimension", "location", "radius", "search_radius", "discovery_radius", "item", "items", "item_tag", "item_tags", "entity", "entities", "entity_tag", "entity_tags", "block", "blocks", "block_tag", "block_tags", "memory", "memory_tag", "memory_tags", "gift_reaction", "gift_reactions", "reputation_level", "reputation_levels", "min", "max", "scope", "quest", "quest_id", "tag", "tags", "key", "value", "values", "stage", "stages", "choices", "metadata", "ui"]);
const questV2TrackerKeys = new Set(["text", "text_key", "complete_text", "complete_text_key", "show_progress", "progress", "metadata"]);
const questV2DialogueSlotKeys = new Set(["scene", "scene_ref", "external", "external_scene", "external_entry", "label", "request", "show_for_babies", "order", "text", "text_key", "lines", "responses", "conditions", "actions", "metadata"]);
const questV2SceneKeys = new Set(["id", "slot", "label", "request", "show_for_babies", "order", "text", "text_key", "lines", "responses", "actions", "conditions", "next", "transition", "external", "external_scene", "external_entry", "scene_ref", "metadata"]);
const questV2ExternalSceneKeys = new Set(["tree", "tree_id", "dialogue_tree", "entry", "entry_id", "metadata"]);
const questV2ResponseKeys = new Set(["id", "label", "label_key", "text", "text_key", "lines", "conditions", "actions", "transition", "next", "stage", "scene", "response", "complete", "abandon", "fail", "request", "order", "metadata"]);
const questV2TransitionKeys = new Set(["stage", "scene", "response", "complete", "abandon", "fail"]);
const questV2EventKeys = new Set(["id", "event", "type", "trigger", "stage", "stages", "conditions", "actions", "transition", "next", "cooldown", "cooldown_ticks", "cooldown_seconds", "cooldown_days", "radius", "repeatable", "metadata"]);
const questV2RewardsKeys = new Set(["actions", "experience", "reputation", "gossip_reputation", "loot_table", "memory_event", "memory_scope"]);
const questV2UiKeys = new Set(["title", "title_key", "description", "description_key", "tracker_text", "tracker_text_key", "tracker_complete_text", "tracker_complete_text_key", "show_progress", "progress", "placeholders", "metadata", "icon", "color", "priority", "hidden"]);
const questV2LifecycleCoverageSlots = new Set(["offer", "reminder", "turn_in"]);

const cli = parseValidatorArgs(process.argv.slice(2));
const questRegistryMetadata = await loadQuestRegistryMetadata();
validateQuestRegistryMetadata(questRegistryMetadata);
const questV2Schema = await loadQuestV2Schema();
validateQuestV2Schema(questV2Schema);
const skillTradeSchema = await loadSkillTradeSchema();
validateSkillTradeSchema(skillTradeSchema);
const questV2RegistryIndex = questRegistryIndex(questRegistryMetadata);

if (cli.help) {
  printValidatorHelp();
  process.exit(0);
}

if (cli.questFiles.length > 0 || cli.skillTradeFiles.length > 0) {
  if (cli.questFiles.length > 0) await validateQuestFiles(cli.questFiles);
  if (cli.skillTradeFiles.length > 0) await validateSkillTradeFiles(cli.skillTradeFiles);
} else {
  await validateBuiltInData();
}

validateCrossReferences();

if (errors.length > 0) {
  for (const error of errors) {
    console.error(formatIssue(error, "error"));
  }
  process.exitCode = 1;
} else if (!cli.quiet) {
  console.log("Built-in dialogue data validation passed.");
}

if (warnings.length > 0) {
  for (const warning of warnings) {
    console.warn(formatIssue(warning, "warning"));
  }
}

function parseValidatorArgs(args) {
  const parsed = {
    help: false,
    quiet: false,
    questFiles: [],
    skillTradeFiles: []
  };
  for (let index = 0; index < args.length; index++) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      parsed.help = true;
    } else if (arg === "--quiet" || arg === "-q") {
      parsed.quiet = true;
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
    } else if (arg.startsWith("--")) {
      errors.push(`tools/validate-dialogue-data.mjs: unsupported option "${arg}".`);
    } else {
      parsed.questFiles.push(path.resolve(arg));
    }
  }
  return parsed;
}

function printValidatorHelp() {
  console.log([
    "Usage: node tools/validate-dialogue-data.mjs [--quiet] [--quest path/to/quest.json ...] [--skill-trade path/to/file.json ...]",
    "",
    "Without --quest, validates the checked-in built-in datapack dialogue and quest data.",
    "With --quest or --skill-trade, validates the supplied resource files plus generated metadata."
  ].join("\n"));
}

async function validateBuiltInData() {
  for (const [kind, relativeRoot] of Object.entries(roots)) {
    for (const file of await jsonFiles(path.join(root, relativeRoot))) {
      const data = await parseJson(file);
      if (data === undefined) {
        continue;
      }
      if (!(kind === "quests" && isQuestModuleV2(data))) {
        checkPlaceholders(file, data);
      }
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
      } else if (kind === "quests") {
        indexQuestResource(file, data);
        checkQuestResource(file, data);
      } else if (kind === "skillTrades") {
        checkSkillTrades(file, data);
      }
    }
  }
}

async function validateQuestFiles(files) {
  await indexBuiltInReferenceData();
  for (const file of files) {
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
  const metadataPath = path.join(root, "tools/datapack-builder/quest-registry-metadata.json");
  try {
    return JSON.parse(await readFile(metadataPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(metadataPath)}: could not read quest registry metadata: ${error.message}`);
    return null;
  }
}

function validateQuestRegistryMetadata(metadata) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    errors.push("tools/datapack-builder/quest-registry-metadata.json: root must be an object.");
    return;
  }
  if (![1, 2].includes(metadata.format_version)) {
    errors.push("tools/datapack-builder/quest-registry-metadata.json: format_version must be 1 or 2.");
  }
  const registries = metadata.registries;
  if (!registries || typeof registries !== "object" || Array.isArray(registries)) {
    errors.push("tools/datapack-builder/quest-registry-metadata.json: registries must be an object.");
    return;
  }
  for (const registry of ["conditions", "actions", "objectives", "triggers", "providers"]) {
    if (!Array.isArray(registries[registry]) || registries[registry].length === 0) {
      errors.push(`tools/datapack-builder/quest-registry-metadata.json: registries.${registry} must be a non-empty array.`);
    }
  }
  if (metadata.format_version >= 2) {
    for (const registry of ["actor_types", "scene_steps", "encounter_templates", "public_quest_providers", "public_quest_objectives", "public_quest_actions", "public_quest_conditions", "public_quest_triggers"]) {
      if (!Array.isArray(registries[registry]) || registries[registry].length === 0) {
        errors.push(`tools/datapack-builder/quest-registry-metadata.json: registries.${registry} must be a non-empty array in format v2.`);
      }
    }
  }
  const fragments = metadata.schema_fragments;
  if (!fragments || typeof fragments !== "object" || Array.isArray(fragments)) {
    errors.push("tools/datapack-builder/quest-registry-metadata.json: schema_fragments must be an object.");
    return;
  }
  for (const fragment of ["condition_type", "action_type", "objective_type", "trigger_event", "provider_type"]) {
    if (!fragments[fragment] || !Array.isArray(fragments[fragment].enum) || fragments[fragment].enum.length === 0) {
      errors.push(`tools/datapack-builder/quest-registry-metadata.json: schema_fragments.${fragment}.enum must be a non-empty array.`);
    }
  }
}

async function loadQuestV2Schema() {
  const schemaPath = path.join(root, "tools/datapack-builder/quest-v2.schema.json");
  try {
    return JSON.parse(await readFile(schemaPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(schemaPath)}: could not read quest module v2 schema: ${error.message}`);
    return null;
  }
}

async function loadSkillTradeSchema() {
  const schemaPath = path.join(root, "tools/datapack-builder/skill-trades.schema.json");
  try {
    return JSON.parse(await readFile(schemaPath, "utf8"));
  } catch (error) {
    errors.push(`${relative(schemaPath)}: could not read skill-trade schema: ${error.message}`);
    return null;
  }
}

function validateSkillTradeSchema(schema) {
  const schemaPath = "tools/datapack-builder/skill-trades.schema.json";
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
  const schemaPath = "tools/datapack-builder/quest-v2.schema.json";
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

function questIdForFile(file, data) {
  return stringValue(data?.id) || resourceIdForFile(file, roots.quests);
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
    checkResourceIdValues(file, line, `lines[${index}]`, ["retaliation_target_entity_types", "retaliation_target_entities"], "retaliation target entity id");
    checkResourceIdValues(file, line, `lines[${index}]`, ["story_structure", "story_structures"], "story structure id");
    checkResourceIdValues(file, line, `lines[${index}]`, ["story_biome", "story_biomes"], "story biome id");
    checkConditions(file, line, `lines[${index}]`);
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

function indexQuestResource(file, data) {
  if (isQuestModuleV2(data)) {
    indexQuestV2(file, data);
  } else {
    indexQuest(file, data);
  }
}

function checkQuestResource(file, data) {
  if (isQuestModuleV2(data)) {
    checkQuestV2(file, data);
  } else {
    checkQuest(file, data);
  }
}

function isQuestModuleV2(data) {
  return data
    && typeof data === "object"
    && !Array.isArray(data)
    && stringValue(data.schema) === questV2SchemaId;
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
    stageIds: questV2StageIds(data.stages),
    requiresDialogueTree: false
  });
}

function checkQuestV2(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    questV2Error(file, "", "root", "quest module v2 root must be an object.", "Wrap the resource in a JSON object.");
    return;
  }

  const questId = stringValue(data.id);
  checkQuestV2UnknownKeys(file, data, "", "root", questV2RootKeys);
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
  checkQuestV2Availability(file, data.availability, "/availability", "availability", questId);
  checkQuestV2Lifecycle(file, data.lifecycle, "/lifecycle", "lifecycle", questId);
  checkQuestV2Target(file, data.target, "/target", "target");
  checkQuestV2RootDialogue(file, data.dialogue, "/dialogue", "dialogue", questId);
  const model = checkQuestV2Stages(file, data.stages, "/stages", "stages", questId);
  checkQuestV2Events(file, data.events, "/events", "events", questId, model.stageIds, "");
  checkQuestV2Rewards(file, data.rewards, "/rewards", "rewards", questId);
  checkQuestV2Ui(file, data.ui, "/ui", "ui");
  checkQuestV2ExternalScenes(file, data.external_scenes, "/external_scenes", "external_scenes");
  validateQuestV2Graph(file, data, model, questId);
  warnQuestV2LifecycleCoverage(file, data, model);
  collectQuestMessageKeyReferences(file, data);
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
    checkQuestV2Tracker(file, objective.tracker, `${objectivePointer}/tracker`, `${objectiveLocation}.tracker`);
    checkQuestV2OptionalObject(file, objective.target, `${objectivePointer}/target`, `${objectiveLocation}.target`, "objective target");
    checkQuestV2OptionalObject(file, objective.location, `${objectivePointer}/location`, `${objectiveLocation}.location`, "objective location");
    checkQuestV2Conditions(file, objective.conditions, `${objectivePointer}/conditions`, `${objectiveLocation}.conditions`, questId, "quest module v2 objective");
    checkQuestV2Ui(file, objective.ui, `${objectivePointer}/ui`, `${objectiveLocation}.ui`);
    checkQuestV2ObjectiveRuntimeRequirements(file, objective, objectivePointer, objectiveLocation, type);
  }
  return ids;
}

function checkQuestV2ObjectiveRuntimeRequirements(file, objective, pointer, location, type) {
  const canonical = questV2RegistryIndex.objectives.canonical.get(type) || type;
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
  if (canonical === "item_check" && !hasStringValues(objective, ["item", "items", "item_tag", "item_tags"])) {
    questV2Error(file, pointer, location, "item_check objective is missing an item selector.", "Set item, items, item_tag, or item_tags.");
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
    questV2Error(file, pointer, location, "dialogue slot must be an object.", "Use scene, external_scene, or inline lines/responses.");
    return;
  }
  checkQuestV2UnknownKeys(file, slot, pointer, location, questV2DialogueSlotKeys);
  const scene = firstString(slot.scene, slot.scene_ref);
  if (scene && sceneIds.size > 0 && !sceneIds.has(scene)) {
    questV2Error(file, `${pointer}/scene`, `${location}.scene`, `dialogue slot references missing local scene "${scene}".`, "Reference a scene id from this stage's scenes array.");
  }
  checkQuestV2OptionalString(file, slot, pointer, location, "label");
  checkQuestV2OptionalString(file, slot, pointer, location, "request");
  checkQuestV2OptionalBoolean(file, slot, pointer, location, "show_for_babies");
  checkQuestV2OptionalInteger(file, slot, pointer, location, "order");
  const hasInline = hasAnyKey(slot, ["text", "text_key", "lines", "responses"]);
  const hasExternal = hasAnyKey(slot, ["external", "external_scene"]);
  if (hasInline && (scene || hasExternal)) {
    questV2Error(file, pointer, location, "dialogue slot cannot mix inline scene content with scene_ref or external_scene.", "Use either inline content or a reference.");
  }
  collectQuestV2ExternalSceneReference(file, slot, pointer, location, slotId);
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
    questV2Error(file, pointer, location, "scene must be an object.", "Use lines, text_key, responses, or external_scene inside the scene.");
    return "";
  }
  checkQuestV2UnknownKeys(file, scene, pointer, location, questV2SceneKeys);
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
  collectQuestV2ExternalSceneReference(file, scene, pointer, location, id, "scene_ref");
  const hasInline = hasAnyKey(scene, ["text", "text_key", "lines", "responses"]);
  const hasExternal = hasAnyKey(scene, ["external", "external_scene", "scene_ref"]);
  if (hasInline && hasExternal) {
    questV2Error(file, pointer, location, "scene cannot mix inline dialogue content with an external scene reference.", "Use either inline lines/responses or external_scene.");
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

function checkQuestV2ExternalScenes(file, externalScenes, pointer, location) {
  if (externalScenes === undefined) {
    return;
  }
  if (!Array.isArray(externalScenes)) {
    questV2Error(file, pointer, location, "external_scenes must be an array.", "List external dialogue tree resource ids.");
    return;
  }
  for (const [index, scene] of externalScenes.entries()) {
    const externalPointer = `${pointer}/${index}`;
    const externalLocation = `${location}[${index}]`;
    if (typeof scene !== "string" || !scene.trim()) {
      questV2Error(file, externalPointer, externalLocation, "external_scenes entries must be nonblank strings.", "Use dialogue tree resource ids such as villagerretaliation:example.");
      continue;
    }
    if (!isResourceLocation(scene.trim())) {
      questV2Error(file, externalPointer, externalLocation, `external scene "${scene.trim()}" is not a valid resource location.`, "Use namespace:path.");
    }
    pendingExternalDialogueSceneReferences.push({ file, location: externalLocation, pointer: externalPointer, treeId: scene.trim(), entry: "" });
  }
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
    if (conditionTypes.has(type)) {
      checkCondition(file, condition, entry.location, questId);
    }
    warnLiveOnlyQuestCondition(file, condition, entry.location, usage);
    if (["all", "all_of", "and", "any", "any_of", "or"].includes(type)) {
      checkQuestV2Conditions(file, condition.conditions, `${entry.pointer}/conditions`, `${entry.location}.conditions`, questId, usage);
    } else if (type === "not") {
      checkQuestV2Conditions(file, condition.condition, `${entry.pointer}/condition`, `${entry.location}.condition`, questId, usage);
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
    const actionPointer = `${pointer}/${index}`;
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

function validateQuestV2Graph(file, data, model) {
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
  warnQuestV2UnreachableStages(file, entryStage, model);
  warnQuestV2UnreachableScenes(file, model);
}

function warnQuestV2UnreachableStages(file, entryStage, model) {
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
      questV2Warning(file, `${stage.pointer}/id`, `${stage.location}.id`, `stage "${stage.id}" is unreachable from entry_stage.`, "Link to it from an earlier transition or remove it.");
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
    const enqueueResponseScenes = (responses) => {
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
      enqueueResponseScenes(slot?.responses);
    }
    for (const entry of model.transitions) {
      if (entry.stageId === stage.id && entry.transition.scene && scenes.has(entry.transition.scene)) {
        enqueueScene(entry.transition.scene);
      }
    }
    while (queue.length > 0) {
      const sceneId = queue.shift();
      enqueueResponseScenes(rawScenes.get(sceneId)?.responses);
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

function collectQuestV2ExternalSceneReference(file, object, pointer, location, defaultEntry, ...extraKeys) {
  if (!object || typeof object !== "object" || Array.isArray(object)) {
    return;
  }
  const keys = ["external_scene", "external", ...extraKeys];
  const key = keys.find((candidate) => Object.hasOwn(object, candidate));
  if (!key) {
    return;
  }
  const value = object[key];
  const externalPointer = `${pointer}/${escapeJsonPointer(key)}`;
  const externalLocation = `${location}.${key}`;
  const fallbackEntry = firstString(object.external_entry, defaultEntry);
  if (typeof value === "string") {
    if (!value.trim()) {
      return;
    }
    if (!isResourceLocation(value.trim())) {
      questV2Error(file, externalPointer, externalLocation, `invalid external dialogue tree "${value.trim()}".`, "Use a resource location such as namespace:path.");
      return;
    }
    pendingExternalDialogueSceneReferences.push({ file, location: externalLocation, pointer: externalPointer, treeId: value.trim(), entry: fallbackEntry });
    return;
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    questV2Error(file, externalPointer, externalLocation, "external scene reference must be a string or object.", "Use a dialogue tree id or an object with tree and entry.");
    return;
  }
  checkQuestV2UnknownKeys(file, value, externalPointer, externalLocation, questV2ExternalSceneKeys);
  const treeId = firstString(value.tree, value.tree_id, value.dialogue_tree);
  if (!treeId) {
    questV2Error(file, `${externalPointer}/tree`, `${externalLocation}.tree`, "external dialogue scene must define tree.", "Set external.tree to the dialogue tree resource id.");
    return;
  }
  if (!isResourceLocation(treeId)) {
    questV2Error(file, `${externalPointer}/tree`, `${externalLocation}.tree`, `invalid external dialogue tree "${treeId}".`, "Use a resource location such as namespace:path.");
    return;
  }
  pendingExternalDialogueSceneReferences.push({
    file,
    location: externalLocation,
    pointer: externalPointer,
    treeId,
    entry: firstString(value.entry, value.entry_id, fallbackEntry)
  });
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
  if (object?.[key] !== undefined && typeof object[key] !== "string") {
    questV2Error(file, `${pointer}/${key}`, `${location}.${key}`, `${key} must be a string.`, "Use a string value or remove the field.");
  }
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
    questV2Error(file, pointer, location, `${label} must be a string or array of strings.`, "Use string values.");
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
  const empty = () => ({ values: new Set(), canonical: new Map(), capabilities: new Map() });
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

function checkQuest(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: quest root must be an object.`);
    return;
  }

  checkUnknownObjectKeys(file, data, "root", new Set([
    "id",
    "replace",
    "remove",
    "display",
    "metadata",
    "links",
    "questline",
    "group",
    "tag",
    "tags",
    "parent",
    "offer",
    "target",
    "objectives",
    "rules",
    "tracker",
    "stages",
    "triggers",
    "rewards",
    "dialogue"
  ]));

  checkOptionalBoolean(file, data, "root", "replace");
  checkOptionalBoolean(file, data, "root", "remove");
  checkOptionalString(file, data, "root", "id");
  checkOptionalString(file, data, "root", "group");
  checkStringList(file, data, "root", ["tag", "tags"], "quest tag");
  checkResourceIdValues(file, data, "root", ["id", "parent"], "quest resource id");
  checkOptionalString(file, data, "root", "message_prefix");
  checkOptionalString(file, data, "root", "text_prefix");
  checkDialogueMetadata(file, data, "root");
  if (data.remove === true || (data.replace === true && isControlOnly(data, ["replace", "metadata"]))) {
    return;
  }
  const defaultQuestId = questIdForFile(file, data);
  checkQuestMetadataConsistency(file, data, "root", defaultQuestId);
  checkOptionalString(file, data, "root", "parent");
  for (const parentId of readValues(data, ["parent"])) {
    if (typeof parentId === "string" && parentId.trim()) {
      pendingQuestReferences.push({
        file,
        location: "root.parent",
        id: parentId.trim(),
        reason: "quest parent"
      });
    }
  }
  checkDisplayObject(file, data.display, "display");
  checkQuestLinks(file, data, "links");
  checkQuestOffer(file, data.offer, "offer", defaultQuestId);
  checkQuestTarget(file, data.target, "target");
  const objectiveIds = questObjectiveIds(data.objectives);
  const stageIds = questStageIds(data.stages);
  checkQuestObjectives(file, data.objectives, "objectives", defaultQuestId);
  checkQuestRules(file, data.rules, "rules", defaultQuestId);
  checkQuestTracker(file, data.tracker, "tracker", objectiveIds);
  checkQuestStages(file, data.stages, "stages", defaultQuestId, objectiveIds);
  checkQuestTriggers(file, data.triggers, "triggers", defaultQuestId, stageIds);
  checkQuestRewards(file, data.rewards, "rewards");
  checkQuestDialogue(file, data.dialogue, "dialogue");
  collectQuestMessageKeyReferences(file, data);
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
  checkUnknownObjectKeys(file, display, location, new Set(["title", "description", "title_key", "description_key"]));
  checkOptionalString(file, display, location, "title");
  checkOptionalString(file, display, location, "description");
  checkOptionalString(file, display, location, "title_key");
  checkOptionalString(file, display, location, "description_key");
}

function checkQuestOffer(file, offer, location, defaultQuestId = "") {
  if (offer === undefined) {
    return;
  }
  if (!offer || typeof offer !== "object" || Array.isArray(offer)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, offer, location, new Set(["professions", "min_villager_level", "skills", "conditions"]));
  checkStringList(file, offer, location, ["professions"], "profession id");
  checkProfessionReferences(file, offer, location, ["professions"], "quest offer");
  checkStringValues(file, offer, location, ["min_villager_level"], villagerLevels, "villager trade level");
  checkConditions(file, offer, location, defaultQuestId);
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
    "dimension",
    "pieces",
    "search_radius",
    "discovery_radius",
    "proof_item"
  ]));
  checkOptionalString(file, target, location, "structure");
  checkOptionalString(file, target, location, "dimension");
  checkResourceIdValues(file, target, location, ["structure", "dimension", "proof_item"], "quest target resource id");
  checkStringList(file, target, location, ["pieces"], "structure piece");
  checkOptionalInteger(file, target, location, "search_radius", { min: 1 });
  checkOptionalInteger(file, target, location, "discovery_radius", { min: 1 });
  checkOptionalString(file, target, location, "proof_item");
}

function checkQuestObjectives(file, objectives, location, defaultQuestId = "") {
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
      "dimension",
      "x",
      "y",
      "z",
      "pos",
      "radius",
      "pieces",
      "search_radius",
      "discovery_radius",
      "item",
      "entity",
      "entities",
      "entity_tag",
      "entity_tags",
      "block",
      "blocks",
      "block_tag",
      "block_tags",
      "memory",
      "memories",
      "memory_event",
      "memory_events",
      "memory_tag",
      "memory_tags",
      "event",
      "events",
      "reaction",
      "reactions",
      "gift_reaction",
      "gift_reactions",
      "level",
      "levels",
      "reputation_level",
      "reputation_levels",
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
      "choice",
      "choices",
      "stage",
      "stages",
      "min",
      "min_reputation",
      "max",
      "max_reputation",
      "count",
      "consume",
      "enchantment",
      "enchantments",
      "min_enchantment_level",
      "max_enchantment_level",
      "min_durability",
      "max_durability",
      "min_durability_percent",
      "max_durability_percent",
      "custom_data",
      "nbt",
      "conditions",
      "tracker"
    ]));
    checkStringValues(file, objective, objectiveLocation, ["type"], questObjectiveTypes, "quest objective type", { requireAny: true });
    const type = normalizedString(objective.type);
    checkOptionalBoolean(file, objective, objectiveLocation, "optional");
    checkOptionalString(file, objective, objectiveLocation, "structure");
    checkOptionalString(file, objective, objectiveLocation, "dimension");
    checkResourceIdValues(file, objective, objectiveLocation, ["structure", "dimension", "item"], "quest objective resource id");
    for (const key of ["x", "y", "z"]) {
      checkOptionalInteger(file, objective, objectiveLocation, key);
    }
    checkQuestObjectivePosition(file, objective.pos, `${objectiveLocation}.pos`);
    checkOptionalInteger(file, objective, objectiveLocation, "radius", { min: 0 });
    checkStringList(file, objective, objectiveLocation, ["pieces"], "structure piece");
    checkOptionalInteger(file, objective, objectiveLocation, "search_radius", { min: 1 });
    checkOptionalInteger(file, objective, objectiveLocation, "discovery_radius", { min: 1 });
    checkOptionalString(file, objective, objectiveLocation, "item");
    checkStringList(file, objective, objectiveLocation, ["entity", "entities"], "entity id or #entity tag");
    checkStringList(file, objective, objectiveLocation, ["entity_tag", "entity_tags"], "entity tag id");
    checkStringList(file, objective, objectiveLocation, ["block", "blocks"], "block id or #block tag");
    checkStringList(file, objective, objectiveLocation, ["block_tag", "block_tags"], "block tag id");
    checkResourceSelectorValues(file, objective, objectiveLocation, ["entity", "entities", "entity_tag", "entity_tags"], "quest objective entity selector");
    checkResourceSelectorValues(file, objective, objectiveLocation, ["block", "blocks", "block_tag", "block_tags"], "quest objective block selector");
    checkStringList(file, objective, objectiveLocation, ["memory", "memories", "memory_event", "memory_events", "memory_tag", "memory_tags", "event", "events"], "village memory tag id");
    checkMemoryTagReferences(file, objective, objectiveLocation, ["memory", "memories", "memory_event", "memory_events", "memory_tag", "memory_tags", "event", "events"], "memory_event objective");
    checkStringValues(file, objective, objectiveLocation, ["reaction", "reactions", "gift_reaction", "gift_reactions"], questGiftReactions, "gift reaction");
    checkStringValues(file, objective, objectiveLocation, ["level", "levels", "reputation_level", "reputation_levels"], reputationLevels, "reputation level");
    checkStringList(file, objective, objectiveLocation, ["quest", "quest_id"], "quest id");
    checkStringValues(file, objective, objectiveLocation, ["scope"], questFactScopes, "quest fact scope");
    checkStringList(file, objective, objectiveLocation, ["tag", "tags", "fact_tag", "quest_tag"], "quest fact tag");
    checkResourceIdValues(file, objective, objectiveLocation, ["tag", "tags", "fact_tag", "quest_tag"], "quest fact tag");
    checkStringList(file, objective, objectiveLocation, ["key", "variable", "counter", "fact"], "quest fact key");
    checkStringList(file, objective, objectiveLocation, ["value", "values", "stage", "stages", "choice", "choices"], "quest fact value");
    checkOptionalInteger(file, objective, objectiveLocation, "min");
    checkOptionalInteger(file, objective, objectiveLocation, "min_reputation");
    checkOptionalInteger(file, objective, objectiveLocation, "max");
    checkOptionalInteger(file, objective, objectiveLocation, "max_reputation");
    checkOptionalInteger(file, objective, objectiveLocation, "count", { min: 1 });
    checkOptionalBoolean(file, objective, objectiveLocation, "consume");
    checkQuestObjectiveItemRequirements(file, objective, objectiveLocation);
    checkConditions(file, objective, objectiveLocation, defaultQuestId);
    if (type === "condition") {
      warnLiveOnlyQuestConditions(
        file,
        objective.conditions,
        `${objectiveLocation}.conditions`,
        "quest condition objective"
      );
    }
    checkQuestObjectiveTracker(file, objective.tracker, `${objectiveLocation}.tracker`);
    for (const questId of readValues(objective, ["quest", "quest_id"])) {
      if (typeof questId === "string" && questId.trim()) {
        pendingQuestReferences.push({
          file,
          location: objectiveLocation,
          id: questId.trim(),
          reason: "fact objective"
        });
      }
    }
    collectQuestStageReferences(
      file,
      objectiveLocation,
      objective,
      ["quest", "quest_id"],
      questFactStageReferenceKeys(objective, type),
      defaultQuestId,
      "fact objective"
    );

    if (type === "structure_visit" && !stringValue(objective.structure)) {
      errors.push(`${relative(file)}: ${objectiveLocation}.structure is required for a structure_visit objective.`);
    }
    if (questLocationObjectiveTypes.has(type) && !hasQuestObjectivePosition(objective)) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define x, y, and z, or pos for a location_visit objective.`);
    }
    if (type === "item_check" && !stringValue(objective.item)) {
      errors.push(`${relative(file)}: ${objectiveLocation}.item is required for an item_check objective.`);
    }
    if (questMobKillObjectiveTypes.has(type) && readValues(objective, ["entity", "entities", "entity_tag", "entity_tags"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define entity, entities, entity_tag, or entity_tags for a mob_kill objective.`);
    }
    if (questBlockObjectiveTypes.has(type) && readValues(objective, ["block", "blocks", "block_tag", "block_tags"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define block, blocks, block_tag, or block_tags for a block event objective.`);
    }
    if (questMemoryObjectiveTypes.has(type) && readValues(objective, ["memory", "memories", "memory_event", "memory_events", "memory_tag", "memory_tags", "event", "events"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define memory, memory_event, memory_tags, event, or events for a memory_event objective.`);
    }
    if (questFactObjectiveTypes.has(type) && readValues(objective, ["tag", "tags", "fact_tag", "quest_tag", "key", "variable", "counter", "fact", "stage", "stages"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define tag, tags, key, variable, counter, stage, or stages for a fact objective.`);
    }
    if (questChoiceObjectiveTypes.has(type) && readValues(objective, ["choice", "choices", "value", "values", "key", "variable", "fact"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define choice, choices, value, values, key, variable, or fact for a choice objective.`);
    }
    if (questReputationObjectiveTypes.has(type) && readValues(objective, ["level", "levels", "reputation_level", "reputation_levels", "min", "min_reputation", "max", "max_reputation"]).length === 0) {
      errors.push(`${relative(file)}: ${objectiveLocation} must define level, levels, min_reputation, max_reputation, min, or max for a reputation objective.`);
    }
    if (type === "condition" && (!Array.isArray(objective.conditions) || objective.conditions.length === 0)) {
      errors.push(`${relative(file)}: ${objectiveLocation}.conditions is required for a condition objective.`);
    }
  }
}

function checkQuestObjectivePosition(file, value, location) {
  if (value === undefined) {
    return;
  }
  if (!Array.isArray(value)) {
    errors.push(`${relative(file)}: ${location} must be an array of three integers.`);
    return;
  }
  if (value.length < 3) {
    errors.push(`${relative(file)}: ${location} must contain x, y, and z.`);
    return;
  }
  value.slice(0, 3).forEach((coordinate, index) => {
    if (!Number.isInteger(coordinate)) {
      errors.push(`${relative(file)}: ${location}[${index}] must be an integer.`);
    }
  });
}

function hasQuestObjectivePosition(objective) {
  return (Number.isInteger(objective.x) && Number.isInteger(objective.y) && Number.isInteger(objective.z))
    || (Array.isArray(objective.pos) && objective.pos.length >= 3 && objective.pos.slice(0, 3).every(Number.isInteger));
}

function checkQuestObjectiveItemRequirements(file, objective, location) {
  checkOptionalInteger(file, objective, location, "min_enchantment_level", { min: 1 });
  checkOptionalInteger(file, objective, location, "max_enchantment_level", { min: 1 });
  for (const key of ["min_durability", "max_durability", "min_durability_percent", "max_durability_percent"]) {
    checkOptionalInteger(file, objective, location, key, { min: 0 });
  }
  for (const key of ["enchantment", "enchantments"]) {
    checkQuestObjectiveEnchantments(file, objective[key], `${location}.${key}`);
  }
  for (const key of ["custom_data", "nbt"]) {
    const value = objective[key];
    if (value !== undefined && (!value || typeof value !== "object" || Array.isArray(value))) {
      errors.push(`${relative(file)}: ${location}.${key} must be an object.`);
    }
  }
}

function checkQuestObjectiveEnchantments(file, value, location) {
  if (value === undefined) {
    return;
  }
  const checkOne = (entry, entryLocation) => {
    if (typeof entry === "string") {
      if (!entry.trim()) {
        errors.push(`${relative(file)}: ${entryLocation} must be a nonblank string.`);
      }
      return;
    }
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
      errors.push(`${relative(file)}: ${entryLocation} must be a string or object.`);
      return;
    }
    checkUnknownObjectKeys(file, entry, entryLocation, new Set(["id", "enchantment", "name", "min_level", "max_level"]));
    if (!stringValue(entry.id) && !stringValue(entry.enchantment) && !stringValue(entry.name)) {
      errors.push(`${relative(file)}: ${entryLocation} must define id, enchantment, or name.`);
    }
    checkOptionalString(file, entry, entryLocation, "id");
    checkOptionalString(file, entry, entryLocation, "enchantment");
    checkOptionalString(file, entry, entryLocation, "name");
    checkOptionalInteger(file, entry, entryLocation, "min_level", { min: 1 });
    checkOptionalInteger(file, entry, entryLocation, "max_level", { min: 1 });
  };
  if (Array.isArray(value)) {
    value.forEach((entry, index) => checkOne(entry, `${location}[${index}]`));
    return;
  }
  checkOne(value, location);
}

function checkQuestObjectiveTracker(file, tracker, location) {
  if (tracker === undefined) {
    return;
  }
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, tracker, location, new Set(["text", "complete_text", "text_key", "complete_text_key", "show_progress", "progress", "metadata"]));
  checkOptionalString(file, tracker, location, "text");
  checkOptionalString(file, tracker, location, "complete_text");
  checkOptionalString(file, tracker, location, "text_key");
  checkOptionalString(file, tracker, location, "complete_text_key");
  checkOptionalBoolean(file, tracker, location, "show_progress");
  checkOptionalNumber(file, tracker, location, "progress", { min: 0, max: 1 });
  checkStringMap(file, tracker.metadata, `${location}.metadata`);
}

function checkQuestRules(file, rules, location, defaultQuestId = "") {
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
    "completion_scope",
    "scope",
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
    "expiration",
    "exclusive_group",
    "branch_group",
    "exclusive_on",
    "exclusive_lock_on",
    "blocks",
    "blocks_on_start",
    "blocks_on_completion",
    "blocks_on_complete",
    "lock_on_start",
    "lock_on_completion",
    "lock_on_complete",
    "branch"
  ]));
  for (const key of ["repeatable", "locked_to_villager", "cross_villager_compatible", "consume_on_completion", "consume_on_abandonment"]) {
    checkOptionalBoolean(file, rules, location, key);
  }
  for (const key of ["max_starts", "max_completions", "completion_cooldown_ticks", "completion_cooldown_seconds", "completion_cooldown_days", "abandonment_cooldown_ticks", "abandonment_cooldown_seconds", "abandonment_cooldown_days"]) {
    checkOptionalInteger(file, rules, location, key, { min: 0 });
  }
  checkStringValues(file, rules, location, ["completion_scope", "scope"], questCompletionScopes, "quest completion scope");
  checkStringValues(file, rules, location, ["abandonment"], questAbandonmentModes, "quest abandonment mode");
  checkOptionalString(file, rules, location, "exclusive_group");
  checkOptionalString(file, rules, location, "branch_group");
  checkResourceIdValues(file, rules, location, ["exclusive_group", "branch_group"], "quest branch exclusive group");
  checkStringValues(file, rules, location, ["exclusive_on", "exclusive_lock_on"], questBranchLockEvents, "quest branch lock event");
  checkQuestReferenceList(
    file,
    rules,
    location,
    ["blocks", "blocks_on_start", "blocks_on_completion", "blocks_on_complete", "lock_on_start", "lock_on_completion", "lock_on_complete"],
    "quest branch lock"
  );
  checkQuestBranching(file, rules.branch, `${location}.branch`);
  checkQuestActive(file, rules.active, `${location}.active`, defaultQuestId);
  checkQuestExpiration(file, rules.expiration, `${location}.expiration`, defaultQuestId);
}

function checkQuestBranching(file, branch, location) {
  if (branch === undefined) {
    return;
  }
  if (!branch || typeof branch !== "object" || Array.isArray(branch)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, branch, location, new Set([
    "exclusive_group",
    "group",
    "exclusive_on",
    "lock_on",
    "blocks",
    "blocks_on_start",
    "blocks_on_completion",
    "blocks_on_complete",
    "lock_on_start",
    "lock_on_completion",
    "lock_on_complete"
  ]));
  checkOptionalString(file, branch, location, "exclusive_group");
  checkOptionalString(file, branch, location, "group");
  checkResourceIdValues(file, branch, location, ["exclusive_group", "group"], "quest branch exclusive group");
  checkStringValues(file, branch, location, ["exclusive_on", "lock_on"], questBranchLockEvents, "quest branch lock event");
  checkQuestReferenceList(
    file,
    branch,
    location,
    ["blocks", "blocks_on_start", "blocks_on_completion", "blocks_on_complete", "lock_on_start", "lock_on_completion", "lock_on_complete"],
    "quest branch lock"
  );
}

function checkQuestActive(file, active, location, defaultQuestId = "") {
  if (active === undefined) {
    return;
  }
  if (!active || typeof active !== "object" || Array.isArray(active)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, active, location, new Set(["conditions", "hide_when_unmet", "pause_progress_when_unmet"]));
  checkConditions(file, active, location, defaultQuestId);
  warnLiveOnlyQuestConditions(file, active.conditions, `${location}.conditions`, "quest active gate");
  checkOptionalBoolean(file, active, location, "hide_when_unmet");
  checkOptionalBoolean(file, active, location, "pause_progress_when_unmet");
}

function checkQuestExpiration(file, expiration, location, defaultQuestId = "") {
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
    "text",
    "text_key",
    "notification_text_key"
  ]));
  for (const key of ["after_ticks", "after_seconds", "after_days"]) {
    checkOptionalInteger(file, expiration, location, key, { min: 0 });
  }
  checkConditions(file, expiration, location, defaultQuestId);
  warnLiveOnlyQuestConditions(file, expiration.conditions, `${location}.conditions`, "quest expiration gate");
  for (const key of ["consume", "allow_repickup", "notify"]) {
    checkOptionalBoolean(file, expiration, location, key);
  }
  checkOptionalString(file, expiration, location, "notification");
  collectNotificationTriggerReference(file, `${location}.notification`, expiration.notification, "quest expiration notification");
  checkOptionalString(file, expiration, location, "text");
  checkOptionalString(file, expiration, location, "text_key");
  checkOptionalString(file, expiration, location, "notification_text_key");
}

function checkQuestTracker(file, tracker, location, objectiveIds = new Set()) {
  if (tracker === undefined) {
    return;
  }
  if (!tracker || typeof tracker !== "object" || Array.isArray(tracker)) {
    errors.push(`${relative(file)}: ${location} must be an object.`);
    return;
  }
  checkUnknownObjectKeys(file, tracker, location, new Set(["title", "title_key", "steps", "metadata"]));
  checkOptionalString(file, tracker, location, "title");
  checkOptionalString(file, tracker, location, "title_key");
  checkStringMap(file, tracker.metadata, `${location}.metadata`);
  if (tracker.steps !== undefined) {
    if (!tracker.steps || typeof tracker.steps !== "object" || Array.isArray(tracker.steps)) {
      errors.push(`${relative(file)}: ${location}.steps must be an object keyed by step id.`);
    } else {
      for (const [stepId, step] of Object.entries(tracker.steps)) {
        const stepLocation = `${location}.steps.${stepId}`;
        if (!objectiveIds.has(stepId) && !questTrackerStepKeys.has(stepId)) {
          warnings.push(`${relative(file)}: ${stepLocation} is not a quest objective id or runtime tracker step key; it may never be selected.`);
        }
        if (!step || typeof step !== "object" || Array.isArray(step)) {
          errors.push(`${relative(file)}: ${stepLocation} must be an object.`);
          continue;
        }
        checkUnknownObjectKeys(file, step, stepLocation, new Set(["text", "text_key", "show_progress", "progress", "metadata"]));
        checkOptionalString(file, step, stepLocation, "text");
        checkOptionalString(file, step, stepLocation, "text_key");
        checkOptionalBoolean(file, step, stepLocation, "show_progress");
        checkOptionalNumber(file, step, stepLocation, "progress", { min: 0, max: 1 });
        checkStringMap(file, step.metadata, `${stepLocation}.metadata`);
      }
    }
  }
}

function checkQuestStages(file, stages, location, defaultQuestId = "", objectiveIds = new Set()) {
  if (stages === undefined) {
    return;
  }
  if (!stages || typeof stages !== "object" || Array.isArray(stages)) {
    errors.push(`${relative(file)}: ${location} must be an object keyed by stage id.`);
    return;
  }

  const stageIds = new Set(Object.keys(stages).filter((stageId) => stageId.trim()));
  for (const [stageId, stage] of Object.entries(stages)) {
    const stageLocation = `${location}.${stageId}`;
    if (!stageId.trim()) {
      errors.push(`${relative(file)}: ${stageLocation} stage id must not be blank.`);
      continue;
    }
    if (!stage || typeof stage !== "object" || Array.isArray(stage)) {
      errors.push(`${relative(file)}: ${stageLocation} must be an object.`);
      continue;
    }
    checkUnknownObjectKeys(file, stage, stageLocation, new Set([
      "objective",
      "objectives",
      "complete_when",
      "next",
      "next_stage",
      "entry_actions",
      "exit_actions",
      "branches"
    ]));
    checkStringList(file, stage, stageLocation, ["objective", "objectives"], "quest objective id");
    checkStageObjectiveReferences(file, readValues(stage, ["objective", "objectives"]), objectiveIds, stageLocation);
    checkQuestStagePredicates(file, stage.complete_when, `${stageLocation}.complete_when`, defaultQuestId, objectiveIds);
    checkOptionalString(file, stage, stageLocation, "next");
    checkOptionalString(file, stage, stageLocation, "next_stage");
    checkStageReferences(file, readValues(stage, ["next", "next_stage"]), stageIds, stageLocation);
    checkDialogueTreeActions(file, stage.entry_actions, `${stageLocation}.entry_actions`, defaultQuestId, {
      liveContextWarningUsage: "quest stage entry action"
    });
    checkDialogueTreeActions(file, stage.exit_actions, `${stageLocation}.exit_actions`, defaultQuestId, {
      liveContextWarningUsage: "quest stage exit action"
    });
    checkQuestStageBranches(file, stage.branches, `${stageLocation}.branches`, defaultQuestId, stageIds);
  }
  checkUnreachableQuestStages(file, stages, location, stageIds);
}

function questObjectiveIds(objectives) {
  if (!Array.isArray(objectives)) {
    return new Set();
  }
  return new Set(objectives
    .filter((objective) => objective && typeof objective === "object" && !Array.isArray(objective))
    .map((objective) => stringValue(objective.id))
    .filter(Boolean));
}

function questStageIds(stages) {
  if (!stages || typeof stages !== "object" || Array.isArray(stages)) {
    return new Set();
  }
  return new Set(Object.keys(stages)
    .map((id) => id.trim())
    .filter(Boolean));
}

function checkQuestStagePredicates(file, predicates, location, defaultQuestId = "", objectiveIds = new Set()) {
  if (predicates === undefined) {
    return;
  }
  if (typeof predicates === "string") {
    checkStageObjectiveReferences(file, [predicates], objectiveIds, location);
    return;
  }
  if (predicates && typeof predicates === "object" && !Array.isArray(predicates)) {
    checkQuestStagePredicate(file, predicates, location, defaultQuestId, objectiveIds);
    return;
  }
  if (!Array.isArray(predicates)) {
    errors.push(`${relative(file)}: ${location} must be a string, object, or array.`);
    return;
  }
  predicates.forEach((predicate, index) => {
    checkQuestStagePredicate(file, predicate, `${location}[${index}]`, defaultQuestId, objectiveIds);
  });
}

function checkQuestStagePredicate(file, predicate, location, defaultQuestId = "", objectiveIds = new Set()) {
  if (typeof predicate === "string") {
    checkStageObjectiveReferences(file, [predicate], objectiveIds, location);
    return;
  }
  if (!predicate || typeof predicate !== "object" || Array.isArray(predicate)) {
    errors.push(`${relative(file)}: ${location} must be a stage predicate object or objective id string.`);
    return;
  }

  const type = normalizedString(predicate.type);
  const objectiveRefs = readValues(predicate, ["objective", "objective_id", "objectives"]);
  const idRef = (type === "objective" || type === "objectives") ? readValues(predicate, ["id"]) : [];
  if (objectiveRefs.length > 0 || idRef.length > 0 || type === "objective" || type === "objectives") {
    checkUnknownObjectKeys(file, predicate, location, new Set(["type", "objective", "objective_id", "objectives", "id"]));
    checkStringList(file, predicate, location, ["objective", "objective_id", "objectives", "id"], "quest objective id");
    const refs = [...objectiveRefs, ...idRef];
    if (refs.length === 0) {
      errors.push(`${relative(file)}: ${location} must define objective, objective_id, objectives, or id.`);
    }
    checkStageObjectiveReferences(file, refs, objectiveIds, location);
    return;
  }

  if (Object.hasOwn(predicate, "conditions")) {
    checkUnknownObjectKeys(file, predicate, location, new Set(["conditions"]));
    checkConditions(file, predicate, location, defaultQuestId);
    warnLiveOnlyQuestConditions(file, predicate.conditions, `${location}.conditions`, "quest stage complete_when predicate");
    return;
  }

  if (!type && looksLikeQuestFactStagePredicate(predicate)) {
    checkCondition(file, { ...predicate, type: "quest_fact" }, location, defaultQuestId);
    return;
  }

  checkCondition(file, predicate, location, defaultQuestId);
  warnLiveOnlyQuestCondition(file, predicate, location, "quest stage complete_when predicate");
}

function looksLikeQuestFactStagePredicate(predicate) {
  return [
    "tag",
    "tags",
    "fact_tag",
    "quest_tag",
    "key",
    "variable",
    "counter",
    "fact",
    "stage",
    "stages"
  ].some((key) => Object.hasOwn(predicate, key));
}

function checkQuestStageBranches(file, branches, location, defaultQuestId = "", stageIds = new Set()) {
  if (branches === undefined) {
    return;
  }
  if (!Array.isArray(branches)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }

  const branchIds = new Map();
  for (const [index, branch] of branches.entries()) {
    const branchLocation = `${location}[${index}]`;
    if (!branch || typeof branch !== "object" || Array.isArray(branch)) {
      errors.push(`${relative(file)}: ${branchLocation} must be an object.`);
      continue;
    }
    checkUnknownObjectKeys(file, branch, branchLocation, new Set([
      "id",
      "label",
      "label_key",
      "conditions",
      "actions",
      "next",
      "next_stage",
      "blocked_by"
    ]));
    checkOptionalString(file, branch, branchLocation, "id");
    checkOptionalString(file, branch, branchLocation, "label");
    checkOptionalString(file, branch, branchLocation, "label_key");
    checkOptionalString(file, branch, branchLocation, "next");
    checkOptionalString(file, branch, branchLocation, "next_stage");
    const branchId = stringValue(branch.id) || `branch_${index}`;
    if (branchIds.has(branchId)) {
      errors.push(`${relative(file)}: duplicate quest stage branch id "${branchId}" at ${branchIds.get(branchId)} and ${branchLocation}.`);
    }
    branchIds.set(branchId, branchLocation);
    checkConditions(file, branch, branchLocation, defaultQuestId);
    checkDialogueTreeActions(file, branch.actions, `${branchLocation}.actions`, defaultQuestId);
    checkStageReferences(file, readValues(branch, ["next", "next_stage"]), stageIds, branchLocation);
    if (
      (Array.isArray(branch.actions) || readValues(branch, ["next", "next_stage"]).length > 0)
      && !stringValue(branch.label)
      && !stringValue(branch.label_key)
    ) {
      warnings.push(`${relative(file)}: ${branchLocation} has actions or next stage but no label or label_key for auto-rendered branch dialogue.`);
    }
    checkQuestStageBranchBlockers(
      file,
      branch.blocked_by,
      `${branchLocation}.blocked_by`,
      defaultQuestId,
      Boolean(stringValue(branch.label) || stringValue(branch.label_key))
    );
  }
}

function checkQuestStageBranchBlockers(file, blockers, location, defaultQuestId = "", visibleBranch = false) {
  if (blockers === undefined) {
    return;
  }
  if (!Array.isArray(blockers)) {
    errors.push(`${relative(file)}: ${location} must be an array.`);
    return;
  }
  for (const [index, blocker] of blockers.entries()) {
    const blockerLocation = `${location}[${index}]`;
    if (!blocker || typeof blocker !== "object" || Array.isArray(blocker)) {
      errors.push(`${relative(file)}: ${blockerLocation} must be an object.`);
      continue;
    }
    checkUnknownObjectKeys(file, blocker, blockerLocation, new Set(["conditions", "reason", "reason_key"]));
    checkConditions(file, blocker, blockerLocation, defaultQuestId);
    checkOptionalString(file, blocker, blockerLocation, "reason");
    checkOptionalString(file, blocker, blockerLocation, "reason_key");
    if (
      visibleBranch
      && Array.isArray(blocker.conditions)
      && blocker.conditions.length > 0
      && !stringValue(blocker.reason)
      && !stringValue(blocker.reason_key)
    ) {
      warnings.push(`${relative(file)}: ${blockerLocation} blocks a visible quest stage branch without reason or reason_key.`);
    }
  }
}

function checkUnreachableQuestStages(file, stages, location, stageIds) {
  if (stageIds.size === 0) {
    return;
  }
  const initialStage = stageIds.has("started") ? "started" : [...stageIds][0];
  const reachable = new Set();
  const pending = [initialStage];
  while (pending.length > 0) {
    const stageId = pending.pop();
    if (reachable.has(stageId)) {
      continue;
    }
    reachable.add(stageId);
    const stage = stages[stageId];
    if (!stage || typeof stage !== "object" || Array.isArray(stage)) {
      continue;
    }
    for (const nextStage of stageNextReferences(stage)) {
      if (stageIds.has(nextStage) && !reachable.has(nextStage)) {
        pending.push(nextStage);
      }
    }
  }

  for (const stageId of stageIds) {
    if (!reachable.has(stageId)) {
      warnings.push(`${relative(file)}: ${location}.${stageId} is not reachable from initial quest stage "${initialStage}".`);
    }
  }
}

function stageNextReferences(stage) {
  const refs = [];
  for (const value of readValues(stage, ["next", "next_stage"])) {
    if (typeof value === "string" && value.trim()) {
      refs.push(value.trim());
    }
  }
  if (Array.isArray(stage.branches)) {
    for (const branch of stage.branches) {
      if (!branch || typeof branch !== "object" || Array.isArray(branch)) {
        continue;
      }
      for (const value of readValues(branch, ["next", "next_stage"])) {
        if (typeof value === "string" && value.trim()) {
          refs.push(value.trim());
        }
      }
    }
  }
  return refs;
}

function checkStageObjectiveReferences(file, refs, objectiveIds, location) {
  for (const ref of refs) {
    if (typeof ref !== "string" || !ref.trim()) {
      continue;
    }
    if (!objectiveIds.has(ref.trim())) {
      errors.push(`${relative(file)}: ${location} references unknown quest objective "${ref}".`);
    }
  }
}

function checkStageReferences(file, refs, stageIds, location) {
  for (const ref of refs) {
    if (typeof ref !== "string" || !ref.trim()) {
      continue;
    }
    if (!stageIds.has(ref.trim())) {
      errors.push(`${relative(file)}: ${location} references unknown quest stage "${ref}".`);
    }
  }
}

function checkQuestTriggers(file, triggers, location, defaultQuestId = "", stageIds = new Set()) {
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
      "stage",
      "stages",
      "repeatable"
    ]));
    checkStringValues(file, trigger, triggerLocation, ["event"], questTriggerEvents, "quest trigger event", { requireAny: true });
    const event = normalizedString(trigger.event);
    checkConditions(file, trigger, triggerLocation, defaultQuestId);
    const stageRefs = readValues(trigger, ["stage", "stages"]);
    checkStringList(file, trigger, triggerLocation, ["stage", "stages"], "quest trigger stage");
    const nonblankStageRefs = stageRefs.filter((stageRef) => typeof stageRef === "string" && stageRef.trim());
    if (nonblankStageRefs.length > 0) {
      if (stageIds.size === 0) {
        errors.push(`${relative(file)}: ${triggerLocation} references quest stages, but the quest does not define stages.`);
      } else {
        checkStageReferences(file, nonblankStageRefs, stageIds, triggerLocation);
      }
    }
    checkDialogueTreeActions(file, trigger.actions, `${triggerLocation}.actions`, defaultQuestId, {
      liveContextWarningUsage: questTriggerEventsThatMayLackLiveIssuer.has(event) ? "quest trigger action" : ""
    });
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
  collectLootTableReferences(file, rewards, location, ["loot_table"], "quest rewards");
  checkMemoryTagReferences(file, rewards, location, ["memory_event"], "quest rewards");
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
      questId: questIdForFile(file, data),
      location,
      treeId,
      offer,
      reminder,
      turnIn,
      metadataQuest: stringValue(metadataObject(data).quest) || questIdForFile(file, data)
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
    ...questDialogueStages,
    ...questDialogueStages.map((key) => `${key}_key`),
    ...questDialogueStages.map((key) => `${key}_keys`)
  ]));

  for (const key of questDialogueStages) {
    checkQuestDialogueStage(file, dialogue, location, key);
    checkStringList(file, dialogue, location, [`${key}_key`, `${key}_keys`], "quest dialogue text key");
  }
}

function checkQuestDialogueStage(file, dialogue, location, key) {
  const value = dialogue[key];
  if (value === undefined) {
    return;
  }
  if (typeof value === "string" || Array.isArray(value)) {
    checkStringList(file, dialogue, location, [key], "quest dialogue line");
    return;
  }
  const stageLocation = `${location}.${key}`;
  if (!value || typeof value !== "object") {
    errors.push(`${relative(file)}: ${stageLocation} must be a string, array of strings, or object.`);
    return;
  }
  checkUnknownObjectKeys(file, value, stageLocation, new Set(["text", "texts", "line", "lines", "text_key", "text_keys"]));
  checkStringList(file, value, stageLocation, ["text", "texts", "line", "lines"], "quest dialogue line");
  checkStringList(file, value, stageLocation, ["text_key", "text_keys"], "quest dialogue text key");
  if (
    readValues(value, ["text", "texts", "line", "lines", "text_key", "text_keys"]).length === 0
  ) {
    errors.push(`${relative(file)}: ${stageLocation} must define text, lines, text_key, or text_keys.`);
  }
}

function checkDialogueTree(file, data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    errors.push(`${relative(file)}: dialogue tree root must be an object.`);
    return;
  }
  const defaultQuestId = dialogueTreeDefaultQuestId(file, data);
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

function checkDialogueTreeNodeFields(file, node, location) {
  checkOptionalString(file, node, location, "id");
  checkStringList(file, node, location, ["lines"], "dialogue tree node line");
  checkOptionalString(file, node, location, "text");
  checkOptionalBoolean(file, node, location, "end");
}

function checkDialogueTreeResponseFields(file, response, location) {
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
  if (entry.label === undefined) {
    warnings.push(`${relative(file)}: ${location}.label is missing; dialogue tree entry will not appear as a player option.`);
  }
  if (entry.show_for_adults === false && entry.show_for_babies === false) {
    warnings.push(`${relative(file)}: ${location} disables both adult and baby audiences and can never match.`);
  }
}

function warnInvisibleDialogueTreeResponse(file, response, location) {
  if (response.label === undefined) {
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
    warnings.push(`${relative(file)}: ${location}.end is ignored because next continues to "${stringValue(response.next)}".`);
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
    for (const key of Object.keys(action)) {
      if (!dialogueTreeActionKeys.has(key)) {
        errors.push(`${relative(file)}: ${actionLocation}.${key} is not a supported dialogue action field.`);
      }
    }
    const type = actionType(action, defaultQuestId);
    if (!dialogueTreeActionTypes.has(type)) {
      errors.push(`${relative(file)}: ${actionLocation}.type must be one of ${[...dialogueTreeActionTypes].join(", ")}.`);
    }
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
  } else if (type === "quest_fact" || type === "quest_tag" || type === "quest_variable" || type === "quest_counter" || type === "quest_stage" || type === "fact" || type === "stage") {
    checkQuestFactCondition(file, condition, location, defaultQuestId);
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

function checkResourceSelectorValues(file, entry, location, keys, label) {
  for (const value of readValues(entry, keys)) {
    if (typeof value !== "string" || !value.trim()) {
      continue;
    }
    const raw = value.trim();
    const parsed = parseResourceId(raw.startsWith("#") ? raw.slice(1) : raw);
    if (!parsed || !parsed.valid) {
      errors.push(`${relative(file)}: ${location} has invalid ${label} "${value}".`);
    }
  }
}

function checkQuestReferenceList(file, entry, location, keys, reason) {
  checkStringList(file, entry, location, keys, "quest id");
  for (const questId of readValues(entry, keys)) {
    if (typeof questId === "string" && questId.trim()) {
      pendingQuestReferences.push({
        file,
        location,
        id: questId.trim(),
        reason
      });
    }
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

function indexQuest(file, data) {
  const questId = questIdForFile(file, data);
  if (!questId) {
    return;
  }
  const previous = questDefinitions.get(questId);
  if (previous) {
    errors.push(`${relative(file)}: duplicate quest id "${questId}" also defined in ${previous.file}.`);
    return;
  }
  questDefinitions.set(questId, {
    file: relative(file),
    parent: stringValue(data.parent),
    questline: stringValue(data.questline),
    pathQuestline: questPathQuestline(file),
    stageIds: questStageIds(data.stages),
    requiresDialogueTree: questHasLifecycleDialogue(data)
  });
}

function questHasLifecycleDialogue(data) {
  if (!data || data.remove === true || (data.replace === true && isControlOnly(data, ["replace", "metadata"]))) {
    return false;
  }
  const dialogue = data.dialogue;
  if (!dialogue || typeof dialogue !== "object" || Array.isArray(dialogue)) {
    return false;
  }
  return readValues(dialogue, [
    "start",
    "start_key",
    "start_keys",
    "reminder",
    "reminder_key",
    "reminder_keys",
    "turn_in",
    "turn_in_key",
    "turn_in_keys"
  ]).length > 0;
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

function checkQuestMetadataConsistency(file, data, location, defaultQuestId = "") {
  const metadata = metadataObject(data);
  const metadataQuestline = stringValue(metadata.questline);
  const metadataQuest = stringValue(metadata.quest);
  const questline = stringValue(data.questline);
  const questId = defaultQuestId || stringValue(data.id);

  if (metadataQuestline && !metadataQuest) {
    warnings.push(`${relative(file)}: ${location}.metadata.questline is set without metadata.quest; the quest id will be inferred from the file path or id.`);
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
  validateQuestDialogueTreeCoverage();
  validateForcedDialogueQuestModules();
  validateDialogueMessageKeyReferences();
  validateForcedDialogueMessageKeyReferences();
  validateLootTableReferences();
  validateNotificationTriggerReferences();
  validateExternalDialogueSceneReferences();

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

function validateExternalDialogueSceneReferences() {
  for (const reference of pendingExternalDialogueSceneReferences) {
    const tree = dialogueTreeDefinitions.get(reference.treeId);
    if (!tree) {
      errors.push(questV2Issue(
        reference.file,
        "error",
        reference.pointer,
        reference.location,
        `external dialogue tree "${reference.treeId}" does not exist.`,
        "Create the dialogue tree or update the external_scene tree id."
      ));
      continue;
    }
    if (reference.entry && !tree.entryIds.has(reference.entry)) {
      errors.push(questV2Issue(
        reference.file,
        "error",
        reference.pointer,
        reference.location,
        `external dialogue tree "${reference.treeId}" has no entry "${reference.entry}".`,
        "Use an entry id defined by the referenced dialogue tree."
      ));
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

function validateQuestDialogueTreeCoverage() {
  for (const [questId, quest] of questDefinitions) {
    if (!quest.requiresDialogueTree || dialogueTreeDefinitions.has(questId)) {
      continue;
    }
    warnings.push(`${quest.file}: quest defines lifecycle dialogue but has no matching quest dialogue tree "${questId}".`);
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
