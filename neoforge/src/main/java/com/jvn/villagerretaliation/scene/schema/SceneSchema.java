package com.jvn.villagerretaliation.scene.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.encounter.EncounterTemplate;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import java.util.Arrays;
import java.util.Locale;

/** Checked-in browser schemas are generated from the same enums and registries used by compilation. */
public final class SceneSchema {
    private SceneSchema() {
    }

    public static JsonObject sceneV1() {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject root = object("Persistent cinematic scene v1");
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://jeven.dev/villager-retaliation/schema/scene-v1.schema.json");
        root.addProperty("additionalProperties", false);
        root.add("required", strings("schema", "id", "ownership", "entry_step", "actors", "steps"));
        JsonObject properties = new JsonObject();
        properties.add("schema", constant("villagerretaliation:scene/v1"));
        properties.add("id", resourceLocation());
        properties.add("definition_version", integer(1));
        properties.add("ownership", enumValues(SceneResource.OwnershipMode.values()));
        properties.add("entry_step", text());
        properties.add("timeout_ticks", integer(0));
        properties.add("failure_policy", enumValues(SceneResource.TransitionPolicy.values()));
        properties.add("cancellation_policy", enumValues(SceneResource.TransitionPolicy.values()));
        properties.add("cleanup_policy", enumValues(SceneResource.CleanupPolicy.values()));
        properties.add("metadata", map());
        properties.add("actors", array(actor(), 0));
        properties.add("steps", array(step(), 1));
        root.add("properties", properties);
        return root;
    }

    public static JsonObject encounterV1() {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject root = object("Controlled encounter template v1");
        root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.addProperty("$id", "https://jeven.dev/villager-retaliation/schema/encounter-v1.schema.json");
        root.addProperty("additionalProperties", false);
        root.add("required", strings("schema", "id"));
        JsonArray composition = new JsonArray();composition.add(requiredWithout("members", "waves"));composition.add(requiredWithout("waves", "members"));root.add("oneOf", composition);
        JsonObject properties = new JsonObject();
        properties.add("schema", constant("villagerretaliation:encounter/v1"));
        properties.add("id", resourceLocation());
        properties.add("version", integer(1));
        properties.add("controller", registeredIds(VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES));
        properties.add("members", memberArray());
        JsonObject waves = array(wave(), 1);waves.addProperty("maxItems", 32);properties.add("waves", waves);
        properties.add("extra_per_player", boundedInteger(0, 64));
        properties.add("max_party_size", boundedInteger(1, 16));
        properties.add("placement_attempts", boundedInteger(1, 64));
        properties.add("spawn_radius", boundedInteger(1, 32));
        properties.add("spawn_mode", enumValues(EncounterTemplate.SpawnMode.values()));
        JsonObject spawnPoints=array(spawnPoint(),1);spawnPoints.addProperty("maxItems",64);properties.add("spawn_points",spawnPoints);
        properties.add("spawn_selection",enumValues(EncounterTemplate.SpawnSelectionMode.values()));
        properties.add("wave_count", boundedInteger(1, 32));
        properties.add("wave_interval_ticks", integer(0));
        properties.add("wave_trigger", enumValues(EncounterTemplate.WaveTrigger.values()));
        properties.add("boss_bar", bool());
        properties.add("location_message", text());
        properties.add("area", encounterArea());
        properties.add("respawn_policy", enumValues(EncounterTemplate.RespawnPolicy.values()));
        properties.add("cleanup_policy", enumValues(EncounterTemplate.CleanupPolicy.values()));
        properties.add("completion_condition", enumValues(EncounterTemplate.CompletionCondition.values()));
        root.add("properties", properties);
        JsonArray rules=new JsonArray();JsonObject selectionCondition=new JsonObject();selectionCondition.add("required",strings("spawn_selection"));JsonObject pointsRequired=new JsonObject();pointsRequired.add("required",strings("spawn_points"));JsonObject selectionRule=new JsonObject();selectionRule.add("if",selectionCondition);selectionRule.add("then",pointsRequired);rules.add(selectionRule);
        JsonObject nearCondition=new JsonObject();nearCondition.add("required",strings("spawn_mode"));JsonObject nearProperties=new JsonObject();nearProperties.add("spawn_mode",constant("near_player"));nearCondition.add("properties",nearProperties);JsonObject noPoints=new JsonObject();noPoints.add("not",pointsRequired);JsonObject nearRule=new JsonObject();nearRule.add("if",nearCondition);nearRule.add("then",noPoints);rules.add(nearRule);root.add("allOf",rules);
        return root;
    }

    private static JsonObject spawnPoint(){
        JsonObject point=object("Named authored spawn point");point.addProperty("additionalProperties",false);point.add("required",strings("id"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("actor",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("marker",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("dimension",resourceLocation());properties.add("x",boundedInteger(-30000000,30000000));properties.add("y",boundedInteger(-2048,2048));properties.add("z",boundedInteger(-30000000,30000000));properties.add("offset_x",boundedInteger(-256,256));properties.add("offset_y",boundedInteger(-256,256));properties.add("offset_z",boundedInteger(-256,256));properties.add("weight",boundedInteger(1,10000));point.add("properties",properties);
        JsonArray sources=new JsonArray();sources.add(requiredWithoutAny(new String[]{"actor"},"marker","dimension","x","y","z"));sources.add(requiredWithoutAny(new String[]{"marker"},"actor","dimension","x","y","z"));sources.add(requiredWithoutAny(new String[]{"x","y","z"},"actor","marker","offset_x","offset_y","offset_z"));point.add("oneOf",sources);return point;
    }

    private static JsonObject wave() {
        JsonObject wave = object("Authored encounter wave");wave.addProperty("additionalProperties", false);wave.add("required", strings("id", "members"));JsonObject properties = new JsonObject();
        properties.add("id", patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("members", memberArray());properties.add("delay_ticks", boundedInteger(0, 12000));properties.add("trigger", enumValues(EncounterTemplate.WaveTrigger.values()));JsonObject title=text();title.addProperty("maxLength",128);properties.add("boss_bar_title", title);properties.add("equipment", equipment());JsonObject hooks=array(waveHook(),0);hooks.addProperty("maxItems",32);properties.add("scene_actions",hooks);properties.add("dialogue_hook", dialogueHook());wave.add("properties", properties);return wave;
    }

    private static JsonObject waveHook(){JsonObject hook=object("Safe wave scene action");hook.addProperty("additionalProperties",false);hook.add("required",strings("id","type","text"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("type",enumValues(EncounterTemplate.HookType.values()));JsonObject text=text();text.addProperty("maxLength",512);properties.add("text",text);hook.add("properties",properties);return hook;}
    private static JsonObject dialogueHook(){JsonObject hook=object("Wave dialogue hook");hook.addProperty("additionalProperties",false);hook.add("required",strings("id","text"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));JsonObject text=text();text.addProperty("maxLength",512);properties.add("text",text);hook.add("properties",properties);return hook;}
    private static JsonObject requiredWithout(String required,String forbidden){JsonObject value=new JsonObject();value.add("required",strings(required));JsonObject not=new JsonObject();not.add("required",strings(forbidden));value.add("not",not);return value;}
    private static JsonObject requiredWithoutAny(String[] required,String... forbidden){JsonObject value=new JsonObject();value.add("required",strings(required));JsonArray rules=new JsonArray();for(String field:forbidden){JsonObject present=new JsonObject();present.add("required",strings(field));rules.add(present);}JsonObject any=new JsonObject();any.add("anyOf",rules);JsonObject not=new JsonObject();not.add("not",any);value.add("allOf",new JsonArray());value.getAsJsonArray("allOf").add(not);return value;}

    private static JsonObject encounterArea() {
        JsonObject area = object("Durable encounter area");
        area.addProperty("additionalProperties", false);
        area.add("required", strings("radius"));
        JsonObject properties = new JsonObject();
        properties.add("radius", boundedInteger(1, 256));
        properties.add("vertical_radius", boundedInteger(1, 128));
        properties.add("leave_behavior", enumValues(EncounterTemplate.LeaveBehavior.values()));
        properties.add("leave_timeout_ticks", boundedInteger(1, 12000));
        properties.add("mob_behavior", enumValues(EncounterTemplate.MobBehavior.values()));
        properties.add("mob_timeout_ticks", boundedInteger(1, 12000));
        area.add("properties", properties);
        JsonObject condition = new JsonObject();condition.add("required", strings("mob_timeout_ticks"));
        JsonObject consequence = new JsonObject();consequence.add("required", strings("mob_behavior"));JsonObject consequenceProperties = new JsonObject();consequenceProperties.add("mob_behavior", constant("teleport"));consequence.add("properties", consequenceProperties);
        JsonObject rule = new JsonObject();rule.add("if", condition);rule.add("then", consequence);JsonArray rules = new JsonArray();rules.add(rule);area.add("allOf", rules);
        return area;
    }

    private static JsonObject actor() {
        JsonObject actor = object("Actor declaration");
        actor.addProperty("additionalProperties", false);
        actor.add("required", strings("alias", "type", "binding_source", "replacement_policy"));
        JsonObject properties = new JsonObject();
        properties.add("alias", patternedText("^[a-z][a-z0-9_.-]{0,63}$"));
        properties.add("type", registeredIds(VillagerRetaliationRegistries.ACTOR_TYPES));
        properties.add("required", bool());
        properties.add("capabilities", array(resourceLocation(), 0));
        properties.add("binding_source", enumValues(SceneActorDeclaration.BindingSource.values()));
        properties.add("binding", text());
        properties.add("replacement_policy", enumValues(SceneActorDeclaration.ReplacementPolicy.values()));
        properties.add("missing_actor_policy", enumValues(SceneActorDeclaration.MissingActorPolicy.values()));
        properties.add("death_policy", enumValues(SceneActorDeclaration.DeathPolicy.values()));
        properties.add("filters", map());
        properties.add("timeout_ticks", integer(0));
        actor.add("properties", properties);
        return actor;
    }

    private static JsonObject step() {
        JsonObject step = object("Stable scene step");
        step.addProperty("additionalProperties", false);
        step.add("required", strings("id", "type"));
        JsonObject properties = new JsonObject();
        properties.add("id", text());
        properties.add("type", registeredIds(VillagerRetaliationRegistries.SCENE_STEPS));
        properties.add("actors", array(patternedText("^[a-z][a-z0-9_.-]{0,63}$"), 0));
        properties.add("data", map());
        properties.add("next", text());
        properties.add("failure_step", text());
        properties.add("transitions", map());
        step.add("properties", properties);
        return step;
    }

    private static JsonObject member() {
        JsonObject member = object("Encounter member");
        member.addProperty("additionalProperties", false);
        member.add("required", strings("entity"));
        JsonObject properties = new JsonObject();
        properties.add("entity", resourceLocation());
        properties.add("count", boundedInteger(1, 64));
        properties.add("equipment", equipment());
        JsonObject customName=text();customName.addProperty("maxLength",128);properties.add("custom_name",customName);
        properties.add("name_visible",bool());properties.add("glowing",bool());properties.add("persistent",bool());
        properties.add("health",number(1.0D,2048.0D));properties.add("movement_speed",number(0.0D,4.0D));properties.add("attack_damage",number(0.0D,2048.0D));properties.add("armor",number(0.0D,30.0D));properties.add("knockback_resistance",number(0.0D,1.0D));properties.add("attributes",mobAttributes());properties.add("boss",bool());properties.add("boss_bar_color",enumValues(EncounterTemplate.BossColor.values()));properties.add("boss_bar_overlay",enumValues(EncounterTemplate.BossOverlay.values()));
        member.add("properties", properties);
        JsonArray rules=new JsonArray();rules.add(requireWhenTrue("name_visible","custom_name"));rules.add(requireBossFor("boss_bar_color"));rules.add(requireBossFor("boss_bar_overlay"));member.add("allOf",rules);
        return member;
    }
    private static JsonObject memberArray(){JsonObject members=array(member(),1);members.addProperty("maxItems",64);return members;}

    private static JsonObject equipment() {
        JsonObject equipment = object("Equipment by slot");
        equipment.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        for (String slot : new String[]{"mainhand", "offhand", "head", "chest", "legs", "feet", "body"}) {
            properties.add(slot, gear());
        }
        equipment.add("properties", properties);
        return equipment;
    }

    private static JsonObject gear() {
        JsonObject gear = object("Equipped item");
        gear.addProperty("additionalProperties", false);
        gear.add("required", strings("item"));
        JsonObject properties = new JsonObject();
        properties.add("item", resourceLocation());
        properties.add("count", boundedInteger(1, 99));
        properties.add("enchantments", enchantments());
        JsonObject chance = new JsonObject();chance.addProperty("type", "number");chance.addProperty("minimum", 0);chance.addProperty("maximum", 1);
        properties.add("drop_chance", chance);
        gear.add("properties", properties);
        return gear;
    }

    private static JsonObject enchantments() {
        JsonObject value = object("Enchantment levels");
        value.add("propertyNames", resourceLocation());
        value.add("additionalProperties", boundedInteger(1, 255));
        return value;
    }

    private static JsonObject mobAttributes(){JsonObject value=object("Allowlisted mob attributes");value.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();properties.add("minecraft:max_health",number(1.0D,2048.0D));properties.add("minecraft:movement_speed",number(0.0D,4.0D));properties.add("minecraft:attack_damage",number(0.0D,2048.0D));properties.add("minecraft:armor",number(0.0D,30.0D));properties.add("minecraft:knockback_resistance",number(0.0D,1.0D));value.add("properties",properties);return value;}
    private static JsonObject number(double minimum,double maximum){JsonObject value=new JsonObject();value.addProperty("type","number");value.addProperty("minimum",minimum);value.addProperty("maximum",maximum);return value;}
    private static JsonObject requireWhenTrue(String flag,String required){JsonObject condition=new JsonObject();JsonObject conditionProperties=new JsonObject();conditionProperties.add(flag,constant(true));condition.add("properties",conditionProperties);condition.add("required",strings(flag));JsonObject consequence=new JsonObject();consequence.add("required",strings(required));JsonObject rule=new JsonObject();rule.add("if",condition);rule.add("then",consequence);return rule;}
    private static JsonObject requireBossFor(String field){JsonObject condition=new JsonObject();condition.add("required",strings(field));JsonObject consequence=new JsonObject();JsonObject properties=new JsonObject();properties.add("boss",constant(true));consequence.add("properties",properties);consequence.add("required",strings("boss"));JsonObject rule=new JsonObject();rule.add("if",condition);rule.add("then",consequence);return rule;}

    private static JsonObject registeredIds(com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry<?> registry) {
        JsonObject value = resourceLocation();
        JsonArray ids = new JsonArray();
        registry.descriptors().forEach(descriptor -> ids.add(descriptor.id().toString()));
        value.add("enum", ids);
        return value;
    }

    private static JsonObject object(String title) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "object");
        value.addProperty("title", title);
        return value;
    }

    private static JsonObject text() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("minLength", 1);
        return value;
    }

    private static JsonObject patternedText(String pattern) {
        JsonObject value = text();
        value.addProperty("pattern", pattern);
        return value;
    }

    private static JsonObject bool() {
        JsonObject value = new JsonObject();
        value.addProperty("type", "boolean");
        return value;
    }

    private static JsonObject resourceLocation() {
        return patternedText("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    }

    private static JsonObject integer(int minimum) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "integer");
        value.addProperty("minimum", minimum);
        return value;
    }

    private static JsonObject boundedInteger(int minimum, int maximum) {
        JsonObject value = integer(minimum);
        value.addProperty("maximum", maximum);
        return value;
    }

    private static JsonObject map() {
        JsonObject value = object("Data");
        value.addProperty("additionalProperties", true);
        return value;
    }

    private static JsonObject array(JsonObject item, int minimumItems) {
        JsonObject value = new JsonObject();
        value.addProperty("type", "array");
        value.addProperty("minItems", minimumItems);
        value.add("items", item);
        return value;
    }

    private static JsonObject constant(String constant) {
        JsonObject value = new JsonObject();
        value.addProperty("const", constant);
        return value;
    }
    private static JsonObject constant(boolean constant){JsonObject value=new JsonObject();value.addProperty("const",constant);return value;}

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        Arrays.stream(values).forEach(array::add);
        return array;
    }

    private static JsonObject enumValues(Enum<?>[] values) {
        JsonObject value = text();
        JsonArray choices = new JsonArray();
        Arrays.stream(values).map(entry -> entry.name().toLowerCase(Locale.ROOT)).forEach(choices::add);
        value.add("enum", choices);
        return value;
    }
}
