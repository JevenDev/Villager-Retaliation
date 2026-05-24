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
  dialogueTypes: [
    "chat",
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
    "combat.player_killed",
    "trade.completed",
    "trade.refused",
    "alert.player_attacked_villager",
    "alert.villager_damaged",
    "alert.witness_attack.player",
    "alert.witness_attack",
    "alert.witness_death.player",
    "alert.witness_death"
  ],
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
    "villager_death"
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
    "villager_attacked",
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
    "player_gave_hated_gift"
  ],
  itemSlots: ["main_hand", "off_hand", "hands", "armor", "hotbar", "inventory", "equipment", "any"],
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
  { key: "options", label: "Options" },
  { key: "lines", label: "Lines" },
  { key: "messages", label: "Messages" },
  { key: "openings", label: "Openings" },
  { key: "closings", label: "Closings" },
  { key: "pacify", label: "Pacify Lines" }
];

const GIFT_KINDS = [
  { key: "preferences", label: "Preferences" },
  { key: "rewards", label: "Rewards" }
];

const STORY_KINDS = [
  { key: "structures", label: "Structures" },
  { key: "biomes", label: "Biomes" }
];

const KIND_TOOLTIPS = {
  "dialogue.options": "Adds choices to the villager talk menu.",
  "dialogue.lines": "Adds villager responses for a dialogue request type.",
  "dialogue.messages": "Adds keyed one-off text used by specific systems and gift responses.",
  "dialogue.openings": "Adds lines used when a conversation starts.",
  "dialogue.closings": "Adds lines used when a conversation ends.",
  "dialogue.pacify": "Adds text shown after a pacification attempt.",
  "gifts.preferences": "Maps items or item tags to loved, liked, neutral, disliked, or hated gift reactions.",
  "gifts.rewards": "Defines items trusted villagers can give back at high reputation.",
  "stories.structures": "Defines structure ids that villagers can remember and turn into shared stories.",
  "stories.biomes": "Defines biome ids that villagers can remember and turn into shared stories."
};

const FIELD_TOOLTIPS = {
  "meta-packName": "Used as the project name and exported zip filename.",
  "meta-packFormat": "Written into pack.mcmeta. The wiki starter uses 34 for this Minecraft target.",
  "meta-namespace": "Namespace used for story discovery files. Most other Villager Retaliation data must stay in the villagerretaliation namespace.",
  "meta-slug": "Lowercase file stem used for generated file names and starter ids.",
  "meta-locale": "Dialogue and notifications load en_us first, then overlay the player's locale.",
  "meta-description": "Text Minecraft shows for the datapack in the pack list.",
  "dialogue-fileName": "JSON file under data/villagerretaliation/dialogue/<locale>/. Use a unique name unless intentionally replacing built-in data.",
  "dialogue-locale": "Locale folder for this dialogue file, such as en_us or fr_fr.",
  "dialogue-id": "Stable id for translations and overrides. Reusing the same id can replace an earlier dialogue entry.",
  "dialogue-label": "Button text shown in the villager talk menu.",
  "dialogue-type": "Dialogue request type. Lines must use the same type as the option that asks for them.",
  "dialogue-order": "Lower numbers appear earlier in the talk menu.",
  "dialogue-professions": "Limits this entry to specific villager professions. Blank means any profession.",
  "dialogue-dispositions": "Limits this entry to villager moods derived from reputation and context.",
  "dialogue-player_items": "Requires the player to have one matching item or item tag. Prefix item tags with #.",
  "dialogue-player_item_slots": "Controls where the player item check looks. If items are set and slots are blank, the mod defaults to hands.",
  "dialogue-text": "Villager text. Supported placeholders depend on the dialogue type and filters.",
  "dialogue-option": "Restricts a line to one or more option ids, including built-in options like adult_share_story.",
  "dialogue-weather": "Limits a line to clear, rain, or thunder.",
  "dialogue-times": "Limits a line to morning, afternoon, evening, or night.",
  "dialogue-event_tags": "Requires a recent nearby village memory with a matching event tag.",
  "dialogue-player_event_tags": "Requires a recent village memory associated with the player.",
  "dialogue-story_structure": "Restricts share_story lines to one or more structure ids.",
  "dialogue-story_biome": "Restricts share_story lines to one or more biome ids.",
  "dialogue-recruitment_followup_scenarios": "Limits recruitment follow-up lines to stored follow-up scenario ids.",
  "dialogue-recruitment_memory_scenarios": "Limits recruitment memory lines to stored recruitment scenario ids.",
  "dialogue-min_recruitment_follow_distance": "Requires the villager to have followed at least this many blocks.",
  "dialogue-gift_advice": "Limits the line to a specific kind of gift advice result.",
  "dialogue-weight": "Higher values are more likely when several matching entries are available.",
  "dialogue-key": "Code or gift preference key this message responds to.",
  "dialogue-outcomes": "Limits pacify text to specific pacification result outcomes.",
  "notifications-fileName": "JSON file under data/villagerretaliation/notifications/<locale>/. Use a unique name unless replacing built-in data.",
  "notifications-locale": "Locale folder for this notification file, such as en_us or fr_fr.",
  "notification-id": "Stable id for translations and overrides.",
  "notification-trigger": "Event trigger emitted by the mod, such as gift.liked or ambient.murmur.",
  "notification-text": "HUD or world text. Supported placeholders depend on the trigger.",
  "notification-kind": "HUD notification category used by the client.",
  "notification-world_text_kind": "Visual style for ambient text above villagers.",
  "notification-color": "Default text/chat color. Accepts named colors or hex values.",
  "notification-text_color": "On-screen text color override.",
  "notification-chat_color": "Chat/log text color override.",
  "notification-professions": "Limits this notification to specific villager professions.",
  "notification-reputation_levels": "Limits this notification to specific reputation tiers.",
  "notification-min_reputation": "Minimum exact reputation value required.",
  "notification-max_reputation": "Maximum exact reputation value allowed.",
  "notification-player_items": "Requires the player to have one matching item or item tag.",
  "notification-player_item_slots": "Controls where the player item check looks.",
  "notification-weight": "Higher values are more likely when several matching notifications are available.",
  "notification-chance": "Random chance gate from 0.0 to 1.0 before weighted selection.",
  "gifts-fileName": "JSON file under data/villagerretaliation/gifts/. Use default only to replace all built-in gifts.",
  "gift-reaction": "How the villager reacts to matching items, and the default reputation change.",
  "gift-priority": "Higher priority wins when several gift preference rules match.",
  "gift-items": "Gift item ids. Unnamespaced ids are treated as minecraft ids; # values are item tags.",
  "gift-tags": "Gift item tag ids, such as minecraft:villager_plantable_seeds.",
  "gift-professions": "Limits the gift rule or reward to specific professions.",
  "gift-reputation_per_item": "Overrides the default reputation gained or lost per gifted item.",
  "gift-response_key": "Dialogue message key used for custom gift response text.",
  "gift-item": "Reward item id returned by trusted villagers.",
  "gift-reputation_levels": "Reputation tiers that can receive this reward.",
  "gift-min_count": "Minimum reward stack count.",
  "gift-max_count": "Maximum reward stack count.",
  "gift-weight": "Higher values are more likely when several rewards match.",
  "pacification-fileName": "JSON file under data/villagerretaliation/pacification/. Use default only to replace the built-in emerald rule.",
  "pacification-items": "Payment item ids. Unnamespaced ids are treated as minecraft ids; # values are item tags.",
  "pacification-tags": "Payment item tag ids, such as c:coins.",
  "pacification-professions": "Limits the payment rule to specific professions. Wandering traders match none.",
  "pacification-count": "Exact number of items consumed when this payment is used.",
  "pacification-min_count": "Minimum random payment cost when exact count is blank.",
  "pacification-max_count": "Maximum random payment cost when exact count is blank.",
  "pacification-name": "Singular item name used by pacify dialogue placeholders.",
  "pacification-plural_name": "Plural item name used by pacify dialogue placeholders.",
  "pacification-priority": "Higher priority wins when several payment rules match.",
  "stories-namespace": "Namespace used for story_structures and story_biomes files.",
  "stories-radius": "Fallback detection radius for structure story entries that do not set their own radius.",
  "stories-structureFileName": "JSON file under data/<namespace>/story_structures/.",
  "stories-biomeFileName": "JSON file under data/<namespace>/story_biomes/.",
  "story-structures": "Structure ids that can unlock share_story dialogue.",
  "story-biomes": "Biome ids that can unlock share_story dialogue.",
  "story-name": "Readable target name used by {target} and {target_article}.",
  "story-radius": "Detection radius in blocks for this structure entry.",
  "names-male_names": "Names used for villagers assigned male identity.",
  "names-female_names": "Names used for villagers assigned female identity."
};

const FLAG_TOOLTIPS = {
  show_for_adults: "When off, adult villagers will not use this entry.",
  show_for_babies: "When off, baby villagers will not use this entry.",
  first_conversation_only: "Only matches during the first conversation with that villager.",
  first_village_interaction_only: "Only matches during the player's first interaction in that village context.",
  requires_unreported_cartographer_map_discovery: "Shows after an unreported cartographer map discovery.",
  requires_unreported_story_hint_discovery: "Shows after an unreported story hint discovery.",
  requires_unreported_combat_survival_report: "Shows after a villager survival report is waiting.",
  requires_unreported_gear_report: "Shows after the player gives combat gear and has not asked about it yet.",
  requires_unreported_recruitment_followup: "Shows after a recruitment follow-up is waiting.",
  requires_unreported_cured_recognition: "Shows after cured villager recognition is waiting.",
  requires_recent_village_event: "Shows when a nearby remembered village event can be reported.",
  requires_unreported_gift_advice_result: "Shows after the player tests gift advice and has not discussed the result yet.",
  requires_unapologized_remembered_harm: "Shows after remembered harm that has not been apologized for.",
  requires_unreported_village_defense: "Shows after the player defends the village and the defense has not been reported.",
  requires_shareable_story: "Shows when the villager has a discovered structure or biome story to share.",
  requires_recent_broken_bed_memory: "Requires a recent memory of the player breaking a villager bed.",
  requires_recent_direct_hit_memory: "Requires a recent memory of the player directly hitting a villager.",
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
  villager_attacked: "A villager was attacked.",
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
  player_gave_hated_gift: "The player gave a hated gift."
};

const DISPOSITION_TOOLTIPS = {
  friendly: "High-trust or positive-context villager mood.",
  respectful: "Respectful positive villager mood.",
  neutral: "Neither especially trusting nor hostile.",
  cautious: "Low-trust or wary villager mood.",
  rude: "Irritated or negative villager mood.",
  hostile: "Angry or hostile villager mood.",
  fearful: "Fear-driven villager mood."
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
  "dialogue-player_item_slots": CONSTANTS.itemSlots,
  "dialogue-weather": CONSTANTS.weather,
  "dialogue-times": CONSTANTS.times,
  "dialogue-event_tags": CONSTANTS.eventTags,
  "dialogue-player_event_tags": CONSTANTS.eventTags,
  "dialogue-outcomes": CONSTANTS.pacifyOutcomes,
  "notification-professions": CONSTANTS.professions,
  "notification-reputation_levels": CONSTANTS.reputationLevels,
  "notification-player_item_slots": CONSTANTS.itemSlots,
  "gift-professions": CONSTANTS.professions,
  "gift-reputation_levels": CONSTANTS.reputationLevels,
  "pacification-professions": CONSTANTS.professions
};

const encoder = new TextEncoder();
const decoder = new TextDecoder();

let state = createInitialState();
let activeSection = "overview";
let activeDialogueKind = "options";
let activeGiftKind = "preferences";
let activeStoryKind = "structures";
let editing = null;
let selectedPath = "pack.mcmeta";
let toastTimer = null;
let showLeftPanel = true;
let showRightPanel = true;

const els = {
  workspace: document.querySelector(".workspace"),
  tabs: document.querySelector("#section-tabs"),
  panel: document.querySelector("#builder-panel"),
  fileTree: document.querySelector("#file-tree"),
  fileCount: document.querySelector("#file-count"),
  checks: document.querySelector("#checks"),
  checkCount: document.querySelector("#check-count"),
  selectedPath: document.querySelector("#selected-path"),
  preview: document.querySelector("#json-preview"),
  importInput: document.querySelector("#import-input"),
  directoryInput: document.querySelector("#directory-input"),
  exportButton: document.querySelector("#export-button"),
  starterButton: document.querySelector("#starter-button"),
  leftPanelToggleButton: document.querySelector("#left-panel-toggle-button"),
  rightPanelToggleButton: document.querySelector("#right-panel-toggle-button"),
  copyButton: document.querySelector("#copy-file-button"),
  downloadButton: document.querySelector("#download-file-button"),
  toast: document.querySelector("#toast")
};

function createInitialState() {
  return {
    meta: {
      packName: "Villager Retaliation Pack",
      description: "Custom Villager Retaliation datapack",
      packFormat: 34,
      namespace: "my_pack",
      slug: "my_pack",
      locale: "en_us"
    },
    dialogue: {
      fileName: "my_pack_dialogue",
      options: [],
      lines: [],
      messages: [],
      openings: [],
      closings: [],
      pacify: []
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

function tooltipForTag(fieldId, value) {
  if (fieldId.includes("event_tags")) return EVENT_TAG_TOOLTIPS[value] || "Accepted village event tag.";
  if (fieldId.includes("professions")) return value === "none" || value === "unemployed"
    ? "Matches villagers with no profession."
    : `Matches ${humanize(value)} villagers.`;
  if (fieldId.includes("dispositions")) return DISPOSITION_TOOLTIPS[value] || "Villager mood filter.";
  if (fieldId.includes("reputation_levels")) return `Matches villagers at the ${humanize(value)} reputation tier.`;
  if (fieldId.includes("player_item_slots")) return ITEM_SLOT_TOOLTIPS[value] || "Player item slot filter.";
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

function parseNumber(value) {
  if (value === "" || value === null || value === undefined) return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function parseInteger(value) {
  const number = parseNumber(value);
  return number === undefined ? undefined : Math.trunc(number);
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
  return cleanObject({
    pack: {
      pack_format: state.meta.packFormat || 34,
      description: state.meta.description || state.meta.packName || "Villager Retaliation datapack"
    }
  });
}

function safeJson(value) {
  return JSON.stringify(cleanObject(value), null, 2) + "\n";
}

function dialoguePath() {
  return `data/villagerretaliation/dialogue/${state.meta.locale}/${state.dialogue.fileName}.json`;
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
    files[dialoguePath()] = safeJson({
      options: state.dialogue.options,
      lines: state.dialogue.lines,
      messages: state.dialogue.messages,
      openings: state.dialogue.openings,
      closings: state.dialogue.closings,
      pacify: state.dialogue.pacify
    });
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

function validate() {
  const checks = [];
  const namespacePattern = /^[a-z0-9_.-]+$/;
  const localePattern = /^[a-z]{2}_[a-z]{2}$/;

  if (!namespacePattern.test(state.meta.namespace)) {
    checks.push({ type: "error", title: "Pack namespace", text: "Use lowercase letters, numbers, underscores, dots, or hyphens." });
  }
  if (!localePattern.test(state.meta.locale)) {
    checks.push({ type: "warning", title: "Locale", text: "Locale folders usually look like en_us or fr_fr." });
  }
  if (!Number.isInteger(state.meta.packFormat) || state.meta.packFormat < 1) {
    checks.push({ type: "error", title: "Pack format", text: "pack_format must be a positive integer." });
  }

  for (const entry of state.dialogue.options) {
    if (!entry.id || !entry.label || !entry.type) {
      checks.push({ type: "error", title: "Dialogue option", text: "Every option needs an id, label, and type." });
      break;
    }
  }
  for (const entry of state.dialogue.lines) {
    if (!entry.type || !entry.text) {
      checks.push({ type: "error", title: "Dialogue line", text: "Every line needs a type and text." });
      break;
    }
  }
  for (const entry of state.dialogue.messages) {
    if (!entry.key || !entry.text) {
      checks.push({ type: "error", title: "Dialogue message", text: "Every message needs a key and text." });
      break;
    }
  }
  for (const entry of state.notifications.notifications) {
    if (!entry.trigger || !entry.text) {
      checks.push({ type: "error", title: "Notification", text: "Every notification needs a trigger and text." });
      break;
    }
  }
  for (const entry of state.gifts.preferences) {
    if (!entry.reaction || (!entry.items?.length && !entry.tags?.length && !entry.item && !entry.tag)) {
      checks.push({ type: "error", title: "Gift preference", text: "Every preference needs a reaction and at least one item or tag." });
      break;
    }
  }
  for (const entry of state.gifts.rewards) {
    if (!entry.item) {
      checks.push({ type: "error", title: "Gift reward", text: "Every reward needs an item id." });
      break;
    }
  }
  for (const entry of state.pacification.payments) {
    if (!entry.items?.length && !entry.tags?.length && !entry.item && !entry.tag) {
      checks.push({ type: "error", title: "Pacification payment", text: "Every payment needs at least one item or tag." });
      break;
    }
  }
  for (const entry of state.stories.structures) {
    if (!entry.structure && !entry.structures?.length) {
      checks.push({ type: "error", title: "Story structure", text: "Every structure story needs a structure id." });
      break;
    }
  }
  for (const entry of state.stories.biomes) {
    if (!entry.biome && !entry.biomes?.length) {
      checks.push({ type: "error", title: "Story biome", text: "Every biome story needs a biome id." });
      break;
    }
  }
  if (checks.length === 0) {
    checks.push({ type: "ok", title: "Ready", text: "The generated datapack paths and required fields look good." });
  }
  return checks;
}

function render() {
  renderWorkspaceChrome();
  renderTabs();
  renderPanel();
  renderFiles();
  renderChecks();
  renderPreview();
}

function renderWorkspaceChrome() {
  els.workspace.classList.toggle("is-left-hidden", !showLeftPanel);
  els.workspace.classList.toggle("is-right-hidden", !showRightPanel);
  els.leftPanelToggleButton.classList.toggle("is-on", showLeftPanel);
  els.leftPanelToggleButton.setAttribute("aria-pressed", String(showLeftPanel));
  els.leftPanelToggleButton.setAttribute("aria-label", showLeftPanel ? "Hide left panel" : "Show left panel");
  els.leftPanelToggleButton.textContent = showLeftPanel ? "<" : ">";
  els.rightPanelToggleButton.classList.toggle("is-on", showRightPanel);
  els.rightPanelToggleButton.setAttribute("aria-pressed", String(showRightPanel));
  els.rightPanelToggleButton.setAttribute("aria-label", showRightPanel ? "Hide right panel" : "Show right panel");
  els.rightPanelToggleButton.textContent = showRightPanel ? ">" : "<";
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
    const counter = tab.querySelector(".tab-count");
    tab.classList.toggle("is-active", active);
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
  const files = generatedFiles();
  const paths = Object.keys(files).sort();
  if (!paths.includes(selectedPath)) {
    selectedPath = paths[0] || "pack.mcmeta";
  }
  els.fileCount.textContent = String(paths.length);
  els.fileTree.innerHTML = paths
    .map((path) => {
      const label = path.split("/").pop();
      const folder = path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "root";
      return `
        <button class="file-button ${path === selectedPath ? "is-active" : ""}" type="button" data-path="${escapeHtml(path)}">
          ${escapeHtml(label)}
          <small>${escapeHtml(folder)}</small>
        </button>
      `;
    })
    .join("");
}

function renderChecks() {
  const checks = validate();
  els.checkCount.textContent = String(checks.filter((check) => check.type !== "ok").length);
  els.checks.innerHTML = checks
    .map((check) => `
      <div class="check ${escapeHtml(check.type)}">
        <strong>${escapeHtml(check.title)}</strong>
        <span>${escapeHtml(check.text)}</span>
      </div>
    `)
    .join("");
}

function renderPreview() {
  const files = generatedFiles();
  const value = files[selectedPath];
  els.selectedPath.textContent = selectedPath;
  if (value instanceof Uint8Array) {
    els.preview.textContent = `Binary file preserved (${value.byteLength} bytes).`;
  } else {
    els.preview.textContent = value || "";
  }
}

function renderPanel() {
  if (activeSection === "overview") renderOverview();
  if (activeSection === "dialogue") renderDialogue();
  if (activeSection === "notifications") renderNotifications();
  if (activeSection === "gifts") renderGifts();
  if (activeSection === "pacification") renderPacification();
  if (activeSection === "stories") renderStories();
  if (activeSection === "names") renderNames();
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
      const isSelected = multiple ? selected.includes(option) : value === option;
      return `<option value="${escapeHtml(option)}" ${isSelected ? "selected" : ""}>${escapeHtml(humanize(option))}</option>`;
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

function renderValueTags(fieldId, tags) {
  if (!tags.length) return "";
  return `
    <div class="value-tags" aria-label="${escapeHtml(fieldId)} suggestions">
      ${tags.map((tag) => {
        const tooltip = tooltipForTag(fieldId, tag);
        return `
          <button class="value-tag has-tooltip" type="button" data-action="insert-tag" data-target="${escapeHtml(fieldId)}" data-value="${escapeHtml(tag)}" data-tooltip="${escapeHtml(tooltip)}">
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
    <label class="toggle has-tooltip" for="${id}" data-tooltip="${escapeHtml(tip)}">
      <input id="${id}" name="${id}" type="checkbox" ${checked ? "checked" : ""}>
      <span class="toggle-name">${escapeHtml(label)}</span>
      <span class="toggle-choice toggle-false" aria-hidden="true">False</span>
      <span class="toggle-choice toggle-true" aria-hidden="true">True</span>
    </label>
  `;
}

function toggleGrid(flags, entry, prefix) {
  return `
    <div class="field full">
      <label>${prefix === "option" ? "Visibility and Requirements" : "Extra Filters"}</label>
      <div class="toggle-grid">
        ${toggle({ id: `${prefix}-show_for_adults`, label: "Show for adults", checked: entry.show_for_adults !== false, tooltip: tooltipForFlag("show_for_adults") })}
        ${toggle({ id: `${prefix}-show_for_babies`, label: "Show for babies", checked: entry.show_for_babies !== false, tooltip: tooltipForFlag("show_for_babies") })}
        ${flags.map((flag) => toggle({ id: `${prefix}-${flag}`, label: humanize(flag), checked: entry[flag] === true, tooltip: tooltipForFlag(flag) })).join("")}
      </div>
    </div>
  `;
}

function renderOverview() {
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div>
          <p class="eyebrow">Project</p>
          <h2>Pack Setup</h2>
        </div>
        <span class="pill">Minecraft 1.21.1 docs</span>
      </div>
      <div class="form-grid overview-grid">
        ${field({ id: "meta-packName", label: "Pack name", value: state.meta.packName, className: "span-7" })}
        ${field({ id: "meta-packFormat", label: "Pack format", value: state.meta.packFormat, type: "number", help: "Use 34 for this Minecraft target.", className: "span-5" })}
        ${field({ id: "meta-namespace", label: "Story namespace", value: state.meta.namespace, help: "Story discovery can use your namespace.", className: "span-6" })}
        ${field({ id: "meta-slug", label: "File slug", value: state.meta.slug, help: "Used in generated file names.", className: "span-6" })}
        ${field({ id: "meta-locale", label: "Locale", value: state.meta.locale, help: "Dialogue and notifications load en_us first.", className: "span-5" })}
        ${textareaField({ id: "meta-description", label: "Description", value: state.meta.description, className: "span-12", rows: 2 })}
      </div>
    </div>
  `;
}

function renderEntryTabs(kinds, activeKey, scope) {
  return `
    <div class="entry-tabs" data-scope="${scope}">
      ${kinds.map((kind) => `
        <button class="entry-tab has-tooltip ${kind.key === activeKey ? "is-active" : ""}" type="button" data-kind="${kind.key}" data-tooltip="${escapeHtml(KIND_TOOLTIPS[`${scope}.${kind.key}`] || "")}">
          ${escapeHtml(kind.label)}
        </button>
      `).join("")}
    </div>
  `;
}

function renderEntryList(collection, kind, section) {
  if (collection.length === 0) {
    return `<div class="empty-state">No ${escapeHtml(humanize(kind).toLowerCase())} yet.</div>`;
  }
  return collection
    .map((entry, index) => {
      const title = entry.id || entry.key || entry.trigger || entry.label || entry.text || entry.item || entry.name || `${humanize(kind)} ${index + 1}`;
      const detail = entry.type || entry.reaction || entry.world_text_kind || entry.structure || entry.biome || entry.items?.join(", ") || "";
      const active = editing && editing.section === section && editing.kind === kind && editing.index === index;
      return `
        <article class="entry-card ${active ? "is-active" : ""}">
          <div class="entry-object-header">
            <button class="entry-object-title" type="button" data-action="edit-entry" data-section="${section}" data-kind="${kind}" data-index="${index}">
              ${escapeHtml(title)}
            </button>
            <button class="entry-delete danger" type="button" data-action="delete-entry" data-section="${section}" data-kind="${kind}" data-index="${index}" aria-label="Delete ${escapeHtml(title)}">
              <span class="trash-icon" aria-hidden="true"></span>
            </button>
          </div>
          ${detail ? `<small>${escapeHtml(detail)}</small>` : ""}
        </article>
      `;
    })
    .join("");
}

function renderDialogue() {
  const collection = state.dialogue[activeDialogueKind];
  const entry = editing?.section === "dialogue" && editing.kind === activeDialogueKind
    ? collection[editing.index]
    : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div>
          <p class="eyebrow">data/villagerretaliation/dialogue</p>
          <h2>Dialogue</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-dialogue-example">Add Example</button>
      </div>
      <div class="form-grid">
        ${field({ id: "dialogue-fileName", label: "Dialogue file", value: state.dialogue.fileName, help: "Avoid global unless replacing the built-in file." })}
        ${field({ id: "dialogue-locale", label: "Locale", value: state.meta.locale })}
      </div>
      ${renderEntryTabs(DIALOGUE_KINDS, activeDialogueKind, "dialogue")}
      <div class="entry-layout">
        <div class="entry-list">${renderEntryList(collection, activeDialogueKind, "dialogue")}</div>
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
    ${listField({ id: "dialogue-dispositions", label: "Dispositions", value: entry.dispositions, help: "Blank means any mood." })}
  `;

  if (kind === "options") {
    return `
      <div class="form-grid">
        ${field({ id: "dialogue-id", label: "Option id", value: entry.id })}
        ${field({ id: "dialogue-label", label: "Button label", value: entry.label })}
        ${selectField({ id: "dialogue-type", label: "Dialogue type", value: entry.type, options: CONSTANTS.dialogueTypes, allowBlank: false })}
        ${field({ id: "dialogue-order", label: "Order", value: entry.order ?? "", type: "number" })}
        ${commonFilters}
        ${listField({ id: "dialogue-player_items", label: "Required player items or tags", value: entry.player_items, help: "Use #minecraft:swords for item tags." })}
        ${listField({ id: "dialogue-player_item_slots", label: "Item slots", value: entry.player_item_slots, help: CONSTANTS.itemSlots.join(", ") })}
        ${toggleGrid(CONSTANTS.optionFlags, entry, "option")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  if (kind === "lines") {
    return `
      <div class="form-grid">
        ${field({ id: "dialogue-id", label: "Line id", value: entry.id })}
        ${selectField({ id: "dialogue-type", label: "Dialogue type", value: entry.type, options: CONSTANTS.dialogueTypes, allowBlank: false })}
        ${textareaField({ id: "dialogue-text", label: "Line text", value: entry.text, className: "full", rows: 3 })}
        ${listField({ id: "dialogue-option", label: "Option id(s)", value: entry.option ?? entry.option_ids, help: "Link to a custom or built-in talk option." })}
        ${commonFilters}
        ${listField({ id: "dialogue-weather", label: "Weather", value: entry.weather, help: CONSTANTS.weather.join(", ") })}
        ${listField({ id: "dialogue-times", label: "Times", value: entry.times, help: CONSTANTS.times.join(", ") })}
        ${listField({ id: "dialogue-event_tags", label: "Village event tags", value: entry.event_tags })}
        ${listField({ id: "dialogue-player_event_tags", label: "Player event tags", value: entry.player_event_tags })}
        ${listField({ id: "dialogue-player_items", label: "Required player items or tags", value: entry.player_items })}
        ${listField({ id: "dialogue-player_item_slots", label: "Item slots", value: entry.player_item_slots })}
        ${listField({ id: "dialogue-story_structure", label: "Story structures", value: entry.story_structure ?? entry.story_structures })}
        ${listField({ id: "dialogue-story_biome", label: "Story biomes", value: entry.story_biome ?? entry.story_biomes })}
        ${listField({ id: "dialogue-recruitment_followup_scenarios", label: "Recruitment follow-up scenarios", value: entry.recruitment_followup_scenarios })}
        ${listField({ id: "dialogue-recruitment_memory_scenarios", label: "Recruitment memory scenarios", value: entry.recruitment_memory_scenarios })}
        ${field({ id: "dialogue-min_recruitment_follow_distance", label: "Minimum follow distance", value: entry.min_recruitment_follow_distance ?? "", type: "number" })}
        ${selectField({ id: "dialogue-gift_advice", label: "Gift advice filter", value: entry.gift_advice, options: CONSTANTS.giftAdvice })}
        ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
        ${toggleGrid(CONSTANTS.lineFlags, entry, "line")}
      </div>
      ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
    `;
  }

  if (kind === "messages") {
    return `
      <div class="form-grid">
        ${field({ id: "dialogue-id", label: "Message id", value: entry.id })}
        ${field({ id: "dialogue-key", label: "Message key", value: entry.key, help: "Gift rules can point response_key at custom message keys." })}
        ${textareaField({ id: "dialogue-text", label: "Message text", value: entry.text, className: "full", rows: 3 })}
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
        ${textareaField({ id: "dialogue-text", label: "Pacify text", value: entry.text, className: "full", rows: 3 })}
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
      ${textareaField({ id: "dialogue-text", label: "Text", value: entry.text, className: "full", rows: 3 })}
      ${commonFilters}
      ${field({ id: "dialogue-weight", label: "Weight", value: entry.weight ?? "", type: "number" })}
      ${toggleGrid(["first_conversation_only", "first_village_interaction_only"], entry, "opening")}
    </div>
    ${formActions(action, "save-dialogue-entry", "clear-dialogue-form")}
  `;
}

function formActions(actionLabel, saveAction, clearAction) {
  return `
    <div class="form-actions">
      <button class="button button-primary" type="submit" data-action="${saveAction}">${actionLabel}</button>
      <button class="button button-secondary" type="button" data-action="${clearAction}">Clear</button>
    </div>
  `;
}

function renderNotifications() {
  const collection = state.notifications.notifications;
  const entry = editing?.section === "notifications" ? collection[editing.index] : {};
  els.panel.innerHTML = `
    <div class="builder-content">
      <div class="builder-header">
        <div>
          <p class="eyebrow">data/villagerretaliation/notifications</p>
          <h2>Notifications</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-notification-example">Add Example</button>
      </div>
      <div class="form-grid">
        ${field({ id: "notifications-fileName", label: "Notification file", value: state.notifications.fileName, help: "Avoid global unless replacing the built-in file." })}
        ${field({ id: "notifications-locale", label: "Locale", value: state.meta.locale })}
      </div>
      <div class="entry-layout">
        <div class="entry-list">${renderEntryList(collection, "notifications", "notifications")}</div>
        <form class="entry-form" data-form="notifications">
          <div class="form-grid">
            ${field({ id: "notification-id", label: "Notification id", value: entry.id })}
            ${field({ id: "notification-trigger", label: "Trigger", value: entry.trigger, attrs: 'list="notification-triggers"', help: "Use a built-in trigger or a custom trigger emitted by code." })}
            ${textareaField({ id: "notification-text", label: "Text", value: entry.text, className: "full", rows: 3 })}
            ${selectField({ id: "notification-kind", label: "HUD kind", value: entry.kind, options: CONSTANTS.hudKinds })}
            ${selectField({ id: "notification-world_text_kind", label: "World text kind", value: entry.world_text_kind ?? entry.style, options: CONSTANTS.worldTextKinds })}
            ${field({ id: "notification-color", label: "Color", value: entry.color, attrs: 'list="color-values"', help: "Named color, #RRGGBB, or #AARRGGBB." })}
            ${field({ id: "notification-text_color", label: "Text color", value: entry.text_color, attrs: 'list="color-values"' })}
            ${field({ id: "notification-chat_color", label: "Chat color", value: entry.chat_color, attrs: 'list="color-values"' })}
            ${listField({ id: "notification-professions", label: "Professions", value: entry.professions })}
            ${listField({ id: "notification-reputation_levels", label: "Reputation levels", value: entry.reputation_levels })}
            ${field({ id: "notification-min_reputation", label: "Minimum reputation", value: entry.min_reputation ?? "", type: "number" })}
            ${field({ id: "notification-max_reputation", label: "Maximum reputation", value: entry.max_reputation ?? "", type: "number" })}
            ${listField({ id: "notification-player_items", label: "Required player items or tags", value: entry.player_items })}
            ${listField({ id: "notification-player_item_slots", label: "Item slots", value: entry.player_item_slots })}
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
        <div>
          <p class="eyebrow">data/villagerretaliation/gifts</p>
          <h2>Gifts</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-gift-example">Add Example</button>
      </div>
      <div class="form-grid one">
        ${field({ id: "gifts-fileName", label: "Gift file", value: state.gifts.fileName, help: "Use default only when replacing all built-in gifts." })}
      </div>
      ${renderEntryTabs(GIFT_KINDS, activeGiftKind, "gifts")}
      <div class="entry-layout">
        <div class="entry-list">${renderEntryList(collection, activeGiftKind, "gifts")}</div>
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
        <div>
          <p class="eyebrow">data/villagerretaliation/pacification</p>
          <h2>Pacification Payments</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-pacification-example">Add Example</button>
      </div>
      <div class="form-grid one">
        ${field({ id: "pacification-fileName", label: "Pacification file", value: state.pacification.fileName, help: "Use default only when replacing the built-in emerald rule." })}
      </div>
      <div class="entry-layout">
        <div class="entry-list">${renderEntryList(collection, "payments", "pacification")}</div>
        <form class="entry-form" data-form="pacification">
          <div class="form-grid">
            ${listField({ id: "pacification-items", label: "Items", value: entry.items ?? entry.item })}
            ${listField({ id: "pacification-tags", label: "Tags", value: entry.tags ?? entry.tag })}
            ${listField({ id: "pacification-professions", label: "Professions", value: entry.professions })}
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
        <div>
          <p class="eyebrow">data/&lt;namespace&gt;/story_*</p>
          <h2>Story Discovery</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-story-example">Add Example</button>
      </div>
      <div class="form-grid">
        ${field({ id: "stories-namespace", label: "Story namespace", value: state.stories.namespace })}
        ${field({ id: "stories-radius", label: "Default structure radius", value: state.stories.radius ?? "", type: "number" })}
        ${field({ id: "stories-structureFileName", label: "Structure file", value: state.stories.structureFileName })}
        ${field({ id: "stories-biomeFileName", label: "Biome file", value: state.stories.biomeFileName })}
      </div>
      ${renderEntryTabs(STORY_KINDS, activeStoryKind, "stories")}
      <div class="entry-layout">
        <div class="entry-list">${renderEntryList(collection, activeStoryKind, "stories")}</div>
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
        <div>
          <p class="eyebrow">data/villagerretaliation/villager_names</p>
          <h2>Preset Names</h2>
        </div>
        <button class="button button-secondary" type="button" data-action="add-name-example">Add Example</button>
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

function saveDialogueEntry(event) {
  event.preventDefault();
  const kind = activeDialogueKind;
  let entry = {};
  if (kind === "options") {
    entry = readBooleans("option", CONSTANTS.optionFlags, {
      id: readValue("dialogue-id").trim(),
      label: readValue("dialogue-label").trim(),
      type: readValue("dialogue-type"),
      order: parseInteger(readValue("dialogue-order")),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      player_items: readList("dialogue-player_items"),
      player_item_slots: readList("dialogue-player_item_slots")
    });
  } else if (kind === "lines") {
    const optionIds = readList("dialogue-option");
    const storyStructures = readList("dialogue-story_structure");
    const storyBiomes = readList("dialogue-story_biome");
    entry = readBooleans("line", CONSTANTS.lineFlags, {
      id: readValue("dialogue-id").trim(),
      type: readValue("dialogue-type"),
      text: readValue("dialogue-text").trim(),
      option: optionIds.length <= 1 ? optionIds[0] : optionIds,
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      weather: readList("dialogue-weather"),
      times: readList("dialogue-times"),
      event_tags: readList("dialogue-event_tags"),
      player_event_tags: readList("dialogue-player_event_tags"),
      player_items: readList("dialogue-player_items"),
      player_item_slots: readList("dialogue-player_item_slots"),
      story_structures: storyStructures,
      story_biomes: storyBiomes,
      recruitment_followup_scenarios: readList("dialogue-recruitment_followup_scenarios"),
      recruitment_memory_scenarios: readList("dialogue-recruitment_memory_scenarios"),
      min_recruitment_follow_distance: parseInteger(readValue("dialogue-min_recruitment_follow_distance")),
      gift_advice: readValue("dialogue-gift_advice"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else if (kind === "messages") {
    entry = readBooleans("message", [], {
      id: readValue("dialogue-id").trim(),
      key: readValue("dialogue-key").trim(),
      text: readValue("dialogue-text").trim(),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else if (kind === "pacify") {
    entry = readBooleans("pacify", [], {
      id: readValue("dialogue-id").trim(),
      text: readValue("dialogue-text").trim(),
      outcomes: readList("dialogue-outcomes"),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  } else {
    entry = readBooleans("opening", ["first_conversation_only", "first_village_interaction_only"], {
      id: readValue("dialogue-id").trim(),
      text: readValue("dialogue-text").trim(),
      professions: readList("dialogue-professions"),
      dispositions: readList("dialogue-dispositions"),
      weight: parseInteger(readValue("dialogue-weight"))
    });
  }
  upsertEntry("dialogue", kind, cleanObject(entry));
}

function saveNotification(event) {
  event.preventDefault();
  const entry = readBooleans("notification", [], {
    id: readValue("notification-id").trim(),
    trigger: readValue("notification-trigger").trim(),
    text: readValue("notification-text").trim(),
    kind: readValue("notification-kind"),
    world_text_kind: readValue("notification-world_text_kind"),
    color: readValue("notification-color").trim(),
    text_color: readValue("notification-text_color").trim(),
    chat_color: readValue("notification-chat_color").trim(),
    professions: readList("notification-professions"),
    reputation_levels: readList("notification-reputation_levels"),
    min_reputation: parseInteger(readValue("notification-min_reputation")),
    max_reputation: parseInteger(readValue("notification-max_reputation")),
    player_items: readList("notification-player_items"),
    player_item_slots: readList("notification-player_item_slots"),
    weight: parseInteger(readValue("notification-weight")),
    chance: parseNumber(readValue("notification-chance"))
  });
  upsertEntry("notifications", "notifications", cleanObject(entry));
}

function saveGiftEntry(event) {
  event.preventDefault();
  const kind = activeGiftKind;
  const entry = kind === "preferences"
    ? {
        reaction: readValue("gift-reaction"),
        items: readList("gift-items"),
        tags: readList("gift-tags"),
        professions: readList("gift-professions"),
        reputation_per_item: parseInteger(readValue("gift-reputation_per_item")),
        response_key: readValue("gift-response_key").trim(),
        priority: parseInteger(readValue("gift-priority"))
      }
    : {
        item: readValue("gift-item").trim(),
        professions: readList("gift-professions"),
        reputation_levels: readList("gift-reputation_levels"),
        min_count: parseInteger(readValue("gift-min_count")),
        max_count: parseInteger(readValue("gift-max_count")),
        weight: parseInteger(readValue("gift-weight"))
      };
  upsertEntry("gifts", kind, cleanObject(entry));
}

function savePacification(event) {
  event.preventDefault();
  const entry = {
    items: readList("pacification-items"),
    tags: readList("pacification-tags"),
    professions: readList("pacification-professions"),
    count: parseInteger(readValue("pacification-count")),
    min_count: parseInteger(readValue("pacification-min_count")),
    max_count: parseInteger(readValue("pacification-max_count")),
    name: readValue("pacification-name").trim(),
    plural_name: readValue("pacification-plural_name").trim(),
    priority: parseInteger(readValue("pacification-priority"))
  };
  upsertEntry("pacification", "payments", cleanObject(entry));
}

function saveStoryEntry(event) {
  event.preventDefault();
  const kind = activeStoryKind;
  const ids = kind === "structures" ? readList("story-structures") : readList("story-biomes");
  const entry = kind === "structures"
    ? {
        structures: ids,
        name: readValue("story-name").trim(),
        radius: parseInteger(readValue("story-radius"))
      }
    : {
        biomes: ids,
        name: readValue("story-name").trim()
      };
  upsertEntry("stories", kind, cleanObject(entry));
}

function upsertEntry(section, kind, entry) {
  if (editing && editing.section === section && editing.kind === kind) {
    state[section][kind][editing.index] = entry;
    showToast("Entry updated.");
  } else {
    state[section][kind].push(entry);
    showToast("Entry added.");
  }
  editing = null;
  selectedPath = inferSelectedPath(section);
  render();
}

function inferSelectedPath(section) {
  if (section === "dialogue") return dialoguePath();
  if (section === "notifications") return notificationsPath();
  if (section === "gifts") return giftsPath();
  if (section === "pacification") return pacificationPath();
  if (section === "stories") return activeStoryKind === "structures" ? structurePath() : biomePath();
  if (section === "names") return namesPath();
  return selectedPath;
}

function clearEditing() {
  editing = null;
  render();
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
  input.focus();
}

function deleteEntry(section, kind, index) {
  state[section][kind].splice(index, 1);
  editing = null;
  showToast("Entry deleted.");
  render();
}

function addDialogueExample() {
  const slug = state.meta.slug || "my_pack";
  if (activeDialogueKind === "options") {
    state.dialogue.options.push({
      id: `${slug}.ask_local_rumors`,
      label: "Ask Local Rumors",
      type: "story",
      order: 30,
      show_for_babies: false
    });
  } else if (activeDialogueKind === "lines") {
    state.dialogue.lines.push({
      id: `${slug}.rumor.generic`,
      option: `${slug}.ask_local_rumors`,
      type: "story",
      text: "Roads keep secrets. Villages keep better ones.",
      weight: 10
    });
  } else if (activeDialogueKind === "messages") {
    state.dialogue.messages.push({
      id: `${slug}.gift.librarian.rare_book`,
      key: `${slug}.gift.librarian.rare_book`,
      text: "{gift_item}? This belongs near a reading lamp, not forgotten in a chest."
    });
  } else if (activeDialogueKind === "pacify") {
    state.dialogue.pacify.push({
      id: `${slug}.pacify.accepted`,
      text: "Fine. {payment_cost} {payment_items}, and we try peace again.",
      outcomes: ["success"],
      weight: 10
    });
  } else {
    state.dialogue[activeDialogueKind].push({
      id: `${slug}.${activeDialogueKind}.farmer`,
      text: "Good to see a steady face.",
      professions: ["farmer"],
      weight: 10
    });
  }
  selectedPath = dialoguePath();
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

function loadStarterPack() {
  state = createInitialState();
  state.meta.packName = "Village Rumors";
  state.meta.description = "Starter Villager Retaliation datapack";
  state.meta.namespace = "village_rumors";
  state.meta.slug = "village_rumors";
  state.dialogue.fileName = "village_rumors_dialogue";
  state.notifications.fileName = "village_rumors_notifications";
  state.gifts.fileName = "village_rumors_gifts";
  state.stories.namespace = "village_rumors";
  state.stories.structureFileName = "village_rumors_structures";
  state.stories.biomeFileName = "village_rumors_biomes";
  state.dialogue.options.push({
    id: "village_rumors.ask_local_rumors",
    label: "Ask Local Rumors",
    type: "story",
    order: 30,
    show_for_babies: false
  });
  state.dialogue.lines.push(
    {
      id: "village_rumors.rumor.generic",
      option: "village_rumors.ask_local_rumors",
      type: "story",
      text: "Roads keep secrets. Villages keep better ones.",
      weight: 10
    },
    {
      id: "village_rumors.share_story.haunted_keep",
      type: "share_story",
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
  selectedPath = dialoguePath();
  editing = null;
  render();
  showToast("Starter pack loaded.");
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function updateOverviewFromInput(target) {
  const id = target.id;
  if (id === "meta-packName") state.meta.packName = target.value;
  if (id === "meta-description") state.meta.description = target.value;
  if (id === "meta-packFormat") state.meta.packFormat = parseInteger(target.value) || 34;
  if (id === "meta-namespace") {
    state.meta.namespace = namespaceify(target.value);
    state.stories.namespace = state.meta.namespace;
  }
  if (id === "meta-slug") {
    const slug = normalizeFileName(target.value, "my_pack");
    state.meta.slug = slug;
    state.dialogue.fileName = `${slug}_dialogue`;
    state.notifications.fileName = `${slug}_notifications`;
    state.gifts.fileName = `${slug}_gifts`;
    state.pacification.fileName = `${slug}_pacification`;
    state.stories.structureFileName = `${slug}_structures`;
    state.stories.biomeFileName = `${slug}_biomes`;
  }
  if (id === "meta-locale") state.meta.locale = slugify(target.value, "en_us");
}

function updateSectionSettings(target) {
  if (target.id === "dialogue-fileName") state.dialogue.fileName = normalizeFileName(target.value, `${state.meta.slug}_dialogue`);
  if (target.id === "dialogue-locale") state.meta.locale = slugify(target.value, "en_us");
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
  const files = generatedFiles();
  const value = files[selectedPath];
  if (value instanceof Uint8Array) {
    showToast("Binary files cannot be copied as text.");
    return;
  }
  await navigator.clipboard.writeText(value || "");
  showToast("Copied current file.");
}

function downloadCurrentFile() {
  const files = generatedFiles();
  const value = files[selectedPath] || "";
  const blob = value instanceof Uint8Array
    ? new Blob([value])
    : new Blob([value], { type: "application/json" });
  downloadBlob(blob, selectedPath.split("/").pop() || "datapack-file");
}

async function exportZip() {
  const files = generatedFiles();
  const zip = createZip(files);
  const name = `${slugify(state.meta.packName || state.meta.slug, "villager_retaliation_pack")}.zip`;
  downloadBlob(new Blob([zip], { type: "application/zip" }), name);
  showToast("Datapack zip exported.");
}

function normalizeImportedPaths(fileMap) {
  const paths = Object.keys(fileMap);
  const packPath = paths.find((path) => path === "pack.mcmeta" || path.endsWith("/pack.mcmeta"));
  if (!packPath || packPath === "pack.mcmeta") return fileMap;
  const prefix = packPath.slice(0, -"pack.mcmeta".length);
  const normalized = {};
  for (const [path, value] of Object.entries(fileMap)) {
    normalized[path.startsWith(prefix) ? path.slice(prefix.length) : path] = value;
  }
  return normalized;
}

function isTextPath(path) {
  return /\.(json|mcmeta|mcfunction|txt|md|lang)$/i.test(path);
}

function importedKnownKind(path) {
  if (/^data\/villagerretaliation\/dialogue\/[^/]+\/.+\.json$/.test(path)) return "dialogue";
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
      imported[path] = isTextPath(path) ? decoder.decode(bytes) : bytes;
    }
  }
  ingestFiles(normalizeImportedPaths(imported));
  selectedPath = Object.keys(generatedFiles()).sort()[0] || "pack.mcmeta";
  editing = null;
  render();
  showToast("Import complete.");
}

function ingestFiles(files) {
  const extra = {};
  const knownCounts = {};
  for (const path of Object.keys(files)) {
    const normalizedPath = path.replace(/^\/+/, "");
    const kind = importedKnownKind(normalizedPath);
    if (kind) {
      knownCounts[kind] = (knownCounts[kind] || 0) + 1;
    }
  }
  for (const [path, value] of Object.entries(files)) {
    const normalizedPath = path.replace(/^\/+/, "");
    if (normalizedPath.endsWith("/")) continue;
    if (normalizedPath === "pack.mcmeta" && typeof value === "string") {
      try {
        const json = JSON.parse(value);
        state.meta.description = json.pack?.description || state.meta.description;
        state.meta.packFormat = Number(json.pack?.pack_format) || state.meta.packFormat;
      } catch {
        extra[normalizedPath] = value;
      }
      continue;
    }
    const knownKind = importedKnownKind(normalizedPath);
    if (knownKind && knownCounts[knownKind] > 1) {
      extra[normalizedPath] = value;
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
    json = JSON.parse(source);
  } catch {
    return false;
  }

  const dialogueMatch = path.match(/^data\/villagerretaliation\/dialogue\/([^/]+)\/(.+)\.json$/);
  if (dialogueMatch) {
    state.meta.locale = dialogueMatch[1];
    state.dialogue.fileName = normalizeFileName(dialogueMatch[2].split("/").pop(), state.dialogue.fileName);
    const profession = dialogueMatch[2].match(/^professions\/([^/]+)/)?.[1];
    mergeArray("dialogue", "options", withDefaultProfession(json.options, profession));
    mergeArray("dialogue", "lines", withDefaultProfession(json.lines, profession));
    mergeArray("dialogue", "messages", withDefaultProfession(json.messages, profession));
    mergeArray("dialogue", "openings", withDefaultProfession(json.openings, profession));
    mergeArray("dialogue", "closings", withDefaultProfession(json.closings, profession));
    mergeArray("dialogue", "pacify", withDefaultProfession(json.pacify, profession));
    return true;
  }

  const notificationMatch = path.match(/^data\/villagerretaliation\/notifications\/([^/]+)\/(.+)\.json$/);
  if (notificationMatch) {
    state.meta.locale = notificationMatch[1];
    state.notifications.fileName = normalizeFileName(notificationMatch[2].split("/").pop(), state.notifications.fileName);
    mergeArray("notifications", "notifications", json.notifications);
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
    mergeArray("notifications", "notifications", json.notifications);
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
  if (Array.isArray(json.notifications)) return { section: "notifications", kind: "notifications", key: "notifications" };
  if (Array.isArray(json.preferences)) return { section: "gifts", kind: "preferences", key: "preferences" };
  if (Array.isArray(json.rewards)) return { section: "gifts", kind: "rewards", key: "rewards" };
  if (Array.isArray(json.payments)) return { section: "pacification", kind: "payments", key: "payments" };
  return null;
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

function mergeArray(section, kind, entries) {
  if (!Array.isArray(entries)) return;
  state[section][kind].push(...entries.map((entry) => cleanObject(entry)));
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
  const count = readUint16(bytes, eocd + 10);
  let offset = readUint32(bytes, eocd + 16);
  const result = {};
  for (let index = 0; index < count; index++) {
    if (readUint32(bytes, offset) !== 0x02014b50) break;
    const flags = readUint16(bytes, offset + 8);
    const method = readUint16(bytes, offset + 10);
    const compressedSize = readUint32(bytes, offset + 20);
    const nameLength = readUint16(bytes, offset + 28);
    const extraLength = readUint16(bytes, offset + 30);
    const commentLength = readUint16(bytes, offset + 32);
    const localOffset = readUint32(bytes, offset + 42);
    const nameBytes = bytes.slice(offset + 46, offset + 46 + nameLength);
    const name = new TextDecoder(flags & 0x0800 ? "utf-8" : "utf-8").decode(nameBytes);
    offset += 46 + nameLength + extraLength + commentLength;
    if (name.endsWith("/")) continue;

    const localNameLength = readUint16(bytes, localOffset + 26);
    const localExtraLength = readUint16(bytes, localOffset + 28);
    const dataStart = localOffset + 30 + localNameLength + localExtraLength;
    const compressed = bytes.slice(dataStart, dataStart + compressedSize);
    const data = await decompressZipEntry(compressed, method);
    result[name] = isTextPath(name) ? decoder.decode(data) : data;
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
  activeSection = button.dataset.section;
  editing = null;
  render();
});

els.panel.addEventListener("click", (event) => {
  const entryTab = event.target.closest(".entry-tab");
  if (entryTab) {
    const scope = entryTab.closest(".entry-tabs").dataset.scope;
    if (scope === "dialogue") activeDialogueKind = entryTab.dataset.kind;
    if (scope === "gifts") activeGiftKind = entryTab.dataset.kind;
    if (scope === "stories") activeStoryKind = entryTab.dataset.kind;
    editing = null;
    render();
    return;
  }

  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) return;
  const action = actionButton.dataset.action;
  if (action === "insert-tag") {
    insertTag(actionButton.dataset.target, actionButton.dataset.value);
    return;
  }
  if (action === "edit-entry") {
    editing = {
      section: actionButton.dataset.section,
      kind: actionButton.dataset.kind,
      index: Number(actionButton.dataset.index)
    };
    render();
  }
  if (action === "delete-entry") {
    deleteEntry(actionButton.dataset.section, actionButton.dataset.kind, Number(actionButton.dataset.index));
  }
  if (action === "clear-dialogue-form" || action === "clear-notification-form" || action === "clear-gift-form" || action === "clear-pacification-form" || action === "clear-story-form") {
    clearEditing();
  }
  if (action === "add-dialogue-example") addDialogueExample();
  if (action === "add-notification-example") addNotificationExample();
  if (action === "add-gift-example") addGiftExample();
  if (action === "add-pacification-example") addPacificationExample();
  if (action === "add-story-example") addStoryExample();
  if (action === "add-name-example") addNameExample();
});

els.panel.addEventListener("submit", (event) => {
  const form = event.target.closest("form");
  if (!form) return;
  if (form.dataset.form === "dialogue") saveDialogueEntry(event);
  if (form.dataset.form === "notifications") saveNotification(event);
  if (form.dataset.form === "gifts") saveGiftEntry(event);
  if (form.dataset.form === "pacification") savePacification(event);
  if (form.dataset.form === "stories") saveStoryEntry(event);
});

els.panel.addEventListener("input", (event) => {
  if (activeSection === "overview") updateOverviewFromInput(event.target);
  updateSectionSettings(event.target);
  renderFiles();
  renderChecks();
  renderPreview();
});

els.fileTree.addEventListener("click", (event) => {
  const button = event.target.closest(".file-button");
  if (!button) return;
  selectedPath = button.dataset.path;
  renderFiles();
  renderPreview();
});

els.importInput.addEventListener("change", async () => {
  try {
    await handleImport([...els.importInput.files], [...els.importInput.files].some((file) => /\.zip$/i.test(file.name)));
  } catch (error) {
    showToast(error.message || "Import failed.");
  } finally {
    els.importInput.value = "";
  }
});

els.directoryInput.addEventListener("change", async () => {
  try {
    await handleImport([...els.directoryInput.files], true);
  } catch (error) {
    showToast(error.message || "Folder import failed.");
  } finally {
    els.directoryInput.value = "";
  }
});

els.exportButton.addEventListener("click", exportZip);
els.starterButton.addEventListener("click", loadStarterPack);
els.leftPanelToggleButton.addEventListener("click", () => {
  showLeftPanel = !showLeftPanel;
  renderWorkspaceChrome();
});
els.rightPanelToggleButton.addEventListener("click", () => {
  showRightPanel = !showRightPanel;
  renderWorkspaceChrome();
});
els.copyButton.addEventListener("click", copyCurrentFile);
els.downloadButton.addEventListener("click", downloadCurrentFile);

render();
