import assert from "node:assert/strict";
import { templateLibrary, expandPresentationEntry, expandPresentationDocument, presentationTemplates } from "./dialogue-template-validation.mjs";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContextDirectory } from "./dialogue-context-validation.mjs";
import { markupWarnings } from "./dialogue-markup-validation.mjs";
import { styleIssues, compileStyleDocuments, loadDialogueStyles } from "./dialogue-style-validation.mjs";
import { wordPoolIssues, compileWordPool, wordReferences, validateWordDocuments, validateWordDirectory } from "./dialogue-word-validation.mjs";
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
const performed = { tag: "performed", on_enter: ["look=nearest_participant", "agree", "particle:smoke amount:2",
  "sound:entity.villager.no volume=0.4 pitch=1.2", "pause=50"], on_exit: ["look=player"], text_effects: { shake: { strength: 0.35, speed: 0.8 } } };
assert.deepEqual(styleIssues(performed), []);
for (const fields of [{ on_enter: null }, { on_exit: {} }, { on_enter: [1] }, { restore_pose: 1 },
  { on_enter: ["speed=2"] }, { on_exit: ["<agree>"] }, { on_enter: ["invented=1"] }, { on_enter: ["look=door"] },
  { on_exit: ["sound:entity.villager.yes volume=2"] }, { on_enter: Array(17).fill("agree") }, { on_exit: ["a".repeat(513)] }])
  assert(styleIssues({ tag: "invalid", ...fields }).length, JSON.stringify(fields));
const performedStyles = new Map([["performed", performed]]);
assert.deepEqual(markupWarnings("<performed>A</performed>B", performedStyles), []);
assert(markupWarnings("<performed>" + "<agree>".repeat(58) + "A</performed>", performedStyles).includes("too many dialogue cues"));
assert.deepEqual(markupWarnings("<performed>" + "<agree>".repeat(56) + "A</performed>", performedStyles), []);
assert.deepEqual(styleIssues({ tag: "sticky", on_enter: ["look=player"], restore_pose: false }), []);
assert(compileStyleDocuments(Array.from({ length: 33 }, (_, i) => ["file" + i, { tag: "style_" + i,
  on_enter: Array(16).fill("agree"), on_exit: Array(16).fill("reset") }])).errors.includes("too many dialogue style cues"));
const schema = JSON.parse(await readFile(new URL("./schemas/conversation-v1.schema.json", import.meta.url), "utf8"));
const response = { id: "reply", label: { key: "#reply" }, next: "end", unavailable_behavior: "hide" };
assert.deepEqual(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }), []);
response.unavailable_behavior = "disable";
assert.deepEqual(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }), []);
response.unavailable_behavior = "invented";
assert(validateConversationSchema(response, { $defs: schema.$defs, $ref: "#/$defs/response" }).length);
assert.deepEqual(await validateContextDirectory(fileURLToPath(new URL("../example-packs/dialogue-choices-and-contexts/data", import.meta.url))), []);
const basePool = { role: "opening_clause", values: [{ id: "fallback", text: "Hello" }, { id: "old", text: "Old" }] };
const extendedPool = { extends: "test:base", remove: ["old"], groups: [{ priority: 20, conditions: [{ type: "mood", mood: "angry" }], values: ["Keep it brief", { text: "I will listen", weight: 3 }] }] };
assert.deepEqual(wordPoolIssues(extendedPool), []);
const compiledPool = compileWordPool("test:child", id => id === "test:child" ? extendedPool : basePool);
assert.equal(compiledPool.values.length, 3);
assert.equal(compiledPool.values[2].priority, 20);
assert.equal(compiledPool.role, "opening_clause");
assert.equal(basePool.values.length, 2);
assert.throws(() => compileWordPool("test:child", () => ({ extends: "test:child" })), /cyclic/);
assert.throws(() => compileWordPool("test:child", id => id === "test:child" ? { extends: "test:base", remove: ["unknown"] } : basePool), /unknown inherited/);
assert.deepEqual(wordPoolIssues({ optional: true, chance: 0.5, role: "sentence", groups: [{ conditions: [{ type: "mood", mood: "grateful" }], values: ["You helped."] }] }), []);
for (const invalid of [{ values: [""] }, { optional: true, chance: 2, values: ["Thanks"] }, { values: ["Hi"], role: "invented" }, { values: [{ id: "same", text: "one" }, { id: "same", text: "two" }] }]) assert(wordPoolIssues(invalid).length);
const wordFile = "/pack/data/custom/dialogue_words/en_us/greetings/friendly.json";
const wordLine = "/pack/data/custom/dialogue/fr_fr/lines/greeting.json";
const words = { values: ["happy", "in good spirits"] };
assert.deepEqual(wordPoolIssues(words), []);
assert.deepEqual(wordPoolIssues({ values: ["fine", { text: "splendid", weight: 3,
  conditions: [{ type: "speaking_style", style: "formal" }] }] }), []);
assert.deepEqual(wordPoolIssues({ selection_group: "custom:tone", avoid_recent: 2, values: ["Hello.",
  { text: "Welcome.", priority: 20, variant: "warm", conditions: [{ type: "mood", mood: "content" }] }] }), []);
for (const invalid of [
  { values: ["Hi"], selection_group: "unqualified" },
  { values: ["Hi"], avoid_recent: 17 },
  { values: ["Hi"], avoid_recent: 1.5 },
  { values: [{ text: "Hi", priority: 1.5 }] },
  { values: [{ text: "Hi", priority: 2147483648 }] },
  { values: ["Hi", { text: "Welcome", variant: "warm" }] },
  { selection_group: "custom:tone", values: [{ text: "Welcome", variant: "warm" }] },
  { selection_group: "custom:tone", values: ["Hi", { text: "Welcome", variant: "Bad Tone" }] }
]) assert(wordPoolIssues(invalid).length, JSON.stringify(invalid));
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


const templateDefinitions = {
  "test:styled": { parameters: ["body"], variants: [{ id: "ordinary", text: "{body}" },
    { id: "quiet", priority: 20, conditions: [{ type: "mood", mood: "stressed" }], text: "<gray>{body}</gray>" }] },
  "test:greeting": { extends: "test:styled", parameters: ["greeting"], arguments: { body: "Hello, {greeting}" } }
};
const presentations = templateLibrary(id => templateDefinitions[id]);
const presentationSource = { id: "farmer", template: "test:greeting", arguments: { greeting: "need a hand?" } };
const presentationBaseline = structuredClone(presentationSource);
const presentation = expandPresentationEntry(presentationSource, presentations);
assert.equal(presentation.variants[0].text, "Hello, need a hand?");
assert.equal(presentation.variants[1].text, "<gray>Hello, need a hand?</gray>");
assert.deepEqual(presentation.variants[1].conditions, templateDefinitions["test:styled"].variants[1].conditions);
assert.deepEqual(presentationSource, presentationBaseline);
for (const source of [
  { template: "test:missing" },
  { template: "test:greeting" },
  { template: "test:greeting", arguments: { greeting: "hello", unknown: "x" } },
  { template: "test:greeting", arguments: { greeting: "{body}" } },
  { template: "test:greeting", arguments: { greeting: "hello" }, text: "override" }
]) assert.throws(() => expandPresentationEntry(source, presentations));
assert.throws(() => templateLibrary(() => ({ extends: "test:cycle" }))("test:cycle"));
assert.equal(expandPresentationDocument(null, presentations), null);
assert.throws(() => expandPresentationDocument({ messages: [{ arguments: {} }] }, presentations));
const bounded = templateLibrary(() => ({ parameters: ["body"], text: "{body}{body}" }));
assert.throws(() => expandPresentationEntry({ template: "test:bounded", arguments: { body: "x".repeat(16384) } }, bounded), /too long/);
const localizedTemplates = presentationTemplates([
  ["/pack/data/test/dialogue_templates/en_us/hello.json", { text: "Hello." }],
  ["/pack/data/test/dialogue_templates/fr_fr/hello.json", { text: "Bonjour." }],
  ["/pack/data/test/world_campaigns/story/dialogue_templates/en_us/hello.json", { text: "Welcome." }]
]);
assert.deepEqual(localizedTemplates.errors, []);
assert.equal(expandPresentationEntry({ template: "test:hello" },
  localizedTemplates.lookup("/pack/data/test/world_campaigns/story/dialogue/fr_fr/messages.json")).text, "Bonjour.");
assert.equal(expandPresentationEntry({ template: "test:hello" },
  localizedTemplates.lookup("/pack/data/test/world_campaigns/story/dialogue/en_us/messages.json")).text, "Welcome.");

const guardedBase = { role: "sentence", selection_group: "test:tone", avoid_recent: 2, values: [{ id: "plain", text: "Hello." }],
  groups: [{ conditions: [{ type: "mood", mood: "stressed", min_intensity: 25 }], priority: 30, variant: "quiet",
    values: [{ id: "stressed", text: "Tired.", weight: 3 }] }] };
const wordingChild = { extends: "test:base", text_overrides: { stressed: "A bit tired." } };
const wordingPool = compileWordPool("test:child", id => id === "test:child" ? wordingChild : guardedBase);
const originalStressed = compileWordPool("test:base", () => guardedBase).values.find(entry => entry.id === "stressed");
assert.deepEqual(wordingPool.values.find(entry => entry.id === "stressed"), { ...originalStressed, text: "A bit tired." });
const legacyReplacement = compileWordPool("test:child", id => id === "test:child"
  ? { extends: "test:base", values: [{ id: "stressed", text: "Replacement." }] } : guardedBase);
assert.equal(legacyReplacement.values.find(entry => entry.id === "stressed").conditions, undefined);
for (const child of [
  { extends: "test:base", text_overrides: { missing: "No." } },
  { extends: "test:base", remove: ["stressed"], text_overrides: { stressed: "No." } },
  { extends: "test:base", text_overrides: { plain: "" } }
]) assert.throws(() => compileWordPool("test:child", id => id === "test:child" ? child : guardedBase));
for (const invalid of [
  { values: ["Hello"], text_overrides: { plain: "No." } },
  { extends: "test:base", text_overrides: [] },
  { extends: "test:base", text_overrides: { stressed: "{word:test:bad}" } },
  { extends: "test:base", values: [{ id: "plain", text: "Full." }], text_overrides: { plain: "Text." } }
]) assert(wordPoolIssues(invalid).length);

const farmerPool = JSON.parse(await readFile(new URL("../neoforge/src/main/resources/data/villagerretaliation/dialogue_words/en_us/greetings/professions/farmer/wellbeing.json", import.meta.url), "utf8"));
assert.deepEqual(wordPoolIssues(farmerPool), []);
assert.equal(farmerPool.extends, "villagerretaliation:greetings/npc/wellbeing");
assert.equal(typeof farmerPool.text_overrides.content_1, "string");
for (const purpose of ["greetings/contextual", "address/player", "farewells/contextual", "thanks/help", "responses/accept_help", "responses/refuse_request"]) {
  const definition = JSON.parse(await readFile(new URL(`../neoforge/src/main/resources/data/villagerretaliation/dialogue_words/en_us/${purpose}.json`, import.meta.url), "utf8"));
  assert.deepEqual(wordPoolIssues(definition), []);
  assert.equal(definition.avoid_recent, 2);
}
assert.deepEqual(await validateWordDirectory(fileURLToPath(new URL("../example-packs/dialogue-choices-and-contexts/data", import.meta.url))), []);
console.log("Dialogue authoring checks passed: choice policy, markup, contexts, word pools, and inherited profession pools.");
