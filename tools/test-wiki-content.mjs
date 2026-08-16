import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

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
  ["wiki/player/content/guides.js", ["Quest-provider reservation", "beta13RenderCommands"]],
  ["wiki/dev/content/Quests.md", ["blocks_hiring", "death_protection"]],
  ["wiki/dev/content/Quest-Scenes.md", ["custom_name_key", "boss_bar_title_key", "location_message_key", "trophy_name_key"]]
]) {
  const source = read(file);
  for (const text of required) if (!source.includes(text)) fail(`${file}: missing current documentation marker ${text}`);
}

if (failures.length) {
  console.error(failures.map((failure) => `[wiki] ${failure}`).join("\n"));
  process.exit(1);
}

console.log(`Wiki content passed: ${markdownFiles.length} developer pages, valid JSON examples and example packs, current command coverage.`);
