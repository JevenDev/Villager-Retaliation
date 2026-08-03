import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..", "..");
const outputPath = path.join(scriptDir, "site-data.js");
const checkOnly = process.argv.includes("--check");

const catalog = [
  ["Pack-Development.md", "Getting Started", "package-open", "Folder layout, namespaces, overrides, reloads, and a safe pack-authoring workflow."],
  ["JSON-Reference.md", "Getting Started", "braces", "Shared IDs, selectors, conditions, quest facts, actions, weights, and message-key rules."],
  ["First-Quest.md", "Getting Started", "route", "Build and test a complete quest module v2 file from the smallest working shape."],
  ["Datapack-Generator.md", "Getting Started", "hammer", "Use the bundled browser generator and understand which versioned surfaces it writes."],
  ["Example-Packs.md", "Getting Started", "folder-down", "Copy complete packs ranging from one focused feature to persistent cinematic encounters."],
  ["Dialogue.md", "Dialogue", "message-square-text", "Talk options, replies, keyed messages, openings, closings, and pacify lines."],
  ["Dialogue-Requests.md", "Dialogue", "messages-square", "Request families and the option-to-line contract behind the Talk menu."],
  ["Dialogue-Trees.md", "Dialogue", "git-fork", "Branching authored conversations inside and outside quest modules."],
  ["Forced-Dialogue.md", "Dialogue", "message-square-warning", "Event-driven confrontations, payment choices, reactions, and chat barks."],
  ["Notifications.md", "Dialogue", "bell", "HUD notices and floating world text with locale-aware overrides."],
  ["Notification-Triggers.md", "Dialogue", "zap", "The built-in notification triggers and when each family is emitted."],
  ["Event-Tags.md", "Dialogue", "tags", "Durable villager and player event memory used by dialogue filters."],
  ["Localization.md", "Dialogue", "languages", "Datapack locale overlays, message keys, GUI translations, and resource-pack language files."],
  ["Quests.md", "Quests & Scenes", "scroll-text", "Quest module v2 providers, stages, objectives, transitions, branches, rewards, and diagnostics."],
  ["Dialogue-And-Quests.md", "Quests & Scenes", "workflow", "Choose clean ownership boundaries between quests, dialogue, trees, and forced scenes."],
  ["Quest-Scenes.md", "Quests & Scenes", "clapperboard", "Persistent actors, scene steps, encounters, recovery, scheduling, and extension hooks."],
  ["Quest-Runtime-Roadmap.md", "Quests & Scenes", "milestone", "The high-level runtime contract for typed actors, resumable scenes, and recovery."],
  ["Gifts.md", "Economy & Progression", "gift", "Gift preferences, reactions, trust caps, and high-reputation reward rolls."],
  ["Pacification.md", "Economy & Progression", "hand-coins", "Items and profession-specific costs that calm hostile villagers."],
  ["Profession-Loot.md", "Economy & Progression", "package", "Profession-aware drop rules backed by normal Minecraft loot tables."],
  ["Skill-Trades.md", "Economy & Progression", "badge-percent", "Skill-gated trades, rank bands, refresh cycles, and targetable Special Orders."],
  ["Sell-Box-And-Daily-Market.md", "Economy & Progression", "store", "Add, override, group, or disable daily market sell-price definitions."],
  ["Builder-Structures.md", "Economy & Progression", "building-2", "Add structure templates and currency costs to the hired-builder menu."],
  ["Natural-Job-Armor.md", "Economy & Progression", "shield", "Difficulty-aware armor profiles for newly spawned villagers."],
  ["Story-Discovery.md", "World & Identity", "map", "Structure and biome pools used by story-sharing dialogue."],
  ["Villager-Names.md", "World & Identity", "user-round", "Add to or replace the preset villager-name pool."],
  ["Village-Names.md", "World & Identity", "landmark", "Customize generated village names and naming fragments."],
  ["Player-Raids.md", "World & Identity", "flag", "Configure datapack loadouts used by villagers defending against player sieges."],
  ["Resource-Pack-Models.md", "Resource Packs", "image", "Combat textures, item models, trader visuals, and compatibility advice."],
  ["Pack-Format-Changes.md", "Reference", "history", "Live beta.13 target notes plus migration guidance for older pack layouts."]
];

const implementationDocs = [
  ["docs/quest-scene-runtime.md", "Runtime Internals", "database-zap", "Durable quest-run identity, operation ownership, continuations, cleanup, and downed-state contracts."],
  ["docs/tracked-villages.md", "Runtime Internals", "map-pinned", "Tracked-village identity, footprints, allegiance, naming, lifecycle, and administration."]
];

function readText(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8").replace(/\r\n/g, "\n");
}

function slugFor(file) {
  return file.replace(/^.*\//, "").replace(/\.md$/i, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function titleFrom(markdown, fallback) {
  return markdown.match(/^#\s+(.+)$/m)?.[1]?.trim() || fallback;
}

function plainText(markdown) {
  return markdown.replace(/```[\s\S]*?```/g, " ").replace(/<[^>]+>/g, " ")
    .replace(/`([^`]+)`/g, "$1").replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1 $2")
    .replace(/[#>*|_-]/g, " ").replace(/\s+/g, " ").trim();
}

function headingsFrom(markdown) {
  return [...markdown.matchAll(/^(#{2,3})\s+(.+)$/gm)].map((match) => ({
    level: match[1].length,
    title: match[2].trim()
  }));
}

function classifyExample(section, index, code) {
  const label = section.toLowerCase();
  if (/(minimal|smallest|simple|basic|first|add one|starter)/.test(label)) return "Starter";
  if (/(advanced|branch|encounter|scene|replace|remove|override|condition|migration|localization|payment|armor set|structure target|shared action|quest fact)/.test(label)) return "Advanced";
  if (index === 0) return "Starter";
  return "Advanced";
}

function examplesFrom(page) {
  const lines = page.markdown.split("\n");
  const examples = [];
  let heading = page.title;
  let inFence = false;
  let language = "";
  let code = [];

  for (const line of lines) {
    if (!inFence) {
      const headingMatch = line.match(/^#{2,4}\s+(.+)$/);
      if (headingMatch) heading = headingMatch[1].trim();
      const fence = line.match(/^```([a-z0-9_-]*)\s*$/i);
      if (fence) {
        language = fence[1].toLowerCase();
        inFence = true;
        code = [];
      }
      continue;
    }
    if (line.startsWith("```")) {
      if (language === "json" || language === "jsonc") {
        const value = code.join("\n").trim();
        if (value) {
          const index = examples.length;
          examples.push({
            id: `${page.slug}-example-${index + 1}`,
            page: page.slug,
            pageTitle: page.title,
            section: heading,
            level: classifyExample(heading, index, value),
            language,
            code: value
          });
        }
      }
      inFence = false;
      language = "";
      code = [];
      continue;
    }
    code.push(line);
  }
  return examples;
}

function pageRecord(file, group, icon, description, sourceKind = "wiki") {
  const source = sourceKind === "wiki" ? `wiki/${file}` : file;
  const markdown = readText(source);
  return {
    slug: slugFor(file),
    file: file.replace(/^.*\//, ""),
    source,
    sourceKind,
    group,
    icon,
    title: titleFrom(markdown, file),
    description,
    markdown,
    text: plainText(markdown),
    headings: headingsFrom(markdown)
  };
}

const pages = [
  ...catalog.map((entry) => pageRecord(...entry)),
  ...implementationDocs.map((entry) => pageRecord(...entry, "implementation"))
];

const related = {
  "pack-development": ["json-reference", "datapack-generator", "example-packs", "pack-format-changes"],
  "json-reference": ["pack-development", "quests", "dialogue", "forced-dialogue"],
  "first-quest": ["quests", "dialogue-and-quests", "quest-scenes"],
  "dialogue": ["dialogue-requests", "dialogue-trees", "forced-dialogue", "localization"],
  "dialogue-requests": ["dialogue", "event-tags", "notifications"],
  "dialogue-trees": ["dialogue-and-quests", "quests", "quest-scenes"],
  "forced-dialogue": ["dialogue", "notification-triggers", "localization"],
  "notifications": ["notification-triggers", "localization", "event-tags"],
  "quests": ["first-quest", "dialogue-and-quests", "quest-scenes", "quest-scene-runtime"],
  "dialogue-and-quests": ["quests", "dialogue-trees", "forced-dialogue"],
  "quest-scenes": ["quests", "quest-scene-runtime", "quest-runtime-roadmap"],
  "quest-runtime-roadmap": ["quest-scenes", "quest-scene-runtime"],
  "gifts": ["json-reference", "localization", "profession-loot"],
  "pacification": ["forced-dialogue", "gifts", "json-reference"],
  "skill-trades": ["datapack-generator", "json-reference", "example-packs"],
  "sell-box-and-daily-market": ["json-reference", "example-packs"],
  "builder-structures": ["json-reference", "example-packs"],
  "natural-job-armor": ["player-raids", "json-reference"],
  "story-discovery": ["dialogue-requests", "tracked-villages"],
  "village-names": ["tracked-villages", "villager-names"],
  "player-raids": ["natural-job-armor", "forced-dialogue", "tracked-villages"],
  "pack-format-changes": ["pack-development", "example-packs", "datapack-generator"],
  "quest-scene-runtime": ["quest-scenes", "quests", "quest-runtime-roadmap"],
  "tracked-villages": ["village-names", "story-discovery", "player-raids"]
};
for (const page of pages) page.related = related[page.slug] || [];

const examples = pages.filter((page) => page.sourceKind === "wiki").flatMap(examplesFrom);

const packDirectories = fs.readdirSync(path.join(repoRoot, "example-packs"), { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => {
    const packRoot = path.join(repoRoot, "example-packs", entry.name);
    const readmePath = path.join(packRoot, "README.md");
    const readme = fs.existsSync(readmePath) ? fs.readFileSync(readmePath, "utf8").replace(/\r\n/g, "\n") : "";
    const files = [];
    const visit = (directory) => {
      for (const child of fs.readdirSync(directory, { withFileTypes: true })) {
        const absolute = path.join(directory, child.name);
        if (child.isDirectory()) visit(absolute);
        else files.push(path.relative(packRoot, absolute).replaceAll("\\", "/"));
      }
    };
    visit(packRoot);
    return {
      id: entry.name,
      title: titleFrom(readme, entry.name.replaceAll("-", " ").replace(/\b\w/g, (letter) => letter.toUpperCase())),
      description: plainText(readme).slice(0, 280) || "Repository example pack.",
      files
    };
  })
  .filter((pack) => pack.files.length > 0);

const versionMatch = readText("gradle.properties").match(/^mod_version=(.+)$/m);
const data = {
  version: versionMatch?.[1]?.trim() || "development",
  generatedAt: "repository build",
  pages,
  examples,
  packs: packDirectories
};
const output = `window.VR_DEVELOPER_WIKI_DATA = ${JSON.stringify(data, null, 2)};\n`;

if (checkOnly) {
  if (!fs.existsSync(outputPath) || fs.readFileSync(outputPath, "utf8").replace(/\r\n/g, "\n") !== output) {
    console.error("tools/developer-wiki/site-data.js is out of date. Run node tools/developer-wiki/build-data.mjs.");
    process.exit(1);
  }
  console.log(`Developer wiki data is current (${pages.length} pages, ${examples.length} JSON examples, ${packDirectories.length} packs).`);
} else {
  fs.writeFileSync(outputPath, output, "utf8");
  console.log(`Wrote ${path.relative(repoRoot, outputPath)} (${pages.length} pages, ${examples.length} JSON examples, ${packDirectories.length} packs).`);
}
