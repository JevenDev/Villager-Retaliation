import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { compileWordPool } from "./dialogue-word-validation.mjs";
import { presentationTemplates, expandPresentationDocument } from "./dialogue-template-validation.mjs";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const data = path.join(root, "neoforge/src/main/resources/data/villagerretaliation");
const raw = file => JSON.parse(readFileSync(file, "utf8"));
const read = file => file.replaceAll("\\", "/").includes("/dialogue/")
  ? expandPresentationDocument(raw(file), templates.lookup(file)) : raw(file);
function files(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap(entry => entry.isDirectory() ? files(path.join(directory, entry.name))
      : entry.name.endsWith(".json") ? [path.join(directory, entry.name)] : []);
}

const templates = presentationTemplates(files(path.join(data, "dialogue_templates")).map(file => [file, raw(file)]));
assert.deepEqual(templates.errors, []);
const owners = new Map();
let greetingCount = 0;
let openingCount = 0;
for (const file of files(path.join(data, "dialogue/en_us"))) {
  const relative = path.relative(path.join(data, "dialogue/en_us"), file).replaceAll("\\", "/");
  const document = read(file);
  for (const section of ["options", "lines", "openings"]) {
    const entries = document[section] ?? [];
    if (relative.includes("/greetings/")) {
      assert(entries.every(entry => section === "openings" || entry.request === "greeting"),
        relative + ": greeting resources contain only their own dialogue");
    }
    for (const entry of entries) {
      if (section === "lines") {
        assert(!owners.has(entry.id), "duplicate dialogue owner: " + entry.id);
        owners.set(entry.id, entry);
      }
      if (section !== "openings" && entry.request !== "greeting") continue;
      if (section === "openings") openingCount++;
      if (section === "lines") greetingCount++;
      assert(relative.startsWith("global/" + section + "/greetings/")
        || section !== "options" && new RegExp("^professions/[^/]+/" + section + "/greetings/[^/]+\\.json$").test(relative),
        relative + ": greeting is outside its dedicated resource");
    }
  }
}

const vanillaProfessions = [
  "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
  "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "unemployed", "weaponsmith"
];
const professionRoot = path.join(data, "dialogue/en_us/professions");
assert.deepEqual(readdirSync(professionRoot).sort(), vanillaProfessions, "all vanilla profession states are represented");
for (const profession of vanillaProfessions) {
  for (const section of ["lines", "openings"]) {
    const document = read(path.join(professionRoot, profession, section, "greetings/ordinary.json"));
    assert(document[section]?.length > 0, profession + ": missing " + section + " greeting content");
    assert(document.metadata.tags.includes("scope.profession." + profession), profession + ": profession metadata is retained");
  }
}

const pairIds = new Set();
let pairedCount = 0;
for (const file of files(path.join(data, "paired_greetings/en_us"))) {
  const document = read(file);
  for (const source of [...(document.metadata?.owners ?? []), ...(document.metadata?.assessed_owners ?? [])]) {
    assert(existsSync(path.join(data, source.source_resource)), file + ": missing NPC source resource");
  }
  for (const pair of document.pairs) {
    assert(!pairIds.has(pair.id), "duplicate greeting pair: " + pair.id);
    pairIds.add(pair.id);
    const owner = owners.get(pair.owner_id);
    assert(owner, pair.id + ": missing dialogue owner");
    const variants = owner.variants ?? owner.lines.map((text, index, lines) => ({
      id: lines.length === 1 ? owner.id : "line_" + index, text
    }));
    const variant = variants.find(value => (value.id === owner.id || value.id.startsWith(owner.id + "#")
      ? value.id : owner.id + "#" + value.id) === pair.npc_variant_id);
    assert(variant, pair.id + ": missing NPC variant");
    assert.equal(variant.text.replace(/<[^>]+>/g, ""), pair.npc_text.replace(/<[^>]+>/g, ""),
      pair.id + ": NPC text guard no longer matches its source");
    if ((pair.option_id ?? "adult_greeting") === "adult_greeting") pairedCount++;
    assert.equal(pair.option_id, "adult_question", pair.id + ": obsolete greeting pairs have been retired");
  }
}

let childPairs = 0;
for (const file of files(path.join(data, "paired_dialogue/en_us"))) {
  const relative = path.relative(path.join(data, "paired_dialogue/en_us"), file).replaceAll("\\", "/");
  for (const pair of read(file).pairs) {
    const greeting = pair.request === "greeting";
    if (relative.startsWith("greetings/")) assert(greeting, pair.id + ": unrelated paired dialogue");
    if (!greeting) continue;
    childPairs++;
    assert.equal(pair.option, "baby_greeting", pair.id);
    assert(relative.startsWith("greetings/"), pair.id + ": greeting remains in a mixed paired resource");
    for (const id of pair.owners) assert.equal(owners.get(id)?.request, "greeting", pair.id + ": missing greeting owner");
  }
}

assert(greetingCount > 0 && openingCount > 0 && pairedCount === 0 && childPairs > 0);
assert.equal(pairIds.size, 240, "existing reputation question pairs are retained");
console.log("Greeting content checks passed: " + greetingCount + " reply pools, " + openingCount
  + " openings, " + pairedCount + " adult pair bindings, and " + childPairs + " child exchanges.");

const sharedTree = read(path.join(data, "dialogue_trees/en_us/greetings/shared.json"));
const sharedMessages = read(path.join(data, "dialogue/en_us/global/messages/greetings/shared.json")).messages;
const messageKeys = new Set(sharedMessages.map(message => message.key));
assert.equal(sharedTree.entries.length, 1);
assert(sharedTree.entries[0].line_ids.includes("greeting.shared.ordinary"));
assert.equal(owners.get("greeting.shared.ordinary")?.request, "greeting");
assert(!owners.has("global_new_greeting_bell") && !owners.has("greeting.speaking_style"),
  "old shared ordinary greeting owners have been replaced");
const reachable = new Set();
const pending = [sharedTree.entries[0].start];
while (pending.length) {
  const id = pending.shift();
  if (reachable.has(id)) continue;
  reachable.add(id);
  const node = sharedTree.nodes[id];
  assert(node, "missing shared greeting node: " + id);
  assert.equal(node.actions?.length ?? 0, 0, id + ": greeting replies do not grant rewards");
  for (const exchange of node.exchanges ?? []) {
    if (typeof exchange.text === "object") assert(messageKeys.has(exchange.text.key), id + ": missing shared message");
  }
  if (node.end) {
    assert.equal(node.responses?.length ?? 0, 0, id + ": terminal greeting has no hidden choices");
    continue;
  }
  assert(node.responses?.length > 0, id + ": nonterminal greeting needs responses");
  for (const response of node.responses) {
    assert.equal(response.exchanges.length, 1, response.id);
    assert.equal(response.exchanges[0].speaker.type, "player", response.id);
    assert.deepEqual(response.exchanges[0].text, { key: response.label_key }, response.id + ": labels and spoken replies use the same key");
    const fallback = sharedMessages.find(message => message.key === response.label_key);
    assert.equal(fallback?.text, response.label, response.id + ": unknown professions keep the shared player wording");
    assert.equal(response.actions?.length ?? 0, 0, response.id + ": greeting choices do not grant rewards");
    pending.push(response.next);
  }
}
assert.equal(reachable.size, Object.keys(sharedTree.nodes).length, "every shared greeting branch is reachable");
assert.equal(sharedTree.nodes.hello.responses.length, 3);
assert.equal(sharedTree.nodes.wellbeing.responses.length, 4);
console.log("Shared greeting checks passed: one bound entry, ordinary and wellbeing choices, spoken responses, and terminal branches.");

const poolLookup = id => read(path.join(data, "dialogue_words/en_us", id.split(":")[1] + ".json"));
const sharedWellbeing = compileWordPool("villagerretaliation:greetings/npc/wellbeing", poolLookup);
const salutationId = "villagerretaliation:greetings/professions/salutations";
const salutations = compileWordPool(salutationId, poolLookup);
assert.equal(salutations.role, "opening_clause", "profession salutations leave punctuation to their templates");
assert.equal(salutations.avoid_recent, 2);
for (const entry of salutations.values) {
  assert(!/[.!?,;:]$/.test(entry.text), "salutations must join the following phrase without a sentence break");
}
const professionOwnerIds = new Set(["greeting.shared.ordinary"]);
for (const profession of vanillaProfessions) {
  const definitions = read(path.join(professionRoot, profession, "lines/greetings/ordinary.json"));
  const owner = definitions.lines[0];
  professionOwnerIds.add(owner.id);
  assert.equal(owner.text_key, "greeting.shared.ordinary", profession);
  assert(!owner.variants && !owner.lines, profession + ": legacy greeting wording has been replaced");
  const messages = read(path.join(professionRoot, profession, "messages/greetings/shared.json")).messages;
  const byKey = new Map(messages.map(message => [message.key, message]));
  const allowedKeys = new Set(sharedMessages.map(message => message.key));
  for (const key of byKey.keys()) assert(allowedKeys.has(key), profession + ": known shared message slot");
  for (const key of ["ordinary", "wellbeing"]) assert(byKey.has("greeting.shared." + key), profession + ": distinctive profession wording");
  assert(raw(path.join(professionRoot, profession, "messages/greetings/shared.json")).messages
    .every(message => !message.variants), profession + ": presentation comes from shared templates");
  const openings = read(path.join(professionRoot, profession, "openings/greetings/ordinary.json")).openings;
  for (const [kind, variants] of [["ordinary", byKey.get("greeting.shared.ordinary").variants], ["opening", openings[0].variants]]) {
    const expected = "{word:" + salutationId + "}, {word:villagerretaliation:greetings/professions/" + profession + "/" + kind + "}";
    for (const variant of variants) {
      assert.equal(variant.text.replace(/<[^>]+>/g, ""), expected, profession + ": greeting and message join with one comma");
    }
  }
  for (const key of ["ordinary", "wellbeing", "return_greeting", "brief_hello", "player_well", "player_steady", "player_struggling", "player_private"]) {
    const message = byKey.get("greeting.shared." + key) ?? sharedMessages.find(value => value.key === "greeting.shared." + key);
    assert(message, profession + ": every slot has a shared fallback");
    assert(message.variants.some(variant => variant.text.includes("<greeting_warm>")), profession + ": warm styling");
    assert(message.variants.some(variant => variant.text.includes("<greeting_quiet>")), profession + ": quiet styling");
    assert(message.variants.some(variant => variant.text.includes("<greeting_guarded>")), profession + ": guarded styling");
  }
  for (const key of ["return_greeting", "ask_wellbeing", "brief_hello"]) {
    const message = byKey.get("greeting.player." + key) ?? sharedMessages.find(value => value.key === "greeting.player." + key);
    assert(typeof message?.text === "string" && !message.text.includes("{"), profession + ": player labels are stable literal speech");
  }
  const prefix = "villagerretaliation:greetings/professions/" + profession + "/";
  const wellbeing = compileWordPool(prefix + "wellbeing", poolLookup);
  for (const inherited of sharedWellbeing.values) {
    const replacement = wellbeing.values.find(entry => entry.id === inherited.id);
    if (["ordinary_4", "ordinary_5", "ordinary_6"].includes(inherited.id)) continue;
    assert(replacement, profession + ": missing inherited wellbeing state " + inherited.id);
    assert.deepEqual(replacement.conditions, inherited.conditions, profession + ": wellbeing gates are preserved");
    assert.equal(replacement.priority ?? 0, inherited.priority ?? 0, profession + ": wellbeing priority is preserved");
    if (!/^(ordinary|content|stressed)_/.test(inherited.id)) {
      assert.equal(replacement.text, inherited.text, profession + ": serious and guarded reactions keep shared wording");
    }
  }
  for (const key of ["ordinary", "opening", "return_greeting", "brief_hello", "player_well", "player_steady", "player_struggling", "player_private"]) {
    const file = path.join(data, "dialogue_words/en_us/greetings/professions", profession, key + ".json");
    if (!existsSync(file)) {
      assert(!byKey.has("greeting.shared." + key), profession + ": missing overrides use shared wording");
      continue;
    }
    const pool = compileWordPool(prefix + key, poolLookup);
    assert.equal(pool.avoid_recent, 2, profession + ": repeated wording is avoided");
    assert(pool.values.filter(entry => !entry.conditions?.length).length >= 3, profession + ": enough ordinary alternatives");
    if (key === "ordinary" || key === "opening") {
      assert.equal(pool.role, "literal", profession + ": greeting continuations keep their authored sentence case");
      for (const entry of pool.values) {
        assert.match(entry.text, /^(?:[a-z]|I(?:\b|'))/, profession + ": continuation starts naturally after a comma");
        assert.match(entry.text, /[.!?]$/, profession + ": continuation supplies its ending punctuation");
      }
    }
  }
}
assert.deepEqual(new Set(sharedTree.entries[0].line_ids), professionOwnerIds, "all vanilla professions use the shared flow");
console.log("Profession greeting checks passed: 15 profession states, shared presentation templates, optional wording overrides, stable choices, and inherited wellbeing guards.");
