import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
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
      campaign: "villagerretaliation:tool_campaign",
      revision: 2,
      migration: {
        active_policy: "keep",
        stage_aliases: { old_started: "started" }
      }
    },
    provider: {
      type: "villagerretaliation:villager",
      blocks_hiring: true
    },
    availability: {
      weight: 7,
      selection_weight: 2,
      max_active_quests: 4,
      max_active_by_tag: { story: 2 }
    },
    events: [{ id: "reminder", event: "player_tick", priority: 3, chance: 0.5,
      exclusive: true, weight: 2, actions: [{ type: "tracker" }] }],
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
            lines: { key: "#offer.lines" },
            priority: 3,
            end: true
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
    campaign: "villagerretaliation:tool_campaign",
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

  const conditionFixture = JSON.parse(await readFile(validQuest, "utf8"));
  for (const campaign of [null, 7, true, "", "journey", "test:Bad", `test:${"x".repeat(257)}`, {}]) {
    const candidate = structuredClone(conditionFixture);
    candidate.metadata.campaign = campaign;
    await writeJson(validQuest, candidate);
    const failure = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
    assert(failure.stderr.includes("pointer=/metadata/campaign"), "Invalid campaign ownership lost its source pointer.");
  }
  await writeJson(validQuest, conditionFixture);
  const nestedConditions = [
    [{ type: "all_of", conditions: [{ type: "not", condition: { type: "reputation", min: 0 } }] }, false],
    [{ type: "and", conditions: [{ type: "unsupported_nested_condition" }] }, true],
    [{ type: "not", condition: { type: "all", conditions: [{ type: "unsupported_nested_condition" }] } }, true],
    [{ type: "not" }, true],
    [{ type: "or", conditions: [] }, true],
    [{ type: "any_of", conditions: [false] }, true]
  ];
  for (const [condition, expectFailure] of nestedConditions) {
    const candidate = structuredClone(conditionFixture);
    candidate.availability.conditions = [condition];
    await writeJson(validQuest, candidate);
    run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
  }
  const unreachable = structuredClone(conditionFixture);
  unreachable.stages.push({ id: "unreachable", objectives: [] });
  await writeJson(validQuest, unreachable);
  const reachabilityFailure = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
  assert(reachabilityFailure.stderr.includes("pointer=/stages/1/id"), "Unreachable stage diagnostic lost its authored pointer.");
  assert(reachabilityFailure.stderr.includes('stage "unreachable" is unreachable'), "Unreachable stages must reject content just as the runtime does.");
  unreachable.stages[0].next = "unreachable";
  await writeJson(validQuest, unreachable);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  await writeJson(validQuest, conditionFixture);

  for (const [override, expectFailure] of [
    [{}, false], [{ operation_id: false }, true], [{ milestone: "bad id" }, true],
    [{ outcome: null }, true], [{ required: false }, true], [{ required: true }, false]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.stages[0].on_enter = [{ type: "campaign_outcome", milestone: "dragon", outcome: "spared", operation_id: "decide", ...override }];
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) {
      assert(result.stderr.includes("registered action schema"), "Campaign action fields did not use the exported runtime schema.");
      assert(result.stderr.includes("pointer=/stages/0/on_enter/0/"), "Campaign action diagnostics lost their authored source pointer.");
    }
  }
  await writeJson(validQuest, conditionFixture);

  for (const [override, expectFailure] of [
    [{}, false], [{ mutation: "add" }, false], [{ mutation: "multiply" }, true],
    [{ fact: false }, true], [{ value: true }, true], [{ value: "x".repeat(4097) }, true],
    [{ operation_id: "has spaces" }, true], [{ required: false }, true]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.stages[0].on_enter = [{ type: "campaign_fact", fact: "clue", value: "true", operation_id: "discover", ...override }];
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) {
      assert(result.stderr.includes("registered action schema"), "Campaign fact action fields did not use the exported runtime schema.");
      assert(result.stderr.includes("pointer=/stages/0/on_enter/0/"), "Campaign fact action diagnostic lost its source pointer.");
    }
  }
  for (const [override, expectFailure] of [
    [{}, false], [{ comparison: "at_least", value: "2" }, false], [{ comparison: "guess" }, true],
    [{ campaign: "missing namespace" }, true], [{ fact: null }, true], [{ value: false }, true], [{ scope: "world" }, true],
    [{ value: "x".repeat(4097) }, true]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.availability.conditions = [{ type: "not", condition: {
      type: "campaign_fact", campaign: "test:campaign", fact: "clue", value: "true", ...override
    } }];
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) {
      assert(result.stderr.includes("registered condition schema"), "Campaign fact conditions did not use the exported runtime schema.");
      assert(result.stderr.includes("pointer=/availability/conditions/0/condition/"), "Nested campaign condition diagnostic lost its source pointer.");
    }
  }
  for (const [override, expectFailure] of [
    [{}, false], [{ scope: "world" }, false], [{ scope: "participated" }, false], [{ scope: "completed" }, false],
    [{ campaign: "missing namespace" }, true], [{ milestone: true }, true], [{ outcome: null }, true],
    [{ scope: "player" }, true], [{ scope: undefined }, true], [{ operation_id: "unexpected" }, true]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.availability.conditions = [{ type: "not", condition: {
      type: "campaign_outcome", campaign: "test:campaign", milestone: "dragon", outcome: "defeated", scope: "discovered", ...override
    } }];
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) {
      assert(result.stderr.includes("registered condition schema"), "Campaign outcome conditions did not use the exported runtime schema.");
      assert(result.stderr.includes("pointer=/availability/conditions/0/condition/"), "Outcome condition diagnostic lost its source pointer.");
    }
  }
  for (const [type, base, cases] of [
    ["campaign_character", { role: "mentor", state: "speaker" }, [
      [{}, false], [{ state: "bound" }, false], [{ state: "available" }, false], [{ state: "downed" }, false], [{ state: "lost" }, false], [{ state: "replaced" }, false],
      [{ role: null }, true], [{ state: "dead" }, true], [{ state: undefined }, true], [{ radius: 4 }, true], [{ role: "bad name" }, true]
    ]],
    ["campaign_location", { location: "home" }, [
      [{}, false], [{ radius: 1 }, false], [{ radius: 256 }, false], [{ radius: 0 }, true], [{ radius: 257 }, true],
      [{ radius: 1.5 }, true], [{ radius: "12" }, true], [{ radius: null }, true], [{ location: false }, true], [{ state: "near" }, true]
    ]]
  ]) {
    for (const [override, expectFailure] of cases) {
      const candidate = structuredClone(conditionFixture);
      candidate.availability.conditions = [{ type: "not", condition: {
        type, campaign: "test:campaign", ...base, ...override
      } }];
      await writeJson(validQuest, candidate);
      const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
      if (expectFailure) {
        assert(result.stderr.includes("registered condition schema"), "Campaign binding conditions must use the exported runtime schema.");
        assert(result.stderr.includes("pointer=/availability/conditions/0/condition/"), "Binding condition diagnostic lost its source pointer.");
      }
    }
  }
  await writeJson(validQuest, conditionFixture);

  const invalid = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", invalidQuest], { expectFailure: true });
  assert(invalid.stderr.includes("pointer=/entry_stage"), "Invalid v2 fixture did not report the entry_stage JSON pointer.");
  assert(invalid.stderr.includes("suggestion="), "Invalid v2 fixture did not include an actionable suggestion.");
  assert(invalid.stderr.includes("not_a_real_objective"), "Invalid v2 fixture did not report the bad objective type.");
  assert(invalid.stderr.includes("external dialogue roots are unsupported in beta.13 quest bundles"), "Invalid v2 fixture did not reject an external structural dialogue tree.");

  for (const [override, expectFailure] of [
    [{}, false], [{ required: true }, false], [{ required: false }, true],
    [{ reward: null }, true], [{ reward: 2 }, true], [{ operation_id: "has spaces" }, true]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.stages[0].on_enter = [{ type: "campaign_reward", reward: "catch_up", operation_id: "claim", ...override }];
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) {
      assert(result.stderr.includes("registered action schema"), "Campaign reward fields did not use the exported runtime schema.");
      assert(result.stderr.includes("pointer=/stages/0/on_enter/0/"), "Campaign reward diagnostic lost its source pointer.");
    }
  }
  await writeJson(validQuest, conditionFixture);

  for (const [fields, owner, expectFailure] of [
    [{ campaign_location: "village" }, true, false],
    [{ campaign_location: "village" }, false, true],
    [{ campaign_location: true }, true, true],
    [{ campaign_location: "bad name" }, true, true],
    [{ campaign_location: "village", location: { x: 0, y: 70, z: 0 } }, true, true],
    [{ campaign_location: "village", dimension: "minecraft:overworld" }, true, true],
    [{ campaign_location: "village", type: "condition" }, true, true]
  ]) {
    const candidate = structuredClone(conditionFixture);
    candidate.stages[0].objectives = [{ id: "visit", type: "location_visit", ...fields }];
    delete candidate.stages[0].complete_when;
    delete candidate.stages[0].bonuses;
    if (owner) candidate.metadata.campaign = "villagerretaliation:tool_campaign";
    else delete candidate.metadata.campaign;
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) assert(result.stderr.includes("campaign_location"), "Campaign location diagnostic lost its source field.");
  }
  await writeJson(validQuest, conditionFixture);

  for (const [role, owner, expectFailure] of [["mentor", true, false], ["mentor", false, true], [true, true, true], ["bad name", true, true], [null, true, true]]) {
    const candidate = structuredClone(conditionFixture);
    candidate.provider.campaign_role = role;
    if (owner) candidate.metadata.campaign = "villagerretaliation:tool_campaign";
    else delete candidate.metadata.campaign;
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
    if (expectFailure) assert(result.stderr.includes("campaign_role"), "Campaign character diagnostic lost its source field.");
  }
  await writeJson(validQuest, conditionFixture);

  for (const [type, story] of [
    ["campaign_speak", { role: "mentor", conversation: "villagerretaliation:mentor", node: "welcome" }],
    ["campaign_outcome", { milestone: "dragon", outcome: "defeated", scope: "participated" }],
    ["campaign_investigate", { evidence: "ledger" }],
    ["campaign_follow", { role: "mentor", location: "home", scene: "villagerretaliation:walk", step: "walk" }]
  ]) {
    const variants = [
      [{ story }, true, false],
      [{ story }, false, true],
      [{}, true, true],
      [{ story: null }, true, true],
      [{ type: undefined, story }, true, true],
      [{ type: "missing", story }, true, true],
      [{ story: { ...story, unexpected: true } }, true, true],
      [{ story, count: 1 }, true, true],
      [{ story, item: "minecraft:paper" }, true, true],
      ...Object.keys(story).flatMap((field) => [
        [{ story: Object.fromEntries(Object.entries(story).filter(([key]) => key !== field)) }, true, true],
        [{ story: { ...story, [field]: true } }, true, true],
        [{ story: { ...story, [field]: "has spaces" } }, true, true]
      ])
    ];
    for (const [fields, owner, expectFailure] of variants) {
      const candidate = structuredClone(conditionFixture);
      candidate.stages[0].objectives = [{ id: "story", type, ...fields }];
      delete candidate.stages[0].complete_when;
      delete candidate.stages[0].bonuses;
      if (owner) candidate.metadata.campaign = "villagerretaliation:tool_campaign";
      else delete candidate.metadata.campaign;
      await writeJson(validQuest, candidate);
      const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure });
      if (expectFailure) assert(result.stderr.includes("pointer=/stages/0/objectives/0"), "Story objective diagnostic lost its source pointer.");
    }
    const legacy = path.join(workspace, "legacy-story-objective.json");
    for (const [owner, fields, expectFailure] of [
      [true, { story }, false], [false, { story }, true],
      [true, { story: null }, true], [true, { story, item: "minecraft:paper" }, true],
      [true, { type: undefined, story }, true], [true, { type: "missing", story }, true]
    ]) {
      await writeJson(legacy, { id: "villagerretaliation:legacy_story", ...(owner ? { campaign: "villagerretaliation:tool_campaign" } : {}),
        objectives: [{ id: "story", type, ...fields }] });
      run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", legacy], { expectFailure });
    }
  }
  await writeJson(validQuest, conditionFixture);

  const legacyLocationQuest = path.join(workspace, "legacy-campaign-location.json");
  for (const [owner, expectFailure] of [["villagerretaliation:tool_campaign", false], [undefined, true], [true, true]]) {
    await writeJson(legacyLocationQuest, { id: "villagerretaliation:legacy_location", campaign: owner,
      objectives: [{ id: "visit", type: "location_visit", campaign_location: "village" }] });
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", legacyLocationQuest], { expectFailure });
    if (expectFailure) assert(result.stderr.includes("campaign"), "Legacy location ownership was not validated.");
  }

  const legacyCampaignAction = path.join(workspace, "legacy-campaign-action.json");
  for (const [outcome, expectFailure] of [["spared", false], [null, true]]) {
    const candidate = JSON.parse(await readFile(v1Quest, "utf8"));
    delete candidate.campaign;
    delete candidate.objectives[0].complete_text;
    delete candidate.rules.active;
    candidate.triggers = [{ id: "world_choice", event: "started", actions: [
      { type: "campaign_outcome", milestone: "dragon", outcome, operation_id: "decide" }
    ] }];
    await writeJson(legacyCampaignAction, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", legacyCampaignAction], { expectFailure });
    if (expectFailure) assert(result.stderr.includes("registered action schema"), "Legacy campaign action fields did not use the exported runtime schema.");
  }

  const first = run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree"]);
  const second = run("node", ["tools/migrate-quest-v1-to-v2.mjs", v1Quest, "--no-dialogue-tree"]);
  assert(first.stdout === second.stdout, "Migration output is not deterministic.");
  const migrated = JSON.parse(first.stdout);
  assert(migrated.metadata.campaign === "villagerretaliation:tool_campaign", "Migration dropped campaign ownership.");
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
