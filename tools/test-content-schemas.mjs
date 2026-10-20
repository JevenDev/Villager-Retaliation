import fs from "node:fs";

function testQuestBundleSchemas() {
  const read = name => JSON.parse(fs.readFileSync(`tools/schemas/${name}`, "utf8"));
  const quest = read("quest-v2.schema.json");
  const locale = read("quest-locale-v1.schema.json");
  const reward = read("quest-reward-v1.schema.json");
  const scene = read("scene-v1.schema.json");
  const encounter = read("encounter-v1.schema.json");
  assert(quest.required.includes("localization_prefix"), "Quest schema does not require localization_prefix.");
  assert(
    !new RegExp(quest.properties.id.pattern).test("examplemod:nested/quest"),
    "Quest schema accepts slash-containing quest IDs."
  );
  assert(locale.properties.schema.const === "villagerretaliation:quest_locale/v1", "Locale schema id changed.");
  assert(reward.properties.table.properties.type.const === "minecraft:generic", "Reward schema accepts a non-generic loot type.");
  assert(scene.$defs.localized_reference && encounter.$defs.localized_reference, "Companion schemas do not expose localized references.");
  assert(encounter.properties.location_message.$ref === "#/$defs/localized_reference",
    "Encounter schema accepts inline player-facing location messages.");
  assert(!Object.hasOwn(quest.properties, "external_scenes"), "Quest schema exposes an external dialogue root.");
  for (const definition of [quest.$defs.dialogue_slot, quest.$defs.scene]) {
    for (const field of ["external", "external_scene", "external_entry"]) {
      assert(!Object.hasOwn(definition.properties, field), "Quest schema exposes external dialogue field " + field + ".");
    }
  }
  assert(
    quest.$defs.provider.properties.blocks_hiring?.type === "boolean",
    "Quest schema does not expose provider.blocks_hiring as a boolean."
  );
  const questSchemaText = JSON.stringify(quest);
  const encounterSchemaText = JSON.stringify(encounter);
  assert(questSchemaText.includes('"label_key"'), "Quest schema does not preserve localized dialogue labels.");
  for (const field of ["custom_name_key", "boss_bar_title_key", "text_key", "trophy_name_key", "location_message_key"]) {
    assert(encounterSchemaText.includes(`"${field}"`), `Encounter schema does not expose localized runtime field ${field}.`);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

testQuestBundleSchemas();
console.log("Content schema tests passed.");
