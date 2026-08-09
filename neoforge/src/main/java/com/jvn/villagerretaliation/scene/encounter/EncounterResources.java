package com.jvn.villagerretaliation.scene.encounter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import com.jvn.villagerretaliation.util.item.ItemStackPredicateParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.storage.loot.LootTable;

public final class EncounterResources {
    public static final ResourceLocation SCHEMA=VillagerRetaliation.id("encounter/v1");
    private static final Map<ResourceLocation,double[]> MOB_ATTRIBUTES=Map.of(
            ResourceLocation.parse("minecraft:max_health"),new double[]{1.0D,2048.0D},
            ResourceLocation.parse("minecraft:movement_speed"),new double[]{0.0D,4.0D},
            ResourceLocation.parse("minecraft:attack_damage"),new double[]{0.0D,2048.0D},
            ResourceLocation.parse("minecraft:armor"),new double[]{0.0D,30.0D},
            ResourceLocation.parse("minecraft:knockback_resistance"),new double[]{0.0D,1.0D});
    private static volatile Cache cache=new Cache(null,Map.of(),Map.of());
    private EncounterResources(){}
    public static Optional<EncounterTemplate> template(MinecraftServer server,ResourceLocation id){return Optional.ofNullable(load(server).templates.get(id));}
    public static List<EncounterTemplate> templates(MinecraftServer server){return List.copyOf(load(server).templates.values());}
    public static Map<ResourceLocation,List<String>> diagnostics(MinecraftServer server){return load(server).diagnostics;}
    public static void warm(MinecraftServer server){load(server);}public static void clearCache(){cache=new Cache(null,Map.of(),Map.of());}
    public static void installTestTemplates(MinecraftServer server,List<EncounterTemplate> templates){Map<ResourceLocation,EncounterTemplate> map=new LinkedHashMap<>();templates.stream().sorted(Comparator.comparing(v->v.id().toString())).forEach(v->map.put(v.id(),v));cache=new Cache(server,Map.copyOf(map),Map.of());}
    public static void validateRewardLootTables(MinecraftServer server,EncounterTemplate template,List<String> errors){if(template==null||template.rewards()==null)return;java.util.stream.Stream.of(template.rewards().waves(),template.rewards().phases(),template.rewards().completion()).flatMap(List::stream).map(EncounterTemplate.Reward::lootTable).filter(java.util.Objects::nonNull).distinct().forEach(id->{if(server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,id))==LootTable.EMPTY)errors.add("unknown encounter reward loot table "+id);});}
    private static Cache load(MinecraftServer server){Cache value=cache;if(value.server==server)return value;synchronized(EncounterResources.class){if(cache.server==server)return cache;Map<ResourceLocation,EncounterTemplate> templates=new LinkedHashMap<>();Map<ResourceLocation,List<String>> diagnostics=new LinkedHashMap<>();for(var resource:DatapackResourceLoader.jsonResources(server,"quest_encounters")){JsonObject root=DatapackResourceLoader.readObject(resource.location(),"quest encounter",resource.resource()).orElse(null);List<String> errors=new ArrayList<>();EncounterTemplate template=parse(resource.location(),root,errors,server.registryAccess());validateRewardLootTables(server,template,errors);ResourceLocation key=template==null?resource.location():template.id();diagnostics.put(key,List.copyOf(errors));if(template!=null&&errors.isEmpty())templates.put(template.id(),template);}validateVariantGraph(templates,diagnostics);cache=new Cache(server,Map.copyOf(templates),Map.copyOf(diagnostics));return cache;}}
    public static EncounterTemplate parse(ResourceLocation source,JsonObject root,List<String> errors){
        return parse(source,root,errors,ItemStackPredicateParser.DEFAULT_REGISTRIES);
    }
    private static EncounterTemplate parse(ResourceLocation source,JsonObject root,List<String> errors,HolderLookup.Provider registries){
        if(root==null){errors.add("root must be an object");return null;}
        ResourceLocation schema=ResourceLocation.tryParse(string(root,"schema",""));if(!SCHEMA.equals(schema))errors.add("schema must be "+SCHEMA);
        ResourceLocation id=parseId(source,string(root,"id",""));
        ResourceLocation controller=parseId(source,string(root,"controller","villagerretaliation:controlled"));
        if(controller==null||VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES.get(controller).isEmpty())errors.add("unknown encounter controller "+controller);
        boolean hasWaves=root.has("waves"),hasVariants=root.has("variants");List<EncounterTemplate.Member> members=parseMembers(root.get("members"),Map.of(),errors,"encounter");
        List<EncounterTemplate.Wave> waves=parseWaves(root,errors);
        List<EncounterTemplate.Variant> variants=parseVariants(root,errors);
        List<EncounterTemplate.SpawnPoint> spawnPoints=parseSpawnPoints(root,errors);EncounterTemplate.SpawnSelectionMode spawnSelection=strictEnum(EncounterTemplate.SpawnSelectionMode.class,root,"spawn_selection",EncounterTemplate.SpawnSelectionMode.RANDOM,errors);List<EncounterTemplate.Phase> phases=parsePhases(root,errors);EncounterTemplate.ObjectiveComposition objectives=parseCompletionObjectives(root,errors,registries);
        if(hasWaves&&root.has("members"))errors.add("members and waves are mutually exclusive");
        if(hasVariants&&(root.has("members")||hasWaves))errors.add("variants are mutually exclusive with members and waves");
        if(hasVariants)for(String key:root.keySet())if(!List.of("schema","id","version","controller","variants").contains(key))errors.add("variant selector field "+key+" is unreachable");
        if(hasWaves&&(root.has("wave_count")||root.has("wave_interval_ticks")||root.has("wave_trigger")))errors.add("explicit waves cannot use wave_count, wave_interval_ticks, or wave_trigger shorthand");
        if(hasWaves&&!bool(root,"boss_bar",true)&&waves.stream().anyMatch(wave->!wave.bossBarTitle().isBlank()))errors.add("wave boss_bar_title is unreachable when boss_bar is false");
        if(spawnPoints.isEmpty()&&root.has("spawn_selection"))errors.add("spawn_selection requires a non-empty spawn_points array");if(!spawnPoints.isEmpty()&&string(root,"spawn_mode",defaultSpawnFor(hasWaves)).equalsIgnoreCase("near_player"))errors.add("authored spawn_points are incompatible with spawn_mode near_player");
        if(objectives!=null&&root.has("completion_condition"))errors.add("completion_objectives and completion_condition are mutually exclusive");
        if(root.has("guidance")&&root.has("location_message"))errors.add("guidance.coordinate_message and legacy location_message are mutually exclusive");
        if(id==null)errors.add("valid namespaced id is required");if(members.isEmpty()&&waves.isEmpty()&&variants.isEmpty())errors.add("at least one valid member, wave, or variant is required");
        if(id==null||controller==null||(members.isEmpty()&&waves.isEmpty()&&variants.isEmpty()))return null;
        String defaultSpawn=defaultSpawnFor(hasWaves);
        int extra=boundedInteger(root,"extra_per_player",0,0,64,"encounter",errors);int maxParty=boundedInteger(root,"max_party_size",4,1,16,"encounter",errors);
        try{return new EncounterTemplate(id,integer(root,"version",1),controller,members,extra,
                maxParty,integer(root,"placement_attempts",16),integer(root,"spawn_radius",8),
                enumValue(EncounterTemplate.RespawnPolicy.class,string(root,"respawn_policy","never"),EncounterTemplate.RespawnPolicy.NEVER),
                enumValue(EncounterTemplate.CleanupPolicy.class,string(root,"cleanup_policy","remove_survivors"),EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS),
                enumValue(EncounterTemplate.CompletionCondition.class,string(root,"completion_condition","all_defeated"),EncounterTemplate.CompletionCondition.ALL_DEFEATED),
                enumValue(EncounterTemplate.SpawnMode.class,string(root,"spawn_mode",defaultSpawn),hasWaves?EncounterTemplate.SpawnMode.RAID_WAVES:EncounterTemplate.SpawnMode.GROUP),
                integer(root,"wave_count",1),integer(root,"wave_interval_ticks",100),
                enumValue(EncounterTemplate.WaveTrigger.class,string(root,"wave_trigger","all_defeated"),EncounterTemplate.WaveTrigger.ALL_DEFEATED),
                bool(root,"boss_bar",true),string(root,"location_message",""),parseArea(root,errors),waves,spawnPoints,spawnSelection,phases,objectives,parseAllies(root,errors),parseFailure(root,errors),variants,validateEnvironment(parseEnvironment(root,errors),errors),parseGuidance(root,errors),parseRewards(root,members,waves,phases,errors));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
    }
    private static EncounterTemplate.RewardPolicy parseRewards(JsonObject root,List<EncounterTemplate.Member> members,List<EncounterTemplate.Wave> waves,List<EncounterTemplate.Phase> phases,List<String> errors){
        if(!root.has("rewards"))return null;JsonElement raw=root.get("rewards");if(!raw.isJsonObject()){errors.add("rewards must be an object");return null;}JsonObject rewards=raw.getAsJsonObject();if(rewards.isEmpty())errors.add("rewards must configure a reward or drop_policy");for(String key:rewards.keySet())if(!List.of("waves","phases","completion","trophies","drop_policy").contains(key))errors.add("unknown rewards field "+key);
        Set<String> waveIds=waves.isEmpty()?java.util.stream.IntStream.range(0,Math.max(1,integer(root,"wave_count",1))).mapToObj(index->"repeat_"+(index+1)).collect(java.util.stream.Collectors.toSet()):waves.stream().map(EncounterTemplate.Wave::id).collect(java.util.stream.Collectors.toSet()),phaseIds=phases.stream().map(EncounterTemplate.Phase::id).collect(java.util.stream.Collectors.toSet());Set<String> memberIds=java.util.stream.Stream.concat(members.stream(),waves.stream().flatMap(wave->wave.members().stream())).map(EncounterTemplate.Member::id).filter(id->!id.isBlank()).collect(java.util.stream.Collectors.toSet());
        validateRewardIds(rewards,errors);List<EncounterTemplate.Reward> waveRewards=parseRewardList(rewards,"waves","wave",waveIds,errors),phaseRewards=parseRewardList(rewards,"phases","phase",phaseIds,errors),completionRewards=parseRewardList(rewards,"completion","",Set.of(),errors);List<EncounterTemplate.Trophy> trophies=new ArrayList<>();JsonElement trophyRaw=rewards.get("trophies");if(trophyRaw!=null){if(!trophyRaw.isJsonArray()||trophyRaw.getAsJsonArray().size()<1||trophyRaw.getAsJsonArray().size()>32)errors.add("rewards.trophies must contain 1 to 32 entries");else for(JsonElement element:trophyRaw.getAsJsonArray()){if(!element.isJsonObject()){errors.add("encounter trophy must be an object");continue;}JsonObject trophy=element.getAsJsonObject();for(String key:trophy.keySet())if(!List.of("id","member","item","count","name").contains(key))errors.add("unknown trophy field "+key);String id=string(trophy,"id",""),member=string(trophy,"member","");ResourceLocation item=ResourceLocation.tryParse(string(trophy,"item",""));if(item==null||!BuiltInRegistries.ITEM.containsKey(item))errors.add("trophy "+id+" has unknown item "+item);if(!memberIds.contains(member))errors.add("trophy "+id+" references unknown named member "+member);try{trophies.add(new EncounterTemplate.Trophy(id,member,item,boundedInteger(trophy,"count",1,1,64,"trophy "+id,errors),string(trophy,"name","")));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}}
        EncounterTemplate.DropPolicy dropPolicy=strictEnum(EncounterTemplate.DropPolicy.class,rewards,"drop_policy",EncounterTemplate.DropPolicy.NORMAL,errors);boolean authoredEquipment=java.util.stream.Stream.concat(members.stream(),waves.stream().flatMap(wave->wave.members().stream())).flatMap(member->member.equipment().values().stream()).anyMatch(gear->gear.dropChance()>0);if(dropPolicy==EncounterTemplate.DropPolicy.AUTHORED_ONLY&&!authoredEquipment)errors.add("authored_only drop policy is unreachable without equipment drop_chance above zero");try{return new EncounterTemplate.RewardPolicy(waveRewards,phaseRewards,completionRewards,trophies,dropPolicy);}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
    }
    private static void validateRewardIds(JsonObject rewards,List<String> errors){Set<String> ids=new java.util.LinkedHashSet<>();for(String key:List.of("waves","phases","completion","trophies")){JsonElement raw=rewards.get(key);if(raw==null||!raw.isJsonArray())continue;for(JsonElement element:raw.getAsJsonArray())if(element.isJsonObject()){String id=string(element.getAsJsonObject(),"id","");if(!id.isBlank()&&!ids.add(id))errors.add("duplicate encounter reward id "+id);}}}
    private static List<EncounterTemplate.Reward> parseRewardList(JsonObject rewards,String key,String targetField,Set<String> knownTargets,List<String> errors){JsonElement raw=rewards.get(key);if(raw==null)return List.of();if(!raw.isJsonArray()||raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>32){errors.add("rewards."+key+" must contain 1 to 32 entries");return List.of();}List<EncounterTemplate.Reward> values=new ArrayList<>();for(JsonElement element:raw.getAsJsonArray()){if(!element.isJsonObject()){errors.add("encounter reward must be an object");continue;}JsonObject reward=element.getAsJsonObject();for(String field:reward.keySet())if(!List.of("id","wave","phase","loot_table","item","count","trophy_name").contains(field))errors.add("unknown reward field "+field);String id=string(reward,"id",""),target=targetField.isBlank()?"":string(reward,targetField,"");if(targetField.isBlank()&&(reward.has("wave")||reward.has("phase")))errors.add("completion reward "+id+" cannot target a wave or phase");if(!targetField.isBlank()&&!knownTargets.contains(target))errors.add("reward "+id+" references unknown "+targetField+" "+target);String unreachable=targetField.equals("wave")?"phase":targetField.equals("phase")?"wave":"";if(!unreachable.isBlank()&&reward.has(unreachable))errors.add("reward "+id+" field "+unreachable+" is unreachable in rewards."+key);ResourceLocation loot=ResourceLocation.tryParse(string(reward,"loot_table","")),item=ResourceLocation.tryParse(string(reward,"item",""));if(!reward.has("loot_table"))loot=null;if(!reward.has("item"))item=null;if(item!=null&&!BuiltInRegistries.ITEM.containsKey(item))errors.add("reward "+id+" has unknown item "+item);try{values.add(new EncounterTemplate.Reward(id,target,loot,item,boundedInteger(reward,"count",1,1,64,"reward "+id,errors),string(reward,"trophy_name","")));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}return values;}
    private static EncounterTemplate.Guidance parseGuidance(JsonObject root,List<String> errors){
        if(!root.has("guidance"))return null;JsonElement raw=root.get("guidance");if(!raw.isJsonObject()){errors.add("guidance must be an object");return null;}JsonObject guidance=raw.getAsJsonObject();
        for(String key:guidance.keySet())if(!List.of("coordinate_message","arrival_message","discovery_radius","arrival_radius","distance_tracker","compass_target","directional_particles","hud_marker","exact_coordinates","update_interval_ticks").contains(key))errors.add("unknown guidance field "+key);
        try{return new EncounterTemplate.Guidance(string(guidance,"coordinate_message",""),string(guidance,"arrival_message","Destination reached."),boundedInteger(guidance,"discovery_radius",64,1,512,"guidance",errors),boundedInteger(guidance,"arrival_radius",8,1,64,"guidance",errors),strictBoolean(guidance,"distance_tracker",true,"guidance",errors),strictBoolean(guidance,"compass_target",false,"guidance",errors),strictBoolean(guidance,"directional_particles",false,"guidance",errors),strictBoolean(guidance,"hud_marker",false,"guidance",errors),strictEnum(EncounterTemplate.ExactCoordinates.class,guidance,"exact_coordinates",EncounterTemplate.ExactCoordinates.AFTER_DISCOVERY,errors),boundedInteger(guidance,"update_interval_ticks",20,10,200,"guidance",errors));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
    }
    private static EncounterTemplate.Environment validateEnvironment(EncounterTemplate.Environment environment,List<String> errors){if(environment==null)return null;for(EncounterTemplate.EnvironmentCue cue:environment.cues())if((cue.type()==EncounterTemplate.EnvironmentCueType.PARTICLES||cue.type()==EncounterTemplate.EnvironmentCueType.GLOWING_COLUMN)&&!(BuiltInRegistries.PARTICLE_TYPE.get(cue.resource()) instanceof SimpleParticleType))errors.add("environment cue "+cue.id()+" requires a registered simple particle type");return environment;}
    private static EncounterTemplate.Environment parseEnvironment(JsonObject root,List<String> errors){if(!root.has("environment"))return null;JsonElement raw=root.get("environment");if(!raw.isJsonObject()){errors.add("environment must be an object");return null;}JsonObject environment=raw.getAsJsonObject();for(String key:environment.keySet())if(!List.of("cues","temporary_blocks").contains(key))errors.add("unknown environment field "+key);List<EncounterTemplate.EnvironmentCue> cues=new ArrayList<>();JsonElement cueRaw=environment.get("cues");if(cueRaw!=null){if(!cueRaw.isJsonArray()||cueRaw.getAsJsonArray().size()<1||cueRaw.getAsJsonArray().size()>32)errors.add("environment.cues must contain 1 to 32 entries");else for(JsonElement element:cueRaw.getAsJsonArray()){if(!element.isJsonObject()){errors.add("environment cue must be an object");continue;}JsonObject cue=element.getAsJsonObject();for(String key:cue.keySet())if(!List.of("id","type","sound","particle","offset_x","offset_y","offset_z","count","height","volume","pitch").contains(key))errors.add("unknown environment cue field "+key);String id=string(cue,"id","");EncounterTemplate.EnvironmentCueType type=strictEnum(EncounterTemplate.EnvironmentCueType.class,cue,"type",null,errors);String resourceKey=type==EncounterTemplate.EnvironmentCueType.SOUND||type==EncounterTemplate.EnvironmentCueType.MUSIC?"sound":"particle";for(String key:List.of("sound","particle"))if(cue.has(key)&&!key.equals(resourceKey))errors.add("environment cue "+id+" field "+key+" is unreachable for "+(type==null?"unknown":type.name().toLowerCase(Locale.ROOT)));ResourceLocation resource=ResourceLocation.tryParse(string(cue,resourceKey,""));if(resource==null||type!=null&&(type==EncounterTemplate.EnvironmentCueType.SOUND||type==EncounterTemplate.EnvironmentCueType.MUSIC?!BuiltInRegistries.SOUND_EVENT.containsKey(resource):!BuiltInRegistries.PARTICLE_TYPE.containsKey(resource)))errors.add("environment cue "+id+" has unknown "+resourceKey+" "+resource);try{cues.add(new EncounterTemplate.EnvironmentCue(id,type,resource,new BlockPos(boundedInteger(cue,"offset_x",0,-64,64,"environment cue "+id,errors),boundedInteger(cue,"offset_y",0,-64,64,"environment cue "+id,errors),boundedInteger(cue,"offset_z",0,-64,64,"environment cue "+id,errors)),boundedInteger(cue,"count",16,1,128,"environment cue "+id,errors),boundedInteger(cue,"height",8,1,64,"environment cue "+id,errors),decimal(cue,"volume",1),decimal(cue,"pitch",1)));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}}List<EncounterTemplate.TemporaryBlock> blocks=new ArrayList<>();JsonElement blockRaw=environment.get("temporary_blocks");if(blockRaw!=null){if(!blockRaw.isJsonArray()||blockRaw.getAsJsonArray().size()<1||blockRaw.getAsJsonArray().size()>64)errors.add("environment.temporary_blocks must contain 1 to 64 entries");else for(JsonElement element:blockRaw.getAsJsonArray()){if(!element.isJsonObject()){errors.add("temporary block must be an object");continue;}JsonObject block=element.getAsJsonObject();for(String key:block.keySet())if(!List.of("id","block","offset_x","offset_y","offset_z").contains(key))errors.add("unknown temporary block field "+key);String id=string(block,"id","");ResourceLocation blockId=ResourceLocation.tryParse(string(block,"block",""));if(!Set.of("minecraft:barrier","minecraft:light","minecraft:structure_void","minecraft:glass").contains(blockId==null?"":blockId.toString()))errors.add("temporary block "+id+" uses non-allowlisted block "+blockId);try{blocks.add(new EncounterTemplate.TemporaryBlock(id,blockId,new BlockPos(boundedInteger(block,"offset_x",0,-64,64,"temporary block "+id,errors),boundedInteger(block,"offset_y",0,-64,64,"temporary block "+id,errors),boundedInteger(block,"offset_z",0,-64,64,"temporary block "+id,errors))));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}}if(cues.isEmpty()&&blocks.isEmpty())errors.add("environment requires at least one cue or temporary block");try{return new EncounterTemplate.Environment(cues,blocks);}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}}
    private static List<EncounterTemplate.Variant> parseVariants(JsonObject root,List<String> errors){if(!root.has("variants"))return List.of();JsonElement raw=root.get("variants");if(!raw.isJsonArray()){errors.add("variants must be an array");return List.of();}if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>32)errors.add("variants must contain between 1 and 32 entries");List<EncounterTemplate.Variant> values=new ArrayList<>();Set<String> ids=new java.util.LinkedHashSet<>();for(JsonElement element:raw.getAsJsonArray()){if(!element.isJsonObject()){errors.add("encounter variant must be an object");continue;}JsonObject variant=element.getAsJsonObject();for(String key:variant.keySet())if(!List.of("id","weight","template").contains(key))errors.add("unknown encounter variant field "+key);String id=string(variant,"id","");if(!ids.add(id))errors.add("duplicate encounter variant id "+id);ResourceLocation template=ResourceLocation.tryParse(string(variant,"template",""));try{values.add(new EncounterTemplate.Variant(id,boundedInteger(variant,"weight",1,1,10000,"variant "+id,errors),template));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}return values;}
    private static EncounterTemplate.FailurePolicy parseFailure(JsonObject root,List<String> errors){if(!root.has("failure"))return null;JsonElement raw=root.get("failure");if(!raw.isJsonObject()){errors.add("failure must be an object");return null;}JsonObject failure=raw.getAsJsonObject();for(String key:failure.keySet())if(!List.of("on_player_death","on_protected_actor_death","retry_delay_ticks","max_attempts","retain_defeated","branch_step").contains(key))errors.add("unknown failure field "+key);EncounterTemplate.FailureAction player=strictEnum(EncounterTemplate.FailureAction.class,failure,"on_player_death",EncounterTemplate.FailureAction.FAIL,errors),actor=strictEnum(EncounterTemplate.FailureAction.class,failure,"on_protected_actor_death",EncounterTemplate.FailureAction.FAIL,errors);boolean branches=player==EncounterTemplate.FailureAction.BRANCH_SCENE||actor==EncounterTemplate.FailureAction.BRANCH_SCENE;if(branches&&!failure.has("branch_step"))errors.add("failure branch_scene requires branch_step");if(!branches&&failure.has("branch_step"))errors.add("failure branch_step is unreachable without branch_scene");try{return new EncounterTemplate.FailurePolicy(player,actor,boundedInteger(failure,"retry_delay_ticks",200,0,12000,"failure",errors),boundedInteger(failure,"max_attempts",3,1,16,"failure",errors),strictBoolean(failure,"retain_defeated",false,"failure",errors),string(failure,"branch_step",""));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}}
    private static List<EncounterTemplate.Ally> parseAllies(JsonObject root,List<String> errors){
        if(!root.has("allies"))return List.of();JsonElement raw=root.get("allies");if(!raw.isJsonArray()){errors.add("allies must be an array");return List.of();}if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>32)errors.add("allies must contain between 1 and 32 definitions");List<EncounterTemplate.Ally> allies=new ArrayList<>();java.util.Set<String> ids=new java.util.LinkedHashSet<>();
        for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add("ally must be an object");continue;}JsonObject ally=value.getAsJsonObject();for(String key:ally.keySet())if(!List.of("id","entity","actor","count","equipment","custom_name","name_visible","glowing","persistent","health","movement_speed","attack_damage","armor","knockback_resistance","attributes","required_survival","invulnerable","revivable","revive_delay_ticks","replacement_policy","cleanup_policy","affects_completion").contains(key))errors.add("unknown ally field "+key);String id=string(ally,"id","");if(!ids.add(id))errors.add("duplicate ally id "+id);boolean hasEntity=ally.has("entity"),hasActor=ally.has("actor");if(hasEntity==hasActor)errors.add("ally "+id+" requires exactly one entity or actor");ResourceLocation entity=null;if(hasEntity){entity=ResourceLocation.tryParse(string(ally,"entity",""));if(entity==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)){errors.add("ally "+id+" has unknown entity type "+entity);entity=null;}}String actor=string(ally,"actor","");int count=boundedInteger(ally,"count",1,1,16,"ally "+id,errors);boolean revivable=strictBoolean(ally,"revivable",false,"ally "+id,errors);if(!revivable&&ally.has("revive_delay_ticks"))errors.add("ally "+id+" revive_delay_ticks requires revivable true");Map<EquipmentSlot,EncounterTemplate.Gear> equipment=hasActor?Map.of():parseEquipment(ally,errors);EncounterTemplate.MobOptions options=hasActor?EncounterTemplate.MobOptions.DEFAULT:parseMobOptions(ally,"ally "+id,errors);if(hasActor)for(String key:List.of("count","equipment","custom_name","name_visible","glowing","persistent","health","movement_speed","attack_damage","armor","knockback_resistance","attributes"))if(ally.has(key))errors.add("bound ally "+id+" field "+key+" is unreachable");try{allies.add(new EncounterTemplate.Ally(id,entity,actor,count,equipment,options,strictBoolean(ally,"required_survival",false,"ally "+id,errors),strictBoolean(ally,"invulnerable",false,"ally "+id,errors),revivable,boundedInteger(ally,"revive_delay_ticks",revivable?100:0,0,12000,"ally "+id,errors),strictEnum(EncounterTemplate.AllyReplacementPolicy.class,ally,"replacement_policy",EncounterTemplate.AllyReplacementPolicy.NEVER,errors),strictEnum(EncounterTemplate.AllyCleanupPolicy.class,ally,"cleanup_policy",hasActor?EncounterTemplate.AllyCleanupPolicy.PRESERVE:EncounterTemplate.AllyCleanupPolicy.REMOVE,errors),strictBoolean(ally,"affects_completion",false,"ally "+id,errors)));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}
        return allies;
    }
    private static String defaultSpawnFor(boolean hasWaves){return hasWaves?"raid_waves":"group";}
    private static List<EncounterTemplate.SpawnPoint> parseSpawnPoints(JsonObject root,List<String> errors){
        if(!root.has("spawn_points"))return List.of();JsonElement raw=root.get("spawn_points");if(!raw.isJsonArray()){errors.add("spawn_points must be an array");return List.of();}if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>64)errors.add("spawn_points must contain between 1 and 64 entries");List<EncounterTemplate.SpawnPoint> points=new ArrayList<>();java.util.Set<String> ids=new java.util.LinkedHashSet<>();int index=0;
        for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add("spawn_points["+index+"] must be an object");index++;continue;}JsonObject point=value.getAsJsonObject();for(String key:point.keySet())if(!List.of("id","actor","marker","dimension","x","y","z","offset_x","offset_y","offset_z","weight").contains(key))errors.add("unknown spawn point field "+key);String id=string(point,"id","");if(!ids.add(id))errors.add("duplicate spawn point id "+id);String actor=string(point,"actor",string(point,"marker",""));if(point.has("actor")&&point.has("marker"))errors.add("spawn point "+id+" cannot define both actor and marker");boolean anyCoordinate=point.has("x")||point.has("y")||point.has("z");boolean allCoordinates=point.has("x")&&point.has("y")&&point.has("z");if(anyCoordinate&&!allCoordinates)errors.add("spawn point "+id+" coordinates require x, y, and z");if(!actor.isBlank()&&anyCoordinate)errors.add("spawn point "+id+" actor and coordinates are mutually exclusive");ResourceLocation dimension=null;if(point.has("dimension")){dimension=ResourceLocation.tryParse(string(point,"dimension",""));if(dimension==null)errors.add("spawn point "+id+" has invalid dimension");}BlockPos position=allCoordinates?new BlockPos(boundedInteger(point,"x",0,-30000000,30000000,"spawn point "+id,errors),boundedInteger(point,"y",0,-2048,2048,"spawn point "+id,errors),boundedInteger(point,"z",0,-30000000,30000000,"spawn point "+id,errors)):null;BlockPos offset=new BlockPos(boundedInteger(point,"offset_x",0,-256,256,"spawn point "+id,errors),boundedInteger(point,"offset_y",0,-256,256,"spawn point "+id,errors),boundedInteger(point,"offset_z",0,-256,256,"spawn point "+id,errors));try{points.add(new EncounterTemplate.SpawnPoint(id,actor,dimension,position,offset,boundedInteger(point,"weight",1,1,10000,"spawn point "+id,errors)));}catch(IllegalArgumentException e){errors.add(e.getMessage());}index++;}
        return points;
    }
    private static List<EncounterTemplate.Phase> parsePhases(JsonObject root,List<String> errors){
        if(!root.has("phases"))return List.of();JsonElement raw=root.get("phases");if(!raw.isJsonArray()){errors.add("phases must be an array");return List.of();}if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>64)errors.add("phases must contain between 1 and 64 entries");List<EncounterTemplate.Phase> phases=new ArrayList<>();int index=0;
        for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add("phase["+index+"] must be an object");index++;continue;}JsonObject phase=value.getAsJsonObject();for(String key:phase.keySet())if(!List.of("id","trigger","actions","repeatable","repeat_interval_ticks","max_fires").contains(key))errors.add("unknown phase field "+key);String id=string(phase,"id","");EncounterTemplate.PhaseTrigger trigger=parsePhaseTrigger(phase.get("trigger"),id,errors);List<EncounterTemplate.PhaseAction> actions=parsePhaseActions(phase.get("actions"),id,errors);boolean repeatable=strictBoolean(phase,"repeatable",false,"phase "+id,errors);if(repeatable&&!phase.has("repeat_interval_ticks"))errors.add("repeatable phase "+id+" requires repeat_interval_ticks");if(repeatable&&!phase.has("max_fires"))errors.add("repeatable phase "+id+" requires max_fires");if(!repeatable&&(phase.has("repeat_interval_ticks")||phase.has("max_fires")))errors.add("non-repeatable phase "+id+" cannot configure repeat fields");try{phases.add(new EncounterTemplate.Phase(id,trigger,actions,repeatable,boundedInteger(phase,"repeat_interval_ticks",0,0,12000,"phase "+id,errors),boundedInteger(phase,"max_fires",1,1,64,"phase "+id,errors)));}catch(IllegalArgumentException e){errors.add(e.getMessage());}index++;}return phases;
    }
    private static EncounterTemplate.ObjectiveComposition parseCompletionObjectives(
            JsonObject root, List<String> errors, HolderLookup.Provider registries) {
        if (!root.has("completion_objectives")) return null;
        JsonElement raw = root.get("completion_objectives");
        if (!raw.isJsonObject()) {
            errors.add("completion_objectives must be an object");
            return null;
        }
        JsonObject composition = raw.getAsJsonObject();
        for (String key : composition.keySet())
            if (!List.of("mode", "objectives").contains(key))
                errors.add("unknown completion_objectives field " + key);
        EncounterTemplate.ObjectiveMode mode = strictEnum(
                EncounterTemplate.ObjectiveMode.class, composition, "mode", EncounterTemplate.ObjectiveMode.ALL, errors);
        JsonElement entries = composition.get("objectives");
        if (entries == null || !entries.isJsonArray()) {
            errors.add("completion_objectives.objectives must be an array");
            return null;
        }
        if (entries.getAsJsonArray().size() < 1 || entries.getAsJsonArray().size() > 32)
            errors.add("completion_objectives.objectives must contain between 1 and 32 entries");
        List<EncounterTemplate.Objective> objectives = new ArrayList<>();
        for (JsonElement value : entries.getAsJsonArray()) {
            if (!value.isJsonObject()) {
                errors.add("completion objective must be an object");
                continue;
            }
            JsonObject objective = value.getAsJsonObject();
            for (String key : objective.keySet())
                if (!List.of("id", "type", "duration_ticks", "actor", "point", "actors", "points", "member",
                        "item", "components", "durability", "custom_data", "nbt", "count", "radius",
                        "vertical_radius").contains(key))
                    errors.add("unknown completion objective field " + key);
            String id = string(objective, "id", "");
            EncounterTemplate.ObjectiveType type = strictEnum(
                    EncounterTemplate.ObjectiveType.class, objective, "type", null, errors);
            if (type == null) continue;
            Set<String> reachable = switch (type) {
                case ALL_DEFEATED, ALL_GONE -> Set.of();
                case SURVIVE_DURATION -> Set.of("duration_ticks");
                case PROTECT_ACTOR -> Set.of("actor", "duration_ticks");
                case PREVENT_ENTRY -> Set.of("point", "duration_ticks", "radius", "vertical_radius");
                case ESCORT_ACTOR -> Set.of("actor", "point", "radius", "vertical_radius");
                case DESTROY_TARGETS -> Set.of("actors");
                case DEFEAT_LEADER -> Set.of("member");
                case RETRIEVE_ITEM -> Set.of("item", "components", "durability", "custom_data", "nbt", "count");
                case HOLD_AREAS -> Set.of("points", "duration_ticks", "radius", "vertical_radius");
            };
            for (String key : objective.keySet())
                if (!key.equals("id") && !key.equals("type") && !reachable.contains(key))
                    errors.add("objective " + id + " field " + key + " is unreachable for "
                            + type.name().toLowerCase(Locale.ROOT));
            List<String> actors = stableStrings(objective, "actors", 32, "objective " + id, errors);
            List<String> points = stableStrings(objective, "points", 16, "objective " + id, errors);
            ResourceLocation item = null;
            if (objective.has("item")) {
                String itemValue = string(objective, "item", "");
                item = itemValue.contains(":") ? ResourceLocation.tryParse(itemValue) : null;
                if (item == null || !BuiltInRegistries.ITEM.containsKey(item)
                        || BuiltInRegistries.ITEM.get(item) == net.minecraft.world.item.Items.AIR) {
                    errors.add("objective " + id + " has unknown item " + itemValue);
                    item = null;
                }
            }
            try {
                ItemStackPredicate predicate = ItemStackPredicateParser.parse(
                        registries,
                        objective,
                        item == null ? List.of() : List.of(BuiltInRegistries.ITEM.get(item)),
                        "components",
                        "durability",
                        "custom_data",
                        "nbt");
                objectives.add(new EncounterTemplate.Objective(
                        id,
                        type,
                        boundedInteger(objective, "duration_ticks", 0, 0, 1728000, "objective " + id, errors),
                        string(objective, "actor", ""),
                        string(objective, "point", ""),
                        actors,
                        points,
                        string(objective, "member", ""),
                        item,
                        predicate,
                        boundedInteger(objective, "count", type == EncounterTemplate.ObjectiveType.RETRIEVE_ITEM ? 1 : 0,
                                0, 64, "objective " + id, errors),
                        boundedInteger(objective, "radius",
                                type == EncounterTemplate.ObjectiveType.PREVENT_ENTRY
                                        || type == EncounterTemplate.ObjectiveType.ESCORT_ACTOR
                                        || type == EncounterTemplate.ObjectiveType.HOLD_AREAS ? 4 : 0,
                                0, 64, "objective " + id, errors),
                        boundedInteger(objective, "vertical_radius",
                                type == EncounterTemplate.ObjectiveType.PREVENT_ENTRY
                                        || type == EncounterTemplate.ObjectiveType.ESCORT_ACTOR
                                        || type == EncounterTemplate.ObjectiveType.HOLD_AREAS ? 4 : 0,
                                0, 64, "objective " + id, errors)));
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        try {
            return new EncounterTemplate.ObjectiveComposition(mode, objectives);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            return null;
        }
    }
    private static List<String> stableStrings(JsonObject object,String key,int maximum,String owner,List<String> errors){if(!object.has(key))return List.of();if(!object.get(key).isJsonArray()){errors.add(owner+" "+key+" must be an array");return List.of();}List<String> values=new ArrayList<>();java.util.Set<String> unique=new java.util.LinkedHashSet<>();for(JsonElement raw:object.getAsJsonArray(key)){String value=raw.isJsonPrimitive()?raw.getAsString():"";if(!value.matches("[a-z][a-z0-9_.-]{0,63}"))errors.add(owner+" "+key+" requires stable aliases");else if(!unique.add(value))errors.add(owner+" "+key+" contains duplicate "+value);else values.add(value);}if(values.size()<1||values.size()>maximum)errors.add(owner+" "+key+" must contain between 1 and "+maximum+" entries");return values;}
    private static EncounterTemplate.PhaseTrigger parsePhaseTrigger(JsonElement raw,String phaseId,List<String> errors){
        if(raw==null||!raw.isJsonObject()){errors.add("phase "+phaseId+" trigger must be an object");return null;}JsonObject trigger=raw.getAsJsonObject();for(String key:trigger.keySet())if(!List.of("type","wave","percentage","ticks","member").contains(key))errors.add("unknown phase trigger field "+key);EncounterTemplate.PhaseTriggerType type=strictEnum(EncounterTemplate.PhaseTriggerType.class,trigger,"type",null,errors);if(type==null)return null;for(String key:trigger.keySet())if(!key.equals("type")&&!switch(type){case WAVE_STARTED,WAVE_COMPLETED->key.equals("wave");case REMAINING_PERCENTAGE->key.equals("percentage");case ELAPSED_TIME->key.equals("ticks");case ELITE_DEFEATED->key.equals("member");})errors.add("phase "+phaseId+" trigger field "+key+" is unreachable for "+type.name().toLowerCase(Locale.ROOT));try{return new EncounterTemplate.PhaseTrigger(type,string(trigger,"wave",""),boundedInteger(trigger,"percentage",-1,-1,100,"phase "+phaseId+" trigger",errors),boundedInteger(trigger,"ticks",0,0,1728000,"phase "+phaseId+" trigger",errors),string(trigger,"member",""));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
    }
    private static List<EncounterTemplate.PhaseAction> parsePhaseActions(JsonElement raw,String phaseId,List<String> errors){
        if(raw==null||!raw.isJsonArray()){errors.add("phase "+phaseId+" actions must be an array");return List.of();}if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>32)errors.add("phase "+phaseId+" actions must contain between 1 and 32 entries");List<EncounterTemplate.PhaseAction> actions=new ArrayList<>();for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add("phase "+phaseId+" action must be an object");continue;}JsonObject action=value.getAsJsonObject();for(String key:action.keySet())if(!List.of("id","type","text","scope","tag","key","value","target").contains(key))errors.add("unknown phase action field "+key);String id=string(action,"id","");EncounterTemplate.PhaseActionType type=strictEnum(EncounterTemplate.PhaseActionType.class,action,"type",null,errors);if(type==null)continue;for(String key:action.keySet())if(!key.equals("id")&&!key.equals("type")&&!switch(type){case NOTIFICATION,DIALOGUE->key.equals("text");case FACT->List.of("scope","tag","key","value").contains(key);case TRANSITION->key.equals("target");})errors.add("phase action "+id+" field "+key+" is unreachable for "+type.name().toLowerCase(Locale.ROOT));String tagValue=string(action,"tag","");ResourceLocation tag=action.has("tag")&&tagValue.contains(":")?ResourceLocation.tryParse(tagValue):null;if(action.has("tag")&&tag==null)errors.add("phase fact "+id+" tag must be a namespaced resource location");if(type==EncounterTemplate.PhaseActionType.FACT&&action.has("key")!=action.has("value"))errors.add("phase fact "+id+" requires key and value together");try{actions.add(new EncounterTemplate.PhaseAction(id,type,string(action,"text",""),strictEnum(EncounterTemplate.FactScope.class,action,"scope",EncounterTemplate.FactScope.PLAYER,errors),tag,string(action,"key",""),string(action,"value",""),string(action,"target","")));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}return actions;
    }
    private static List<EncounterTemplate.Wave> parseWaves(JsonObject root,List<String> errors){
        if(!root.has("waves"))return List.of();JsonElement raw=root.get("waves");if(!raw.isJsonArray()){errors.add("waves must be an array");return List.of();}
        if(raw.getAsJsonArray().size()<1||raw.getAsJsonArray().size()>32)errors.add("waves must contain between 1 and 32 entries");
        List<EncounterTemplate.Wave> waves=new ArrayList<>();java.util.Set<String> ids=new java.util.LinkedHashSet<>();int index=0;
        for(JsonElement value:raw.getAsJsonArray()){
            if(!value.isJsonObject()){errors.add("wave["+index+"] must be an object");index++;continue;}JsonObject wave=value.getAsJsonObject();String waveId=string(wave,"id","");
            for(String key:wave.keySet())if(!List.of("id","members","delay_ticks","trigger","boss_bar_title","equipment","scene_actions","dialogue_hook").contains(key))errors.add("unknown wave field "+key);
            if(!ids.add(waveId))errors.add("duplicate wave id "+waveId);Map<EquipmentSlot,EncounterTemplate.Gear> defaults=parseEquipment(wave,errors);
            List<EncounterTemplate.Member> waveMembers=parseMembers(wave.get("members"),defaults,errors,"wave "+waveId);List<EncounterTemplate.WaveHook> hooks=parseWaveHooks(wave,waveId,errors);
            try{waves.add(new EncounterTemplate.Wave(waveId,waveMembers,boundedInteger(wave,"delay_ticks",0,0,12000,"wave "+waveId,errors),strictEnum(EncounterTemplate.WaveTrigger.class,wave,"trigger",EncounterTemplate.WaveTrigger.ALL_DEFEATED,errors),string(wave,"boss_bar_title",""),hooks));}catch(IllegalArgumentException e){errors.add(e.getMessage());}index++;
        }return waves;
    }
    private static List<EncounterTemplate.Member> parseMembers(JsonElement raw,Map<EquipmentSlot,EncounterTemplate.Gear> defaults,List<String> errors,String owner){
        List<EncounterTemplate.Member> members=new ArrayList<>();if(raw==null)return members;if(!raw.isJsonArray()){errors.add(owner+" members must be an array");return members;}
        for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add(owner+" member must be an object");continue;}JsonObject member=value.getAsJsonObject();for(String key:member.keySet())if(!List.of("id","entity","count","equipment","custom_name","name_visible","glowing","persistent","health","movement_speed","attack_damage","armor","knockback_resistance","attributes","boss","boss_bar_color","boss_bar_overlay").contains(key))errors.add("unknown encounter member field "+key);ResourceLocation entity=ResourceLocation.tryParse(string(member,"entity",""));if(entity==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)){errors.add("unknown entity type "+entity);continue;}Map<EquipmentSlot,EncounterTemplate.Gear> equipment=new LinkedHashMap<>(defaults);equipment.putAll(parseEquipment(member,errors));members.add(new EncounterTemplate.Member(entity,boundedInteger(member,"count",1,1,64,owner+" member",errors),equipment,parseMobOptions(member,owner,errors),string(member,"id","")));}
        return members;
    }
    private static EncounterTemplate.MobOptions parseMobOptions(JsonObject member,String owner,List<String> errors){
        String customName=string(member,"custom_name","");if(member.has("custom_name")&&customName.isBlank())errors.add(owner+" custom_name must not be blank");if(customName.length()>128)errors.add(owner+" custom_name exceeds 128 characters");
        boolean nameVisible=strictBoolean(member,"name_visible",false,owner,errors);boolean glowing=strictBoolean(member,"glowing",false,owner,errors);boolean persistent=strictBoolean(member,"persistent",false,owner,errors);boolean boss=strictBoolean(member,"boss",false,owner,errors);
        if(nameVisible&&customName.isBlank())errors.add(owner+" name_visible requires custom_name");if(!boss&&(member.has("boss_bar_color")||member.has("boss_bar_overlay")))errors.add(owner+" boss-bar presentation requires boss true");
        EncounterTemplate.BossColor color=strictEnum(EncounterTemplate.BossColor.class,member,"boss_bar_color",EncounterTemplate.BossColor.RED,errors);EncounterTemplate.BossOverlay overlay=strictEnum(EncounterTemplate.BossOverlay.class,member,"boss_bar_overlay",EncounterTemplate.BossOverlay.PROGRESS,errors);
        Map<ResourceLocation,Double> attributes=new LinkedHashMap<>();JsonElement raw=member.get("attributes");if(raw!=null){if(!raw.isJsonObject())errors.add(owner+" attributes must be an object");else for(var entry:raw.getAsJsonObject().entrySet()){ResourceLocation id=ResourceLocation.tryParse(entry.getKey());if(id==null||!MOB_ATTRIBUTES.containsKey(id)){errors.add("unknown or unsafe encounter attribute "+entry.getKey());continue;}Double value=strictDecimal(entry.getValue(),owner+" attribute "+id,errors);if(value!=null)putAttribute(attributes,id,value,owner,errors);}}
        Map<String,ResourceLocation> aliases=Map.of("health",ResourceLocation.parse("minecraft:max_health"),"movement_speed",ResourceLocation.parse("minecraft:movement_speed"),"attack_damage",ResourceLocation.parse("minecraft:attack_damage"),"armor",ResourceLocation.parse("minecraft:armor"),"knockback_resistance",ResourceLocation.parse("minecraft:knockback_resistance"));
        for(var alias:aliases.entrySet())if(member.has(alias.getKey())){if(attributes.containsKey(alias.getValue()))errors.add(owner+" duplicates attribute "+alias.getValue()+" through "+alias.getKey());Double value=strictDecimal(member.get(alias.getKey()),owner+" "+alias.getKey(),errors);if(value!=null)putAttribute(attributes,alias.getValue(),value,owner,errors);}
        return new EncounterTemplate.MobOptions(customName,nameVisible,glowing,persistent,attributes,boss,color,overlay);
    }
    private static void putAttribute(Map<ResourceLocation,Double> attributes,ResourceLocation id,double value,String owner,List<String> errors){double[] bounds=MOB_ATTRIBUTES.get(id);if(value<bounds[0]||value>bounds[1])errors.add(owner+" attribute "+id+" must be between "+bounds[0]+" and "+bounds[1]);else attributes.put(id,value);}
    private static Double strictDecimal(JsonElement raw,String owner,List<String> errors){try{double value=raw.getAsDouble();if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(RuntimeException e){errors.add(owner+" must be a finite number");return null;}}
    private static boolean strictBoolean(JsonObject object,String key,boolean fallback,String owner,List<String> errors){if(!object.has(key))return fallback;try{JsonElement raw=object.get(key);if(!raw.isJsonPrimitive()||!raw.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();return raw.getAsBoolean();}catch(RuntimeException e){errors.add(owner+" "+key+" must be a boolean");return fallback;}}
    private static List<EncounterTemplate.WaveHook> parseWaveHooks(JsonObject wave,String waveId,List<String> errors){
        List<EncounterTemplate.WaveHook> hooks=new ArrayList<>();java.util.Set<String> ids=new java.util.LinkedHashSet<>();JsonElement raw=wave.get("scene_actions");if(raw!=null){if(!raw.isJsonArray())errors.add("wave "+waveId+" scene_actions must be an array");else for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject()){errors.add("wave scene action must be an object");continue;}JsonObject hook=value.getAsJsonObject();for(String key:hook.keySet())if(!List.of("id","type","text").contains(key))errors.add("unknown wave scene action field "+key);String id=string(hook,"id","");if(!ids.add(id))errors.add("duplicate wave hook id "+id);EncounterTemplate.HookType type=strictEnum(EncounterTemplate.HookType.class,hook,"type",null,errors);try{hooks.add(new EncounterTemplate.WaveHook(id,type,string(hook,"text","")));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}}
        if(wave.has("dialogue_hook")){if(!wave.get("dialogue_hook").isJsonObject())errors.add("wave "+waveId+" dialogue_hook must be an object");else{JsonObject hook=wave.getAsJsonObject("dialogue_hook");for(String key:hook.keySet())if(!List.of("id","text").contains(key))errors.add("unknown wave dialogue hook field "+key);String id=string(hook,"id","");if(!ids.add(id))errors.add("duplicate wave hook id "+id);try{hooks.add(new EncounterTemplate.WaveHook(id,EncounterTemplate.HookType.DIALOGUE,string(hook,"text","")));}catch(IllegalArgumentException e){errors.add(e.getMessage());}}}return hooks;
    }
    private static EncounterTemplate.Area parseArea(JsonObject root,List<String> errors){
        if(!root.has("area"))return null;
        if(!root.get("area").isJsonObject()){errors.add("area must be an object");return null;}
        JsonObject area=root.getAsJsonObject("area");
        for(String key:area.keySet())if(!List.of("radius","vertical_radius","leave_behavior","leave_timeout_ticks","mob_behavior","mob_timeout_ticks").contains(key))errors.add("unknown area field "+key);
        if(!area.has("radius")){errors.add("area.radius is required");return null;}
        Integer radius=strictInteger(area,"radius",errors);Integer vertical=strictInteger(area,"vertical_radius",errors);
        Integer leaveTimeout=strictInteger(area,"leave_timeout_ticks",errors);Integer mobTimeout=strictInteger(area,"mob_timeout_ticks",errors);
        EncounterTemplate.LeaveBehavior leave=strictEnum(EncounterTemplate.LeaveBehavior.class,area,"leave_behavior",EncounterTemplate.LeaveBehavior.IGNORE,errors);
        EncounterTemplate.MobBehavior mob=strictEnum(EncounterTemplate.MobBehavior.class,area,"mob_behavior",EncounterTemplate.MobBehavior.IGNORE,errors);
        if(area.has("mob_timeout_ticks")&&mob!=EncounterTemplate.MobBehavior.TELEPORT)errors.add("area.mob_timeout_ticks requires mob_behavior teleport");
        if(radius==null)return null;
        try{return new EncounterTemplate.Area(radius,vertical==null?Math.min(radius,128):vertical,leave,
                leaveTimeout==null?200:leaveTimeout,mob,mobTimeout==null?200:mobTimeout);}
        catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
    }
    private static Integer strictInteger(JsonObject object,String key,List<String> errors){
        if(!object.has(key))return null;
        try{double value=object.get(key).getAsDouble();if(!Double.isFinite(value)||value!=Math.rint(value))throw new NumberFormatException();return (int)value;}catch(RuntimeException e){errors.add("area."+key+" must be an integer");return null;}
    }
    private static <E extends Enum<E>>E strictEnum(Class<E> type,JsonObject object,String key,E fallback,List<String> errors){
        if(!object.has(key))return fallback;String value=string(object,key,"");
        try{return Enum.valueOf(type,value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){errors.add("unknown "+key+" '"+value+"'");return fallback;}
    }
    private static Map<EquipmentSlot,EncounterTemplate.Gear> parseEquipment(JsonObject member,List<String> errors){
        if(!member.has("equipment"))return Map.of();JsonElement raw=member.get("equipment");
        if(!raw.isJsonObject()){errors.add("member equipment must be an object keyed by slot");return Map.of();}
        Map<EquipmentSlot,EncounterTemplate.Gear> equipment=new LinkedHashMap<>();
        for(var entry:raw.getAsJsonObject().entrySet()){
            EquipmentSlot slot=equipmentSlot(entry.getKey());
            if(slot==null){errors.add("unknown equipment slot "+entry.getKey());continue;}
            if(!entry.getValue().isJsonObject()){errors.add("equipment "+entry.getKey()+" must be an object");continue;}
            JsonObject gear=entry.getValue().getAsJsonObject();ResourceLocation item=ResourceLocation.tryParse(string(gear,"item",""));
            if(item==null||!BuiltInRegistries.ITEM.containsKey(item)){errors.add("unknown equipment item "+item);continue;}
            Map<ResourceLocation,Integer> enchantments=new LinkedHashMap<>();
            if(gear.has("enchantments")){
                if(!gear.get("enchantments").isJsonObject())errors.add("equipment enchantments must be an object");
                else for(var enchantment:gear.getAsJsonObject("enchantments").entrySet()){
                    ResourceLocation enchantmentId=ResourceLocation.tryParse(enchantment.getKey());int level;
                    try{level=enchantment.getValue().getAsInt();}catch(RuntimeException e){level=0;}
                    if(enchantmentId==null||level<1||level>255)errors.add("invalid enchantment "+enchantment.getKey()+" level "+level);
                    else enchantments.put(enchantmentId,level);
                }
            }
            equipment.put(slot,new EncounterTemplate.Gear(item,integer(gear,"count",1),enchantments,decimal(gear,"drop_chance",0.0F)));
        }
        return equipment;
    }
    private static EquipmentSlot equipmentSlot(String value){return switch(value.toLowerCase(Locale.ROOT)){case "mainhand","main_hand"->EquipmentSlot.MAINHAND;case "offhand","off_hand"->EquipmentSlot.OFFHAND;case "head"->EquipmentSlot.HEAD;case "chest"->EquipmentSlot.CHEST;case "legs"->EquipmentSlot.LEGS;case "feet"->EquipmentSlot.FEET;case "body"->EquipmentSlot.BODY;default->null;};}
    private static void validateVariantGraph(Map<ResourceLocation,EncounterTemplate> templates,Map<ResourceLocation,List<String>> diagnostics){Set<ResourceLocation> invalid=new java.util.LinkedHashSet<>();for(EncounterTemplate template:List.copyOf(templates.values())){if(!template.variantSelector())continue;List<String> errors=new ArrayList<>();validateVariantPath(template.id(),templates,new java.util.LinkedHashSet<>(),errors);if(!errors.isEmpty()){List<String> merged=new ArrayList<>(diagnostics.getOrDefault(template.id(),List.of()));merged.addAll(errors.stream().distinct().toList());diagnostics.put(template.id(),List.copyOf(merged));invalid.add(template.id());}}invalid.forEach(templates::remove);}
    private static void validateVariantPath(ResourceLocation id,Map<ResourceLocation,EncounterTemplate> templates,java.util.LinkedHashSet<ResourceLocation> path,List<String> errors){if(!path.add(id)){errors.add("recursive encounter variant reference "+String.join(" -> ",path.stream().map(ResourceLocation::toString).toList())+" -> "+id);return;}EncounterTemplate template=templates.get(id);if(template==null){errors.add("unknown encounter variant template "+id);path.remove(id);return;}if(path.size()>32&&template.variantSelector()){errors.add("encounter variant chain exceeds 32 selectors at "+id);path.remove(id);return;}for(EncounterTemplate.Variant variant:template.variants())validateVariantPath(variant.template(),templates,path,errors);path.remove(id);}
    public static long variantSeed(UUID sceneId,String operationId){long value=sceneId.getMostSignificantBits()^Long.rotateLeft(sceneId.getLeastSignificantBits(),23)^0x9E3779B97F4A7C15L;for(int i=0;i<operationId.length();i++)value=(value^operationId.charAt(i))*0x100000001B3L;return mix(value);}
    public static EncounterTemplate.Variant selectVariant(List<EncounterTemplate.Variant> variants,long seed,String selector){if(variants==null||variants.isEmpty())return null;long total=variants.stream().mapToLong(EncounterTemplate.Variant::weight).sum();long roll=Math.floorMod(mix(seed^stableHash(selector)),total);for(EncounterTemplate.Variant variant:variants){roll-=variant.weight();if(roll<0)return variant;}return variants.getLast();}
    public static VariantResolution resolve(MinecraftServer server,ResourceLocation source,long seed){Cache value=load(server);EncounterTemplate root=value.templates.get(source);if(root==null)return new VariantResolution("",source,null,seed,"unknown encounter template "+source);String selected="";EncounterTemplate current=root;Set<ResourceLocation> path=new java.util.LinkedHashSet<>();for(int depth=0;depth<32&&current.variantSelector();depth++){if(!path.add(current.id()))return new VariantResolution(selected,current.id(),null,seed,"recursive encounter variant reference "+current.id());EncounterTemplate.Variant choice=selectVariant(current.variants(),seed,current.id().toString());if(choice==null)return new VariantResolution(selected,current.id(),null,seed,"encounter variant selector "+current.id()+" is empty");if(selected.isBlank())selected=choice.id();current=value.templates.get(choice.template());if(current==null)return new VariantResolution(selected,choice.template(),null,seed,"unknown encounter variant template "+choice.template());}if(current.variantSelector())return new VariantResolution(selected,current.id(),null,seed,"encounter variant chain exceeds 32 selectors");return new VariantResolution(selected,current.id(),current,seed,"");}
    private static long stableHash(String value){long hash=0xCBF29CE484222325L;for(int i=0;i<value.length();i++)hash=(hash^value.charAt(i))*0x100000001B3L;return hash;}
    private static long mix(long value){value^=value>>>30;value*=0xBF58476D1CE4E5B9L;value^=value>>>27;value*=0x94D049BB133111EBL;return value^(value>>>31);}
    public record VariantResolution(String selectedVariantId,ResourceLocation resolvedTemplateId,EncounterTemplate template,long seed,String diagnostic){public boolean valid(){return template!=null&&diagnostic.isBlank();}}
    private static ResourceLocation parseId(ResourceLocation source,String value){return value.contains(":")?ResourceLocation.tryParse(value):value.isBlank()?null:ResourceLocation.fromNamespaceAndPath(source.getNamespace(),value);}
    private static int boundedInteger(JsonObject object,String key,int fallback,int minimum,int maximum,String owner,List<String> errors){if(!object.has(key))return fallback;try{double raw=object.get(key).getAsDouble();if(!Double.isFinite(raw)||raw!=Math.rint(raw)||raw<minimum||raw>maximum)throw new NumberFormatException();return (int)raw;}catch(RuntimeException e){errors.add(owner+" "+key+" must be an integer between "+minimum+" and "+maximum);return fallback;}}
    private static String string(JsonObject o,String k,String f){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():f;}private static int integer(JsonObject o,String k,int f){try{return o.has(k)?o.get(k).getAsInt():f;}catch(RuntimeException e){return f;}}private static float decimal(JsonObject o,String k,float f){try{return o.has(k)?o.get(k).getAsFloat():f;}catch(RuntimeException e){return f;}}private static boolean bool(JsonObject o,String k,boolean f){try{return o.has(k)?o.get(k).getAsBoolean():f;}catch(RuntimeException e){return f;}}private static <E extends Enum<E>>E enumValue(Class<E> t,String v,E f){try{return Enum.valueOf(t,v.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return f;}}
    private record Cache(MinecraftServer server,Map<ResourceLocation,EncounterTemplate> templates,Map<ResourceLocation,List<String>> diagnostics){}
}
