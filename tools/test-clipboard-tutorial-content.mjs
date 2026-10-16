import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const directory = "neoforge/src/main/resources/data/villagerretaliation/clipboard_tutorials";
const roles = ["farming", "fishing", "mining", "logging", "hunting", "animal_handling", "brewing",
  "craftsman", "cook", "smelter", "courier", "nitwit", "combat", "builder"];
let sequences = 0;
let cards = 0;
const lessons = new Map();
for (const role of roles) {
  const lesson = JSON.parse(fs.readFileSync(path.join(directory, `${role}.json`), "utf8"));
  lessons.set(role, lesson);
  assert.equal(lesson.id, `villagerretaliation:clipboard_${role}`);
  assert.equal(lesson.role, role);
  assert.equal(lesson.source.version, 1);
  assert.equal(lesson.source.sha256, "54954eef6850d1693d6ef07cace9d14600f62a5eec694413ff4b2804b5ee106f");
  assert.equal(lesson.credit_policy, "personal");
  assert.equal(lesson.outcome_policy, "committed_since_binding");
  assert.equal(lesson.setup_policy, "recognize_existing");
  assert.equal(lesson.completion_offer,role==="builder"?"one_off_project_or_stop":"normal_contract_or_stop");
  assert.equal(lesson.release_status, "integrated");
  assert(lesson.checkpoints && Object.keys(lesson.checkpoints).length > 0);
  const steps = new Set();
  for (const step of lesson.steps) {
    assert(!steps.has(step.id)); steps.add(step.id);
    assert(step.design_notes && step.title);
    for (const sequence of step.sequences) {
      sequences++;
      assert(sequence.recovery && sequence.confirmation_guidance);
      assert.equal(sequence.repeat_label, "Show me again");
      assert(sequence.cards.length > 0);
      const ids = new Set();
      for (const card of sequence.cards) {
        cards++;
        assert(card.text && !ids.has(card.id)); ids.add(card.id);
        assert(!/\{(?!Inventory\}|Use\}|Attack\}|Sneak\}|ControlModifier\}|ShiftModifier\}|AltModifier\})/.test(card.text));
      }
    }
  }
}
const shared = JSON.parse(fs.readFileSync(path.join(directory, "shared.json"), "utf8"));
assert.equal(shared.sequence.id, "supplies_deposit_readiness");
sequences++;
cards += shared.sequence.cards.length;
assert.equal(sequences, 84);
const allCards = [...lessons.values()].flatMap(l => l.steps.flatMap(s => s.sequences.flatMap(q => q.cards.map(c => c.text))));
assert(allCards.some(text => /Assign Clipboard Storage/.test(text)), "Storage commit menu must be taught");
assert(allCards.some(text => /right-click the Clipboard slot/.test(text)), "Physical inventory right-click must remain literal");
assert(allCards.some(text => /\{ControlModifier\}/.test(text)), "Hardcoded modifier hint must be platform-specific");
const fishing = JSON.stringify(lessons.get("fishing"));
assert(/fresh|again|redraw/i.test(fishing), "Persistent area clears the Fishing task draft");
const builder = JSON.stringify(lessons.get("builder"));
assert(builder.includes("minecraft:village/plains/houses/plains_accessory_1"));
assert(/runtime|scanned|manifest/i.test(builder));
assert(/one-off/.test(builder));
for (const role of roles) {
  const folder=`neoforge/src/main/resources/data/villagerretaliation/quests/clipboard/clipboard_${role}`;
  const quest=JSON.parse(fs.readFileSync(`${folder}/quest.json`,"utf8"));
  const locale=JSON.parse(fs.readFileSync(`${folder}/locales/en_us.json`,"utf8"));
  assert.equal(quest.id,lessons.get(role).id);
  assert(quest.metadata.tags.includes("group.clipboard"));
  assert.equal(quest.availability.completion_scope,"player");
  assert.equal(quest.availability.max_completions,1);
  assert(!quest.rewards,"Do not invent extra loot or XP for working lessons");
  assert(quest.stages.some(stage=>stage.id==="choose_next"));
  const authIndex=quest.stages.findIndex(stage=>stage.id==="authorize_remaining");
  if(authIndex>=0) {
    const firstOutcome=quest.stages.findIndex(stage=>lessons.get(role).checkpoints[stage.id]?.some(check=>["outcome","completed"].includes(check.check)));
    assert(firstOutcome<0||authIndex<firstOutcome,`${role}: authorize finite work before waiting for its outcome`);
  }
  assert(Object.keys(locale).length>0);
  for(const sequence of lessons.get(role).steps.flatMap(step=>step.sequences)) {
    assert(quest.stages.some(stage=>stage.id===sequence.id),`${role}/${sequence.id} must be playable`);
    assert(lessons.get(role).checkpoints[sequence.id]?.length,`${role}/${sequence.id} needs a server checkpoint`);
  }
}
console.log(`Clipboard tutorials: 14 playable personal quests, ${sequences} teaching sequences, ${cards} source cards and server checkpoints checked`);

const craftsman = lessons.get("craftsman");
for (const [step, parameter] of [["step_4_sequence_1", "filter_excluded"], ["filter_observe", "filter_observed"], ["filter_adjust", "filter_corrected"]]) {
  assert(craftsman.checkpoints[step].some(check => check.check === "selection" && check.parameter === parameter));
}
const craftText = JSON.stringify(craftsman.steps[3].sequences);
assert(craftText.includes("Assign configured filter"));
assert(craftText.includes("Other outputs") || craftText.includes("other outputs"));
assert(craftText.includes("main hand") && craftText.includes("{Use}") && craftText.includes("{Sneak}"));
const craftQuest = JSON.parse(fs.readFileSync("neoforge/src/main/resources/data/villagerretaliation/quests/clipboard/clipboard_craftsman/quest.json", "utf8"));
const stageIndex = id => craftQuest.stages.findIndex(stage => stage.id === id);
assert(stageIndex("step_4_sequence_1") < stageIndex("filter_observe"));
assert(stageIndex("filter_observe") < stageIndex("filter_adjust"));
assert(stageIndex("filter_adjust") < stageIndex("step_5_sequence_1"));
console.log("Craftsman filter exercise: committed assignment, observation, correction and ordering checked");
