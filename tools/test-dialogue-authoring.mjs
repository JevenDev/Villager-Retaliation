import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContextDirectory } from "./dialogue-context-validation.mjs";
import { markupWarnings } from "./dialogue-markup-validation.mjs";
import { styleIssues, compileStyleDocuments, loadDialogueStyles } from "./dialogue-style-validation.mjs";
import { wordPoolIssues, wordReferences, validateWordDocuments, validateWordDirectory } from "./dialogue-word-validation.mjs";
import { validateConversationSchema } from "./conversation-validation.mjs";

const cases = JSON.parse(await readFile(new URL("../neoforge/src/gameTest/resources/dialogue_markup/cases.json", import.meta.url), "utf8"));
for (const entry of cases) assert.equal(markupWarnings(entry.text).length > 0, entry.warning, entry.text);
const nervous = { tag: "nervous", text_effects: { shake: true }, speed: 0.8 };
assert.deepEqual(styleIssues(nervous), []);
for (const field of ["wavy", "shake", "pulse", "jump"]) {
  assert.deepEqual(styleIssues({ tag: "tuned", text_effects: { [field]: { strength: 0.35, speed: 0.8 } } }), []);
  assert.deepEqual(styleIssues({ tag: "tuned", text_effects: { [field]: { strength: 0, speed: 0 } } }), []);
  for (const setting of [{ strength: -1 }, { strength: 4.1 }, { speed: 10.1 }, { speed: "1" }, { extra: 1 }])
    assert(styleIssues({ tag: "tuned", text_effects: { [field]: setting } }).length);
}
assert.deepEqual(styleIssues({ tag: "calm", text_effects: { shake: false, color: null, gradient_start: null, gradient_end: null } }), []);
for (const invalid of [{}, { tag: "shake" }, { tag: "pause" }, { tag: "abcdef" }, { tag: "Nervous" },
  { tag: "nervous", extra: true }, { tag: "nervous", speed: 0 }, { tag: "nervous", speed: "1" },
  { tag: "nervous", text_effects: { shake: 1 } }, { tag: "nervous", text_effects: { colour: "red" } },
  { tag: "nervous", text_effects: { color: "invented" } }, { tag: "nervous", text_effects: { gradient_start: "red" } }])
  assert(styleIssues(invalid).length, JSON.stringify(invalid));
assert(compileStyleDocuments([["a", nervous], ["b", nervous]]).errors.length);
const styles = (await loadDialogueStyles()).styles;
assert(styles.has("nervous"));
assert(markupWarnings("<nervous>" + "<agree>".repeat(64) + "A</nervous>", styles).length);
assert.deepEqual(markupWarnings("<nervous>Please <b>listen</b>.</nervous>", styles), []);
assert(markupWarnings("<nervous>Please listen", styles).length);
assert(markupWarnings("<nervous><b>Listen</nervous></b>", styles).length);
assert.deepEqual((await loadDialogueStyles()).errors, []);
const exampleStyles = await loadDialogueStyles(fileURLToPath(new URL("../example-packs/dialogue-choices-and-contexts/data", import.meta.url)));
assert.deepEqual(exampleStyles.errors, []);
assert(exampleStyles.styles.has("uneasy"));
assert.deepEqual(markupWarnings("<uneasy>Did you hear that?</uneasy>", exampleStyles.styles), []);
const schema = JSON.parse(await readFile(new URL("./schemas/conversation-v1.schema.json", import.meta.url), "utf8"));
const response = { id: "reply", label: { key: "#reply" }, next: "end", unavailable_behavior: "hide" };
assert.deepEqual(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }), []);
response.unavailable_behavior = "disable";
assert.deepEqual(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }), []);
response.unavailable_behavior = "invented";
assert(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }).length);
assert.deepEqual(await validateContextDirectory(fileURLToPath(new URL("../example-packs/dialogue-choices-and-contexts/data", import.meta.url))), []);
const wordFile = "/pack/data/custom/dialogue_words/en_us/greetings/friendly.json";
const wordLine = "/pack/data/custom/dialogue/fr_fr/lines/greeting.json";
const words = { values: ["happy", "in good spirits"] };
assert.deepEqual(wordPoolIssues(words), []);
assert.deepEqual(wordPoolIssues({ values: ["fine", { text: "splendid", weight: 3,
  conditions: [{ type: "speaking_style", style: "formal" }] }] }), []);
for (const entry of [{ text: "fine", weight: 0 }, { text: "fine", weight: 1.5 }, { text: "fine", extra: true },
  { text: "fine", conditions: [{ type: "speaking_style", style: "unknown" }] }, { text: "fine", conditions: null }])
  assert(wordPoolIssues({ values: ["fallback", entry] }).length);
assert(wordPoolIssues({ values: [{ text: "secret", conditions: [{ type: "speaking_style", style: "formal" }] }] }).length);
for (const invalid of [{}, { values: [] }, { values: [" "] }, { values: [1] }, { values: [null] },
  { values: ["{player}"] }, { values: ["<aqua>happy</aqua>"] }, { values: ["bad\nword"] }])
  assert(wordPoolIssues(invalid).length > 0, JSON.stringify(invalid));
assert.deepEqual(wordReferences("{player} {word:custom:greetings/friendly}"),
  [{ token: "{word:custom:greetings/friendly}", id: "custom:greetings/friendly" }]);
const wordText = { text: "{word:custom:greetings/friendly}, {player}." };
assert.deepEqual(validateWordDocuments([[wordFile, words], [wordLine, wordText]]), []);
assert(validateWordDocuments([[wordLine, wordText]])[0].includes("missing word pool"));
for (const malformed of ["{word}", "{word:}", "{word:custom:greetings/friendly", "{word:Bad:pool}", "{word:custom:pool:extra}"])
  assert(validateWordDocuments([[wordLine, { text: malformed }]])[0].includes("malformed"), malformed);
assert.deepEqual(await validateWordDirectory(), []);
const farmer = JSON.parse(await readFile(new URL("../neoforge/src/main/resources/data/villagerretaliation/dialogue/en_us/professions/farmer/lines/02_greetings.json", import.meta.url), "utf8"));
const pilot = farmer.lines.flatMap(line => line.variants ?? []).filter(line => line.id.startsWith("paired_greet.wellbeing.") && line.id.endsWith(".content"));
assert.equal(pilot.length, 4);
for (const line of pilot) {
  assert(line.text.includes("{word:villagerretaliation:mood/content}"));
  assert(line.conditions.some(condition => condition.type === "mood" && condition.mood === "content"));
  assert(line.conditions.some(condition => condition.type === "villager_age" && condition.adult === true));
  assert(line.conditions.some(condition => condition.type === "reputation"));
}
assert(pilot.find(line => line.id.includes("trusted")).text.includes("<aqua>{word:villagerretaliation:mood/content}</aqua>"));
console.log("Dialogue authoring checks passed: choice policy, markup, contexts, word pools, and farmer pilot.");
