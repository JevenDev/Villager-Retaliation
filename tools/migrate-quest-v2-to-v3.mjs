import { lstat, readdir, readFile, mkdir, writeFile, copyFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { migrationReceipts } from "./quest-v3-compatibility.mjs";
import { validateContentSchema } from "./content-schema-validation.mjs";
const { V2, migrate } = createRequire(import.meta.url)("./quest-v3-format.js");
const schema = JSON.parse(await readFile(new URL("./datapack-builder/quest-v3.schema.json", import.meta.url), "utf8"));
const args = process.argv.slice(2);
const check = args.includes("--check");
const outputIndex = args.indexOf("--out-dir");
const input = args.find((value, index) => !value.startsWith("--") && (outputIndex < 0 || index !== outputIndex + 1));
if (!input || (!check && (outputIndex < 0 || !args[outputIndex + 1]))) {
  console.error("Usage: node tools/migrate-quest-v2-to-v3.mjs <pack-directory> --check | --out-dir <new-directory>");
  process.exit(1);
}
const source = path.resolve(input);
const output = outputIndex >= 0 ? path.resolve(args[outputIndex + 1]) : null;
if (output && (output === source || output.startsWith(source + path.sep) || source.startsWith(output + path.sep))) throw new Error("Output must be separate from the source pack");
const files = [];
async function scan(directory) {
  for (const name of (await readdir(directory)).sort()) {
    const file = path.join(directory, name);
    const info = await lstat(file);
    if (info.isSymbolicLink()) throw new Error(`Symbolic links are not supported: ${file}`);
    if (info.isDirectory()) await scan(file);
    else if (info.isFile()) files.push(file);
  }
}
await scan(source);
const converted = new Map();
const definitions = new Map();
for (const file of files.filter(file => file.endsWith(".json"))) {
  const value = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  definitions.set(path.relative(source, file).split(path.sep).join("/"), value);
  if (value.schema === V2) {
    try {
      const quest = migrate(value);
      const issues = validateContentSchema(quest, schema);
      if (issues.length) throw new Error(issues.map(issue => `${issue.pointer}: ${issue.message}`).join("\n"));
      converted.set(file, quest);
    }
    catch (error) { throw new Error(`${path.relative(source, file)}: ${error.message}`); }
  }
}
const receipts = migrationReceipts(definitions);
if (!check) {
  await mkdir(output, { recursive: false });
  for (const file of files) {
    const destination = path.join(output, path.relative(source, file));
    await mkdir(path.dirname(destination), { recursive: true });
    if (converted.has(file)) await writeFile(destination, JSON.stringify(converted.get(file), null, 2) + "\n", { flag: "wx" });
    else await copyFile(file, destination);
  }
  for (const [relative, receipt] of receipts) {
    const destination = path.join(output, relative);
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, JSON.stringify(receipt, null, 2) + "\n", { flag: "wx" });
  }
}
console.log(`${check ? "Checked" : "Migrated"} ${converted.size} quests. Source pack unchanged.`);
