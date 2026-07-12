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
        JsonArray composition = new JsonArray();composition.add(requiredWithoutAny(new String[]{"members"},"waves","variants"));composition.add(requiredWithoutAny(new String[]{"waves"},"members","variants"));composition.add(requiredWithoutAny(new String[]{"variants"},"members","waves"));root.add("oneOf", composition);
        JsonObject properties = new JsonObject();
        properties.add("schema", constant("villagerretaliation:encounter/v1"));
        properties.add("id", resourceLocation());
        properties.add("version", integer(1));
        properties.add("controller", registeredIds(VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES));
        properties.add("members", memberArray());
        JsonObject waves = array(wave(), 1);waves.addProperty("maxItems", 32);properties.add("waves", waves);
        JsonObject variants=array(encounterVariant(),1);variants.addProperty("maxItems",32);properties.add("variants",variants);
        properties.add("extra_per_player", boundedInteger(0, 64));
        properties.add("max_party_size", boundedInteger(1, 16));
        properties.add("placement_attempts", boundedInteger(1, 64));
        properties.add("spawn_radius", boundedInteger(1, 32));
        properties.add("spawn_mode", enumValues(EncounterTemplate.SpawnMode.values()));
        JsonObject spawnPoints=array(spawnPoint(),1);spawnPoints.addProperty("maxItems",64);properties.add("spawn_points",spawnPoints);
        properties.add("spawn_selection",enumValues(EncounterTemplate.SpawnSelectionMode.values()));
        JsonObject phases=array(encounterPhase(),1);phases.addProperty("maxItems",64);properties.add("phases",phases);
        JsonObject allies=array(encounterAlly(),1);allies.addProperty("maxItems",32);properties.add("allies",allies);
        properties.add("failure",encounterFailure());
        properties.add("environment",encounterEnvironment());
        properties.add("guidance",encounterGuidance());
        properties.add("rewards",encounterRewards());
        properties.add("wave_count", boundedInteger(1, 32));
        properties.add("wave_interval_ticks", integer(0));
        properties.add("wave_trigger", enumValues(EncounterTemplate.WaveTrigger.values()));
        properties.add("boss_bar", bool());
        properties.add("location_message", text());
        properties.add("area", encounterArea());
        properties.add("respawn_policy", enumValues(EncounterTemplate.RespawnPolicy.values()));
        properties.add("cleanup_policy", enumValues(EncounterTemplate.CleanupPolicy.values()));
        properties.add("completion_condition", enumValues(EncounterTemplate.CompletionCondition.values()));
        properties.add("completion_objectives",completionObjectives());
        root.add("properties", properties);
        JsonArray rules=new JsonArray();JsonObject selectionCondition=new JsonObject();selectionCondition.add("required",strings("spawn_selection"));JsonObject pointsRequired=new JsonObject();pointsRequired.add("required",strings("spawn_points"));JsonObject selectionRule=new JsonObject();selectionRule.add("if",selectionCondition);selectionRule.add("then",pointsRequired);rules.add(selectionRule);
        JsonObject nearCondition=new JsonObject();nearCondition.add("required",strings("spawn_mode"));JsonObject nearProperties=new JsonObject();nearProperties.add("spawn_mode",constant("near_player"));nearCondition.add("properties",nearProperties);JsonObject noPoints=new JsonObject();noPoints.add("not",pointsRequired);JsonObject nearRule=new JsonObject();nearRule.add("if",nearCondition);nearRule.add("then",noPoints);rules.add(nearRule);JsonObject exclusiveCompletion=new JsonObject();JsonObject bothCompletion=new JsonObject();bothCompletion.add("required",strings("completion_condition","completion_objectives"));exclusiveCompletion.add("not",bothCompletion);rules.add(exclusiveCompletion);JsonObject legacyGuidance=new JsonObject();legacyGuidance.add("required",strings("location_message","guidance"));JsonObject noLegacyGuidance=new JsonObject();noLegacyGuidance.add("not",legacyGuidance);rules.add(noLegacyGuidance);JsonObject variantCondition=new JsonObject();variantCondition.add("required",strings("variants"));JsonObject variantRule=new JsonObject();variantRule.add("if",variantCondition);variantRule.add("then",requiredWithoutAny(new String[]{},"members","waves","extra_per_player","max_party_size","placement_attempts","spawn_radius","spawn_mode","spawn_points","spawn_selection","phases","allies","failure","environment","guidance","rewards","wave_count","wave_interval_ticks","wave_trigger","boss_bar","location_message","area","respawn_policy","cleanup_policy","completion_condition","completion_objectives"));rules.add(variantRule);root.add("allOf",rules);
        return root;
    }

    private static JsonObject spawnPoint(){
        JsonObject point=object("Named authored spawn point");point.addProperty("additionalProperties",false);point.add("required",strings("id"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("actor",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("marker",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("dimension",resourceLocation());properties.add("x",boundedInteger(-30000000,30000000));properties.add("y",boundedInteger(-2048,2048));properties.add("z",boundedInteger(-30000000,30000000));properties.add("offset_x",boundedInteger(-256,256));properties.add("offset_y",boundedInteger(-256,256));properties.add("offset_z",boundedInteger(-256,256));properties.add("weight",boundedInteger(1,10000));point.add("properties",properties);
        JsonArray sources=new JsonArray();sources.add(requiredWithoutAny(new String[]{"actor"},"marker","dimension","x","y","z"));sources.add(requiredWithoutAny(new String[]{"marker"},"actor","dimension","x","y","z"));sources.add(requiredWithoutAny(new String[]{"x","y","z"},"actor","marker","offset_x","offset_y","offset_z"));point.add("oneOf",sources);return point;
    }

    private static JsonObject encounterPhase(){JsonObject phase=object("Durable mid-fight encounter phase");phase.addProperty("additionalProperties",false);phase.add("required",strings("id","trigger","actions"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("trigger",phaseTrigger());JsonObject actions=array(phaseAction(),1);actions.addProperty("maxItems",32);properties.add("actions",actions);properties.add("repeatable",bool());properties.add("repeat_interval_ticks",boundedInteger(1,12000));properties.add("max_fires",boundedInteger(2,64));phase.add("properties",properties);JsonObject repeatIf=new JsonObject();repeatIf.add("required",strings("repeatable"));JsonObject repeatProperties=new JsonObject();repeatProperties.add("repeatable",constant(true));repeatIf.add("properties",repeatProperties);JsonObject repeatThen=new JsonObject();repeatThen.add("required",strings("repeat_interval_ticks","max_fires"));JsonObject repeatElse=requiredWithoutAny(new String[]{},"repeat_interval_ticks","max_fires");JsonObject rule=new JsonObject();rule.add("if",repeatIf);rule.add("then",repeatThen);rule.add("else",repeatElse);JsonArray rules=new JsonArray();rules.add(rule);phase.add("allOf",rules);return phase;}
    private static JsonObject phaseTrigger(){JsonObject trigger=object("Durable encounter phase trigger");trigger.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();properties.add("type",enumValues(EncounterTemplate.PhaseTriggerType.values()));properties.add("wave",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("percentage",boundedInteger(0,100));properties.add("ticks",boundedInteger(1,1728000));properties.add("member",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));trigger.add("properties",properties);JsonArray choices=new JsonArray();choices.add(triggerChoice("wave_started","wave","percentage","ticks","member"));choices.add(triggerChoice("wave_completed","wave","percentage","ticks","member"));choices.add(triggerChoice("remaining_percentage","percentage","wave","ticks","member"));choices.add(triggerChoice("elapsed_time","ticks","wave","percentage","member"));choices.add(triggerChoice("elite_defeated","member","wave","percentage","ticks"));trigger.add("oneOf",choices);return trigger;}
    private static JsonObject phaseAction(){JsonObject action=object("Allowlisted encounter phase action");action.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("type",enumValues(EncounterTemplate.PhaseActionType.values()));JsonObject text=text();text.addProperty("maxLength",512);properties.add("text",text);properties.add("scope",enumValues(EncounterTemplate.FactScope.values()));properties.add("tag",resourceLocation());properties.add("key",patternedText("^[a-zA-Z0-9_.:-]{1,128}$"));JsonObject value=text();value.addProperty("maxLength",128);properties.add("value",value);properties.add("target",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));action.add("properties",properties);JsonArray choices=new JsonArray();choices.add(typedChoice("notification","text","scope","tag","key","value","target"));choices.add(typedChoice("dialogue","text","scope","tag","key","value","target"));choices.add(typedChoice("transition","target","text","scope","tag","key","value"));JsonObject factTag=typedChoice("fact","tag","text","key","value","target");JsonObject factVariable=typedChoice("fact","key","text","tag","target");factVariable.add("required",strings("id","type","key","value"));JsonObject fact=new JsonObject();JsonArray factChoices=new JsonArray();factChoices.add(factTag);factChoices.add(factVariable);fact.add("oneOf",factChoices);choices.add(fact);action.add("oneOf",choices);return action;}
    private static JsonObject typedChoice(String type,String requiredField,String... forbidden){JsonObject choice=requiredWithoutAny(new String[]{"id","type",requiredField},forbidden);JsonObject properties=new JsonObject();properties.add("type",constant(type));choice.add("properties",properties);return choice;}
    private static JsonObject triggerChoice(String type,String requiredField,String... forbidden){JsonObject choice=requiredWithoutAny(new String[]{"type",requiredField},forbidden);JsonObject properties=new JsonObject();properties.add("type",constant(type));choice.add("properties",properties);return choice;}

    private static JsonObject completionObjectives(){JsonObject composition=object("Composable encounter completion objectives");composition.addProperty("additionalProperties",false);composition.add("required",strings("objectives"));JsonObject properties=new JsonObject();properties.add("mode",enumValues(EncounterTemplate.ObjectiveMode.values()));JsonObject objectives=array(encounterObjective(),1);objectives.addProperty("maxItems",32);properties.add("objectives",objectives);composition.add("properties",properties);return composition;}
    private static JsonObject encounterObjective(){JsonObject objective=object("Durable encounter completion objective");objective.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("type",enumValues(EncounterTemplate.ObjectiveType.values()));properties.add("duration_ticks",boundedInteger(1,1728000));properties.add("actor",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("point",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));JsonObject actors=array(patternedText("^[a-z][a-z0-9_.-]{0,63}$"),1);actors.addProperty("maxItems",32);actors.addProperty("uniqueItems",true);properties.add("actors",actors);JsonObject points=array(patternedText("^[a-z][a-z0-9_.-]{0,63}$"),1);points.addProperty("maxItems",16);points.addProperty("uniqueItems",true);properties.add("points",points);properties.add("member",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("item",resourceLocation());properties.add("count",boundedInteger(1,64));properties.add("radius",boundedInteger(1,64));properties.add("vertical_radius",boundedInteger(1,64));objective.add("properties",properties);String[] all={"duration_ticks","actor","point","actors","points","member","item","count","radius","vertical_radius"};JsonArray choices=new JsonArray();choices.add(objectiveChoice("all_defeated",new String[]{},all));choices.add(objectiveChoice("all_gone",new String[]{},all));choices.add(objectiveChoice("survive_duration",new String[]{"duration_ticks"},"actor","point","actors","points","member","item","count","radius","vertical_radius"));choices.add(objectiveChoice("protect_actor",new String[]{"actor","duration_ticks"},"point","actors","points","member","item","count","radius","vertical_radius"));choices.add(objectiveChoice("prevent_entry",new String[]{"point","duration_ticks"},"actor","actors","points","member","item","count"));choices.add(objectiveChoice("escort_actor",new String[]{"actor","point"},"duration_ticks","actors","points","member","item","count"));choices.add(objectiveChoice("destroy_targets",new String[]{"actors"},"duration_ticks","actor","point","points","member","item","count","radius","vertical_radius"));choices.add(objectiveChoice("defeat_leader",new String[]{"member"},"duration_ticks","actor","point","actors","points","item","count","radius","vertical_radius"));choices.add(objectiveChoice("retrieve_item",new String[]{"item"},"duration_ticks","actor","point","actors","points","member","radius","vertical_radius"));choices.add(objectiveChoice("hold_areas",new String[]{"points","duration_ticks"},"actor","point","actors","member","item","count"));objective.add("oneOf",choices);return objective;}
    private static JsonObject objectiveChoice(String type,String[] fields,String... forbidden){String[] required=new String[fields.length+2];required[0]="id";required[1]="type";System.arraycopy(fields,0,required,2,fields.length);JsonObject choice=requiredWithoutAny(required,forbidden);JsonObject properties=new JsonObject();properties.add("type",constant(type));choice.add("properties",properties);return choice;}
    private static JsonObject encounterAlly(){JsonObject ally=object("Controlled friendly encounter participant");ally.addProperty("additionalProperties",false);ally.add("required",strings("id"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("entity",resourceLocation());properties.add("actor",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("count",boundedInteger(1,16));properties.add("equipment",equipment());JsonObject customName=text();customName.addProperty("maxLength",128);properties.add("custom_name",customName);properties.add("name_visible",bool());properties.add("glowing",bool());properties.add("persistent",bool());properties.add("health",number(1.0D,2048.0D));properties.add("movement_speed",number(0.0D,4.0D));properties.add("attack_damage",number(0.0D,2048.0D));properties.add("armor",number(0.0D,30.0D));properties.add("knockback_resistance",number(0.0D,1.0D));properties.add("attributes",mobAttributes());properties.add("required_survival",bool());properties.add("invulnerable",bool());properties.add("revivable",bool());properties.add("revive_delay_ticks",boundedInteger(1,12000));properties.add("replacement_policy",enumValues(EncounterTemplate.AllyReplacementPolicy.values()));properties.add("cleanup_policy",enumValues(EncounterTemplate.AllyCleanupPolicy.values()));properties.add("affects_completion",bool());ally.add("properties",properties);JsonArray sources=new JsonArray();sources.add(requiredWithoutAny(new String[]{"entity"},"actor"));sources.add(requiredWithoutAny(new String[]{"actor"},"entity","count","equipment","custom_name","name_visible","glowing","persistent","health","movement_speed","attack_damage","armor","knockback_resistance","attributes"));ally.add("oneOf",sources);JsonArray rules=new JsonArray();JsonObject reviveCondition=new JsonObject();reviveCondition.add("required",strings("revive_delay_ticks"));JsonObject reviveConsequence=new JsonObject();reviveConsequence.add("required",strings("revivable"));JsonObject reviveProperties=new JsonObject();reviveProperties.add("revivable",constant(true));reviveConsequence.add("properties",reviveProperties);JsonObject reviveRule=new JsonObject();reviveRule.add("if",reviveCondition);reviveRule.add("then",reviveConsequence);rules.add(reviveRule);JsonObject incompatible=new JsonObject();JsonArray requiredSurvival=new JsonArray();JsonObject both=new JsonObject();both.add("required",strings("required_survival","revivable"));JsonObject bothProperties=new JsonObject();bothProperties.add("required_survival",constant(true));bothProperties.add("revivable",constant(true));both.add("properties",bothProperties);incompatible.add("not",both);rules.add(incompatible);ally.add("allOf",rules);return ally;}

    private static JsonObject encounterFailure(){JsonObject failure=object("Durable encounter failure and retry policy");failure.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();properties.add("on_player_death",enumValues(EncounterTemplate.FailureAction.values()));properties.add("on_protected_actor_death",enumValues(EncounterTemplate.FailureAction.values()));properties.add("retry_delay_ticks",boundedInteger(0,12000));properties.add("max_attempts",boundedInteger(1,16));properties.add("retain_defeated",bool());properties.add("branch_step",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));failure.add("properties",properties);JsonArray branchSources=new JsonArray();for(String field:new String[]{"on_player_death","on_protected_actor_death"}){JsonObject source=new JsonObject();source.add("required",strings(field));JsonObject sourceProperties=new JsonObject();sourceProperties.add(field,constant("branch_scene"));source.add("properties",sourceProperties);branchSources.add(source);}JsonObject branchCondition=new JsonObject();branchCondition.add("anyOf",branchSources);JsonObject requireBranch=new JsonObject();requireBranch.add("required",strings("branch_step"));JsonObject forbidBranch=new JsonObject();forbidBranch.add("not",requireBranch.deepCopy());JsonObject rule=new JsonObject();rule.add("if",branchCondition);rule.add("then",requireBranch);rule.add("else",forbidBranch);JsonArray rules=new JsonArray();rules.add(rule);failure.add("allOf",rules);return failure;}

    private static JsonObject encounterVariant(){JsonObject variant=object("Weighted deterministic encounter variant");variant.addProperty("additionalProperties",false);variant.add("required",strings("id","template"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("weight",boundedInteger(1,10000));properties.add("template",resourceLocation());variant.add("properties",properties);return variant;}

    private static JsonObject encounterEnvironment(){JsonObject environment=object("Bounded encounter-owned environmental effects");environment.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();JsonObject cues=array(environmentCue(),1);cues.addProperty("maxItems",32);properties.add("cues",cues);JsonObject blocks=array(temporaryBlock(),1);blocks.addProperty("maxItems",64);properties.add("temporary_blocks",blocks);environment.add("properties",properties);JsonArray choices=new JsonArray();JsonObject cueRequired=new JsonObject();cueRequired.add("required",strings("cues"));choices.add(cueRequired);JsonObject blockRequired=new JsonObject();blockRequired.add("required",strings("temporary_blocks"));choices.add(blockRequired);environment.add("anyOf",choices);return environment;}
    private static JsonObject encounterGuidance(){JsonObject guidance=object("Participant-only navigation guidance to the durable encounter anchor");guidance.addProperty("additionalProperties",false);JsonObject properties=new JsonObject();JsonObject coordinate=text();coordinate.addProperty("maxLength",512);properties.add("coordinate_message",coordinate);JsonObject arrival=text();arrival.addProperty("maxLength",512);properties.add("arrival_message",arrival);properties.add("discovery_radius",boundedInteger(1,512));properties.add("arrival_radius",boundedInteger(1,64));properties.add("distance_tracker",bool());properties.add("compass_target",bool());properties.add("directional_particles",bool());properties.add("hud_marker",bool());properties.add("exact_coordinates",enumValues(EncounterTemplate.ExactCoordinates.values()));properties.add("update_interval_ticks",boundedInteger(10,200));guidance.add("properties",properties);return guidance;}
    private static JsonObject encounterRewards(){JsonObject rewards=object("Receipt-guarded encounter rewards and mob drop policy");rewards.addProperty("additionalProperties",false);rewards.addProperty("minProperties",1);JsonObject properties=new JsonObject();JsonObject waves=array(encounterReward("wave"),1);waves.addProperty("maxItems",32);properties.add("waves",waves);JsonObject phases=array(encounterReward("phase"),1);phases.addProperty("maxItems",32);properties.add("phases",phases);JsonObject completion=array(encounterReward(""),1);completion.addProperty("maxItems",32);properties.add("completion",completion);JsonObject trophies=array(encounterTrophy(),1);trophies.addProperty("maxItems",32);properties.add("trophies",trophies);properties.add("drop_policy",enumValues(EncounterTemplate.DropPolicy.values()));rewards.add("properties",properties);return rewards;}
    private static JsonObject encounterReward(String target){JsonObject reward=object("One durable per-participant item or loot-table reward");reward.addProperty("additionalProperties",false);reward.add("required",target.isBlank()?strings("id"):strings("id",target));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));if(target.equals("wave"))properties.add("wave",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));if(target.equals("phase"))properties.add("phase",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("loot_table",resourceLocation());properties.add("item",resourceLocation());properties.add("count",boundedInteger(1,64));JsonObject name=text();name.addProperty("maxLength",128);properties.add("trophy_name",name);reward.add("properties",properties);JsonArray sources=new JsonArray();sources.add(requiredWithoutAny(new String[]{"loot_table"},"item","count","trophy_name"));sources.add(requiredWithoutAny(new String[]{"item"},"loot_table"));reward.add("oneOf",sources);return reward;}
    private static JsonObject encounterTrophy(){JsonObject trophy=object("Retry-safe trophy dropped once for a durable hostile spawn index");trophy.addProperty("additionalProperties",false);trophy.add("required",strings("id","member","item"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("member",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("item",resourceLocation());properties.add("count",boundedInteger(1,64));JsonObject name=text();name.addProperty("maxLength",128);properties.add("name",name);trophy.add("properties",properties);return trophy;}
    private static JsonObject environmentCue(){JsonObject cue=object("One-time safe sound, music, particle, or glowing-column cue");cue.addProperty("additionalProperties",false);cue.add("required",strings("id","type"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));properties.add("type",enumValues(EncounterTemplate.EnvironmentCueType.values()));properties.add("sound",resourceLocation());properties.add("particle",resourceLocation());for(String key:new String[]{"offset_x","offset_y","offset_z"})properties.add(key,boundedInteger(-64,64));properties.add("count",boundedInteger(1,128));properties.add("height",boundedInteger(1,64));properties.add("volume",number(0,4));properties.add("pitch",number(.25,4));cue.add("properties",properties);return cue;}
    private static JsonObject temporaryBlock(){JsonObject block=object("Encounter-owned temporary allowlisted block");block.addProperty("additionalProperties",false);block.add("required",strings("id","block"));JsonObject properties=new JsonObject();properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));JsonObject blockId=text();JsonArray ids=new JsonArray();for(String id:new String[]{"minecraft:barrier","minecraft:light","minecraft:structure_void","minecraft:glass"})ids.add(id);blockId.add("enum",ids);properties.add("block",blockId);for(String key:new String[]{"offset_x","offset_y","offset_z"})properties.add(key,boundedInteger(-64,64));block.add("properties",properties);return block;}

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
        properties.add("id",patternedText("^[a-z][a-z0-9_.-]{0,63}$"));
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
