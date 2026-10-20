import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { validateContextDirectory } from "./dialogue-context-validation.mjs";
import { markupWarnings } from "./dialogue-markup-validation.mjs";
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
console.log("Dialogue authoring checks passed: choice policy, markup diagnostics, and example context references.");
