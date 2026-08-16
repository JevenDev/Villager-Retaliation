import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const workspace = await mkdtemp(path.join(tmpdir(), "vr-quest-v2-tools-"));

const conversionParent = path.join(root, "run", "quest-migrations");
await mkdir(conversionParent, { recursive: true });
const conversionWorkspace = await mkdtemp(path.join(conversionParent, "tool-test-"));
try {
  const validRoot = path.join(workspace, "valid");
  const invalidRoot = path.join(workspace, "invalid");
  const validQuest = path.join(validRoot, "quest.json");
  const invalidQuest = path.join(invalidRoot, "quest.json");
  const v1Quest = path.join(workspace, "migration-source-v1.json");
  const legacyDialogueTree = path.join(workspace, "migration-dialogue-tree.json");
  await mkdir(validRoot, { recursive: true });
  await mkdir(invalidRoot, { recursive: true });

  await writeJson(validQuest, {
    schema: "villagerretaliation:quest/v2",
    id: "villagerretaliation:tool_valid",
    localization_prefix: "villagerretaliation.quest.tool_valid",
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
            lines: { key: "#offer.lines" }
          },
          turn_in: {
            lines: { key: "#turn_in.lines" }
          }
        }
      }
    ],
    ui: {
      tracker_text: { key: "#ui.tracker_text" },
      color: "#ffffff",
      outline_color: "#000000",
      placeholders: {
        item: "objective.item"
      }
    }
  });

  await writeJson(invalidQuest, {
    localization_prefix: "villagerretaliation.quest.tool_invalid",
    schema: "villagerretaliation:quest/v2",
    id: "villagerretaliation:tool_invalid",
    external_scenes: ["villagerretaliation:legacy_tree"],
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
            label: { key: "#response.jump.label" },
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
          tracker_text: { key: "#ui.tracker_text" }
        }
      }
    ]
  });
  await mkdir(path.join(validRoot, "locales"), { recursive: true });
  await mkdir(path.join(invalidRoot, "locales"), { recursive: true });
  await writeJson(path.join(validRoot, "locales", "en_us.json"), {
    schema: "villagerretaliation:quest_locale/v1",
    messages: {
      "villagerretaliation.quest.tool_valid.offer.lines": { lines: ["Bring one paper."] },
      "villagerretaliation.quest.tool_valid.turn_in.lines": { lines: ["Thank you for the paper."] },
      "villagerretaliation.quest.tool_valid.ui.tracker_text": { lines: ["Bring {item}."] }
    }
  });
  await writeJson(path.join(invalidRoot, "locales", "en_us.json"), {
    schema: "villagerretaliation:quest_locale/v1",
    messages: {
      "villagerretaliation.quest.tool_invalid.response.jump.label": { lines: ["Jump"] },
      "villagerretaliation.quest.tool_invalid.ui.tracker_text": { lines: ["Bring {missing}."] }
    }
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

  await writeJson(legacyDialogueTree, {
    id: "villagerretaliation:tool_migrate",
    entries: [
      {
        id: "offer",
        start: "offer"
      }
    ],
    nodes: {
      offer: {
        lines: ["A legacy extracted offer."],
        end: true
      }
    }
  });

  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);

  const invalid = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", invalidQuest], { expectFailure: true });
  assert(invalid.stderr.includes("pointer=/entry_stage"), "Invalid v2 fixture did not report the entry_stage JSON pointer.");
  assert(invalid.stderr.includes("suggestion="), "Invalid v2 fixture did not include an actionable suggestion.");
  assert(invalid.stderr.includes("not_a_real_objective"), "Invalid v2 fixture did not report the bad objective type.");
  assert(invalid.stderr.includes("external dialogue roots are unsupported in beta.13 quest bundles"), "Invalid v2 fixture did not reject an external structural dialogue tree.");

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

  const conversion = JSON.parse(run("node", [
    "tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree",
    "--out-dir", conversionWorkspace
  ]).stdout);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", conversion.candidate]);
  assert(conversion.locale.endsWith("/locales/en_us.json"), "Converter did not emit bundle English.");
  assert(conversion.pack.endsWith("/pack.mcmeta"), "Converter did not emit pack.mcmeta.");

  const check = JSON.parse(run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree", "--check"]).stdout);
  assert(check.ok === true, "Migration check mode reported unsupported conversions for the simple fixture.");
  assert(check.comparison.objective_ids_preserved === true, "Migration check did not preserve objective ids.");

  const legacyConversion = run("node", [
    "tools/migrate-quest-v1-to-v2.mjs", v1Quest,
    "--dialogue-tree", legacyDialogueTree
  ]);
  const legacyCandidate = JSON.parse(legacyConversion.stdout);
  assert(!JSON.stringify(legacyCandidate).includes('"external'), "Converter emitted a public external-dialogue field.");
  assert(legacyConversion.stderr.includes("cannot be referenced by a beta.13 quest bundle"),
    "Converter did not diagnose the legacy external dialogue tree.");
  const legacyCheck = JSON.parse(run("node", [
    "tools/migrate-quest-v1-to-v2.mjs", v1Quest,
    "--dialogue-tree", legacyDialogueTree, "--check"
  ]).stdout);
  assert(legacyCheck.ok === false && legacyCheck.report.unsupported.length === 1,
    "Migration check accepted a legacy external dialogue tree without inlining it.");

  console.log("Quest module v2 tooling smoke test passed.");
} finally {
  await rm(workspace, { recursive: true, force: true });
  await rm(conversionWorkspace, { recursive: true, force: true });
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
