const CONSTANTS = {
  professions: [
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
    "weaponsmith",
    "none",
    "unemployed"
  ],
  dispositions: ["friendly", "respectful", "neutral", "cautious", "rude", "hostile", "fearful"],
  moods: ["neutral", "content", "grateful", "afraid", "angry", "suspicious", "grieving", "protective", "hopeful", "stressed", "proud", "lonely"],
  socialAttributes: ["knowledge", "guts", "proficiency", "kindness", "charm"],
  dialogueTypes: [
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
  ],
  notificationTriggers: [
    "gift.liked",
    "gift.neutral",
    "gift.disliked",
    "gift.received_item",
    "gift.high_reputation",
    "gift.world.liked",
    "gift.world.neutral",
    "gift.world.disliked",
    "dialogue.greeting",
    "dialogue.question",
    "dialogue.cooldown",
    "dialogue.joke.positive",
    "dialogue.insult.negative",
    "dialogue.map.found",
    "dialogue.rumor.found",
    "quest.started",
    "quest.updated",
    "quest.location_reached",
    "quest.completed",
    "quest.abandoned",
    "recruitment.follow_start",
    "recruitment.follow_stop",
    "recruitment.hired",
    "recruitment.fired",
    "recruitment.follower_death",
    "recruitment.hired_death",
    "recruitment.betrayed_follower_death",
    "ambient.murmur",
    "ambient.player_item",
    "ambient.sleep_breathing",
    "ambient.sleep_murmur",
    "combat.retaliation_started",
    "combat.flee_started",
    "combat.attack_landed",
    "combat.player_killed",
    "trade.completed",
    "trade.refused",
    "alert.player_attacked_villager",
    "alert.villager_damaged",
    "alert.witness_attack.player",
    "alert.witness_attack",
    "alert.witness_death.player",
    "alert.witness_death",
    "reputation.tier.royalty.improved",
    "reputation.tier.royalty.worsened",
    "reputation.tier.revered.improved",
    "reputation.tier.revered.worsened",
    "reputation.tier.respected.improved",
    "reputation.tier.respected.worsened",
    "reputation.tier.trusted.improved",
    "reputation.tier.trusted.worsened",
    "reputation.tier.neutral.improved",
    "reputation.tier.neutral.worsened",
    "reputation.tier.suspicious.improved",
    "reputation.tier.suspicious.worsened",
    "reputation.tier.hostile.improved",
    "reputation.tier.hostile.worsened",
    "reputation.tier.despised.improved",
    "reputation.tier.despised.worsened",
    "reputation.tier.feared.improved",
    "reputation.tier.feared.worsened"
  ],
  forcedDialogueTriggers: [
    "container_theft",
    "container_opened",
    "container_broken",
    "retaliation_started",
    "player_item_proximity",
    "trade_refresh"
  ],
  forcedOutputModes: ["forced_dialogue", "chat"],
  reputationLevels: ["royalty", "revered", "respected", "trusted", "neutral", "suspicious", "hostile", "despised", "feared"],
  hudKinds: [
    "default",
    "map_discovery",
    "received_item",
    "gift_liked",
    "gift_neutral",
    "gift_disliked",
    "villager_following",
    "villager_dismissed",
    "villager_hired",
    "villager_fired",
    "villager_death",
    "quest"
  ],
  worldTextKinds: ["alert", "murmur", "positive", "negative", "trade", "dialogue", "sleep"],
  eventTags: [
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
    "villager_retaliation_started"
  ],
  itemSlots: ["main_hand", "off_hand", "hands", "armor", "hotbar", "inventory", "equipment", "any"],
  dialogueItemDestinations: ["discard", "villager_inventory", "drop_at_villager"],
  forcedItemDestinations: ["discard", "villager_inventory", "villager_inventory_then_source_container", "source_container", "drop_at_villager", "drop_at_container"],
  weather: ["clear", "rain", "thunder"],
  times: ["morning", "afternoon", "evening", "night"],
  giftAdvice: ["global_liked", "global_disliked", "profession_liked", "profession_disliked", "already_known"],
  reactions: ["loved", "liked", "neutral", "disliked", "hated"],
  colors: [
    "white",
    "gray",
    "grey",
    "dark_gray",
    "black",
    "red",
    "dark_red",
    "green",
    "dark_green",
    "blue",
    "aqua",
    "yellow",
    "gold",
    "purple",
    "light_purple"
  ],
  pacifyOutcomes: ["not_applicable", "success", "not_enough_emeralds", "blocked_by_reputation"],
  optionFlags: [
    "force_camera_towards_villager",
    "requires_unreported_cartographer_map_discovery",
    "requires_unreported_story_hint_discovery",
    "requires_unreported_combat_survival_report",
    "requires_unreported_gear_report",
    "requires_unreported_recruitment_followup",
    "requires_unreported_cured_recognition",
    "requires_recent_village_event",
    "requires_unreported_gift_advice_result",
    "requires_unapologized_remembered_harm",
    "requires_unreported_village_defense",
    "requires_shareable_story",
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
  ],
  lineFlags: [
    "requires_recent_broken_bed_memory",
    "requires_recent_direct_hit_memory",
    "requires_container_theft_to_self",
    "requires_container_theft_from_other",
    "requires_retaliation_to_self",
    "requires_retaliation_from_other",
    "requires_gear_report_used_in_combat",
    "requires_gear_report_unused_in_combat",
    "requires_recruitment_memory",
    "requires_recruitment_boat_trip",
    "requires_recruitment_ocean_crossing",
    "requires_recruitment_swim_trip",
    "excludes_recruitment_ocean_crossing",
    "first_conversation_only",
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
  ]
};

const DIALOGUE_KINDS = [
  { key: "options", label: "Options", icon: "list-checks" },
  { key: "lines", label: "Lines", icon: "message-square" },
  { key: "messages", label: "Messages", icon: "message-circle" },
  { key: "openings", label: "Openings", icon: "door-open" },
  { key: "closings", label: "Closings", icon: "door-closed" },
  { key: "pacify", label: "Pacify Lines", icon: "hand-heart" }
];
const DIALOGUE_KIND_KEYS = DIALOGUE_KINDS.map((kind) => kind.key);
const DIALOGUE_LAYOUTS = [
  { value: "folders", label: "Typed folders" },
  { value: "bundle", label: "Single bundle file" }
];

const GIFT_KINDS = [
  { key: "preferences", label: "Preferences", icon: "heart" },
  { key: "rewards", label: "Rewards", icon: "package-plus" }
];

const STORY_KINDS = [
  { key: "structures", label: "Structures", icon: "landmark" },
  { key: "biomes", label: "Biomes", icon: "trees" }
];

const PACK_VERSIONS = [
  {
    id: "1.0.0-beta.11",
    label: "VR 1.0.0-beta.11",
    packFormat: 34,
    feature: "beta.11"
  },
  {
    id: "1.0.0-beta.12",
    label: "VR 1.0.0-beta.12",
    packFormat: 34,
    feature: "beta.12"
  }
];

const CURRENT_PACK_VERSION = PACK_VERSIONS[PACK_VERSIONS.length - 1].id;
const PACK_VERSION_IDS = PACK_VERSIONS.map((version) => version.id);
const PACK_VERSION_STORAGE_KEY = "pack_version";
const PACK_VERSION_NAMESPACE = "villagerretaliation";
const COLLAPSIBLE_TOGGLE_MIN_COUNT = 8;
const ENTRY_PAGE_SIZE = 250;
const WIKI_PAGE_FILES = [
  "Home.md",
  "Pack-Development.md",
  "Datapack-Generator.md",
  "Pack-Format-Changes.md",
  "JSON-Reference.md",
  "Dialogue.md",
  "Forced-Dialogue.md",
  "Dialogue-Requests.md",
  "Event-Tags.md",
  "Notifications.md",
  "Notification-Triggers.md",
  "Localization.md",
  "Gifts.md",
  "Pacification.md",
  "Profession-Loot.md",
  "Story-Discovery.md",
  "Villager-Names.md",
  "Resource-Pack-Models.md",
  "Example-Packs.md"
];

const TEMPLATE_CHOICES = [
  {
    id: "starter",
    label: "Starter Pack",
    detail: "Small editable pack with dialogue, gifts, events, stories, and notifications.",
    icon: "package-plus"
  },
  {
    id: "dialogue-folder",
    label: "Dialogue Folder Template",
    detail: "Beta.12 folder layout with every dialogue request and typed dialogue folder represented.",
    icon: "folder-open"
  }
];

const KIND_TOOLTIPS = {
  "dialogue.options": "Dialogue options add player choices to the villager talk menu. The option id is what matching lines use through option or option_ids.",
  "dialogue.lines": "Dialogue lines are selected for a request type, then filtered by option, profession, disposition, memories, items, weather, time, and weight.",
  "dialogue.messages": "Messages are keyed one-off text used by systems such as gifts. Gift response_key values look up matching message keys.",
  "dialogue.openings": "Openings are localized lines used when a conversation starts.",
  "dialogue.closings": "Closings are localized lines used when a conversation ends.",
  "dialogue.pacify": "Pacify lines are localized responses shown after a pacification attempt and can filter by pacification outcome.",
  "gifts.preferences": "Preferences choose gift reactions from item or tag selectors. Higher priority wins, then earlier rule order.",
  "gifts.rewards": "Rewards define item rolls for trusted, respected, revered, or royalty villagers and can filter by profession and reputation tier.",
  "stories.structures": "Structure entries live under data/<namespace>/story_structures and unlock share_story lines for structure targets.",
  "stories.biomes": "Biome entries live under data/<namespace>/story_biomes and unlock share_story lines for biome targets."
};

const FIELD_TOOLTIPS = {
  "meta-packName": "Used for the export zip name and project label. It does not change datapack paths.",
  "meta-packFormat": "Written to pack.mcmeta as pack.pack_format. This is Minecraft's datapack format, separate from the VR version target.",
  "meta-packVersion": "Targets a Villager Retaliation datapack format. Imported beta.11+ packs generated by this builder select this automatically. Beta.12 is a breaking authoring target; rebuild or manually adapt older packs before selecting it.",
  "meta-namespace": "Used for generated story discovery files. Dialogue, notifications, gifts, pacification, and preset names must stay in the villagerretaliation namespace.",
  "meta-slug": "Lowercase file stem used for generated file names and starter ids. Keep it stable if other files refer to those ids.",
  "meta-locale": "Locale folder for dialogue and notifications. The mod loads en_us first, then overlays the player's locale by matching ids.",
  "meta-description": "Text Minecraft shows in the datapack list inside pack.mcmeta.",
  "dialogue-layout": "Typed folders write beta.12 dialogue as focused JSON files under section folders. Single bundle file writes all dialogue sections to one JSON file.",
  "dialogue-folderName": "Folder path used under data/villagerretaliation/dialogue/<locale>/ before the typed section folder.",
  "dialogue-fileName": "Single bundle output creates data/villagerretaliation/dialogue/<locale>/<file>.json. Avoid global unless intentionally replacing built-in dialogue.",
  "dialogue-locale": "Locale folder for this dialogue file, such as en_us or fr_fr. Matching ids can override the en_us fallback.",
  "dialogue-id": "Stable id for generated, translated, overridden, or removed entries. Explicit ids survive array reordering.",
  "dialogue-label": "Text shown on the talk-menu button for this option.",
  "dialogue-type": "Request sent by a dialogue option and matched by response lines.",
  "dialogue-order": "Lower values appear earlier in the talk menu. If omitted, array order is used.",
  "dialogue-professions": "Profession filter. Vanilla ids can omit minecraft:, custom professions need their full registered id, and blank means any profession.",
  "dialogue-dispositions": "Legacy dialogue disposition filter derived from reputation and context: friendly, respectful, neutral, cautious, rude, hostile, or fearful.",
  "dialogue-moods": "Beta.12 temporary mood filter for dialogue lines: neutral, content, grateful, afraid, angry, suspicious, grieving, protective, hopeful, stressed, proud, or lonely.",
  "dialogue-min_mood_intensity": "Beta.12 minimum temporary mood intensity from 0 to 100. Only applies when mood or moods is set.",
  "dialogue-requires_high_knowledge": "Beta.12 shorthand for Knowledge 60 or higher.",
  "dialogue-requires_high_guts": "Beta.12 shorthand for Guts 60 or higher.",
  "dialogue-requires_high_proficiency": "Beta.12 shorthand for Proficiency 60 or higher.",
  "dialogue-requires_high_kindness": "Beta.12 shorthand for Kindness 60 or higher.",
  "dialogue-requires_high_charm": "Beta.12 shorthand for Charm 60 or higher.",
  "dialogue-min_knowledge": "Beta.12 minimum Knowledge social attribute score from 1 to 100.",
  "dialogue-max_knowledge": "Beta.12 maximum Knowledge social attribute score from 1 to 100.",
  "dialogue-min_guts": "Beta.12 minimum Guts social attribute score from 1 to 100.",
  "dialogue-max_guts": "Beta.12 maximum Guts social attribute score from 1 to 100.",
  "dialogue-min_proficiency": "Beta.12 minimum Proficiency social attribute score from 1 to 100.",
  "dialogue-max_proficiency": "Beta.12 maximum Proficiency social attribute score from 1 to 100.",
  "dialogue-min_kindness": "Beta.12 minimum Kindness social attribute score from 1 to 100.",
  "dialogue-max_kindness": "Beta.12 maximum Kindness social attribute score from 1 to 100.",
  "dialogue-min_charm": "Beta.12 minimum Charm social attribute score from 1 to 100.",
  "dialogue-max_charm": "Beta.12 maximum Charm social attribute score from 1 to 100.",
  "dialogue-reputation_levels": "Exact reputation tier filter for dialogue options and lines. Prefer tier names over fixed numeric reputation.",
  "dialogue-min_reputation": "Minimum exact reputation value required for dialogue options and lines.",
  "dialogue-max_reputation": "Maximum exact reputation value allowed for dialogue options and lines.",
  "dialogue-player_items": "Requires one matching player item or item tag. Prefix tags with #; aliases such as player_item_tag are accepted by the loader.",
  "dialogue-player_item_slots": "Where to check player items. If player_items is set and slots are blank, the default is hands.",
  "dialogue-min_player_item_durability": "Minimum remaining durability required on the matched player item.",
  "dialogue-max_player_item_durability": "Maximum remaining durability allowed on the matched player item.",
  "dialogue-min_player_item_durability_percent": "Minimum remaining durability percent required on the matched player item.",
  "dialogue-max_player_item_durability_percent": "Maximum remaining durability percent allowed on the matched player item.",
  "dialogue-player_item_enchantments": "Requires one matching enchantment on the matched player item. Object entries with id, min_level, and max_level are also supported in raw JSON.",
  "dialogue-min_player_item_enchantment_level": "Minimum level required for the matched enchantment.",
  "dialogue-max_player_item_enchantment_level": "Maximum level allowed for the matched enchantment.",
  "dialogue-give_items": "Optional item hand-in for this option. Use item/items or tag/tags plus count. destination can be discard, villager_inventory, or drop_at_villager.",
  "dialogue-text": "Localized villager text. Enter one variation per line. Placeholder support depends on type and filters, such as {target}, {held_item}, family names, or recruitment values.",
  "dialogue-text_key": "Beta.12+. Optional message key for line text. Use this to keep line filters separate from localized text variants in messages.",
  "dialogue-topic": "Optional author metadata. Groups related dialogue without changing runtime selection.",
  "dialogue-tags": "Optional author metadata tags for searching, organizing story packs, and future quest hooks.",
  "dialogue-questline": "Optional narrative metadata id. The current runtime records it for authoring and future quest systems.",
  "dialogue-quest": "Optional quest metadata id. It does not affect current dialogue matching.",
  "dialogue-stage": "Optional narrative stage or chapter id. It does not affect current dialogue matching.",
  "dialogue-notes": "Optional author notes kept in JSON for pack maintainers.",
  "dialogue-option": "Restricts a line to option id(s), including custom ids or built-ins such as adult_share_story.",
  "dialogue-weather": "Weather filter for lines: clear, rain, or thunder.",
  "dialogue-times": "Time filter for lines: morning, afternoon, evening, or night.",
  "dialogue-conditions": "Optional beta.12+ condition blocks for compound option or line logic. Supports all_of, any_of, not, reputation, memory, family, relationship, recruitment_memory, villager_age, weather, and time.",
  "dialogue-event_tags": "Requires a recent nearby village memory with a matching event tag.",
  "dialogue-player_event_tags": "Requires a recent village memory associated with the current player.",
  "dialogue-retaliation_target_entity_types": "Restricts retaliation-memory lines to recent villager retaliation targets such as minecraft:player or minecraft:zombie.",
  "dialogue-requires_villager_unarmed": "Requires the speaking villager to have no usable weapon in either hand.",
  "dialogue-requires_villager_armed": "Requires the speaking villager to have a usable weapon in either hand.",
  "dialogue-story_structure": "Restricts share_story lines to one or more structure ids from story discovery JSON.",
  "dialogue-story_biome": "Restricts share_story lines to one or more biome ids from story discovery JSON.",
  "dialogue-recruitment_followup_scenarios": "Filters recruitment follow-up lines by stored scenario ids.",
  "dialogue-recruitment_memory_scenarios": "Filters recruitment memory lines by stored scenario ids.",
  "dialogue-recruitment_memory_biomes": "Filters recruitment memory lines by remembered biome ids (for example minecraft:badlands).",
  "dialogue-min_recruitment_follow_distance": "Minimum followed distance in blocks for recruitment memory lines.",
  "dialogue-gift_advice": "Filters a line to a gift advice result such as global_liked, profession_disliked, or already_known.",
  "dialogue-priority": "Beta.12+. Normal dialogue line priority. Higher priority candidates are chosen before weighted random selection. Defaults to 0.",
  "dialogue-category": "Beta.12+. Optional author label shown by dialogue explain. Does not affect matching or selection.",
  "dialogue-weight": "Weighted selection among matching entries. Missing weights usually default to 10.",
  "dialogue-key": "Message lookup key used by systems such as gift preference response_key.",
  "dialogue-outcomes": "Pacification result filter, such as success, not_enough_emeralds, blocked_by_reputation, or not_applicable.",
  "forcedDialogue-fileName": "Creates data/villagerretaliation/forced_dialogue/<file>.json. Use default only when replacing the built-in theft confrontation.",
  "forced-id": "Stable id for this forced dialogue rule. Duplicate ids can override or collide depending on load order.",
  "forced-trigger": "Event trigger for this forced dialogue entry. Use Output mode to choose how the line is delivered.",
  "forced-output_mode": "Delivery channel for the event line. forced_dialogue opens the locked interaction screen; chat sends villager-styled chat.",
  "forced-output_radius": "Radius for chat output. Leave blank to use the configured forced-dialogue chat distance.",
  "forced-line": "Villager line shown when the event fires. If Initiates dialogue is off, this is sent as villager-styled chat only. Put each variation on its own line.",
  "forced-priority": "Lower priority wins when multiple forced dialogue rules match the same event.",
  "forced-witness_radius": "Maximum block distance for witnesses to detect the event.",
  "forced-reputation": "Optional reputation change applied when this rule runs.",
  "forced-witness_professions": "Optional profession ids for the witnessing villager, such as armorer or minecraft:weaponsmith.",
  "forced-requires_witness_unarmed": "Requires the witnessing villager to have no usable weapon in either hand.",
  "forced-requires_witness_armed": "Requires the witnessing villager to have a usable weapon in either hand.",
  "forced-player_items": "For player_item_proximity, requires the nearby player to carry one matching item or item tag. Prefix tags with #.",
  "forced-player_item_slots": "Where to check player items. Defaults to hands when player_items is set.",
  "forced-min_player_item_durability": "Minimum remaining durability required on the matched player item.",
  "forced-max_player_item_durability": "Maximum remaining durability allowed on the matched player item.",
  "forced-min_player_item_durability_percent": "Minimum remaining durability percent required on the matched player item.",
  "forced-max_player_item_durability_percent": "Maximum remaining durability percent allowed on the matched player item.",
  "forced-player_item_enchantments": "Requires one matching enchantment on the matched player item. Object entries with id, min_level, and max_level are also supported in raw JSON.",
  "forced-min_player_item_enchantment_level": "Minimum level required for the matched enchantment.",
  "forced-max_player_item_enchantment_level": "Maximum level allowed for the matched enchantment.",
  "forced-chance": "Random chance from 0.0 to 1.0 before a matching event line is shown.",
  "forced-target_entity_types": "Optional retaliation target entity ids such as minecraft:player. Useful for retaliation_started entries.",
  "forced-min_recent_retaliations": "Optional minimum earlier villager_retaliation_started memories for this player near the villager's village.",
  "forced-max_recent_retaliations": "Optional maximum earlier villager_retaliation_started memories for this player near the villager's village.",
  "forced-initiate_dialogue": "Opens the locked interaction menu when enabled for forced_dialogue output.",
  "forced-force_camera_towards_villager": "Smoothly turns the player's camera toward the witnessing villager while this forced dialogue is active.",
  "forced-options_json": "JSON array of player response options. Each option can use response or responses, plus success_response/success_responses and failure_response/failure_responses for payments or stolen-item returns.",
  "forced-leave_option_json": "Optional JSON object or array for forced Leave/Escape outcomes. Uses option fields such as label, response, reputation, aggro_chance, take_stolen_items, and reputation_levels.",
  "notifications-fileName": "Creates data/villagerretaliation/notifications/<locale>/<file>.json. Avoid global unless intentionally replacing built-in notifications.",
  "notifications-locale": "Locale folder for this notification file. en_us loads first, then the player's locale overlays matching ids.",
  "notification-id": "Stable id for translation overlays and replacement. Generated ids work, but explicit ids are safer.",
  "notification-trigger": "Event trigger emitted by the mod, such as gift.liked, combat.retaliation_started, trade.refused, or alert.witness_death.",
  "notification-text": "Localized HUD or world text. Enter one variation per line. Supported placeholders depend on the trigger.",
  "notification-kind": "HUD notification category. Defaults to default when omitted.",
  "notification-world_text_kind": "Ambient text style above villagers. The loader also accepts style as an alias.",
  "notification-color": "Default color for text and chat unless text_color or chat_color is more specific. Accepts named colors, #RRGGBB, or #AARRGGBB.",
  "notification-text_color": "On-screen text color override. Falls back to color when omitted.",
  "notification-chat_color": "Chat/log color override. Falls back to text_color, then color.",
  "notification-professions": "Profession filter for this notification. Blank means any profession.",
  "notification-requires_villager_unarmed": "Requires the notification villager to have no usable weapon in either hand.",
  "notification-requires_villager_armed": "Requires the notification villager to have a usable weapon in either hand.",
  "notification-reputation_levels": "Reputation tier filter. Prefer tier names over assuming fixed numeric thresholds.",
  "notification-min_reputation": "Minimum exact reputation value required.",
  "notification-max_reputation": "Maximum exact reputation value allowed.",
  "notification-player_items": "Requires one matching player item or item tag before this notification can match.",
  "notification-player_item_slots": "Where to check player items. Defaults to hands when player_items is set.",
  "notification-min_player_item_durability": "Minimum remaining durability required on the matched player item.",
  "notification-max_player_item_durability": "Maximum remaining durability allowed on the matched player item.",
  "notification-min_player_item_durability_percent": "Minimum remaining durability percent required on the matched player item.",
  "notification-max_player_item_durability_percent": "Maximum remaining durability percent allowed on the matched player item.",
  "notification-player_item_enchantments": "Requires one matching enchantment on the matched player item. Object entries with id, min_level, and max_level are also supported in raw JSON.",
  "notification-min_player_item_enchantment_level": "Minimum level required for the matched enchantment.",
  "notification-max_player_item_enchantment_level": "Maximum level allowed for the matched enchantment.",
  "notification-weight": "Weighted selection among matching notifications. Missing weights usually default to 10.",
  "notification-chance": "Random chance gate from 0.0 to 1.0 before weighted selection.",
  "gifts-fileName": "Creates data/villagerretaliation/gifts/<file>.json. Use default only when replacing the built-in default gift table.",
  "gift-reaction": "Gift reaction: loved, liked, neutral, disliked, or hated. Each has a default reputation per item.",
  "gift-priority": "Higher priority wins when multiple preference rules match. Ties use earlier rule order.",
  "gift-items": "Gift item ids. Unnamespaced values count as minecraft ids; values beginning with # are treated as tags.",
  "gift-tags": "Gift item tag ids, such as minecraft:villager_plantable_seeds. At least one item or tag selector is required.",
  "gift-professions": "Profession filter. Profession-specific matches beat generic matches for the same gift or reward roll.",
  "gift-requires_villager_unarmed": "Requires the gift rule villager to have no usable weapon in either hand.",
  "gift-requires_villager_armed": "Requires the gift rule villager to have a usable weapon in either hand.",
  "gift-reputation_per_item": "Overrides the reaction's default reputation per gifted item.",
  "gift-response_key": "Dialogue message key for custom gift text. Define the localized text in dialogue messages.",
  "gift-item": "Reward item id returned by high-reputation villagers.",
  "gift-reputation_levels": "Reputation tiers that can receive this reward, such as trusted, respected, revered, or royalty.",
  "gift-min_count": "Minimum reward stack count, clamped to at least 1.",
  "gift-max_count": "Maximum reward stack count, clamped to at least the minimum.",
  "gift-weight": "Weighted selection among matching rewards. Missing weights default to 10.",
  "pacification-fileName": "Creates data/villagerretaliation/pacification/<file>.json. Use default only when replacing the built-in emerald rule.",
  "pacification-items": "Payment item ids. Unnamespaced values count as minecraft ids; values beginning with # are treated as tags.",
  "pacification-tags": "Payment tag ids, such as c:coins. At least one item or tag selector is required.",
  "pacification-professions": "Profession filter for payment rules. Wandering traders match none.",
  "pacification-requires_villager_unarmed": "Requires the pacification villager to have no usable weapon in either hand.",
  "pacification-requires_villager_armed": "Requires the pacification villager to have a usable weapon in either hand.",
  "pacification-count": "Exact number of items consumed, clamped from 1 to 64. When set, min/max are ignored.",
  "pacification-min_count": "Minimum random payment cost when count is omitted, clamped from 1 to 64.",
  "pacification-max_count": "Maximum random payment cost when count is omitted, clamped from min_count to 64.",
  "pacification-name": "Singular item name used by pacify dialogue placeholders. Defaults to the held item name.",
  "pacification-plural_name": "Plural item name used when count is not 1. Defaults to name.",
  "pacification-priority": "Higher priority wins when multiple payment rules match. Ties use earlier rule order.",
  "stories-namespace": "Namespace for story_structures and story_biomes files. Story discovery can live outside villagerretaliation.",
  "stories-radius": "Root fallback radius for structure entries that omit their own radius. Defaults to 96.",
  "stories-structureFileName": "Creates data/<namespace>/story_structures/<file>.json.",
  "stories-biomeFileName": "Creates data/<namespace>/story_biomes/<file>.json.",
  "story-structures": "Structure id or ids for share_story targets. Use full resource locations unless a page says a shortcut is supported.",
  "story-biomes": "Biome id or ids for share_story targets. Use full resource locations.",
  "story-name": "Readable target name used by {target} and {target_article}. If omitted, the id path is humanized.",
  "story-radius": "Detection radius in blocks for this structure entry, clamped to at least 1.",
  "names-male_names": "Preset names used for villagers assigned male identity. Only non-blank strings are loaded.",
  "names-female_names": "Preset names used for villagers assigned female identity. Existing villagers with stored names are not renamed."
};

const FLAG_TOOLTIPS = {
  show_for_adults: "Adult visibility. Defaults to true.",
  show_for_babies: "Baby visibility. Defaults to true.",
  force_camera_towards_villager: "Smoothly turns the player's camera toward the speaking villager when this dialogue choice is used.",
  first_conversation_only: "Only matches during the first conversation with that villager.",
  first_village_interaction_only: "Only matches during the player's first interaction in that village context.",
  requires_unreported_cartographer_map_discovery: "Requires a cartographer map discovery that has not been reported yet.",
  requires_unreported_story_hint_discovery: "Requires a story hint discovery that has not been reported yet.",
  requires_unreported_combat_survival_report: "Requires a waiting combat survival report.",
  requires_unreported_gear_report: "Requires a waiting gear report after the player gives combat gear.",
  requires_unreported_recruitment_followup: "Requires a waiting recruitment follow-up.",
  requires_unreported_cured_recognition: "Requires cured villager recognition that has not been reported yet.",
  requires_recent_village_event: "Requires a recent nearby village event memory.",
  requires_unreported_gift_advice_result: "Requires a gift advice result the player has not discussed yet.",
  requires_unapologized_remembered_harm: "Requires remembered harm that has not been apologized for.",
  requires_unreported_village_defense: "Requires a village defense event that has not been reported yet.",
  requires_shareable_story: "Requires a discovered structure or biome story the villager can share.",
  requires_recent_broken_bed_memory: "Requires recent memory of the player breaking a villager bed.",
  requires_recent_direct_hit_memory: "Requires recent memory of the player directly hitting a villager.",
  requires_container_theft_to_self: "Requires a recent container theft memory witnessed by this villager.",
  requires_container_theft_from_other: "Requires a recent container theft memory reported by another villager.",
  requires_retaliation_to_self: "Requires a recent retaliation-start memory from this villager.",
  requires_retaliation_from_other: "Requires a recent retaliation-start memory from another villager.",
  requires_high_knowledge: "Beta.12 shorthand for Knowledge 60 or higher.",
  requires_high_guts: "Beta.12 shorthand for Guts 60 or higher.",
  requires_high_proficiency: "Beta.12 shorthand for Proficiency 60 or higher.",
  requires_high_kindness: "Beta.12 shorthand for Kindness 60 or higher.",
  requires_high_charm: "Beta.12 shorthand for Charm 60 or higher.",
  requires_gear_report_used_in_combat: "Requires gifted gear that has been used in combat.",
  requires_gear_report_unused_in_combat: "Requires gifted gear that has not yet been used in combat.",
  requires_recruitment_memory: "Requires stored recruitment memory for the villager.",
  requires_recruitment_boat_trip: "Requires a remembered boat trip during recruitment.",
  requires_recruitment_ocean_crossing: "Requires a remembered ocean crossing during recruitment.",
  requires_recruitment_swim_trip: "Requires a remembered swim trip during recruitment.",
  excludes_recruitment_ocean_crossing: "Rejects lines when the recruitment memory includes an ocean crossing.",
  requires_known_family: "Requires any known family relationship.",
  requires_known_parent: "Requires a known parent.",
  requires_known_sibling: "Requires a known sibling.",
  requires_known_spouse: "Requires a known family spouse.",
  requires_known_child: "Requires a known child.",
  requires_known_grandparent: "Requires a known grandparent.",
  requires_known_grandchild: "Requires a known grandchild.",
  requires_known_descendant: "Requires a known descendant.",
  requires_known_aunt_uncle: "Requires a known aunt or uncle.",
  requires_known_cousin: "Requires a known cousin.",
  requires_known_niece_nephew: "Requires a known niece or nephew.",
  requires_known_extended_family: "Requires known extended family.",
  requires_known_deceased_family: "Requires a known deceased family member.",
  requires_known_relationship: "Requires any known romantic relationship state.",
  requires_known_current_relationship: "Requires a current romantic partner.",
  requires_known_past_relationship: "Requires a past romantic partner.",
  requires_known_crush: "Requires a known crush.",
  requires_known_dating_partner: "Requires a dating partner.",
  requires_known_fiance: "Requires an engaged partner.",
  requires_known_romantic_spouse: "Requires a romantic spouse.",
  requires_known_separated_partner: "Requires a separated partner.",
  requires_known_widowed_partner: "Requires a late partner."
};

const EVENT_TAG_TOOLTIPS = {
  baby_born: "A baby was born near the village.",
  iron_golem_defeated_mob: "An iron golem defeated a hostile mob.",
  thunderstorm: "A thunderstorm affected the village.",
  sandstorm: "A sandstorm-style village memory was recorded.",
  snowstorm: "A snowstorm-style village memory was recorded.",
  village_fire: "Fire threatened the village.",
  night_attack: "Hostile mobs attacked near the village at night.",
  raid: "A raid affected the village.",
  villager_death: "A villager died near the village.",
  player_killed_villager: "The player killed a villager and another villager witnessed it.",
  villager_attacked: "A villager was attacked.",
  baby_villager_attacked: "A baby villager was attacked.",
  player_attacked_villager: "The player attacked a villager.",
  player_defended_village: "The player defended the village from hostiles.",
  player_defended_raid: "The player defended the village during a raid.",
  player_cured_villager: "The player cured a zombie villager.",
  golem_created: "Accepted by the parser for golem creation memories.",
  golem_killed: "An iron golem was killed.",
  nearby_hostile_mob: "Accepted by the parser for nearby hostile mob memories.",
  reputation_changed: "A relevant reputation change was remembered.",
  player_gave_loved_gift: "The player gave a loved gift.",
  player_gave_liked_gift: "The player gave a liked gift.",
  player_gave_neutral_gift: "The player gave a neutral gift.",
  player_gave_disliked_gift: "The player gave a disliked gift.",
  player_gave_hated_gift: "The player gave a hated gift.",
  player_container_theft: "The player was witnessed taking items from a watched container.",
  villager_retaliation_started: "A villager or wandering trader acquired a new retaliation target."
};

const DISPOSITION_TOOLTIPS = {
  friendly: "High-trust or positive-context dialogue disposition.",
  respectful: "Respectful positive dialogue disposition.",
  neutral: "Neither especially trusting nor hostile.",
  cautious: "Low-trust or wary dialogue disposition.",
  rude: "Irritated or negative dialogue disposition.",
  hostile: "Angry or hostile dialogue disposition.",
  fearful: "Fear-driven dialogue disposition."
};

const MOOD_TOOLTIPS = {
  neutral: "No strong temporary mood is active.",
  content: "A calm positive temporary mood.",
  grateful: "A temporary mood caused by help, gifts, or recovery.",
  afraid: "A temporary fear mood caused by danger or harm.",
  angry: "A temporary anger mood caused by offenses or threats.",
  suspicious: "A temporary wary mood caused by troubling events.",
  grieving: "A temporary grief mood caused by loss.",
  protective: "A temporary defensive mood focused on guarding others.",
  hopeful: "A temporary optimistic mood after positive events.",
  stressed: "A temporary pressure or overload mood.",
  proud: "A temporary confidence mood after success or survival.",
  lonely: "A temporary isolation mood."
};

const ITEM_SLOT_TOOLTIPS = {
  main_hand: "Checks the player's main hand.",
  off_hand: "Checks the player's off hand.",
  hands: "Checks main hand and off hand.",
  armor: "Checks armor slots.",
  hotbar: "Checks the hotbar.",
  inventory: "Checks inventory slots.",
  equipment: "Checks hands and armor.",
  any: "Checks any carried or equipped slot."
};

const TAG_SUGGESTIONS = {
  "dialogue-professions": CONSTANTS.professions,
  "dialogue-dispositions": CONSTANTS.dispositions,
  "dialogue-moods": CONSTANTS.moods,
  "dialogue-reputation_levels": CONSTANTS.reputationLevels,
  "dialogue-player_item_slots": CONSTANTS.itemSlots,
  "dialogue-weather": CONSTANTS.weather,
  "dialogue-times": CONSTANTS.times,
  "dialogue-event_tags": CONSTANTS.eventTags,
  "dialogue-player_event_tags": CONSTANTS.eventTags,
  "dialogue-retaliation_target_entity_types": ["minecraft:player", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:raider"],
  "dialogue-outcomes": CONSTANTS.pacifyOutcomes,
  "forced-trigger": CONSTANTS.forcedDialogueTriggers,
  "forced-output_mode": CONSTANTS.forcedOutputModes,
  "forced-witness_professions": CONSTANTS.professions,
  "forced-player_item_slots": CONSTANTS.itemSlots,
  "forced-target_entity_types": ["minecraft:player", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:raider"],
  "notification-professions": CONSTANTS.professions,
  "notification-reputation_levels": CONSTANTS.reputationLevels,
  "notification-player_item_slots": CONSTANTS.itemSlots,
  "gift-professions": CONSTANTS.professions,
  "gift-reputation_levels": CONSTANTS.reputationLevels,
  "pacification-professions": CONSTANTS.professions
};

const BETA_12_ONLY_DIALOGUE_KEYS = [
  "topic",
  "tags",
  "questline",
  "quest",
  "stage",
  "notes",
  "mood",
  "moods",
  "min_mood_intensity",
  "requires_high_knowledge",
  "requires_high_guts",
  "requires_high_proficiency",
  "requires_high_kindness",
  "requires_high_charm",
  "min_knowledge",
  "max_knowledge",
  "min_guts",
  "max_guts",
  "min_proficiency",
  "max_proficiency",
  "min_kindness",
  "max_kindness",
  "min_charm",
  "max_charm",
  "conditions",
  "priority",
  "category",
  "text_key"
];

const BETA_12_ONLY_DIALOGUE_OPTION_KEYS = [
  "conditions",
  "topic",
  "tags",
  "questline",
  "quest",
  "stage",
  "notes"
];

const BETA_12_ONLY_DIALOGUE_LINE_KEYS = [
  "topic",
  "tags",
  "questline",
  "quest",
  "stage",
  "notes",
  "mood",
  "moods",
  "min_mood_intensity",
  "requires_high_knowledge",
  "requires_high_guts",
  "requires_high_proficiency",
  "requires_high_kindness",
  "requires_high_charm",
  "min_knowledge",
  "max_knowledge",
  "min_guts",
  "max_guts",
  "min_proficiency",
  "max_proficiency",
  "min_kindness",
  "max_kindness",
  "min_charm",
  "max_charm",
  "conditions",
  "priority",
  "category",
  "text_key"
];

const BETA_13_PLANNED_DIALOGUE_LINE_DEPRECATION_KEYS = [
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
];

const BETA_13_PLANNED_DIALOGUE_OPTION_DEPRECATION_KEYS = [
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
];

const BETA_13_PLANNED_DIALOGUE_DEPRECATION_REPLACEMENT = "`conditions` blocks";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const PREVIEW_EXACT_WRAP_LINE_LIMIT = 2500;

let state = createInitialState();
let activeSection = "overview";
let activeDialogueKind = "options";
let activeGiftKind = "preferences";
let activeStoryKind = "structures";
let editing = null;
let selectedPath = "pack.mcmeta";
let toastTimer = null;
let outputRenderTimer = null;
let showLeftPanel = true;
let showRightPanel = true;
let wrapPreviewLines = false;
let previewEditTimer = null;
let previewEditError = null;
let previewLineHighlightRanges = [];
let pendingPreviewEntryScroll = false;
let previewLineNumberState = { source: "", lineCount: 1, rangesKey: "" };
let previewLineNumberFrame = 0;
let previewUndoStack = [];
let previewRedoStack = [];
let previewBeforeInputSnapshot = null;
let previewHistoryPath = "";
let isApplyingPreviewHistory = false;
let previewEditor = null;
let previewEditorApi = null;
let previewEditorLineHighlightEffect = null;
let previewEditorWrapCompartment = null;
let previewEditorReadOnlyCompartment = null;
let previewEditorReadOnly = false;
let isApplyingPreviewEditorValue = false;
let fileTreeSignature = "";
let entryDirectorySignature = "";
let currentViewSnapshotCache = null;
let entryListPages = {};
let collapsedTreeFolders = new Set();
let entryDragState = null;
let suppressEntryClickUntil = 0;
let importDragDepth = 0;
let entryFormDirty = false;
let unsavedShakeTimer = null;
let exportIssueDialogResolve = null;

const els = {
  workspace: document.querySelector(".workspace"),
  leftRail: document.querySelector(".left-rail"),
  fileExplorer: document.querySelector(".file-explorer"),
  rightRail: document.querySelector(".right-rail"),
  tabs: document.querySelector("#section-tabs"),
  panel: document.querySelector("#builder-panel"),
  fileTree: document.querySelector("#file-tree"),
  fileCount: document.querySelector("#file-count"),
  entryDirectory: document.querySelector("#entry-directory"),
  entryCount: document.querySelector("#entry-count"),
  checks: document.querySelector("#checks"),
  checkCount: document.querySelector("#check-count"),
  selectedPath: document.querySelector("#selected-path"),
  preview: document.querySelector("#json-preview"),
  previewLines: document.querySelector("#json-preview-lines"),
  importInput: document.querySelector("#import-input"),
  directoryInput: document.querySelector("#directory-input"),
  exportButton: document.querySelector("#export-button"),
  starterButton: document.querySelector("#starter-button"),
  settingsButton: document.querySelector("#settings-button"),
  leftPanelToggleButton: document.querySelector("#left-panel-toggle-button"),
  rightPanelToggleButton: document.querySelector("#right-panel-toggle-button"),
  undoPreviewButton: document.querySelector("#undo-preview-button"),
  redoPreviewButton: document.querySelector("#redo-preview-button"),
  wrapPreviewButton: document.querySelector("#wrap-preview-button"),
  codePreview: document.querySelector(".code-preview"),
  copyButton: document.querySelector("#copy-file-button"),
  downloadButton: document.querySelector("#download-file-button"),
  toast: document.querySelector("#toast"),
  settingsDialog: document.querySelector("#settings-dialog"),
  settingsKeybinds: document.querySelector("#settings-keybinds"),
  settingsResetButton: document.querySelector("#settings-reset-button"),
  settingsCloseButton: document.querySelector("#settings-close-button"),
  templateDialog: document.querySelector("#template-dialog"),
  templateList: document.querySelector("#template-list"),
  templateCancel: document.querySelector("#template-cancel"),
  exportIssueDialog: document.querySelector("#export-issue-dialog"),
  exportIssueList: document.querySelector("#export-issue-list"),
  exportIssueCancel: document.querySelector("#export-issue-cancel"),
  exportIssueConfirm: document.querySelector("#export-issue-confirm"),
  wikiButton: document.querySelector("#wiki-button"),
  wikiOverlay: document.querySelector("#wiki-overlay"),
  wikiWindow: document.querySelector(".wiki-window"),
  wikiTitlebar: document.querySelector("#wiki-titlebar"),
  wikiVersion: document.querySelector("#wiki-version"),
  wikiSearch: document.querySelector("#wiki-search"),
  wikiHighlightButton: document.querySelector("#wiki-highlight-button"),
  wikiTabs: document.querySelector("#wiki-tabs"),
  wikiCloseButton: document.querySelector("#wiki-close-button"),
  wikiResults: document.querySelector("#wiki-results"),
  wikiContent: document.querySelector("#wiki-content"),
  toolbarHintText: document.querySelector("#toolbar-hint-text")
};

const PANEL_SIZE_STORAGE_KEY = "vr-datapack-builder-panel-sizes";
const PANEL_SIZE_VARS = {
  left: "--left-panel-width",
  right: "--right-panel-width",
  checks: "--checks-panel-height"
};
const PANEL_SIZE_LIMITS = {
  left: { min: 220, max: 430 },
  right: { min: 320, max: 820 },
  checks: { min: 48, max: 360 }
};
const LEFT_PANEL_COMPACT_WIDTH = 230;
const LEFT_PANEL_EXPANDED_SNAP_WIDTH = 286;
const RIGHT_PANEL_TITLE_ONLY_HEIGHT = 48;
const RIGHT_PANEL_EXPANDED_SNAP_HEIGHT = 120;
const MIN_BUILDER_WIDTH = 360;
const MIN_PREVIEW_HEIGHT = 180;
let panelResizeState = null;
const WIKI_STORAGE_KEY = "vr-datapack-builder-wiki";
const WIKI_HIGHLIGHTS_FILE = "__wiki_highlights__";
const KEYBIND_STORAGE_KEY = "vr-datapack-builder-keybinds";
const WIKI_MIN_WIDTH = 300;
const WIKI_MIN_HEIGHT = 310;
const WIKI_DEFAULT_LAYOUT = { left: 104, top: 88, width: 760, height: 560 };
const DEFAULT_KEYBINDS = {
  openWiki: { alt: true, ctrl: false, shift: false, meta: false, key: "q" },
  saveEntry: { alt: true, ctrl: false, shift: false, meta: false, key: "s" },
  openSettings: { alt: true, ctrl: false, shift: false, meta: false, key: "," }
};
const KEYBIND_ACTIONS = [
  { id: "openWiki", label: "Open wiki", detail: "Toggle the versioned wiki search." },
  { id: "saveEntry", label: "Save active entry", detail: "Save the form currently being edited." },
  { id: "openSettings", label: "Open settings", detail: "Open this keybind menu." }
];
let nextWikiTabId = 2;
let wikiPointerState = null;
let wikiTabDragState = null;
let suppressWikiTabClickUntil = 0;
let lastWikiMiddleOpen = { signature: "", time: 0 };
let lastWikiHighlightSelection = null;
let keybinds = readKeybinds();
let recordingKeybindAction = "";
let wikiHighlightDragState = null;
let wikiState = {
  isOpen: false,
  version: CURRENT_PACK_VERSION,
  loadedVersion: "",
  docs: [],
  query: "",
  selectedFile: "Home.md",
  selectedSectionId: "",
  activeTabId: "wiki-tab-1",
  tabs: [{ id: "wiki-tab-1", file: "Home.md", sectionId: "" }],
  highlights: [],
  results: [],
  resultMode: "pages",
  status: ""
};
const TOOLBAR_HINTS = [
  () => `${formatKeybind(getKeybind("openWiki"))} opens the wiki.`,
  () => `${formatKeybind(getKeybind("openSettings"))} opens settings.`,
  () => "Drag panel dividers to resize sections.",
  () => "Middle-click a divider to reset it.",
  () => "Click a Checks item to jump to its field.",
  () => "Drag entry cards to reorder output."
];
let toolbarHintIndex = 0;

function createInitialState() {
  return {
    meta: {
      packName: "Villager Retaliation Pack",
      description: "Custom Villager Retaliation datapack",
      packVersion: CURRENT_PACK_VERSION,
      packFormat: 34,
      namespace: "my_pack",
      slug: "my_pack",
      locale: "en_us"
    },
    dialogue: {
      layout: "folders",
      fileName: "my_pack_dialogue",
      folderName: "my_pack",
      options: [],
      lines: [],
      messages: [],
      openings: [],
      closings: [],
      pacify: []
    },
    forcedDialogue: {
      fileName: "my_pack_forced_dialogue",
      entries: []
    },
    notifications: {
      fileName: "my_pack_notifications",
      notifications: []
    },
    gifts: {
      fileName: "my_pack_gifts",
      preferences: [],
      rewards: []
    },
    pacification: {
      fileName: "my_pack_pacification",
      payments: []
    },
    stories: {
      namespace: "my_pack",
      structureFileName: "my_pack_structures",
      biomeFileName: "my_pack_biomes",
      radius: 96,
      structures: [],
      biomes: []
    },
    names: {
      male_names: [],
      female_names: []
    },
    extraFiles: {}
  };
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function icon(name, className = "inline-icon") {
  return `<i data-lucide="${escapeHtml(name)}" class="${escapeHtml(className)}" aria-hidden="true"></i>`;
}

function renderIcons() {
  if (window.lucide?.createIcons) {
    try {
      window.lucide.createIcons({
        attrs: {
          "stroke-width": 1.8
        }
      });
    } catch {
      // Text labels keep the builder usable if the icon CDN is unavailable.
    }
  }
}

function readKeybinds() {
  let stored = {};
  try {
    stored = JSON.parse(localStorage.getItem(KEYBIND_STORAGE_KEY) || "{}") || {};
  } catch {
    stored = {};
  }
  return KEYBIND_ACTIONS.reduce((result, action) => {
    result[action.id] = normalizeStoredKeybind(stored[action.id]) || { ...DEFAULT_KEYBINDS[action.id] };
    return result;
  }, {});
}

function writeKeybinds() {
  try {
    localStorage.setItem(KEYBIND_STORAGE_KEY, JSON.stringify(keybinds));
  } catch {
    // Keybinds still apply for the current session if storage is unavailable.
  }
}

function getKeybind(actionId) {
  return keybinds[actionId] || DEFAULT_KEYBINDS[actionId];
}

function normalizeStoredKeybind(bind) {
  if (!bind || typeof bind !== "object" || typeof bind.key !== "string") return null;
  const key = normalizeKeyName(bind.key);
  if (!key) return null;
  return {
    alt: Boolean(bind.alt),
    ctrl: Boolean(bind.ctrl),
    shift: Boolean(bind.shift),
    meta: Boolean(bind.meta),
    key
  };
}

function normalizeKeyName(key) {
  const value = String(key || "").trim();
  if (!value) return "";
  const lower = value.toLowerCase();
  if (lower === " ") return "space";
  if (lower === "esc") return "escape";
  return lower;
}

function formatKeybind(bind) {
  if (!bind) return "Unassigned";
  const parts = [];
  if (bind.ctrl) parts.push("Ctrl");
  if (bind.alt) parts.push("Alt");
  if (bind.shift) parts.push("Shift");
  if (bind.meta) parts.push(navigator.platform.toLowerCase().includes("mac") ? "Cmd" : "Meta");
  parts.push(formatKey(bind.key));
  return parts.join("+");
}

function formatKey(key) {
  const aliases = {
    " ": "Space",
    space: "Space",
    escape: "Esc",
    arrowup: "Arrow Up",
    arrowright: "Arrow Right",
    arrowdown: "Arrow Down",
    arrowleft: "Arrow Left",
    ",": ",",
    ".": ".",
    "/": "/",
    "\\": "\\",
    ";": ";",
    "'": "'",
    "[": "[",
    "]": "]",
    "-": "-",
    "=": "="
  };
  if (aliases[key]) return aliases[key];
  if (/^f\d{1,2}$/.test(key)) return key.toUpperCase();
  return key.length === 1 ? key.toUpperCase() : key.replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function keybindFromEvent(event) {
  const key = normalizeKeyName(event.key);
  if (!key || ["alt", "control", "ctrl", "shift", "meta"].includes(key)) return null;
  if (!event.altKey && !event.ctrlKey && !event.shiftKey && !event.metaKey && !/^f\d{1,2}$/.test(key)) {
    return null;
  }
  return {
    alt: event.altKey,
    ctrl: event.ctrlKey,
    shift: event.shiftKey,
    meta: event.metaKey,
    key
  };
}

function keybindMatches(event, bind) {
  if (!bind) return false;
  return event.altKey === Boolean(bind.alt)
    && event.ctrlKey === Boolean(bind.ctrl)
    && event.shiftKey === Boolean(bind.shift)
    && event.metaKey === Boolean(bind.meta)
    && normalizeKeyName(event.key) === bind.key;
}

function keybindSignature(bind) {
  return [bind.ctrl, bind.alt, bind.shift, bind.meta, bind.key].join(":");
}

function keybindConflict(actionId, bind) {
  const signature = keybindSignature(bind);
  return KEYBIND_ACTIONS.find((action) => action.id !== actionId && keybindSignature(getKeybind(action.id)) === signature);
}

function setupToolbarHints() {
  if (!els.toolbarHintText || TOOLBAR_HINTS.length === 0) return;
  updateToolbarHint();
  window.setInterval(() => {
    toolbarHintIndex = (toolbarHintIndex + 1) % TOOLBAR_HINTS.length;
    updateToolbarHint();
  }, 5200);
}

function updateToolbarHint() {
  if (!els.toolbarHintText) return;
  const hint = TOOLBAR_HINTS[toolbarHintIndex];
  els.toolbarHintText.textContent = typeof hint === "function" ? hint() : hint;
}

function readPanelSizes() {
  try {
    return JSON.parse(localStorage.getItem(PANEL_SIZE_STORAGE_KEY) || "{}") || {};
  } catch {
    return {};
  }
}

function writePanelSizes(sizes) {
  try {
    localStorage.setItem(PANEL_SIZE_STORAGE_KEY, JSON.stringify(sizes));
  } catch {
    // Resizing still works for the current session if storage is unavailable.
  }
}

function applyStoredPanelSizes() {
  const sizes = readPanelSizes();
  for (const [target, value] of Object.entries(sizes)) {
    let number = Number(value);
    if (target === "left" && Number.isFinite(number) && number <= LEFT_PANEL_COMPACT_WIDTH) {
      number = LEFT_PANEL_EXPANDED_SNAP_WIDTH;
    }
    if (PANEL_SIZE_VARS[target] && Number.isFinite(number)) {
      const clamped = clampPanelSize(target, number);
      document.documentElement.style.setProperty(PANEL_SIZE_VARS[target], `${Math.round(clamped)}px`);
      updatePanelSnapMode(target, clamped);
    }
  }
  updateLeftPanelMode(Number(sizes.left));
  updatePanelSnapMode("checks", Number(sizes.checks));
}

function savePanelSize(target, value) {
  const clamped = clampPanelSize(target, value);
  document.documentElement.style.setProperty(PANEL_SIZE_VARS[target], `${Math.round(clamped)}px`);
  const sizes = readPanelSizes();
  sizes[target] = Math.round(clamped);
  writePanelSizes(sizes);
  if (target === "left") updateLeftPanelMode(clamped);
  updatePanelSnapMode(target, clamped);
}

function resetPanelSize(target) {
  if (!PANEL_SIZE_VARS[target]) return;
  const property = PANEL_SIZE_VARS[target];
  const hadInlineSize = Boolean(document.documentElement.style.getPropertyValue?.(property));
  document.documentElement.style.removeProperty(property);
  const sizes = readPanelSizes();
  const hadStoredSize = Object.hasOwn(sizes, target);
  delete sizes[target];
  writePanelSizes(sizes);
  if (target === "left") updateLeftPanelMode();
  updatePanelSnapMode(target);
  if (hadInlineSize || hadStoredSize) {
    showToast("Panel size reset.");
  }
}

function readWikiLayout() {
  const stored = readWikiStorage();
  return {
    left: Number.isFinite(Number(stored.left)) ? Number(stored.left) : WIKI_DEFAULT_LAYOUT.left,
    top: Number.isFinite(Number(stored.top)) ? Number(stored.top) : WIKI_DEFAULT_LAYOUT.top,
    width: Number.isFinite(Number(stored.width)) ? Number(stored.width) : WIKI_DEFAULT_LAYOUT.width,
    height: Number.isFinite(Number(stored.height)) ? Number(stored.height) : WIKI_DEFAULT_LAYOUT.height
  };
}

function readWikiStorage() {
  try {
    return JSON.parse(localStorage.getItem(WIKI_STORAGE_KEY) || "{}") || {};
  } catch {
    return {};
  }
}

function writeWikiLayout(layout) {
  const stored = readWikiStorage();
  writeWikiStorage({
    ...stored,
    left: layout.left,
    top: layout.top,
    width: layout.width,
    height: layout.height
  });
}

function writeWikiStorage(value) {
  try {
    localStorage.setItem(WIKI_STORAGE_KEY, JSON.stringify(value));
  } catch {
    // The wiki remains usable for the current session if storage is unavailable.
  }
}

function readWikiHighlights() {
  const stored = readWikiStorage();
  return normalizeWikiHighlights(stored.highlights);
}

function writeWikiHighlights(highlights) {
  const stored = readWikiStorage();
  writeWikiStorage({
    ...stored,
    highlights: normalizeWikiHighlights(highlights)
  });
}

function normalizeWikiHighlightTerm(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function normalizeWikiHighlightKey(value) {
  return normalizeWikiHighlightTerm(value).toLowerCase();
}

function normalizeWikiHighlights(values) {
  if (!Array.isArray(values)) return [];
  const seen = new Set();
  const highlights = [];
  for (const value of values) {
    const entry = typeof value === "string" ? { text: value, html: escapeHtml(value) } : value;
    const text = normalizeWikiHighlightTerm(entry?.text);
    if (!text) continue;
    const occurrenceIndex = Number.isFinite(Number(entry?.occurrenceIndex)) ? Number(entry.occurrenceIndex) : -1;
    const startOffset = Number.isFinite(Number(entry?.startOffset)) ? Number(entry.startOffset) : -1;
    const endOffset = Number.isFinite(Number(entry?.endOffset)) ? Number(entry.endOffset) : -1;
    const key = [entry?.file || "", entry?.sectionId || "", startOffset, endOffset, occurrenceIndex, normalizeWikiHighlightKey(text)].join("|");
    if (seen.has(key)) continue;
    seen.add(key);
    highlights.push({
      id: String(entry?.id || `highlight-${Date.now()}-${highlights.length}`),
      text,
      html: sanitizeWikiHighlightHtml(entry?.html || escapeHtml(text)),
      file: String(entry?.file || ""),
      title: String(entry?.title || ""),
      sectionId: String(entry?.sectionId || ""),
      occurrenceIndex,
      startOffset,
      endOffset
    });
  }
  return highlights.slice(0, 80);
}

function wikiSavedHighlightEntries() {
  return normalizeWikiHighlights(wikiState.highlights)
    .filter((entry) => !entry.file || entry.file === wikiState.selectedFile);
}

function sanitizeWikiHighlightHtml(value) {
  const template = document.createElement("template");
  template.innerHTML = String(value || "");
  template.content.querySelectorAll("script, style, iframe, object, embed").forEach((node) => node.remove());
  template.content.querySelectorAll("*").forEach((node) => {
    [...node.attributes].forEach((attribute) => {
      if (/^on/i.test(attribute.name)) node.removeAttribute(attribute.name);
    });
  });
  return template.innerHTML;
}

function normalizeWikiHighlightFragment(html) {
  const template = document.createElement("template");
  template.innerHTML = sanitizeWikiHighlightHtml(html);
  const normalized = document.createDocumentFragment();
  let list = null;
  let table = null;
  let tableRow = null;
  const closeList = () => {
    if (!list) return;
    normalized.append(list);
    list = null;
  };
  const closeTableRow = () => {
    if (!tableRow) return;
    if (!table) table = document.createElement("table");
    table.append(tableRow);
    tableRow = null;
  };
  const closeTable = () => {
    closeTableRow();
    if (!table) return;
    normalized.append(table);
    table = null;
  };

  [...template.content.childNodes].forEach((node) => {
    if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === "li") {
      closeTable();
      if (!list) list = document.createElement("ul");
      list.append(node);
      return;
    }
    if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === "tr") {
      closeList();
      closeTableRow();
      if (!table) table = document.createElement("table");
      table.append(node);
      return;
    }
    if (node.nodeType === Node.ELEMENT_NODE && ["td", "th"].includes(node.tagName.toLowerCase())) {
      closeList();
      if (!tableRow) tableRow = document.createElement("tr");
      tableRow.append(node);
      return;
    }
    closeList();
    closeTable();
    normalized.append(node);
  });
  closeList();
  closeTable();
  template.content.append(normalized);
  return template.innerHTML;
}

function clampWikiLayout(layout) {
  const margin = 10;
  const maxWidth = Math.max(WIKI_MIN_WIDTH, window.innerWidth - margin * 2);
  const maxHeight = Math.max(WIKI_MIN_HEIGHT, window.innerHeight - margin * 2);
  const width = clamp(Math.round(layout.width), WIKI_MIN_WIDTH, maxWidth);
  const height = clamp(Math.round(layout.height), WIKI_MIN_HEIGHT, maxHeight);
  const left = clamp(Math.round(layout.left), margin, Math.max(margin, window.innerWidth - width - margin));
  const top = clamp(Math.round(layout.top), margin, Math.max(margin, window.innerHeight - height - margin));
  return { left, top, width, height };
}

function applyWikiLayout(layout = readWikiLayout()) {
  if (!els.wikiWindow) return;
  const clamped = clampWikiLayout(layout);
  els.wikiWindow.style.left = `${clamped.left}px`;
  els.wikiWindow.style.top = `${clamped.top}px`;
  els.wikiWindow.style.width = `${clamped.width}px`;
  els.wikiWindow.style.height = `${clamped.height}px`;
  writeWikiLayout(clamped);
}

function setupWikiChrome() {
  if (!els.wikiVersion) return;
  els.wikiVersion.innerHTML = PACK_VERSIONS
    .map((version) => `<option value="${escapeHtml(version.id)}">${escapeHtml(version.label)}</option>`)
    .join("");
  const stored = readWikiLayout();
  wikiState.version = PACK_VERSION_IDS.includes(state.meta.packVersion) ? state.meta.packVersion : CURRENT_PACK_VERSION;
  wikiState.highlights = readWikiHighlights();
  els.wikiVersion.value = wikiState.version;
  applyWikiLayout(stored);
}

function openWiki() {
  wikiState.isOpen = true;
  wikiState.version = PACK_VERSION_IDS.includes(els.wikiVersion?.value) ? els.wikiVersion.value : state.meta.packVersion;
  if (!PACK_VERSION_IDS.includes(wikiState.version)) wikiState.version = CURRENT_PACK_VERSION;
  els.wikiVersion.value = wikiState.version;
  els.wikiOverlay.classList.add("is-open");
  els.wikiOverlay.setAttribute("aria-hidden", "false");
  applyWikiLayout();
  renderWiki();
  ensureWikiLoaded(wikiState.version);
  window.setTimeout(() => els.wikiSearch?.focus(), 0);
}

function closeWiki() {
  wikiState.isOpen = false;
  els.wikiOverlay.classList.remove("is-open");
  els.wikiOverlay.setAttribute("aria-hidden", "true");
}

function toggleWiki() {
  if (wikiState.isOpen) {
    closeWiki();
  } else {
    openWiki();
  }
}

function createWikiTab(file = "Home.md", sectionId = "") {
  return {
    id: `wiki-tab-${nextWikiTabId++}`,
    file,
    sectionId,
    pinned: false
  };
}

function isWikiHighlightsFile(file) {
  return file === WIKI_HIGHLIGHTS_FILE;
}

function activeWikiTab() {
  let tab = wikiState.tabs.find((candidate) => candidate.id === wikiState.activeTabId);
  if (!tab) {
    tab = wikiState.tabs[0] || createWikiTab();
    if (wikiState.tabs.length === 0) wikiState.tabs.push(tab);
    wikiState.activeTabId = tab.id;
  }
  return tab;
}

function syncWikiSelectionFromActiveTab() {
  if (isWikiHighlightsFile(wikiState.activeTabId)) {
    wikiState.selectedFile = WIKI_HIGHLIGHTS_FILE;
    wikiState.selectedSectionId = "";
    return;
  }
  const tab = activeWikiTab();
  wikiState.selectedFile = tab.file || "Home.md";
  wikiState.selectedSectionId = tab.sectionId || "";
}

function syncActiveWikiTabToSelection() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) return;
  const tab = activeWikiTab();
  tab.file = wikiState.selectedFile || "Home.md";
  tab.sectionId = wikiState.selectedSectionId || "";
}

function resetWikiTabs(file = "Home.md", sectionId = "") {
  const tab = createWikiTab(file, sectionId);
  wikiState.tabs = [tab];
  wikiState.activeTabId = tab.id;
  syncWikiSelectionFromActiveTab();
}

function setWikiLocation(file, sectionId = "", options = {}) {
  if (!file || (!isWikiHighlightsFile(file) && wikiState.docs.length > 0 && !wikiState.docs.some((doc) => doc.file === file))) return false;
  if (isWikiHighlightsFile(file)) {
    ensureHighlightsTab();
    renderWiki();
    return true;
  }
  const targetSectionId = sectionId || "";
  if (options.newTab) {
    const tab = createWikiTab(file, sectionId);
    wikiState.tabs.push(tab);
    wikiState.activeTabId = tab.id;
  } else {
    const tab = activeWikiTab();
    const targetSignature = `${file}#${targetSectionId}`;
    const currentSignature = `${tab.file || "Home.md"}#${tab.sectionId || ""}`;
    if (tab.pinned && targetSignature !== currentSignature) {
      const newTab = createWikiTab(file, sectionId);
      wikiState.tabs.push(newTab);
      wikiState.activeTabId = newTab.id;
    } else {
      tab.file = file;
      tab.sectionId = targetSectionId;
    }
  }
  syncWikiSelectionFromActiveTab();
  renderWiki();
  return true;
}

function setWikiLocationInNewTabFromMiddleClick(file, sectionId = "") {
  const signature = `${file}#${sectionId || ""}`;
  const now = Date.now();
  if (lastWikiMiddleOpen.signature === signature && now - lastWikiMiddleOpen.time < 350) return false;
  lastWikiMiddleOpen = { signature, time: now };
  return setWikiLocation(file, sectionId, { newTab: true });
}

function closeWikiTab(tabId) {
  if (wikiState.tabs.length <= 1) return;
  const index = wikiState.tabs.findIndex((tab) => tab.id === tabId);
  if (index < 0) return;
  wikiState.tabs.splice(index, 1);
  if (wikiState.activeTabId === tabId) {
    const nextTab = wikiState.tabs[Math.min(index, wikiState.tabs.length - 1)] || wikiState.tabs[0];
    wikiState.activeTabId = nextTab.id;
    syncWikiSelectionFromActiveTab();
  }
  renderWiki();
}

function toggleWikiTabPinned(tabId) {
  const tab = wikiState.tabs.find((candidate) => candidate.id === tabId);
  if (!tab) return;
  tab.pinned = !tab.pinned;
  renderWiki();
}

function reorderWikiTab(fromId, toId, placement) {
  if (!fromId || !toId || fromId === toId) return;
  const fromIndex = wikiState.tabs.findIndex((tab) => tab.id === fromId);
  let toIndex = wikiState.tabs.findIndex((tab) => tab.id === toId);
  if (fromIndex < 0 || toIndex < 0) return;
  if (placement === "after") toIndex += 1;
  const [tab] = wikiState.tabs.splice(fromIndex, 1);
  if (fromIndex < toIndex) toIndex -= 1;
  wikiState.tabs.splice(Math.max(0, Math.min(toIndex, wikiState.tabs.length)), 0, tab);
  renderWiki();
}

function wikiTabDropPlacement(event, tabElement) {
  const rect = tabElement.getBoundingClientRect();
  return event.clientX > rect.left + rect.width / 2 ? "after" : "before";
}

function clearWikiTabDropIndicators() {
  els.wikiTabs?.querySelectorAll(".wiki-tab.is-drop-before, .wiki-tab.is-drop-after").forEach((tab) => {
    tab.classList.remove("is-drop-before", "is-drop-after");
  });
}

function validateWikiTabs() {
  if (wikiState.docs.length === 0) {
    if (wikiState.tabs.length === 0) resetWikiTabs();
    syncWikiSelectionFromActiveTab();
    return;
  }
  wikiState.tabs = wikiState.tabs.filter((tab) => !isWikiHighlightsFile(tab.file) && wikiState.docs.some((doc) => doc.file === tab.file));
  if (wikiState.tabs.length === 0) {
    resetWikiTabs(wikiState.docs[0]?.file || "Home.md");
    return;
  }
  if (!isWikiHighlightsFile(wikiState.activeTabId) && !wikiState.tabs.some((tab) => tab.id === wikiState.activeTabId)) {
    wikiState.activeTabId = wikiState.tabs[0].id;
  }
  syncWikiSelectionFromActiveTab();
}

function openSettings() {
  recordingKeybindAction = "";
  renderSettingsKeybinds();
  els.settingsDialog.classList.add("is-open");
  els.settingsDialog.setAttribute("aria-hidden", "false");
  window.setTimeout(() => els.settingsCloseButton?.focus(), 0);
}

function closeSettings() {
  recordingKeybindAction = "";
  els.settingsDialog.classList.remove("is-open");
  els.settingsDialog.setAttribute("aria-hidden", "true");
  renderSettingsKeybinds();
}

function renderSettingsKeybinds() {
  if (!els.settingsKeybinds) return;
  els.settingsKeybinds.innerHTML = KEYBIND_ACTIONS.map((action) => {
    const isRecording = recordingKeybindAction === action.id;
    return `
      <div class="keybind-row">
        <div class="keybind-row-main">
          <strong>${escapeHtml(action.label)}</strong>
          <span>${escapeHtml(action.detail)}</span>
        </div>
        <button class="button button-secondary keybind-button ${isRecording ? "is-recording" : ""}" type="button" data-keybind-action="${escapeHtml(action.id)}" aria-label="Set keybind for ${escapeHtml(action.label)}">
          ${isRecording ? "Press keys..." : escapeHtml(formatKeybind(getKeybind(action.id)))}
        </button>
      </div>
    `;
  }).join("");
}

function resetKeybinds() {
  keybinds = readDefaultKeybinds();
  recordingKeybindAction = "";
  writeKeybinds();
  renderSettingsKeybinds();
  updateShortcutTooltips();
  updateToolbarHint();
  showToast("Keybinds reset.");
}

function readDefaultKeybinds() {
  return KEYBIND_ACTIONS.reduce((result, action) => {
    result[action.id] = { ...DEFAULT_KEYBINDS[action.id] };
    return result;
  }, {});
}

function updateShortcutTooltips() {
  els.wikiButton?.setAttribute("data-tooltip", `Search the versioned builder wiki. ${formatKeybind(getKeybind("openWiki"))} toggles it.`);
  els.settingsButton?.setAttribute("data-tooltip", `Configure builder keybinds. ${formatKeybind(getKeybind("openSettings"))} opens settings.`);
  els.panel
    ?.querySelectorAll('[data-action="save-entry-form"]')
    .forEach((button) => button.setAttribute("data-tooltip", `Save changes. ${formatKeybind(getKeybind("saveEntry"))}`));
}

function applyRecordedKeybind(event) {
  if (!recordingKeybindAction) return false;
  event.preventDefault();
  event.stopPropagation();
  if (event.key === "Escape") {
    recordingKeybindAction = "";
    renderSettingsKeybinds();
    return true;
  }
  const bind = keybindFromEvent(event);
  if (!bind) {
    showToast("Press a key with optional modifiers.");
    return true;
  }
  const conflict = keybindConflict(recordingKeybindAction, bind);
  if (conflict) {
    showToast(`${formatKeybind(bind)} is already used for ${conflict.label}.`);
    return true;
  }
  keybinds[recordingKeybindAction] = bind;
  recordingKeybindAction = "";
  writeKeybinds();
  renderSettingsKeybinds();
  updateShortcutTooltips();
  updateToolbarHint();
  showToast("Keybind updated.");
  return true;
}

async function ensureWikiLoaded(version) {
  if (wikiState.loadedVersion === version && wikiState.docs.length > 0) return;
  wikiState.status = "Loading wiki...";
  wikiState.docs = [];
  wikiState.loadedVersion = "";
  renderWiki();
  try {
    const snapshot = window.VR_WIKI_SNAPSHOT?.[version];
    const docs = snapshot
      ? WIKI_PAGE_FILES.map((file) => {
        if (!Object.hasOwn(snapshot, file)) throw new Error(`Missing ${file}`);
        return buildWikiDoc(file, snapshot[file]);
      })
      : await Promise.all(WIKI_PAGE_FILES.map(async (file) => {
        const response = await fetch(`./wiki/${encodeURIComponent(version)}/${file}`);
        if (!response.ok) throw new Error(`Missing ${file}`);
        const markdown = await response.text();
        return buildWikiDoc(file, markdown);
      }));
    wikiState.docs = docs;
    wikiState.loadedVersion = version;
    wikiState.status = "";
    validateWikiTabs();
  } catch (error) {
    wikiState.status = `Wiki docs for ${version} could not be loaded.`;
    wikiState.docs = [];
    wikiState.loadedVersion = "";
  }
  renderWiki();
}

function buildWikiDoc(file, markdown) {
  const titleMatch = markdown.match(/^#\s+(.+)$/m);
  const title = titleMatch ? titleMatch[1].trim() : file.replace(/\.md$/i, "").replace(/-/g, " ");
  return {
    file,
    title,
    markdown,
    text: markdownToPlainText(markdown),
    sections: wikiSections(file, markdown, title)
  };
}

function wikiSections(file, markdown, pageTitle) {
  const lines = markdown.split(/\r?\n/);
  const sections = [];
  const fileSlug = wikiFileSlug(file);
  let current = {
    title: pageTitle,
    level: 1,
    lines: [],
    id: `${fileSlug}-0`,
    file
  };
  let parentHeading = {
    title: pageTitle,
    level: 1
  };
  let index = 0;
  const pushCurrent = () => {
    if (!current.lines.some((entry) => entry.trim())) return;
    current.text = markdownToPlainText(current.lines.join("\n"));
    sections.push(current);
  };
  for (const line of lines) {
    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      pushCurrent();
      index += 1;
      parentHeading = {
        title: heading[2].trim(),
        level: heading[1].length
      };
      current = {
        title: parentHeading.title,
        level: parentHeading.level,
        lines: [line],
        id: `${fileSlug}-${index}`,
        file
      };
      continue;
    }
    const summary = line.trim().match(/^<summary><strong>(.+)<\/strong><\/summary>$/);
    if (summary) {
      pushCurrent();
      index += 1;
      current = {
        title: cleanWikiSummaryTitle(summary[1]),
        level: Math.min(parentHeading.level + 1, 3),
        lines: [line],
        id: `${fileSlug}-${index}`,
        file,
        parentTitle: parentHeading.title
      };
      continue;
    }
    current.lines.push(line);
  }
  pushCurrent();
  return sections;
}

function wikiFileSlug(file) {
  return file.toLowerCase().replace(/\.md$/i, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function wikiAnchorSlug(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/<[^>]+>/g, "")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function sectionIdForWikiAnchor(file, anchor) {
  const cleanAnchor = String(anchor || "").replace(/^#/, "");
  if (!cleanAnchor) return "";
  const decodedAnchor = decodeURIComponent(cleanAnchor);
  const doc = wikiState.docs.find((candidate) => candidate.file === file);
  if (!doc) return "";
  const anchorSlug = wikiAnchorSlug(decodedAnchor);
  const section = doc.sections.find((candidate) => (
    candidate.id === decodedAnchor
    || wikiAnchorSlug(candidate.title) === anchorSlug
  ));
  return section?.id || "";
}

function markdownToPlainText(markdown) {
  return String(markdown || "")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1 $2")
    .replace(/[#>*|-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function cleanWikiSummaryTitle(value) {
  return String(value || "").replace(/<[^>]+>/g, "").trim();
}

function normalizeSearchText(value) {
  return String(value || "").toLowerCase().replace(/[_-]+/g, " ").replace(/[^a-z0-9.:#/\s]+/g, " ").replace(/\s+/g, " ").trim();
}

function compactSearchText(value) {
  return normalizeSearchText(value).replace(/[^a-z0-9]+/g, "");
}

function searchTokens(value) {
  return normalizeSearchText(value).split(" ").filter((token) => token.length > 1);
}

function wikiKeyIds(value) {
  return [...new Set(String(value || "").toLowerCase().match(/#?[a-z0-9]+(?:[_.:-][a-z0-9]+)+/g) || [])];
}

function wikiKeyMatchScore(value, query, compactQuery, tokens) {
  const normalized = normalizeSearchText(value);
  const compact = compactSearchText(value);
  if (!normalized || !compact) return 0;
  if (normalized === query || (compactQuery && compact === compactQuery)) return 180;
  if (normalized.startsWith(query) || (compactQuery && compact.startsWith(compactQuery))) return 120;
  if (normalized.includes(query) || (compactQuery && compact.includes(compactQuery))) return 90;
  if (tokens.length > 0 && tokens.every((token) => normalized.includes(token))) return 65;
  return 0;
}

function searchWiki() {
  const query = normalizeSearchText(wikiState.query);
  if (!query) {
    wikiState.resultMode = "pages";
    wikiState.results = wikiState.docs.map((doc) => ({
      type: "page",
      file: doc.file,
      title: doc.title,
      text: `${doc.sections.length} sections`
    }));
    return;
  }

  const tokens = searchTokens(query);
  const compactQuery = compactSearchText(query);
  const matches = [];
  for (const doc of wikiState.docs) {
    for (const section of doc.sections) {
      const title = normalizeSearchText(section.title);
      const titleCompact = compactSearchText(section.title);
      const parent = normalizeSearchText(section.parentTitle || "");
      const haystack = normalizeSearchText(`${doc.title} ${section.parentTitle || ""} ${section.title} ${section.text}`);
      const haystackCompact = compactSearchText(`${section.title} ${section.text}`);
      const titleWords = new Set(title.split(" ").filter(Boolean));
      const parentWords = new Set(parent.split(" ").filter(Boolean));
      const haystackWords = new Set(haystack.split(" ").filter(Boolean));
      const keyMatches = wikiKeyIds(`${section.title} ${section.text}`)
        .map((id) => ({
          id,
          score: wikiKeyMatchScore(id, query, compactQuery, tokens)
        }))
        .filter((match) => match.score > 0)
        .sort((a, b) => b.score - a.score || a.id.localeCompare(b.id));
      const keyScore = keyMatches[0]?.score || 0;
      let exactTokenMatches = 0;
      const tokenScore = tokens.reduce((sum, token) => {
        if (titleWords.has(token)) {
          exactTokenMatches += 1;
          return sum + 8;
        }
        if (title.includes(token)) return sum + 5;
        if (parentWords.has(token)) {
          exactTokenMatches += 1;
          return sum + 3;
        }
        if (parent.includes(token)) return sum + 2;
        if (haystackWords.has(token)) {
          exactTokenMatches += 1;
          return sum + 2;
        }
        return sum + (haystack.includes(token) ? 0.5 : 0);
      }, 0);
      let score = tokenScore;
      const exactTitleMatch = title === query || (compactQuery && titleCompact === compactQuery);
      const titlePrefixMatch = title.startsWith(query) || (compactQuery && titleCompact.startsWith(compactQuery));
      const titlePhraseMatch = title.includes(query) || (compactQuery && titleCompact.includes(compactQuery));
      const bodyPhraseMatch = haystack.includes(query) || (compactQuery && haystackCompact.includes(compactQuery));
      if (exactTitleMatch) score += 120;
      else if (titlePrefixMatch) score += 70;
      else if (titlePhraseMatch) score += 45;
      if (haystack.includes(query)) score += 18;
      if (compactQuery && haystackCompact.includes(compactQuery)) score += 18;
      if (tokens.length > 0 && tokens.every((token) => haystackWords.has(token) || title.includes(token))) score += 10;
      score += keyScore;
      if (tokens.length > 1 && exactTokenMatches < 2 && !titlePhraseMatch && !bodyPhraseMatch) continue;
      if (score > 0) {
        matches.push({
          type: keyScore > 0 ? "tag" : "section",
          matchKind: keyScore > 0 ? "tag" : "keyword",
          file: doc.file,
          sectionId: section.id,
          title: section.title,
          pageTitle: doc.title,
          parentTitle: section.parentTitle || "",
          text: sectionSnippet(section.text, keyMatches[0]?.id || tokens.find((token) => haystack.includes(token)) || query),
          score
        });
      }
    }
  }
  wikiState.resultMode = "matches";
  wikiState.results = matches.sort((a, b) => (
    (a.matchKind === "tag" ? -1 : 1) - (b.matchKind === "tag" ? -1 : 1)
    || b.score - a.score
    || a.pageTitle.localeCompare(b.pageTitle)
    || a.title.localeCompare(b.title)
  )).slice(0, 80);
}

function sectionSnippet(text, query) {
  const source = String(text || "").replace(/\s+/g, " ").trim();
  if (!source) return "";
  const lower = source.toLowerCase();
  const needle = String(query || "").toLowerCase().split(" ")[0];
  const index = needle ? lower.indexOf(needle) : -1;
  const start = index >= 0 ? Math.max(0, index - 58) : 0;
  const snippet = source.slice(start, start + 150);
  return `${start > 0 ? "... " : ""}${snippet}${start + 150 < source.length ? " ..." : ""}`;
}

function renderWiki() {
  if (!els.wikiResults || !els.wikiContent) return;
  validateWikiTabs();
  searchWiki();
  syncWikiSelectionToResults();
  renderWikiHighlightControls();
  renderWikiTabs();
  renderWikiResults();
  renderWikiContent();
  renderIcons();
}

function renderWikiHighlightControls() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) {
    lastWikiHighlightSelection = null;
    if (els.wikiHighlightButton) {
      els.wikiHighlightButton.disabled = true;
      els.wikiHighlightButton.classList.remove("is-on");
      els.wikiHighlightButton.setAttribute("aria-pressed", "false");
      els.wikiHighlightButton.setAttribute("data-tooltip", "Highlights cannot be created from the Highlights tab.");
    }
    return;
  }
  const current = selectedWikiHighlightText();
  const selection = lastWikiHighlightSelection;
  const isSaved = selection && wikiState.highlights.some((entry) => wikiHighlightMatchesSelection(entry, selection));
  if (els.wikiHighlightButton) {
    els.wikiHighlightButton.disabled = !current;
    els.wikiHighlightButton.classList.toggle("is-on", Boolean(isSaved));
    els.wikiHighlightButton.setAttribute("aria-pressed", isSaved ? "true" : "false");
    els.wikiHighlightButton.setAttribute("data-tooltip", !current
      ? "Select wiki text to highlight it."
      : isSaved
        ? `Remove "${current}" from saved highlights.`
        : `Highlight "${current}".`);
  }
}

function toggleCurrentWikiHighlight() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) return;
  const selection = currentWikiSelection() || lastWikiHighlightSelection;
  if (!selection) return;
  const contentScrollTop = els.wikiContent.scrollTop;
  const contentScrollLeft = els.wikiContent.scrollLeft;
  const existing = wikiState.highlights.find((entry) => wikiHighlightMatchesSelection(entry, selection));
  if (existing) {
    wikiState.highlights = wikiState.highlights.filter((entry) => entry.id !== existing.id);
    writeWikiHighlights(wikiState.highlights);
    lastWikiHighlightSelection = null;
    renderWiki();
    restoreWikiContentScroll(contentScrollTop, contentScrollLeft);
    return;
  }
  wikiState.highlights = normalizeWikiHighlights([...wikiState.highlights, selection]);
  writeWikiHighlights(wikiState.highlights);
  lastWikiHighlightSelection = null;
  renderWiki();
  restoreWikiContentScroll(contentScrollTop, contentScrollLeft);
}

function restoreWikiContentScroll(top, left) {
  window.requestAnimationFrame(() => {
    els.wikiContent.scrollTop = top;
    els.wikiContent.scrollLeft = left;
  });
}

function wikiHighlightIdentity(entry) {
  return [
    entry?.file || "",
    entry?.sectionId || "",
    Number.isFinite(Number(entry?.startOffset)) ? Number(entry.startOffset) : -1,
    Number.isFinite(Number(entry?.endOffset)) ? Number(entry.endOffset) : -1,
    Number.isFinite(Number(entry?.occurrenceIndex)) ? Number(entry.occurrenceIndex) : -1,
    normalizeWikiHighlightKey(entry?.text)
  ].join("|");
}

function wikiHighlightFallbackIdentity(entry) {
  return [
    entry?.file || "",
    entry?.sectionId || "",
    Number.isFinite(Number(entry?.occurrenceIndex)) ? Number(entry.occurrenceIndex) : -1,
    normalizeWikiHighlightKey(entry?.text)
  ].join("|");
}

function wikiHighlightMatchesSelection(entry, selection) {
  return wikiHighlightIdentity(entry) === wikiHighlightIdentity(selection)
    || wikiHighlightFallbackIdentity(entry) === wikiHighlightFallbackIdentity(selection);
}

function selectedWikiHighlightText() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) return "";
  const selection = currentWikiSelection();
  if (selection) {
    lastWikiHighlightSelection = selection;
    return selection.text;
  }
  return lastWikiHighlightSelection?.text || "";
}

function currentWikiTableSelection(range) {
  const table = (range.commonAncestorContainer.nodeType === Node.ELEMENT_NODE
    ? range.commonAncestorContainer
    : range.commonAncestorContainer.parentElement)?.closest?.("table");
  const fallbackTable = table || [...els.wikiContent.querySelectorAll("table")].find((candidate) => {
    try {
      return range.intersectsNode(candidate);
    } catch {
      return false;
    }
  });
  if (!fallbackTable) return null;
  const selectedRows = [];
  fallbackTable.querySelectorAll("tr").forEach((row) => {
    const cells = [...row.children].filter((cell) => ["TH", "TD"].includes(cell.tagName));
    const selectedCells = cells.filter((cell) => {
      try {
        return range.intersectsNode(cell);
      } catch {
        return false;
      }
    });
    if (selectedCells.length > 0) selectedRows.push(selectedCells);
  });
  if (selectedRows.length === 0) return null;
  const html = `<table>${selectedRows.map((cells) => (
    `<tr>${cells.map((cell) => `<${cell.tagName.toLowerCase()}>${sanitizeWikiHighlightHtml(cell.innerHTML)}</${cell.tagName.toLowerCase()}>`).join("")}</tr>`
  )).join("")}</table>`;
  const text = normalizeWikiHighlightTerm(selectedRows.map((cells) => (
    cells.map((cell) => cell.textContent.trim()).filter(Boolean).join(" ")
  )).filter(Boolean).join(" "));
  return text ? { html, text } : null;
}

function wikiHighlightOccurrenceIndex(range, text) {
  const before = document.createRange();
  before.selectNodeContents(els.wikiContent);
  before.setEnd(range.startContainer, range.startOffset);
  const haystack = normalizeWikiHighlightKey(before.toString());
  const needle = normalizeWikiHighlightKey(text);
  if (!needle) return -1;
  let count = 0;
  let index = haystack.indexOf(needle);
  while (index >= 0) {
    count += 1;
    index = haystack.indexOf(needle, index + needle.length);
  }
  return count;
}

function wikiContentTextIndex(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (!node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
      if (node.parentElement?.closest("script, style")) return NodeFilter.FILTER_REJECT;
      return NodeFilter.FILTER_ACCEPT;
    }
  });
  const textNodes = [];
  while (walker.nextNode()) textNodes.push(walker.currentNode);
  let text = "";
  const map = [];
  let previousWasSpace = true;
  textNodes.forEach((node) => {
    const firstVisible = node.nodeValue.search(/\S/);
    if (firstVisible >= 0 && text && !previousWasSpace) {
      text += " ";
      map.push(null);
      previousWasSpace = true;
    }
    [...node.nodeValue].forEach((character, offset) => {
      if (/\s/.test(character)) {
        if (!previousWasSpace) {
          text += " ";
          map.push({ node, offset });
          previousWasSpace = true;
        }
        return;
      }
      text += character.toLowerCase();
      map.push({ node, offset });
      previousWasSpace = false;
    });
  });
  return { text, map };
}

function wikiHighlightRangeOffsets(range) {
  const index = wikiContentTextIndex(els.wikiContent);
  let start = -1;
  let end = -1;
  index.map.forEach((position, offset) => {
    if (!position?.node) return;
    try {
      if (range.comparePoint(position.node, position.offset) !== 0) return;
    } catch {
      return;
    }
    if (start < 0) start = offset;
    end = offset + 1;
  });
  return start >= 0 && end > start ? { startOffset: start, endOffset: end } : { startOffset: -1, endOffset: -1 };
}

function currentWikiSelection() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) return null;
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;
  const range = selection.getRangeAt(0);
  if (!els.wikiContent.contains(range.commonAncestorContainer)) return null;
  const tableSelection = currentWikiTableSelection(range);
  const text = tableSelection?.text || normalizeWikiHighlightTerm(selection.toString());
  if (!text) return null;
  const fragment = range.cloneContents();
  const wrapper = document.createElement("div");
  wrapper.append(fragment);
  const doc = wikiState.docs.find((candidate) => candidate.file === wikiState.selectedFile);
  const offsets = wikiHighlightRangeOffsets(range);
  return {
    id: `highlight-${Date.now()}`,
    text,
    html: tableSelection?.html || sanitizeWikiHighlightHtml(wrapper.innerHTML || escapeHtml(text)),
    file: wikiState.selectedFile,
    title: doc?.title || "",
    sectionId: nearestWikiSectionId(range.commonAncestorContainer),
    occurrenceIndex: wikiHighlightOccurrenceIndex(range, text),
    ...offsets
  };
}

function nearestWikiSectionId(node) {
  let current = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
  while (current && current !== els.wikiContent) {
    if (current.id) return current.id;
    current = current.previousElementSibling || current.parentElement;
  }
  return "";
}

function ensureHighlightsTab() {
  wikiState.activeTabId = WIKI_HIGHLIGHTS_FILE;
  syncWikiSelectionFromActiveTab();
}

function updateWikiHighlightSelection() {
  const selection = currentWikiSelection();
  lastWikiHighlightSelection = selection;
  renderWikiHighlightControls();
}

function syncWikiSelectionToResults() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) return;
  if (!normalizeSearchText(wikiState.query) || wikiState.results.length === 0) return;
  const hasSelectedResult = wikiState.results.some((result) => (
    result.file === wikiState.selectedFile
    && (result.sectionId || "") === (wikiState.selectedSectionId || "")
  ));
  if (hasSelectedResult) return;
  wikiState.selectedFile = wikiState.results[0].file;
  wikiState.selectedSectionId = wikiState.results[0].sectionId || "";
  syncActiveWikiTabToSelection();
}

function renderWikiTabs() {
  if (!els.wikiTabs) return;
  const highlightsActive = isWikiHighlightsFile(wikiState.selectedFile);
  const highlightsButton = `
    <button class="wiki-bookmark-tab has-tooltip ${highlightsActive ? "is-active" : ""}" type="button" data-wiki-bookmark-tab="${WIKI_HIGHLIGHTS_FILE}" aria-current="${highlightsActive ? "page" : "false"}" data-tooltip="Highlights">
      ${icon("bookmark", "button-icon")}
    </button>
  `;
  const pageTabs = wikiState.tabs.map((tab) => {
    const doc = wikiState.docs.find((candidate) => candidate.file === tab.file);
    const label = doc?.title || tab.file.replace(/\.md$/i, "").replace(/-/g, " ");
    const active = tab.id === wikiState.activeTabId;
    const pinTooltip = tab.pinned ? "Unpin page." : "Pin page.";
    return `
      <div class="wiki-tab ${active ? "is-active" : ""} ${tab.pinned ? "is-pinned" : ""}" data-wiki-tab-id="${escapeHtml(tab.id)}" draggable="true">
        <button class="wiki-tab-button has-tooltip" type="button" data-wiki-tab="${escapeHtml(tab.id)}" aria-current="${active ? "page" : "false"}" data-tooltip="${escapeHtml(label)}">
          ${escapeHtml(label)}
        </button>
        <button class="wiki-tab-pin has-tooltip ${tab.pinned ? "is-on" : ""}" type="button" data-toggle-wiki-pin="${escapeHtml(tab.id)}" aria-pressed="${tab.pinned ? "true" : "false"}" aria-label="${escapeHtml(tab.pinned ? `Unpin ${label}` : `Pin ${label}`)}" data-tooltip="${pinTooltip}">
          ${icon("pin", "button-icon")}
        </button>
        ${wikiState.tabs.length > 1 ? `
          <button class="wiki-tab-close has-tooltip" type="button" data-close-wiki-tab="${escapeHtml(tab.id)}" aria-label="Close ${escapeHtml(label)}" data-tooltip="Close tab.">
            ${icon("x", "button-icon")}
          </button>
        ` : ""}
      </div>
    `;
  }).join("");
  els.wikiTabs.innerHTML = `${highlightsButton}${pageTabs}`;
}

function renderWikiResults() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) {
    els.wikiResults.innerHTML = `
      <div class="wiki-result-label">Highlights</div>
      ${wikiState.highlights.length === 0
        ? `<div class="wiki-status">No highlighted wiki text yet.</div>`
        : wikiState.highlights.map((entry) => `
          <div class="wiki-result wiki-highlight-result" draggable="true" data-highlight-source="${escapeHtml(entry.id)}">
            <button class="wiki-highlight-result-main" type="button" data-highlight-source-jump="${escapeHtml(entry.id)}">
              <span>${escapeHtml(entry.title || entry.file || "Wiki")}</span>
              <small>${escapeHtml(entry.text)}</small>
            </button>
            <button class="wiki-highlight-delete has-tooltip" type="button" data-delete-wiki-highlight="${escapeHtml(entry.id)}" aria-label="Delete highlight" data-tooltip="Delete highlight.">
              ${icon("trash-2", "button-icon")}
            </button>
          </div>
        `).join("")}
    `;
    return;
  }
  if (wikiState.status) {
    els.wikiResults.innerHTML = `<div class="wiki-status">${escapeHtml(wikiState.status)}</div>`;
    return;
  }
  if (wikiState.results.length === 0) {
    els.wikiResults.innerHTML = `
      <div class="wiki-result-label">${wikiState.resultMode === "pages" ? "Pages" : "Keyword matches"}</div>
      <div class="wiki-status">No matching wiki entries.</div>
    `;
    return;
  }
  if (wikiState.resultMode === "pages") {
    els.wikiResults.innerHTML = `
      <div class="wiki-result-label">Pages</div>
      ${wikiState.results.map(renderWikiResultButton).join("")}
    `;
    return;
  }
  const tagMatches = wikiState.results.filter((result) => result.matchKind === "tag");
  const keywordMatches = wikiState.results.filter((result) => result.matchKind !== "tag");
  els.wikiResults.innerHTML = [
    tagMatches.length > 0 ? `<div class="wiki-result-label">Tag matches</div>${tagMatches.map(renderWikiResultButton).join("")}` : "",
    keywordMatches.length > 0 ? `<div class="wiki-result-label">Keyword matches</div>${keywordMatches.map(renderWikiResultButton).join("")}` : ""
  ].filter(Boolean).join("");
}

function renderWikiResultButton(result) {
  const isActive = result.file === wikiState.selectedFile && (!result.sectionId || result.sectionId === wikiState.selectedSectionId);
  const titleParts = [result.pageTitle, result.parentTitle, result.title].filter((part, index, parts) => part && parts.indexOf(part) === index);
  const sectionLabel = titleParts.length > 1 ? titleParts.join(" / ") : result.title;
  const tooltip = `${sectionLabel} Middle-click to open in a new tab.`;
  return `
    <button class="wiki-result has-tooltip ${isActive ? "is-active" : ""} ${result.type !== "page" ? "is-section-match" : ""} ${result.matchKind === "tag" ? "is-tag-match" : ""}" type="button" data-file="${escapeHtml(result.file)}" data-section="${escapeHtml(result.sectionId || "")}" data-tooltip="${escapeHtml(tooltip)}">
      <span>${renderWikiInline(sectionLabel, wikiState.query)}</span>
      <small>${renderWikiInline(result.text || result.file, wikiState.query)}</small>
    </button>
  `;
}

function renderWikiContent() {
  if (isWikiHighlightsFile(wikiState.selectedFile)) {
    renderWikiHighlightsContent();
    return;
  }
  const doc = wikiState.docs.find((candidate) => candidate.file === wikiState.selectedFile) || wikiState.docs[0];
  if (!doc) {
    els.wikiContent.innerHTML = wikiState.status ? "" : `<div class="empty-state">Open a version to load the wiki.</div>`;
    return;
  }
  els.wikiContent.innerHTML = markdownToWikiHtml(doc.markdown, doc.file, wikiState.query, new Set(wikiState.results.map((result) => result.sectionId).filter(Boolean)), wikiState.selectedSectionId);
  if (wikiState.selectedSectionId) {
    window.requestAnimationFrame(() => {
      els.wikiContent.querySelector(`#${CSS.escape(wikiState.selectedSectionId)}`)?.scrollIntoView({ block: "start" });
    });
  }
}

function renderWikiHighlightsContent() {
  if (wikiState.highlights.length === 0) {
    els.wikiContent.innerHTML = `<div class="empty-state">Select wiki text and press Highlight to save it here.</div>`;
    return;
  }
  els.wikiContent.innerHTML = `
    <h1>Highlights</h1>
    <div class="wiki-highlight-list">
      ${wikiState.highlights.map((entry) => `
        <article class="wiki-highlight-card" id="${escapeHtml(entry.id)}" draggable="true" data-highlight-id="${escapeHtml(entry.id)}">
          <div class="wiki-highlight-card-source">${escapeHtml(entry.title || entry.file || "Wiki")}</div>
          <div class="wiki-highlight-card-body">${renderSavedWikiHighlightHtml(entry.html, entry.text)}</div>
        </article>
      `).join("")}
    </div>
  `;
}

function renderSavedWikiHighlightHtml(html, text) {
  const source = normalizeWikiHighlightFragment(html || escapeHtml(text));
  const template = document.createElement("template");
  template.innerHTML = source;
  template.content.querySelectorAll("mark.wiki-user-mark").forEach((mark) => mark.replaceWith(...mark.childNodes));
  return template.innerHTML || escapeHtml(text);
}

function reorderWikiHighlight(fromId, toId, placement) {
  if (!fromId || !toId || fromId === toId) return;
  const fromIndex = wikiState.highlights.findIndex((entry) => entry.id === fromId);
  let toIndex = wikiState.highlights.findIndex((entry) => entry.id === toId);
  if (fromIndex < 0 || toIndex < 0) return;
  if (placement === "after") toIndex += 1;
  const [entry] = wikiState.highlights.splice(fromIndex, 1);
  if (fromIndex < toIndex) toIndex -= 1;
  wikiState.highlights.splice(Math.max(0, Math.min(toIndex, wikiState.highlights.length)), 0, entry);
  writeWikiHighlights(wikiState.highlights);
  renderWiki();
}

function deleteWikiHighlight(id) {
  const nextHighlights = wikiState.highlights.filter((entry) => entry.id !== id);
  if (nextHighlights.length === wikiState.highlights.length) return;
  wikiState.highlights = nextHighlights;
  writeWikiHighlights(wikiState.highlights);
  renderWiki();
}

function wikiHighlightDropPlacement(event, element) {
  const rect = element.getBoundingClientRect();
  return event.clientY > rect.top + rect.height / 2 ? "after" : "before";
}

function clearWikiHighlightDropIndicators() {
  document.querySelectorAll(".wiki-highlight-card.is-drop-before, .wiki-highlight-card.is-drop-after, .wiki-highlight-result.is-drop-before, .wiki-highlight-result.is-drop-after").forEach((element) => {
    element.classList.remove("is-drop-before", "is-drop-after");
  });
}

function markdownToWikiHtml(markdown, file, query, highlightedSectionIds, selectedSectionId = "") {
  const lines = String(markdown || "").split(/\r?\n/);
  let html = "";
  let inCode = false;
  let codeLines = [];
  let inList = false;
  let listTag = "ul";
  let inTable = false;
  let tableRows = [];
  let sectionIndex = 0;
  let pendingDetails = false;
  const flushPendingDetails = () => {
    if (!pendingDetails) return;
    html += "<details>";
    pendingDetails = false;
  };
  const closeList = () => {
    if (inList) {
      html += `</${listTag}>`;
      inList = false;
      listTag = "ul";
    }
  };
  const closeTable = () => {
    if (!inTable) return;
    html += renderWikiTable(tableRows, query);
    tableRows = [];
    inTable = false;
  };

  for (const line of lines) {
    if (line.startsWith("```")) {
      closeList();
      closeTable();
      if (inCode) {
        html += `<pre><code>${highlightWikiText(escapeHtml(codeLines.join("\n")), query)}</code></pre>`;
        codeLines = [];
        inCode = false;
      } else {
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      codeLines.push(line);
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      closeList();
      closeTable();
      flushPendingDetails();
      sectionIndex += 1;
      const level = Math.min(4, heading[1].length);
      const id = `${wikiFileSlug(file)}-${sectionIndex}`;
      const className = highlightedSectionIds.has(id) ? " class=\"wiki-hit-section\"" : "";
      html += `<h${level} id="${escapeHtml(id)}"${className}>${renderWikiInline(heading[2].trim(), query)}</h${level}>`;
      continue;
    }

    const detailsLine = line.trim();
    if (detailsLine === "<details>") {
      closeList();
      closeTable();
      pendingDetails = true;
      continue;
    }

    if (detailsLine === "</details>") {
      closeList();
      closeTable();
      flushPendingDetails();
      html += detailsLine;
      continue;
    }

    const summary = detailsLine.match(/^<summary><strong>(.+)<\/strong><\/summary>$/);
    if (summary) {
      closeList();
      closeTable();
      sectionIndex += 1;
      const id = `${wikiFileSlug(file)}-${sectionIndex}`;
      const className = highlightedSectionIds.has(id) ? " class=\"wiki-hit-section\"" : "";
      if (pendingDetails) {
        html += `<details${selectedSectionId === id ? " open" : ""}>`;
        pendingDetails = false;
      }
      html += `<summary id="${escapeHtml(id)}"${className}><strong>${renderWikiInline(summary[1], query)}</strong></summary>`;
      continue;
    }

    if (/^\|.+\|$/.test(line.trim())) {
      closeList();
      flushPendingDetails();
      inTable = true;
      tableRows.push(line);
      continue;
    }
    closeTable();

    const listItem = line.match(/^\s*-\s+(.+)$/);
    if (listItem) {
      if (!inList || listTag !== "ul") {
        closeList();
        flushPendingDetails();
        html += "<ul>";
        listTag = "ul";
        inList = true;
      }
      html += `<li>${renderWikiInline(listItem[1], query)}</li>`;
      continue;
    }

    const orderedListItem = line.match(/^\s*\d+\.\s+(.+)$/);
    if (orderedListItem) {
      if (!inList || listTag !== "ol") {
        closeList();
        flushPendingDetails();
        html += "<ol>";
        listTag = "ol";
        inList = true;
      }
      html += `<li>${renderWikiInline(orderedListItem[1], query)}</li>`;
      continue;
    }

    if (!line.trim()) {
      closeList();
      html += "";
      continue;
    }

    closeList();
    flushPendingDetails();
    html += `<p>${renderWikiInline(line, query)}</p>`;
  }
  closeList();
  closeTable();
  flushPendingDetails();
  if (inCode) {
    html += `<pre><code>${highlightWikiText(escapeHtml(codeLines.join("\n")), query)}</code></pre>`;
  }
  return html;
}

function renderWikiTable(rows, query) {
  const filteredRows = rows.filter((row) => !/^\|\s*-+/.test(row));
  if (filteredRows.length === 0) return "";
  return `<table>${filteredRows.map((row, index) => {
    const cells = row.trim().replace(/^\||\|$/g, "").split("|").map((cell) => cell.trim());
    const tag = index === 0 ? "th" : "td";
    return `<tr>${cells.map((cell) => `<${tag}>${renderWikiInline(cell, query)}</${tag}>`).join("")}</tr>`;
  }).join("")}</table>`;
}

function renderWikiInline(text, query) {
  const placeholders = [];
  const hold = (value) => {
    const key = `@@$P${placeholders.length}$@@`;
    placeholders.push(value);
    return key;
  };
  let output = escapeHtml(text);
  output = output.replace(/`([^`]+)`/g, (_, code) => hold(`<code>${highlightWikiText(code, query)}</code>`));
  output = output.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, href) => {
    const safeHref = String(href || "");
    if (/\.md(?:#.*)?$/i.test(safeHref)) {
      const [path, anchor = ""] = safeHref.split("#");
      const file = path.split("/").pop();
      const tooltip = `${label} Middle-click to open in a new tab.`;
      return hold(`<a href="#" class="has-tooltip" data-wiki-link="${escapeHtml(file)}" data-wiki-anchor="${escapeHtml(anchor)}" data-tooltip="${escapeHtml(tooltip)}">${label}</a>`);
    }
    return hold(`<a href="${escapeHtml(safeHref)}" target="_blank" rel="noopener noreferrer">${label}</a>`);
  });
  output = highlightWikiText(output, query);
  output = output.replace(/@@\$P(\d+)\$@@/g, (_, index) => placeholders[Number(index)] || "");
  return output;
}

function highlightWikiText(value, query) {
  const normalizedQuery = normalizeSearchText(query);
  const terms = normalizedQuery.split(" ").filter((term) => term.length > 1).slice(0, 6);
  if (terms.length === 0) return value;
  const compactQuery = compactSearchText(query);
  const placeholders = [];
  const hold = (value) => {
    const key = `@@$M${placeholders.length}$@@`;
    placeholders.push(value);
    return key;
  };
  let output = value;
  output = output.replace(/#?[a-z0-9]+(?:[_.:-][a-z0-9]+)+/gi, (match) => {
    if (wikiKeyMatchScore(match, normalizedQuery, compactQuery, terms) <= 0) return match;
    return hold(`<mark class="wiki-key-mark">${match}</mark>`);
  });
  for (const term of terms) {
    const escapedTerm = term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    output = output.replace(new RegExp(`(${escapedTerm})`, "gi"), "<mark>$1</mark>");
  }
  return output.replace(/@@\$M(\d+)\$@@/g, (_, index) => placeholders[Number(index)] || "");
}

function updateLeftPanelMode(width = undefined) {
  els.leftRail.classList.add("is-compact");
}

function updatePanelSnapMode(target, size = undefined) {
  const panel = {
    checks: els.checks?.closest(".checks-panel"),
    files: els.fileTree?.closest(".files-panel")
  }[target];
  if (!panel) return;
  const measuredSize = Number.isFinite(size)
    ? size
    : panel.getBoundingClientRect().height;
  panel.classList.toggle("is-title-only", measuredSize > 0 && measuredSize <= RIGHT_PANEL_TITLE_ONLY_HEIGHT);
}

function toggleRightPanelSnap(target) {
  const panel = {
    checks: els.checks?.closest(".checks-panel"),
    files: els.fileTree?.closest(".files-panel")
  }[target];
  if (!panel) return;
  if (panel.classList.contains("is-title-only")) {
    resetPanelSize(target);
  } else {
    savePanelSize(target, RIGHT_PANEL_TITLE_ONLY_HEIGHT);
  }
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), Math.max(min, max));
}

function clampPanelSize(target, value) {
  const base = PANEL_SIZE_LIMITS[target];
  if (!base) return value;
  let max = base.max;

  if (target === "left" || target === "right") {
    const workspaceWidth = els.workspace.getBoundingClientRect().width || window.innerWidth;
    const railWidth = els.leftRail.getBoundingClientRect().width || 0;
    const leftWidth = target === "left" ? value : showLeftPanel ? els.fileExplorer.getBoundingClientRect().width : 0;
    const rightWidth = target === "right" ? value : showRightPanel ? els.rightRail.getBoundingClientRect().width : 0;
    const otherWidth = target === "left" ? rightWidth : leftWidth;
    max = Math.min(base.max, workspaceWidth - railWidth - otherWidth - MIN_BUILDER_WIDTH - 14);
  }

  if (target === "checks") {
    const railHeight = els.rightRail.getBoundingClientRect().height;
    max = Math.min(base.max, railHeight - MIN_PREVIEW_HEIGHT - 7);
  }

  return clamp(value, base.min, max);
}

function panelResizeStart(event) {
  const handle = event.target.closest(".panel-resizer");
  if (!handle) return;
  const target = handle.dataset.resizeTarget;
  if (!PANEL_SIZE_VARS[target]) return;
  if (event.button === 1) {
    event.preventDefault();
    resetPanelSize(target);
    return;
  }
  if (event.button !== 0) return;
  event.preventDefault();
  panelResizeState = {
    target,
    handle,
    startX: event.clientX,
    startY: event.clientY,
    left: els.fileExplorer.getBoundingClientRect().width,
    right: els.rightRail.getBoundingClientRect().width,
    checks: els.checks.closest(".checks-panel").getBoundingClientRect().height
  };
  handle.classList.add("is-dragging");
  document.body.classList.add(target === "checks" ? "is-resizing-row" : "is-resizing");
  document.addEventListener("pointermove", panelResizeMove);
  document.addEventListener("pointerup", panelResizeEnd, { once: true });
}

function panelResizeMove(event) {
  if (!panelResizeState) return;
  const dx = event.clientX - panelResizeState.startX;
  const dy = event.clientY - panelResizeState.startY;
  const target = panelResizeState.target;
  let next = {
    left: panelResizeState.left + dx,
    right: panelResizeState.right - dx,
    checks: panelResizeState.checks + dy
  }[target];
  if (target === "checks") {
    const startedTitleOnly = panelResizeState[target] <= RIGHT_PANEL_TITLE_ONLY_HEIGHT;
    if (next <= RIGHT_PANEL_EXPANDED_SNAP_HEIGHT) {
      next = RIGHT_PANEL_TITLE_ONLY_HEIGHT;
    } else if (startedTitleOnly) {
      next = Math.max(next, RIGHT_PANEL_EXPANDED_SNAP_HEIGHT);
    }
  }
  savePanelSize(target, next);
}

function panelResizeEnd() {
  panelResizeState?.handle.classList.remove("is-dragging");
  panelResizeState = null;
  document.body.classList.remove("is-resizing", "is-resizing-row");
  document.removeEventListener("pointermove", panelResizeMove);
}

function panelResizeKeydown(event) {
  const handle = event.target.closest(".panel-resizer");
  if (!handle || !PANEL_SIZE_VARS[handle.dataset.resizeTarget]) return;
  const target = handle.dataset.resizeTarget;
  const step = event.shiftKey ? 32 : 16;
  const isColumn = target === "left" || target === "right";
  const isRow = target === "checks";
  let direction = 0;

  if (isColumn && event.key === "ArrowLeft") direction = target === "left" ? -1 : 1;
  if (isColumn && event.key === "ArrowRight") direction = target === "left" ? 1 : -1;
  if (isRow && event.key === "ArrowUp") direction = -1;
  if (isRow && event.key === "ArrowDown") direction = 1;
  if (!direction) return;

  event.preventDefault();
  const current = {
    left: els.fileExplorer.getBoundingClientRect().width,
    right: els.rightRail.getBoundingClientRect().width,
    checks: els.checks.closest(".checks-panel").getBoundingClientRect().height
  }[target];
  let next = current + direction * step;
  if (target === "checks" && next <= RIGHT_PANEL_EXPANDED_SNAP_HEIGHT) {
    next = direction < 0 ? RIGHT_PANEL_TITLE_ONLY_HEIGHT : RIGHT_PANEL_EXPANDED_SNAP_HEIGHT;
  }
  savePanelSize(target, next);
}

function panelResizeAuxClick(event) {
  const handle = event.target.closest(".panel-resizer");
  if (!handle || event.button !== 1) return;
  event.preventDefault();
  resetPanelSize(handle.dataset.resizeTarget);
}

function keepPanelSizesInRange() {
  if (window.matchMedia?.("(max-width: 900px)").matches) return;
  const sizes = readPanelSizes();
  for (const target of Object.keys(PANEL_SIZE_VARS)) {
    if (sizes[target] !== undefined) {
      savePanelSize(target, Number(sizes[target]));
    }
  }
}

function wikiPointerStart(event) {
  if (!wikiState.isOpen || event.button !== 0) return;
  const resizeHandle = event.target.closest("[data-wiki-resize]");
  const canDrag = event.target.closest("#wiki-titlebar") && !event.target.closest("button, select, input, a");
  if (!resizeHandle && !canDrag) return;
  event.preventDefault();
  const rect = els.wikiWindow.getBoundingClientRect();
  wikiPointerState = {
    mode: resizeHandle ? "resize" : "move",
    edge: resizeHandle?.dataset.wikiResize || "",
    startX: event.clientX,
    startY: event.clientY,
    left: rect.left,
    top: rect.top,
    width: rect.width,
    height: rect.height
  };
  els.wikiWindow.classList.add("is-moving");
  document.body.classList.add("is-wiki-moving");
  document.addEventListener("pointermove", wikiPointerMove);
  document.addEventListener("pointerup", wikiPointerEnd, { once: true });
}

function wikiPointerMove(event) {
  if (!wikiPointerState) return;
  const dx = event.clientX - wikiPointerState.startX;
  const dy = event.clientY - wikiPointerState.startY;
  let next = {
    left: wikiPointerState.left,
    top: wikiPointerState.top,
    width: wikiPointerState.width,
    height: wikiPointerState.height
  };

  if (wikiPointerState.mode === "move") {
    next.left += dx;
    next.top += dy;
  } else {
    if (wikiPointerState.edge === "right" || wikiPointerState.edge === "corner") next.width += dx;
    if (wikiPointerState.edge === "bottom" || wikiPointerState.edge === "corner") next.height += dy;
  }
  applyWikiLayout(next);
}

function wikiPointerEnd() {
  wikiPointerState = null;
  els.wikiWindow.classList.remove("is-moving");
  document.body.classList.remove("is-wiki-moving");
  document.removeEventListener("pointermove", wikiPointerMove);
}

const MINECRAFT_COLORS = {
  0: "#000000",
  1: "#0000aa",
  2: "#00aa00",
  3: "#00aaaa",
  4: "#aa0000",
  5: "#aa00aa",
  6: "#ffaa00",
  7: "#aaaaaa",
  8: "#555555",
  9: "#5555ff",
  a: "#55ff55",
  b: "#55ffff",
  c: "#ff5555",
  d: "#ff55ff",
  e: "#ffff55",
  f: "#ffffff"
};

const MINECRAFT_STYLE_CODES = new Set(["l", "m", "n", "o"]);

const tooltipLayer = document.createElement("div");
tooltipLayer.id = "minecraft-tooltip";
tooltipLayer.className = "minecraft-tooltip";
tooltipLayer.setAttribute("role", "tooltip");
tooltipLayer.setAttribute("aria-hidden", "true");
document.body.appendChild(tooltipLayer);

let activeTooltipTarget = null;
let activeTooltipPointer = null;

function minecraftTooltipHtml(source) {
  const lines = String(source ?? "").replace(/\r\n?/g, "\n").split("\n");
  return lines.map((line) => `<span class="minecraft-tooltip-line">${minecraftLineHtml(line)}</span>`).join("");
}

function minecraftLineHtml(line) {
  const segments = [];
  let style = defaultMinecraftStyle();
  let text = "";

  const flush = () => {
    if (!text) return;
    segments.push(`<span class="${minecraftStyleClass(style)}" style="color: ${style.color}">${escapeHtml(text)}</span>`);
    text = "";
  };

  for (let index = 0; index < line.length; index++) {
    const char = line[index];
    const next = line[index + 1]?.toLowerCase();
    if ((char === "&" || char === "\u00a7") && next && isMinecraftFormatCode(next)) {
      flush();
      style = applyMinecraftFormat(style, next);
      index++;
      continue;
    }
    text += char;
  }
  flush();

  return segments.join("") || "&nbsp;";
}

function defaultMinecraftStyle() {
  return {
    color: MINECRAFT_COLORS.f,
    bold: false,
    italic: false,
    underlined: false,
    strikethrough: false
  };
}

function isMinecraftFormatCode(code) {
  return code === "r" || MINECRAFT_STYLE_CODES.has(code) || Object.hasOwn(MINECRAFT_COLORS, code);
}

function applyMinecraftFormat(style, code) {
  if (code === "r") return defaultMinecraftStyle();
  if (Object.hasOwn(MINECRAFT_COLORS, code)) {
    return { ...defaultMinecraftStyle(), color: MINECRAFT_COLORS[code] };
  }
  return {
    ...style,
    bold: style.bold || code === "l",
    italic: style.italic || code === "o",
    underlined: style.underlined || code === "n",
    strikethrough: style.strikethrough || code === "m"
  };
}

function minecraftStyleClass(style) {
  return [
    style.bold ? "mc-bold" : "",
    style.italic ? "mc-italic" : "",
    style.underlined ? "mc-underlined" : "",
    style.strikethrough ? "mc-strikethrough" : ""
  ].filter(Boolean).join(" ");
}

function tooltipTarget(element) {
  return element?.closest?.("[data-tooltip]");
}

function showTooltip(target, pointer = null) {
  const text = target?.dataset.tooltip;
  if (!text) return;
  if (!pointer && target === activeTooltipTarget && activeTooltipPointer) return;
  activeTooltipTarget = target;
  activeTooltipPointer = pointer;
  tooltipLayer.innerHTML = minecraftTooltipHtml(text);
  tooltipLayer.setAttribute("aria-hidden", "false");
  tooltipLayer.classList.add("is-visible");
  target.setAttribute("aria-describedby", tooltipLayer.id);
  requestAnimationFrame(positionTooltip);
}

function hideTooltip(target = activeTooltipTarget) {
  if (!activeTooltipTarget || target !== activeTooltipTarget) return;
  activeTooltipTarget.removeAttribute("aria-describedby");
  activeTooltipTarget = null;
  activeTooltipPointer = null;
  tooltipLayer.classList.remove("is-visible");
  tooltipLayer.setAttribute("aria-hidden", "true");
  tooltipLayer.style.transform = "translate3d(-9999px, -9999px, 0)";
}

function positionTooltip() {
  if (!activeTooltipTarget) return;
  const rect = activeTooltipTarget.getBoundingClientRect();
  const tooltipRect = tooltipLayer.getBoundingClientRect();
  const gap = 10;
  const margin = 12;
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  let x;
  let y;

  if (activeTooltipPointer) {
    x = activeTooltipPointer.x + 14;
    y = activeTooltipPointer.y + 12;
  } else if (activeTooltipTarget.closest(".section-tabs")) {
    x = rect.right + 14;
    y = rect.top + rect.height / 2 - tooltipRect.height / 2;
  } else {
    x = rect.left;
    y = rect.bottom + gap;
  }

  if (x + tooltipRect.width > viewportWidth - margin) {
    x = Math.max(margin, rect.left - tooltipRect.width - gap);
  }
  if (y + tooltipRect.height > viewportHeight - margin) {
    y = Math.max(margin, rect.top - tooltipRect.height - gap);
  }

  x = Math.max(margin, Math.min(x, viewportWidth - tooltipRect.width - margin));
  y = Math.max(margin, Math.min(y, viewportHeight - tooltipRect.height - margin));
  tooltipLayer.style.transform = `translate3d(${Math.round(x)}px, ${Math.round(y)}px, 0)`;
}

function tooltipAttrs(text, className = "tooltip-label") {
  if (!text) return "";
  const tooltip = escapeHtml(text);
  return ` class="${className}" data-tooltip="${tooltip}" tabindex="0"`;
}

function tooltipForField(id, help = "") {
  return FIELD_TOOLTIPS[id] || help || "";
}

function tooltipForFlag(flag) {
  return FLAG_TOOLTIPS[flag] || humanize(flag);
}

function tooltipForToggleFlag(flag, prefix) {
  const base = tooltipForFlag(flag);
  if (prefix === "line" && BETA_13_PLANNED_DIALOGUE_LINE_DEPRECATION_KEYS.includes(flag)) {
    return `${base} Planned for beta.13 deprecation; use conditions instead.`;
  }
  if (prefix === "option" && BETA_13_PLANNED_DIALOGUE_OPTION_DEPRECATION_KEYS.includes(flag)) {
    return `${base} Planned for beta.13 deprecation; use conditions instead.`;
  }
  return base;
}

function tooltipForTag(fieldId, value) {
  if (fieldId.includes("event_tags")) return EVENT_TAG_TOOLTIPS[value] || "Village-memory tag accepted by event_tags or player_event_tags.";
  if (fieldId.includes("professions")) return value === "none" || value === "unemployed"
    ? "Matches villagers with no profession."
    : `Matches ${humanize(value)} villagers. Custom professions should use a full registered id.`;
  if (fieldId.includes("moods")) return MOOD_TOOLTIPS[value] || "Temporary beta.12 villager mood filter.";
  if (fieldId.includes("dispositions")) return DISPOSITION_TOOLTIPS[value] || "Dialogue disposition filter derived from reputation and context.";
  if (fieldId.includes("reputation_levels")) return `Matches the ${humanize(value)} reputation tier. Prefer tier names over fixed numeric reputation.`;
  if (fieldId.includes("player_item_slots")) return ITEM_SLOT_TOOLTIPS[value] || "Player item slot filter. Defaults to hands when player_items is set.";
  if (fieldId.includes("weather")) return `Matches ${humanize(value)} weather.`;
  if (fieldId.includes("times")) return `Matches the ${humanize(value)} time window.`;
  if (fieldId.includes("outcomes")) return `Matches the ${humanize(value)} pacification result.`;
  return `Insert ${value}.`;
}

function slugify(value, fallback = "my_pack") {
  const slug = String(value || "")
    .trim()
    .toLowerCase()
    .replace(/['"]/g, "")
    .replace(/[^a-z0-9_./-]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "");
  return slug || fallback;
}

function namespaceify(value, fallback = "my_pack") {
  const namespace = slugify(value, fallback).replace(/[^a-z0-9_.-]/g, "_");
  return namespace || fallback;
}

function normalizeFileName(value, fallback) {
  return slugify(value, fallback).replace(/\.json$/i, "");
}

function capitalize(value) {
  const text = String(value || "");
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : "";
}

function humanize(value) {
  return String(value || "")
    .replace(/_/g, " ")
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function parseList(value) {
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
  if (typeof value === "string") {
    return value
      .split(/[\n,]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

function listToText(value) {
  if (Array.isArray(value)) return value.join(", ");
  return value || "";
}

function prettyJson(value) {
  if (!value || typeof value !== "object") return "";
  if (Array.isArray(value) && value.length === 0) return "";
  if (!Array.isArray(value) && Object.keys(value).length === 0) return "";
  return JSON.stringify(value, null, 2);
}

function parseNumber(value) {
  if (value === "" || value === null || value === undefined) return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function parseInteger(value) {
  const number = parseNumber(value);
  return number === undefined ? undefined : Math.trunc(number);
}

function packVersionInfo(version = state.meta.packVersion) {
  return PACK_VERSIONS.find((candidate) => candidate.id === version) || PACK_VERSIONS[PACK_VERSIONS.length - 1];
}

function normalizePackVersion(value) {
  if (typeof value !== "string") return "";
  const text = value.trim();
  if (!text) return "";
  const lower = text.toLowerCase();
  return PACK_VERSION_IDS.find((id) => id.toLowerCase() === lower || id.toLowerCase().endsWith(lower)) || "";
}

function readPackVersion(json) {
  const vr = json?.[PACK_VERSION_NAMESPACE] || json?.villager_retaliation || json?.vr;
  return normalizePackVersion(
    vr?.[PACK_VERSION_STORAGE_KEY]
      || vr?.packVersion
      || json?.[PACK_VERSION_STORAGE_KEY]
      || json?.packVersion
  );
}

function packVersionIndex(version) {
  return PACK_VERSION_IDS.indexOf(normalizePackVersion(version));
}

function packVersionAtLeast(version, minimumVersion) {
  const versionIndex = packVersionIndex(version);
  const minimumIndex = packVersionIndex(minimumVersion);
  return versionIndex >= 0 && minimumIndex >= 0 && versionIndex >= minimumIndex;
}

function supportsBeta12DialogueFields(version = state.meta.packVersion) {
  return packVersionAtLeast(version, "1.0.0-beta.12");
}

function isValidDialogueOptionType(entry) {
  return entry?.type === "dialogue_option" || (supportsBeta12DialogueFields() && !entry?.type);
}

function beta12DialogueKeysForKind(kind) {
  if (kind === "options") return BETA_12_ONLY_DIALOGUE_OPTION_KEYS;
  if (kind === "lines") return BETA_12_ONLY_DIALOGUE_LINE_KEYS;
  return [];
}

function hiddenDialogueVersionKeys(kind) {
  return supportsBeta12DialogueFields() ? [] : beta12DialogueKeysForKind(kind);
}

function plannedBeta13DialogueDeprecationKeysForKind(kind) {
  if (kind === "options") return BETA_13_PLANNED_DIALOGUE_OPTION_DEPRECATION_KEYS;
  if (kind === "lines") return BETA_13_PLANNED_DIALOGUE_LINE_DEPRECATION_KEYS;
  return [];
}

function authoredDialogueFlags(flags, kind) {
  const hidden = new Set(plannedBeta13DialogueDeprecationKeysForKind(kind));
  return flags.filter((flag) => !hidden.has(flag));
}

function inferPackVersionFromFiles(files) {
  for (const [path, value] of Object.entries(files)) {
    if (path.replace(/^\/+/, "") === "pack.mcmeta" && typeof value === "string") {
      try {
        const version = readPackVersion(JSON.parse(value));
        if (version) return version;
      } catch {
        // A malformed pack.mcmeta will be kept as an extra file by the importer.
      }
    }
  }
  const paths = Object.keys(files).map((path) => path.replace(/^\/+/, ""));
  const hasBeta12DialogueField = Object.entries(files).some(([path, value]) => (
    /^data\/villagerretaliation\/dialogue\/.+\.json$/.test(path.replace(/^\/+/, ""))
    && typeof value === "string"
    && jsonContainsAnyKey(value, BETA_12_ONLY_DIALOGUE_KEYS)
  ));
  if (hasBeta12DialogueField) return "1.0.0-beta.12";
  const hasBeta11Path = paths.some((path) => (
    /^data\/villagerretaliation\/forced_dialogue\/.+\.json$/.test(path)
    || /^data\/villagerretaliation\/pacification\/.+\.json$/.test(path)
    || /^data\/villagerretaliation\/villager_names\/preset_names\.json$/.test(path)
    || /^data\/[^/]+\/story_(structures|biomes)\/.+\.json$/.test(path)
  ));
  return hasBeta11Path ? "1.0.0-beta.11" : "";
}

function jsonContainsAnyKey(source, keys) {
  try {
    return valueContainsAnyKey(JSON.parse(stripTextBom(source)), new Set(keys));
  } catch {
    return false;
  }
}

function valueContainsAnyKey(value, keys) {
  if (Array.isArray(value)) return value.some((item) => valueContainsAnyKey(item, keys));
  if (!value || typeof value !== "object") return false;
  return Object.entries(value).some(([key, child]) => keys.has(key) || valueContainsAnyKey(child, keys));
}

function cleanObject(value) {
  if (Array.isArray(value)) {
    return value
      .map((item) => cleanObject(item))
      .filter((item) => item !== undefined && !(Array.isArray(item) && item.length === 0));
  }
  if (value && typeof value === "object" && !(value instanceof Uint8Array)) {
    const result = {};
    for (const [key, child] of Object.entries(value)) {
      if (key.startsWith("__")) continue;
      const cleaned = cleanObject(child);
      const emptyArray = Array.isArray(cleaned) && cleaned.length === 0;
      const emptyObject = cleaned && typeof cleaned === "object" && !Array.isArray(cleaned) && Object.keys(cleaned).length === 0;
      if (cleaned !== undefined && cleaned !== "" && !emptyArray && !emptyObject) {
        result[key] = cleaned;
      }
    }
    return result;
  }
  return value === null ? undefined : value;
}

function hasAnyEntries(section, keys) {
  return keys.some((key) => state[section][key].length > 0);
}

function makePackMeta() {
  const version = packVersionInfo();
  return cleanObject({
    pack: {
      pack_format: state.meta.packFormat || version.packFormat,
      description: state.meta.description || state.meta.packName || "Villager Retaliation datapack"
    },
    [PACK_VERSION_NAMESPACE]: {
      [PACK_VERSION_STORAGE_KEY]: version.id
    }
  });
}

function safeJson(value) {
  return JSON.stringify(cleanObject(value), null, 2) + "\n";
}

function dialogueKindIndex(segments) {
  return segments.findIndex((segment) => DIALOGUE_KIND_KEYS.includes(segment));
}

function professionFromDialoguePathSegments(segments) {
  const professionIndex = segments.indexOf("professions");
  if (professionIndex < 0) return "";
  const kindIndex = dialogueKindIndex(segments);
  if (kindIndex <= professionIndex) {
    const afterProfession = segments.slice(professionIndex + 1);
    if (afterProfession.length === 0) return "";
    if (CONSTANTS.professions.includes(afterProfession[0]) || afterProfession.length === 1) return afterProfession[0];
    return `${afterProfession[0]}:${afterProfession[1]}`;
  }
  const professionSegments = segments.slice(professionIndex + 1, kindIndex);
  if (professionSegments.length === 0) return "";
  if (professionSegments.length === 1 || CONSTANTS.professions.includes(professionSegments[0])) {
    return professionSegments[0];
  }
  return `${professionSegments[0]}:${professionSegments.slice(1).join("/")}`;
}

function dialoguePathInfo(path) {
  const match = path.match(/^data\/villagerretaliation\/dialogue\/([^/]+)\/(.+)\.json$/);
  if (!match) return null;
  const relative = match[2];
  const segments = relative.split("/");
  const kindIndex = dialogueKindIndex(segments);
  const kind = kindIndex >= 0 ? segments[kindIndex] : "";
  return {
    locale: match[1],
    relative,
    kind,
    folderName: kindIndex > 0 ? segments.slice(0, kindIndex).join("/") : "",
    profession: professionFromDialoguePathSegments(segments)
  };
}

function dialogueEntriesFromJson(json, kind) {
  if (Array.isArray(json?.[kind])) return cleanArray(json[kind]);
  if (Array.isArray(json)) return cleanArray(json);
  if (!json || typeof json !== "object") return [];
  const entry = cleanObject(json);
  return Object.keys(entry).length > 0 ? [entry] : [];
}

function dialogueOutputEntry(path, entry) {
  const result = cleanObject(entry);
  const profession = dialoguePathInfo(path)?.profession;
  if (profession && Array.isArray(result.professions) && result.professions.length === 1 && result.professions[0] === profession) {
    delete result.professions;
  }
  return result;
}

function dialogueFilePayload(path, value) {
  const info = dialoguePathInfo(path);
  const payload = Object.fromEntries(DIALOGUE_KIND_KEYS
    .filter((key) => Array.isArray(value[key]) && value[key].length > 0)
    .map((key) => [key, value[key].map((entry) => dialogueOutputEntry(path, entry))]));
  const kind = info?.kind;
  if (!kind) return payload;
  const nonEmptyKinds = Object.keys(payload);
  if (nonEmptyKinds.length !== 1 || nonEmptyKinds[0] !== kind) return payload;
  return payload[kind].length === 1 ? payload[kind][0] : { [kind]: payload[kind] };
}

function dialogueUsesFolderLayout() {
  return supportsBeta12DialogueFields() && state.dialogue.layout !== "bundle";
}

function dialogueBundlePath() {
  return `data/villagerretaliation/dialogue/${state.meta.locale}/${state.dialogue.fileName}.json`;
}

function dialogueFolderName() {
  return normalizeFileName(state.dialogue.folderName || state.meta.slug, state.meta.slug || "my_pack");
}

function dialogueFileStem(value, fallback) {
  return normalizeFileName(value, fallback)
    .replace(/[:/]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "") || fallback;
}

function defaultDialogueEntryPath(kind = activeDialogueKind, entry = {}, index = 0) {
  if (!dialogueUsesFolderLayout()) return dialogueBundlePath();
  const stemSource = entry.request || entry.key || entry.id || kind;
  const stem = dialogueFileStem(stemSource, kind);
  const order = String(Math.max(0, index)).padStart(2, "0");
  return `data/villagerretaliation/dialogue/${state.meta.locale}/${dialogueFolderName()}/${kind}/${order}_${stem}.json`;
}

function dialoguePath(kind = activeDialogueKind, entry = {}, index = 0) {
  return defaultDialogueEntryPath(kind, entry, index);
}

function forcedDialoguePath() {
  return `data/villagerretaliation/forced_dialogue/${state.forcedDialogue.fileName}.json`;
}

function notificationsPath() {
  return `data/villagerretaliation/notifications/${state.meta.locale}/${state.notifications.fileName}.json`;
}

function giftsPath() {
  return `data/villagerretaliation/gifts/${state.gifts.fileName}.json`;
}

function pacificationPath() {
  return `data/villagerretaliation/pacification/${state.pacification.fileName}.json`;
}

function structurePath() {
  return `data/${state.stories.namespace}/story_structures/${state.stories.structureFileName}.json`;
}

function biomePath() {
  return `data/${state.stories.namespace}/story_biomes/${state.stories.biomeFileName}.json`;
}

function namesPath() {
  return "data/villagerretaliation/villager_names/preset_names.json";
}

function generatedFiles() {
  const files = { ...state.extraFiles };
  files["pack.mcmeta"] = safeJson(makePackMeta());

  if (hasAnyEntries("dialogue", ["options", "lines", "messages", "openings", "closings", "pacify"])) {
    Object.assign(files, generatedDialogueFiles());
  }

  if (state.forcedDialogue.entries.length > 0) {
    Object.assign(files, generatedForcedDialogueFiles());
  }

  if (state.notifications.notifications.length > 0) {
    files[notificationsPath()] = safeJson({ notifications: state.notifications.notifications });
  }

  if (hasAnyEntries("gifts", ["preferences", "rewards"])) {
    files[giftsPath()] = safeJson({
      preferences: state.gifts.preferences,
      rewards: state.gifts.rewards
    });
  }

  if (state.pacification.payments.length > 0) {
    files[pacificationPath()] = safeJson({ payments: state.pacification.payments });
  }

  if (state.stories.structures.length > 0) {
    files[structurePath()] = safeJson({
      radius: state.stories.radius || 96,
      entries: state.stories.structures
    });
  }

  if (state.stories.biomes.length > 0) {
    files[biomePath()] = safeJson({ entries: state.stories.biomes });
  }

  if (state.names.male_names.length > 0 || state.names.female_names.length > 0) {
    files[namesPath()] = safeJson({
      male_names: state.names.male_names,
      female_names: state.names.female_names
    });
  }

  return files;
}

function currentViewFiles() {
  return currentViewSnapshot().files;
}

function currentViewChecks() {
  return currentViewSnapshot().checks;
}

function invalidateCurrentViewSnapshot() {
  currentViewSnapshotCache = null;
}

function currentViewSnapshot() {
  if (!currentViewSnapshotCache) {
    currentViewSnapshotCache = withDraftState(() => ({
      files: generatedFiles(),
      checks: validate()
    }));
  }
  return currentViewSnapshotCache;
}

function withDraftState(callback) {
  const restoreDraft = applyTemporaryDraftEntry();
  try {
    return callback();
  } finally {
    restoreDraft();
  }
}

function applyTemporaryDraftEntry() {
  const noop = () => {};
  if (!entryFormDirty) return noop;
  const draft = readCurrentDraftEntry({ quiet: true });
  if (!draft || !draft.entry) return noop;
  const collection = state[draft.section]?.[draft.kind];
  if (!Array.isArray(collection)) return noop;
  if (editing && editing.section === draft.section && editing.kind === draft.kind) {
    const existing = collection[editing.index];
    if (existing?.__sourcePath && !draft.entry.__sourcePath) draft.entry.__sourcePath = existing.__sourcePath;
    const previous = collection[editing.index];
    collection[editing.index] = draft.entry;
    return () => {
      collection[editing.index] = previous;
    };
  }
  collection.push(draft.entry);
  return () => {
    collection.pop();
  };
}

function generatedDialogueFiles() {
  const grouped = new Map();
  for (const kind of DIALOGUE_KIND_KEYS) {
    for (const [index, entry] of state.dialogue[kind].entries()) {
      const path = entry.__sourcePath || dialoguePath(kind, entry, index);
      if (!grouped.has(path)) {
        grouped.set(path, {
          options: [],
          lines: [],
          messages: [],
          openings: [],
          closings: [],
          pacify: []
        });
      }
      grouped.get(path)[kind].push(entry);
    }
  }
  return Object.fromEntries([...grouped.entries()].map(([path, value]) => [path, safeJson(dialogueFilePayload(path, value))]));
}

function generatedForcedDialogueFiles() {
  const grouped = new Map();
  for (const entry of state.forcedDialogue.entries) {
    const path = entry.__sourcePath || forcedDialoguePath();
    if (!grouped.has(path)) grouped.set(path, { entries: [] });
    grouped.get(path).entries.push(entry);
  }
  return Object.fromEntries([...grouped.entries()].map(([path, value]) => [path, safeJson(value)]));
}

function pathsFromGeneratedFiles(fileMap, fallbackPath) {
  const paths = Object.keys(fileMap);
  return paths.length > 0 ? paths : [fallbackPath];
}

function storyPaths() {
  return [structurePath(), biomePath()];
}

function primaryGeneratedPaths() {
  return [
    dialoguePath(),
    forcedDialoguePath(),
    notificationsPath(),
    giftsPath(),
    pacificationPath(),
    ...storyPaths(),
    namesPath()
  ];
}

function pathsForCheck(check) {
  if (Array.isArray(check.paths) && check.paths.length > 0) return check.paths;
  if (check.title === "Preview JSON") return previewEditError?.path ? [previewEditError.path] : [];
  if (check.title === "Pack format" || check.title === "VR version") return ["pack.mcmeta"];
  if (check.title === "File slug") return primaryGeneratedPaths();
  if (check.title.startsWith("Dialogue") || check.title === "Pacify outcome") {
    return pathsFromGeneratedFiles(generatedDialogueFiles(), dialoguePath());
  }
  if (check.title.startsWith("Forced")) {
    return pathsFromGeneratedFiles(generatedForcedDialogueFiles(), forcedDialoguePath());
  }
  if (check.title.startsWith("Notification")) return [notificationsPath()];
  if (check.title.startsWith("Gift")) return [giftsPath()];
  if (check.title.startsWith("Pacification")) return [pacificationPath()];
  if (check.title === "Story namespace" || check.title === "Story file" || check.title === "Story radius") return storyPaths();
  if (check.title.startsWith("Story structure")) return [structurePath()];
  if (check.title.startsWith("Story biome")) return [biomePath()];
  return [];
}

function errorPathsForChecks(checks) {
  return new Set(checks.filter((check) => check.type === "error").flatMap(pathsForCheck));
}

function warningPathsForChecks(checks) {
  return new Set(checks.filter((check) => check.type === "warning").flatMap(pathsForCheck));
}

function strongestSeverity(current, next) {
  if (current === "error" || next === "error") return "error";
  if (current === "warning" || next === "warning") return "warning";
  if (current === "info" || next === "info") return "info";
  return "";
}

function issueSeverityClass(severity) {
  if (severity === "error") return "has-error";
  if (severity === "warning") return "has-warning";
  if (severity === "info") return "has-info";
  return "";
}

function issueSeverityFromEntries(entries, tests) {
  let severity = "";
  for (const entry of entries) {
    for (const test of tests) {
      if (test.predicate(entry)) severity = strongestSeverity(severity, test.severity);
    }
  }
  return severity;
}

function entryIssueSeverity(section, kind, entry) {
  if (!entry) return "";
  if (section === "dialogue") {
    const tests = [
      { severity: "error", predicate: (item) => kind === "options" && (!item.id || !item.label || !isValidDialogueOptionType(item) || !item.request) },
      { severity: "error", predicate: (item) => kind === "lines" && (!item.request || (!hasDialogueText(item) && !item.text_key)) },
      { severity: "error", predicate: (item) => kind === "messages" && (!item.key || !hasDialogueText(item)) },
      { severity: "error", predicate: (item) => ["openings", "closings", "pacify"].includes(kind) && !hasDialogueText(item) },
      { severity: "warning", predicate: (item) => ["options", "lines"].includes(kind) && item.request && !CONSTANTS.dialogueTypes.includes(item.request) },
      { severity: "info", predicate: (item) => kind === "options" && hasPlannedBeta13DialogueOptionDeprecationField(item) },
      { severity: "info", predicate: (item) => kind === "lines" && hasPlannedBeta13DialogueLineDeprecationField(item) },
      { severity: "warning", predicate: (item) => entryValues(item, ["dispositions"]).some((value) => !CONSTANTS.dispositions.includes(value)) },
      { severity: "warning", predicate: (item) => ["options", "lines"].includes(kind) && hasBeta12DialogueField(item) && !supportsBeta12DialogueFields() },
      { severity: "warning", predicate: (item) => kind === "lines" && entryValues(item, ["mood", "moods"]).some((value) => !CONSTANTS.moods.includes(value)) },
      { severity: "warning", predicate: (item) => entryValues(item, ["professions"]).some((value) => !isValidProfession(value)) },
      { severity: "error", predicate: (item) => ["options", "lines"].includes(kind) && entryValues(item, ["player_item", "player_items", "player_item_tag", "player_item_tags"]).some((value) => !isValidResourceLocation(value, { allowTag: true })) },
      { severity: "error", predicate: (item) => ["options", "lines"].includes(kind) && entryValues(item, ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "warning", predicate: (item) => ["options", "lines"].includes(kind) && entryValues(item, ["player_item_slot", "player_item_slots"]).some((value) => !CONSTANTS.itemSlots.includes(value)) },
      { severity: "warning", predicate: (item) => ["options", "lines"].includes(kind) && entryValues(item, ["reputation_level", "reputation_levels"]).some((value) => !CONSTANTS.reputationLevels.includes(value)) },
      { severity: "warning", predicate: (item) => kind === "lines" && entryValues(item, ["weather"]).some((value) => !CONSTANTS.weather.includes(value)) },
      { severity: "warning", predicate: (item) => kind === "lines" && entryValues(item, ["times"]).some((value) => !CONSTANTS.times.includes(value)) },
      { severity: "warning", predicate: (item) => kind === "lines" && entryValues(item, ["gift_advice"]).some((value) => !CONSTANTS.giftAdvice.includes(value)) },
      { severity: "warning", predicate: (item) => kind === "pacify" && entryValues(item, ["outcomes"]).some((value) => !CONSTANTS.pacifyOutcomes.includes(value)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["order"], Number.isFinite) !== "" },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["weight", "min_recruitment_follow_distance"], (value) => value >= 0) !== "" },
      { severity: "error", predicate: (item) => kind === "lines" && firstBadNumber([item], ["min_mood_intensity"], (value) => value >= 0 && value <= 100) !== "" },
      { severity: "error", predicate: (item) => kind === "lines" && firstBadNumber([item], socialAttributeNumberKeys(), (value) => value >= 1 && value <= 100) !== "" },
      { severity: "error", predicate: (item) => kind === "lines" && Boolean(invalidSocialAttributeRange(item)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level"], (value) => value >= 1) !== "" },
      { severity: "error", predicate: (item) => kind === "options" && dialogueItemPayments([item]).some((payment) => !hasAnySelector(payment, ["items", "item", "tags", "tag"]) || (payment.count === undefined && payment.amount === undefined)) },
      { severity: "error", predicate: (item) => kind === "options" && firstInvalidValue(dialogueItemPayments([item]), ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true })) !== "" },
      { severity: "warning", predicate: (item) => kind === "options" && firstInvalidValue(dialogueItemPayments([item]), ["destination", "overflow_destination"], (value) => CONSTANTS.dialogueItemDestinations.includes(value)) !== "" },
      { severity: "error", predicate: (item) => kind === "options" && firstBadNumber(dialogueItemPayments([item]), ["count", "amount"], (value) => value >= 1) !== "" },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["min_reputation", "max_reputation"], Number.isFinite) !== "" },
      { severity: "error", predicate: (item) => {
        const min = numberValue(item.min_reputation);
        const max = numberValue(item.max_reputation);
        return min !== undefined && max !== undefined && min > max;
      } },
      { severity: "warning", predicate: (item) => firstBlankListValue([item], ["professions", "dispositions", "mood", "moods", "reputation_level", "reputation_levels", "player_item", "player_items", "player_item_tag", "player_item_tags", "player_item_slot", "player_item_slots", "player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments", "weather", "times", "event_tags", "player_event_tags", "retaliation_target_entity_types", "story_structures", "story_biomes", "outcomes"]) !== "" },
      { severity: "error", predicate: (item) => entryValues(item, ["retaliation_target_entity_types", "retaliation_target_entities"]).some((value) => !isValidResourceLocation(value)) }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "forcedDialogue") {
    const options = isForcedDialogueOutput(entry) && Array.isArray(entry.options) ? entry.options : [];
    const actionableOptions = isForcedDialogueOutput(entry) ? [...options, ...forcedLeaveOptions(entry)] : [];
    const payments = actionableOptions.map((option) => option.take_items || option.payment).filter((payment) => payment && typeof payment === "object" && !Array.isArray(payment));
    const stolenReturns = actionableOptions.map((option) => option.take_stolen_items || option.return_stolen_items).filter((stolenReturn) => stolenReturn && typeof stolenReturn === "object" && !Array.isArray(stolenReturn));
    const tests = [
      { severity: "error", predicate: (item) => !forcedTriggerValue(item) || !hasForcedDialogueLine(item) },
      { severity: "error", predicate: (item) => forcedTriggerValue(item) && !CONSTANTS.forcedDialogueTriggers.includes(forcedTriggerValue(item)) },
      { severity: "error", predicate: (item) => item.output?.mode && !CONSTANTS.forcedOutputModes.includes(item.output.mode) },
      { severity: "warning", predicate: (item) => entryValues(item, ["witness_profession", "witness_professions", "professions"]).some((value) => !isValidProfession(value)) },
      { severity: "error", predicate: (item) => entryValues(item, ["loot_table", "loot_tables"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "error", predicate: (item) => entryValues(item, ["target_entity_type", "target_entity_types", "target_entities"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "error", predicate: (item) => entryValues(item, ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["priority", "reputation", "witness_radius", "min_recent_retaliations", "max_recent_retaliations"], (value, itemEntry, key) => {
        if (key === "reputation") return isForcedDialogueOutput(itemEntry) ? Number.isFinite(value) : true;
        if (key === "priority") return Number.isFinite(value);
        if (key === "witness_radius") return value >= 1;
        return Number.isFinite(value) && value >= 0;
      }) !== "" },
      { severity: "error", predicate: (item) => isChatOutputEntry(item) && firstBadNumber([item.output || {}], ["radius"], (value) => value >= 1) !== "" },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level"], (value) => value >= 1) !== "" },
      { severity: "warning", predicate: hasIgnoredForcedDialogueFields },
      { severity: "warning", predicate: (item) => firstBlankListValue([item], ["lines", "loot_tables", "witness_profession", "witness_professions", "professions", "target_entity_types", "target_entities"]) !== "" },
      { severity: "error", predicate: (item) => Number.isFinite(item.min_recent_retaliations) && Number.isFinite(item.max_recent_retaliations) && item.min_recent_retaliations > item.max_recent_retaliations },
      { severity: "error", predicate: () => options.some((option) => !option.id || !option.label) },
      { severity: "error", predicate: () => firstBadNumber(actionableOptions, ["order", "reputation", "aggro_chance"], (value, option, key) => key === "aggro_chance" ? value >= 0 && value <= 1 : Number.isFinite(value)) !== "" },
      { severity: "warning", predicate: () => firstInvalidValue(actionableOptions, ["reputation_level", "reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value)) !== "" },
      { severity: "error", predicate: () => firstBadNumber(actionableOptions, ["min_reputation", "max_reputation"], Number.isFinite) !== "" },
      { severity: "error", predicate: () => actionableOptions.some((option) => {
        const min = numberValue(option.min_reputation);
        const max = numberValue(option.max_reputation);
        return min !== undefined && max !== undefined && min > max;
      }) },
      { severity: "error", predicate: () => payments.some((payment) => !hasAnySelector(payment, ["items", "item", "tags", "tag"]) || (payment.count === undefined && payment.amount === undefined)) },
      { severity: "error", predicate: () => firstInvalidValue(payments, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true })) !== "" },
      { severity: "warning", predicate: () => firstInvalidValue(payments, ["destination", "overflow_destination"], (value) => CONSTANTS.forcedItemDestinations.includes(value)) !== "" },
      { severity: "error", predicate: () => firstBadNumber(payments, ["count", "amount", "success_reputation", "failure_reputation"], (value, payment, key) => key === "count" || key === "amount" ? value >= 1 : Number.isFinite(value)) !== "" },
      { severity: "warning", predicate: () => firstInvalidValue(stolenReturns, ["destination", "overflow_destination"], (value) => CONSTANTS.forcedItemDestinations.includes(value)) !== "" },
      { severity: "error", predicate: () => firstBadNumber(stolenReturns, ["success_reputation", "failure_reputation"], Number.isFinite) !== "" }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "notifications") {
    const tests = [
      { severity: "error", predicate: (item) => !item.trigger || !hasNotificationText(item) },
      { severity: "warning", predicate: (item) => item.trigger && !CONSTANTS.notificationTriggers.includes(item.trigger) },
      { severity: "error", predicate: (item) => item.kind && !CONSTANTS.hudKinds.includes(item.kind) },
      { severity: "error", predicate: (item) => entryValues(item, ["world_text_kind", "style"]).some((value) => !CONSTANTS.worldTextKinds.includes(value)) },
      { severity: "warning", predicate: (item) => entryValues(item, ["color", "text_color", "chat_color"]).some((value) => !isValidColor(value)) },
      { severity: "warning", predicate: (item) => entryValues(item, ["professions"]).some((value) => !isValidProfession(value)) },
      { severity: "warning", predicate: (item) => entryValues(item, ["reputation_levels"]).some((value) => !CONSTANTS.reputationLevels.includes(value)) },
      { severity: "error", predicate: (item) => entryValues(item, ["target_entity_type", "target_entity", "target_entity_types", "target_entities"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "error", predicate: (item) => entryValues(item, ["player_item", "player_items", "player_item_tag", "player_item_tags"]).some((value) => !isValidResourceLocation(value, { allowTag: true })) },
      { severity: "error", predicate: (item) => entryValues(item, ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"]).some((value) => !isValidResourceLocation(value)) },
      { severity: "warning", predicate: (item) => entryValues(item, ["player_item_slot", "player_item_slots"]).some((value) => !CONSTANTS.itemSlots.includes(value)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["min_reputation", "max_reputation", "weight"], (value, notification, key) => key === "weight" ? value >= 0 : Number.isFinite(value)) !== "" },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level"], (value) => value >= 1) !== "" },
      { severity: "error", predicate: (item) => {
        const min = numberValue(item.min_reputation);
        const max = numberValue(item.max_reputation);
        const chance = numberValue(item.chance);
        return (min !== undefined && max !== undefined && min > max) || (chance !== undefined && (chance < 0 || chance > 1));
      } }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "gifts") {
    const tests = [
      { severity: "error", predicate: (item) => kind === "preferences" && (!item.reaction || !hasAnySelector(item, ["items", "tags", "item", "tag"])) },
      { severity: "error", predicate: (item) => kind === "rewards" && !item.item },
      { severity: "error", predicate: (item) => kind === "preferences" && item.reaction && !CONSTANTS.reactions.includes(item.reaction) },
      { severity: "error", predicate: (item) => kind === "preferences" && entryValues(item, ["items", "item", "tags", "tag"]).some((value) => !isValidResourceLocation(value, { allowTag: true })) },
      { severity: "error", predicate: (item) => kind === "rewards" && item.item && !isValidResourceLocation(item.item) },
      { severity: "warning", predicate: (item) => entryValues(item, ["professions"]).some((value) => !isValidProfession(value)) },
      { severity: "warning", predicate: (item) => kind === "rewards" && entryValues(item, ["reputation_levels"]).some((value) => !CONSTANTS.reputationLevels.includes(value)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["priority", "reputation_per_item", "min_count", "max_count", "weight"], (value, gift, key) => {
        if (key === "min_count" || key === "max_count") return value >= 1 && value <= 64;
        if (key === "weight") return value > 0;
        return Number.isFinite(value);
      }) !== "" },
      { severity: "error", predicate: (item) => {
        const min = numberValue(item.min_count);
        const max = numberValue(item.max_count);
        return min !== undefined && max !== undefined && min > max;
      } }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "pacification") {
    const tests = [
      { severity: "error", predicate: (item) => !hasAnySelector(item, ["items", "tags", "item", "tag"]) },
      { severity: "error", predicate: (item) => entryValues(item, ["items", "item", "tags", "tag"]).some((value) => !isValidResourceLocation(value, { allowTag: true })) },
      { severity: "warning", predicate: (item) => entryValues(item, ["professions"]).some((value) => !isValidProfession(value)) },
      { severity: "error", predicate: (item) => firstBadNumber([item], ["count", "min_count", "max_count"], (value) => value >= 1 && value <= 64) !== "" },
      { severity: "error", predicate: (item) => {
        const min = numberValue(item.min_count);
        const max = numberValue(item.max_count);
        return min !== undefined && max !== undefined && min > max;
      } }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "stories") {
    const tests = [
      { severity: "error", predicate: (item) => kind === "structures" && !hasAnySelector(item, ["structure", "structures"]) },
      { severity: "error", predicate: (item) => kind === "biomes" && !hasAnySelector(item, ["biome", "biomes"]) },
      { severity: "warning", predicate: (item) => kind === "structures" && entryValues(item, ["structure", "structures"]).some((value) => !isValidResourceLocation(value, { requireNamespace: true })) },
      { severity: "warning", predicate: (item) => kind === "biomes" && entryValues(item, ["biome", "biomes"]).some((value) => !isValidResourceLocation(value, { requireNamespace: true })) },
      { severity: "error", predicate: (item) => kind === "structures" && firstBadNumber([item], ["radius"], (value) => value >= 1) !== "" }
    ];
    return issueSeverityFromEntries([entry], tests);
  }
  if (section === "names") {
    return String(entry).trim() === "" ? "warning" : "";
  }
  return "";
}

function entryIssueMessage(section, kind, entry) {
  const detail = entryIssueDetail(section, kind, entry);
  return detail ? detail.message : "";
}

function valueLabel(value) {
  if (Array.isArray(value)) {
    const values = value.map((item) => String(item)).filter((item) => item !== "");
    return values.length ? values.join(", ") : "blank";
  }
  if (value === undefined || value === null || value === "") return "blank";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function issueDetail(field, expected, received, fieldIds, severity = "error") {
  return {
    field,
    expected,
    received: valueLabel(received),
    fieldIds: Array.isArray(fieldIds) ? fieldIds : [fieldIds].filter(Boolean),
    message: `${field}: expected ${expected}; received ${valueLabel(received)}.`,
    severity
  };
}

function infoIssueDetail(message, fieldIds = []) {
  return {
    field: "Info",
    expected: "",
    received: "",
    fieldIds: Array.isArray(fieldIds) ? fieldIds : [fieldIds].filter(Boolean),
    message,
    severity: "info"
  };
}

function firstInvalidListValue(entry, keys, predicate) {
  for (const key of keys) {
    for (const value of entryValues(entry, [key])) {
      if (!predicate(value)) return { key, value };
    }
  }
  return null;
}

function firstBadNumberDetail(entries, specs) {
  for (const entry of entries) {
    for (const spec of specs) {
      const value = entry[spec.key];
      if (value === undefined || value === null || value === "") continue;
      const number = numberValue(value);
      if (number === undefined || !spec.valid(number, entry)) {
        return { ...spec, value };
      }
    }
  }
  return null;
}

function dialogueTextValue(entry) {
  if (Array.isArray(entry?.lines) && entry.lines.length > 0) return entry.lines.join("\n");
  return entry?.text ?? "";
}

function duplicateTextVariant(entry) {
  if (!Array.isArray(entry?.lines)) return "";
  return firstDuplicate(entry.lines);
}

function dialogueItemPaymentText(entry) {
  const payment = entry?.give_items ?? entry?.take_items ?? entry?.payment;
  if (!payment || typeof payment !== "object" || Array.isArray(payment)) return "";
  if (entry?.give_items || payment.destination) return prettyJson(payment);
  return prettyJson({ ...payment, destination: "discard" });
}

function hasDialogueText(entry) {
  return parseList(dialogueTextValue(entry)).length > 0;
}

function notificationTextValue(entry) {
  if (Array.isArray(entry?.lines) && entry.lines.length > 0) return entry.lines.join("\n");
  return entry?.text ?? "";
}

function hasNotificationText(entry) {
  return parseList(notificationTextValue(entry)).length > 0;
}

function forcedTriggerValue(entry) {
  return entry?.trigger ?? entry?.event ?? "";
}

function socialAttributeRangeKeys(prefix) {
  return CONSTANTS.socialAttributes.flatMap((attribute) => [`${prefix}_${attribute}`]);
}

function socialAttributeNumberKeys() {
  return [
    ...socialAttributeRangeKeys("min"),
    ...socialAttributeRangeKeys("max")
  ];
}

function hasBeta12DialogueField(entry) {
  return BETA_12_ONLY_DIALOGUE_KEYS.some((key) => entry?.[key] !== undefined);
}

function hasPlannedBeta13DialogueLineDeprecationField(entry) {
  return BETA_13_PLANNED_DIALOGUE_LINE_DEPRECATION_KEYS.some((key) => entry?.[key] !== undefined);
}

function hasPlannedBeta13DialogueOptionDeprecationField(entry) {
  return BETA_13_PLANNED_DIALOGUE_OPTION_DEPRECATION_KEYS.some((key) => entry?.[key] !== undefined);
}

function plannedBeta13DialogueDeprecationFields(kind, entry) {
  return plannedBeta13DialogueDeprecationKeysForKind(kind).filter((key) => entry?.[key] !== undefined);
}

function dialogueDeprecationReplacementText(fields) {
  const hasFamily = fields.some((field) => field.startsWith("requires_known_") && !field.includes("relationship") && !field.includes("crush") && !field.includes("dating") && !field.includes("fiance") && !field.includes("romantic") && !field.includes("separated") && !field.includes("widowed"));
  const hasRelationship = fields.some((field) => field.includes("relationship") || field.includes("crush") || field.includes("dating") || field.includes("fiance") || field.includes("romantic") || field.includes("separated") || field.includes("widowed"));
  const hasMemory = fields.some((field) => field.includes("memory") || field.includes("gear_report") || field.includes("container_theft") || field.includes("retaliation") || field.includes("recruitment"));
  const replacements = [];
  if (hasFamily) replacements.push("family condition blocks");
  if (hasRelationship) replacements.push("relationship condition blocks");
  if (hasMemory) replacements.push("memory or recruitment condition blocks");
  return replacements.length ? replacements.join(", ") : BETA_13_PLANNED_DIALOGUE_DEPRECATION_REPLACEMENT;
}

function dialogueDeprecationMessage(kind, entry) {
  const fields = plannedBeta13DialogueDeprecationFields(kind, entry);
  if (fields.length === 0) return "";
  const fieldText = fields.length === 1 ? fields[0] : `${fields.slice(0, 3).join(", ")}${fields.length > 3 ? `, and ${fields.length - 3} more` : ""}`;
  return `Marked for beta.13 deprecation: ${fieldText}. Replace with ${dialogueDeprecationReplacementText(fields)} inside conditions.`;
}

function invalidSocialAttributeRange(entry) {
  for (const attribute of CONSTANTS.socialAttributes) {
    const min = numberValue(entry?.[`min_${attribute}`]);
    const max = numberValue(entry?.[`max_${attribute}`]);
    if (min !== undefined && max !== undefined && min > max) return attribute;
  }
  return "";
}

function entryIssueDetail(section, kind, entry) {
  if (!entry) return null;
  if (section === "dialogue") {
    if (kind === "options") {
      if (!entry.id) return issueDetail("Option id", "a non-empty stable id", entry.id, "dialogue-id");
      if (!entry.label) return issueDetail("Button label", "non-empty button text", entry.label, "dialogue-label");
      if (!isValidDialogueOptionType(entry)) return issueDetail("Entry type", "dialogue_option or omitted in beta.12 typed options folders", entry.type, "dialogue-type");
      if (!entry.request) return issueDetail("Request", `one of ${CONSTANTS.dialogueTypes.join(", ")}`, entry.request, "dialogue-type");
    }
    if (kind === "lines") {
      if (!entry.request) return issueDetail("Request", `one of ${CONSTANTS.dialogueTypes.join(", ")}`, entry.request, "dialogue-type");
      if (!hasDialogueText(entry) && !entry.text_key) return issueDetail("Line(s) or text key", "non-empty villager text or a message key", dialogueTextValue(entry) || entry.text_key, ["dialogue-text", "dialogue-text_key"]);
    }
    if (kind === "messages") {
      if (!entry.key) return issueDetail("Message key", "a non-empty lookup key", entry.key, "dialogue-key");
      if (!hasDialogueText(entry)) return issueDetail("Message text variation(s)", "non-empty message text", dialogueTextValue(entry), "dialogue-text");
    }
    if (["openings", "closings", "pacify"].includes(kind) && !hasDialogueText(entry)) {
      return issueDetail("Text variation(s)", "non-empty dialogue text", dialogueTextValue(entry), "dialogue-text");
    }
    const duplicateVariant = duplicateTextVariant(entry);
    if (duplicateVariant) {
      return issueDetail("Text variations", "unique line variants", duplicateVariant, "dialogue-text", "warning");
    }
    if (["options", "lines"].includes(kind) && entry.request && !CONSTANTS.dialogueTypes.includes(entry.request)) {
      return issueDetail("Request", `one of ${CONSTANTS.dialogueTypes.join(", ")}`, entry.request, "dialogue-type", "warning");
    }
    if (kind === "lines" && hasBeta12DialogueField(entry) && !supportsBeta12DialogueFields()) {
      return issueDetail("Beta.12 dialogue filters", "VR 1.0.0-beta.12 or newer", state.meta.packVersion, "meta-packVersion", "warning");
    }
    if (kind === "options" && hasBeta12DialogueField(entry) && !supportsBeta12DialogueFields()) {
      return issueDetail("Beta.12 option conditions", "VR 1.0.0-beta.12 or newer", state.meta.packVersion, "meta-packVersion", "warning");
    }
    if (kind === "options" && hasPlannedBeta13DialogueOptionDeprecationField(entry)) {
      return infoIssueDetail(dialogueDeprecationMessage(kind, entry), "dialogue-conditions");
    }
    if (kind === "lines" && hasPlannedBeta13DialogueLineDeprecationField(entry)) {
      return infoIssueDetail(dialogueDeprecationMessage(kind, entry), "dialogue-conditions");
    }
    const dialogueListChecks = [
      { keys: ["dispositions"], label: "Dispositions", expected: CONSTANTS.dispositions.join(", "), fieldId: "dialogue-dispositions", valid: (value) => CONSTANTS.dispositions.includes(value), severity: "warning" },
      { keys: ["mood", "moods"], label: "Temporary moods", expected: CONSTANTS.moods.join(", "), fieldId: "dialogue-moods", valid: (value) => CONSTANTS.moods.includes(value), severity: "warning" },
      { keys: ["professions"], label: "Professions", expected: "a valid profession id such as farmer or minecraft:farmer", fieldId: "dialogue-professions", valid: isValidProfession, severity: "warning" },
      { keys: ["player_item", "player_items", "player_item_tag", "player_item_tags"], label: "Required player items or tags", expected: "a valid item id or #tag id", fieldId: "dialogue-player_items", valid: (value) => isValidResourceLocation(value, { allowTag: true }) },
      { keys: ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"], label: "Item enchantments", expected: "a valid enchantment id such as minecraft:sharpness", fieldId: "dialogue-player_item_enchantments", valid: isValidResourceLocation },
      { keys: ["player_item_slot", "player_item_slots"], label: "Item slots", expected: CONSTANTS.itemSlots.join(", "), fieldId: "dialogue-player_item_slots", valid: (value) => CONSTANTS.itemSlots.includes(value), severity: "warning" },
      { keys: ["reputation_level", "reputation_levels"], label: "Reputation levels", expected: CONSTANTS.reputationLevels.join(", "), fieldId: "dialogue-reputation_levels", valid: (value) => CONSTANTS.reputationLevels.includes(value), severity: "warning" },
      { keys: ["weather"], label: "Weather", expected: CONSTANTS.weather.join(", "), fieldId: "dialogue-weather", valid: (value) => CONSTANTS.weather.includes(value), severity: "warning" },
      { keys: ["times"], label: "Times", expected: CONSTANTS.times.join(", "), fieldId: "dialogue-times", valid: (value) => CONSTANTS.times.includes(value), severity: "warning" },
      { keys: ["gift_advice"], label: "Gift advice filter", expected: CONSTANTS.giftAdvice.join(", "), fieldId: "dialogue-gift_advice", valid: (value) => CONSTANTS.giftAdvice.includes(value), severity: "warning" },
      { keys: ["outcomes"], label: "Outcomes", expected: CONSTANTS.pacifyOutcomes.join(", "), fieldId: "dialogue-outcomes", valid: (value) => CONSTANTS.pacifyOutcomes.includes(value), severity: "warning" },
      { keys: ["retaliation_target_entity_types", "retaliation_target_entities"], label: "Retaliation target entity types", expected: "a valid entity id such as minecraft:player", fieldId: "dialogue-retaliation_target_entity_types", valid: isValidResourceLocation }
    ];
    for (const check of dialogueListChecks) {
      const bad = firstInvalidListValue(entry, check.keys, check.valid);
      if (bad) return issueDetail(check.label, check.expected, bad.value, check.fieldId, check.severity || "error");
    }
    const badNumber = firstBadNumberDetail([entry], [
      { key: "order", label: "Order", expected: "a valid order number, positive or negative", fieldId: "dialogue-order", valid: Number.isFinite },
      { key: "priority", label: "Priority", expected: "a valid priority number, positive or negative", fieldId: "dialogue-priority", valid: Number.isFinite },
      { key: "weight", label: "Weight", expected: "a number greater than or equal to 0", fieldId: "dialogue-weight", valid: (value) => value >= 0 },
      { key: "min_recruitment_follow_distance", label: "Minimum follow distance", expected: "a number greater than or equal to 0", fieldId: "dialogue-min_recruitment_follow_distance", valid: (value) => value >= 0 },
      { key: "min_mood_intensity", label: "Minimum mood intensity", expected: "a number from 0 to 100", fieldId: "dialogue-min_mood_intensity", valid: (value) => value >= 0 && value <= 100 },
      { key: "min_player_item_enchantment_level", label: "Minimum enchantment level", expected: "a number greater than or equal to 1", fieldId: "dialogue-min_player_item_enchantment_level", valid: (value) => value >= 1 },
      { key: "max_player_item_enchantment_level", label: "Maximum enchantment level", expected: "a number greater than or equal to 1", fieldId: "dialogue-max_player_item_enchantment_level", valid: (value) => value >= 1 },
      { key: "min_reputation", label: "Minimum reputation", expected: "a valid number", fieldId: "dialogue-min_reputation", valid: Number.isFinite },
      { key: "max_reputation", label: "Maximum reputation", expected: "a valid number", fieldId: "dialogue-max_reputation", valid: Number.isFinite },
      ...CONSTANTS.socialAttributes.flatMap((attribute) => [
        { key: `min_${attribute}`, label: `Minimum ${humanize(attribute)}`, expected: "a number from 1 to 100", fieldId: `dialogue-min_${attribute}`, valid: (value) => value >= 1 && value <= 100 },
        { key: `max_${attribute}`, label: `Maximum ${humanize(attribute)}`, expected: "a number from 1 to 100", fieldId: `dialogue-max_${attribute}`, valid: (value) => value >= 1 && value <= 100 }
      ])
    ]);
    if (badNumber) return issueDetail(badNumber.label, badNumber.expected, badNumber.value, badNumber.fieldId);
    const min = numberValue(entry.min_reputation);
    const max = numberValue(entry.max_reputation);
    if (min !== undefined && max !== undefined && min > max) {
      return issueDetail("Reputation range", "minimum reputation less than or equal to maximum reputation", `${min} > ${max}`, ["dialogue-min_reputation", "dialogue-max_reputation"]);
    }
    const badAttributeRange = invalidSocialAttributeRange(entry);
    if (badAttributeRange) {
      return issueDetail("Social attribute range", `minimum ${badAttributeRange} less than or equal to maximum ${badAttributeRange}`, `${entry[`min_${badAttributeRange}`]} > ${entry[`max_${badAttributeRange}`]}`, [`dialogue-min_${badAttributeRange}`, `dialogue-max_${badAttributeRange}`]);
    }
    const payments = kind === "options" ? dialogueItemPayments([entry]) : [];
    if (payments.some((payment) => !hasAnySelector(payment, ["items", "item", "tags", "tag"]))) {
      return issueDetail("Give items JSON", "at least one item/items or tag/tags selector", "selector missing", "dialogue-give_items");
    }
    if (payments.some((payment) => payment.count === undefined && payment.amount === undefined)) {
      return issueDetail("Give items JSON", "a count or amount value", "count missing", "dialogue-give_items");
    }
    const badPaymentSelector = firstInvalidValue(payments, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
    if (badPaymentSelector) {
      return issueDetail("Give items JSON", "a valid item id or #tag id", badPaymentSelector, "dialogue-give_items");
    }
    const badPaymentDestination = firstInvalidValue(payments, ["destination", "overflow_destination"], (value) => CONSTANTS.dialogueItemDestinations.includes(value));
    if (badPaymentDestination) {
      return issueDetail("Give items JSON", CONSTANTS.dialogueItemDestinations.join(", "), badPaymentDestination, "dialogue-give_items", "warning");
    }
    const badPaymentNumber = firstBadNumberDetail(payments, [
      { key: "count", label: "Give items count", expected: "a number greater than or equal to 1", fieldId: "dialogue-give_items", valid: (value) => value >= 1 },
      { key: "amount", label: "Give items amount", expected: "a number greater than or equal to 1", fieldId: "dialogue-give_items", valid: (value) => value >= 1 }
    ]);
    if (badPaymentNumber) return issueDetail(badPaymentNumber.label, badPaymentNumber.expected, badPaymentNumber.value, badPaymentNumber.fieldId);
  }
  if (section === "forcedDialogue") {
    const trigger = forcedTriggerValue(entry);
    if (!trigger) return issueDetail("Trigger", `one of ${CONSTANTS.forcedDialogueTriggers.join(", ")}`, trigger, "forced-trigger");
    if (!hasForcedDialogueLine(entry)) return issueDetail("Opening line(s)", "at least one non-empty line", forcedDialogueLineValue(entry), "forced-line");
    if (!CONSTANTS.forcedDialogueTriggers.includes(trigger)) return issueDetail("Trigger", `one of ${CONSTANTS.forcedDialogueTriggers.join(", ")}`, trigger, "forced-trigger");
    if (trigger === "player_item_proximity" && !hasPlayerItemFilter(entry)) {
      return issueDetail("Player item filter", "at least one item, durability, or enchantment filter for player_item_proximity", "none set", ["forced-player_items", "forced-player_item_enchantments"]);
    }
    if (entry.output?.mode && !CONSTANTS.forcedOutputModes.includes(entry.output.mode)) return issueDetail("Output mode", `one of ${CONSTANTS.forcedOutputModes.join(", ")}`, entry.output.mode, "forced-output_mode");
    const forcedListChecks = [
      { keys: ["witness_profession", "witness_professions", "professions"], label: "Witness professions", expected: "a valid profession id such as armorer or minecraft:weaponsmith", fieldId: "forced-witness_professions", valid: isValidProfession, severity: "warning" },
      { keys: ["player_item", "player_items", "player_item_tag", "player_item_tags"], label: "Player items or tags", expected: "a valid item id or #tag id", fieldId: "forced-player_items", valid: (value) => isValidResourceLocation(value, { allowTag: true }) },
      { keys: ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"], label: "Item enchantments", expected: "a valid enchantment id such as minecraft:sharpness", fieldId: "forced-player_item_enchantments", valid: isValidResourceLocation },
      { keys: ["player_item_slot", "player_item_slots"], label: "Player item slots", expected: CONSTANTS.itemSlots.join(", "), fieldId: "forced-player_item_slots", valid: (value) => CONSTANTS.itemSlots.includes(value), severity: "warning" },
      { keys: ["loot_table", "loot_tables"], label: "Loot tables", expected: "a valid loot table id such as minecraft:chests/village/village_armorer", fieldId: "forced-loot_tables", valid: isValidResourceLocation },
      { keys: ["target_entity_type", "target_entity_types", "target_entities"], label: "Target entity types", expected: "a valid entity id such as minecraft:player", fieldId: "forced-target_entity_types", valid: isValidResourceLocation }
    ];
    for (const check of forcedListChecks) {
      const bad = firstInvalidListValue(entry, check.keys, check.valid);
      if (bad) return issueDetail(check.label, check.expected, bad.value, check.fieldId, check.severity || "error");
    }
    const forcedNumberSpecs = [
      { key: "priority", label: "Priority", expected: "a valid priority number, positive or negative", fieldId: "forced-priority", valid: Number.isFinite },
      { key: "witness_radius", label: "Witness radius", expected: "a number greater than or equal to 1", fieldId: "forced-witness_radius", valid: (value) => value >= 1 },
      { key: "min_recent_retaliations", label: "Min prior retaliations", expected: "a number greater than or equal to 0", fieldId: "forced-min_recent_retaliations", valid: (value) => Number.isFinite(value) && value >= 0 },
      { key: "max_recent_retaliations", label: "Max prior retaliations", expected: "a number greater than or equal to 0", fieldId: "forced-max_recent_retaliations", valid: (value) => Number.isFinite(value) && value >= 0 },
      { key: "min_player_item_enchantment_level", label: "Minimum enchantment level", expected: "a number greater than or equal to 1", fieldId: "forced-min_player_item_enchantment_level", valid: (value) => value >= 1 },
      { key: "max_player_item_enchantment_level", label: "Maximum enchantment level", expected: "a number greater than or equal to 1", fieldId: "forced-max_player_item_enchantment_level", valid: (value) => value >= 1 }
    ];
    if (isForcedDialogueOutput(entry)) {
      forcedNumberSpecs.splice(1, 0, { key: "reputation", label: "Reputation change", expected: "a valid number, positive or negative", fieldId: "forced-reputation", valid: Number.isFinite });
    }
    const badNumber = firstBadNumberDetail([entry], forcedNumberSpecs);
    if (badNumber) return issueDetail(badNumber.label, badNumber.expected, badNumber.value, badNumber.fieldId);
    const badOutputNumber = isChatOutputEntry(entry) ? firstBadNumberDetail([entry.output || {}], [
      { key: "radius", label: "Output radius", expected: "a number greater than or equal to 1", fieldId: "forced-output_radius", valid: (value) => value >= 1 }
    ]) : null;
    if (badOutputNumber) return issueDetail(badOutputNumber.label, badOutputNumber.expected, badOutputNumber.value, badOutputNumber.fieldId);
    if (Number.isFinite(entry.min_recent_retaliations) && Number.isFinite(entry.max_recent_retaliations) && entry.min_recent_retaliations > entry.max_recent_retaliations) {
      return issueDetail("Prior retaliation range", "minimum less than or equal to maximum", `${entry.min_recent_retaliations} > ${entry.max_recent_retaliations}`, ["forced-min_recent_retaliations", "forced-max_recent_retaliations"]);
    }
    if (hasIgnoredForcedDialogueFields(entry)) {
      return issueDetail("Output mode", "chat entries use trigger, filters, line, chance, line-of-sight, and output radius", "forced-dialogue-only fields are present but ignored", "forced-output_mode", "warning");
    }
    if (!isForcedDialogueOutput(entry)) return null;
    const options = Array.isArray(entry.options) ? entry.options : [];
    const actionableOptions = [...options, ...forcedLeaveOptions(entry)];
    if (options.some((option) => !option.id || !option.label)) return issueDetail("Options JSON", "every option has id and label", "an option is missing one", "forced-options_json");
    const badOptionNumber = firstBadNumberDetail(actionableOptions, [
      { key: "order", label: "Options JSON order", expected: "a valid number", fieldId: "forced-options_json", valid: Number.isFinite },
      { key: "reputation", label: "Options JSON reputation", expected: "a valid number", fieldId: "forced-options_json", valid: Number.isFinite },
      { key: "aggro_chance", label: "Options JSON aggro_chance", expected: "a number from 0 to 1", fieldId: "forced-options_json", valid: (value) => value >= 0 && value <= 1 }
    ]);
    if (badOptionNumber) return issueDetail(badOptionNumber.label, badOptionNumber.expected, badOptionNumber.value, badOptionNumber.fieldId);
    const payments = actionableOptions.map((option) => option.take_items || option.payment).filter((payment) => payment && typeof payment === "object" && !Array.isArray(payment));
    const badPaymentNumber = firstBadNumberDetail(payments, [
      { key: "count", label: "Options JSON payment count", expected: "a number greater than or equal to 1", fieldId: "forced-options_json", valid: (value) => value >= 1 },
      { key: "amount", label: "Options JSON payment amount", expected: "a number greater than or equal to 1", fieldId: "forced-options_json", valid: (value) => value >= 1 },
      { key: "success_reputation", label: "Options JSON payment success_reputation", expected: "a valid number", fieldId: "forced-options_json", valid: Number.isFinite },
      { key: "failure_reputation", label: "Options JSON payment failure_reputation", expected: "a valid number", fieldId: "forced-options_json", valid: Number.isFinite }
    ]);
    if (badPaymentNumber) return issueDetail(badPaymentNumber.label, badPaymentNumber.expected, badPaymentNumber.value, badPaymentNumber.fieldId);
  }
  if (section === "notifications") {
    if (!entry.trigger) return issueDetail("Trigger", "a non-empty notification trigger", entry.trigger, "notification-trigger");
    if (!hasNotificationText(entry)) return issueDetail("Text variation(s)", "non-empty notification text", notificationTextValue(entry), "notification-text");
    if (!CONSTANTS.notificationTriggers.includes(entry.trigger)) return issueDetail("Trigger", "a built-in trigger or custom trigger emitted by code", entry.trigger, "notification-trigger", "warning");
    const checks = [
      { keys: ["kind"], label: "HUD kind", expected: CONSTANTS.hudKinds.join(", "), fieldId: "notification-kind", valid: (value) => CONSTANTS.hudKinds.includes(value) },
      { keys: ["world_text_kind", "style"], label: "World text kind", expected: CONSTANTS.worldTextKinds.join(", "), fieldId: "notification-world_text_kind", valid: (value) => CONSTANTS.worldTextKinds.includes(value) },
      { keys: ["color"], label: "Color", expected: "a Minecraft color name, #RRGGBB, or #AARRGGBB", fieldId: "notification-color", valid: isValidColor, severity: "warning" },
      { keys: ["text_color"], label: "Text color", expected: "a Minecraft color name, #RRGGBB, or #AARRGGBB", fieldId: "notification-text_color", valid: isValidColor, severity: "warning" },
      { keys: ["chat_color"], label: "Chat color", expected: "a Minecraft color name, #RRGGBB, or #AARRGGBB", fieldId: "notification-chat_color", valid: isValidColor, severity: "warning" },
      { keys: ["professions"], label: "Professions", expected: "a valid profession id such as farmer or minecraft:farmer", fieldId: "notification-professions", valid: isValidProfession, severity: "warning" },
      { keys: ["reputation_levels"], label: "Reputation levels", expected: CONSTANTS.reputationLevels.join(", "), fieldId: "notification-reputation_levels", valid: (value) => CONSTANTS.reputationLevels.includes(value), severity: "warning" },
      { keys: ["target_entity_type", "target_entity", "target_entity_types", "target_entities"], label: "Target entity types", expected: "a valid entity id such as minecraft:player", fieldId: "notification-target_entity_types", valid: isValidResourceLocation },
      { keys: ["player_item", "player_items", "player_item_tag", "player_item_tags"], label: "Required player items or tags", expected: "a valid item id or #tag id", fieldId: "notification-player_items", valid: (value) => isValidResourceLocation(value, { allowTag: true }) },
      { keys: ["player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments"], label: "Item enchantments", expected: "a valid enchantment id such as minecraft:sharpness", fieldId: "notification-player_item_enchantments", valid: isValidResourceLocation },
      { keys: ["player_item_slot", "player_item_slots"], label: "Item slots", expected: CONSTANTS.itemSlots.join(", "), fieldId: "notification-player_item_slots", valid: (value) => CONSTANTS.itemSlots.includes(value), severity: "warning" }
    ];
    for (const check of checks) {
      const bad = firstInvalidListValue(entry, check.keys, check.valid);
      if (bad) return issueDetail(check.label, check.expected, bad.value, check.fieldId, check.severity || "error");
    }
    const badNumber = firstBadNumberDetail([entry], [
      { key: "min_reputation", label: "Minimum reputation", expected: "a valid number", fieldId: "notification-min_reputation", valid: Number.isFinite },
      { key: "max_reputation", label: "Maximum reputation", expected: "a valid number", fieldId: "notification-max_reputation", valid: Number.isFinite },
      { key: "weight", label: "Weight", expected: "a number greater than or equal to 0", fieldId: "notification-weight", valid: (value) => value >= 0 },
      { key: "chance", label: "Chance", expected: "a number from 0 to 1", fieldId: "notification-chance", valid: (value) => value >= 0 && value <= 1 },
      { key: "min_player_item_enchantment_level", label: "Minimum enchantment level", expected: "a number greater than or equal to 1", fieldId: "notification-min_player_item_enchantment_level", valid: (value) => value >= 1 },
      { key: "max_player_item_enchantment_level", label: "Maximum enchantment level", expected: "a number greater than or equal to 1", fieldId: "notification-max_player_item_enchantment_level", valid: (value) => value >= 1 }
    ]);
    if (badNumber) return issueDetail(badNumber.label, badNumber.expected, badNumber.value, badNumber.fieldId);
    const min = numberValue(entry.min_reputation);
    const max = numberValue(entry.max_reputation);
    if (min !== undefined && max !== undefined && min > max) {
      return issueDetail("Reputation range", "minimum reputation less than or equal to maximum reputation", `${min} > ${max}`, ["notification-min_reputation", "notification-max_reputation"]);
    }
  }
  if (section === "gifts") {
    if (kind === "preferences") {
      if (!entry.reaction) return issueDetail("Reaction", CONSTANTS.reactions.join(", "), entry.reaction, "gift-reaction");
      if (!CONSTANTS.reactions.includes(entry.reaction)) return issueDetail("Reaction", CONSTANTS.reactions.join(", "), entry.reaction, "gift-reaction");
      if (!hasAnySelector(entry, ["items", "tags", "item", "tag"])) return issueDetail("Items or tags", "at least one valid item or tag selector", "blank", ["gift-items", "gift-tags"]);
      const badGiftItem = firstInvalidListValue(entry, ["items", "item"], (value) => isValidResourceLocation(value, { allowTag: true }));
      if (badGiftItem) return issueDetail("Items", "a valid item id or #tag id", badGiftItem.value, "gift-items");
      const badGiftTag = firstInvalidListValue(entry, ["tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
      if (badGiftTag) return issueDetail("Tags", "a valid tag id such as minecraft:villager_plantable_seeds", badGiftTag.value, "gift-tags");
    }
    if (kind === "rewards") {
      if (!entry.item) return issueDetail("Reward item", "a valid item id", entry.item, "gift-item");
      if (!isValidResourceLocation(entry.item)) return issueDetail("Reward item", "a valid item id such as minecraft:emerald", entry.item, "gift-item");
      const badRewardReputation = firstInvalidListValue(entry, ["reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value));
      if (badRewardReputation) return issueDetail("Reputation levels", CONSTANTS.reputationLevels.join(", "), badRewardReputation.value, "gift-reputation_levels", "warning");
    }
    const badGiftProfession = firstInvalidListValue(entry, ["professions"], isValidProfession);
    if (badGiftProfession) return issueDetail("Professions", "a valid profession id such as farmer or minecraft:farmer", badGiftProfession.value, "gift-professions", "warning");
    const badNumber = firstBadNumberDetail([entry], [
      { key: "priority", label: "Priority", expected: "a valid number", fieldId: "gift-priority", valid: Number.isFinite },
      { key: "reputation_per_item", label: "Reputation per item", expected: "a valid number", fieldId: "gift-reputation_per_item", valid: Number.isFinite },
      { key: "min_count", label: "Minimum count", expected: "a number from 1 to 64", fieldId: "gift-min_count", valid: (value) => value >= 1 && value <= 64 },
      { key: "max_count", label: "Maximum count", expected: "a number from 1 to 64", fieldId: "gift-max_count", valid: (value) => value >= 1 && value <= 64 },
      { key: "weight", label: "Weight", expected: "a number greater than 0", fieldId: "gift-weight", valid: (value) => value > 0 }
    ]);
    if (badNumber) return issueDetail(badNumber.label, badNumber.expected, badNumber.value, badNumber.fieldId);
    const min = numberValue(entry.min_count);
    const max = numberValue(entry.max_count);
    if (min !== undefined && max !== undefined && min > max) {
      return issueDetail("Reward count range", "minimum count less than or equal to maximum count", `${min} > ${max}`, ["gift-min_count", "gift-max_count"]);
    }
  }
  if (section === "pacification") {
    if (!hasAnySelector(entry, ["items", "tags", "item", "tag"])) return issueDetail("Items or tags", "at least one valid item or tag selector", "blank", ["pacification-items", "pacification-tags"]);
    const badPacificationItem = firstInvalidListValue(entry, ["items", "item"], (value) => isValidResourceLocation(value, { allowTag: true }));
    if (badPacificationItem) return issueDetail("Items", "a valid item id or #tag id", badPacificationItem.value, "pacification-items");
    const badPacificationTag = firstInvalidListValue(entry, ["tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
    if (badPacificationTag) return issueDetail("Tags", "a valid tag id such as c:coins", badPacificationTag.value, "pacification-tags");
    const badPacificationProfession = firstInvalidListValue(entry, ["professions"], isValidProfession);
    if (badPacificationProfession) return issueDetail("Professions", "a valid profession id such as farmer or minecraft:farmer", badPacificationProfession.value, "pacification-professions", "warning");
    const badNumber = firstBadNumberDetail([entry], [
      { key: "count", label: "Exact count", expected: "a number from 1 to 64", fieldId: "pacification-count", valid: (value) => value >= 1 && value <= 64 },
      { key: "min_count", label: "Minimum count", expected: "a number from 1 to 64", fieldId: "pacification-min_count", valid: (value) => value >= 1 && value <= 64 },
      { key: "max_count", label: "Maximum count", expected: "a number from 1 to 64", fieldId: "pacification-max_count", valid: (value) => value >= 1 && value <= 64 }
    ]);
    if (badNumber) return issueDetail(badNumber.label, badNumber.expected, badNumber.value, badNumber.fieldId);
    const min = numberValue(entry.min_count);
    const max = numberValue(entry.max_count);
    if (min !== undefined && max !== undefined && min > max) {
      return issueDetail("Payment count range", "minimum count less than or equal to maximum count", `${min} > ${max}`, ["pacification-min_count", "pacification-max_count"]);
    }
  }
  if (section === "stories") {
    if (kind === "structures" && !hasAnySelector(entry, ["structure", "structures"])) return issueDetail("Structure id(s)", "at least one full structure id like minecraft:village/plains", entry.structure ?? entry.structures, "story-structures");
    if (kind === "biomes" && !hasAnySelector(entry, ["biome", "biomes"])) return issueDetail("Biome id(s)", "at least one full biome id like minecraft:plains", entry.biome ?? entry.biomes, "story-biomes");
    const badStructure = kind === "structures" ? firstInvalidListValue(entry, ["structure", "structures"], (value) => isValidResourceLocation(value, { requireNamespace: true })) : null;
    if (badStructure) return issueDetail("Structure id(s)", "full resource location namespace:path", badStructure.value, "story-structures", "warning");
    const badBiome = kind === "biomes" ? firstInvalidListValue(entry, ["biome", "biomes"], (value) => isValidResourceLocation(value, { requireNamespace: true })) : null;
    if (badBiome) return issueDetail("Biome id(s)", "full resource location namespace:path", badBiome.value, "story-biomes", "warning");
    const badRadius = kind === "structures" ? firstBadNumberDetail([entry], [{ key: "radius", label: "Radius", expected: "a number greater than or equal to 1", fieldId: "story-radius", valid: (value) => value >= 1 }]) : null;
    if (badRadius) return issueDetail(badRadius.label, badRadius.expected, badRadius.value, badRadius.fieldId);
  }
  if (section === "names" && String(entry).trim() === "") return issueDetail("Name", "a non-empty name", entry, []);
  return null;
}

function entryCollectionIssueSeverity(section, kind) {
  const collection = state[section]?.[kind] || [];
  return collection.reduce((severity, entry) => strongestSeverity(severity, entryIssueSeverity(section, kind, entry)), "");
}

function sectionIssueSeverity(section) {
  if (section === "overview") {
    let severity = "";
    const namespacePattern = /^[a-z0-9_.-]+$/;
    const localePattern = /^[a-z]{2}_[a-z]{2}$/;
    if (!namespacePattern.test(state.meta.namespace) || !Number.isInteger(state.meta.packFormat) || state.meta.packFormat < 1 || !PACK_VERSION_IDS.includes(state.meta.packVersion) || !isValidFileName(state.meta.slug)) {
      severity = "error";
    }
    if (!localePattern.test(state.meta.locale)) severity = strongestSeverity(severity, "warning");
    return severity;
  }
  if (section === "dialogue") {
    return ["options", "lines", "messages", "openings", "closings", "pacify"].reduce((severity, kind) => strongestSeverity(severity, entryCollectionIssueSeverity(section, kind)), !isValidFileName(state.dialogue.fileName) ? "error" : "");
  }
  if (section === "forcedDialogue") {
    return entryCollectionIssueSeverity(section, "entries") || (!isValidFileName(state.forcedDialogue.fileName) ? "error" : "");
  }
  if (section === "notifications") {
    return entryCollectionIssueSeverity(section, "notifications") || (!isValidFileName(state.notifications.fileName) ? "error" : "");
  }
  if (section === "gifts") {
    return ["preferences", "rewards"].reduce((severity, kind) => strongestSeverity(severity, entryCollectionIssueSeverity(section, kind)), !isValidFileName(state.gifts.fileName) ? "error" : "");
  }
  if (section === "pacification") {
    return entryCollectionIssueSeverity(section, "payments") || (!isValidFileName(state.pacification.fileName) ? "error" : "");
  }
  if (section === "stories") {
    let severity = ["structures", "biomes"].reduce((value, kind) => strongestSeverity(value, entryCollectionIssueSeverity(section, kind)), "");
    const storyRadius = numberValue(state.stories.radius);
    if (!/^[a-z0-9_.-]+$/.test(state.stories.namespace) || !isValidFileName(state.stories.structureFileName) || !isValidFileName(state.stories.biomeFileName) || (storyRadius !== undefined && storyRadius < 1)) {
      severity = strongestSeverity(severity, "error");
    }
    return severity;
  }
  if (section === "names") {
    const names = [...state.names.male_names, ...state.names.female_names];
    return names.some((name) => String(name).trim() === "") || Boolean(firstDuplicate(names)) ? "warning" : "";
  }
  return "";
}

function addCheck(checks, type, title, text, details = {}) {
  if (checks.some((check) => check.title === title && check.type === type && check.text === text)) return;
  checks.push({ type, title, text, ...details });
}

function firstDuplicate(values) {
  const seen = new Set();
  for (const value of values
    .filter((item) => item !== undefined && item !== null)
    .map(String)
    .map((item) => item.trim())
    .filter(Boolean)) {
    if (seen.has(value)) return value;
    seen.add(value);
  }
  return "";
}

function firstDuplicateEntries(entries, valueForEntry = (entry) => entry?.id) {
  const seen = new Map();
  for (let index = 0; index < entries.length; index += 1) {
    const value = String(valueForEntry(entries[index]) ?? "").trim();
    if (!value) continue;
    if (seen.has(value)) {
      return {
        value,
        matches: [
          { entry: entries[seen.get(value)], index: seen.get(value) },
          { entry: entries[index], index }
        ]
      };
    }
    seen.set(value, index);
  }
  return null;
}

function firstEntryWithDuplicateTextVariant(entries) {
  for (let index = 0; index < entries.length; index += 1) {
    const value = duplicateTextVariant(entries[index]);
    if (value) {
      return {
        value,
        matches: [{ entry: entries[index], index }]
      };
    }
  }
  return null;
}

function fileNameFromPath(path) {
  return String(path || "").split("/").pop() || path;
}

function entryPath(section, entry, kind = activeDialogueKind, index = 0) {
  if (entry?.__sourcePath) return entry.__sourcePath;
  if (section === "dialogue") return dialoguePath(kind, entry, index);
  if (section === "forcedDialogue") return forcedDialoguePath();
  if (section === "notifications") return notificationsPath();
  if (section === "gifts") return giftsPath();
  if (section === "pacification") return pacificationPath();
  if (section === "stories") return kind === "structures" ? structurePath() : biomePath();
  return selectedPath;
}

function entryLocations(section, kind, matches, fieldId) {
  return matches.map((match) => ({
    section,
    kind,
    index: match.index,
    path: entryPath(section, match.entry, kind, match.index),
    fieldId
  }));
}

function locationLabel(location) {
  const kind = humanize(location.kind).toLowerCase();
  return `${fileNameFromPath(location.path)} ${kind} #${location.index + 1}`;
}

function checkLocationDetails(locations) {
  return locations.map(locationLabel).join(" and ");
}

function entryValues(entry, keys) {
  return keys.flatMap((key) => {
    const value = entry[key];
    if (Array.isArray(value)) {
      return value.flatMap(entryValue);
    }
    return entryValue(value);
  });
}

function entryValue(value) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return parseList(value.id ?? value.enchantment ?? value.name);
  }
  return parseList(value);
}

function hasAnySelector(entry, keys) {
  return keys.some((key) => parseList(entry[key]).length > 0 || Boolean(entry[key]));
}

function dialogueItemPayments(entries) {
  return entries
    .map((entry) => entry.give_items || entry.take_items || entry.payment)
    .filter((payment) => payment && typeof payment === "object" && !Array.isArray(payment));
}

function hasPlayerItemFilter(entry) {
  return hasAnySelector(entry, [
    "player_item",
    "player_items",
    "player_item_tag",
    "player_item_tags",
    "player_item_enchantment",
    "player_item_enchantments",
    "held_item_enchantment",
    "held_item_enchantments"
  ])
    || [
      "min_player_item_durability",
      "max_player_item_durability",
      "min_player_item_durability_percent",
      "max_player_item_durability_percent",
      "min_held_item_durability",
      "max_held_item_durability",
      "min_held_item_durability_percent",
      "max_held_item_durability_percent",
      "min_player_item_enchantment_level",
      "max_player_item_enchantment_level",
      "min_held_item_enchantment_level",
      "max_held_item_enchantment_level"
    ].some((key) => numberValue(entry[key]) !== undefined);
}

function firstInvalidValue(entries, keys, predicate) {
  for (const entry of entries) {
    for (const value of entryValues(entry, keys)) {
      if (!predicate(value)) return value;
    }
  }
  return "";
}

function firstBlankListValue(entries, keys) {
  for (const entry of entries) {
    for (const key of keys) {
      if (Array.isArray(entry[key]) && entry[key].some((value) => String(value).trim() === "")) {
        return key;
      }
    }
  }
  return "";
}

function isValidFileName(value) {
  const text = String(value || "").trim();
  return /^[a-z0-9_.\/-]+$/.test(text) && !text.includes("//") && !text.startsWith("/") && !text.endsWith("/");
}

function isValidResourceLocation(value, { allowTag = false, requireNamespace = false } = {}) {
  const text = String(value || "").trim();
  if (!text) return false;
  const isTag = text.startsWith("#");
  if (isTag && !allowTag) return false;
  const body = isTag ? text.slice(1) : text;
  const pattern = requireNamespace
    ? /^[a-z0-9_.-]+:[a-z0-9_./-]+$/
    : /^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$/;
  return pattern.test(body);
}

function isValidProfession(value) {
  return CONSTANTS.professions.includes(value) || isValidResourceLocation(value);
}

function isValidColor(value) {
  return CONSTANTS.colors.includes(value) || /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/.test(value);
}

function numberValue(value) {
  if (value === undefined || value === null || value === "") return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function firstBadNumber(entries, keys, predicate = () => true) {
  for (const entry of entries) {
    for (const key of keys) {
      if (entry[key] === undefined || entry[key] === null || entry[key] === "") continue;
      const number = numberValue(entry[key]);
      if (number === undefined || !predicate(number, entry, key)) return key;
    }
  }
  return "";
}

function validate() {
  const checks = [];
  const namespacePattern = /^[a-z0-9_.-]+$/;
  const localePattern = /^[a-z]{2}_[a-z]{2}$/;

  if (previewEditError) {
    addCheck(checks, "error", "Preview JSON", `Fix invalid JSON in ${previewEditError.path} before it can sync.`);
  }

  if (!namespacePattern.test(state.meta.namespace)) {
    addCheck(checks, "error", "Pack namespace", "Use lowercase letters, numbers, underscores, dots, or hyphens.");
  }
  if (!localePattern.test(state.meta.locale)) {
    addCheck(checks, "warning", "Locale", "Locale folders usually look like en_us or fr_fr.");
  }
  if (!Number.isInteger(state.meta.packFormat) || state.meta.packFormat < 1) {
    addCheck(checks, "error", "Pack format", "pack_format must be a positive integer.");
  }
  if (!PACK_VERSION_IDS.includes(state.meta.packVersion)) {
    addCheck(checks, "error", "VR version", "Choose a supported Villager Retaliation pack version.");
  }
  if (!isValidFileName(state.meta.slug)) {
    addCheck(checks, "error", "File slug", "Use lowercase letters, numbers, underscores, dots, hyphens, or path slashes.");
  }
  if (!isValidFileName(state.dialogue.fileName)) {
    addCheck(checks, "error", "Dialogue file", "Dialogue file names must be lowercase datapack path names.");
  }
  if (!isValidFileName(state.dialogue.folderName || state.meta.slug)) {
    addCheck(checks, "error", "Dialogue folder", "Dialogue folder names must be lowercase datapack path names.");
  }
  if (dialogueUsesFolderLayout() && dialogueFolderName().split("/").some((segment) => DIALOGUE_KIND_KEYS.includes(segment))) {
    addCheck(checks, "error", "Dialogue folder", "Do not use reserved section folder names in the dialogue folder. The builder adds options, lines, messages, openings, closings, or pacify automatically.");
  }
  if (!isValidFileName(state.forcedDialogue.fileName)) {
    addCheck(checks, "error", "Forced dialogue file", "Forced dialogue file names must be lowercase datapack path names.");
  }
  if (!isValidFileName(state.notifications.fileName)) {
    addCheck(checks, "error", "Notification file", "Notification file names must be lowercase datapack path names.");
  }
  if (!isValidFileName(state.gifts.fileName)) {
    addCheck(checks, "error", "Gift file", "Gift file names must be lowercase datapack path names.");
  }
  if (!isValidFileName(state.pacification.fileName)) {
    addCheck(checks, "error", "Pacification file", "Pacification file names must be lowercase datapack path names.");
  }
  if (!namespacePattern.test(state.stories.namespace)) {
    addCheck(checks, "error", "Story namespace", "Story files need a valid lowercase namespace.");
  }
  if (!isValidFileName(state.stories.structureFileName) || !isValidFileName(state.stories.biomeFileName)) {
    addCheck(checks, "error", "Story file", "Story file names must be lowercase datapack path names.");
  }

  for (const entry of state.dialogue.options) {
    if (!entry.id || !entry.label || !isValidDialogueOptionType(entry) || !entry.request) {
      addCheck(checks, "error", "Dialogue option", "Every option needs an id, label, request, and a valid option type.");
      break;
    }
  }
  for (const entry of state.dialogue.lines) {
    if (!entry.request || (!hasDialogueText(entry) && !(supportsBeta12DialogueFields() && entry.text_key))) {
      addCheck(checks, "error", "Dialogue line", "Every line needs a request and text.");
      break;
    }
  }
  for (const entry of state.dialogue.messages) {
    if (!entry.key || !hasDialogueText(entry)) {
      addCheck(checks, "error", "Dialogue message", "Every message needs a key and text.");
      break;
    }
  }
  for (const kind of ["openings", "closings", "pacify"]) {
    for (const entry of state.dialogue[kind]) {
      if (!hasDialogueText(entry)) {
        const label = kind === "pacify" ? "pacify line" : kind.slice(0, -1);
        addCheck(checks, "error", `Dialogue ${kind}`, `Every ${label} entry needs text.`);
        break;
      }
    }
  }
  const duplicateOption = firstDuplicateEntries(state.dialogue.options);
  if (duplicateOption) {
    const locations = entryLocations("dialogue", "options", duplicateOption.matches, "dialogue-id");
    addCheck(checks, "warning", "Dialogue option ids", `Duplicate option id: ${duplicateOption.value} in ${checkLocationDetails(locations)}.`, {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const allDialogueEntries = ["options", "lines", "messages", "openings", "closings", "pacify"].flatMap((kind) => state.dialogue[kind]);
  const duplicateDialogueVariant = ["lines", "messages", "openings", "closings", "pacify"]
    .map((kind) => ({ kind, duplicate: firstEntryWithDuplicateTextVariant(state.dialogue[kind]) }))
    .find((result) => result.duplicate);
  if (duplicateDialogueVariant) {
    const locations = entryLocations("dialogue", duplicateDialogueVariant.kind, duplicateDialogueVariant.duplicate.matches, "dialogue-text");
    addCheck(checks, "warning", "Dialogue text variants", `Duplicate text variation: ${duplicateDialogueVariant.duplicate.value} in ${checkLocationDetails(locations)}.`, {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const badDialogueType = firstInvalidValue([...state.dialogue.options, ...state.dialogue.lines], ["request"], (value) => CONSTANTS.dialogueTypes.includes(value));
  if (badDialogueType) {
    addCheck(checks, "warning", "Dialogue request", `Unknown dialogue request: ${badDialogueType}.`);
  }
  const badDisposition = firstInvalidValue(allDialogueEntries, ["dispositions"], (value) => CONSTANTS.dispositions.includes(value));
  if (badDisposition) {
    addCheck(checks, "warning", "Dialogue disposition", `Unknown disposition: ${badDisposition}.`);
  }
  if (!supportsBeta12DialogueFields() && state.dialogue.lines.some(hasBeta12DialogueField)) {
    addCheck(checks, "warning", "Dialogue beta.12 fields", "Mood and social-attribute dialogue filters require the VR 1.0.0-beta.12 target.");
  }
  if (!supportsBeta12DialogueFields() && state.dialogue.options.some(hasBeta12DialogueField)) {
    addCheck(checks, "warning", "Dialogue beta.12 option fields", "Option conditions require the VR 1.0.0-beta.12 target.");
  }
  const plannedOptionDeprecation = state.dialogue.options
    .map((entry, index) => ({ entry, index }))
    .filter(({ entry }) => hasPlannedBeta13DialogueOptionDeprecationField(entry));
  if (plannedOptionDeprecation.length > 0) {
    const locations = entryLocations("dialogue", "options", plannedOptionDeprecation, "dialogue-conditions");
    addCheck(checks, "info", "Dialogue option deprecation", dialogueDeprecationMessage("options", plannedOptionDeprecation[0].entry), {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const plannedLineDeprecation = state.dialogue.lines
    .map((entry, index) => ({ entry, index }))
    .filter(({ entry }) => hasPlannedBeta13DialogueLineDeprecationField(entry));
  if (plannedLineDeprecation.length > 0) {
    const locations = entryLocations("dialogue", "lines", plannedLineDeprecation, "dialogue-conditions");
    addCheck(checks, "info", "Dialogue line deprecation", dialogueDeprecationMessage("lines", plannedLineDeprecation[0].entry), {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const badMood = firstInvalidValue(state.dialogue.lines, ["mood", "moods"], (value) => CONSTANTS.moods.includes(value));
  if (badMood) {
    addCheck(checks, "warning", "Dialogue mood", `Unknown temporary mood: ${badMood}.`);
  }
  const badDialogueProfession = firstInvalidValue(allDialogueEntries, ["professions"], isValidProfession);
  if (badDialogueProfession) {
    addCheck(checks, "warning", "Dialogue profession", `Invalid profession id: ${badDialogueProfession}.`);
  }
  const badDialogueItem = firstInvalidValue([...state.dialogue.options, ...state.dialogue.lines], ["player_item", "player_items", "player_item_tag", "player_item_tags"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badDialogueItem) {
    addCheck(checks, "error", "Dialogue item filter", `Invalid item or tag selector: ${badDialogueItem}.`);
  }
  const badDialogueSlot = firstInvalidValue([...state.dialogue.options, ...state.dialogue.lines], ["player_item_slot", "player_item_slots"], (value) => CONSTANTS.itemSlots.includes(value));
  if (badDialogueSlot) {
    addCheck(checks, "warning", "Dialogue item slot", `Unknown item slot: ${badDialogueSlot}.`);
  }
  const dialoguePayments = dialogueItemPayments(state.dialogue.options);
  for (const payment of dialoguePayments) {
    if (!hasAnySelector(payment, ["items", "item", "tags", "tag"])) {
      addCheck(checks, "error", "Dialogue give items", "Every give_items object needs at least one item or tag.");
      break;
    }
    if (payment.count === undefined && payment.amount === undefined) {
      addCheck(checks, "error", "Dialogue give items", "Every give_items object needs a count.");
      break;
    }
  }
  const badDialoguePaymentSelector = firstInvalidValue(dialoguePayments, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badDialoguePaymentSelector) {
    addCheck(checks, "error", "Dialogue give items", `Invalid give_items item or tag selector: ${badDialoguePaymentSelector}.`);
  }
  const badDialoguePaymentDestination = firstInvalidValue(dialoguePayments, ["destination", "overflow_destination"], (value) => CONSTANTS.dialogueItemDestinations.includes(value));
  if (badDialoguePaymentDestination) {
    addCheck(checks, "warning", "Dialogue give items", `Unknown give_items destination: ${badDialoguePaymentDestination}.`);
  }
  const badDialoguePaymentNumber = firstBadNumber(dialoguePayments, ["count", "amount"], (value) => value >= 1);
  if (badDialoguePaymentNumber) {
    addCheck(checks, "error", "Dialogue give items", `${humanize(badDialoguePaymentNumber)} has an invalid item count.`);
  }
  const reputationConditionEntries = [...state.dialogue.options, ...state.dialogue.lines];
  const badDialogueReputation = firstInvalidValue(reputationConditionEntries, ["reputation_level", "reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value));
  if (badDialogueReputation) {
    addCheck(checks, "warning", "Dialogue reputation", `Unknown reputation level: ${badDialogueReputation}.`);
  }
  const badWeather = firstInvalidValue(state.dialogue.lines, ["weather"], (value) => CONSTANTS.weather.includes(value));
  if (badWeather) {
    addCheck(checks, "warning", "Dialogue weather", `Unknown weather value: ${badWeather}.`);
  }
  const badTime = firstInvalidValue(state.dialogue.lines, ["times"], (value) => CONSTANTS.times.includes(value));
  if (badTime) {
    addCheck(checks, "warning", "Dialogue time", `Unknown time value: ${badTime}.`);
  }
  const badGiftAdvice = firstInvalidValue(state.dialogue.lines, ["gift_advice"], (value) => CONSTANTS.giftAdvice.includes(value));
  if (badGiftAdvice) {
    addCheck(checks, "warning", "Gift advice filter", `Unknown gift advice filter: ${badGiftAdvice}.`);
  }
  const badPacifyOutcome = firstInvalidValue(state.dialogue.pacify, ["outcomes"], (value) => CONSTANTS.pacifyOutcomes.includes(value));
  if (badPacifyOutcome) {
    addCheck(checks, "warning", "Pacify outcome", `Unknown pacify outcome: ${badPacifyOutcome}.`);
  }
  const badDialogueOrderNumber = firstBadNumber(allDialogueEntries, ["order"], Number.isFinite);
  if (badDialogueOrderNumber) {
    addCheck(checks, "error", "Dialogue number", `${humanize(badDialogueOrderNumber)} has an invalid number.`);
  }
  const badDialogueNumber = firstBadNumber(allDialogueEntries, ["weight", "min_recruitment_follow_distance"], (value) => value >= 0);
  if (badDialogueNumber) {
    addCheck(checks, "error", "Dialogue number", `${humanize(badDialogueNumber)} must be a non-negative number.`);
  }
  const badMoodIntensity = firstBadNumber(state.dialogue.lines, ["min_mood_intensity"], (value) => value >= 0 && value <= 100);
  if (badMoodIntensity) {
    addCheck(checks, "error", "Dialogue mood", "Minimum mood intensity must be from 0 to 100.");
  }
  const badSocialAttributeNumber = firstBadNumber(state.dialogue.lines, socialAttributeNumberKeys(), (value) => value >= 1 && value <= 100);
  if (badSocialAttributeNumber) {
    addCheck(checks, "error", "Dialogue social attribute", `${humanize(badSocialAttributeNumber)} must be from 1 to 100.`);
  }
  const badSocialAttributeRange = state.dialogue.lines.map(invalidSocialAttributeRange).find(Boolean);
  if (badSocialAttributeRange) {
    addCheck(checks, "error", "Dialogue social attribute", `Minimum ${humanize(badSocialAttributeRange)} cannot be higher than maximum ${humanize(badSocialAttributeRange)}.`);
  }
  const badDialogueReputationNumber = firstBadNumber(reputationConditionEntries, ["min_reputation", "max_reputation"], Number.isFinite);
  if (badDialogueReputationNumber) {
    addCheck(checks, "error", "Dialogue reputation", `${humanize(badDialogueReputationNumber)} has an invalid number.`);
  }
  for (const entry of reputationConditionEntries) {
    const min = numberValue(entry.min_reputation);
    const max = numberValue(entry.max_reputation);
    if (min !== undefined && max !== undefined && min > max) {
      addCheck(checks, "error", "Dialogue reputation", "Minimum reputation cannot be higher than maximum reputation.");
      break;
    }
  }
  const blankDialogueList = firstBlankListValue(allDialogueEntries, ["professions", "dispositions", "tags", "mood", "moods", "reputation_level", "reputation_levels", "player_items", "player_item_slots", "weather", "times", "event_tags", "player_event_tags", "retaliation_target_entity_types", "story_structures", "story_biomes", "outcomes"]);
  if (blankDialogueList) {
    addCheck(checks, "warning", "Dialogue list", `${humanize(blankDialogueList)} contains a blank value.`);
  }
  const badDialogueRetaliationTarget = firstInvalidValue(allDialogueEntries, ["retaliation_target_entity_types", "retaliation_target_entities"], isValidResourceLocation);
  if (badDialogueRetaliationTarget) {
    addCheck(checks, "error", "Dialogue retaliation target", `Invalid retaliation target entity id: ${badDialogueRetaliationTarget}.`);
  }

  for (const entry of state.forcedDialogue.entries) {
    if (!forcedTriggerValue(entry) || !hasForcedDialogueLine(entry)) {
      addCheck(checks, "error", "Forced dialogue", "Every forced dialogue entry needs a trigger and opening line.");
      break;
    }
  }
  const duplicateForcedDialogue = firstDuplicateEntries(state.forcedDialogue.entries);
  if (duplicateForcedDialogue) {
    const locations = entryLocations("forcedDialogue", "entries", duplicateForcedDialogue.matches, "forced-id");
    addCheck(checks, "warning", "Forced dialogue ids", `Duplicate forced dialogue id: ${duplicateForcedDialogue.value} in ${checkLocationDetails(locations)}.`, {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const badForcedTrigger = firstInvalidValue(state.forcedDialogue.entries, ["trigger", "event"], (value) => CONSTANTS.forcedDialogueTriggers.includes(value));
  if (badForcedTrigger) {
    addCheck(checks, "error", "Forced dialogue trigger", `Unknown forced dialogue trigger: ${badForcedTrigger}.`);
  }
  if (state.forcedDialogue.entries.some((entry) => forcedTriggerValue(entry) === "player_item_proximity" && entryValues(entry, ["player_item", "player_items", "player_item_tag", "player_item_tags"]).length === 0)) {
    addCheck(checks, "error", "Forced dialogue player item", "player_item_proximity entries need at least one player item or tag filter.");
  }
  const badForcedOutputMode = firstInvalidValue(state.forcedDialogue.entries.map((entry) => entry.output || {}), ["mode"], (value) => CONSTANTS.forcedOutputModes.includes(value));
  if (badForcedOutputMode) {
    addCheck(checks, "error", "Forced dialogue output", `Unknown output mode: ${badForcedOutputMode}.`);
  }
  const badForcedProfession = firstInvalidValue(state.forcedDialogue.entries, ["witness_profession", "witness_professions", "professions"], isValidProfession);
  if (badForcedProfession) {
    addCheck(checks, "warning", "Forced dialogue witness", `Invalid witness profession id: ${badForcedProfession}.`);
  }
  const badForcedPlayerItem = firstInvalidValue(state.forcedDialogue.entries, ["player_item", "player_items", "player_item_tag", "player_item_tags"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badForcedPlayerItem) {
    addCheck(checks, "error", "Forced dialogue player item", `Invalid player item or tag id: ${badForcedPlayerItem}.`);
  }
  const badForcedPlayerItemSlot = firstInvalidValue(state.forcedDialogue.entries, ["player_item_slot", "player_item_slots"], (value) => CONSTANTS.itemSlots.includes(value));
  if (badForcedPlayerItemSlot) {
    addCheck(checks, "warning", "Forced dialogue player item slot", `Unknown item slot: ${badForcedPlayerItemSlot}.`);
  }
  const badForcedLootTable = firstInvalidValue(state.forcedDialogue.entries, ["loot_table", "loot_tables"], isValidResourceLocation);
  if (badForcedLootTable) {
    addCheck(checks, "error", "Forced dialogue loot table", `Invalid loot table id: ${badForcedLootTable}.`);
  }
  const badForcedTargetEntity = firstInvalidValue(state.forcedDialogue.entries, ["target_entity_type", "target_entity_types", "target_entities"], isValidResourceLocation);
  if (badForcedTargetEntity) {
    addCheck(checks, "error", "Forced dialogue target", `Invalid target entity id: ${badForcedTargetEntity}.`);
  }
  const badForcedNumber = firstBadNumber(state.forcedDialogue.entries, ["priority", "reputation", "witness_radius", "min_recent_retaliations", "max_recent_retaliations"], (value, entry, key) => {
    if (key === "reputation") return isForcedDialogueOutput(entry) ? Number.isFinite(value) : true;
    if (key === "priority") return Number.isFinite(value);
    if (key === "witness_radius") return value >= 1;
    return Number.isFinite(value) && value >= 0;
  });
  if (badForcedNumber) {
    addCheck(checks, "error", "Forced dialogue number", `${humanize(badForcedNumber)} has an invalid number.`);
  }
  const badForcedOutputRadius = firstBadNumber(state.forcedDialogue.entries.filter(isChatOutputEntry).map((entry) => entry.output || {}), ["radius"], (value) => value >= 1);
  if (badForcedOutputRadius) {
    addCheck(checks, "error", "Forced dialogue output", "Output radius must be a positive number.");
  }
  if (state.forcedDialogue.entries.some(hasIgnoredForcedDialogueFields)) {
    addCheck(checks, "warning", "Forced dialogue output", "Chat output ignores forced-dialogue options, leave outcomes, reputation changes, aggro, and camera controls.");
  }
  const blankForcedList = firstBlankListValue(state.forcedDialogue.entries, ["lines", "loot_tables", "witness_profession", "witness_professions", "professions", "player_items", "player_item_slots", "target_entity_types", "target_entities"]);
  if (blankForcedList) {
    addCheck(checks, "warning", "Forced dialogue list", `${humanize(blankForcedList)} contains a blank value.`);
  }
  const badForcedRetaliationRange = state.forcedDialogue.entries.some((entry) => {
    const min = entry.min_recent_retaliations;
    const max = entry.max_recent_retaliations;
    return Number.isFinite(min) && Number.isFinite(max) && min > max;
  });
  if (badForcedRetaliationRange) {
    addCheck(checks, "error", "Forced dialogue retaliation range", "Min recent retaliations must be less than or equal to max recent retaliations.");
  }
  for (const entry of state.forcedDialogue.entries) {
    if (!isForcedDialogueOutput(entry)) continue;
    const options = Array.isArray(entry.options) ? entry.options : [];
    const leaveOptions = forcedLeaveOptions(entry);
    const actionableOptions = [...options, ...leaveOptions];
    for (const option of options) {
      if (!option.id || !option.label) {
        addCheck(checks, "error", "Forced dialogue option", "Every forced dialogue option needs an id and label.");
        break;
      }
    }
    const badOptionNumber = firstBadNumber(actionableOptions, ["order", "reputation", "aggro_chance"], (value, entry, key) => key === "aggro_chance" ? value >= 0 && value <= 1 : Number.isFinite(value));
    if (badOptionNumber) {
      addCheck(checks, "error", "Forced option number", `${humanize(badOptionNumber)} has an invalid number.`);
      break;
    }
    const badForcedOptionReputation = firstInvalidValue(actionableOptions, ["reputation_level", "reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value));
    if (badForcedOptionReputation) {
      addCheck(checks, "warning", "Forced option reputation", `Unknown reputation level: ${badForcedOptionReputation}.`);
      break;
    }
    const badForcedOptionReputationNumber = firstBadNumber(actionableOptions, ["min_reputation", "max_reputation"], Number.isFinite);
    if (badForcedOptionReputationNumber) {
      addCheck(checks, "error", "Forced option reputation", `${humanize(badForcedOptionReputationNumber)} has an invalid number.`);
      break;
    }
    const badForcedOptionReputationRange = actionableOptions.some((option) => {
      const min = numberValue(option.min_reputation);
      const max = numberValue(option.max_reputation);
      return min !== undefined && max !== undefined && min > max;
    });
    if (badForcedOptionReputationRange) {
      addCheck(checks, "error", "Forced option reputation", "Minimum reputation cannot be higher than maximum reputation.");
      break;
    }
    const payments = actionableOptions
      .map((option) => option.take_items || option.payment)
      .filter((payment) => payment && typeof payment === "object" && !Array.isArray(payment));
    const stolenReturns = actionableOptions
      .map((option) => option.take_stolen_items || option.return_stolen_items)
      .filter((stolenReturn) => stolenReturn && typeof stolenReturn === "object" && !Array.isArray(stolenReturn));
    for (const payment of payments) {
      if (!hasAnySelector(payment, ["items", "item", "tags", "tag"])) {
        addCheck(checks, "error", "Forced option payment", "Every take_items payment needs at least one item or tag.");
        break;
      }
      if (payment.count === undefined && payment.amount === undefined) {
        addCheck(checks, "error", "Forced option payment", "Every take_items payment needs a count.");
        break;
      }
    }
    const badPaymentSelector = firstInvalidValue(payments, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
    if (badPaymentSelector) {
      addCheck(checks, "error", "Forced option payment", `Invalid take_items item or tag selector: ${badPaymentSelector}.`);
      break;
    }
    const badPaymentDestination = firstInvalidValue(payments, ["destination", "overflow_destination"], (value) => CONSTANTS.forcedItemDestinations.includes(value));
    if (badPaymentDestination) {
      addCheck(checks, "warning", "Forced option payment", `Unknown take_items destination: ${badPaymentDestination}.`);
      break;
    }
    const badPaymentNumber = firstBadNumber(payments, ["count", "amount", "success_reputation", "failure_reputation"], (value, entry, key) => key === "count" || key === "amount" ? value >= 1 : Number.isFinite(value));
    if (badPaymentNumber) {
      addCheck(checks, "error", "Forced option payment", `${humanize(badPaymentNumber)} has an invalid payment number.`);
      break;
    }
    const badStolenReturnDestination = firstInvalidValue(stolenReturns, ["destination", "overflow_destination"], (value) => CONSTANTS.forcedItemDestinations.includes(value));
    if (badStolenReturnDestination) {
      addCheck(checks, "warning", "Forced stolen item return", `Unknown stolen-item destination: ${badStolenReturnDestination}.`);
      break;
    }
    const badStolenReturnNumber = firstBadNumber(stolenReturns, ["success_reputation", "failure_reputation"], Number.isFinite);
    if (badStolenReturnNumber) {
      addCheck(checks, "error", "Forced stolen item return", `${humanize(badStolenReturnNumber)} has an invalid number.`);
      break;
    }
  }

  for (const entry of state.notifications.notifications) {
    if (!entry.trigger || !hasNotificationText(entry)) {
      addCheck(checks, "error", "Notification", "Every notification needs a trigger and text.");
      break;
    }
  }
  const duplicateNotification = firstDuplicateEntries(state.notifications.notifications);
  if (duplicateNotification) {
    const locations = entryLocations("notifications", "notifications", duplicateNotification.matches, "notification-id");
    addCheck(checks, "warning", "Notification ids", `Duplicate notification id: ${duplicateNotification.value} in ${checkLocationDetails(locations)}.`, {
      locations,
      paths: locations.map((location) => location.path)
    });
  }
  const badNotificationTrigger = firstInvalidValue(state.notifications.notifications, ["trigger"], (value) => CONSTANTS.notificationTriggers.includes(value));
  if (badNotificationTrigger) {
    addCheck(checks, "warning", "Notification trigger", `Custom notification trigger: ${badNotificationTrigger}.`);
  }
  const badHudKind = firstInvalidValue(state.notifications.notifications, ["kind"], (value) => CONSTANTS.hudKinds.includes(value));
  if (badHudKind) {
    addCheck(checks, "error", "Notification HUD kind", `Unknown HUD kind: ${badHudKind}.`);
  }
  const badWorldKind = firstInvalidValue(state.notifications.notifications, ["world_text_kind", "style"], (value) => CONSTANTS.worldTextKinds.includes(value));
  if (badWorldKind) {
    addCheck(checks, "error", "Notification world text", `Unknown world text kind: ${badWorldKind}.`);
  }
  const badNotificationColor = firstInvalidValue(state.notifications.notifications, ["color", "text_color", "chat_color"], isValidColor);
  if (badNotificationColor) {
    addCheck(checks, "warning", "Notification color", `Use a Minecraft color name or hex color instead of ${badNotificationColor}.`);
  }
  const badNotificationProfession = firstInvalidValue(state.notifications.notifications, ["professions"], isValidProfession);
  if (badNotificationProfession) {
    addCheck(checks, "warning", "Notification profession", `Invalid profession id: ${badNotificationProfession}.`);
  }
  const badReputationLevel = firstInvalidValue(state.notifications.notifications, ["reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value));
  if (badReputationLevel) {
    addCheck(checks, "warning", "Notification reputation", `Unknown reputation level: ${badReputationLevel}.`);
  }
  const badNotificationTargetEntity = firstInvalidValue(state.notifications.notifications, ["target_entity_type", "target_entity", "target_entity_types", "target_entities"], isValidResourceLocation);
  if (badNotificationTargetEntity) {
    addCheck(checks, "error", "Notification target filter", `Invalid target entity id: ${badNotificationTargetEntity}.`);
  }
  const badNotificationItem = firstInvalidValue(state.notifications.notifications, ["player_item", "player_items", "player_item_tag", "player_item_tags"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badNotificationItem) {
    addCheck(checks, "error", "Notification item filter", `Invalid item or tag selector: ${badNotificationItem}.`);
  }
  const badNotificationSlot = firstInvalidValue(state.notifications.notifications, ["player_item_slot", "player_item_slots"], (value) => CONSTANTS.itemSlots.includes(value));
  if (badNotificationSlot) {
    addCheck(checks, "warning", "Notification item slot", `Unknown item slot: ${badNotificationSlot}.`);
  }
  const badNotificationNumber = firstBadNumber(state.notifications.notifications, ["min_reputation", "max_reputation", "weight"], (value, entry, key) => key === "weight" ? value >= 0 : Number.isFinite(value));
  if (badNotificationNumber) {
    addCheck(checks, "error", "Notification number", `${humanize(badNotificationNumber)} has an invalid number.`);
  }
  for (const entry of state.notifications.notifications) {
    const min = numberValue(entry.min_reputation);
    const max = numberValue(entry.max_reputation);
    if (min !== undefined && max !== undefined && min > max) {
      addCheck(checks, "error", "Notification range", "Minimum reputation cannot be higher than maximum reputation.");
      break;
    }
    const chance = numberValue(entry.chance);
    if (chance !== undefined && (chance < 0 || chance > 1)) {
      addCheck(checks, "error", "Notification chance", "Chance must be between 0 and 1.");
      break;
    }
  }

  for (const entry of state.gifts.preferences) {
    if (!entry.reaction || !hasAnySelector(entry, ["items", "tags", "item", "tag"])) {
      addCheck(checks, "error", "Gift preference", "Every preference needs a reaction and at least one item or tag.");
      break;
    }
  }
  for (const entry of state.gifts.rewards) {
    if (!entry.item) {
      addCheck(checks, "error", "Gift reward", "Every reward needs an item id.");
      break;
    }
  }
  const badReaction = firstInvalidValue(state.gifts.preferences, ["reaction"], (value) => CONSTANTS.reactions.includes(value));
  if (badReaction) {
    addCheck(checks, "error", "Gift reaction", `Unknown gift reaction: ${badReaction}.`);
  }
  const badGiftSelector = firstInvalidValue(state.gifts.preferences, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badGiftSelector) {
    addCheck(checks, "error", "Gift selector", `Invalid item or tag selector: ${badGiftSelector}.`);
  }
  const badGiftReward = firstInvalidValue(state.gifts.rewards, ["item"], (value) => isValidResourceLocation(value));
  if (badGiftReward) {
    addCheck(checks, "error", "Gift reward item", `Invalid reward item id: ${badGiftReward}.`);
  }
  const badGiftProfession = firstInvalidValue([...state.gifts.preferences, ...state.gifts.rewards], ["professions"], isValidProfession);
  if (badGiftProfession) {
    addCheck(checks, "warning", "Gift profession", `Invalid profession id: ${badGiftProfession}.`);
  }
  const badGiftReputation = firstInvalidValue(state.gifts.rewards, ["reputation_levels"], (value) => CONSTANTS.reputationLevels.includes(value));
  if (badGiftReputation) {
    addCheck(checks, "warning", "Gift reputation", `Unknown reputation level: ${badGiftReputation}.`);
  }
  const badGiftNumber = firstBadNumber([...state.gifts.preferences, ...state.gifts.rewards], ["priority", "reputation_per_item", "min_count", "max_count", "weight"], (value, entry, key) => {
    if (key === "min_count" || key === "max_count") return value >= 1 && value <= 64;
    if (key === "weight") return value > 0;
    return Number.isFinite(value);
  });
  if (badGiftNumber) {
    addCheck(checks, "error", "Gift number", `${humanize(badGiftNumber)} has an invalid number.`);
  }
  for (const entry of state.gifts.rewards) {
    const min = numberValue(entry.min_count);
    const max = numberValue(entry.max_count);
    if (min !== undefined && max !== undefined && min > max) {
      addCheck(checks, "error", "Gift count range", "Reward minimum count cannot be higher than maximum count.");
      break;
    }
  }
  const messageKeys = new Set(state.dialogue.messages.map((entry) => entry.key).filter(Boolean));
  for (const entry of state.gifts.preferences) {
    if (entry.response_key && !messageKeys.has(entry.response_key)) {
      addCheck(checks, "warning", "Gift response key", `No dialogue message currently defines ${entry.response_key}.`);
      break;
    }
  }

  for (const entry of state.pacification.payments) {
    if (!hasAnySelector(entry, ["items", "tags", "item", "tag"])) {
      addCheck(checks, "error", "Pacification payment", "Every payment needs at least one item or tag.");
      break;
    }
  }
  const badPacificationSelector = firstInvalidValue(state.pacification.payments, ["items", "item", "tags", "tag"], (value) => isValidResourceLocation(value, { allowTag: true }));
  if (badPacificationSelector) {
    addCheck(checks, "error", "Pacification selector", `Invalid item or tag selector: ${badPacificationSelector}.`);
  }
  const badPacificationProfession = firstInvalidValue(state.pacification.payments, ["professions"], isValidProfession);
  if (badPacificationProfession) {
    addCheck(checks, "warning", "Pacification profession", `Invalid profession id: ${badPacificationProfession}.`);
  }
  const badPacificationNumber = firstBadNumber(state.pacification.payments, ["count", "min_count", "max_count"], (value) => value >= 1 && value <= 64);
  if (badPacificationNumber) {
    addCheck(checks, "error", "Pacification count", `${humanize(badPacificationNumber)} must be between 1 and 64.`);
  }
  for (const entry of state.pacification.payments) {
    const min = numberValue(entry.min_count);
    const max = numberValue(entry.max_count);
    if (min !== undefined && max !== undefined && min > max) {
      addCheck(checks, "error", "Pacification range", "Minimum count cannot be higher than maximum count.");
      break;
    }
  }

  const storyRadius = numberValue(state.stories.radius);
  if (storyRadius !== undefined && storyRadius < 1) {
    addCheck(checks, "error", "Story radius", "Story radius must be at least 1.");
  }
  for (const entry of state.stories.structures) {
    if (!hasAnySelector(entry, ["structure", "structures"])) {
      addCheck(checks, "error", "Story structure", "Every structure story needs a structure id.");
      break;
    }
  }
  for (const entry of state.stories.biomes) {
    if (!hasAnySelector(entry, ["biome", "biomes"])) {
      addCheck(checks, "error", "Story biome", "Every biome story needs a biome id.");
      break;
    }
  }
  const badStructure = firstInvalidValue(state.stories.structures, ["structure", "structures"], (value) => isValidResourceLocation(value, { requireNamespace: true }));
  if (badStructure) {
    addCheck(checks, "warning", "Story structure id", `Use a full structure id like namespace:path instead of ${badStructure}.`);
  }
  const badBiome = firstInvalidValue(state.stories.biomes, ["biome", "biomes"], (value) => isValidResourceLocation(value, { requireNamespace: true }));
  if (badBiome) {
    addCheck(checks, "warning", "Story biome id", `Use a full biome id like namespace:path instead of ${badBiome}.`);
  }
  const badStoryNumber = firstBadNumber(state.stories.structures, ["radius"], (value) => value >= 1);
  if (badStoryNumber) {
    addCheck(checks, "error", "Story entry radius", "Structure story radius must be at least 1.");
  }
  const blankNames = [...state.names.male_names, ...state.names.female_names].some((name) => String(name).trim() === "");
  if (blankNames) {
    addCheck(checks, "warning", "Preset names", "Preset name lists contain a blank value.");
  }
  const duplicateName = firstDuplicate([...state.names.male_names, ...state.names.female_names]);
  if (duplicateName) {
    addCheck(checks, "warning", "Duplicate preset name", `Preset name appears more than once: ${duplicateName}.`);
  }

  if (checks.length === 0) {
    addCheck(checks, "ok", "Ready", "The generated datapack paths and required fields look good.");
  }
  return checks;
}

function render() {
  hideTooltip();
  window.clearTimeout(outputRenderTimer);
  migrateMisroutedDialogueNotifications();
  invalidateCurrentViewSnapshot();
  renderWorkspaceChrome();
  renderTabs();
  renderPanel();
  updateForcedOutputModeFields(els.panel);
  resizeTextareas(els.panel);
  syncValueTags(els.panel);
  applyEntryIssueHighlights();
  renderFiles();
  renderEntryDirectory();
  renderChecks();
  renderPreview();
  updateShortcutTooltips();
  renderIcons();
}

function migrateMisroutedDialogueNotifications() {
  const moved = [];
  for (const kind of ["options", "lines", "messages", "openings", "closings", "pacify"]) {
    const kept = [];
    for (const entry of state.dialogue[kind]) {
      if (isNotificationEntry(entry)) {
        moved.push(entry);
      } else {
        kept.push(entry);
      }
    }
    state.dialogue[kind] = kept;
  }
  if (moved.length === 0) return;
  const existingKeys = new Set(state.notifications.notifications.map(notificationEntryKey));
  for (const entry of moved) {
    const cleaned = cleanObject(entry);
    const key = notificationEntryKey(cleaned);
    if (existingKeys.has(key)) continue;
    state.notifications.notifications.push(cleaned);
    existingKeys.add(key);
  }
  if (editing?.section === "dialogue") {
    editing = null;
    clearEntryFormDirty();
  }
}

function notificationEntryKey(entry) {
  if (entry?.id) return `id:${entry.id}`;
  return JSON.stringify(cleanObject(entry));
}

function renderOutputPanels() {
  window.clearTimeout(outputRenderTimer);
  renderFiles();
  renderEntryDirectory();
  renderChecks();
  renderPreview();
  renderIcons();
}

function scheduleOutputRender(delay = 140) {
  window.clearTimeout(outputRenderTimer);
  outputRenderTimer = window.setTimeout(() => {
    invalidateCurrentViewSnapshot();
    renderOutputPanels();
  }, delay);
}

function renderWorkspaceChrome() {
  if (!els.leftPanelToggleButton || !els.rightPanelToggleButton) return;
  els.workspace.classList.toggle("is-left-hidden", !showLeftPanel);
  els.workspace.classList.toggle("is-right-hidden", !showRightPanel);
  els.fileExplorer.classList.toggle("is-collapsed", !showLeftPanel);
  els.rightRail.classList.toggle("is-collapsed", !showRightPanel);
  updateLeftPanelMode();
  els.fileExplorer.setAttribute("aria-label", showLeftPanel ? "File explorer" : "Show file explorer");
  els.rightRail.setAttribute("aria-label", showRightPanel ? "Output" : "Show output");
  if (!showLeftPanel) {
    els.fileExplorer.setAttribute("role", "button");
    els.fileExplorer.setAttribute("tabindex", "0");
  } else {
    els.fileExplorer.removeAttribute("role");
    els.fileExplorer.removeAttribute("tabindex");
  }
  if (!showRightPanel) {
    els.rightRail.setAttribute("role", "button");
    els.rightRail.setAttribute("tabindex", "0");
  } else {
    els.rightRail.removeAttribute("role");
    els.rightRail.removeAttribute("tabindex");
  }
  els.leftPanelToggleButton.classList.toggle("is-on", showLeftPanel);
  els.leftPanelToggleButton.setAttribute("aria-pressed", String(showLeftPanel));
  els.leftPanelToggleButton.setAttribute("aria-label", showLeftPanel ? "Hide sections" : "Show sections");
  els.leftPanelToggleButton.innerHTML = icon(showLeftPanel ? "panel-left-close" : "panel-left-open", "button-icon");
  els.rightPanelToggleButton.classList.toggle("is-on", showRightPanel);
  els.rightPanelToggleButton.setAttribute("aria-pressed", String(showRightPanel));
  els.rightPanelToggleButton.setAttribute("aria-label", showRightPanel ? "Hide output" : "Show output");
  els.rightPanelToggleButton.innerHTML = icon(showRightPanel ? "panel-right-close" : "panel-right-open", "button-icon");
}

function totalEntries(...collections) {
  return collections.reduce((sum, collection) => sum + (Array.isArray(collection) ? collection.length : 0), 0);
}

function sectionCounts() {
  return {
    overview: state.meta.packName && state.meta.namespace && state.meta.slug ? "Ready" : "Setup",
    dialogue: totalEntries(
      state.dialogue.options,
      state.dialogue.lines,
      state.dialogue.messages,
      state.dialogue.openings,
      state.dialogue.closings,
      state.dialogue.pacify
    ),
    forcedDialogue: totalEntries(state.forcedDialogue.entries),
    notifications: totalEntries(state.notifications.notifications),
    gifts: totalEntries(state.gifts.preferences, state.gifts.rewards),
    pacification: totalEntries(state.pacification.payments),
    stories: totalEntries(state.stories.structures, state.stories.biomes),
    names: totalEntries(state.names.male_names, state.names.female_names)
  };
}

function renderTabs() {
  const counts = sectionCounts();
  for (const tab of els.tabs.querySelectorAll(".tab")) {
    const active = tab.dataset.section === activeSection;
    const value = counts[tab.dataset.section] ?? "";
    const severity = sectionIssueSeverity(tab.dataset.section);
    const counter = tab.querySelector(".tab-count");
    tab.classList.toggle("is-active", active);
    tab.classList.toggle("has-error", severity === "error");
    tab.classList.toggle("has-warning", severity === "warning");
    tab.classList.toggle("has-info", severity === "info");
    if (active) {
      tab.setAttribute("aria-current", "step");
    } else {
      tab.removeAttribute("aria-current");
    }
    if (counter) {
      counter.textContent = String(value);
      counter.classList.toggle("is-empty", value === 0 || value === "Setup");
    }
  }
}

function renderFiles() {
  const files = currentViewFiles();
  const paths = Object.keys(files).sort();
  const checks = currentViewChecks();
  const errorPaths = errorPathsForChecks(checks);
  const warningPaths = warningPathsForChecks(checks);
  const entryItems = fileTreeEntryItems();
  if (!paths.includes(selectedPath)) {
    selectedPath = paths[0] || "pack.mcmeta";
  }
  els.fileCount.textContent = String(paths.length);
  const signature = JSON.stringify({
    paths,
    selectedPath,
    errorPaths: [...errorPaths].sort(),
    warningPaths: [...warningPaths].sort(),
    collapsed: [...collapsedTreeFolders].sort(),
    editing,
    entryItems: entryItems.map((item) => ({
      path: item.path,
      section: item.section,
      kind: item.kind,
      index: item.index,
      title: item.title,
      severity: item.severity
    })),
    entryFormDirty
  });
  if (signature === fileTreeSignature) {
    return;
  }
  const scrollTop = els.fileTree.scrollTop;
  els.fileTree.innerHTML = renderFileTree(paths, entryItems, errorPaths, warningPaths);
  fileTreeSignature = signature;
  const maxScrollTop = Math.max(0, els.fileTree.scrollHeight - els.fileTree.clientHeight);
  els.fileTree.scrollTop = Math.min(scrollTop, maxScrollTop);
}

function resetFileTreeExpansion() {
  collapsedTreeFolders.clear();
  fileTreeSignature = "";
}

function fileTreeEntryItems() {
  const items = [];
  const addEntries = (section, kind, collection) => {
    if (!Array.isArray(collection)) return;
    collection.forEach((entry, index) => {
      const path = entryPath(section, entry, kind, index);
      items.push({
        section,
        kind,
        index,
        path,
        title: entryTreeTitle(entry, kind, index),
        detail: entryTreeDetail(entry, section, kind),
        severity: entryIssueSeverity(section, kind, entry)
      });
    });
  };
  for (const kind of DIALOGUE_KIND_KEYS) addEntries("dialogue", kind, state.dialogue[kind]);
  addEntries("forcedDialogue", "entries", state.forcedDialogue.entries);
  addEntries("notifications", "notifications", state.notifications.notifications);
  for (const kind of GIFT_KINDS.map((item) => item.key)) addEntries("gifts", kind, state.gifts[kind]);
  addEntries("pacification", "payments", state.pacification.payments);
  for (const kind of STORY_KINDS.map((item) => item.key)) addEntries("stories", kind, state.stories[kind]);
  return items;
}

function entryTreeTitle(entry, kind, index) {
  return entry.id || entry.key || entry.trigger || entry.label || entry.text || entry.item || entry.name || `${humanize(kind)} ${index + 1}`;
}

function entryTreeDetail(entry, section, kind) {
  if (section === "dialogue" && (kind === "options" || kind === "lines")) return entry.request || "";
  return entry.request || entry.type || entry.reaction || entry.world_text_kind || entry.structure || entry.biome || entry.items?.join(", ") || "";
}

function renderFileTree(paths, entryItems, errorPaths, warningPaths) {
  const root = { name: "", path: "", folders: new Map(), files: [] };
  const entriesByPath = new Map();
  for (const item of entryItems) {
    const list = entriesByPath.get(item.path) || [];
    list.push(item);
    entriesByPath.set(item.path, list);
  }
  for (const path of paths) {
    const parts = path.split("/");
    let node = root;
    for (const folder of parts.slice(0, -1)) {
      const folderPath = node.path ? `${node.path}/${folder}` : folder;
      if (!node.folders.has(folder)) {
        node.folders.set(folder, { name: folder, path: folderPath, folders: new Map(), files: [] });
      }
      node = node.folders.get(folder);
    }
    node.files.push({ name: parts.at(-1), path, entries: entriesByPath.get(path) || [] });
  }
  return renderFileTreeChildren(root, 0, errorPaths, warningPaths);
}

function renderFileTreeChildren(node, depth, errorPaths, warningPaths) {
  const folders = [...node.folders.values()].sort((a, b) => a.name.localeCompare(b.name));
  const files = node.files.sort((a, b) => a.name.localeCompare(b.name));
  return [
    ...folders.map((folder) => renderFolderNode(folder, depth, errorPaths, warningPaths)),
    ...files.map((file) => renderFileNode(file, depth, errorPaths, warningPaths))
  ].join("");
}

function renderFolderNode(folder, depth, errorPaths, warningPaths) {
  const isOpen = !collapsedTreeFolders.has(folder.path);
  return `
    <button class="tree-row tree-folder-row" type="button" style="--tree-depth: ${depth}" data-tree-folder="${escapeHtml(folder.path)}" aria-expanded="${isOpen}">
      ${icon(isOpen ? "chevron-down" : "chevron-right", "tree-chevron")}
      ${icon(isOpen ? "folder-open" : "folder", "inline-icon")}
      <span class="tree-label">${escapeHtml(folder.name)}</span>
    </button>
    ${isOpen ? renderFileTreeChildren(folder, depth + 1, errorPaths, warningPaths) : ""}
  `;
}

function renderFileNode(file, depth, errorPaths, warningPaths) {
  const entry = file.entries.length === 1 ? file.entries[0] : null;
  const entrySeverity = file.entries.reduce((severity, item) => strongestSeverity(severity, item.severity), "");
  const hasError = errorPaths.has(file.path) || entrySeverity === "error";
  const hasWarning = !hasError && (warningPaths.has(file.path) || entrySeverity === "warning");
  const isEntryActive = entry && editing?.section === entry.section && editing.kind === entry.kind && editing.index === entry.index;
  const entryAttrs = entry
    ? ` data-entry-section="${escapeHtml(entry.section)}" data-entry-kind="${escapeHtml(entry.kind)}" data-entry-index="${entry.index}"`
    : "";
  const childEntries = file.entries.length > 1
    ? file.entries.map((item) => renderEntryTreeRow(item, depth + 1)).join("")
    : "";
  return `
    <button class="tree-row tree-file-row has-tooltip ${file.path === selectedPath ? "is-active" : ""} ${isEntryActive ? "is-entry-active" : ""} ${hasError ? "has-error" : ""} ${hasWarning ? "has-warning" : ""}" type="button" style="--tree-depth: ${depth}" data-path="${escapeHtml(file.path)}"${entryAttrs} data-tooltip="${escapeHtml(file.path)}">
      <span class="tree-row-spacer"></span>
      ${icon(file.name.endsWith(".mcmeta") ? "file-cog" : "file-json", "inline-icon")}
      <span class="tree-label">${escapeHtml(file.name)}</span>
    </button>
    ${childEntries}
  `;
}

function renderEntryTreeRow(item, depth) {
  const isActive = editing?.section === item.section && editing.kind === item.kind && editing.index === item.index;
  return `
    <button class="tree-row tree-entry-row is-sortable ${isActive ? "is-entry-active" : ""} ${issueSeverityClass(item.severity)}" type="button" style="--tree-depth: ${depth}" data-entry-section="${escapeHtml(item.section)}" data-entry-kind="${escapeHtml(item.kind)}" data-entry-index="${item.index}" draggable="true">
      <span class="tree-row-spacer"></span>
      ${icon("braces", "inline-icon")}
      <span class="tree-label">${escapeHtml(item.title)}</span>
    </button>
  `;
}

function activeEntryDirectoryData() {
  if (activeSection === "dialogue") {
    const kind = DIALOGUE_KINDS.find((item) => item.key === activeDialogueKind);
    return { section: "dialogue", kind: activeDialogueKind, label: kind?.label || "Dialogue", collection: state.dialogue[activeDialogueKind] };
  }
  if (activeSection === "forcedDialogue") {
    return { section: "forcedDialogue", kind: "entries", label: "Forced Dialogue", collection: state.forcedDialogue.entries };
  }
  if (activeSection === "notifications") {
    return { section: "notifications", kind: "notifications", label: "Notifications", collection: state.notifications.notifications };
  }
  if (activeSection === "gifts") {
    const kind = GIFT_KINDS.find((item) => item.key === activeGiftKind);
    return { section: "gifts", kind: activeGiftKind, label: kind?.label || "Gifts", collection: state.gifts[activeGiftKind] };
  }
  if (activeSection === "pacification") {
    return { section: "pacification", kind: "payments", label: "Pacification", collection: state.pacification.payments };
  }
  if (activeSection === "stories") {
    const kind = STORY_KINDS.find((item) => item.key === activeStoryKind);
    return { section: "stories", kind: activeStoryKind, label: kind?.label || "Stories", collection: state.stories[activeStoryKind] };
  }
  return null;
}

function renderEntryDirectory() {
  const data = activeEntryDirectoryData();
  const collection = Array.isArray(data?.collection) ? data.collection : [];
  els.entryCount.textContent = String(collection.length);
  const signature = JSON.stringify({
    section: data?.section || "",
    kind: data?.kind || "",
    count: collection.length,
    editing,
    page: data ? entryListPages[entryListPageKey(data.section, data.kind)] || 0 : 0,
    entries: data ? collection.map((entry, index) => ({
      title: entry.id || entry.key || entry.trigger || entry.label || entry.text || entry.item || entry.name || `${humanize(data.kind)} ${index + 1}`,
      detail: data.section === "dialogue" && (data.kind === "options" || data.kind === "lines")
        ? entry.request || ""
        : entry.request || entry.type || entry.reaction || entry.world_text_kind || entry.structure || entry.biome || entry.items?.join(", ") || "",
      severity: entryIssueSeverity(data.section, data.kind, entry),
      issue: entryIssueMessage(data.section, data.kind, entry)
    })) : []
  });
  if (signature === entryDirectorySignature) return;
  const scrollTop = els.entryDirectory.querySelector(".entry-list")?.scrollTop || els.entryDirectory.scrollTop;
  if (!data) {
    els.entryDirectory.innerHTML = `<div class="empty-state">No entries for this section.</div>`;
  } else {
    els.entryDirectory.innerHTML = `<div class="entry-list" aria-label="${escapeHtml(data.label)} entries">${renderEntryList(collection, data.kind, data.section)}</div>`;
  }
  entryDirectorySignature = signature;
  const list = els.entryDirectory.querySelector(".entry-list");
  if (list) {
    const maxScrollTop = Math.max(0, list.scrollHeight - list.clientHeight);
    list.scrollTop = Math.min(scrollTop, maxScrollTop);
  }
}

function renderChecks() {
  const checks = currentViewChecks();
  const issueCount = checks.filter((check) => check.type !== "ok").length;
  const hasError = checks.some((check) => check.type === "error");
  const hasWarning = checks.some((check) => check.type === "warning");
  const hasInfo = checks.some((check) => check.type === "info");
  els.checkCount.textContent = String(issueCount);
  els.checkCount.classList.toggle("has-error", hasError);
  els.checkCount.classList.toggle("has-warning", !hasError && hasWarning);
  els.checkCount.classList.toggle("has-info", !hasError && !hasWarning && hasInfo);
  els.checkCount.classList.toggle("is-ok", !hasError && !hasWarning && !hasInfo);
  els.checks.innerHTML = checks
    .map((check, index) => {
      const details = Array.isArray(check.locations) && check.locations.length > 0
        ? `<small>${escapeHtml(check.locations.map(locationLabel).join(" | "))}</small>`
        : "";
      const tag = check.locations?.length ? "button" : "div";
      const attrs = check.locations?.length ? ` type="button" data-check-index="${index}"` : "";
      return `
      <${tag} class="check ${escapeHtml(check.type)}"${attrs}>
        ${icon(check.type === "error" ? "circle-alert" : check.type === "warning" ? "triangle-alert" : check.type === "info" ? "info" : "circle-check", "inline-icon")}
        <strong>${escapeHtml(check.title)}</strong>
        <span>${escapeHtml(check.text)}</span>
        ${details}
      </${tag}>
    `;
    })
    .join("");
}

function setActiveKindForLocation(location) {
  if (location.section === "dialogue") activeDialogueKind = location.kind;
  if (location.section === "gifts") activeGiftKind = location.kind;
  if (location.section === "stories") activeStoryKind = location.kind;
}

function pinAppViewport() {
  window.scrollTo(0, 0);
  document.documentElement.scrollTop = 0;
  document.body.scrollTop = 0;
}

function stabilizeAppViewport() {
  pinAppViewport();
  window.requestAnimationFrame(pinAppViewport);
  window.setTimeout(pinAppViewport, 50);
}

function scrollChildIntoContainer(child, container, { block = "center" } = {}) {
  if (!child || !container) return;
  const childRect = child.getBoundingClientRect();
  const containerRect = container.getBoundingClientRect();
  const currentTop = container.scrollTop;
  let nextTop = currentTop;
  if (block === "center") {
    nextTop += childRect.top - containerRect.top - (container.clientHeight / 2) + (childRect.height / 2);
  } else if (childRect.top < containerRect.top) {
    nextTop += childRect.top - containerRect.top - 10;
  } else if (childRect.bottom > containerRect.bottom) {
    nextTop += childRect.bottom - containerRect.bottom + 10;
  }
  const maxTop = Math.max(0, container.scrollHeight - container.clientHeight);
  container.scrollTop = Math.max(0, Math.min(maxTop, nextTop));
}

function scrollEntryCardIntoView(location) {
  const row = els.fileTree.querySelector(`[data-entry-section="${CSS.escape(location.section)}"][data-entry-kind="${CSS.escape(location.kind)}"][data-entry-index="${Number(location.index)}"]`);
  scrollChildIntoContainer(row, els.fileTree, { block: "nearest" });
}

function scrollFieldIntoForm(field) {
  const target = field?.closest(".field") || field?.closest(".toggle") || field;
  scrollChildIntoContainer(target, target?.closest(".entry-form"), { block: "center" });
}

function jumpToCheck(check) {
  const location = check?.locations?.[0];
  if (!location || !canLeaveEntryForm()) return;
  activeSection = location.section;
  setActiveKindForLocation(location);
  selectEntryForEditing(location.section, location.kind, location.index);
  if (location.path) selectedPath = location.path;
  render();
  window.requestAnimationFrame(() => {
    pinAppViewport();
    scrollEntryCardIntoView(location);
    const field = location.fieldId ? document.querySelector(`#${CSS.escape(location.fieldId)}`) : null;
    field?.focus({ preventScroll: true });
    scrollFieldIntoForm(field);
    stabilizeAppViewport();
  });
}

function initializePreviewEditor() {
  if (!window.VR_CODEMIRROR_READY || !els.codePreview || !els.preview) return;
  window.VR_CODEMIRROR_READY
    .then((api) => setupPreviewEditor(api))
    .catch((error) => {
      console.warn("CodeMirror preview editor failed to load; using textarea fallback.", error);
    });
}

function setupPreviewEditor(api) {
  if (previewEditor || !api?.EditorView || !api?.EditorState) return;
  previewEditorApi = api;
  const host = document.createElement("div");
  host.className = "code-preview-editor";
  els.codePreview.append(host);

  const {
    EditorState,
    EditorView,
    Compartment,
    Decoration,
    StateEffect,
    StateField,
    json,
    lineNumbers,
    highlightActiveLineGutter,
    highlightSpecialChars,
    history,
    drawSelection,
    dropCursor,
    indentOnInput,
    syntaxHighlighting,
    defaultHighlightStyle,
    bracketMatching,
    closeBrackets,
    autocompletion,
    rectangularSelection,
    crosshairCursor,
    highlightActiveLine,
    keymap,
    closeBracketsKeymap,
    defaultKeymap,
    historyKeymap,
    foldKeymap,
    completionKeymap,
    indentWithTab
  } = api;

  previewEditorWrapCompartment = new Compartment();
  previewEditorReadOnlyCompartment = new Compartment();
  previewEditorLineHighlightEffect = StateEffect.define();
  const highlightField = StateField.define({
    create() {
      return Decoration.none;
    },
    update(value, transaction) {
      for (const effect of transaction.effects) {
        if (effect.is(previewEditorLineHighlightEffect)) return effect.value;
      }
      return value.map(transaction.changes);
    },
    provide: (field) => EditorView.decorations.from(field)
  });

  const previewTheme = EditorView.theme({
    "&": {
      height: "100%",
      minHeight: "0",
      backgroundColor: "transparent",
      color: "#d8d8d8",
      fontSize: "14px"
    },
    ".cm-scroller": {
      fontFamily: "var(--font-code)",
      lineHeight: "1.55",
      overflow: "auto"
    },
    ".cm-content": {
      minHeight: "100%",
      padding: "14px 15px",
      caretColor: "#f3f3f3",
      tabSize: "2"
    },
    ".cm-line": {
      padding: "0 0"
    },
    ".cm-gutters": {
      backgroundColor: "#1b1b1b",
      borderRight: "1px solid #2b2b2b",
      color: "#6d6d6d"
    },
    ".cm-lineNumbers .cm-gutterElement": {
      padding: "0 10px 0 12px"
    },
    ".cm-activeLine": {
      backgroundColor: "rgba(255, 255, 255, 0.035)"
    },
    ".cm-activeLineGutter": {
      backgroundColor: "#262626",
      color: "#b8b8b8"
    },
    ".cm-selectionBackground, &.cm-focused .cm-selectionBackground": {
      backgroundColor: "rgba(76, 141, 43, 0.38)"
    },
    ".cm-preview-issue-line": {
      backgroundColor: "rgba(209, 106, 92, 0.18)"
    },
    ".cm-preview-entry-line": {
      backgroundColor: "rgba(76, 141, 43, 0.22)"
    },
    ".cm-preview-issue-line.cm-preview-entry-line": {
      backgroundColor: "rgba(142, 124, 65, 0.28)"
    }
  }, { dark: true });

  previewEditor = new EditorView({
    state: EditorState.create({
      doc: els.preview.value || "",
      extensions: [
        lineNumbers(),
        highlightActiveLineGutter(),
        highlightSpecialChars(),
        history(),
        drawSelection(),
        dropCursor(),
        EditorState.allowMultipleSelections.of(true),
        indentOnInput(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        bracketMatching(),
        closeBrackets(),
        autocompletion(),
        rectangularSelection(),
        crosshairCursor(),
        highlightActiveLine(),
        json(),
        keymap.of([
          indentWithTab,
          ...(closeBracketsKeymap || []),
          ...(defaultKeymap || []),
          ...(historyKeymap || []),
          ...(foldKeymap || []),
          ...(completionKeymap || [])
        ]),
        EditorState.tabSize.of(2),
        EditorView.contentAttributes.of({
          "aria-label": "Edit selected generated file",
          autocapitalize: "off",
          autocomplete: "off",
          autocorrect: "off",
          spellcheck: "false"
        }),
        EditorView.updateListener.of((update) => {
          if (!update.docChanged || isApplyingPreviewEditorValue) return;
          els.preview.value = update.state.doc.toString();
          updatePreviewHistoryButtons();
          handlePreviewEditorInput();
        }),
        previewEditorWrapCompartment.of(wrapPreviewLines ? EditorView.lineWrapping : []),
        previewEditorReadOnlyCompartment.of(previewEditorReadOnlyExtensions(false)),
        highlightField,
        previewTheme
      ]
    }),
    parent: host
  });

  els.codePreview.classList.add("is-codemirror");
  setPreviewEditorReadOnly(els.preview.readOnly);
  renderPreview();
}

function previewEditorReadOnlyExtensions(readOnly) {
  if (!previewEditorApi) return [];
  return [
    previewEditorApi.EditorState.readOnly.of(readOnly),
    previewEditorApi.EditorView.editable.of(!readOnly)
  ];
}

function previewDocumentValue() {
  return previewEditor ? previewEditor.state.doc.toString() : els.preview.value;
}

function setPreviewDocumentValue(value) {
  const text = String(value ?? "");
  if (els.preview.value !== text) els.preview.value = text;
  if (!previewEditor || previewEditor.state.doc.toString() === text) return;
  isApplyingPreviewEditorValue = true;
  previewEditor.dispatch({
    changes: { from: 0, to: previewEditor.state.doc.length, insert: text }
  });
  isApplyingPreviewEditorValue = false;
}

function setPreviewEditorReadOnly(readOnly) {
  previewEditorReadOnly = readOnly;
  els.preview.readOnly = readOnly;
  if (!previewEditor || !previewEditorReadOnlyCompartment) return;
  previewEditor.dispatch({
    effects: previewEditorReadOnlyCompartment.reconfigure(previewEditorReadOnlyExtensions(readOnly))
  });
}

function reconfigurePreviewEditorWrapping() {
  if (!previewEditor || !previewEditorWrapCompartment || !previewEditorApi) return;
  previewEditor.dispatch({
    effects: previewEditorWrapCompartment.reconfigure(wrapPreviewLines ? previewEditorApi.EditorView.lineWrapping : [])
  });
}

function focusPreviewEditor() {
  if (previewEditor) {
    previewEditor.focus();
  } else {
    els.preview.focus();
  }
}

function handlePreviewEditorInput() {
  recordPreviewInputHistory();
  renderPreviewLineNumbers(previewDocumentValue(), previewEditError?.path === selectedPath ? [] : previewLineHighlightRanges);
  window.clearTimeout(previewEditTimer);
  previewEditTimer = window.setTimeout(applyPreviewEdit, 180);
}

function renderPreview() {
  const files = currentViewFiles();
  const value = files[selectedPath];
  const hasValidationIssue = errorPathsForChecks(currentViewChecks()).has(selectedPath);
  const hasInvalidJson = previewEditError?.path === selectedPath;
  els.selectedPath.textContent = entryFormDirty ? `${selectedPath} (unsaved)` : selectedPath;
  els.codePreview.classList.toggle("is-wrapped", wrapPreviewLines);
  els.codePreview.classList.toggle("is-invalid", hasInvalidJson);
  els.preview.closest(".preview")?.classList.toggle("has-error", hasValidationIssue || hasInvalidJson);
  els.wrapPreviewButton.classList.toggle("is-on", wrapPreviewLines);
  els.wrapPreviewButton.setAttribute("aria-pressed", String(wrapPreviewLines));
  els.wrapPreviewButton.setAttribute("data-tooltip", wrapPreviewLines ? "Keep preview lines unwrapped." : "Wrap preview lines.");
  reconfigurePreviewEditorWrapping();
  if (value instanceof Uint8Array) {
    setPreviewDocumentValue(`Binary file preserved (${value.byteLength} bytes).`);
    setPreviewEditorReadOnly(true);
    resetPreviewHistory(selectedPath);
    previewLineHighlightRanges = [];
    applyPreviewLineHighlights([]);
    renderPreviewLineNumbers(previewDocumentValue(), []);
    pendingPreviewEntryScroll = false;
  } else {
    setPreviewEditorReadOnly(false);
    setPreviewDocumentValue(value || "");
    resetPreviewHistory(selectedPath);
    const source = previewDocumentValue();
    const issueRanges = hasInvalidJson ? [] : withDraftState(() => previewIssueLineRanges(selectedPath, source));
    const entryRanges = hasInvalidJson ? [] : previewSelectedEntryLineRanges(selectedPath, source);
    const ranges = mergeLineRanges([...issueRanges, ...entryRanges]);
    previewLineHighlightRanges = ranges;
    applyPreviewLineHighlights(issueRanges, entryRanges);
    renderPreviewLineNumbers(source, ranges);
    if (pendingPreviewEntryScroll) {
      pendingPreviewEntryScroll = false;
      if (entryRanges.length > 0) {
        window.requestAnimationFrame(() => scrollPreviewToLineRange(entryRanges[0]));
      }
    }
  }
  updatePreviewHistoryButtons();
}

function handlePreviewEditorKeydown(event) {
  if (els.preview.readOnly || event.defaultPrevented) return;
  const key = event.key.toLowerCase();
  const modifier = event.ctrlKey || event.metaKey;
  if (modifier && !event.altKey && key === "z") {
    event.preventDefault();
    if (event.shiftKey) {
      redoPreviewEdit();
    } else {
      undoPreviewEdit();
    }
    return;
  }
  if (modifier && !event.altKey && key === "y") {
    event.preventDefault();
    redoPreviewEdit();
    return;
  }
  if (event.key === "Tab") {
    event.preventDefault();
    if (event.shiftKey) {
      unindentPreviewSelection();
    } else {
      indentPreviewSelection();
    }
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    insertPreviewNewline();
    return;
  }
  if (!event.ctrlKey && !event.altKey && !event.metaKey) {
    handlePreviewPairKey(event);
  }
}

function indentPreviewSelection() {
  const textarea = els.preview;
  const value = textarea.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  if (start === end) {
    replacePreviewSelection("  ", start + 2, start + 2);
    return;
  }
  const lineStart = value.lastIndexOf("\n", start - 1) + 1;
  const selectionEnd = end > start && value[end - 1] === "\n" ? end - 1 : end;
  const lineEnd = value.indexOf("\n", selectionEnd);
  const blockEnd = lineEnd === -1 ? value.length : lineEnd;
  const block = value.slice(lineStart, blockEnd);
  const indented = block.split("\n").map((line) => `  ${line}`).join("\n");
  replacePreviewRange(lineStart, blockEnd, indented, start + 2, end + (indented.length - block.length));
}

function unindentPreviewSelection() {
  const textarea = els.preview;
  const value = textarea.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const lineStart = value.lastIndexOf("\n", start - 1) + 1;
  const selectionEnd = end > start && value[end - 1] === "\n" ? end - 1 : end;
  const lineEnd = value.indexOf("\n", selectionEnd);
  const blockEnd = lineEnd === -1 ? value.length : lineEnd;
  const block = value.slice(lineStart, blockEnd);
  let firstLineRemoved = 0;
  let removedBeforeEnd = 0;
  let offset = lineStart;
  const unindented = block.split("\n").map((line, index) => {
    const removed = line.startsWith("  ") ? 2 : line.startsWith("\t") || line.startsWith(" ") ? 1 : 0;
    if (index === 0) firstLineRemoved = removed;
    if (offset < end) removedBeforeEnd += removed;
    offset += line.length + 1;
    return line.slice(removed);
  }).join("\n");
  replacePreviewRange(lineStart, blockEnd, unindented, Math.max(lineStart, start - firstLineRemoved), Math.max(lineStart, end - removedBeforeEnd));
}

function insertPreviewNewline() {
  const textarea = els.preview;
  const value = textarea.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const lineStart = value.lastIndexOf("\n", start - 1) + 1;
  const linePrefix = value.slice(lineStart, start);
  const indent = linePrefix.match(/^\s*/)?.[0] || "";
  const before = value.slice(lineStart, start).trimEnd();
  const after = value.slice(end).trimStart();
  const opensBlock = /[\[{]$/.test(before);
  const closesBlock = /^[\]}]/.test(after);
  if (opensBlock && closesBlock) {
    const innerIndent = `${indent}  `;
    const inserted = `\n${innerIndent}\n${indent}`;
    replacePreviewSelection(inserted, start + 1 + innerIndent.length, start + 1 + innerIndent.length);
    return;
  }
  const nextIndent = opensBlock ? `${indent}  ` : indent;
  const inserted = `\n${nextIndent}`;
  replacePreviewSelection(inserted, start + inserted.length, start + inserted.length);
}

function handlePreviewPairKey(event) {
  const pairs = {
    "{": "}",
    "[": "]",
    "\"": "\""
  };
  const closers = new Set(Object.values(pairs));
  const opener = event.key;
  const closer = pairs[opener];
  const textarea = els.preview;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  if (closers.has(event.key) && start === end && textarea.value[start] === event.key) {
    event.preventDefault();
    textarea.setSelectionRange(start + 1, start + 1);
    return;
  }
  if (!closer) return;
  event.preventDefault();
  const selected = textarea.value.slice(start, end);
  const inserted = `${opener}${selected}${closer}`;
  const nextStart = selected ? start + 1 : start + 1;
  const nextEnd = selected ? end + 1 : start + 1;
  replacePreviewSelection(inserted, nextStart, nextEnd);
}

function replacePreviewSelection(text, selectionStart, selectionEnd) {
  replacePreviewRange(els.preview.selectionStart, els.preview.selectionEnd, text, selectionStart, selectionEnd);
}

function replacePreviewRange(start, end, text, selectionStart, selectionEnd) {
  const textarea = els.preview;
  const before = previewSnapshot();
  textarea.setRangeText(text, start, end, "preserve");
  textarea.setSelectionRange(selectionStart, selectionEnd);
  pushPreviewUndoSnapshot(before);
  textarea.dispatchEvent(new Event("input", { bubbles: true }));
}

function resetPreviewHistory(path = selectedPath) {
  if (previewHistoryPath === path) return;
  previewHistoryPath = path;
  previewUndoStack = [];
  previewRedoStack = [];
  previewBeforeInputSnapshot = null;
  updatePreviewHistoryButtons();
}

function previewSnapshot() {
  return {
    value: els.preview.value,
    start: els.preview.selectionStart,
    end: els.preview.selectionEnd
  };
}

function pushPreviewUndoSnapshot(snapshot) {
  if (isApplyingPreviewHistory || !snapshot || snapshot.value === els.preview.value) return;
  previewUndoStack.push(snapshot);
  if (previewUndoStack.length > 100) previewUndoStack.shift();
  previewRedoStack = [];
  updatePreviewHistoryButtons();
}

function restorePreviewSnapshot(snapshot) {
  if (!snapshot) return;
  isApplyingPreviewHistory = true;
  els.preview.value = snapshot.value;
  els.preview.setSelectionRange(snapshot.start, snapshot.end);
  els.preview.dispatchEvent(new Event("input", { bubbles: true }));
  isApplyingPreviewHistory = false;
  updatePreviewHistoryButtons();
}

function undoPreviewEdit() {
  if (previewEditor && previewEditorApi?.undo) {
    previewEditorApi.undo(previewEditor);
    updatePreviewHistoryButtons();
    return;
  }
  const snapshot = previewUndoStack.pop();
  if (!snapshot) return;
  previewRedoStack.push(previewSnapshot());
  restorePreviewSnapshot(snapshot);
}

function redoPreviewEdit() {
  if (previewEditor && previewEditorApi?.redo) {
    previewEditorApi.redo(previewEditor);
    updatePreviewHistoryButtons();
    return;
  }
  const snapshot = previewRedoStack.pop();
  if (!snapshot) return;
  previewUndoStack.push(previewSnapshot());
  restorePreviewSnapshot(snapshot);
}

function updatePreviewHistoryButtons() {
  const canUndo = previewEditor
    ? !previewEditorReadOnly && previewEditorApi?.undoDepth?.(previewEditor.state) > 0
    : !els.preview.readOnly && previewUndoStack.length > 0;
  const canRedo = previewEditor
    ? !previewEditorReadOnly && previewEditorApi?.redoDepth?.(previewEditor.state) > 0
    : !els.preview.readOnly && previewRedoStack.length > 0;
  els.undoPreviewButton.disabled = !canUndo;
  els.redoPreviewButton.disabled = !canRedo;
  els.undoPreviewButton.setAttribute("aria-disabled", String(!canUndo));
  els.redoPreviewButton.setAttribute("aria-disabled", String(!canRedo));
  els.undoPreviewButton.setAttribute("data-tooltip", canUndo ? "Undo preview edit." : "Nothing to undo.");
  els.redoPreviewButton.setAttribute("data-tooltip", canRedo ? "Redo preview edit." : "Nothing to redo.");
}

function recordPreviewBeforeInput(event) {
  if (els.preview.readOnly || isApplyingPreviewHistory) return;
  if (event.inputType === "historyUndo" || event.inputType === "historyRedo") return;
  previewBeforeInputSnapshot = previewSnapshot();
}

function recordPreviewInputHistory() {
  if (isApplyingPreviewHistory) return;
  if (!previewBeforeInputSnapshot) return;
  pushPreviewUndoSnapshot(previewBeforeInputSnapshot);
  previewBeforeInputSnapshot = null;
}

function renderPreviewLineNumbers(source, ranges = []) {
  if (previewEditor) {
    els.previewLines.replaceChildren();
    els.previewLines.style.transform = "";
    return;
  }
  const style = getComputedStyle(els.preview);
  const text = String(source || "");
  const lineState = previewLineNumberMetrics(text, ranges);
  if (wrapPreviewLines && lineState.lineCount <= PREVIEW_EXACT_WRAP_LINE_LIMIT) {
    const wrappedRows = previewWrappedRowCounts(text.split("\n"), style);
    const lineHeight = previewRowHeight(text, style, wrappedRows);
    renderExactWrappedPreviewLineNumbers(text, ranges, lineHeight, lineState.lineCount, wrappedRows);
    return;
  }
  const lineHeight = previewRowHeight(text, style);
  const highlighted = previewHighlightedLines(ranges);
  const viewportHeight = els.preview.clientHeight || 0;
  const scrollTop = els.preview.scrollTop || 0;
  const overscan = 18;
  const firstVisible = Math.min(lineState.lineCount, Math.max(1, Math.floor(scrollTop / lineHeight) + 1 - overscan));
  const visibleRows = Math.max(1, Math.ceil(viewportHeight / lineHeight) + overscan * 2);
  const lastVisible = Math.min(lineState.lineCount, firstVisible + visibleRows);
  const fragment = document.createDocumentFragment();
  for (let lineNumber = firstVisible; lineNumber <= lastVisible; lineNumber += 1) {
    const node = document.createElement("div");
    node.className = "code-preview-line-number";
    if (highlighted.has(lineNumber)) node.classList.add("is-highlighted");
    node.textContent = String(lineNumber);
    fragment.append(node);
  }
  els.codePreview.style.setProperty("--preview-line-height", `${lineHeight}px`);
  els.codePreview.style.setProperty("--preview-gutter-digits", String(Math.max(2, String(lineState.lineCount).length)));
  els.previewLines.style.transform = `translateY(${(firstVisible - 1) * lineHeight - scrollTop}px)`;
  els.previewLines.replaceChildren(fragment);
}

function renderExactWrappedPreviewLineNumbers(source, ranges, lineHeight, lineCount, wrappedRows) {
  const lines = source.split("\n");
  const highlighted = previewHighlightedLines(ranges);
  const fragment = document.createDocumentFragment();
  lines.forEach((_, index) => {
    const lineNumber = index + 1;
    const node = document.createElement("div");
    node.className = "code-preview-line-number";
    if (highlighted.has(lineNumber)) node.classList.add("is-highlighted");
    node.textContent = String(lineNumber);
    node.style.height = `${wrappedRows[index] * lineHeight}px`;
    fragment.append(node);
  });
  els.codePreview.style.setProperty("--preview-line-height", `${lineHeight}px`);
  els.codePreview.style.setProperty("--preview-gutter-digits", String(Math.max(2, String(lineCount).length)));
  els.previewLines.style.transform = `translateY(${-els.preview.scrollTop}px)`;
  els.previewLines.replaceChildren(fragment);
}

function previewRowHeight(source, style = getComputedStyle(els.preview), wrappedRows = null) {
  const fallback = parseFloat(style.lineHeight) || 21.7;
  const paddingTop = parseFloat(style.paddingTop) || 0;
  const paddingBottom = parseFloat(style.paddingBottom) || 0;
  const rows = wrappedRows
    ? wrappedRows.reduce((total, count) => total + count, 0)
    : Math.max(1, previewLineNumberMetrics(source).lineCount);
  const measured = (els.preview.scrollHeight - paddingTop - paddingBottom) / Math.max(1, rows);
  const nearExpected = Math.abs(measured - fallback) / fallback < 0.25;
  return Number.isFinite(measured) && measured > 0 && nearExpected ? measured : fallback;
}

function previewWrappedRowCounts(lines, style) {
  const previewWidth = els.preview.clientWidth;
  if (!previewWidth) return lines.map(() => 1);
  const paddingLeft = parseFloat(style.paddingLeft) || 0;
  const paddingRight = parseFloat(style.paddingRight) || 0;
  const availableWidth = previewWidth - paddingLeft - paddingRight;
  if (availableWidth <= 0) return lines.map(() => 1);
  const context = document.createElement("canvas").getContext("2d");
  if (!context) return lines.map(() => 1);
  context.font = style.font || `${style.fontSize} ${style.fontFamily}`;
  const characterWidth = context.measureText("0").width || (parseFloat(style.fontSize) || 14) * 0.6;
  const columns = Math.max(1, Math.floor(availableWidth / characterWidth));
  const tabSize = Math.max(1, Number.parseInt(style.tabSize, 10) || 2);
  return lines.map((line) => {
    if (!line.length) return 1;
    let columnsUsed = 0;
    for (const character of line) {
      if (character === "\t") {
        const remainder = columnsUsed % tabSize;
        columnsUsed += remainder === 0 ? tabSize : tabSize - remainder;
      } else {
        columnsUsed += 1;
      }
    }
    return Math.max(1, Math.ceil(columnsUsed / columns));
  });
}

function previewLineNumberMetrics(source, ranges = []) {
  const rangesKey = ranges.map((range) => `${range.start}:${range.end || range.start}`).join("|");
  if (previewLineNumberState.source === source && previewLineNumberState.rangesKey === rangesKey) {
    return previewLineNumberState;
  }
  let lineCount = 1;
  for (let index = 0; index < source.length; index += 1) {
    if (source.charCodeAt(index) === 10) lineCount += 1;
  }
  previewLineNumberState = {
    source,
    lineCount,
    rangesKey
  };
  return previewLineNumberState;
}

function previewHighlightedLines(ranges) {
  const lines = new Set();
  for (const range of ranges) {
    const start = Math.max(1, range.start || 1);
    const end = Math.max(start, range.end || start);
    for (let line = start; line <= end; line += 1) {
      lines.add(line);
    }
  }
  return lines;
}

function syncPreviewLineNumberScroll() {
  schedulePreviewLineNumberRender();
}

function schedulePreviewLineNumberRender() {
  window.cancelAnimationFrame(previewLineNumberFrame);
  previewLineNumberFrame = window.requestAnimationFrame(() => {
    renderPreviewLineNumbers(previewDocumentValue(), previewEditError?.path === selectedPath ? [] : previewLineHighlightRanges);
  });
}

function previewLineOffsets(source, style = getComputedStyle(els.preview)) {
  const text = String(source || "");
  const lines = text.split("\n");
  const wrappedRows = wrapPreviewLines && lines.length <= PREVIEW_EXACT_WRAP_LINE_LIMIT
    ? previewWrappedRowCounts(lines, style)
    : null;
  const lineHeight = previewRowHeight(text, style, wrappedRows);
  const offsets = [0];
  for (let index = 0; index < lines.length; index += 1) {
    offsets.push(offsets[index] + (wrappedRows ? wrappedRows[index] : 1) * lineHeight);
  }
  return { offsets, lineHeight };
}

function previewLineHighlightBackgrounds(ranges, color, source, style, lineOffsets = null) {
  const offsetState = lineOffsets || previewLineOffsets(source, style);
  const paddingTop = parseFloat(style.paddingTop) || 0;
  return ranges.slice(0, 12).map((range) => {
    const start = Math.max(1, range.start);
    const end = Math.max(start, range.end || start);
    const top = paddingTop + (offsetState.offsets[start - 1] ?? ((start - 1) * offsetState.lineHeight));
    const bottom = paddingTop + (offsetState.offsets[end] ?? (end * offsetState.lineHeight));
    return `linear-gradient(to bottom, transparent 0, transparent ${top}px, ${color} ${top}px, ${color} ${bottom}px, transparent ${bottom}px)`;
  });
}

function applyPreviewLineHighlights(issueRanges, entryRanges = []) {
  if (previewEditor) {
    applyPreviewEditorLineHighlights(issueRanges, entryRanges);
    return;
  }
  const style = getComputedStyle(els.preview);
  const lineOffsets = previewLineOffsets(els.preview.value, style);
  const backgrounds = [
    ...previewLineHighlightBackgrounds(issueRanges, "rgba(209, 106, 92, 0.18)", els.preview.value, style, lineOffsets),
    ...previewLineHighlightBackgrounds(entryRanges, "rgba(76, 141, 43, 0.22)", els.preview.value, style, lineOffsets)
  ];
  els.codePreview.classList.toggle("has-line-highlights", backgrounds.length > 0);
  els.preview.style.backgroundImage = backgrounds.join(", ");
}

function applyPreviewEditorLineHighlights(issueRanges, entryRanges = []) {
  if (!previewEditor || !previewEditorApi?.Decoration || !previewEditorLineHighlightEffect) return;
  const doc = previewEditor.state.doc;
  const decorations = [];
  const addRanges = (ranges, className) => {
    for (const range of ranges.slice(0, 12)) {
      const start = clamp(Math.max(1, range.start || 1), 1, doc.lines);
      const end = clamp(Math.max(start, range.end || start), 1, doc.lines);
      for (let lineNumber = start; lineNumber <= end; lineNumber += 1) {
        decorations.push(previewEditorApi.Decoration.line({ class: className }).range(doc.line(lineNumber).from));
      }
    }
  };
  addRanges(issueRanges, "cm-preview-issue-line");
  addRanges(entryRanges, "cm-preview-entry-line");
  els.codePreview.classList.toggle("has-line-highlights", decorations.length > 0);
  previewEditor.dispatch({
    effects: previewEditorLineHighlightEffect.of(previewEditorApi.Decoration.set(decorations, true))
  });
}

function scrollPreviewToLineRange(range) {
  if (!range || (previewEditor ? previewEditorReadOnly : els.preview.readOnly)) return;
  if (previewEditor && previewEditorApi?.EditorView) {
    const doc = previewEditor.state.doc;
    const lineNumber = clamp(Math.max(1, range.start || 1), 1, doc.lines);
    const line = doc.line(lineNumber);
    previewEditor.dispatch({
      effects: previewEditorApi.EditorView.scrollIntoView(line.from, {
        y: "start",
        yMargin: Math.round(previewEditor.scrollDOM.clientHeight * 0.22)
      })
    });
    stabilizeAppViewport();
    return;
  }
  const style = getComputedStyle(els.preview);
  const paddingTop = parseFloat(style.paddingTop) || 0;
  const lineOffsets = previewLineOffsets(els.preview.value, style);
  const top = paddingTop + (lineOffsets.offsets[Math.max(1, range.start) - 1] ?? 0);
  const target = Math.max(0, top - (els.preview.clientHeight * 0.22));
  els.preview.scrollTop = target;
  renderPreviewLineNumbers(previewDocumentValue(), previewLineHighlightRanges);
  stabilizeAppViewport();
}

function previewIssueLineRanges(path, source) {
  const ranges = [];
  for (const { section, kind, entry } of previewIssueEntries(path)) {
    const detail = entryIssueDetail(section, kind, entry);
    if (!detail) continue;
    ranges.push(...findIssueLineRanges(source, entry, detail, section, kind));
  }
  return mergeLineRanges(ranges);
}

function previewSelectedEntryLineRanges(path, source) {
  if (!editing) return [];
  const entry = currentEditingEntry();
  if (!entry || entryPath(editing.section, entry, editing.kind, editing.index) !== path) return [];
  const range = findEntryLineRange(source, entry);
  return range ? [range] : [];
}

function previewIssueEntries(path) {
  if (/^data\/villagerretaliation\/dialogue\/[^/]+\/.+\.json$/.test(path)) {
    return ["options", "lines", "messages", "openings", "closings", "pacify"].flatMap((kind) => (
      state.dialogue[kind]
        .map((entry, index) => ({ entry, index }))
        .filter(({ entry, index }) => (entry.__sourcePath || dialoguePath(kind, entry, index)) === path)
        .map(({ entry }) => ({ section: "dialogue", kind, entry }))
    ));
  }
  if (/^data\/villagerretaliation\/forced_dialogue\/.+\.json$/.test(path)) {
    return state.forcedDialogue.entries
      .filter((entry) => (entry.__sourcePath || forcedDialoguePath()) === path)
      .map((entry) => ({ section: "forcedDialogue", kind: "entries", entry }));
  }
  if (path === notificationsPath()) {
    return state.notifications.notifications.map((entry) => ({ section: "notifications", kind: "notifications", entry }));
  }
  if (path === giftsPath()) {
    return [
      ...state.gifts.preferences.map((entry) => ({ section: "gifts", kind: "preferences", entry })),
      ...state.gifts.rewards.map((entry) => ({ section: "gifts", kind: "rewards", entry }))
    ];
  }
  if (path === pacificationPath()) {
    return state.pacification.payments.map((entry) => ({ section: "pacification", kind: "payments", entry }));
  }
  if (path === structurePath()) {
    return state.stories.structures.map((entry) => ({ section: "stories", kind: "structures", entry }));
  }
  if (path === biomePath()) {
    return state.stories.biomes.map((entry) => ({ section: "stories", kind: "biomes", entry }));
  }
  return [];
}

function findIssueLineRanges(source, entry, detail, section, kind) {
  const entryRange = findEntryLineRange(source, entry);
  if (!entryRange) return [];
  const keys = unique(detail.fieldIds.flatMap((fieldId) => jsonKeysForFieldId(fieldId, section, kind)));
  const ranges = keys.flatMap((key) => findPropertyLineRanges(source, entryRange, key));
  return ranges.length > 0 ? ranges : [{ start: entryRange.start, end: entryRange.start }];
}

function findEntryLineRange(source, entry) {
  const sourceLines = source.split(/\r?\n/);
  const entryLines = JSON.stringify(cleanObject(entry), null, 2).split(/\r?\n/);
  const normalize = (line) => line.trim().replace(/,$/, "");
  for (let index = 0; index <= sourceLines.length - entryLines.length; index++) {
    const matches = entryLines.every((line, offset) => normalize(sourceLines[index + offset]) === normalize(line));
    if (matches) {
      return { start: index + 1, end: index + entryLines.length };
    }
  }
  return null;
}

function findPropertyLineRanges(source, entryRange, key) {
  const sourceLines = source.split(/\r?\n/);
  const ranges = [];
  const propertyPattern = new RegExp(`^\\s*"${escapeRegExp(key)}"\\s*:`);
  for (let index = entryRange.start - 1; index < entryRange.end; index++) {
    if (!propertyPattern.test(sourceLines[index] || "")) continue;
    ranges.push({ start: index + 1, end: propertyEndLine(sourceLines, index, entryRange.end - 1) + 1 });
  }
  return ranges;
}

function propertyEndLine(lines, startIndex, maxIndex) {
  const line = lines[startIndex] || "";
  const valueStart = line.indexOf(":") + 1;
  const tail = line.slice(valueStart).trim();
  if (!tail.startsWith("[") && !tail.startsWith("{")) return startIndex;
  const opener = tail[0];
  const closer = opener === "[" ? "]" : "}";
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = startIndex; index <= maxIndex; index++) {
    const scan = index === startIndex ? lines[index].slice(valueStart) : lines[index];
    for (const char of scan) {
      if (escaped) {
        escaped = false;
        continue;
      }
      if (char === "\\") {
        escaped = inString;
        continue;
      }
      if (char === '"') {
        inString = !inString;
        continue;
      }
      if (inString) continue;
      if (char === opener) depth++;
      if (char === closer) depth--;
      if (depth === 0) return index;
    }
  }
  return startIndex;
}

function jsonKeysForFieldId(fieldId, section, kind) {
  const exact = {
    "dialogue-option": ["option", "option_ids"],
    "dialogue-story_structure": ["story_structure", "story_structures"],
    "dialogue-story_biome": ["story_biome", "story_biomes"],
    "dialogue-text": ["text", "lines"],
    "dialogue-give_items": ["give_items", "take_items", "payment"],
    "forced-line": ["line", "lines"],
    "forced-options_json": ["options"],
    "forced-leave_option_json": ["leave_option", "leave_options"],
    "notification-text": ["text", "lines"],
    "gift-items": ["items", "item"],
    "gift-tags": ["tags", "tag"],
    "pacification-items": ["items", "item"],
    "pacification-tags": ["tags", "tag"],
    "story-structures": ["structures", "structure"],
    "story-biomes": ["biomes", "biome"]
  };
  if (exact[fieldId]) return exact[fieldId];
  const prefix = `${fieldPrefixForSection(section, kind)}-`;
  return fieldId.startsWith(prefix) ? [fieldId.slice(prefix.length)] : [];
}

function fieldPrefixForSection(section, kind) {
  if (section === "forcedDialogue") return "forced";
  if (section === "notifications") return "notification";
  if (section === "pacification") return "pacification";
  if (section === "stories") return "story";
  if (section === "gifts") return "gift";
  if (section === "dialogue") return "dialogue";
  return kind || section;
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function mergeLineRanges(ranges) {
  const sorted = ranges
    .filter(Boolean)
    .sort((a, b) => a.start - b.start || a.end - b.end);
  const merged = [];
  for (const range of sorted) {
    const last = merged[merged.length - 1];
    if (last && range.start <= last.end + 1) {
      last.end = Math.max(last.end, range.end);
    } else {
      merged.push({ ...range });
    }
  }
  return merged;
}

function renderPanel() {
  if (activeSection === "overview") renderOverview();
  if (activeSection === "dialogue") renderDialogue();
  if (activeSection === "forcedDialogue") renderForcedDialogue();
  if (activeSection === "notifications") renderNotifications();
  if (activeSection === "gifts") renderGifts();
  if (activeSection === "pacification") renderPacification();
  if (activeSection === "stories") renderStories();
  if (activeSection === "names") renderNames();
}

function renderPreservingEntryListScroll() {
  const scrollState = { top: els.fileTree.scrollTop, left: els.fileTree.scrollLeft };
  render();
  restoreEntryListScroll(scrollState);
  pinAppViewport();
  window.requestAnimationFrame(() => {
    restoreEntryListScroll(scrollState);
    pinAppViewport();
  });
  window.setTimeout(pinAppViewport, 50);
}

function restoreEntryListScroll(scrollState) {
  if (!scrollState) return;
  els.fileTree.scrollTop = Math.min(scrollState.top, Math.max(0, els.fileTree.scrollHeight - els.fileTree.clientHeight));
  els.fileTree.scrollLeft = Math.min(scrollState.left, Math.max(0, els.fileTree.scrollWidth - els.fileTree.clientWidth));
}

function resizeTextareas(root = document) {
  for (const textarea of root.querySelectorAll(".entry-form textarea")) {
    textarea.style.height = "42px";
    textarea.style.height = `${Math.max(42, textarea.scrollHeight)}px`;
  }
}

function syncValueTags(root = document) {
  for (const button of root.querySelectorAll(".value-tag")) {
    const input = document.querySelector(`#${CSS.escape(button.dataset.target)}`);
    const isAdded = input ? parseList(input.value).includes(button.dataset.value) : false;
    button.classList.toggle("is-added", isAdded);
    button.disabled = isAdded;
    button.setAttribute("aria-disabled", String(isAdded));
  }
}

function currentEditingEntry() {
  if (!editing) return null;
  const collection = state[editing.section]?.[editing.kind];
  return Array.isArray(collection) ? collection[editing.index] : null;
}

function applyEntryIssueHighlights() {
  els.panel.querySelectorAll(".field.has-error, .field.has-warning, .field.has-info").forEach((fieldNode) => {
    fieldNode.classList.remove("has-error", "has-warning", "has-info");
    fieldNode.querySelector(".field-issue")?.remove();
  });
  els.panel.querySelectorAll(".toggle.has-error, .toggle.has-warning, .toggle.has-info").forEach((toggleNode) => {
    toggleNode.classList.remove("has-error", "has-warning", "has-info");
  });
  const entry = currentEditingEntry();
  if (!entry || !editing) return;
  const issue = entryIssueDetail(editing.section, editing.kind, entry);
  if (!issue || issue.fieldIds.length === 0) return;
  const className = issueSeverityClass(issue.severity) || "has-error";
  for (const fieldId of issue.fieldIds) {
    const control = els.panel.querySelector(`#${CSS.escape(fieldId)}`);
    const target = control?.closest(".field") || control?.closest(".toggle");
    if (!target) continue;
    target.classList.add(className);
    if (target.classList.contains("field") && !target.querySelector(".field-issue")) {
      target.insertAdjacentHTML("beforeend", `<small class="field-issue">${escapeHtml(issue.message)}</small>`);
    }
  }
}

function field({ id, label, value = "", type = "text", help = "", className = "", attrs = "" }) {
  const tooltip = tooltipForField(id, help);
  return `
    <div class="field ${className}">
      <label for="${id}"${tooltipAttrs(tooltip)}>${escapeHtml(label)}</label>
      <input id="${id}" name="${id}" type="${type}" value="${escapeHtml(value)}" ${attrs}>
      ${help ? `<small>${escapeHtml(help)}</small>` : ""}
    </div>
  `;
}

function textareaField({ id, label, value = "", help = "", className = "", rows = 3 }) {
  const tooltip = tooltipForField(id, help);
  return `
    <div class="field ${className}">
      <label for="${id}"${tooltipAttrs(tooltip)}>${escapeHtml(label)}</label>
      <textarea id="${id}" name="${id}" rows="${rows}">${escapeHtml(value)}</textarea>
      ${help ? `<small>${escapeHtml(help)}</small>` : ""}
    </div>
  `;
}

function selectField({ id, label, value = "", options, help = "", className = "", allowBlank = true, multiple = false }) {
  const tooltip = tooltipForField(id, help);
  const selected = Array.isArray(value) ? value : parseList(value);
  const renderedOptions = [
    allowBlank && !multiple ? `<option value=""></option>` : "",
    ...options.map((option) => {
      const optionValue = typeof option === "object" ? option.value : option;
      const optionLabel = typeof option === "object" ? option.label : humanize(option);
      const isSelected = multiple ? selected.includes(optionValue) : value === optionValue;
      return `<option value="${escapeHtml(optionValue)}" ${isSelected ? "selected" : ""}>${escapeHtml(optionLabel)}</option>`;
    })
  ].join("");
  return `
    <div class="field ${className}">
      <label for="${id}"${tooltipAttrs(tooltip)}>${escapeHtml(label)}</label>
      <select id="${id}" name="${id}" ${multiple ? "multiple" : ""}>${renderedOptions}</select>
      ${help ? `<small>${escapeHtml(help)}</small>` : ""}
    </div>
  `;
}

function listField({ id, label, value = [], help = "", className = "" }) {
  const tags = TAG_SUGGESTIONS[id] || [];
  return textareaField({
    id,
    label,
    value: listToText(value),
    help,
    className,
    rows: 2
  }).replace("</div>", `${renderValueTags(id, tags)}</div>`);
}

function playerItemDurabilityFields(prefix, entry) {
  return `
    ${field({ id: `${prefix}-min_player_item_durability`, label: "Minimum item durability", value: entry.min_player_item_durability ?? entry.min_held_item_durability ?? "", type: "number", attrs: 'min="0" step="1"' })}
    ${field({ id: `${prefix}-max_player_item_durability`, label: "Maximum item durability", value: entry.max_player_item_durability ?? entry.max_held_item_durability ?? "", type: "number", attrs: 'min="0" step="1"' })}
    ${field({ id: `${prefix}-min_player_item_durability_percent`, label: "Minimum item durability %", value: entry.min_player_item_durability_percent ?? entry.min_held_item_durability_percent ?? "", type: "number", attrs: 'min="0" max="100" step="1"' })}
    ${field({ id: `${prefix}-max_player_item_durability_percent`, label: "Maximum item durability %", value: entry.max_player_item_durability_percent ?? entry.max_held_item_durability_percent ?? "", type: "number", attrs: 'min="0" max="100" step="1"' })}
  `;
}

function playerItemEnchantmentFields(prefix, entry) {
  return `
    ${listField({ id: `${prefix}-player_item_enchantments`, label: "Item enchantments", value: entry.player_item_enchantments ?? entry.player_item_enchantment ?? entry.held_item_enchantments ?? entry.held_item_enchantment, help: "Use ids like minecraft:sharpness. Raw JSON can use objects with id, min_level, and max_level." })}
    ${field({ id: `${prefix}-min_player_item_enchantment_level`, label: "Minimum enchantment level", value: entry.min_player_item_enchantment_level ?? entry.min_held_item_enchantment_level ?? "", type: "number", attrs: 'min="1" step="1"' })}
    ${field({ id: `${prefix}-max_player_item_enchantment_level`, label: "Maximum enchantment level", value: entry.max_player_item_enchantment_level ?? entry.max_held_item_enchantment_level ?? "", type: "number", attrs: 'min="1" step="1"' })}
  `;
}

function beta12DialogueLineFilters(entry) {
  if (!supportsBeta12DialogueFields()) return "";
  return `
    ${listField({ id: "dialogue-moods", label: "Temporary moods", value: entry.moods ?? entry.mood, help: "Beta.12 only. Blank means any current mood." })}
    ${field({ id: "dialogue-min_mood_intensity", label: "Minimum mood intensity", value: entry.min_mood_intensity ?? "", type: "number", attrs: 'min="0" max="100" step="1"' })}
    ${socialAttributeHighToggles(entry)}
    ${socialAttributeRangeFields(entry)}
  `;
}

function dialogueConditionsField(entry) {
  if (!supportsBeta12DialogueFields()) return "";
  return textareaField({
    id: "dialogue-conditions",
    label: "Conditions JSON",
    value: prettyJson(entry.conditions),
    help: "Optional. JSON array of condition blocks for compound matching.",
    className: "full",
    rows: 6
  });
}

function beta12DialogueLineMetadataFields(entry) {
  if (!supportsBeta12DialogueFields()) return "";
  return `
    ${field({ id: "dialogue-text_key", label: "Text key", value: entry.text_key ?? "" })}
    ${field({ id: "dialogue-priority", label: "Priority", value: entry.priority ?? "", type: "number" })}
    ${field({ id: "dialogue-category", label: "Category", value: entry.category ?? "" })}
  `;
}

function dialogueNarrativeMetadataFields(entry) {
  if (!supportsBeta12DialogueFields()) return "";
  return `
    ${field({ id: "dialogue-topic", label: "Topic", value: entry.topic ?? entry.metadata?.topic ?? "" })}
    ${listField({ id: "dialogue-tags", label: "Tags", value: entry.tags ?? entry.metadata?.tags })}
    ${field({ id: "dialogue-questline", label: "Questline", value: entry.questline ?? entry.questline_id ?? entry.metadata?.questline ?? entry.metadata?.questline_id ?? "" })}
    ${field({ id: "dialogue-quest", label: "Quest", value: entry.quest ?? entry.quest_id ?? entry.metadata?.quest ?? entry.metadata?.quest_id ?? "" })}
    ${field({ id: "dialogue-stage", label: "Stage", value: entry.stage ?? entry.chapter ?? entry.metadata?.stage ?? entry.metadata?.chapter ?? "" })}
    ${textareaField({ id: "dialogue-notes", label: "Notes", value: entry.notes ?? entry.author_notes ?? entry.metadata?.notes ?? entry.metadata?.author_notes ?? "", className: "full", rows: 2 })}
  `;
}

function dialogueDeprecationAlert(kind, entry) {
  const message = dialogueDeprecationMessage(kind, entry);
  if (!message) return "";
  return `
    <div class="compat-alert info full" role="status">
      ${icon("info", "inline-icon")}
      <div>
        <strong>Marked for deprecation</strong>
        <span>${escapeHtml(message)}</span>
      </div>
    </div>
  `;
}

function socialAttributeHighToggles(entry) {
  const toggles = CONSTANTS.socialAttributes
    .map((attribute) => {
      const key = `requires_high_${attribute}`;
      return toggle({
        id: `dialogue-${key}`,
        label: `High ${humanize(attribute)}`,
        checked: entry[key] === true,
        tooltip: tooltipForFlag(key)
      });
    })
    .join("");
  return `
    <div class="field full">
      <label>Beta.12 high social attributes</label>
      <div class="toggle-grid">
        ${toggles}
      </div>
    </div>
  `;
}

function socialAttributeRangeFields(entry) {
  return CONSTANTS.socialAttributes
    .map((attribute) => `
      ${field({ id: `dialogue-min_${attribute}`, label: `Minimum ${humanize(attribute)}`, value: entry[`min_${attribute}`] ?? "", type: "number", attrs: 'min="1" max="100" step="1"' })}
      ${field({ id: `dialogue-max_${attribute}`, label: `Maximum ${humanize(attribute)}`, value: entry[`max_${attribute}`] ?? "", type: "number", attrs: 'min="1" max="100" step="1"' })}
    `)
    .join("");
}

function renderValueTags(fieldId, tags) {
  if (!tags.length) return "";
  return `
    <div class="value-tags" aria-label="${escapeHtml(fieldId)} suggestions">
      ${tags.map((tag) => {
        const tooltip = tooltipForTag(fieldId, tag);
        return `
          <button class="value-tag has-tooltip" type="button" data-action="insert-tag" data-target="${escapeHtml(fieldId)}" data-value="${escapeHtml(tag)}" data-tooltip="${escapeHtml(tooltip)}">
            ${icon("plus", "inline-icon")}
            ${escapeHtml(tag)}
          </button>
        `;
      }).join("")}
    </div>
  `;
}

function toggle({ id, label, checked = false, tooltip = "" }) {
  const tip = tooltip || tooltipForField(id, "") || tooltipForFlag(id.replace(/^[^-]+-/, ""));
  return `
    <div class="toggle has-tooltip" data-tooltip="${escapeHtml(tip)}">
      <input id="${id}" name="${id}" type="checkbox" ${checked ? "checked" : ""}>
      <span class="toggle-name">${escapeHtml(label)}</span>
      <button class="toggle-choice toggle-false" type="button" data-toggle-target="${id}" data-toggle-value="false" aria-pressed="${checked ? "false" : "true"}">False</button>
      <button class="toggle-choice toggle-true" type="button" data-toggle-target="${id}" data-toggle-value="true" aria-pressed="${checked ? "true" : "false"}">True</button>
    </div>
  `;
}

function toggleGrid(flags, entry, prefix) {
  const label = prefix === "option" ? "Visibility and Requirements" : "Extra Filters";
  const toggles = `
    ${toggle({ id: `${prefix}-show_for_adults`, label: "Show for adults", checked: entry.show_for_adults !== false, tooltip: tooltipForFlag("show_for_adults") })}
    ${toggle({ id: `${prefix}-show_for_babies`, label: "Show for babies", checked: entry.show_for_babies !== false, tooltip: tooltipForFlag("show_for_babies") })}
    ${flags.map((flag) => toggle({ id: `${prefix}-${flag}`, label: humanize(flag), checked: entry[flag] === true, tooltip: tooltipForToggleFlag(flag, prefix) })).join("")}
  `;
  const toggleCount = flags.length + 2;
  if (toggleCount >= COLLAPSIBLE_TOGGLE_MIN_COUNT) {
    return `
      <details class="toggle-details full">
        <summary class="toggle-details-summary">
          <span>${label}</span>
          <span class="toggle-details-meta">${toggleCount} settings</span>
          ${icon("chevron-right", "inline-icon toggle-details-icon")}
        </summary>
        <div class="toggle-grid">
          ${toggles}
        </div>
      </details>
    `;
  }

  return `
    <div class="field full">
      <label>${label}</label>
      <div class="toggle-grid">
        ${toggles}
      </div>
    </div>
  `;
}

function villagerEquipmentToggles(prefix, entry, subject = "villager") {
  const unarmedKey = `requires_${subject}_unarmed`;
  const armedKey = `requires_${subject}_armed`;
  return `
    ${toggle({ id: `${prefix}-${unarmedKey}`, label: `Requires unarmed ${subject}`, checked: entry[unarmedKey] || entry[`${subject}_unarmed`] })}
    ${toggle({ id: `${prefix}-${armedKey}`, label: `Requires armed ${subject}`, checked: entry[armedKey] || entry[`${subject}_armed`] })}
  `;
}

function readVillagerEquipment(prefix, subject = "villager") {
  const unarmedKey = `requires_${subject}_unarmed`;
  const armedKey = `requires_${subject}_armed`;
  return {
    [unarmedKey]: readValue(`${prefix}-${unarmedKey}`) ? true : undefined,
    [armedKey]: readValue(`${prefix}-${armedKey}`) ? true : undefined
  };
}

function renderOverview() {
  const version = packVersionInfo();
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("settings-2", "section-icon")}
          <div>
            <h2>Pack Setup</h2>
            <p class="path-label">pack.mcmeta</p>
          </div>
        </div>
        <span class="pill">${escapeHtml(version.label)}</span>
      </div>
      <div class="form-grid overview-grid">
        ${field({ id: "meta-packName", label: "Pack name", value: state.meta.packName, className: "span-6" })}
        ${selectField({
          id: "meta-packVersion",
          label: "VR version",
          value: state.meta.packVersion,
          options: PACK_VERSIONS.map((packVersion) => ({ value: packVersion.id, label: packVersion.label })),
          help: "Use beta.12 only for packs authored against the beta.12 wiki. Imports generated by beta.11+ select their saved target automatically; the builder does not convert beta.11 packs to beta.12.",
          className: "span-6",
          allowBlank: false
        })}
        ${field({ id: "meta-packFormat", label: "Minecraft pack format", value: state.meta.packFormat, type: "number", help: `Default for ${version.label}: ${version.packFormat}.`, className: "span-6" })}
        ${field({ id: "meta-namespace", label: "Story namespace", value: state.meta.namespace, help: "Story discovery can use your namespace.", className: "span-6" })}
        ${field({ id: "meta-slug", label: "File slug", value: state.meta.slug, help: "Used in generated file names.", className: "span-6" })}
        ${field({ id: "meta-locale", label: "Locale", value: state.meta.locale, help: "Dialogue and notifications load en_us first.", className: "span-6" })}
        ${textareaField({ id: "meta-description", label: "Description", value: state.meta.description, className: "span-12", rows: 2 })}
      </div>
    </div>
  `;
}

function renderEntryTabs(kinds, activeKey, scope) {
  return `
    <div class="entry-tabs" data-scope="${scope}">
      ${kinds.map((kind) => {
        const severity = entryCollectionIssueSeverity(scope, kind.key);
        return `
        <button class="entry-tab has-tooltip ${kind.key === activeKey ? "is-active" : ""} ${issueSeverityClass(severity)}" type="button" data-kind="${kind.key}" data-tooltip="${escapeHtml(KIND_TOOLTIPS[`${scope}.${kind.key}`] || "")}">
          ${icon(kind.icon || "circle", "inline-icon")}
          ${escapeHtml(kind.label)}
        </button>
      `;
      }).join("")}
    </div>
  `;
}

function entryListPageKey(section, kind) {
  return `${section}.${kind}`;
}

function pageForEntryList(section, kind, collection) {
  const pageCount = Math.max(1, Math.ceil(collection.length / ENTRY_PAGE_SIZE));
  const key = entryListPageKey(section, kind);
  let page = Math.min(Math.max(entryListPages[key] || 0, 0), pageCount - 1);
  if (editing?.section === section && editing.kind === kind) {
    page = Math.min(Math.max(Math.floor(editing.index / ENTRY_PAGE_SIZE), 0), pageCount - 1);
    entryListPages[key] = page;
  }
  return { key, page, pageCount };
}

function renderEntryListControls(section, kind, page, pageCount, collectionLength) {
  if (pageCount <= 1) return "";
  const first = page * ENTRY_PAGE_SIZE + 1;
  const last = Math.min(collectionLength, (page + 1) * ENTRY_PAGE_SIZE);
  return `
    <div class="entry-list-controls">
      <span>${first}-${last} of ${collectionLength}</span>
      <button class="icon-button has-tooltip" type="button" data-action="entry-page" data-section="${section}" data-kind="${kind}" data-page="${page - 1}" ${page <= 0 ? "disabled" : ""} data-tooltip="Previous entries">
        ${icon("chevron-left", "button-icon")}
      </button>
      <button class="icon-button has-tooltip" type="button" data-action="entry-page" data-section="${section}" data-kind="${kind}" data-page="${page + 1}" ${page >= pageCount - 1 ? "disabled" : ""} data-tooltip="Next entries">
        ${icon("chevron-right", "button-icon")}
      </button>
    </div>
  `;
}

function entrySaveAction(section) {
  return {
    dialogue: "save-dialogue-entry",
    forcedDialogue: "save-forced-dialogue",
    notifications: "save-notification",
    gifts: "save-gift-entry",
    pacification: "save-pacification",
    stories: "save-story-entry"
  }[section] || "";
}

function renderEntryList(collection, kind, section) {
  if (collection.length === 0) {
    return `<div class="empty-state">No ${escapeHtml(humanize(kind).toLowerCase())} yet.</div>`;
  }
  const { page, pageCount } = pageForEntryList(section, kind, collection);
  const start = page * ENTRY_PAGE_SIZE;
  const visibleEntries = collection.slice(start, start + ENTRY_PAGE_SIZE);
  const controls = renderEntryListControls(section, kind, page, pageCount, collection.length);
  const sortable = collection.length > 1;
  const entries = visibleEntries
    .map((entry, index) => {
      const absoluteIndex = start + index;
      const title = entry.id || entry.key || entry.trigger || entry.label || entry.text || entry.item || entry.name || `${humanize(kind)} ${absoluteIndex + 1}`;
      const detail = section === "dialogue" && (kind === "options" || kind === "lines")
        ? entry.request || ""
        : entry.request || entry.type || entry.reaction || entry.world_text_kind || entry.structure || entry.biome || entry.items?.join(", ") || "";
      const active = editing && editing.section === section && editing.kind === kind && editing.index === absoluteIndex;
      const severity = entryIssueSeverity(section, kind, entry);
      const issueMessage = entryIssueMessage(section, kind, entry);
      const saveAction = active ? entrySaveAction(section) : "";
      return `
        <article class="entry-card ${active ? "is-active" : ""} ${sortable ? "is-sortable" : ""} ${issueSeverityClass(severity)}" data-section="${section}" data-kind="${kind}" data-index="${absoluteIndex}" tabindex="0" role="button" aria-label="Edit ${escapeHtml(title)}" ${sortable ? `draggable="true"` : ""}>
          <div class="entry-object-header">
            <span class="entry-object-title">
              ${icon("square-pen", "inline-icon")}
              ${escapeHtml(title)}
            </span>
            <button class="entry-delete danger has-tooltip" type="button" data-action="delete-entry" data-section="${section}" data-kind="${kind}" data-index="${absoluteIndex}" aria-label="Delete ${escapeHtml(title)}" data-tooltip="Delete entry">
              ${icon("trash-2", "button-icon")}
            </button>
          </div>
          ${issueMessage ? `<small class="entry-issue">${escapeHtml(issueMessage)}</small>` : ""}
          ${detail ? `<small>${escapeHtml(detail)}</small>` : ""}
          ${saveAction ? `
            <div class="entry-card-actions">
              <button class="entry-save has-tooltip" type="button" data-action="save-entry-form" aria-label="Save ${escapeHtml(title)}" data-tooltip="Save changes. ${escapeHtml(formatKeybind(getKeybind("saveEntry")))}">
                ${icon("save", "button-icon")}
              </button>
            </div>
          ` : ""}
        </article>
      `;
    })
    .join("");
  return `${controls}${entries}${controls}`;
}

function renderDialogue() {
  const collection = state.dialogue[activeDialogueKind];
  const entry = editing?.section === "dialogue" && editing.kind === activeDialogueKind
    ? collection[editing.index]
    : {};
  const folderLayout = dialogueUsesFolderLayout();
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("message-square-text", "section-icon")}
          <div>
            <h2>Dialogue</h2>
            <p class="path-label">data/villagerretaliation/dialogue</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-dialogue-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid">
        ${selectField({ id: "dialogue-layout", label: "Layout", value: dialogueUsesFolderLayout() ? "folders" : "bundle", options: DIALOGUE_LAYOUTS, allowBlank: false, help: supportsBeta12DialogueFields() ? "Typed folders are the beta.12 default." : "The beta.11 target exports a single bundle file." })}
        ${folderLayout
          ? field({ id: "dialogue-folderName", label: "Dialogue folder", value: state.dialogue.folderName, help: "Generated entries use <folder>/<section>/00_*.json." })
          : field({ id: "dialogue-fileName", label: "Dialogue file", value: state.dialogue.fileName, help: "Avoid global unless replacing the built-in file." })}
        ${field({ id: "dialogue-locale", label: "Locale", value: state.meta.locale })}
      </div>
      ${renderEntryTabs(DIALOGUE_KINDS, activeDialogueKind, "dialogue")}
      <div class="entry-layout">
        <form class="entry-form" data-form="dialogue" data-kind="${activeDialogueKind}">
          ${renderDialogueForm(activeDialogueKind, entry)}
        </form>
      </div>
    </div>
  `;
}

function renderDialogueForm(kind, entry) {
  const action = editing?.section === "dialogue" && editing.kind === kind ? "Update" : "Add";
  const commonFilters = `
    ${listField({ id: "dialogue-professions", label: "Professions", value: entry.professions, help: "Blank means any profession." })}
    ${listField({ id: "dialogue-dispositions", label: "Dispositions", value: entry.dispositions, help: "Blank means any reputation-derived disposition." })}
    ${villagerEquipmentToggles("dialogue", entry)}
  `;
  const reputationFilters = `
    ${listField({ id: "dialogue-reputation_levels", label: "Reputation levels", value: entry.reputation_levels ?? entry.reputation_level })}
    ${field({ id: "dialogue-min_reputation", label: "Minimum reputation", value: entry.min_reputation ?? "", type: "number" })}
    ${field({ id: "dialogue-max_reputation", label: "Maximum reputation", value: entry.max_reputation ?? "", type: "number" })}
  `;

  if (kind === "options") {
    return `
      <div class="form-grid">
        ${dialogueDeprecationAlert(kind, entry)}
        ${field({ id: "dialogue-id", label: "Option id", value: entry.id })}
        ${field({ id: "dialogue-label", label: "Button label", value: entry.label })}
        ${selectField({ id: "dialogue-type", label: "Request", value: entry.request ?? "", options: CONSTANTS.dialogueTypes })}
        ${field({ id: "dialogue-order", label: "Order", value: entry.order ?? "", type: "number" })}
        ${dialogueNarrativeMetadataFields(entry)}
        ${commonFilters}
        ${reputationFilters}
        ${dialogueConditionsField(entry)}
        ${listField({ id: "dialogue-player_items", label: "Required player items or tags", value: entry.player_items, help: "Use #minecraft:swords for item tags." })}
        ${listField({ id: "dialogue-player_item_slots", label: "Item slots", value: entry.player_item_slots, help: CONSTANTS.itemSlots.join(", ") })}
        ${playerItemDurabilityFields("dialogue", entry)}
        ${playerItemEnchantmentFields("dialogue", entry)}
        ${textareaField({ id: "dialogue-give_items", label: "Give items JSON", value: dialogueItemPaymentText(entry), help: "Optional. Example: { \"item\": \"minecraft:nether_star\", \"count\": 1, \"destination\": \"villager_inventory\" }", className: "full", rows: 5 })}
        ${toggleGrid(authoredDialogueFlags(CONSTANTS.optionFlags, kind), entry, "option")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  if (kind === "lines") {
    return `
      <div class="form-grid">
        ${dialogueDeprecationAlert(kind, entry)}
        ${field({ id: "dialogue-id", label: "Line id", value: entry.id })}
        ${selectField({ id: "dialogue-type", label: "Request", value: entry.request ?? "", options: CONSTANTS.dialogueTypes })}
        ${textareaField({ id: "dialogue-text", label: "Line(s)", value: dialogueTextValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
        ${beta12DialogueLineMetadataFields(entry)}
        ${dialogueNarrativeMetadataFields(entry)}
        ${listField({ id: "dialogue-option", label: "Option id(s)", value: entry.option ?? entry.option_ids, help: "Link to a custom or built-in talk option." })}
        ${commonFilters}
        ${reputationFilters}
        ${beta12DialogueLineFilters(entry)}
        ${dialogueConditionsField(entry)}
        ${listField({ id: "dialogue-weather", label: "Weather", value: entry.weather, help: CONSTANTS.weather.join(", ") })}
        ${listField({ id: "dialogue-times", label: "Times", value: entry.times, help: CONSTANTS.times.join(", ") })}
        ${listField({ id: "dialogue-event_tags", label: "Village event tags", value: entry.event_tags })}
        ${listField({ id: "dialogue-player_event_tags", label: "Player event tags", value: entry.player_event_tags })}
        ${listField({ id: "dialogue-retaliation_target_entity_types", label: "Retaliation target entity types", value: entry.retaliation_target_entity_types ?? entry.retaliation_target_entities })}
        ${listField({ id: "dialogue-player_items", label: "Required player items or tags", value: entry.player_items })}
        ${listField({ id: "dialogue-player_item_slots", label: "Item slots", value: entry.player_item_slots })}
        ${playerItemDurabilityFields("dialogue", entry)}
        ${playerItemEnchantmentFields("dialogue", entry)}
        ${listField({ id: "dialogue-story_structure", label: "Story structures", value: entry.story_structure ?? entry.story_structures })}
        ${listField({ id: "dialogue-story_biome", label: "Story biomes", value: entry.story_biome ?? entry.story_biomes })}
        ${listField({ id: "dialogue-recruitment_followup_scenarios", label: "Recruitment follow-up scenarios", value: entry.recruitment_followup_scenarios })}
        ${listField({ id: "dialogue-recruitment_memory_scenarios", label: "Recruitment memory scenarios", value: entry.recruitment_memory_scenarios })}
        ${listField({ id: "dialogue-recruitment_memory_biomes", label: "Recruitment memory biomes", value: entry.recruitment_memory_biome ?? entry.recruitment_memory_biomes })}
        ${field({ id: "dialogue-min_recruitment_follow_distance", label: "Minimum follow distance", value: entry.min_recruitment_follow_distance ?? "", type: "number" })}
        ${selectField({ id: "dialogue-gift_advice", label: "Gift advice filter", value: entry.gift_advice, options: CONSTANTS.giftAdvice })}
        ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
        ${toggleGrid(authoredDialogueFlags(CONSTANTS.lineFlags, kind), entry, "line")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  if (kind === "messages") {
    return `
      <div class="form-grid">
        ${field({ id: "dialogue-id", label: "Message id", value: entry.id })}
        ${field({ id: "dialogue-key", label: "Message key", value: entry.key, help: "Gift rules can point response_key at custom message keys." })}
        ${textareaField({ id: "dialogue-text", label: "Message text variation(s)", value: dialogueTextValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
        ${dialogueNarrativeMetadataFields(entry)}
        ${commonFilters}
        ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
        ${toggleGrid([], entry, "message")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  if (kind === "pacify") {
    return `
      <div class="form-grid">
        ${field({ id: "dialogue-id", label: "Pacify line id", value: entry.id })}
        ${textareaField({ id: "dialogue-text", label: "Pacify line(s)", value: dialogueTextValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
        ${dialogueNarrativeMetadataFields(entry)}
        ${listField({ id: "dialogue-outcomes", label: "Outcomes", value: entry.outcomes, help: CONSTANTS.pacifyOutcomes.join(", ") })}
        ${commonFilters}
        ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
        ${toggleGrid([], entry, "pacify")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  return `
    <div class="form-grid">
      ${field({ id: "dialogue-id", label: `${capitalize(kind.slice(0, -1))} id`, value: entry.id })}
      ${textareaField({ id: "dialogue-text", label: "Text variation(s)", value: dialogueTextValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
      ${dialogueNarrativeMetadataFields(entry)}
      ${commonFilters}
      ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
      ${toggleGrid(["first_conversation_only", "first_village_interaction_only"], entry, "opening")}
    </div>
    ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
  `;
}

function formActions(actionLabel, saveAction, clearAction) {
  const saveIcon = actionLabel === "Update" ? "save" : "plus";
  const saveButton = `<button class="button button-primary" type="submit" data-action="${saveAction}">${icon(saveIcon, "button-icon")}${actionLabel}</button>`;
  return `
    <div class="form-actions">
      ${saveButton}
      <button class="button button-secondary" type="button" data-action="${clearAction}">${icon("rotate-ccw", "button-icon")}Clear</button>
    </div>
  `;
}

function forcedOutputClass(currentMode, visibleMode) {
  const hiddenClass = currentMode === visibleMode ? "" : " is-hidden";
  return `forced-output-field forced-output-${visibleMode}${hiddenClass}`;
}

function renderForcedDialogue() {
  const collection = state.forcedDialogue.entries;
  const entry = editing?.section === "forcedDialogue" ? collection[editing.index] : {};
  const optionsJson = Array.isArray(entry.options) ? JSON.stringify(entry.options, null, 2) : "";
  const outputMode = entry.output?.mode ?? "forced_dialogue";
  const outputRadius = entry.output?.radius ?? "";
  const forcedOnlyClass = forcedOutputClass(outputMode, "forced_dialogue");
  const chatOnlyClass = forcedOutputClass(outputMode, "chat");
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("octagon-alert", "section-icon")}
          <div>
            <h2>Forced Dialogue</h2>
            <p class="path-label">data/villagerretaliation/forced_dialogue</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-forced-dialogue-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid one">
        ${field({ id: "forcedDialogue-fileName", label: "Forced dialogue file", value: state.forcedDialogue.fileName, help: "Avoid default unless replacing the built-in theft rule." })}
      </div>
      <div class="entry-layout">
        <form class="entry-form" data-form="forcedDialogue">
          <div class="form-grid">
            ${field({ id: "forced-id", label: "Entry id", value: entry.id })}
            ${selectField({ id: "forced-trigger", label: "Trigger", value: forcedTriggerValue(entry), options: CONSTANTS.forcedDialogueTriggers, allowBlank: false })}
            ${selectField({ id: "forced-output_mode", label: "Output mode", value: outputMode, options: CONSTANTS.forcedOutputModes, allowBlank: false })}
            ${field({ id: "forced-output_radius", label: "Output radius", value: outputRadius, type: "number", attrs: 'min="1" step="1"', className: chatOnlyClass })}
            ${textareaField({ id: "forced-line", label: "Opening line(s)", value: forcedDialogueLineValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
            ${field({ id: "forced-priority", label: "Priority", value: entry.priority ?? "", type: "number" })}
            ${field({ id: "forced-chance", label: "Chance", value: entry.chance ?? "", type: "number", attrs: 'min="0" max="1" step="0.01"' })}
            ${field({ id: "forced-witness_radius", label: "Witness radius", value: entry.witness_radius ?? "", type: "number", attrs: 'min="1" step="1"' })}
            ${field({ id: "forced-reputation", label: "Reputation change", value: entry.reputation ?? "", type: "number", className: forcedOnlyClass })}
            ${listField({ id: "forced-witness_professions", label: "Witness professions", value: entry.witness_professions ?? entry.witness_profession ?? entry.professions, help: "Optional. Restrict to a witnessing profession such as armorer, cleric, or weaponsmith." })}
            ${villagerEquipmentToggles("forced", entry, "witness")}
            ${listField({ id: "forced-player_items", label: "Player items or tags", value: entry.player_items ?? entry.player_item ?? entry.player_item_tags ?? entry.player_item_tag, help: "Required for player_item_proximity. Use minecraft:diamond_sword or #minecraft:swords." })}
            ${listField({ id: "forced-player_item_slots", label: "Player item slots", value: entry.player_item_slots ?? entry.player_item_slot, help: CONSTANTS.itemSlots.join(", ") })}
            ${playerItemDurabilityFields("forced", entry)}
            ${playerItemEnchantmentFields("forced", entry)}
            ${listField({ id: "forced-loot_tables", label: "Loot tables", value: entry.loot_tables ?? entry.loot_table, help: "Optional. Match generated containers from loot tables like minecraft:chests/village/village_armorer." })}
            ${listField({ id: "forced-target_entity_types", label: "Target entity types", value: entry.target_entity_types ?? entry.target_entity_type ?? entry.target_entities, help: "Optional. Useful for retaliation_started, for example minecraft:player." })}
            ${field({ id: "forced-min_recent_retaliations", label: "Min prior retaliations", value: entry.min_recent_retaliations ?? "", type: "number", attrs: 'min="0" step="1"' })}
            ${field({ id: "forced-max_recent_retaliations", label: "Max prior retaliations", value: entry.max_recent_retaliations ?? "", type: "number", attrs: 'min="0" step="1"' })}
            <div class="field full">
              <label>Event Behavior</label>
              <div class="toggle-grid">
                ${toggle({ id: "forced-requires_line_of_sight", label: "Requires line of sight", checked: entry.requires_line_of_sight !== false })}
              </div>
            </div>
            <div class="field full ${forcedOnlyClass}">
              <label>Forced Dialogue Behavior</label>
              <div class="toggle-grid">
                ${toggle({ id: "forced-initiate_dialogue", label: "Initiates dialogue", checked: entry.initiate_dialogue !== false })}
                ${toggle({ id: "forced-aggro_immediately", label: "Aggro immediately", checked: entry.aggro_immediately === true })}
                ${toggle({ id: "forced-force_camera_towards_villager", label: "Force camera to villager", checked: entry.force_camera_towards_villager === true })}
              </div>
            </div>
            ${textareaField({ id: "forced-options_json", label: "Options JSON", value: optionsJson, help: "Use response/responses for option reply variations. Payment or return outcomes can also set success_response/success_responses and failure_response/failure_responses.", className: `full ${forcedOnlyClass}`, rows: 7 })}
            ${textareaField({ id: "forced-leave_option_json", label: "Leave option(s) JSON", value: prettyJson(entry.leave_options ?? entry.leave_option), help: "Optional. Object for one Leave outcome, or array for reputation-gated outcomes.", className: `full ${forcedOnlyClass}`, rows: 4 })}
          </div>
          ${formActions(editing?.section === "forcedDialogue" ? "Update" : "Add", "save-forced-dialogue", "clear-forced-dialogue-form")}
        </form>
      </div>
    </div>
  `;
}

function updateForcedOutputModeFields(root = document) {
  const form = root.querySelector?.('form[data-form="forcedDialogue"]');
  if (!form) return;
  const mode = form.querySelector("#forced-output_mode")?.value || "forced_dialogue";
  for (const field of form.querySelectorAll(".forced-output-chat")) {
    field.classList.toggle("is-hidden", mode !== "chat");
  }
  for (const field of form.querySelectorAll(".forced-output-forced_dialogue")) {
    field.classList.toggle("is-hidden", mode !== "forced_dialogue");
  }
}

function renderNotifications() {
  const collection = state.notifications.notifications;
  const entry = editing?.section === "notifications" ? collection[editing.index] : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("bell-ring", "section-icon")}
          <div>
            <h2>Notifications</h2>
            <p class="path-label">data/villagerretaliation/notifications</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-notification-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid">
        ${field({ id: "notifications-fileName", label: "Notification file", value: state.notifications.fileName, help: "Avoid global unless replacing the built-in file." })}
        ${field({ id: "notifications-locale", label: "Locale", value: state.meta.locale })}
      </div>
      <div class="entry-layout">
        <form class="entry-form" data-form="notifications">
          <div class="form-grid">
            ${field({ id: "notification-id", label: "Notification id", value: entry.id })}
            ${field({ id: "notification-trigger", label: "Trigger", value: entry.trigger, attrs: 'list="notification-triggers"', help: "Use a built-in trigger or a custom trigger emitted by code." })}
            ${textareaField({ id: "notification-text", label: "Text variation(s)", value: notificationTextValue(entry), help: "One variation per line.", className: "full", rows: 3 })}
            ${selectField({ id: "notification-kind", label: "HUD kind", value: entry.kind, options: CONSTANTS.hudKinds })}
            ${selectField({ id: "notification-world_text_kind", label: "World text kind", value: entry.world_text_kind ?? entry.style, options: CONSTANTS.worldTextKinds })}
            ${field({ id: "notification-color", label: "Color", value: entry.color, attrs: 'list="color-values"', help: "Named color, #RRGGBB, or #AARRGGBB." })}
            ${field({ id: "notification-text_color", label: "Text color", value: entry.text_color, attrs: 'list="color-values"' })}
            ${field({ id: "notification-chat_color", label: "Chat color", value: entry.chat_color, attrs: 'list="color-values"' })}
            ${listField({ id: "notification-professions", label: "Professions", value: entry.professions })}
            ${villagerEquipmentToggles("notification", entry)}
            ${listField({ id: "notification-reputation_levels", label: "Reputation levels", value: entry.reputation_levels })}
            ${listField({ id: "notification-target_entity_types", label: "Target entity types", value: entry.target_entity_types ?? entry.target_entities })}
            ${field({ id: "notification-min_reputation", label: "Minimum reputation", value: entry.min_reputation ?? "", type: "number" })}
            ${field({ id: "notification-max_reputation", label: "Maximum reputation", value: entry.max_reputation ?? "", type: "number" })}
            ${listField({ id: "notification-player_items", label: "Required player items or tags", value: entry.player_items })}
            ${listField({ id: "notification-player_item_slots", label: "Item slots", value: entry.player_item_slots })}
            ${playerItemDurabilityFields("notification", entry)}
            ${playerItemEnchantmentFields("notification", entry)}
            ${field({ id: "notification-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
            ${field({ id: "notification-chance", label: "Chance", value: entry.chance ?? "", type: "number", attrs: 'min="0" max="1" step="0.01"' })}
            ${toggleGrid([], entry, "notification")}
          </div>
          ${formActions(editing?.section === "notifications" ? "Update" : "Add", "save-notification", "clear-notification-form")}
        </form>
      </div>
      ${datalist("notification-triggers", CONSTANTS.notificationTriggers)}
      ${datalist("color-values", CONSTANTS.colors)}
    </div>
  `;
}

function datalist(id, values) {
  return `<datalist id="${id}">${values.map((value) => `<option value="${escapeHtml(value)}"></option>`).join("")}</datalist>`;
}

function renderGifts() {
  const collection = state.gifts[activeGiftKind];
  const entry = editing?.section === "gifts" && editing.kind === activeGiftKind ? collection[editing.index] : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("gift", "section-icon")}
          <div>
            <h2>Gifts</h2>
            <p class="path-label">data/villagerretaliation/gifts</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-gift-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid one">
        ${field({ id: "gifts-fileName", label: "Gift file", value: state.gifts.fileName, help: "Use default only when replacing all built-in gifts." })}
      </div>
      ${renderEntryTabs(GIFT_KINDS, activeGiftKind, "gifts")}
      <div class="entry-layout">
        <form class="entry-form" data-form="gifts" data-kind="${activeGiftKind}">
          ${renderGiftForm(activeGiftKind, entry)}
        </form>
      </div>
    </div>
  `;
}

function renderGiftForm(kind, entry) {
  if (kind === "preferences") {
    return `
      <div class="form-grid">
        ${selectField({ id: "gift-reaction", label: "Reaction", value: entry.reaction, options: CONSTANTS.reactions, allowBlank: false })}
        ${field({ id: "gift-priority", label: "Priority", value: entry.priority ?? "", type: "number" })}
        ${listField({ id: "gift-items", label: "Items", value: entry.items ?? entry.item, help: "Unnamespaced values count as minecraft ids." })}
        ${listField({ id: "gift-tags", label: "Tags", value: entry.tags ?? entry.tag })}
        ${listField({ id: "gift-professions", label: "Professions", value: entry.professions })}
        ${villagerEquipmentToggles("gift", entry)}
        ${field({ id: "gift-reputation_per_item", label: "Reputation per item", value: entry.reputation_per_item ?? "", type: "number" })}
        ${field({ id: "gift-response_key", label: "Response key", value: entry.response_key, className: "full", help: "Add a dialogue message with this key for custom gift text." })}
      </div>
      ${formActions(editing?.section === "gifts" && editing.kind === kind ? "Update" : "Add", "save-gift-entry", "clear-gift-form")}
    `;
  }
  return `
    <div class="form-grid">
      ${field({ id: "gift-item", label: "Reward item", value: entry.item })}
      ${listField({ id: "gift-professions", label: "Professions", value: entry.professions })}
      ${villagerEquipmentToggles("gift", entry)}
      ${listField({ id: "gift-reputation_levels", label: "Reputation levels", value: entry.reputation_levels })}
      ${field({ id: "gift-min_count", label: "Minimum count", value: entry.min_count ?? "", type: "number" })}
      ${field({ id: "gift-max_count", label: "Maximum count", value: entry.max_count ?? "", type: "number" })}
      ${field({ id: "gift-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
    </div>
    ${formActions(editing?.section === "gifts" && editing.kind === kind ? "Update" : "Add", "save-gift-entry", "clear-gift-form")}
  `;
}

function renderPacification() {
  const collection = state.pacification.payments;
  const entry = editing?.section === "pacification" ? collection[editing.index] : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("hand-coins", "section-icon")}
          <div>
            <h2>Pacification Payments</h2>
            <p class="path-label">data/villagerretaliation/pacification</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-pacification-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid one">
        ${field({ id: "pacification-fileName", label: "Pacification file", value: state.pacification.fileName, help: "Use default only when replacing the built-in emerald rule." })}
      </div>
      <div class="entry-layout">
        <form class="entry-form" data-form="pacification">
          <div class="form-grid">
            ${listField({ id: "pacification-items", label: "Items", value: entry.items ?? entry.item })}
            ${listField({ id: "pacification-tags", label: "Tags", value: entry.tags ?? entry.tag })}
            ${listField({ id: "pacification-professions", label: "Professions", value: entry.professions })}
            ${villagerEquipmentToggles("pacification", entry)}
            ${field({ id: "pacification-count", label: "Exact count", value: entry.count ?? "", type: "number" })}
            ${field({ id: "pacification-min_count", label: "Minimum count", value: entry.min_count ?? "", type: "number" })}
            ${field({ id: "pacification-max_count", label: "Maximum count", value: entry.max_count ?? "", type: "number" })}
            ${field({ id: "pacification-name", label: "Singular name", value: entry.name })}
            ${field({ id: "pacification-plural_name", label: "Plural name", value: entry.plural_name })}
            ${field({ id: "pacification-priority", label: "Priority", value: entry.priority ?? "", type: "number" })}
          </div>
          ${formActions(editing?.section === "pacification" ? "Update" : "Add", "save-pacification", "clear-pacification-form")}
        </form>
      </div>
    </div>
  `;
}

function renderStories() {
  const collection = state.stories[activeStoryKind];
  const entry = editing?.section === "stories" && editing.kind === activeStoryKind ? collection[editing.index] : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("map", "section-icon")}
          <div>
            <h2>Story Discovery</h2>
            <p class="path-label">data/&lt;namespace&gt;/story_*</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-story-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid">
        ${field({ id: "stories-namespace", label: "Story namespace", value: state.stories.namespace })}
        ${field({ id: "stories-radius", label: "Default structure radius", value: state.stories.radius ?? "", type: "number" })}
        ${field({ id: "stories-structureFileName", label: "Structure file", value: state.stories.structureFileName })}
        ${field({ id: "stories-biomeFileName", label: "Biome file", value: state.stories.biomeFileName })}
      </div>
      ${renderEntryTabs(STORY_KINDS, activeStoryKind, "stories")}
      <div class="entry-layout">
        <form class="entry-form" data-form="stories" data-kind="${activeStoryKind}">
          ${renderStoryForm(activeStoryKind, entry)}
        </form>
      </div>
    </div>
  `;
}

function renderStoryForm(kind, entry) {
  if (kind === "structures") {
    return `
      <div class="form-grid">
        ${listField({ id: "story-structures", label: "Structure id(s)", value: entry.structure ?? entry.structures, help: "Use full ids like minecraft:ancient_city." })}
        ${field({ id: "story-name", label: "Display name", value: entry.name })}
        ${field({ id: "story-radius", label: "Radius", value: entry.radius ?? "", type: "number" })}
      </div>
      ${formActions(editing?.section === "stories" && editing.kind === kind ? "Update" : "Add", "save-story-entry", "clear-story-form")}
    `;
  }
  return `
    <div class="form-grid">
      ${listField({ id: "story-biomes", label: "Biome id(s)", value: entry.biome ?? entry.biomes, help: "Use full ids like minecraft:deep_dark." })}
      ${field({ id: "story-name", label: "Display name", value: entry.name })}
    </div>
    ${formActions(editing?.section === "stories" && editing.kind === kind ? "Update" : "Add", "save-story-entry", "clear-story-form")}
  `;
}

function renderNames() {
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div class="panel-title-main">
          ${icon("user-round", "section-icon")}
          <div>
            <h2>Preset Names</h2>
            <p class="path-label">data/villagerretaliation/villager_names</p>
          </div>
        </div>
        <button class="button button-secondary" type="button" data-action="add-name-example">${icon("plus", "button-icon")}Add Example</button>
      </div>
      <div class="form-grid">
        ${textareaField({ id: "names-male_names", label: "Male names", value: state.names.male_names.join("\n"), rows: 8, help: "One name per line.", className: "full" })}
        ${textareaField({ id: "names-female_names", label: "Female names", value: state.names.female_names.join("\n"), rows: 8, help: "One name per line.", className: "full" })}
      </div>
    </div>
  `;
}

function readValue(id) {
  const element = document.querySelector(`#${id}`);
  if (!element) return "";
  if (element.type === "checkbox") return element.checked;
  return element.value;
}

function readList(id) {
  return parseList(readValue(id));
}

function readDialogueTextFields() {
  const lines = readValue("dialogue-text")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length > 1) return { lines };
  return { text: lines[0] || "" };
}

function readDialogueNarrativeMetadata() {
  if (!supportsBeta12DialogueFields()) return {};
  return {
    topic: readValue("dialogue-topic").trim(),
    tags: readList("dialogue-tags"),
    questline: readValue("dialogue-questline").trim(),
    quest: readValue("dialogue-quest").trim(),
    stage: readValue("dialogue-stage").trim(),
    notes: readValue("dialogue-notes").trim()
  };
}

function readNotificationTextFields() {
  const lines = readValue("notification-text")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  if (lines.length > 1) return { lines };
  return { text: lines[0] || "" };
}

function readPlayerItemDurability(prefix) {
  return {
    min_player_item_durability: parseInteger(readValue(`${prefix}-min_player_item_durability`)),
    max_player_item_durability: parseInteger(readValue(`${prefix}-max_player_item_durability`)),
    min_player_item_durability_percent: parseInteger(readValue(`${prefix}-min_player_item_durability_percent`)),
    max_player_item_durability_percent: parseInteger(readValue(`${prefix}-max_player_item_durability_percent`))
  };
}

function readPlayerItemEnchantments(prefix) {
  return {
    player_item_enchantments: readList(`${prefix}-player_item_enchantments`),
    min_player_item_enchantment_level: parseInteger(readValue(`${prefix}-min_player_item_enchantment_level`)),
    max_player_item_enchantment_level: parseInteger(readValue(`${prefix}-max_player_item_enchantment_level`))
  };
}

function readBooleans(prefix, flags, base = {}) {
  const entry = { ...base };
  const adult = readValue(`${prefix}-show_for_adults`);
  const baby = readValue(`${prefix}-show_for_babies`);
  if (adult === false) entry.show_for_adults = false;
  if (baby === false) entry.show_for_babies = false;
  for (const flag of flags) {
    if (readValue(`${prefix}-${flag}`) === true) {
      entry[flag] = true;
    }
  }
  return entry;
}

function preservedHiddenDialogueFields(kind, entry) {
  if (!editing || editing.section !== "dialogue" || editing.kind !== kind) return {};
  const source = currentEditingEntry();
  if (!source || typeof source !== "object") return {};
  const keys = new Set([
    ...hiddenDialogueVersionKeys(kind),
    ...plannedBeta13DialogueDeprecationKeysForKind(kind)
  ]);
  const preserved = {};
  for (const key of keys) {
    const emptyEntryValue = entry[key] === undefined
      || entry[key] === ""
      || (Array.isArray(entry[key]) && entry[key].length === 0);
    if (source[key] !== undefined && emptyEntryValue) {
      preserved[key] = source[key];
    }
  }
  return preserved;
}

function readCurrentDraftEntry(options = {}) {
  const form = els.panel.querySelector(".entry-form");
  if (!form) return null;
  try {
    if (form.dataset.form === "dialogue") {
      return { section: "dialogue", kind: activeDialogueKind, entry: cleanObject(readDialogueEntry()) };
    }
    if (form.dataset.form === "forcedDialogue") {
      const entry = readForcedDialogueEntry(options);
      return entry ? { section: "forcedDialogue", kind: "entries", entry: cleanObject(entry) } : null;
    }
    if (form.dataset.form === "notifications") {
      return { section: "notifications", kind: "notifications", entry: cleanObject(readNotificationEntry()) };
    }
    if (form.dataset.form === "gifts") {
      return { section: "gifts", kind: activeGiftKind, entry: cleanObject(readGiftEntry()) };
    }
    if (form.dataset.form === "pacification") {
      return { section: "pacification", kind: "payments", entry: cleanObject(readPacificationEntry()) };
    }
    if (form.dataset.form === "stories") {
      return { section: "stories", kind: activeStoryKind, entry: cleanObject(readStoryEntry()) };
    }
  } catch {
    return null;
  }
  return null;
}

function readDialogueEntry() {
  const kind = activeDialogueKind;
  let entry = {};
  if (kind === "options") {
    entry = readBooleans("option", authoredDialogueFlags(CONSTANTS.optionFlags, kind), {
      id: readValue("dialogue-id").trim(),
      label: readValue("dialogue-label").trim(),
      type: "dialogue_option",
      request: readValue("dialogue-type"),
      order: parseInteger(readValue("dialogue-order")),
      ...readDialogueNarrativeMetadata(),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      ...readVillagerEquipment("dialogue"),
      reputation_levels: readList("dialogue-reputation_levels"),
      min_reputation: parseInteger(readValue("dialogue-min_reputation")),
      max_reputation: parseInteger(readValue("dialogue-max_reputation")),
      conditions: readDialogueConditions(),
      player_items: readList("dialogue-player_items"),
      player_item_slots: readList("dialogue-player_item_slots"),
      ...readPlayerItemDurability("dialogue"),
      ...readPlayerItemEnchantments("dialogue"),
      ...readDialogueItemPayment()
    });
  } else if (kind === "lines") {
    const optionIds = readList("dialogue-option");
    const storyStructures = readList("dialogue-story_structure");
    const storyBiomes = readList("dialogue-story_biome");
    entry = readBooleans("line", authoredDialogueFlags(CONSTANTS.lineFlags, kind), {
      id: readValue("dialogue-id").trim(),
      request: readValue("dialogue-type"),
      ...readDialogueTextFields(),
      text_key: readValue("dialogue-text_key").trim(),
      ...readDialogueNarrativeMetadata(),
      option: optionIds.length <= 1 ? optionIds[0] : optionIds,
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      ...readVillagerEquipment("dialogue"),
      reputation_levels: readList("dialogue-reputation_levels"),
      min_reputation: parseInteger(readValue("dialogue-min_reputation")),
      max_reputation: parseInteger(readValue("dialogue-max_reputation")),
      ...readBeta12DialogueLineFilters(),
      conditions: readDialogueConditions(),
      weather: readList("dialogue-weather"),
      times: readList("dialogue-times"),
      event_tags: readList("dialogue-event_tags"),
      player_event_tags: readList("dialogue-player_event_tags"),
      retaliation_target_entity_types: readList("dialogue-retaliation_target_entity_types"),
      player_items: readList("dialogue-player_items"),
      player_item_slots: readList("dialogue-player_item_slots"),
      ...readPlayerItemDurability("dialogue"),
      ...readPlayerItemEnchantments("dialogue"),
      story_structures: storyStructures,
      story_biomes: storyBiomes,
      recruitment_followup_scenarios: readList("dialogue-recruitment_followup_scenarios"),
      recruitment_memory_scenarios: readList("dialogue-recruitment_memory_scenarios"),
      recruitment_memory_biomes: readList("dialogue-recruitment_memory_biomes"),
      min_recruitment_follow_distance: parseInteger(readValue("dialogue-min_recruitment_follow_distance")),
      gift_advice: readValue("dialogue-gift_advice"),
      priority: parseInteger(readValue("dialogue-priority")),
      category: readValue("dialogue-category").trim(),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else if (kind === "messages") {
    entry = readBooleans("message", [], {
      id: readValue("dialogue-id").trim(),
      key: readValue("dialogue-key").trim(),
      ...readDialogueTextFields(),
      ...readDialogueNarrativeMetadata(),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      ...readVillagerEquipment("dialogue"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else if (kind === "pacify") {
    entry = readBooleans("pacify", [], {
      id: readValue("dialogue-id").trim(),
      ...readDialogueTextFields(),
      ...readDialogueNarrativeMetadata(),
      outcomes: readList("dialogue-outcomes"),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      ...readVillagerEquipment("dialogue"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else {
    entry = readBooleans("opening", ["first_conversation_only", "first_village_interaction_only"], {
      id: readValue("dialogue-id").trim(),
      ...readDialogueTextFields(),
      ...readDialogueNarrativeMetadata(),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      ...readVillagerEquipment("dialogue"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  }
  return {
    ...preservedHiddenDialogueFields(kind, entry),
    ...entry
  };
}

function readBeta12DialogueLineFilters() {
  if (!supportsBeta12DialogueFields()) return {};
  const result = {
    moods: readList("dialogue-moods"),
    min_mood_intensity: parseInteger(readValue("dialogue-min_mood_intensity"))
  };
  for (const attribute of CONSTANTS.socialAttributes) {
    const highKey = `requires_high_${attribute}`;
    if (readValue(`dialogue-${highKey}`) === true) result[highKey] = true;
    result[`min_${attribute}`] = parseInteger(readValue(`dialogue-min_${attribute}`));
    result[`max_${attribute}`] = parseInteger(readValue(`dialogue-max_${attribute}`));
  }
  return result;
}

function readDialogueConditions() {
  if (!supportsBeta12DialogueFields()) return [];
  return parseJsonArrayField("dialogue-conditions", "Conditions JSON") ?? [];
}

function readDialogueItemPayment() {
  const source = readValue("dialogue-give_items").trim();
  if (!source) return {};
  try {
    const value = JSON.parse(stripTextBom(source));
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return { give_items: value };
    }
  } catch {
    // Toast below gives the user one clear correction.
  }
  showToast("Give items JSON must be a JSON object.");
  return {};
}

function saveDialogueEntry(event) {
  event.preventDefault();
  upsertEntry("dialogue", activeDialogueKind, cleanObject(readDialogueEntry()));
}

function parseJsonArrayField(id, label, options = {}) {
  const source = readValue(id).trim();
  if (!source) return [];
  try {
    const value = JSON.parse(stripTextBom(source));
    if (Array.isArray(value)) return value;
  } catch {
    // Toast below gives the user one clear correction.
  }
  if (!options.quiet) showToast(`${label} must be a JSON array.`);
  return null;
}

function parseJsonObjectOrArrayField(id, label, options = {}) {
  const source = readValue(id).trim();
  if (!source) return {};
  try {
    const value = JSON.parse(stripTextBom(source));
    if (value && typeof value === "object") return value;
  } catch {
    // Toast below gives the user one clear correction.
  }
  if (!options.quiet) showToast(`${label} must be a JSON object or array.`);
  return null;
}

function readForcedDialogueEntry(options = {}) {
  const outputMode = readValue("forced-output_mode") || "forced_dialogue";
  const isForcedOutput = outputMode === "forced_dialogue";
  const dialogueOptions = isForcedOutput ? parseJsonArrayField("forced-options_json", "Options JSON", options) : [];
  if (dialogueOptions === null) return null;
  const leaveOption = isForcedOutput ? parseJsonObjectOrArrayField("forced-leave_option_json", "Leave option JSON", options) : {};
  if (leaveOption === null) return null;
  const output = { mode: outputMode };
  const outputRadius = parseNumber(readValue("forced-output_radius"));
  if (outputMode === "chat" && Number.isFinite(outputRadius)) {
    output.radius = outputRadius;
  }
  const entry = {
    id: readValue("forced-id").trim(),
    trigger: readValue("forced-trigger"),
    priority: parseInteger(readValue("forced-priority")),
    chance: parseNumber(readValue("forced-chance")),
    witness_radius: parseInteger(readValue("forced-witness_radius")),
    requires_line_of_sight: readValue("forced-requires_line_of_sight"),
    output,
    witness_professions: readList("forced-witness_professions"),
    ...readVillagerEquipment("forced", "witness"),
    player_items: readList("forced-player_items"),
    player_item_slots: readList("forced-player_item_slots"),
    ...readPlayerItemDurability("forced"),
    ...readPlayerItemEnchantments("forced"),
    loot_tables: readList("forced-loot_tables"),
    target_entity_types: readList("forced-target_entity_types"),
    min_recent_retaliations: parseInteger(readValue("forced-min_recent_retaliations")),
    max_recent_retaliations: parseInteger(readValue("forced-max_recent_retaliations"))
  };
  if (isForcedOutput) {
    entry.initiate_dialogue = readValue("forced-initiate_dialogue");
    entry.aggro_immediately = readValue("forced-aggro_immediately");
    entry.force_camera_towards_villager = readValue("forced-force_camera_towards_villager");
    entry.reputation = parseInteger(readValue("forced-reputation"));
    entry.options = dialogueOptions;
    if (Array.isArray(leaveOption)) {
      entry.leave_options = leaveOption;
    } else {
      entry.leave_option = leaveOption;
    }
  }
  const lines = readForcedDialogueLines();
  if (lines.length === 1) {
    entry.line = lines[0];
  } else if (lines.length > 1) {
    entry.lines = lines;
  }
  return entry;
}

function saveForcedDialogue(event) {
  event.preventDefault();
  const entry = readForcedDialogueEntry();
  if (!entry) return;
  upsertEntry("forcedDialogue", "entries", cleanObject(entry));
}

function readNotificationEntry() {
  return readBooleans("notification", [], {
    id: readValue("notification-id").trim(),
    trigger: readValue("notification-trigger").trim(),
    ...readNotificationTextFields(),
    kind: readValue("notification-kind"),
    world_text_kind: readValue("notification-world_text_kind"),
    color: readValue("notification-color").trim(),
    text_color: readValue("notification-text_color").trim(),
    chat_color: readValue("notification-chat_color").trim(),
    professions: readList("notification-professions"),
    ...readVillagerEquipment("notification"),
    reputation_levels: readList("notification-reputation_levels"),
    target_entity_types: readList("notification-target_entity_types"),
    min_reputation: parseInteger(readValue("notification-min_reputation")),
    max_reputation: parseInteger(readValue("notification-max_reputation")),
    player_items: readList("notification-player_items"),
    player_item_slots: readList("notification-player_item_slots"),
    ...readPlayerItemDurability("notification"),
    ...readPlayerItemEnchantments("notification"),
    weight: parseInteger(readValue("notification-weight")),
    chance: parseNumber(readValue("notification-chance"))
  });
}

function saveNotification(event) {
  event.preventDefault();
  upsertEntry("notifications", "notifications", cleanObject(readNotificationEntry()));
}

function readGiftEntry() {
  const kind = activeGiftKind;
  return kind === "preferences"
    ? {
        reaction: readValue("gift-reaction"),
        items: readList("gift-items"),
        tags: readList("gift-tags"),
        professions: readList("gift-professions"),
        ...readVillagerEquipment("gift"),
        reputation_per_item: parseInteger(readValue("gift-reputation_per_item")),
        response_key: readValue("gift-response_key").trim(),
        priority: parseInteger(readValue("gift-priority"))
      }
    : {
        item: readValue("gift-item").trim(),
        professions: readList("gift-professions"),
        ...readVillagerEquipment("gift"),
        reputation_levels: readList("gift-reputation_levels"),
        min_count: parseInteger(readValue("gift-min_count")),
        max_count: parseInteger(readValue("gift-max_count")),
        weight: parseInteger(readValue("gift-weight"))
      };
}

function saveGiftEntry(event) {
  event.preventDefault();
  upsertEntry("gifts", activeGiftKind, cleanObject(readGiftEntry()));
}

function readPacificationEntry() {
  return {
    items: readList("pacification-items"),
    tags: readList("pacification-tags"),
    professions: readList("pacification-professions"),
    ...readVillagerEquipment("pacification"),
    count: parseInteger(readValue("pacification-count")),
    min_count: parseInteger(readValue("pacification-min_count")),
    max_count: parseInteger(readValue("pacification-max_count")),
    name: readValue("pacification-name").trim(),
    plural_name: readValue("pacification-plural_name").trim(),
    priority: parseInteger(readValue("pacification-priority"))
  };
}

function savePacification(event) {
  event.preventDefault();
  upsertEntry("pacification", "payments", cleanObject(readPacificationEntry()));
}

function readStoryEntry() {
  const kind = activeStoryKind;
  const ids = kind === "structures" ? readList("story-structures") : readList("story-biomes");
  return kind === "structures"
    ? {
        structures: ids,
        name: readValue("story-name").trim(),
        radius: parseInteger(readValue("story-radius"))
      }
    : {
        biomes: ids,
        name: readValue("story-name").trim()
      };
}

function saveStoryEntry(event) {
  event.preventDefault();
  upsertEntry("stories", activeStoryKind, cleanObject(readStoryEntry()));
}

function saveActiveEntryForm() {
  const form = els.panel.querySelector(".entry-form");
  if (!form) return false;
  form.requestSubmit();
  return true;
}

function markEntryFormDirty() {
  if (!els.panel.querySelector(".entry-form")) return;
  const wasDirty = entryFormDirty;
  entryFormDirty = true;
  if (!wasDirty) {
    selectedPath = currentEntryPath();
  }
  els.panel.querySelector(".entry-form")?.classList.add("has-unsaved-changes");
}

function currentEntryPath() {
  if (editing) {
    const existing = state[editing.section]?.[editing.kind]?.[editing.index];
    if (existing) return entryPath(editing.section, existing, editing.kind, editing.index);
  }
  return inferSelectedPath(activeSection);
}

function selectEntryForEditing(section, kind, index, { scrollPreview = true } = {}) {
  editing = { section, kind, index };
  const entry = state[section]?.[kind]?.[index];
  if (entry) selectedPath = entryPath(section, entry, kind, index);
  pendingPreviewEntryScroll = scrollPreview;
  clearEntryFormDirty();
}

function clearEntryFormDirty() {
  entryFormDirty = false;
  document.body.classList.remove("is-unsaved-shaking");
}

function warnUnsavedEntry() {
  window.clearTimeout(unsavedShakeTimer);
  document.body.classList.remove("is-unsaved-shaking");
  void document.body.offsetWidth;
  document.body.classList.add("is-unsaved-shaking");
  unsavedShakeTimer = window.setTimeout(() => {
    document.body.classList.remove("is-unsaved-shaking");
  }, 260);
  showToast("Save or clear the current entry before leaving it.");
}

function canLeaveEntryForm() {
  if (!entryFormDirty) return true;
  warnUnsavedEntry();
  return false;
}

function upsertEntry(section, kind, entry) {
  const previousSelectedPath = selectedPath;
  let sourcePath = "";
  let savedIndex = state[section][kind].length;
  if (editing && editing.section === section && editing.kind === kind) {
    savedIndex = editing.index;
    const existing = state[section][kind][editing.index];
    if (existing?.__sourcePath) {
      entry.__sourcePath = existing.__sourcePath;
      sourcePath = existing.__sourcePath;
    } else {
      sourcePath = entryPath(section, entry, kind, savedIndex);
    }
    state[section][kind][editing.index] = entry;
    showToast("Entry updated.");
  } else {
    state[section][kind].push(entry);
    sourcePath = entryPath(section, entry, kind, savedIndex);
    showToast("Entry added.");
  }
  editing = { section, kind, index: savedIndex };
  clearEntryFormDirty();
  selectedPath = selectedPathAfterEntrySave(section, previousSelectedPath, sourcePath);
  renderPreservingEntryListScroll();
}

function selectedPathAfterEntrySave(section, previousSelectedPath, sourcePath = "") {
  const files = generatedFiles();
  if (sourcePath && Object.hasOwn(files, sourcePath)) return sourcePath;
  if (previousSelectedPath && Object.hasOwn(files, previousSelectedPath)) return previousSelectedPath;
  return inferSelectedPath(section);
}

function inferSelectedPath(section) {
  if (section === "dialogue") return dialoguePath(activeDialogueKind, state.dialogue[activeDialogueKind]?.[0] || {}, 0);
  if (section === "forcedDialogue") return forcedDialoguePath();
  if (section === "notifications") return notificationsPath();
  if (section === "gifts") return giftsPath();
  if (section === "pacification") return pacificationPath();
  if (section === "stories") return activeStoryKind === "structures" ? structurePath() : biomePath();
  if (section === "names") return namesPath();
  return selectedPath;
}

function clearEditing() {
  editing = null;
  clearEntryFormDirty();
  render();
}

function focusWithoutScroll(element) {
  const scrollParents = [];
  let parent = element.parentElement;
  while (parent) {
    if (parent.scrollHeight > parent.clientHeight || parent.scrollWidth > parent.clientWidth) {
      scrollParents.push({ node: parent, top: parent.scrollTop, left: parent.scrollLeft });
    }
    parent = parent.parentElement;
  }
  const windowScroll = { x: window.scrollX, y: window.scrollY };
  try {
    element.focus({ preventScroll: true });
  } catch {
    element.focus();
  }
  for (const scrollParent of scrollParents) {
    scrollParent.node.scrollTop = scrollParent.top;
    scrollParent.node.scrollLeft = scrollParent.left;
  }
  window.scrollTo(windowScroll.x, windowScroll.y);
}

function insertTag(targetId, value) {
  const input = document.querySelector(`#${CSS.escape(targetId)}`);
  if (!input) return;
  const values = parseList(input.value);
  if (!values.includes(value)) {
    values.push(value);
    input.value = values.join(", ");
    input.dispatchEvent(new Event("input", { bubbles: true }));
  }
  focusWithoutScroll(input);
}

function deleteEntry(section, kind, index) {
  if (!canLeaveEntryForm()) return;
  state[section][kind].splice(index, 1);
  editing = null;
  clearEntryFormDirty();
  showToast("Entry deleted.");
  render();
}

function reorderEntry(section, kind, fromIndex, toIndex) {
  const collection = state[section]?.[kind];
  if (!Array.isArray(collection) || collection.length <= 1) return;
  if (!Number.isInteger(fromIndex) || !Number.isInteger(toIndex) || fromIndex === toIndex) return;
  if (fromIndex < 0 || fromIndex >= collection.length || toIndex < 0 || toIndex >= collection.length) return;
  const [entry] = collection.splice(fromIndex, 1);
  collection.splice(toIndex, 0, entry);
  if (editing?.section === section && editing.kind === kind) {
    editing.index = movedIndex(editing.index, fromIndex, toIndex);
  }
  selectedPath = Object.hasOwn(generatedFiles(), selectedPath) ? selectedPath : inferSelectedPath(section);
  showToast("Entry moved.");
  render();
}

function movedIndex(index, fromIndex, toIndex) {
  if (index === fromIndex) return toIndex;
  if (fromIndex < index && toIndex >= index) return index - 1;
  if (fromIndex > index && toIndex <= index) return index + 1;
  return index;
}

function clearEntryDropIndicators() {
  document.querySelectorAll(".entry-card.is-drop-before, .entry-card.is-drop-after, .tree-row.is-drop-before, .tree-row.is-drop-after").forEach((card) => {
    card.classList.remove("is-drop-before", "is-drop-after");
  });
}

function entryDropIndex(event, card) {
  const targetIndex = Number(card.dataset.index);
  const midpoint = card.getBoundingClientRect().top + card.getBoundingClientRect().height / 2;
  const afterTarget = event.clientY > midpoint;
  let toIndex = targetIndex + (afterTarget ? 1 : 0);
  if (entryDragState && entryDragState.index < toIndex) toIndex -= 1;
  return {
    toIndex,
    placement: afterTarget ? "after" : "before"
  };
}

function addDialogueExample() {
  const slug = state.meta.slug || "my_pack";
  let entry;
  if (activeDialogueKind === "options") {
    entry = {
      id: `${slug}.ask_local_rumors`,
      label: "Ask Local Rumors",
      type: "dialogue_option",
      request: "story",
      order: 30,
      show_for_babies: false
    };
  } else if (activeDialogueKind === "lines") {
    entry = cleanObject({
      id: `${slug}.rumor.generic`,
      option: `${slug}.ask_local_rumors`,
      request: "story",
      moods: supportsBeta12DialogueFields() ? ["grateful"] : undefined,
      min_mood_intensity: supportsBeta12DialogueFields() ? 25 : undefined,
      requires_high_knowledge: supportsBeta12DialogueFields() ? true : undefined,
      text: "Roads keep secrets. Villages keep better ones.",
      weight: 10
    });
  } else if (activeDialogueKind === "messages") {
    entry = {
      id: `${slug}.gift.librarian.rare_book`,
      key: `${slug}.gift.librarian.rare_book`,
      text: "{gift_item}? This belongs near a reading lamp, not forgotten in a chest."
    };
  } else if (activeDialogueKind === "pacify") {
    entry = {
      id: `${slug}.pacify.accepted`,
      text: "Fine. {payment_cost} {payment_items}, and we try peace again.",
      outcomes: ["success"],
      weight: 10
    };
  } else {
    entry = {
      id: `${slug}.${activeDialogueKind}.farmer`,
      text: "Good to see a steady face.",
      professions: ["farmer"],
      weight: 10
    };
  }
  const index = state.dialogue[activeDialogueKind].length;
  state.dialogue[activeDialogueKind].push(entry);
  selectedPath = dialoguePath(activeDialogueKind, entry, index);
  render();
}

function addForcedDialogueExample() {
  const slug = state.meta.slug || "my_pack";
  state.forcedDialogue.entries.push({
    id: `${slug}.container_theft.warning`,
    trigger: "container_theft",
    output: {
      mode: "forced_dialogue"
    },
    lines: [
      "Stop right there. I saw you take {stolen_stack}.",
      "That {container} is not yours to empty. Put {stolen_stack} back.",
      "Village stores are not free supplies. Return {stolen_stack}."
    ],
    priority: 20,
    loot_tables: ["minecraft:chests/village/village_plains_house"],
    witness_professions: ["farmer"],
    witness_radius: 12,
    requires_line_of_sight: true,
    initiate_dialogue: true,
    force_camera_towards_villager: true,
    aggro_immediately: false,
    reputation: -5,
    options: [
      {
        id: `${slug}.return_items`,
        label: "I'll put it back.",
        response: "See that you do.",
        reputation: 2,
        end_conversation: true,
        order: 0,
        take_stolen_items: {
          destination: "villager_inventory_then_source_container",
          failure_response: "You do not have {stolen_stack} to return.",
          failure_reputation: -2,
          failure_end_conversation: false
        }
      },
      {
        id: `${slug}.offer_payment`,
        label: "Offer payment.",
        response: "That will help replace what you disturbed.",
        reputation_levels: ["neutral", "suspicious"],
        take_items: {
          items: ["minecraft:emerald"],
          count: 8,
          destination: "villager_inventory",
          overflow_destination: "drop_at_villager",
          failure_response: "You do not have enough emeralds to make that offer.",
          failure_reputation: -2,
          failure_end_conversation: false
        },
        reputation: 2,
        end_conversation: true,
        order: 5
      },
      {
        id: `${slug}.trusted_warning`,
        label: "Accept warning.",
        response: "You have earned some trust here. Keep it by leaving village stores alone.",
        reputation_levels: ["trusted", "respected", "revered", "royalty"],
        reputation: 1,
        end_conversation: true,
        order: 6
      },
      {
        id: `${slug}.refuse`,
        label: "Try and stop me.",
        response: "Then you leave me no choice.",
        reputation: -10,
        aggro: true,
        end_conversation: true,
        order: 10
      }
    ],
    leave_options: [
      {
        label: "Leave",
        response: "I will take {stolen_items} back. Go, and do not make me regret letting you leave.",
        reputation_levels: ["trusted", "respected", "revered", "royalty"],
        reputation: -2,
        aggro_chance: 0.05,
        end_conversation: true,
        order: 1000,
        take_stolen_items: {
          destination: "villager_inventory_then_source_container",
          failure_response: "You no longer have {stolen_items}. Then we are past excuses.",
          failure_reputation: -5,
          failure_aggro: true,
          failure_end_conversation: true
        }
      },
      {
        label: "Leave",
        response: "I will take {stolen_items} back. Walking away does not make this settled.",
        reputation_levels: ["neutral", "suspicious"],
        reputation: -4,
        aggro_chance: 0.25,
        end_conversation: true,
        order: 1001,
        take_stolen_items: {
          destination: "villager_inventory_then_source_container",
          failure_response: "You no longer have {stolen_items}. Then we are past excuses.",
          failure_reputation: -5,
          failure_aggro: true,
          failure_end_conversation: true
        }
      },
      {
        label: "Leave",
        response: "No. I will take {stolen_items} back, and you are done running from this.",
        reputation_levels: ["hostile", "despised", "feared"],
        reputation: -8,
        aggro_chance: 0.75,
        end_conversation: true,
        order: 1002,
        take_stolen_items: {
          destination: "villager_inventory_then_source_container",
          failure_response: "You no longer have {stolen_items}. Then we are past excuses.",
          failure_reputation: -5,
          failure_aggro: true,
          failure_end_conversation: true
        }
      }
    ]
  });
  selectedPath = forcedDialoguePath();
  render();
}

function addNotificationExample() {
  const slug = state.meta.slug || "my_pack";
  state.notifications.notifications.push({
    id: `${slug}.ambient.trusted_farmer`,
    trigger: "ambient.murmur",
    text: "Good harvest follows good neighbors",
    world_text_kind: "murmur",
    professions: ["farmer"],
    reputation_levels: ["trusted", "respected", "revered", "royalty"],
    color: "#DCEBA6",
    weight: 20
  });
  selectedPath = notificationsPath();
  render();
}

function addGiftExample() {
  if (activeGiftKind === "preferences") {
    state.gifts.preferences.push({
      professions: ["librarian"],
      reaction: "loved",
      items: ["minecraft:enchanted_book", "minecraft:name_tag"],
      response_key: `${state.meta.slug}.gift.librarian.rare_book`,
      priority: 20
    });
  } else {
    state.gifts.rewards.push({
      professions: ["librarian"],
      reputation_levels: ["revered", "royalty"],
      item: "minecraft:book",
      min_count: 2,
      max_count: 5,
      weight: 10
    });
  }
  selectedPath = giftsPath();
  render();
}

function addPacificationExample() {
  state.pacification.payments.push({
    items: ["minecraft:emerald", "minecraft:diamond"],
    min_count: 3,
    max_count: 32
  });
  selectedPath = pacificationPath();
  render();
}

function addStoryExample() {
  if (activeStoryKind === "structures") {
    state.stories.structures.push({
      structure: "examplemod:haunted_keep",
      name: "Haunted Keep",
      radius: 128
    });
    selectedPath = structurePath();
  } else {
    state.stories.biomes.push({
      biome: "examplemod:crystal_marsh",
      name: "Crystal Marsh"
    });
    selectedPath = biomePath();
  }
  render();
}

function addNameExample() {
  state.names.male_names = unique([...state.names.male_names, "Ada", "Bram"]);
  state.names.female_names = unique([...state.names.female_names, "Cora", "Dorian"]);
  selectedPath = namesPath();
  render();
}

function templateRequestFileName(index, request) {
  return `${String(index).padStart(2, "0")}_${request}.json`;
}

function dialogueFolderTemplateFiles() {
  const files = {
    "README.md": [
      "# Villager Retaliation Dialogue Folder Template",
      "",
      "This beta.12 template gives pack developers a folder-first starting point.",
      "Every dialogue request has one custom option and one response line with the text `example`.",
      "Replace ids, labels, filters, and text as your pack grows."
    ].join("\n") + "\n"
  };
  const dialogueRoot = "data/villagerretaliation/dialogue/en_us/example_template";

  CONSTANTS.dialogueTypes.forEach((request, index) => {
    const fileName = templateRequestFileName(index, request);
    const optionId = `example_template.option.${request}`;
    const option = {
      id: optionId,
      label: `Example ${humanize(request)}`,
      request,
      order: index
    };
    const line = {
      id: `example_template.line.${request}`,
      request,
      option: optionId,
      text: "example",
      weight: 10
    };
    if (request === "share_story") line.story_biome = "minecraft:plains";
    files[`${dialogueRoot}/options/${fileName}`] = safeJson(option);
    files[`${dialogueRoot}/lines/${fileName}`] = safeJson(line);
  });

  files[`${dialogueRoot}/messages/00_example.json`] = safeJson({
    id: "example_template.message.example",
    key: "example_template.message.example",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/openings/00_example.json`] = safeJson({
    id: "example_template.opening.example",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/closings/00_example.json`] = safeJson({
    id: "example_template.closing.example",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/pacify/00_example_success.json`] = safeJson({
    id: "example_template.pacify.success",
    outcomes: ["success"],
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/groups/example_group/lines/00_example.json`] = safeJson({
    id: "example_template.group.example",
    request: "question",
    professions: ["farmer", "librarian"],
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/options/00_example.json`] = safeJson({
    id: "example_template.farmer.option.question",
    label: "Example Farmer Question",
    request: "question",
    order: 20
  });
  files[`${dialogueRoot}/professions/farmer/lines/00_example.json`] = safeJson({
    id: "example_template.farmer.line.question",
    request: "question",
    option: "example_template.farmer.option.question",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/messages/00_example.json`] = safeJson({
    id: "example_template.farmer.message",
    key: "example_template.farmer.message",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/openings/00_example.json`] = safeJson({
    id: "example_template.farmer.opening",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/closings/00_example.json`] = safeJson({
    id: "example_template.farmer.closing",
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/pacify/00_example_success.json`] = safeJson({
    id: "example_template.farmer.pacify.success",
    outcomes: ["success"],
    text: "example",
    weight: 10
  });
  files[`${dialogueRoot}/professions/farmer/share_stories/00_example_structure.json`] = safeJson({ lines: [{
    id: "example_template.farmer.share_story.structure",
    request: "share_story",
    option: "adult_share_story",
    story_structure: "minecraft:village_plains",
    text: "example",
    weight: 10
  }] });
  files[`${dialogueRoot}/professions/farmer/share_stories/01_example_biome.json`] = safeJson({ lines: [{
    id: "example_template.farmer.share_story.biome",
    request: "share_story",
    option: "adult_share_story",
    story_biome: "minecraft:plains",
    text: "example",
    weight: 10
  }] });
  files[`${dialogueRoot}/professions/unemployed/baby_share_stories/00_example_structure.json`] = safeJson({ lines: [{
    id: "example_template.baby.share_story.structure",
    request: "share_story",
    option: "baby_share_story",
    show_for_adults: false,
    show_for_babies: true,
    story_structure: "minecraft:village_plains",
    text: "example",
    weight: 10
  }] });
  files[`${dialogueRoot}/professions/unemployed/baby_share_stories/01_example_biome.json`] = safeJson({ lines: [{
    id: "example_template.baby.share_story.biome",
    request: "share_story",
    option: "baby_share_story",
    show_for_adults: false,
    show_for_babies: true,
    story_biome: "minecraft:plains",
    text: "example",
    weight: 10
  }] });

  files["data/villagerretaliation/forced_dialogue/example_template/00_container_theft.json"] = safeJson({
    entries: [
      {
        id: "example_template.forced.container_theft",
        trigger: "container_theft",
        output: { mode: "forced_dialogue" },
        line: "example",
        witness_radius: 10,
        requires_line_of_sight: true,
        initiate_dialogue: true,
        options: [
          {
            id: "example_template.forced.option.example",
            label: "Example",
            response: "example",
            end_conversation: true,
            order: 0
          }
        ]
      }
    ]
  });
  files["data/villagerretaliation/notifications/en_us/example_template/00_ambient.json"] = safeJson({
    notifications: [
      {
        id: "example_template.notification.ambient",
        trigger: "ambient.murmur",
        text: "example",
        world_text_kind: "murmur",
        weight: 10
      }
    ]
  });
  files["data/villagerretaliation/gifts/example_template/00_gifts.json"] = safeJson({
    preferences: [
      {
        id: "example_template.gift.preference",
        reaction: "liked",
        items: ["minecraft:emerald"],
        response_key: "example_template.message.example",
        priority: 10
      }
    ],
    rewards: [
      {
        id: "example_template.gift.reward",
        reputation_levels: ["trusted"],
        item: "minecraft:bread",
        min_count: 1,
        max_count: 1,
        weight: 10
      }
    ]
  });
  files["data/villagerretaliation/pacification/example_template/00_payments.json"] = safeJson({
    payments: [
      {
        items: ["minecraft:emerald"],
        min_count: 1,
        max_count: 1
      }
    ]
  });
  files["data/villagerretaliation/villager_names/example_template_names.json"] = safeJson({
    male_names: ["Example"],
    female_names: ["Example"]
  });
  files["data/villagerretaliation/profession_loot/example_template/00_loot.json"] = safeJson({
    tables: [
      {
        id: "example_template.farmer.common",
        professions: ["farmer"],
        loot_table: "example_template:villager/profession/farmer/common",
        chance: "always"
      }
    ]
  });
  files["data/example_template/story_structures/00_example_structures.json"] = safeJson({
    radius: 96,
    entries: [
      {
        structure: "minecraft:village_plains",
        name: "Example Structure"
      }
    ]
  });
  files["data/example_template/story_biomes/00_example_biomes.json"] = safeJson({
    entries: [
      {
        biome: "minecraft:plains",
        name: "Example Biome"
      }
    ]
  });
  files["data/example_template/loot_table/villager/profession/farmer/common.json"] = safeJson({
    type: "minecraft:entity",
    pools: [
      {
        rolls: 1,
        entries: [
          {
            type: "minecraft:item",
            name: "minecraft:bread"
          }
        ]
      }
    ]
  });

  return files;
}

function loadStarterPack() {
  state = createInitialState();
  state.meta.packName = "Village Rumors";
  state.meta.description = "Starter Villager Retaliation datapack";
  state.meta.namespace = "village_rumors";
  state.meta.slug = "village_rumors";
  state.dialogue.layout = "folders";
  state.dialogue.fileName = "village_rumors_dialogue";
  state.dialogue.folderName = "village_rumors";
  state.forcedDialogue.fileName = "village_rumors_forced_dialogue";
  state.notifications.fileName = "village_rumors_notifications";
  state.gifts.fileName = "village_rumors_gifts";
  state.stories.namespace = "village_rumors";
  state.stories.structureFileName = "village_rumors_structures";
  state.stories.biomeFileName = "village_rumors_biomes";
  state.dialogue.options.push({
    id: "village_rumors.ask_local_rumors",
    label: "Ask Local Rumors",
    type: "dialogue_option",
    request: "story",
    order: 30,
    show_for_babies: false
  });
  state.dialogue.lines.push(
    {
      id: "village_rumors.rumor.generic",
      option: "village_rumors.ask_local_rumors",
      request: "story",
      text: "Roads keep secrets. Villages keep better ones.",
      weight: 10
    },
    {
      id: "village_rumors.share_story.haunted_keep",
      request: "share_story",
      option: "adult_share_story",
      story_structure: "examplemod:haunted_keep",
      text: "{target_article}. If you found it, walk home before dark.",
      weight: 30
    }
  );
  state.dialogue.messages.push({
    id: "village_rumors.gift.librarian.rare_book",
    key: "village_rumors.gift.librarian.rare_book",
    text: "{gift_item}? This belongs near a reading lamp, not forgotten in a chest."
  });
  state.forcedDialogue.entries.push({
    id: "village_rumors.container_theft.warning",
    trigger: "container_theft",
    output: {
      mode: "forced_dialogue"
    },
    line: "Stop right there. That chest is not yours.",
    priority: 20,
    witness_radius: 12,
    requires_line_of_sight: true,
    initiate_dialogue: true,
    aggro_immediately: false,
    reputation: -5,
    options: [
      {
        id: "village_rumors.apologize",
        label: "Sorry. I'll put it back.",
        response: "Apology heard. Action expected.",
        reputation: 2,
        end_conversation: true,
        order: 0
      },
      {
        id: "village_rumors.refuse",
        label: "It is mine now.",
        response: "Then we settle this the hard way.",
        reputation_levels: ["suspicious", "hostile", "despised", "feared"],
        reputation: -10,
        aggro: true,
        end_conversation: true,
        order: 10
      },
      {
        id: "village_rumors.trusted_warning",
        label: "Accept warning.",
        response: "I know your better choices too. Let this be one of them.",
        reputation_levels: ["trusted", "respected", "revered", "royalty"],
        reputation: 1,
        end_conversation: true,
        order: 5
      }
    ]
  }, {
    id: "village_rumors.retaliation_started.callout",
    trigger: "retaliation_started",
    output: {
      mode: "chat",
      radius: 24
    },
    lines: [
      "You picked the wrong village to threaten.",
      "Stand back. This one has made enemies here.",
      "Weapons ready. Trouble found us."
    ],
    priority: 30,
    chance: 0.75,
    witness_radius: 24,
    requires_line_of_sight: false,
    target_entity_types: ["minecraft:player"]
  });
  state.notifications.notifications.push({
    id: "village_rumors.ambient.trusted_farmer",
    trigger: "ambient.murmur",
    text: "Good harvest follows good neighbors",
    world_text_kind: "murmur",
    professions: ["farmer"],
    reputation_levels: ["trusted", "respected", "revered", "royalty"],
    color: "#DCEBA6",
    weight: 20
  });
  state.gifts.preferences.push({
    professions: ["librarian"],
    reaction: "loved",
    items: ["minecraft:enchanted_book", "minecraft:name_tag"],
    response_key: "village_rumors.gift.librarian.rare_book",
    priority: 20
  });
  state.gifts.rewards.push({
    professions: ["librarian"],
    reputation_levels: ["revered", "royalty"],
    item: "minecraft:book",
    min_count: 2,
    max_count: 5,
    weight: 10
  });
  state.stories.structures.push({
    structure: "examplemod:haunted_keep",
    name: "Haunted Keep",
    radius: 128
  });
  state.stories.biomes.push({
    biome: "examplemod:crystal_marsh",
    name: "Crystal Marsh"
  });
  selectedPath = dialoguePath("options", state.dialogue.options[0] || {}, 0);
  editing = null;
  resetFileTreeExpansion();
  clearEntryFormDirty();
  render();
  showToast("Starter pack loaded.");
}

function loadDialogueFolderTemplate() {
  state = createInitialState();
  state.meta.packName = "VR Dialogue Folder Template";
  state.meta.description = "Folderized Villager Retaliation beta.12 dialogue template";
  state.meta.namespace = "example_template";
  state.meta.slug = "example_template";
  state.meta.locale = "en_us";
  state.meta.packVersion = CURRENT_PACK_VERSION;
  state.meta.packFormat = packVersionInfo(CURRENT_PACK_VERSION).packFormat;
  state.stories.namespace = "example_template";
  state.dialogue.layout = "folders";
  state.dialogue.folderName = "example_template";
  const templateFiles = dialogueFolderTemplateFiles();
  ingestFiles(templateFiles);
  state.dialogue.fileName = "example_template_dialogue";
  state.dialogue.folderName = "example_template";
  state.forcedDialogue.fileName = "example_template/00_container_theft";
  state.notifications.fileName = "example_template/00_ambient";
  state.gifts.fileName = "example_template/00_gifts";
  state.pacification.fileName = "example_template/00_payments";
  state.stories.structureFileName = "00_example_structures";
  state.stories.biomeFileName = "00_example_biomes";
  state.names.male_names = [];
  state.names.female_names = [];
  state.extraFiles["data/villagerretaliation/villager_names/example_template_names.json"] = templateFiles["data/villagerretaliation/villager_names/example_template_names.json"];
  activeSection = "dialogue";
  activeDialogueKind = "options";
  selectedPath = "data/villagerretaliation/dialogue/en_us/example_template/options/00_greeting.json";
  editing = null;
  resetFileTreeExpansion();
  clearEntryFormDirty();
  render();
  showToast("Dialogue folder template loaded.");
}

function renderTemplateChoices() {
  if (!els.templateList) return;
  els.templateList.innerHTML = TEMPLATE_CHOICES.map((template) => `
    <button class="template-choice" type="button" data-template-id="${escapeHtml(template.id)}">
      ${icon(template.icon, "inline-icon")}
      <span>
        <strong>${escapeHtml(template.label)}</strong>
        <small>${escapeHtml(template.detail)}</small>
      </span>
    </button>
  `).join("");
}

function openTemplateDialog() {
  renderTemplateChoices();
  els.templateDialog.classList.add("is-open");
  els.templateDialog.setAttribute("aria-hidden", "false");
  renderIcons();
  window.setTimeout(() => els.templateList?.querySelector(".template-choice")?.focus(), 0);
}

function closeTemplateDialog() {
  els.templateDialog.classList.remove("is-open");
  els.templateDialog.setAttribute("aria-hidden", "true");
}

function loadTemplateChoice(id) {
  closeTemplateDialog();
  if (id === "dialogue-folder") {
    loadDialogueFolderTemplate();
  } else {
    loadStarterPack();
  }
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function updateOverviewFromInput(target) {
  const id = target.id;
  if (id === "meta-packName") state.meta.packName = target.value;
  if (id === "meta-description") state.meta.description = target.value;
  if (id === "meta-packVersion") {
    const previousDefault = packVersionInfo().packFormat;
    state.meta.packVersion = normalizePackVersion(target.value) || CURRENT_PACK_VERSION;
    const nextDefault = packVersionInfo().packFormat;
    if (!state.meta.packFormat || state.meta.packFormat === previousDefault) {
      state.meta.packFormat = nextDefault;
    }
  }
  if (id === "meta-packFormat") state.meta.packFormat = parseInteger(target.value) || 34;
  if (id === "meta-namespace") {
    state.meta.namespace = namespaceify(target.value);
    state.stories.namespace = state.meta.namespace;
  }
  if (id === "meta-slug") {
    const slug = normalizeFileName(target.value, "my_pack");
    state.meta.slug = slug;
    state.dialogue.fileName = `${slug}_dialogue`;
    state.dialogue.folderName = slug;
    state.forcedDialogue.fileName = `${slug}_forced_dialogue`;
    state.notifications.fileName = `${slug}_notifications`;
    state.gifts.fileName = `${slug}_gifts`;
    state.pacification.fileName = `${slug}_pacification`;
    state.stories.structureFileName = `${slug}_structures`;
    state.stories.biomeFileName = `${slug}_biomes`;
  }
  if (id === "meta-locale") state.meta.locale = slugify(target.value, "en_us");
}

function updateSectionSettings(target) {
  if (target.id === "dialogue-layout") state.dialogue.layout = target.value === "bundle" ? "bundle" : "folders";
  if (target.id === "dialogue-folderName") state.dialogue.folderName = normalizeFileName(target.value, state.meta.slug || "my_pack");
  if (target.id === "dialogue-fileName") state.dialogue.fileName = normalizeFileName(target.value, `${state.meta.slug}_dialogue`);
  if (target.id === "dialogue-locale") state.meta.locale = slugify(target.value, "en_us");
  if (target.id === "forcedDialogue-fileName") state.forcedDialogue.fileName = normalizeFileName(target.value, `${state.meta.slug}_forced_dialogue`);
  if (target.id === "notifications-fileName") state.notifications.fileName = normalizeFileName(target.value, `${state.meta.slug}_notifications`);
  if (target.id === "notifications-locale") state.meta.locale = slugify(target.value, "en_us");
  if (target.id === "gifts-fileName") state.gifts.fileName = normalizeFileName(target.value, `${state.meta.slug}_gifts`);
  if (target.id === "pacification-fileName") state.pacification.fileName = normalizeFileName(target.value, `${state.meta.slug}_pacification`);
  if (target.id === "stories-namespace") state.stories.namespace = namespaceify(target.value, state.meta.namespace);
  if (target.id === "stories-radius") state.stories.radius = parseInteger(target.value) || 96;
  if (target.id === "stories-structureFileName") state.stories.structureFileName = normalizeFileName(target.value, `${state.meta.slug}_structures`);
  if (target.id === "stories-biomeFileName") state.stories.biomeFileName = normalizeFileName(target.value, `${state.meta.slug}_biomes`);
  if (target.id === "names-male_names") state.names.male_names = parseList(target.value);
  if (target.id === "names-female_names") state.names.female_names = parseList(target.value);
}

function showToast(message) {
  window.clearTimeout(toastTimer);
  els.toast.textContent = message;
  els.toast.classList.add("is-visible");
  toastTimer = window.setTimeout(() => {
    els.toast.classList.remove("is-visible");
  }, 2400);
}

function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function copyCurrentFile() {
  const files = currentViewFiles();
  const value = files[selectedPath];
  if (value instanceof Uint8Array) {
    showToast("Binary files cannot be copied as text.");
    return;
  }
  await navigator.clipboard.writeText(previewDocumentValue() || "");
  showToast("Copied current file.");
}

function downloadCurrentFile() {
  const files = currentViewFiles();
  const generated = files[selectedPath] || "";
  const value = generated instanceof Uint8Array ? generated : previewDocumentValue();
  const blob = value instanceof Uint8Array
    ? new Blob([value])
    : new Blob([value], { type: "application/json" });
  downloadBlob(blob, selectedPath.split("/").pop() || "datapack-file");
}

function applyPreviewEdit() {
  if (entryFormDirty) {
    invalidateCurrentViewSnapshot();
    renderPreview();
    warnUnsavedEntry();
    return;
  }
  const source = previewDocumentValue();
  if (generatedFiles()[selectedPath] instanceof Uint8Array) return;
  const applied = applyEditedFile(selectedPath, source);
  if (!applied) {
    previewEditError = { path: selectedPath };
    previewLineHighlightRanges = [];
    invalidateCurrentViewSnapshot();
    els.codePreview.classList.add("is-invalid");
    els.preview.closest(".preview")?.classList.add("has-error");
    applyPreviewLineHighlights([]);
    renderPreviewLineNumbers(source, []);
    renderFiles();
    renderChecks();
    renderIcons();
    return;
  }
  previewEditError = null;
  invalidateCurrentViewSnapshot();
  els.codePreview.classList.remove("is-invalid");
  els.preview.closest(".preview")?.classList.remove("has-error");
  const issueRanges = withDraftState(() => previewIssueLineRanges(selectedPath, source));
  const entryRanges = previewSelectedEntryLineRanges(selectedPath, source);
  previewLineHighlightRanges = mergeLineRanges([...issueRanges, ...entryRanges]);
  applyPreviewLineHighlights(issueRanges, entryRanges);
  renderPreviewLineNumbers(source, previewLineHighlightRanges);
  renderTabs();
  renderPanel();
  updateForcedOutputModeFields(els.panel);
  resizeTextareas(els.panel);
  syncValueTags(els.panel);
  applyEntryIssueHighlights();
  renderFiles();
  renderEntryDirectory();
  renderChecks();
  renderIcons();
}

function applyEditedFile(path, source) {
  if (path === "pack.mcmeta") {
    const json = parseEditedJson(source);
    if (!json) return false;
    const pack = json.pack || {};
    if (Object.hasOwn(pack, "description")) state.meta.description = pack.description || "";
    if (Object.hasOwn(pack, "pack_format")) {
      const packFormat = Number(pack.pack_format);
      state.meta.packFormat = Number.isFinite(packFormat) ? Math.trunc(packFormat) : pack.pack_format;
    }
    return true;
  }

  if (path.match(/^data\/villagerretaliation\/dialogue\/([^/]+)\/(.+)\.json$/)) {
    const json = parseEditedJson(source);
    if (!json) return false;
    replaceDialogueFile(path, json);
    return true;
  }

  const forcedDialogueMatch = path.match(/^data\/villagerretaliation\/forced_dialogue\/(.+)\.json$/);
  if (forcedDialogueMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    replaceForcedDialogueFile(path, json);
    return true;
  }

  const notificationMatch = path.match(/^data\/villagerretaliation\/notifications\/([^/]+)\/(.+)\.json$/);
  if (notificationMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.meta.locale = notificationMatch[1];
    state.notifications.fileName = normalizeFileName(notificationMatch[2].split("/").pop(), state.notifications.fileName);
    state.notifications.notifications = cleanArray(normalizeNotificationEntries(json));
    return true;
  }

  const giftMatch = path.match(/^data\/villagerretaliation\/gifts\/(.+)\.json$/);
  if (giftMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.gifts.fileName = normalizeFileName(giftMatch[1].split("/").pop(), state.gifts.fileName);
    state.gifts.preferences = cleanArray(json.preferences);
    state.gifts.rewards = cleanArray(json.rewards);
    return true;
  }

  const pacificationMatch = path.match(/^data\/villagerretaliation\/pacification\/(.+)\.json$/);
  if (pacificationMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.pacification.fileName = normalizeFileName(pacificationMatch[1].split("/").pop(), state.pacification.fileName);
    state.pacification.payments = cleanArray(json.payments);
    return true;
  }

  const structureMatch = path.match(/^data\/([^/]+)\/story_structures\/(.+)\.json$/);
  if (structureMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.stories.namespace = structureMatch[1];
    state.stories.structureFileName = normalizeFileName(structureMatch[2].split("/").pop(), state.stories.structureFileName);
    state.stories.radius = parseInteger(json.radius) || state.stories.radius;
    state.stories.structures = cleanArray(normalizeStoryEntries(json, "structure"));
    return true;
  }

  const biomeMatch = path.match(/^data\/([^/]+)\/story_biomes\/(.+)\.json$/);
  if (biomeMatch) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.stories.namespace = biomeMatch[1];
    state.stories.biomeFileName = normalizeFileName(biomeMatch[2].split("/").pop(), state.stories.biomeFileName);
    state.stories.biomes = cleanArray(normalizeStoryEntries(json, "biome"));
    return true;
  }

  if (path === namesPath()) {
    const json = parseEditedJson(source);
    if (!json) return false;
    state.names.male_names = unique([...parseList(json.male_names), ...parseList(json.names)]);
    state.names.female_names = parseList(json.female_names);
    return true;
  }

  state.extraFiles[path] = source;
  return true;
}

function parseEditedJson(source) {
  try {
    return JSON.parse(stripTextBom(source));
  } catch {
    return null;
  }
}

function stripTextBom(source) {
  return String(source ?? "").replace(/^\uFEFF/, "");
}

function decodeTextFile(bytes) {
  return stripTextBom(decoder.decode(bytes));
}

function cleanArray(entries) {
  return Array.isArray(entries) ? entries.map((entry) => cleanObject(entry)) : [];
}

function replaceDialogueFile(path, json) {
  const info = dialoguePathInfo(path);
  state.meta.locale = info.locale;
  state.dialogue.fileName = normalizeFileName(info.relative.split("/").pop(), state.dialogue.fileName);
  if (info.kind) {
    state.dialogue.layout = "folders";
    if (info.folderName) state.dialogue.folderName = normalizeFileName(info.folderName, state.dialogue.folderName || state.meta.slug);
  }
  state.notifications.notifications = state.notifications.notifications.filter((entry) => entry.__sourcePath !== path);
  const routedNotifications = isNotificationEntry(json) ? [json] : [];
  if (info.kind) {
    state.dialogue[info.kind] = state.dialogue[info.kind].filter((entry, index) => (entry.__sourcePath || dialoguePath(info.kind, entry, index)) !== path);
    state.dialogue[info.kind].push(...withDefaultProfession(dialogueEntriesFromJson(json, info.kind), info.profession).map((entry) => ({ ...entry, __sourcePath: path })));
    delete state.extraFiles[path];
    return;
  }
  for (const kind of DIALOGUE_KIND_KEYS) {
    state.dialogue[kind] = state.dialogue[kind].filter((entry, index) => (entry.__sourcePath || dialoguePath(kind, entry, index)) !== path);
    const entries = cleanArray(json[kind]);
    const dialogueEntries = [];
    for (const entry of entries) {
      if (isNotificationEntry(entry)) {
        routedNotifications.push(entry);
      } else {
        dialogueEntries.push(entry);
      }
    }
    state.dialogue[kind].push(...dialogueEntries.map((entry) => ({ ...entry, __sourcePath: path })));
  }
  if (routedNotifications.length > 0) {
    mergeArray("notifications", "notifications", routedNotifications, path);
  }
  delete state.extraFiles[path];
}

function replaceForcedDialogueFile(path, json) {
  const forcedDialogueMatch = path.match(/^data\/villagerretaliation\/forced_dialogue\/(.+)\.json$/);
  state.forcedDialogue.fileName = normalizeFileName(forcedDialogueMatch[1].split("/").pop(), state.forcedDialogue.fileName);
  state.forcedDialogue.entries = state.forcedDialogue.entries.filter((entry) => (entry.__sourcePath || forcedDialoguePath()) !== path);
  state.forcedDialogue.entries.push(...cleanArray(normalizeForcedDialogueEntries(json)).map((entry) => ({ ...entry, __sourcePath: path })));
}

async function exportZip() {
  const checks = validate().filter((check) => check.type !== "ok");
  if (checks.length > 0 && !(await showExportIssueDialog(checks))) {
    showToast("Export canceled.");
    return;
  }
  const files = generatedFiles();
  const zip = createZip(files);
  const name = `${slugify(state.meta.packName || state.meta.slug, "villager_retaliation_pack")}.zip`;
  downloadBlob(new Blob([zip], { type: "application/zip" }), name);
  showToast(checks.length > 0 ? "Datapack zip exported with checks." : "Datapack zip exported.");
}

function showExportIssueDialog(checks) {
  if (!els.exportIssueDialog || !els.exportIssueList) return Promise.resolve(true);
  els.exportIssueList.innerHTML = checks
    .slice(0, 8)
    .map((check) => `
      <div class="modal-issue ${escapeHtml(check.type)}">
        ${icon(check.type === "error" ? "circle-alert" : check.type === "warning" ? "triangle-alert" : "info", "inline-icon")}
        <div>
          <strong>${escapeHtml(check.title)}</strong>
          <span>${escapeHtml(check.text)}</span>
        </div>
      </div>
    `)
    .join("");
  if (checks.length > 8) {
    els.exportIssueList.insertAdjacentHTML("beforeend", `<div class="modal-more">${checks.length - 8} more issue${checks.length - 8 === 1 ? "" : "s"}</div>`);
  }
  els.exportIssueDialog.classList.add("is-open");
  els.exportIssueDialog.setAttribute("aria-hidden", "false");
  renderIcons();
  els.exportIssueConfirm?.focus();
  return new Promise((resolve) => {
    exportIssueDialogResolve = resolve;
  });
}

function closeExportIssueDialog(confirmed) {
  if (!els.exportIssueDialog) return;
  els.exportIssueDialog.classList.remove("is-open");
  els.exportIssueDialog.setAttribute("aria-hidden", "true");
  if (exportIssueDialogResolve) {
    exportIssueDialogResolve(confirmed);
    exportIssueDialogResolve = null;
  }
}

function normalizeImportedPaths(fileMap) {
  const normalizedInput = {};
  for (const [path, value] of Object.entries(fileMap)) {
    normalizedInput[path.replaceAll("\\", "/").replace(/^\/+/, "")] = value;
  }
  const paths = Object.keys(normalizedInput);
  const packPath = paths.find((path) => path === "pack.mcmeta" || path.endsWith("/pack.mcmeta"));
  const normalized = {};
  if (!packPath || packPath === "pack.mcmeta") {
    Object.assign(normalized, normalizedInput);
  } else {
    const prefix = packPath.slice(0, -"pack.mcmeta".length);
    for (const [path, value] of Object.entries(normalizedInput)) {
      normalized[path.startsWith(prefix) ? path.slice(prefix.length) : path] = value;
    }
  }

  return normalizeNamespaceRootImportPaths(normalized);
}

function normalizeNamespaceRootImportPaths(fileMap) {
  const paths = Object.keys(fileMap);
  if (paths.some((path) => path.startsWith("data/"))) return fileMap;
  const namespaceRoots = new Set(paths
    .filter(isNamespaceRootDataPath)
    .map((path) => path.split("/")[0]));
  if (namespaceRoots.size === 0) return fileMap;

  const normalized = {};
  for (const [path, value] of Object.entries(fileMap)) {
    const namespaceRoot = path.split("/")[0];
    normalized[namespaceRoots.has(namespaceRoot) ? `data/${path}` : path] = value;
  }
  return normalized;
}

function isNamespaceRootDataPath(path) {
  return /^[a-z0-9_.-]+\/(?:dialogue|forced_dialogue|notifications|gifts|pacification|villager_names|story_structures|story_biomes)\/.+\.json$/i.test(path);
}

function isTextPath(path) {
  return /\.(json|mcmeta|mcfunction|txt|md|lang)$/i.test(path);
}

function importedKnownKind(path) {
  if (/^data\/villagerretaliation\/dialogue\/[^/]+\/.+\.json$/.test(path)) return "dialogue";
  if (/^data\/villagerretaliation\/forced_dialogue\/.+\.json$/.test(path)) return "forced_dialogue";
  if (/^data\/villagerretaliation\/notifications\/[^/]+\/.+\.json$/.test(path)) return "notifications";
  if (/^data\/villagerretaliation\/gifts\/.+\.json$/.test(path)) return "gifts";
  if (/^data\/villagerretaliation\/pacification\/.+\.json$/.test(path)) return "pacification";
  if (/^data\/[^/]+\/story_structures\/.+\.json$/.test(path)) return "story_structures";
  if (/^data\/[^/]+\/story_biomes\/.+\.json$/.test(path)) return "story_biomes";
  if (path === namesPath()) return "names";
  return "";
}

async function handleImport(files, replaceProject = false) {
  if (!files.length) return;
  if (replaceProject) {
    state = createInitialState();
  }
  const imported = {};
  for (const file of files) {
    const path = (file.webkitRelativePath || file.name).replaceAll("\\", "/");
    if (/\.zip$/i.test(file.name)) {
      const zipFiles = await readZip(new Uint8Array(await file.arrayBuffer()));
      Object.assign(imported, normalizeImportedPaths(zipFiles));
    } else {
      const bytes = new Uint8Array(await file.arrayBuffer());
      imported[path] = isTextPath(path) ? decodeTextFile(bytes) : bytes;
    }
  }
  const normalized = normalizeImportedPaths(imported);
  const importedVersion = inferPackVersionFromFiles(normalized);
  ingestFiles(normalized);
  if (importedVersion) {
    state.meta.packVersion = importedVersion;
  }
  selectedPath = Object.keys(generatedFiles()).sort()[0] || "pack.mcmeta";
  editing = null;
  resetFileTreeExpansion();
  clearEntryFormDirty();
  render();
  showToast(importedVersion ? `Import complete. Target set to ${packVersionInfo(importedVersion).label}.` : "Import complete.");
}

function hasDroppedFiles(dataTransfer) {
  return Array.from(dataTransfer?.types || []).includes("Files");
}

function setImportDragActive(active) {
  document.body.classList.toggle("is-file-dragging", active);
}

async function collectDroppedFiles(dataTransfer) {
  const items = Array.from(dataTransfer?.items || []);
  if (items.length > 0) {
    const files = [];
    for (const item of items) {
      if (item.kind !== "file") continue;
      const entry = item.webkitGetAsEntry?.();
      if (entry) {
        files.push(...await filesFromDroppedEntry(entry));
      } else {
        const file = item.getAsFile?.();
        if (file) files.push(file);
      }
    }
    return files;
  }
  return Array.from(dataTransfer?.files || []);
}

async function filesFromDroppedEntry(entry, parentPath = "") {
  if (entry.isFile) {
    const file = await new Promise((resolve, reject) => entry.file(resolve, reject));
    const relativePath = `${parentPath}${file.name}`;
    return [fileWithRelativePath(file, relativePath)];
  }
  if (!entry.isDirectory) return [];
  const reader = entry.createReader();
  const children = [];
  let batch = [];
  do {
    batch = await new Promise((resolve, reject) => reader.readEntries(resolve, reject));
    children.push(...batch);
  } while (batch.length > 0);
  const directoryPath = `${parentPath}${entry.name}/`;
  const nested = await Promise.all(children.map((child) => filesFromDroppedEntry(child, directoryPath)));
  return nested.flat();
}

function fileWithRelativePath(file, relativePath) {
  if (!relativePath || relativePath === file.name) return file;
  return {
    name: file.name,
    type: file.type,
    size: file.size,
    lastModified: file.lastModified,
    webkitRelativePath: relativePath,
    arrayBuffer: () => file.arrayBuffer()
  };
}

async function importDroppedFiles(dataTransfer) {
  if (!canLeaveEntryForm()) return;
  const files = await collectDroppedFiles(dataTransfer);
  if (files.length === 0) {
    showToast("No importable files found.");
    return;
  }
  await handleImport(files, false);
}

function ingestFiles(files) {
  const extra = {};
  for (const [path, value] of Object.entries(files)) {
    const normalizedPath = path.replace(/^\/+/, "");
    if (normalizedPath.endsWith("/")) continue;
    if (normalizedPath === "pack.mcmeta" && typeof value === "string") {
      try {
        const json = JSON.parse(stripTextBom(value));
        state.meta.description = json.pack?.description || state.meta.description;
        state.meta.packFormat = Number(json.pack?.pack_format) || state.meta.packFormat;
        state.meta.packVersion = readPackVersion(json) || state.meta.packVersion;
      } catch {
        extra[normalizedPath] = value;
      }
      continue;
    }
    if (typeof value === "string" && ingestKnownJson(normalizedPath, value)) {
      continue;
    }
    extra[normalizedPath] = value;
  }
  state.extraFiles = { ...state.extraFiles, ...extra };
}

function ingestKnownJson(path, source) {
  let json;
  try {
    json = JSON.parse(stripTextBom(source));
  } catch {
    return false;
  }

  const dialogueInfo = dialoguePathInfo(path);
  if (dialogueInfo) {
    state.meta.locale = dialogueInfo.locale;
    state.dialogue.fileName = normalizeFileName(dialogueInfo.relative.split("/").pop(), state.dialogue.fileName);
    if (dialogueInfo.kind) {
      state.dialogue.layout = "folders";
      if (dialogueInfo.folderName) state.dialogue.folderName = normalizeFileName(dialogueInfo.folderName, state.dialogue.folderName || state.meta.slug);
    }
    if (dialogueInfo.kind) {
      const entries = withDefaultProfession(dialogueEntriesFromJson(json, dialogueInfo.kind), dialogueInfo.profession);
      if (entries.length > 0) {
        mergeArray("dialogue", dialogueInfo.kind, entries, path);
        return true;
      }
      return false;
    }
    let importedDialogue = false;
    for (const kind of DIALOGUE_KIND_KEYS) {
      const entries = withDefaultProfession(json[kind], dialogueInfo.profession);
      if (!Array.isArray(entries)) continue;
      mergeArray("dialogue", kind, entries, path);
      importedDialogue = true;
    }
    return importedDialogue;
  }

  const notificationMatch = path.match(/^data\/villagerretaliation\/notifications\/([^/]+)\/(.+)\.json$/);
  if (notificationMatch) {
    state.meta.locale = notificationMatch[1];
    state.notifications.fileName = normalizeFileName(notificationMatch[2].split("/").pop(), state.notifications.fileName);
    mergeArray("notifications", "notifications", normalizeNotificationEntries(json), path);
    return true;
  }

  const forcedDialogueMatch = path.match(/^data\/villagerretaliation\/forced_dialogue\/(.+)\.json$/);
  if (forcedDialogueMatch) {
    state.forcedDialogue.fileName = normalizeFileName(forcedDialogueMatch[1].split("/").pop(), state.forcedDialogue.fileName);
    mergeArray("forcedDialogue", "entries", normalizeForcedDialogueEntries(json), path);
    return true;
  }

  const giftMatch = path.match(/^data\/villagerretaliation\/gifts\/(.+)\.json$/);
  if (giftMatch) {
    state.gifts.fileName = normalizeFileName(giftMatch[1].split("/").pop(), state.gifts.fileName);
    mergeArray("gifts", "preferences", json.preferences);
    mergeArray("gifts", "rewards", json.rewards);
    return true;
  }

  const pacificationMatch = path.match(/^data\/villagerretaliation\/pacification\/(.+)\.json$/);
  if (pacificationMatch) {
    state.pacification.fileName = normalizeFileName(pacificationMatch[1].split("/").pop(), state.pacification.fileName);
    mergeArray("pacification", "payments", json.payments);
    return true;
  }

  const structureMatch = path.match(/^data\/([^/]+)\/story_structures\/(.+)\.json$/);
  if (structureMatch) {
    state.stories.namespace = structureMatch[1];
    state.stories.structureFileName = normalizeFileName(structureMatch[2].split("/").pop(), state.stories.structureFileName);
    if (json.radius) state.stories.radius = json.radius;
    mergeArray("stories", "structures", normalizeStoryEntries(json, "structure"));
    return true;
  }

  const biomeMatch = path.match(/^data\/([^/]+)\/story_biomes\/(.+)\.json$/);
  if (biomeMatch) {
    state.stories.namespace = biomeMatch[1];
    state.stories.biomeFileName = normalizeFileName(biomeMatch[2].split("/").pop(), state.stories.biomeFileName);
    mergeArray("stories", "biomes", normalizeStoryEntries(json, "biome"));
    return true;
  }

  if (path === namesPath()) {
    state.names.male_names = unique([...state.names.male_names, ...parseList(json.male_names), ...parseList(json.names)]);
    state.names.female_names = unique([...state.names.female_names, ...parseList(json.female_names)]);
    return true;
  }

  const forcedEntries = normalizeForcedDialogueEntries(json);
  if (forcedEntries.length > 0) {
    mergeArray("forcedDialogue", "entries", forcedEntries);
    return true;
  }

  const notificationEntries = normalizeNotificationEntries(json);
  if (notificationEntries.length > 0) {
    mergeArray("notifications", "notifications", notificationEntries);
    return true;
  }

  if (Array.isArray(json.options) || Array.isArray(json.lines) || Array.isArray(json.messages) || Array.isArray(json.openings) || Array.isArray(json.closings) || Array.isArray(json.pacify)) {
    mergeArray("dialogue", "options", json.options);
    mergeArray("dialogue", "lines", json.lines);
    mergeArray("dialogue", "messages", json.messages);
    mergeArray("dialogue", "openings", json.openings);
    mergeArray("dialogue", "closings", json.closings);
    mergeArray("dialogue", "pacify", json.pacify);
    return true;
  }

  if (Array.isArray(json.male_names) || Array.isArray(json.female_names) || Array.isArray(json.names)) {
    state.names.male_names = unique([...state.names.male_names, ...parseList(json.male_names), ...parseList(json.names)]);
    state.names.female_names = unique([...state.names.female_names, ...parseList(json.female_names)]);
    return true;
  }

  let matchedTopLevelPackJson = false;
  if (Array.isArray(json.notifications)) {
    mergeArray("notifications", "notifications", normalizeNotificationEntries(json));
    matchedTopLevelPackJson = true;
  }
  if (Array.isArray(json.preferences)) {
    mergeArray("gifts", "preferences", json.preferences);
    matchedTopLevelPackJson = true;
  }
  if (Array.isArray(json.rewards)) {
    mergeArray("gifts", "rewards", json.rewards);
    matchedTopLevelPackJson = true;
  }
  if (Array.isArray(json.payments)) {
    mergeArray("pacification", "payments", json.payments);
    matchedTopLevelPackJson = true;
  }
  if (matchedTopLevelPackJson) return true;

  const detected = detectJsonKind(json);
  if (detected) {
    mergeArray(detected.section, detected.kind, json[detected.key]);
    return true;
  }

  return false;
}

function detectJsonKind(json) {
  if (Array.isArray(json.entries) && json.entries.some(isForcedDialogueEntry)) return { section: "forcedDialogue", kind: "entries", key: "entries" };
  if (Array.isArray(json.notifications)) return { section: "notifications", kind: "notifications", key: "notifications" };
  if (Array.isArray(json.preferences)) return { section: "gifts", kind: "preferences", key: "preferences" };
  if (Array.isArray(json.rewards)) return { section: "gifts", kind: "rewards", key: "rewards" };
  if (Array.isArray(json.payments)) return { section: "pacification", kind: "payments", key: "payments" };
  return null;
}

function isForcedDialogueEntry(entry) {
  if (isNotificationEntry(entry)) return false;
  return Boolean(entry && typeof entry === "object" && forcedTriggerValue(entry) && (hasForcedDialogueLine(entry) || Array.isArray(entry.options)));
}

function hasForcedDialogueLine(entry) {
  return Boolean(entry?.line || (Array.isArray(entry?.lines) && entry.lines.some((line) => String(line ?? "").trim())));
}

function forcedOutputMode(entry) {
  return entry?.output?.mode || "forced_dialogue";
}

function isForcedDialogueOutput(entry) {
  return forcedOutputMode(entry) === "forced_dialogue";
}

function isChatOutputEntry(entry) {
  return forcedOutputMode(entry) === "chat";
}

function hasIgnoredForcedDialogueFields(entry) {
  if (!isChatOutputEntry(entry)) return false;
  return (
    entry.reputation !== undefined
    || entry.initiate_dialogue !== undefined
    || entry.aggro_immediately !== undefined
    || entry.force_camera_towards_villager !== undefined
    || (Array.isArray(entry.options) && entry.options.length > 0)
    || entry.leave_option !== undefined
    || (Array.isArray(entry.leave_options) && entry.leave_options.length > 0)
  );
}

function forcedLeaveOptions(entry) {
  if (Array.isArray(entry?.leave_options)) return entry.leave_options;
  return entry?.leave_option && typeof entry.leave_option === "object" ? [entry.leave_option] : [];
}

function forcedDialogueLineValue(entry) {
  if (Array.isArray(entry?.lines) && entry.lines.length > 0) {
    return entry.lines.join("\n");
  }
  return entry?.line ?? "";
}

function readForcedDialogueLines() {
  return readValue("forced-line")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function normalizeForcedDialogueEntries(json) {
  if (Array.isArray(json.entries) && json.entries.some(isForcedDialogueEntry)) return json.entries;
  if (isForcedDialogueEntry(json)) return [json];
  return [];
}

function isNotificationEntry(entry) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) return false;
  if (!entry.trigger || !hasNotificationText(entry)) return false;
  if (CONSTANTS.forcedDialogueTriggers.includes(forcedTriggerValue(entry))) return false;
  const notificationKeys = [
    "id",
    "kind",
    "world_text_kind",
    "style",
    "weight",
    "professions",
    "player_item",
    "player_items",
    "player_item_tag",
    "player_item_tags",
    "player_item_slot",
    "player_item_slots",
    "min_player_item_durability",
    "max_player_item_durability",
    "min_player_item_durability_percent",
    "max_player_item_durability_percent",
    "min_held_item_durability",
    "max_held_item_durability",
    "min_held_item_durability_percent",
    "max_held_item_durability_percent",
    "player_item_enchantment",
    "player_item_enchantments",
    "held_item_enchantment",
    "held_item_enchantments",
    "min_player_item_enchantment_level",
    "max_player_item_enchantment_level",
    "min_held_item_enchantment_level",
    "max_held_item_enchantment_level",
    "target_entity_type",
    "target_entity",
    "target_entity_types",
    "target_entities",
    "reputation_level",
    "reputation_levels",
    "min_reputation",
    "max_reputation",
    "color",
    "text_color",
    "chat_color"
  ];
  return notificationKeys.some((key) => Object.hasOwn(entry, key));
}

function normalizeNotificationEntries(json) {
  if (Array.isArray(json?.notifications)) return json.notifications;
  if (Array.isArray(json?.entries) && json.entries.some(isNotificationEntry)) return json.entries;
  if (Array.isArray(json?.lines) && json.lines.some(isNotificationEntry)) return json.lines;
  if (Array.isArray(json) && json.some(isNotificationEntry)) return json;
  if (isNotificationEntry(json)) return [json];
  return [];
}

function normalizeStoryEntries(json, type) {
  if (Array.isArray(json.entries)) return json.entries;
  if (type === "structure" && (json.structure || json.structures)) return [json];
  if (type === "biome" && (json.biome || json.biomes)) return [json];
  return [];
}

function withDefaultProfession(entries, profession) {
  if (!Array.isArray(entries) || !profession) return entries;
  return entries.map((entry) => entry.professions ? entry : { ...entry, professions: [profession] });
}

function mergeArray(section, kind, entries, sourcePath = "") {
  if (!Array.isArray(entries)) return;
  state[section][kind].push(...entries.map((entry) => {
    const cleaned = cleanObject(entry);
    if (sourcePath && cleaned && typeof cleaned === "object") {
      cleaned.__sourcePath = sourcePath;
    }
    return cleaned;
  }));
}

function readUint16(bytes, offset) {
  return bytes[offset] | (bytes[offset + 1] << 8);
}

function readUint32(bytes, offset) {
  return (bytes[offset] | (bytes[offset + 1] << 8) | (bytes[offset + 2] << 16) | (bytes[offset + 3] << 24)) >>> 0;
}

async function readZip(bytes) {
  let eocd = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 66000); i--) {
    if (readUint32(bytes, i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error("Could not find zip directory.");
  let offset = readUint32(bytes, eocd + 16);
  const centralDirectorySize = readUint32(bytes, eocd + 12);
  const centralDirectoryEnd = centralDirectorySize > 0 ? offset + centralDirectorySize : bytes.length;
  const result = {};
  while (offset + 46 <= centralDirectoryEnd) {
    if (readUint32(bytes, offset) !== 0x02014b50) break;
    const flags = readUint16(bytes, offset + 8);
    const method = readUint16(bytes, offset + 10);
    const compressedSize = readUint32(bytes, offset + 20);
    const nameLength = readUint16(bytes, offset + 28);
    const extraLength = readUint16(bytes, offset + 30);
    const commentLength = readUint16(bytes, offset + 32);
    const localOffset = readUint32(bytes, offset + 42);
    const nameBytes = bytes.slice(offset + 46, offset + 46 + nameLength);
    const name = new TextDecoder("utf-8").decode(nameBytes).replaceAll("\\", "/");
    offset += 46 + nameLength + extraLength + commentLength;
    if (name.endsWith("/")) continue;

    if (readUint32(bytes, localOffset) !== 0x04034b50) {
      throw new Error(`Invalid zip local file header for ${name}.`);
    }
    const localNameLength = readUint16(bytes, localOffset + 26);
    const localExtraLength = readUint16(bytes, localOffset + 28);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.slice(dataStart, dataStart + compressedSize);
    const data = await decompressZipEntry(compressed, method);
    result[name] = isTextPath(name) ? decodeTextFile(data) : data;
  }
  return result;
}

async function decompressZipEntry(data, method) {
  if (method === 0) return data;
  if (method !== 8) throw new Error(`Unsupported zip compression method ${method}.`);
  if (!("DecompressionStream" in window)) {
    throw new Error("This browser cannot decompress deflated zip entries.");
  }
  try {
    const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  } catch {
    const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate"));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }
}

function createZip(files) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;
  const now = new Date();
  const { dosTime, dosDate } = toDosDateTime(now);

  for (const [path, value] of Object.entries(files).sort(([a], [b]) => a.localeCompare(b))) {
    const nameBytes = encoder.encode(path);
    const data = value instanceof Uint8Array ? value : encoder.encode(String(value));
    const crc = crc32(data);
    const localHeader = concatBytes(
      u32(0x04034b50),
      u16(20),
      u16(0x0800),
      u16(0),
      u16(dosTime),
      u16(dosDate),
      u32(crc),
      u32(data.length),
      u32(data.length),
      u16(nameBytes.length),
      u16(0),
      nameBytes
    );
    localParts.push(localHeader, data);
    const centralHeader = concatBytes(
      u32(0x02014b50),
      u16(20),
      u16(20),
      u16(0x0800),
      u16(0),
      u16(dosTime),
      u16(dosDate),
      u32(crc),
      u32(data.length),
      u32(data.length),
      u16(nameBytes.length),
      u16(0),
      u16(0),
      u16(0),
      u16(0),
      u32(0),
      u32(offset),
      nameBytes
    );
    centralParts.push(centralHeader);
    offset += localHeader.length + data.length;
  }

  const centralOffset = offset;
  const centralDirectory = concatBytes(...centralParts);
  const end = concatBytes(
    u32(0x06054b50),
    u16(0),
    u16(0),
    u16(centralParts.length),
    u16(centralParts.length),
    u32(centralDirectory.length),
    u32(centralOffset),
    u16(0)
  );

  return concatBytes(...localParts, centralDirectory, end);
}

function toDosDateTime(date) {
  const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const dosDate = ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { dosTime, dosDate };
}

function u16(value) {
  return new Uint8Array([value & 0xff, (value >>> 8) & 0xff]);
}

function u32(value) {
  return new Uint8Array([value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff]);
}

function concatBytes(...parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const result = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}

const crcTable = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(data) {
  let c = 0xffffffff;
  for (let i = 0; i < data.length; i++) {
    c = crcTable[(c ^ data[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

els.tabs.addEventListener("click", (event) => {
  const button = event.target.closest(".tab");
  if (!button) return;
  if (button.dataset.section !== activeSection && !canLeaveEntryForm()) return;
  activeSection = button.dataset.section;
  editing = null;
  clearEntryFormDirty();
  render();
});

els.panel.addEventListener("click", (event) => {
  if (Date.now() < suppressEntryClickUntil) return;

  const entryTab = event.target.closest(".entry-tab");
  if (entryTab) {
    if (!canLeaveEntryForm()) return;
    const scope = entryTab.closest(".entry-tabs").dataset.scope;
    if (scope === "dialogue") activeDialogueKind = entryTab.dataset.kind;
    if (scope === "gifts") activeGiftKind = entryTab.dataset.kind;
    if (scope === "stories") activeStoryKind = entryTab.dataset.kind;
    editing = null;
    clearEntryFormDirty();
    render();
    return;
  }

  const toggleButton = event.target.closest("[data-toggle-target]");
  if (toggleButton) {
    const input = document.querySelector(`#${CSS.escape(toggleButton.dataset.toggleTarget)}`);
    if (!input) return;
    input.checked = toggleButton.dataset.toggleValue === "true";
    const toggleRoot = toggleButton.closest(".toggle");
    toggleRoot?.querySelector(".toggle-false")?.setAttribute("aria-pressed", String(!input.checked));
    toggleRoot?.querySelector(".toggle-true")?.setAttribute("aria-pressed", String(input.checked));
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
    return;
  }

  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) {
    const entryCard = event.target.closest(".entry-card");
    if (entryCard) {
      const isSameEntry = editing
        && editing.section === entryCard.dataset.section
        && editing.kind === entryCard.dataset.kind
        && editing.index === Number(entryCard.dataset.index);
      if (!isSameEntry && !canLeaveEntryForm()) return;
      selectEntryForEditing(entryCard.dataset.section, entryCard.dataset.kind, Number(entryCard.dataset.index));
      renderPreservingEntryListScroll();
    }
    return;
  }
  const action = actionButton.dataset.action;
  if (action === "entry-page") {
    const key = entryListPageKey(actionButton.dataset.section, actionButton.dataset.kind);
    entryListPages[key] = Math.max(0, Number(actionButton.dataset.page) || 0);
    renderPreservingEntryListScroll();
    return;
  }
  if (action === "insert-tag") {
    insertTag(actionButton.dataset.target, actionButton.dataset.value);
    return;
  }
  if (action === "save-entry-form") {
    saveActiveEntryForm();
    return;
  }
  if (action === "edit-entry") {
    const isSameEntry = editing
      && editing.section === actionButton.dataset.section
      && editing.kind === actionButton.dataset.kind
      && editing.index === Number(actionButton.dataset.index);
    if (!isSameEntry && !canLeaveEntryForm()) return;
    selectEntryForEditing(actionButton.dataset.section, actionButton.dataset.kind, Number(actionButton.dataset.index));
    renderPreservingEntryListScroll();
  }
  if (action === "delete-entry") {
    deleteEntry(actionButton.dataset.section, actionButton.dataset.kind, Number(actionButton.dataset.index));
    return;
  }
  if (action === "clear-dialogue-form" || action === "clear-forced-dialogue-form" || action === "clear-notification-form" || action === "clear-gift-form" || action === "clear-pacification-form" || action === "clear-story-form") {
    clearEditing();
  }
  if (action === "add-dialogue-example" && canLeaveEntryForm()) addDialogueExample();
  if (action === "add-forced-dialogue-example" && canLeaveEntryForm()) addForcedDialogueExample();
  if (action === "add-notification-example" && canLeaveEntryForm()) addNotificationExample();
  if (action === "add-gift-example" && canLeaveEntryForm()) addGiftExample();
  if (action === "add-pacification-example" && canLeaveEntryForm()) addPacificationExample();
  if (action === "add-story-example" && canLeaveEntryForm()) addStoryExample();
  if (action === "add-name-example" && canLeaveEntryForm()) addNameExample();
});

els.entryDirectory.addEventListener("click", (event) => {
  if (Date.now() < suppressEntryClickUntil) return;
  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) {
    const entryCard = event.target.closest(".entry-card");
    if (!entryCard) return;
    const isSameEntry = editing
      && editing.section === entryCard.dataset.section
      && editing.kind === entryCard.dataset.kind
      && editing.index === Number(entryCard.dataset.index);
    if (!isSameEntry && !canLeaveEntryForm()) return;
    selectEntryForEditing(entryCard.dataset.section, entryCard.dataset.kind, Number(entryCard.dataset.index));
    renderPreservingEntryListScroll();
    return;
  }

  const action = actionButton.dataset.action;
  if (action === "entry-page") {
    const key = entryListPageKey(actionButton.dataset.section, actionButton.dataset.kind);
    entryListPages[key] = Math.max(0, Number(actionButton.dataset.page) || 0);
    renderPreservingEntryListScroll();
    return;
  }
  if (action === "save-entry-form") {
    saveActiveEntryForm();
    return;
  }
  if (action === "edit-entry") {
    const isSameEntry = editing
      && editing.section === actionButton.dataset.section
      && editing.kind === actionButton.dataset.kind
      && editing.index === Number(actionButton.dataset.index);
    if (!isSameEntry && !canLeaveEntryForm()) return;
    selectEntryForEditing(actionButton.dataset.section, actionButton.dataset.kind, Number(actionButton.dataset.index));
    renderPreservingEntryListScroll();
    return;
  }
  if (action === "delete-entry") {
    deleteEntry(actionButton.dataset.section, actionButton.dataset.kind, Number(actionButton.dataset.index));
  }
});

els.panel.addEventListener("dragstart", (event) => {
  if (event.target.closest("button")) {
    event.preventDefault();
    return;
  }
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryCard) return;
  entryDragState = {
    section: entryCard.dataset.section,
    kind: entryCard.dataset.kind,
    index: Number(entryCard.dataset.index)
  };
  entryCard.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", JSON.stringify(entryDragState));
});

els.entryDirectory.addEventListener("dragstart", (event) => {
  if (event.target.closest("button")) {
    event.preventDefault();
    return;
  }
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryCard) return;
  entryDragState = {
    section: entryCard.dataset.section,
    kind: entryCard.dataset.kind,
    index: Number(entryCard.dataset.index)
  };
  entryCard.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", JSON.stringify(entryDragState));
});

els.entryDirectory.addEventListener("dragover", (event) => {
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryDragState || !entryCard) return;
  if (entryCard.dataset.section !== entryDragState.section || entryCard.dataset.kind !== entryDragState.kind) return;
  event.preventDefault();
  const { placement } = entryDropIndex(event, entryCard);
  clearEntryDropIndicators();
  if (Number(entryCard.dataset.index) !== entryDragState.index) {
    entryCard.classList.add(placement === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});

els.entryDirectory.addEventListener("drop", (event) => {
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryDragState || !entryCard) return;
  if (entryCard.dataset.section !== entryDragState.section || entryCard.dataset.kind !== entryDragState.kind) return;
  event.preventDefault();
  const { toIndex } = entryDropIndex(event, entryCard);
  const { section, kind, index } = entryDragState;
  entryDragState = null;
  suppressEntryClickUntil = Date.now() + 120;
  clearEntryDropIndicators();
  reorderEntry(section, kind, index, toIndex);
});

els.entryDirectory.addEventListener("dragend", () => {
  suppressEntryClickUntil = Date.now() + 120;
  entryDragState = null;
  els.entryDirectory.querySelectorAll(".entry-card.is-dragging").forEach((card) => card.classList.remove("is-dragging"));
  clearEntryDropIndicators();
});

els.entryDirectory.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" && event.key !== " ") return;
  const entryCard = event.target.closest(".entry-card");
  if (!entryCard || event.target.closest("button")) return;
  const isSameEntry = editing
    && editing.section === entryCard.dataset.section
    && editing.kind === entryCard.dataset.kind
    && editing.index === Number(entryCard.dataset.index);
  if (!isSameEntry && !canLeaveEntryForm()) return;
  event.preventDefault();
  selectEntryForEditing(entryCard.dataset.section, entryCard.dataset.kind, Number(entryCard.dataset.index));
  renderPreservingEntryListScroll();
});

els.panel.addEventListener("dragover", (event) => {
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryDragState || !entryCard) return;
  if (entryCard.dataset.section !== entryDragState.section || entryCard.dataset.kind !== entryDragState.kind) return;
  event.preventDefault();
  const { placement } = entryDropIndex(event, entryCard);
  clearEntryDropIndicators();
  if (Number(entryCard.dataset.index) !== entryDragState.index) {
    entryCard.classList.add(placement === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});

els.panel.addEventListener("drop", (event) => {
  const entryCard = event.target.closest(".entry-card.is-sortable");
  if (!entryDragState || !entryCard) return;
  if (entryCard.dataset.section !== entryDragState.section || entryCard.dataset.kind !== entryDragState.kind) return;
  event.preventDefault();
  const { toIndex } = entryDropIndex(event, entryCard);
  const { section, kind, index } = entryDragState;
  entryDragState = null;
  suppressEntryClickUntil = Date.now() + 120;
  clearEntryDropIndicators();
  reorderEntry(section, kind, index, toIndex);
});

els.panel.addEventListener("dragend", () => {
  suppressEntryClickUntil = Date.now() + 120;
  entryDragState = null;
  els.panel.querySelectorAll(".entry-card.is-dragging").forEach((card) => card.classList.remove("is-dragging"));
  clearEntryDropIndicators();
});

els.panel.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" && event.key !== " ") return;
  const entryCard = event.target.closest(".entry-card");
  if (!entryCard || event.target.closest("button")) return;
  const isSameEntry = editing
    && editing.section === entryCard.dataset.section
    && editing.kind === entryCard.dataset.kind
    && editing.index === Number(entryCard.dataset.index);
  if (!isSameEntry && !canLeaveEntryForm()) return;
  event.preventDefault();
  selectEntryForEditing(entryCard.dataset.section, entryCard.dataset.kind, Number(entryCard.dataset.index));
  renderPreservingEntryListScroll();
});

els.panel.addEventListener("submit", (event) => {
  const form = event.target.closest("form");
  if (!form) return;
  if (form.dataset.form === "dialogue") saveDialogueEntry(event);
  if (form.dataset.form === "forcedDialogue") saveForcedDialogue(event);
  if (form.dataset.form === "notifications") saveNotification(event);
  if (form.dataset.form === "gifts") saveGiftEntry(event);
  if (form.dataset.form === "pacification") savePacification(event);
  if (form.dataset.form === "stories") saveStoryEntry(event);
});

els.panel.addEventListener("input", (event) => {
  invalidateCurrentViewSnapshot();
  if (event.target.closest(".entry-form")) {
    markEntryFormDirty();
  }
  if (event.target.matches(".entry-form textarea")) {
    resizeTextareas(event.target.closest(".entry-form"));
  }
  if (event.target.matches("textarea")) {
    syncValueTags(event.target.closest(".field") || els.panel);
  }
  if (activeSection === "overview") updateOverviewFromInput(event.target);
  updateSectionSettings(event.target);
  scheduleOutputRender();
});

els.panel.addEventListener("change", (event) => {
  invalidateCurrentViewSnapshot();
  if (event.target.closest(".entry-form")) {
    markEntryFormDirty();
  }
  if (event.target.id === "forced-output_mode") {
    updateForcedOutputModeFields(els.panel);
    resizeTextareas(event.target.closest(".entry-form"));
  }
  if (activeSection === "overview") updateOverviewFromInput(event.target);
  updateSectionSettings(event.target);
  if (event.target.id === "dialogue-layout" && !entryFormDirty) {
    selectedPath = inferSelectedPath(activeSection);
    render();
    return;
  }
  if (event.target.matches("textarea")) {
    syncValueTags(event.target.closest(".field") || els.panel);
  }
  renderOutputPanels();
});

function selectExplorerEntry(row) {
  const section = row.dataset.entrySection;
  const kind = row.dataset.entryKind;
  const index = Number(row.dataset.entryIndex);
  const isSameEntry = editing
    && editing.section === section
    && editing.kind === kind
    && editing.index === index;
  if (!isSameEntry && !canLeaveEntryForm()) return false;
  activeSection = section;
  setActiveKindForLocation({ section, kind });
  selectEntryForEditing(section, kind, index);
  if (row.dataset.path) selectedPath = row.dataset.path;
  render();
  return true;
}

els.fileTree.addEventListener("click", (event) => {
  const folderRow = event.target.closest("[data-tree-folder]");
  if (folderRow) {
    const folderPath = folderRow.dataset.treeFolder;
    if (collapsedTreeFolders.has(folderPath)) {
      collapsedTreeFolders.delete(folderPath);
    } else {
      collapsedTreeFolders.add(folderPath);
    }
    fileTreeSignature = "";
    renderFiles();
    renderIcons();
    return;
  }

  const entryRow = event.target.closest("[data-entry-section]");
  if (entryRow) {
    selectExplorerEntry(entryRow);
    return;
  }

  const fileRow = event.target.closest("[data-path]");
  if (!fileRow) return;
  if (fileRow.dataset.path !== selectedPath && !canLeaveEntryForm()) return;
  selectedPath = fileRow.dataset.path;
  if (previewEditError?.path !== selectedPath) {
    previewEditError = null;
    invalidateCurrentViewSnapshot();
  }
  renderFiles();
  renderChecks();
  renderPreview();
  renderIcons();
});

els.fileTree.addEventListener("dragstart", (event) => {
  const entryRow = event.target.closest(".tree-entry-row.is-sortable");
  if (!entryRow) return;
  entryDragState = {
    section: entryRow.dataset.entrySection,
    kind: entryRow.dataset.entryKind,
    index: Number(entryRow.dataset.entryIndex)
  };
  entryRow.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", JSON.stringify(entryDragState));
});

els.fileTree.addEventListener("dragover", (event) => {
  const entryRow = event.target.closest(".tree-entry-row.is-sortable");
  if (!entryDragState || !entryRow) return;
  if (entryRow.dataset.entrySection !== entryDragState.section || entryRow.dataset.entryKind !== entryDragState.kind) return;
  event.preventDefault();
  const { placement } = entryDropIndex(event, entryRow);
  clearEntryDropIndicators();
  if (Number(entryRow.dataset.entryIndex) !== entryDragState.index) {
    entryRow.classList.add(placement === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});

els.fileTree.addEventListener("drop", (event) => {
  const entryRow = event.target.closest(".tree-entry-row.is-sortable");
  if (!entryDragState || !entryRow) return;
  if (entryRow.dataset.entrySection !== entryDragState.section || entryRow.dataset.entryKind !== entryDragState.kind) return;
  event.preventDefault();
  const { toIndex } = entryDropIndex(event, entryRow);
  const { section, kind, index } = entryDragState;
  entryDragState = null;
  suppressEntryClickUntil = Date.now() + 120;
  clearEntryDropIndicators();
  reorderEntry(section, kind, index, toIndex);
});

els.fileTree.addEventListener("dragend", () => {
  suppressEntryClickUntil = Date.now() + 120;
  entryDragState = null;
  els.fileTree.querySelectorAll(".tree-row.is-dragging").forEach((row) => row.classList.remove("is-dragging"));
  clearEntryDropIndicators();
});

els.checks.addEventListener("click", (event) => {
  const checkButton = event.target.closest("[data-check-index]");
  if (!checkButton) return;
  event.preventDefault();
  checkButton.blur();
  const check = currentViewChecks()[Number(checkButton.dataset.checkIndex)];
  jumpToCheck(check);
});

els.importInput.addEventListener("change", async () => {
  if (!canLeaveEntryForm()) {
    els.importInput.value = "";
    return;
  }
  try {
    await handleImport([...els.importInput.files], [...els.importInput.files].some((file) => /\.zip$/i.test(file.name)));
  } catch (error) {
    showToast(error.message || "Import failed.");
  } finally {
    els.importInput.value = "";
  }
});

els.directoryInput.addEventListener("change", async () => {
  if (!canLeaveEntryForm()) {
    els.directoryInput.value = "";
    return;
  }
  try {
    await handleImport([...els.directoryInput.files], true);
  } catch (error) {
    showToast(error.message || "Folder import failed.");
  } finally {
    els.directoryInput.value = "";
  }
});

document.addEventListener("dragenter", (event) => {
  if (!hasDroppedFiles(event.dataTransfer)) return;
  event.preventDefault();
  importDragDepth += 1;
  setImportDragActive(true);
});
document.addEventListener("dragover", (event) => {
  if (!hasDroppedFiles(event.dataTransfer)) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = "copy";
  setImportDragActive(true);
});
document.addEventListener("dragleave", (event) => {
  if (!hasDroppedFiles(event.dataTransfer)) return;
  event.preventDefault();
  importDragDepth = Math.max(0, importDragDepth - 1);
  if (importDragDepth === 0) setImportDragActive(false);
});
document.addEventListener("drop", async (event) => {
  if (!hasDroppedFiles(event.dataTransfer)) return;
  event.preventDefault();
  importDragDepth = 0;
  setImportDragActive(false);
  try {
    await importDroppedFiles(event.dataTransfer);
  } catch (error) {
    showToast(error.message || "Drop import failed.");
  }
});

els.exportButton.addEventListener("click", () => {
  if (canLeaveEntryForm()) exportZip();
});
els.starterButton.addEventListener("click", () => {
  if (canLeaveEntryForm()) openTemplateDialog();
});
els.leftPanelToggleButton.addEventListener("click", (event) => {
  event.stopPropagation();
  showLeftPanel = !showLeftPanel;
  renderWorkspaceChrome();
  renderIcons();
});
els.rightPanelToggleButton.addEventListener("click", (event) => {
  event.stopPropagation();
  showRightPanel = !showRightPanel;
  renderWorkspaceChrome();
  renderIcons();
});
els.leftRail.addEventListener("click", () => {
  if (showLeftPanel) return;
  showLeftPanel = true;
  renderWorkspaceChrome();
  renderIcons();
});
els.rightRail.addEventListener("click", () => {
  if (showRightPanel) return;
  showRightPanel = true;
  renderWorkspaceChrome();
  renderIcons();
});
els.leftRail.addEventListener("keydown", (event) => {
  if (showLeftPanel || (event.key !== "Enter" && event.key !== " ")) return;
  event.preventDefault();
  showLeftPanel = true;
  renderWorkspaceChrome();
  renderIcons();
});
els.rightRail.addEventListener("keydown", (event) => {
  const title = event.target.closest("[data-panel-snap-target]");
  if (title && (event.key === "Enter" || event.key === " ")) {
    event.preventDefault();
    toggleRightPanelSnap(title.dataset.panelSnapTarget);
    return;
  }
  if (showRightPanel || (event.key !== "Enter" && event.key !== " ")) return;
  event.preventDefault();
  showRightPanel = true;
  renderWorkspaceChrome();
  renderIcons();
});
els.wrapPreviewButton.addEventListener("click", () => {
  wrapPreviewLines = !wrapPreviewLines;
  renderPreview();
  renderIcons();
});
els.undoPreviewButton.addEventListener("click", () => {
  undoPreviewEdit();
  focusPreviewEditor();
});
els.redoPreviewButton.addEventListener("click", () => {
  redoPreviewEdit();
  focusPreviewEditor();
});
els.wikiButton.addEventListener("click", openWiki);
els.settingsButton.addEventListener("click", openSettings);
els.settingsCloseButton.addEventListener("click", closeSettings);
els.settingsResetButton.addEventListener("click", resetKeybinds);
els.settingsDialog.addEventListener("click", (event) => {
  if (event.target === els.settingsDialog) closeSettings();
});
els.templateList.addEventListener("click", (event) => {
  const choice = event.target.closest("[data-template-id]");
  if (!choice) return;
  loadTemplateChoice(choice.dataset.templateId);
});
els.templateCancel.addEventListener("click", closeTemplateDialog);
els.templateDialog.addEventListener("click", (event) => {
  if (event.target === els.templateDialog) closeTemplateDialog();
});
els.settingsKeybinds.addEventListener("click", (event) => {
  const button = event.target.closest("[data-keybind-action]");
  if (!button) return;
  recordingKeybindAction = button.dataset.keybindAction;
  renderSettingsKeybinds();
  els.settingsKeybinds.querySelector(`[data-keybind-action="${CSS.escape(recordingKeybindAction)}"]`)?.focus();
});
els.exportIssueCancel.addEventListener("click", () => closeExportIssueDialog(false));
els.exportIssueConfirm.addEventListener("click", () => closeExportIssueDialog(true));
els.exportIssueDialog.addEventListener("click", (event) => {
  if (event.target === els.exportIssueDialog) closeExportIssueDialog(false);
});
els.wikiCloseButton.addEventListener("click", closeWiki);
els.wikiVersion.addEventListener("change", () => {
  wikiState.version = els.wikiVersion.value;
  resetWikiTabs("Home.md");
  ensureWikiLoaded(wikiState.version);
});
els.wikiSearch.addEventListener("input", () => {
  wikiState.query = els.wikiSearch.value;
  wikiState.selectedSectionId = "";
  syncActiveWikiTabToSelection();
  renderWiki();
});
els.wikiHighlightButton.addEventListener("click", toggleCurrentWikiHighlight);
els.wikiHighlightButton.addEventListener("mousedown", (event) => {
  event.preventDefault();
});
els.wikiResults.addEventListener("click", (event) => {
  const deleteHighlight = event.target.closest("[data-delete-wiki-highlight]");
  if (deleteHighlight) {
    deleteWikiHighlight(deleteHighlight.dataset.deleteWikiHighlight);
    return;
  }
  const highlightSourceJump = event.target.closest("[data-highlight-source-jump]");
  if (highlightSourceJump) {
    els.wikiContent.querySelector(`#${CSS.escape(highlightSourceJump.dataset.highlightSourceJump)}`)?.scrollIntoView({ block: "start" });
    return;
  }
  const result = event.target.closest(".wiki-result");
  if (!result) return;
  setWikiLocation(result.dataset.file || "Home.md", result.dataset.section || "");
});
els.wikiResults.addEventListener("mousedown", (event) => {
  const result = event.target.closest(".wiki-result");
  if (!result || result.matches("[data-highlight-source]") || event.button !== 1) return;
  event.preventDefault();
  setWikiLocationInNewTabFromMiddleClick(result.dataset.file || "Home.md", result.dataset.section || "");
});
els.wikiResults.addEventListener("auxclick", (event) => {
  const result = event.target.closest(".wiki-result");
  if (!result || result.matches("[data-highlight-source]") || event.button !== 1) return;
  event.preventDefault();
  setWikiLocationInNewTabFromMiddleClick(result.dataset.file || "Home.md", result.dataset.section || "");
});
els.wikiResults.addEventListener("dragstart", (event) => {
  if (event.target.closest("[data-delete-wiki-highlight]")) {
    event.preventDefault();
    return;
  }
  const result = event.target.closest("[data-highlight-source]");
  if (!result || !isWikiHighlightsFile(wikiState.selectedFile)) return;
  wikiHighlightDragState = { id: result.dataset.highlightSource };
  result.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", wikiHighlightDragState.id);
});
els.wikiResults.addEventListener("dragover", (event) => {
  const result = event.target.closest("[data-highlight-source]");
  if (!wikiHighlightDragState || !result) return;
  event.preventDefault();
  clearWikiHighlightDropIndicators();
  if (result.dataset.highlightSource !== wikiHighlightDragState.id) {
    result.classList.add(wikiHighlightDropPlacement(event, result) === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});
els.wikiResults.addEventListener("drop", (event) => {
  const result = event.target.closest("[data-highlight-source]");
  if (!wikiHighlightDragState || !result) return;
  event.preventDefault();
  const placement = wikiHighlightDropPlacement(event, result);
  const fromId = wikiHighlightDragState.id;
  const toId = result.dataset.highlightSource;
  wikiHighlightDragState = null;
  clearWikiHighlightDropIndicators();
  reorderWikiHighlight(fromId, toId, placement);
});
els.wikiResults.addEventListener("dragend", () => {
  wikiHighlightDragState = null;
  els.wikiResults.querySelectorAll(".wiki-highlight-result.is-dragging").forEach((result) => result.classList.remove("is-dragging"));
  clearWikiHighlightDropIndicators();
});
els.wikiTabs.addEventListener("click", (event) => {
  if (Date.now() < suppressWikiTabClickUntil) return;
  const bookmarkTab = event.target.closest("[data-wiki-bookmark-tab]");
  if (bookmarkTab) {
    setWikiLocation(bookmarkTab.dataset.wikiBookmarkTab || WIKI_HIGHLIGHTS_FILE);
    return;
  }
  const pinButton = event.target.closest("[data-toggle-wiki-pin]");
  if (pinButton) {
    toggleWikiTabPinned(pinButton.dataset.toggleWikiPin);
    return;
  }
  const closeButton = event.target.closest("[data-close-wiki-tab]");
  if (closeButton) {
    closeWikiTab(closeButton.dataset.closeWikiTab);
    return;
  }
  const tabButton = event.target.closest("[data-wiki-tab]");
  if (!tabButton) return;
  wikiState.activeTabId = tabButton.dataset.wikiTab;
  syncWikiSelectionFromActiveTab();
  renderWiki();
});
els.wikiTabs.addEventListener("dragstart", (event) => {
  if (event.target.closest("[data-close-wiki-tab]") || event.target.closest("[data-toggle-wiki-pin]") || event.target.closest("[data-wiki-bookmark-tab]")) {
    event.preventDefault();
    return;
  }
  const tab = event.target.closest(".wiki-tab");
  if (!tab) return;
  wikiTabDragState = { id: tab.dataset.wikiTabId };
  tab.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", wikiTabDragState.id);
});
els.wikiTabs.addEventListener("dragover", (event) => {
  const tab = event.target.closest(".wiki-tab");
  if (!wikiTabDragState || !tab) return;
  event.preventDefault();
  clearWikiTabDropIndicators();
  if (tab.dataset.wikiTabId !== wikiTabDragState.id) {
    tab.classList.add(wikiTabDropPlacement(event, tab) === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});
els.wikiTabs.addEventListener("drop", (event) => {
  const tab = event.target.closest(".wiki-tab");
  if (!wikiTabDragState || !tab) return;
  event.preventDefault();
  const placement = wikiTabDropPlacement(event, tab);
  const fromId = wikiTabDragState.id;
  const toId = tab.dataset.wikiTabId;
  wikiTabDragState = null;
  suppressWikiTabClickUntil = Date.now() + 120;
  clearWikiTabDropIndicators();
  reorderWikiTab(fromId, toId, placement);
});
els.wikiTabs.addEventListener("dragend", () => {
  wikiTabDragState = null;
  suppressWikiTabClickUntil = Date.now() + 120;
  els.wikiTabs.querySelectorAll(".wiki-tab.is-dragging").forEach((tab) => tab.classList.remove("is-dragging"));
  clearWikiTabDropIndicators();
});
els.wikiTabs.addEventListener("mousedown", (event) => {
  const tabButton = event.target.closest("[data-wiki-tab]");
  if (!tabButton || event.button !== 1) return;
  event.preventDefault();
  closeWikiTab(tabButton.dataset.wikiTab);
});
els.wikiTabs.addEventListener("auxclick", (event) => {
  const tabButton = event.target.closest("[data-wiki-tab]");
  if (!tabButton || event.button !== 1) return;
  event.preventDefault();
});
els.wikiContent.addEventListener("click", (event) => {
  const link = event.target.closest("[data-wiki-link]");
  if (!link) return;
  event.preventDefault();
  const file = link.dataset.wikiLink;
  if (!wikiState.docs.some((doc) => doc.file === file)) return;
  setWikiLocation(file, sectionIdForWikiAnchor(file, link.dataset.wikiAnchor || ""));
});
els.wikiContent.addEventListener("mousedown", (event) => {
  const link = event.target.closest("[data-wiki-link]");
  if (!link || event.button !== 1) return;
  event.preventDefault();
  const file = link.dataset.wikiLink;
  if (!wikiState.docs.some((doc) => doc.file === file)) return;
  setWikiLocationInNewTabFromMiddleClick(file, sectionIdForWikiAnchor(file, link.dataset.wikiAnchor || ""));
});
els.wikiContent.addEventListener("auxclick", (event) => {
  const link = event.target.closest("[data-wiki-link]");
  if (!link || event.button !== 1) return;
  event.preventDefault();
  const file = link.dataset.wikiLink;
  if (!wikiState.docs.some((doc) => doc.file === file)) return;
  setWikiLocationInNewTabFromMiddleClick(file, sectionIdForWikiAnchor(file, link.dataset.wikiAnchor || ""));
});
els.wikiContent.addEventListener("dragstart", (event) => {
  const card = event.target.closest("[data-highlight-id]");
  if (!card || !isWikiHighlightsFile(wikiState.selectedFile)) return;
  wikiHighlightDragState = { id: card.dataset.highlightId };
  card.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", wikiHighlightDragState.id);
});
els.wikiContent.addEventListener("dragover", (event) => {
  const card = event.target.closest("[data-highlight-id]");
  if (!wikiHighlightDragState || !card) return;
  event.preventDefault();
  clearWikiHighlightDropIndicators();
  if (card.dataset.highlightId !== wikiHighlightDragState.id) {
    card.classList.add(wikiHighlightDropPlacement(event, card) === "after" ? "is-drop-after" : "is-drop-before");
  }
  event.dataTransfer.dropEffect = "move";
});
els.wikiContent.addEventListener("drop", (event) => {
  const card = event.target.closest("[data-highlight-id]");
  if (!wikiHighlightDragState || !card) return;
  event.preventDefault();
  const placement = wikiHighlightDropPlacement(event, card);
  const fromId = wikiHighlightDragState.id;
  const toId = card.dataset.highlightId;
  wikiHighlightDragState = null;
  clearWikiHighlightDropIndicators();
  reorderWikiHighlight(fromId, toId, placement);
});
els.wikiContent.addEventListener("dragend", () => {
  wikiHighlightDragState = null;
  els.wikiContent.querySelectorAll(".wiki-highlight-card.is-dragging").forEach((card) => card.classList.remove("is-dragging"));
  clearWikiHighlightDropIndicators();
});
els.rightRail.addEventListener("click", (event) => {
  const title = event.target.closest("[data-panel-snap-target]");
  if (!title) return;
  toggleRightPanelSnap(title.dataset.panelSnapTarget);
});
document.addEventListener("pointerdown", panelResizeStart);
document.addEventListener("pointerdown", wikiPointerStart);
document.addEventListener("auxclick", panelResizeAuxClick);
document.addEventListener("keydown", panelResizeKeydown);
els.preview.addEventListener("keydown", handlePreviewEditorKeydown);
els.preview.addEventListener("beforeinput", recordPreviewBeforeInput);
els.preview.addEventListener("scroll", syncPreviewLineNumberScroll);
els.preview.addEventListener("input", () => {
  handlePreviewEditorInput();
});
document.addEventListener("pointerover", (event) => {
  const target = tooltipTarget(event.target);
  if (!target) return;
  showTooltip(target, { x: event.clientX, y: event.clientY });
});
document.addEventListener("mouseover", (event) => {
  const target = tooltipTarget(event.target);
  if (!target || target === activeTooltipTarget) return;
  showTooltip(target, { x: event.clientX, y: event.clientY });
});
document.addEventListener("pointermove", (event) => {
  if (!activeTooltipTarget || !activeTooltipTarget.contains(event.target)) return;
  activeTooltipPointer = { x: event.clientX, y: event.clientY };
  positionTooltip();
});
document.addEventListener("mousemove", (event) => {
  if (!activeTooltipTarget || !activeTooltipTarget.contains(event.target)) return;
  activeTooltipPointer = { x: event.clientX, y: event.clientY };
  positionTooltip();
});
document.addEventListener("pointerout", (event) => {
  const target = tooltipTarget(event.target);
  if (!target || target.contains(event.relatedTarget)) return;
  hideTooltip(target);
});
document.addEventListener("mouseout", (event) => {
  const target = tooltipTarget(event.target);
  if (!target || target.contains(event.relatedTarget)) return;
  hideTooltip(target);
});
document.addEventListener("focusin", (event) => {
  const target = tooltipTarget(event.target);
  if (target) showTooltip(target);
});
document.addEventListener("focusout", (event) => {
  const target = tooltipTarget(event.target);
  if (target) hideTooltip(target);
});
document.addEventListener("keydown", (event) => {
  if (applyRecordedKeybind(event)) return;
  if (keybindMatches(event, getKeybind("openWiki"))) {
    event.preventDefault();
    toggleWiki();
    return;
  }
  if (keybindMatches(event, getKeybind("saveEntry"))) {
    if (saveActiveEntryForm()) {
      event.preventDefault();
    }
    return;
  }
  if (keybindMatches(event, getKeybind("openSettings"))) {
    event.preventDefault();
    openSettings();
    return;
  }
  if (event.key === "Escape") {
    hideTooltip();
    if (els.settingsDialog?.classList.contains("is-open")) closeSettings();
    if (els.templateDialog?.classList.contains("is-open")) closeTemplateDialog();
    if (els.exportIssueDialog?.classList.contains("is-open")) closeExportIssueDialog(false);
    if (wikiState.isOpen) closeWiki();
  }
});
document.addEventListener("selectionchange", () => {
  if (!wikiState.isOpen) return;
  updateWikiHighlightSelection();
});
document.addEventListener("scroll", positionTooltip, true);
window.addEventListener("resize", () => {
  keepPanelSizesInRange();
  if (wikiState.isOpen) applyWikiLayout();
  renderPreviewLineNumbers(previewDocumentValue(), previewLineHighlightRanges);
  positionTooltip();
});
window.addEventListener("beforeunload", (event) => {
  if (!entryFormDirty) return;
  event.preventDefault();
  event.returnValue = "";
});
els.copyButton.addEventListener("click", copyCurrentFile);
els.downloadButton.addEventListener("click", downloadCurrentFile);

setupWikiChrome();
setupToolbarHints();
updateShortcutTooltips();
applyStoredPanelSizes();
initializePreviewEditor();
render();
