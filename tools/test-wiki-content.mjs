import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const devContent = path.join(repoRoot, "wiki", "dev", "content");
const failures = [];

function fail(message) {
  failures.push(message);
}

function read(relative) {
  return fs.readFileSync(path.join(repoRoot, relative), "utf8");
}

const markdownFiles = fs.readdirSync(devContent).filter((file) => file.endsWith(".md"));
const markdownSet = new Set(markdownFiles);

for (const file of markdownFiles) {
  const source = fs.readFileSync(path.join(devContent, file), "utf8");
  let example = 0;
  for (const match of source.matchAll(/```(?:json|jsonc)\s*\r?\n([\s\S]*?)```/gi)) {
    example++;
    try {
      JSON.parse(match[1]);
    } catch (error) {
      fail(`${file}: JSON example ${example} is not standalone valid JSON: ${error.message}`);
    }
  }
  for (const match of source.matchAll(/\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)/g)) {
    const target = path.basename(match[1]);
    if (!markdownSet.has(target)) fail(`${file}: missing Markdown link target ${match[1]}`);
  }
}

function visitJson(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) visitJson(absolute);
    else if (entry.name.endsWith(".json")) {
      try {
        JSON.parse(fs.readFileSync(absolute, "utf8"));
      } catch (error) {
        fail(`${path.relative(repoRoot, absolute)}: invalid JSON: ${error.message}`);
      }
    }
  }
}
visitJson(path.join(repoRoot, "example-packs"));

const exampleDocs = read("wiki/dev/content/Example-Packs.md");
for (const match of exampleDocs.matchAll(/`(example-packs\/[^`]+)`/g)) {
  const target = match[1].replaceAll("/", path.sep);
  if (!fs.existsSync(path.join(repoRoot, target))) fail(`Example-Packs.md: missing referenced path ${match[1]}`);
}

function commandLiterals(relative) {
  return [...new Set([...read(relative).matchAll(/literal\("([^"]+)"\)/g)].map((match) => match[1]))];
}

function requireCommandCoverage(sourceFile, docFile) {
  const docs = read(docFile);
  const missing = commandLiterals(sourceFile).filter((literal) => !docs.includes(literal));
  if (missing.length) fail(`${docFile}: missing registered command literals: ${missing.join(", ")}`);
}

requireCommandCoverage(
  "neoforge/src/main/java/com/jvn/villagerretaliation/command/VrPlayerCommands.java",
  "wiki/player/content/guides.js"
);
requireCommandCoverage(
  "neoforge/src/main/java/com/jvn/villagerretaliation/command/VrPlayerCommands.java",
  "wiki/dev/content/Commands.md"
);
requireCommandCoverage(
  "neoforge/src/main/java/com/jvn/villagerretaliation/command/VrAdminCommands.java",
  "wiki/dev/content/Commands.md"
);

for (const [file, required] of [
  ["wiki/player/content/guides.js", [
    "Quest-provider reservation",
    "renderCommandsGuide",
    "renderStudyGuide",
    "renderFiltersAndChecklistsGuide",
    "renderWalletsGuide",
    "renderGiftsGuide"
  ]],
  ["wiki/dev/content/Quests.md", ["blocks_hiring", "death_protection"]],
  ["wiki/dev/content/Quest-Scenes.md", ["custom_name_key", "boss_bar_title_key", "location_message_key", "trophy_name_key"]]
]) {
  const source = read(file);
  for (const text of required) if (!source.includes(text)) fail(`${file}: missing current documentation marker ${text}`);
}

const generatedContext = { window: {} };
vm.runInNewContext(read("wiki/player/site-data.js"), generatedContext);
const generated = generatedContext.window.VR_WIKI_DATA;
const generatedQuests = new Map(generated.quests.map(quest => [quest.id, quest]));
if (generatedQuests.size !== generated.quests.length) fail("Player wiki contains duplicate quest IDs.");
let sourceQuests = 0;
function checkGeneratedQuests(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      checkGeneratedQuests(file);
      continue;
    }
    if (entry.name !== "quest.json") continue;
    sourceQuests++;
    const quest = JSON.parse(fs.readFileSync(file, "utf8"));
    const page = generatedQuests.get(quest.id);
    if (!page) {
      fail(`Player wiki is missing ${quest.id}.`);
      continue;
    }
    const locale = JSON.parse(fs.readFileSync(path.join(path.dirname(file), "locales/en_us.json"), "utf8"));
    for (const field of ["title", "description"]) {
      const value = quest.display?.[field] ?? quest.metadata?.[field];
      const key = value?.key;
      const expanded = key?.startsWith("#") ? quest.localization_prefix + (key.length === 1 ? "" : "." + key.slice(1)) : key;
      const payload = key ? locale.messages[expanded] : value;
      const expected = typeof payload === "string" ? payload : payload?.lines?.[0];
      if (typeof expected !== "string" || page[field] !== expected) fail(`${quest.id}: generated ${field} does not match its English source.`);
    }
    if (page.questline !== (quest.metadata?.questline || "")) fail(`${quest.id}: generated questline was lost.`);
    const objectives = quest.stages.flatMap(stage => stage.objectives || []);
    const objectiveCount = objectives.length + (quest.target?.proof_item ? 1 : 0);
    if (page.objectives.length !== objectiveCount) fail(`${quest.id}: generated objectives were lost.`);
    const repeatability = quest.availability?.repeatable ? "Repeatable" : "One-time";
    if (page.rules[0] !== repeatability) fail(`${quest.id}: generated repeatability is incorrect.`);
    const hasDialogue = quest.stages.some(stage => stage.dialogue || stage.scenes?.length || stage.responses?.length);
    if (hasDialogue && !page.dialogue.stages?.length) fail(`${quest.id}: generated dialogue was lost.`);
  }
}
checkGeneratedQuests(path.join(repoRoot, "neoforge/src/main/resources/data/villagerretaliation/quests"));
if (sourceQuests !== generatedQuests.size) fail("Player wiki quest count does not match the built-in content.");
if (!(generated.stats.dialogueLineBreakdown.questModules > 0)) fail("Player wiki statistics omit quest dialogue.");

if (failures.length) {
  console.error(failures.map((failure) => `[wiki] ${failure}`).join("\n"));
  process.exit(1);
}

console.log(`Wiki content passed: ${markdownFiles.length} developer pages, valid JSON examples and example packs, current command coverage.`);
