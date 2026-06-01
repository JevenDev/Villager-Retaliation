import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, "..", "..");
const dataDir = path.join(rootDir, "neoforge", "src", "main", "resources", "data", "villagerretaliation");
const assetsDir = path.join(rootDir, "neoforge", "src", "main", "resources", "assets", "villagerretaliation");

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

function questDialogue(tree) {
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

function questSteps(quest) {
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
  const offer = quest.offer || {};
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

function questObjectiveText(quest) {
  const proof = quest.target?.proof_item ? [`Proof: ${itemName(quest.target.proof_item)}`] : [];
  const objectives = firstArray(quest.objectives).map((objective) => `${objective.count || 1} ${itemName(objective.item || objective.items || objective.tag || objective.tags)}`);
  return [...proof, ...objectives];
}

function questRules(quest) {
  const rules = quest.rules || {};
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

function buildQuests() {
  const questRoot = path.join(dataDir, "quests");
  return walkJson(questRoot).map((file) => {
    const quest = readJson(file);
    const rel = path.relative(questRoot, file);
    const questline = quest.questline || rel.split(path.sep)[0];
    const treePath = path.join(dataDir, "dialogue_trees", "en_us", "quests", rel);
    const tree = fs.existsSync(treePath) ? readJson(treePath) : null;
    const rewards = quest.rewards || {};
    return {
      id: quest.id,
      slug: idTail(quest.id),
      title: quest.display?.title || titleCase(quest.id),
      description: quest.display?.description || "",
      questline,
      questlineLabel: titleCase(questline),
      requirements: questRequirements(quest),
      target: quest.target ? {
        structure: quest.target.structure ? titleCase(quest.target.structure) : "",
        proofItem: quest.target.proof_item ? itemName(quest.target.proof_item) : "",
        searchRadius: quest.target.search_radius || null,
        discoveryRadius: quest.target.discovery_radius || null
      } : null,
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
      dialogue: questDialogue(tree)
    };
  }).sort((a, b) => a.questlineLabel.localeCompare(b.questlineLabel) || a.title.localeCompare(b.title));
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
  return {
    dialogueLinesEstimate: dialogueLines + forcedDialogueLines + dialogueTreeLines,
    dialogueLineBreakdown: {
      dialogue: dialogueLines,
      forcedDialogue: forcedDialogueLines,
      dialogueTrees: dialogueTreeLines
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
fs.writeFileSync(path.join(scriptDir, "site-data.js"), output, "utf8");
console.log(`Generated player wiki data: ${data.quests.length} quests, ${data.advancements.length} advancements, ${data.skillTrades.reduce((sum, group) => sum + group.count, 0)} skill trades.`);
