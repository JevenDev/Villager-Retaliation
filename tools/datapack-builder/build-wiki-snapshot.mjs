import { readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const wikiRoot = path.join(root, "wiki");
const outputPath = path.join(root, "wiki-snapshot.js");
const checkOnly = process.argv.includes("--check");

async function buildSnapshot() {
  const versions = await readdir(wikiRoot, { withFileTypes: true });
  const snapshot = {};

  for (const version of versions.filter((entry) => entry.isDirectory()).sort(byName)) {
    const versionRoot = path.join(wikiRoot, version.name);
    const files = await readdir(versionRoot, { withFileTypes: true });
    snapshot[version.name] = {};

    for (const file of files.filter((entry) => entry.isFile() && entry.name.endsWith(".md")).sort(byName)) {
      snapshot[version.name][file.name] = await readFile(path.join(versionRoot, file.name), "utf8");
    }
  }

  return `var VR_WIKI_SNAPSHOT = ${JSON.stringify(snapshot, null, 2)};\nwindow.VR_WIKI_SNAPSHOT = VR_WIKI_SNAPSHOT;\n`;
}

function byName(left, right) {
  return left.name.localeCompare(right.name, "en");
}

const generated = await buildSnapshot();

if (checkOnly) {
  const current = await readFile(outputPath, "utf8");
  if (current !== generated) {
    console.error("tools/datapack-builder/wiki-snapshot.js is out of date. Run node tools/datapack-builder/build-wiki-snapshot.mjs.");
    process.exitCode = 1;
  }
} else {
  await writeFile(outputPath, generated, "utf8");
}
