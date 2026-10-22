import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContextDirectory } from "./dialogue-context-validation.mjs";
import { markupWarnings } from "./dialogue-markup-validation.mjs";
import { wordPoolIssues, wordReferences, validateWordDocuments, validateWordDirectory } from "./dialogue-word-validation.mjs";
import { validateConversationSchema } from "./conversation-validation.mjs";

const cases = JSON.parse(await readFile(new URL("../neoforge/src/gameTest/resources/dialogue_markup/cases.json", import.meta.url), "utf8"));
for (const entry of cases) assert.equal(markupWarnings(entry.text).length > 0, entry.warning, entry.text);
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
