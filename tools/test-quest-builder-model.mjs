import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const model = require("./quest-builder/quest-model.js");

const registryMetadata = {
  registries: {
    objectives: Object.keys(model.OBJECTIVE_LABELS).map((id) => ({ id, aliases: [] })),
    actions: [
      { id: "quest", aliases: ["quest_action"] },
      { id: "quest_transition", aliases: ["branch_transition"] }
    ],
    conditions: [{ id: "reputation", aliases: [] }],
    triggers: [{ id: "started", aliases: ["start"] }]
  }
};

function codes(issues) {
  return issues.map((issue) => issue.code);
}

function testLinearTemplate() {
  const quest = model.createLinearQuest("example_pack");
  assert.equal(quest.schema, model.SCHEMA_ID);
  assert.equal(quest.id, "example_pack:a_helping_hand");
  assert.equal(quest.entry_stage, "gather_supplies");
  assert.equal(model.questFilePath(quest), "data/example_pack/quests/a_helping_hand.json");
  assert.deepEqual(model.validateQuest(quest, registryMetadata), []);
}

function testBranchingTemplate() {
  const quest = model.createBranchingQuest("story_pack");
  const edges = model.collectEdges(quest);
  assert(edges.some((edge) => edge.from === "choose_a_path" && edge.to === "gather_supplies"));
  assert(edges.some((edge) => edge.from === "choose_a_path" && edge.to === "defend_the_road"));
  assert.equal(model.reachableStages(quest).size, quest.stages.length);
  assert.deepEqual(model.validateQuest(quest, registryMetadata), []);
}

function testRenameReferences() {
  const quest = model.createBranchingQuest();
  model.renameStage(quest, "gather_supplies", "gather_food");
  const edges = model.collectEdges(quest);
  assert(quest.stages.some((stage) => stage.id === "gather_food"));
  assert(!quest.stages.some((stage) => stage.id === "gather_supplies"));
  assert(edges.some((edge) => edge.to === "gather_food"));
  assert(!edges.some((edge) => edge.to === "gather_supplies"));
}

function testRemoveReferences() {
  const quest = model.createBranchingQuest();
  model.removeStage(quest, "defend_the_road");
  assert(!quest.stages.some((stage) => stage.id === "defend_the_road"));
  assert(!model.collectEdges(quest).some((edge) => edge.to === "defend_the_road"));
}

function testActionableValidation() {
  const quest = model.createLinearQuest();
  quest.id = "Not Namespaced";
  quest.entry_stage = "missing";
  quest.stages[0].objectives[0].item = "Bread";
  quest.stages[0].objectives[0].count = 0;
  quest.stages[0].next = "nowhere";
  quest.stages.push({ id: quest.stages[0].id, objectives: [] });
  const issues = model.validateQuest(quest, registryMetadata);
  const issueCodes = codes(issues);
  for (const expected of ["quest.id", "entry.unknown", "stage.duplicate", "objective.target.invalid", "objective.count", "transition.unknown"]) {
    assert(issueCodes.includes(expected), `Missing validation code ${expected}`);
  }
  assert(issues.every((issue) => issue.path && issue.message && issue.hint));
  assert(issues.every((issue, index) => index === 0 || ({ error: 0, warning: 1, info: 2 }[issues[index - 1].severity] <= { error: 0, warning: 1, info: 2 }[issue.severity])));
}

function testProjectRecovery() {
  const project = model.normalizeProject({ name: "Recovered", quests: [] });
  assert.equal(project.name, "Recovered");
  assert.equal(project.quests.length, 1);
  assert.equal(project.selectedQuestId, project.quests[0].id);
}

function testDuplicateProjectPaths() {
  const first = model.createLinearQuest("same_pack");
  const second = model.createLinearQuest("same_pack");
  const issues = model.validateProject({ quests: [first, second] }, registryMetadata);
  assert(codes(issues).includes("project.path.duplicate"));
  assert.equal(issues.find((issue) => issue.code === "project.path.duplicate").questIndex, 1);
}

function testFailureAndPrerequisiteContracts() {
  const quest = model.createLinearQuest("contract_pack");
  quest.availability.prerequisites = ["contract_pack:first", "contract_pack:second", "contract_pack:third"];
  quest.stages[0].dialogue = { reminder: { lines: ["Continue?"], responses: [{ id: "fail", label: "Give up", fail: true }] } };
  assert.deepEqual(model.validateQuest(quest, registryMetadata), []);
  quest.availability.prerequisites.push("Bad prerequisite");
  quest.stages[0].dialogue.reminder.responses[0].abandon = true;
  const issueCodes = codes(model.validateQuest(quest, registryMetadata));
  assert(issueCodes.includes("prerequisite.invalid"));
  assert(issueCodes.includes("response.terminal.conflict"));
}

testLinearTemplate();
testBranchingTemplate();
testRenameReferences();
testRemoveReferences();
testActionableValidation();
testProjectRecovery();
testDuplicateProjectPaths();
testFailureAndPrerequisiteContracts();

console.log("Quest builder model tests passed.");
