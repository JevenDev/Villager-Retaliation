import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { spawnSync } from "node:child_process";

const require = createRequire(import.meta.url);
const model = require("./quest-builder/quest-model.js");
const workspace = await mkdtemp(path.join(os.tmpdir(), "vr-quest-builder-output-"));

try {
  for (const [name, quest] of [
    ["linear", model.createLinearQuest("builder_test")],
    ["branching", model.createBranchingQuest("builder_test")]
  ]) {
    const file = path.join(workspace, `${name}.json`);
    await writeFile(file, JSON.stringify(quest, null, 2) + "\n", "utf8");
    const result = spawnSync("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", file], {
      cwd: path.resolve(import.meta.dirname, ".."),
      encoding: "utf8",
      shell: false
    });
    assert.equal(result.status, 0, `Generated ${name} template failed the canonical quest validator.\n${result.stdout}\n${result.stderr}`);
  }
} finally {
  await rm(workspace, { recursive: true, force: true });
}

console.log("Quest builder output tests passed.");
