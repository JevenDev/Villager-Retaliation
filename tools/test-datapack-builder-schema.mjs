import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

function stubElement() {
  return {
    dataset: {},
    style: { setProperty() {}, removeProperty() {} },
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    value: "",
    checked: false,
    readOnly: false,
    scrollTop: 0,
    scrollLeft: 0,
    scrollHeight: 0,
    scrollWidth: 0,
    clientHeight: 100,
    clientWidth: 100,
    offsetWidth: 100,
    offsetHeight: 100,
    innerHTML: "",
    textContent: "",
    parentElement: null,
    addEventListener() {},
    removeEventListener() {},
    setAttribute() {},
    removeAttribute() {},
    append() {},
    appendChild(child) { return child; },
    replaceChildren() {},
    prepend() {},
    remove() {},
    focus() {},
    blur() {},
    click() {},
    requestSubmit() {},
    closest() { return null; },
    matches() { return false; },
    contains() { return false; },
    querySelector() { return stubElement(); },
    querySelectorAll() { return []; },
    insertAdjacentHTML() {},
    getBoundingClientRect() {
      return { left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 };
    }
  };
}

function createAppHarness() {
  const documentStub = {
    body: stubElement(),
    documentElement: stubElement(),
    querySelector() { return stubElement(); },
    querySelectorAll() { return []; },
    createElement() { return stubElement(); },
    createDocumentFragment() { return stubElement(); },
    createTextNode(text) {
      const node = stubElement();
      node.textContent = text;
      return node;
    },
    addEventListener() {},
    removeEventListener() {},
    getSelection() {
      return { rangeCount: 0, toString() { return ""; } };
    }
  };
  const storage = new Map();
  const context = {
    console,
    TextEncoder,
    TextDecoder,
    Uint8Array,
    Blob,
    Map,
    Set,
    WeakMap,
    Date,
    Math,
    JSON,
    RegExp,
    String,
    Number,
    Boolean,
    Array,
    Object,
    Promise,
    parseInt,
    isNaN,
    localStorage: {
      getItem: (key) => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, String(value)),
      removeItem: (key) => storage.delete(key)
    },
    document: documentStub,
    window: {
      addEventListener() {},
      removeEventListener() {},
      setTimeout,
      clearTimeout,
      setInterval: () => 1,
      clearInterval() {},
      requestAnimationFrame: (callback) => setTimeout(callback, 0),
      scrollX: 0,
      scrollY: 0,
      scrollTo() {},
      lucide: { createIcons() {} }
    },
    navigator: { clipboard: { writeText: async () => {} } },
    CSS: { escape: (value) => String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&") },
    URL: { createObjectURL() { return "blob:test"; }, revokeObjectURL() {} },
    getComputedStyle() { return { paddingTop: "0", font: "12px monospace", lineHeight: "16px" }; },
    setTimeout,
    clearTimeout,
    setInterval: () => 1,
    clearInterval() {},
    requestAnimationFrame: (callback) => setTimeout(callback, 0)
  };
  context.globalThis = context;

  const source = `${fs.readFileSync("tools/datapack-builder/backend.js", "utf8")}
${fs.readFileSync("tools/datapack-builder/app.js", "utf8")}
globalThis.__test = {
  get state() { return state; },
  set state(value) { state = value; },
  backend: datapackBackend,
  createInitialState,
  generatedFiles,
  dialoguePathInfo,
  applyEditedFile,
  ingestKnownJson,
  validate,
  dialogueFolderTemplateFiles,
  get dialogueTypes() { return CONSTANTS.dialogueTypes; }
};`;
  vm.runInNewContext(source, context, { filename: "tools/datapack-builder/app.js" });
  return context.__test;
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function jsonFile(files, path) {
  assert(Object.hasOwn(files, path), `Missing generated file: ${path}`);
  return JSON.parse(files[path]);
}

function testTypedFolderOutput(app) {
  app.state.dialogue.lines.push({
    id: "my_pack.line.category",
    request: "question",
    text_key: "my_pack.message.weather",
    conditions: [{ type: "weather", weather: "rain" }],
    priority: 30,
    category: "weather_reply",
    topic: "market rumors",
    tags: ["rumor", "weather"],
    questline: "market_board",
    quest: "ask_weather",
    stage: "intro",
    weight: 4
  });

  const path = "data/my_pack/dialogue/en_us/my_pack/lines/00_question.json";
  const line = jsonFile(app.generatedFiles(), path);
  for (const key of ["text_key", "conditions", "priority", "category", "topic", "tags", "questline", "quest", "stage"]) {
    assert(Object.hasOwn(line, key), `Generated typed line lost ${key}.`);
  }
  assert(line.category === "weather_reply", "Generated typed line category changed.");
  assert(line.questline === "market_board", "Generated typed line questline changed.");
}

function testTypedImportAndProfessionDefaults(app) {
  const path = "data/examplemod/dialogue/en_us/professions/examplemod/alchemist/lines/reagents.json";
  const input = {
    id: "alchemy.line",
    request: "question",
    text_key: "alchemy.message",
    conditions: [{ type: "family", relation: "parent" }],
    priority: 8,
    category: "alchemy_reply",
    topic: "alchemy reagents",
    tags: ["alchemy", "quest"],
    questline: "apprentice_alchemist",
    quest: "find_reagents",
    stage: "rumor",
    weight: 1
  };
  assert(app.ingestKnownJson(path, JSON.stringify(input)), "Typed line import returned false.");

  const imported = app.state.dialogue.lines.find((entry) => entry.id === "alchemy.line");
  assert(imported, "Typed line import did not add an entry.");
  assert(imported.professions?.[0] === "examplemod:alchemist", "Custom profession folder default was not applied.");
  assert(imported.category === "alchemy_reply", "Imported category changed.");
  assert(imported.priority === 8, "Imported priority changed.");
  assert(imported.text_key === "alchemy.message", "Imported text_key changed.");
  assert(imported.questline === "apprentice_alchemist", "Imported questline changed.");
  assert(imported.tags?.[0] === "alchemy", "Imported tags changed.");

  const output = jsonFile(app.generatedFiles(), path);
  assert(!Object.hasOwn(output, "professions"), "Re-export should omit a profession implied by the typed folder path.");
  assert(output.category === "alchemy_reply", "Re-exported category changed.");
  assert(output.priority === 8, "Re-exported priority changed.");
  assert(output.text_key === "alchemy.message", "Re-exported text_key changed.");
  assert(output.quest === "find_reagents", "Re-exported quest changed.");
  assert(output.stage === "rumor", "Re-exported stage changed.");

  const info = app.dialoguePathInfo(path);
  assert(info.kind === "lines", "Typed path kind was not inferred.");
  assert(info.folderName === "professions/examplemod/alchemist", "Typed path folder name was not inferred.");
  assert(info.profession === "examplemod:alchemist", "Typed path custom profession was not inferred.");
}

function testBundleImport(app) {
  const path = "data/my_pack/dialogue/en_us/global/messages/trade_refresh.json";
  const bundle = { messages: [{ id: "msg.id", key: "msg.key", lines: ["one", "two"] }] };
  assert(app.ingestKnownJson(path, JSON.stringify(bundle)), "Bundle import returned false.");
  const message = app.state.dialogue.messages.find((entry) => entry.id === "msg.id");
  assert(message, "Bundle import did not add the message.");
  assert(message.key === "msg.key", "Bundle import changed the message key.");
  assert(message.__sourcePath === path, "Bundle import did not preserve the source path.");
}

function testTypedOptionWithoutType(app) {
  const path = "data/my_pack/dialogue/en_us/my_pack/options/00_question.json";
  const option = { id: "my_pack.ask_question", label: "Ask", request: "question" };
  assert(app.ingestKnownJson(path, JSON.stringify(option)), "Typed option import without type returned false.");
  const imported = app.state.dialogue.options.find((entry) => entry.id === "my_pack.ask_question");
  assert(imported, "Typed option import did not add an option.");
  assert(imported.request === "question", "Typed option request changed.");
}

function testReservedFolderValidation(app) {
  app.state.dialogue.folderName = "my_pack/lines";
  const checks = app.validate();
  assert(
    checks.some((check) => check.type === "error" && check.title === "Dialogue folder"),
    "Reserved typed folder validation did not fire."
  );
}

function testDialogueFolderTemplate(app) {
  const files = app.dialogueFolderTemplateFiles();
  const optionPaths = Object.keys(files).filter((path) => path.includes("/dialogue/en_us/example_template/options/"));
  const linePaths = Object.keys(files).filter((path) => path.includes("/dialogue/en_us/example_template/lines/"));
  assert(optionPaths.length === app.dialogueTypes.length, "Template does not include one option file per dialogue request.");
  assert(linePaths.length === app.dialogueTypes.length, "Template does not include one line file per dialogue request.");
  for (const path of linePaths) {
    const line = JSON.parse(files[path]);
    assert(line.text === "example", `${path} does not use example text.`);
  }
}

function testCheckedInTemplateMatchesBuilder(app) {
  const root = "example-packs/dialogue-folder-template";
  const generated = app.dialogueFolderTemplateFiles();
  const actual = Object.fromEntries(walkFiles(root).map((file) => [
    file.slice(root.length + 1).replaceAll(path.sep, "/"),
    fs.readFileSync(file, "utf8")
  ]));
  const paths = new Set([...Object.keys(generated), ...Object.keys(actual)]);
  for (const filePath of paths) {
    assert(Object.hasOwn(generated, filePath), `Checked-in template has stale extra file: ${filePath}`);
    assert(Object.hasOwn(actual, filePath), `Checked-in template is missing generated file: ${filePath}`);
    assert(actual[filePath] === generated[filePath], `Checked-in template differs from builder output: ${filePath}`);
  }
}

function walkFiles(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(root, entry.name);
    return entry.isDirectory() ? walkFiles(entryPath) : [entryPath];
  });
}

function testAllSurfaceGeneration(app) {
  app.state = app.createInitialState();
  app.state.dialogue.options.push({ id: "ask_trade", label: "Trade?", request: "trade" });
  app.state.forcedDialogue.entries.push({ trigger: "theft", line: "Put that back." });
  app.state.notifications.notifications.push({ trigger: "quest.expired", text: "Too late.", kind: "quest" });
  app.state.gifts.preferences.push({ reaction: "liked", item: "minecraft:apple" });
  app.state.gifts.rewards.push({ item: "minecraft:emerald", min_count: 1, max_count: 2, weight: 1 });
  app.state.pacification.payments.push({ item: "minecraft:emerald", count: 3 });
  app.state.stories.structures.push({ structure: "minecraft:village_plains", lines: ["The village is near."] });
  app.state.stories.biomes.push({ biome: "minecraft:plains", lines: ["The grassland is familiar."] });
  app.state.names.male_names.push("Alden");
  app.state.names.female_names.push("Mira");
  app.state.extraFiles["data/my_pack/functions/setup.mcfunction"] = "say ready\n";

  const files = app.generatedFiles();
  jsonFile(files, "pack.mcmeta");
  jsonFile(files, "data/my_pack/dialogue/en_us/my_pack/options/00_trade.json");
  jsonFile(files, "data/my_pack/forced_dialogue/my_pack_forced_dialogue.json");
  jsonFile(files, "data/villagerretaliation/notifications/en_us/my_pack_notifications.json");
  jsonFile(files, "data/villagerretaliation/gifts/my_pack_gifts.json");
  jsonFile(files, "data/villagerretaliation/pacification/my_pack_pacification.json");
  jsonFile(files, "data/my_pack/story_structures/my_pack_structures.json");
  jsonFile(files, "data/my_pack/story_biomes/my_pack_biomes.json");
  jsonFile(files, "data/villagerretaliation/villager_names/preset_names.json");
  assert(files["data/my_pack/functions/setup.mcfunction"] === "say ready\n", "Extra file was not preserved in generated output.");
}

function testSurfaceImportsAndEdits(app) {
  app.state = app.createInitialState();
  const imports = [
    ["data/examplepack/forced_dialogue/theft.json", { entries: [{ trigger: "theft", line: "Stop." }] }],
    ["data/villagerretaliation/notifications/en_us/events.json", { notifications: [{ trigger: "quest.expired", text: "Expired.", kind: "quest" }] }],
    ["data/villagerretaliation/gifts/custom.json", { preferences: [{ reaction: "liked", item: "minecraft:bread" }], rewards: [{ item: "minecraft:emerald" }] }],
    ["data/villagerretaliation/pacification/payments.json", { payments: [{ item: "minecraft:emerald", count: 4 }] }],
    ["data/storypack/story_structures/places.json", { radius: 64, entries: [{ structure: "minecraft:village_savanna" }] }],
    ["data/storypack/story_biomes/biomes.json", { entries: [{ biome: "minecraft:savanna" }] }],
    ["data/villagerretaliation/villager_names/preset_names.json", { names: ["Rowan"], female_names: ["Iris"] }]
  ];

  for (const [path, payload] of imports) {
    assert(app.ingestKnownJson(path, JSON.stringify(payload)), `Import failed for ${path}.`);
  }

  assert(app.state.forcedDialogue.entries.length === 1, "Forced dialogue import missed entries.");
  assert(app.state.notifications.notifications[0].trigger === "quest.expired", "Notification import missed quest trigger.");
  assert(app.state.gifts.preferences[0].item === "minecraft:bread", "Gift import missed preference.");
  assert(app.state.pacification.payments[0].count === 4, "Pacification import missed payment.");
  assert(app.state.stories.namespace === "storypack", "Story import did not update namespace.");
  assert(app.state.names.male_names.includes("Rowan"), "Name import missed legacy names field.");

  assert(app.applyEditedFile("data/villagerretaliation/notifications/en_us/events.json", JSON.stringify({
    notifications: [{ trigger: "gift_given", text: "Thanks.", kind: "hud" }]
  })), "Notification preview edit was rejected.");
  assert(app.state.notifications.notifications.length === 1, "Notification preview edit did not replace entries.");
  assert(app.state.notifications.notifications[0].trigger === "gift_given", "Notification preview edit did not apply new trigger.");
}

function testBackendPathNormalization(app) {
  const normalized = app.backend.normalizeImportedPaths({
    "Example Pack/pack.mcmeta": "{}",
    "Example Pack/data/villagerretaliation/gifts/gifts.json": "{\"preferences\":[]}"
  });
  assert(Object.hasOwn(normalized, "pack.mcmeta"), "Root pack.mcmeta was not normalized.");
  assert(Object.hasOwn(normalized, "data/villagerretaliation/gifts/gifts.json"), "Nested data path was not normalized.");

  const namespaceRoot = app.backend.normalizeImportedPaths({
    "villagerretaliation/gifts/gifts.json": "{\"preferences\":[]}"
  });
  assert(Object.hasOwn(namespaceRoot, "data/villagerretaliation/gifts/gifts.json"), "Namespace-root import was not lifted under data/.");
}

const app = createAppHarness();
testTypedFolderOutput(app);
testTypedImportAndProfessionDefaults(app);
testBundleImport(app);
testTypedOptionWithoutType(app);
testReservedFolderValidation(app);
testDialogueFolderTemplate(app);
testCheckedInTemplateMatchesBuilder(app);
testAllSurfaceGeneration(app);
testSurfaceImportsAndEdits(app);
testBackendPathNormalization(app);

console.log("Datapack builder schema/import smoke test passed.");
