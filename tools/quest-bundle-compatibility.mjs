import { createRequire } from "node:module";

const { migrate } = createRequire(import.meta.url)("./quest-v3-format.js");
const localizedFields = new Set([
  "title", "description", "label", "text", "lines", "tracker_text",
  "tracker_complete_text", "complete_text", "custom_name", "trophy_name",
  "boss_bar_title", "location_message"
]);
const cartographerAppendOnlyPaths = new Set([
  "/events/0/actions", "/events/1/actions",
  "/stages/0/dialogue/offer/lines",
  "/stages/2/scenes/0/actions/0/lines/completed"
]);
const cartographerAppendOnlyLocales = new Set([
  "quest.cartographers_atlas.blank_map_promise.stage.gather_paper.dialogue.offer.lines",
  "quest.cartographers_atlas.blank_map_promise.stage.return.scene.complete_quest.action.turn_in.lines.completed"
]);

function withoutLocaleAliases(value, display = false) {
  if (Array.isArray(value)) return value.map(child => withoutLocaleAliases(child));
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value)
    .filter(([key]) => !display || !key.endsWith("_key")
      || !localizedFields.has(key.slice(0, -4)) || !Object.hasOwn(value, key.slice(0, -4)))
    .map(([key, child]) => [key, withoutLocaleAliases(child, key === "ui" || key === "metadata")]));
}

function compareContract(expected, actual, pointer, appendOnlyPaths, allowExchanges = false) {
  if (Array.isArray(expected)) {
    if (!Array.isArray(actual) || actual.length !== expected.length
      && !(appendOnlyPaths.has(pointer) && actual.length >= expected.length)) {
      throw new Error(`Frozen quest contract changed at ${pointer}: array length differs`);
    }
    expected.forEach((value, index) => compareContract(value, actual[index], `${pointer}/${index}`, appendOnlyPaths, allowExchanges));
  } else if (expected && typeof expected === "object") {
    if (!actual || typeof actual !== "object" || Array.isArray(actual)) {
      throw new Error(`Frozen quest contract changed at ${pointer}: expected an object`);
    }
    const additional = Object.keys(actual).filter(key => !Object.hasOwn(expected, key));
    const dialogueOwner = /^\/stages\/\d+\/(?:dialogue\/[^/]+|scenes\/\d+|responses\/\d+)(?:\/responses\/\d+)*$/.test(pointer);
    if (additional.some(key => !allowExchanges || !dialogueOwner || key !== "exchanges")) {
      throw new Error(`Frozen quest contract changed at ${pointer}: object fields differ`);
    }
    if (additional.includes("exchanges")) {
      if (!Array.isArray(actual.exchanges)) throw new Error(`Frozen quest dialogue changed at ${pointer}: expected exchanges`);
      const field = Object.hasOwn(expected, "lines") ? "lines" : Object.hasOwn(expected, "text") ? "text" : null;
      if (field) {
        const providers = actual.exchanges.filter(exchange => exchange.speaker?.type === "role" && exchange.speaker.role === "provider");
        if (providers.length !== 1) throw new Error(`Frozen quest dialogue changed at ${pointer}: expected one provider exchange`);
        compareContract(expected[field], providers[0].text, `${pointer}/${field}`, appendOnlyPaths);
      }
    }
    for (const [key, value] of Object.entries(expected)) {
      if (!Object.hasOwn(actual, key)) throw new Error(`Frozen quest contract lost ${pointer}/${key}`);
      compareContract(value, actual[key], `${pointer}/${key}`, appendOnlyPaths, allowExchanges);
    }
  } else if (expected !== actual) {
    throw new Error(`Frozen quest contract changed at ${pointer}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

export function projectLegacyQuest(actual, frozen) {
  const expected = migrate(withoutLocaleAliases(frozen));
  // repeatable quests retain their provider after completion
  if (expected.availability?.repeatable) expected.availability.consume_on_completion = false;
  const appendOnlyPaths = frozen.id === "villagerretaliation:blank_map_promise"
    ? cartographerAppendOnlyPaths : new Set();
  try {
    compareContract(expected, actual, "", appendOnlyPaths, true);
  } catch (error) {
    throw new Error(`${frozen.id}: ${error.message}`, { cause: error });
  }
  // keep the original representation for frozen fingerprints and behavior checks
  return structuredClone(frozen);
}

export function checkFrozenLocale(key, expected, actual) {
  const appendOnlyPaths = cartographerAppendOnlyLocales.has(key) ? new Set([""]) : new Set();
  try {
    compareContract(expected, actual, "", appendOnlyPaths);
  } catch (error) {
    throw new Error(`${key}: ${error.message}`, { cause: error });
  }
}
