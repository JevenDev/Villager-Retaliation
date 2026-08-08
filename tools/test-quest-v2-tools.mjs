import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const workspace = await mkdtemp(path.join(tmpdir(), "vr-quest-v2-tools-"));

try {
  const validQuest = path.join(workspace, "valid-quest-v2.json");
  const invalidQuest = path.join(workspace, "invalid-quest-v2.json");
  const v1Quest = path.join(workspace, "migration-source-v1.json");

  await writeJson(validQuest, {
    schema: "villagerretaliation:quest/v2",
    id: "villagerretaliation:tool_valid",
    metadata: {
      revision: 2,
      migration: {
        active_policy: "keep",
        stage_aliases: { old_started: "started" }
      }
    },
    provider: {
      type: "villagerretaliation:villager"
    },
    entry_stage: "started",
    stages: [
      {
        id: "started",
        objectives: [
          {
            id: "bring_paper",
            type: "item_check",
            item: "minecraft:paper",
            count: 1
          }
        ],
        completion: {
          mode: "any",
          count: 1
        },
        bonuses: [
          {
            id: "quick_delivery",
            when: ["bring_paper"],
            actions: [{ type: "experience", amount: 2 }]
          }
        ],
        dialogue: {
          offer: {
            lines: ["Bring one paper."]
          },
          turn_in: {
            lines: ["Thank you for the paper."]
          }
        }
      }
    ],
    ui: {
      tracker_text: "Bring {item}.",
      color: "#ffffff",
      outline_color: "#000000",
      placeholders: {
        item: "objective.item"
      }
    }
  });

  await writeJson(invalidQuest, {
    schema: "villagerretaliation:quest/v2",
    id: "villagerretaliation:tool_invalid",
    provider: {
      type: "villagerretaliation:villager"
    },
    entry_stage: "missing",
    stages: [
      {
        id: "started",
        objectives: [
          {
            id: "bring_paper",
            type: "not_a_real_objective"
          }
        ],
        responses: [
          {
            id: "jump",
            label: "Jump",
            transition: {
              stage: "missing"
            },
            actions: [
              {
                type: "set_variable",
                stage: "other"
              }
            ]
          }
        ],
        ui: {
          tracker_text: "Bring {missing}."
        }
      }
    ]
  });

  await writeJson(v1Quest, {
    id: "villagerretaliation:tool_migrate",
    display: {
      title: "Tool Migration",
      description: "Small deterministic migration fixture."
    },
    questline: "tooling",
    offer: {
      professions: ["minecraft:librarian"]
    },
    objectives: [
      {
        id: "bring_book",
        type: "item_check",
        item: "minecraft:book",
        count: 1,
        complete_text: "Book delivered."
      }
    ],
    rules: {
      repeatable: false,
      completion_cooldown_ticks: 40,
      active: {
        conditions: [],
        hide_when_unmet: true,
        pause_progress_when_unmet: false
      },
      expiration: {
        after_ticks: 2400,
        consume: true
      },
      branch: {
        exclusive_group: "villagerretaliation:tool_route",
        exclusive_on: "completed"
      }
    },
    rewards: {
      experience: 5
    },
    dialogue: {
      start: ["Bring a book."],
      reminder: ["Still looking for the book."],
      turn_in: ["That book will help."]
    },
    tracker: {
      title: "Tool Migration",
      steps: {
        started: {
          text: "Bring the book.",
          show_progress: false
        }
      }
    }
  });

  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);

  const invalid = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", invalidQuest], { expectFailure: true });
  assert(invalid.stderr.includes("pointer=/entry_stage"), "Invalid v2 fixture did not report the entry_stage JSON pointer.");
  assert(invalid.stderr.includes("suggestion="), "Invalid v2 fixture did not include an actionable suggestion.");
  assert(invalid.stderr.includes("not_a_real_objective"), "Invalid v2 fixture did not report the bad objective type.");

  const first = run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree"]);
  const second = run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree"]);
  assert(first.stdout === second.stdout, "Migration output is not deterministic.");
  const migrated = JSON.parse(first.stdout);
  assert(migrated.availability.completion_cooldown_ticks === 40, "Migration dropped completion cooldown rules.");
  assert(migrated.availability.active.hide_when_unmet === true, "Migration dropped active-state rules.");
  assert(migrated.availability.expiration.after_ticks === 2400, "Migration dropped expiration rules.");
  assert(migrated.availability.branch.exclusive_on === "completed", "Migration dropped branch lock rules.");
  assert(migrated.stages[0].objectives[0].tracker.complete_text === "Book delivered.", "Migration dropped objective completion text.");
  assert(migrated.stages[0].ui.tracker_text === "Bring the book.", "Migration dropped stage tracker text.");

  const migratedQuest = path.join(workspace, "migrated-quest-v2.json");
  await writeFile(migratedQuest, first.stdout, "utf8");
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", migratedQuest]);

  const check = JSON.parse(run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree", "--check"]).stdout);
  assert(check.ok === true, "Migration check mode reported unsupported conversions for the simple fixture.");
  assert(check.comparison.objective_ids_preserved === true, "Migration check did not preserve objective ids.");

  console.log("Quest module v2 tooling smoke test passed.");
} finally {
  await rm(workspace, { recursive: true, force: true });
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: "utf8",
    shell: false
  });
  const failed = result.status !== 0;
  if (failed !== Boolean(options.expectFailure)) {
    throw new Error([
      `${command} ${args.join(" ")} exited with ${result.status}.`,
      result.stdout,
      result.stderr
    ].join("\n"));
  }
  return result;
}

async function writeJson(file, value) {
  await writeFile(file, JSON.stringify(value, null, 2) + "\n", "utf8");
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}
