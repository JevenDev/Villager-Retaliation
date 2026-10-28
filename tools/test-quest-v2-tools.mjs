import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const workspace = await mkdtemp(path.join(tmpdir(), "vr-quest-v2-tools-"));

try {
  const validRoot = path.join(workspace, "valid");
  const invalidRoot = path.join(workspace, "invalid");
  const validQuest = path.join(validRoot, "quest.json");
  const invalidQuest = path.join(invalidRoot, "quest.json");
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


  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);

  const referenceFixture = JSON.parse(await readFile(validQuest, "utf8"));
  const referenceAvailability = referenceFixture.availability;
  referenceFixture.availability = { conditions: [{ type: "quest_fact", scope: "quest", quest: "villagerretaliation:last_light", key: "outcome", value: "on_time" }] };
  await writeJson(validQuest, referenceFixture);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  referenceFixture.availability.conditions[0].quest = "test:missing_reference";
  await writeJson(validQuest, referenceFixture);
  const missingReference = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
  assert(missingReference.stderr.includes('references missing quest id "test:missing_reference"'), "Missing external quests still fail reference validation.");
  referenceFixture.availability = referenceAvailability;
  await writeJson(validQuest, referenceFixture);

  const conditionFixture = JSON.parse(await readFile(validQuest, "utf8"));
  const discovered = structuredClone(conditionFixture);
  discovered.provider.type = "villagerretaliation:discovery";
  discovered.discovery = [{ id: "letter", event: "book_opened", mode: "automatic", match: { book: "test:letter" } }];
  discovered.stages[0].followers = [{ actor: { alias: "companion", type: "villagerretaliation:living_entity", binding_source: "entity_tag", binding: "escort_dog" }, protected: true }];
  await writeJson(validQuest, discovered);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  const adaptive = structuredClone(discovered);
  adaptive.stages[0].objectives[0] = { id: adaptive.stages[0].objectives[0].id, type: "follower_arrival",
    match: { actor: "companion", destination_tag: "waiting_villager" }, radius: 3 };
  await writeJson(validQuest, adaptive);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  for (const invalid of [
    { ...adaptive, stages: [{ ...adaptive.stages[0], objectives: [{ ...adaptive.stages[0].objectives[0], location: { x: 1, y: 64, z: 0 } }] }] },
    { ...discovered, stages: [{ ...discovered.stages[0], followers: [{ actor: { alias: "companion", type: "villagerretaliation:living_entity", binding_source: "entity_tag" } }] }] },
    { ...discovered, discovery: [{ id: "letter", event: "book_opened" }] },
    { ...discovered, discovery: [{ id: "letter", event: "not_an_event", match: { book: "test:letter" } }] },
    { ...discovered, stages: [{ ...discovered.stages[0], followers: [{ command: "teleport", actor: discovered.stages[0].followers[0].actor }] }] }
  ]) {
    await writeJson(validQuest, invalid);
    run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
  }
  await writeJson(validQuest, conditionFixture);
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
  const parallel = structuredClone(conditionFixture);
  parallel.entry_stage = "fork";
  parallel.stages = [
    { id: "fork", objectives: [], parallel: ["left", "right"], next: "join" },
    { id: "left", objectives: [{ id: "wait", type: "condition", timer: { mode: "duration", duration_ticks: 40 } }], next: "join" },
    { id: "right", objectives: [], timer: { duration_ticks: 80, timeout_stage: "join", warning_ticks: 20 }, next: "join" },
    { id: "join", objectives: [] }
  ];
  await writeJson(validQuest, parallel);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  for (const mutate of [
    value => { value.stages[0].parallel = ["left", "left"]; },
    value => { value.stages[0].parallel = ["left", "missing"]; },
    value => { delete value.stages[1].next; },
    value => { value.stages[2].timer.warning_ticks = 80; },
    value => { value.stages[1].objectives[0].timer.timeout_stage = "join"; }
  ]) {
    const invalid = structuredClone(parallel);
    mutate(invalid);
    await writeJson(validQuest, invalid);
    run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
  }
  const timed = structuredClone(conditionFixture);
  timed.stages[0].responses = [{ id: "wait", label: "Wait", complete: true }];
  timed.stages[0].response_timer = { duration_ticks: 40, response: "wait" };
  await writeJson(validQuest, timed);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  timed.stages[0].scenes = [{ id: "silence", lines: ["No answer."], end: true }];
  timed.stages[0].response_timer = { duration_ticks: 40, next: "silence" };
  await writeJson(validQuest, timed);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
  timed.stages[0].response_timer.next = "missing";
  await writeJson(validQuest, timed);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
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
  }
  for (const schema of [undefined, null, "", "villagerretaliation:quest/v1"]) {
    const candidate = structuredClone(conditionFixture);
    if (schema === undefined) delete candidate.schema;
    else candidate.schema = schema;
    await writeJson(validQuest, candidate);
    const result = run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest], { expectFailure: true });
    assert(result.stderr.includes("pointer=/schema"), "Unsupported quest schema diagnostic lost its source pointer.");
  }
  const entityScopes = structuredClone(conditionFixture);
  entityScopes.availability.conditions = [
    { type: "memory", tags: ["test:greeting"], source: "this_entity" },
    { type: "quest_fact", scope: "entity", tags: ["test:known"] }
  ];
  entityScopes.stages[0].on_enter = [
    { type: "set_tag", scope: "entity", tag: "test:known" },
    { type: "set_variable", scope: "group", key: "greeting", value: "hello" }
  ];
  await writeJson(validQuest, entityScopes);
  run("node", ["tools/validate-dialogue-data.mjs", "--quiet", "--quest", validQuest]);
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
