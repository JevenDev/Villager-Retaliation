import path from "node:path";
import { compileStoryProject } from "./story-authoring.mjs";
import { StoryCompileError } from "./story-fragments.mjs";

function argumentsFor(args) {
  const options = {};
  let manifest;
  for (let index = 0; index < args.length; index++) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") return { help: true };
    if (arg === "--output") {
      if (options.output || !args[index + 1] || args[index + 1].startsWith("--")) throw new Error("--output requires one ZIP file path.");
      options.output = args[++index];
    } else if (arg === "--overwrite") options.overwrite = true;
    else if (arg.startsWith("--")) throw new Error("Unsupported option: " + arg);
    else if (manifest) throw new Error("Supply one story authoring manifest.");
    else manifest = arg;
  }
  if (!manifest) throw new Error("Supply a story authoring manifest. Use --help for usage.");
  if (options.overwrite && !options.output) throw new Error("--overwrite requires --output.");
  return { manifest, options };
}

function format(issue) {
  const location = (issue.file || "story") + (issue.pointer || "/");
  const via = issue.via?.length ? "\n  used from " + issue.via.map(source => source.file + (source.pointer || "/")).join(" -> ") : "";
  return location + ": " + issue.message + via;
}

try {
  const args = argumentsFor(process.argv.slice(2));
  if (args.help) {
    console.log([
      "Usage: node tools/compile-story.mjs project/story.json [--output story.zip] [--overwrite]",
      "",
      "Validates reusable story fragments and their expanded campaign, quest, and conversation resources.",
      "Without --output, validates only. Output is an ordinary datapack ZIP with a separate story-sources.json map.",
      "Existing outputs are preserved unless --overwrite is supplied. Sources stay inside the manifest directory.",
      "Authoring schema: tools/schemas/story-authoring-v1.schema.json"
    ].join("\n"));
  } else {
    const result = await compileStoryProject(args.manifest, args.options);
    for (const issue of result.warnings) console.warn("[WARNING] " + format(issue));
    console.log(args.options.output ? "Wrote " + path.resolve(args.options.output) : "Story validation passed.");
    console.log(result.outputs.size + " resources validated.");
  }
} catch (error) {
  if (error instanceof StoryCompileError) {
    for (const issue of error.diagnostics) console.error("[ERROR] " + format(issue));
  } else console.error("[ERROR] " + error.message);
  process.exitCode = 1;
}
