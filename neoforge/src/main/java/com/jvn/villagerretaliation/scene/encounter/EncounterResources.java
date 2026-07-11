package com.jvn.villagerretaliation.scene.encounter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class EncounterResources {
    public static final ResourceLocation SCHEMA=VillagerRetaliation.id("encounter/v1");
    private static volatile Cache cache=new Cache(null,Map.of(),Map.of());
    private EncounterResources(){}
    public static Optional<EncounterTemplate> template(MinecraftServer server,ResourceLocation id){return Optional.ofNullable(load(server).templates.get(id));}
    public static List<EncounterTemplate> templates(MinecraftServer server){return List.copyOf(load(server).templates.values());}
    public static Map<ResourceLocation,List<String>> diagnostics(MinecraftServer server){return load(server).diagnostics;}
    public static void warm(MinecraftServer server){load(server);}public static void clearCache(){cache=new Cache(null,Map.of(),Map.of());}
    public static void installTestTemplates(MinecraftServer server,List<EncounterTemplate> templates){Map<ResourceLocation,EncounterTemplate> map=new LinkedHashMap<>();templates.stream().sorted(Comparator.comparing(v->v.id().toString())).forEach(v->map.put(v.id(),v));cache=new Cache(server,Map.copyOf(map),Map.of());}
    private static Cache load(MinecraftServer server){Cache value=cache;if(value.server==server)return value;synchronized(EncounterResources.class){if(cache.server==server)return cache;Map<ResourceLocation,EncounterTemplate> templates=new LinkedHashMap<>();Map<ResourceLocation,List<String>> diagnostics=new LinkedHashMap<>();for(var resource:DatapackResourceLoader.jsonResources(server,"quest_encounters")){JsonObject root=DatapackResourceLoader.readObject(resource.location(),"quest encounter",resource.resource()).orElse(null);List<String> errors=new ArrayList<>();EncounterTemplate template=parse(resource.location(),root,errors);ResourceLocation key=template==null?resource.location():template.id();diagnostics.put(key,List.copyOf(errors));if(template!=null&&errors.isEmpty())templates.put(template.id(),template);}cache=new Cache(server,Map.copyOf(templates),Map.copyOf(diagnostics));return cache;}}
    public static EncounterTemplate parse(ResourceLocation source,JsonObject root,List<String> errors){if(root==null){errors.add("root must be an object");return null;}ResourceLocation schema=ResourceLocation.tryParse(string(root,"schema",""));if(!SCHEMA.equals(schema))errors.add("schema must be "+SCHEMA);ResourceLocation id=parseId(source,string(root,"id",""));ResourceLocation controller=parseId(source,string(root,"controller","villagerretaliation:controlled"));if(controller==null||VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES.get(controller).isEmpty())errors.add("unknown encounter controller "+controller);List<EncounterTemplate.Member> members=new ArrayList<>();JsonElement raw=root.get("members");if(raw==null||!raw.isJsonArray())errors.add("members must be an array");else for(JsonElement value:raw.getAsJsonArray()){if(!value.isJsonObject())continue;ResourceLocation entity=ResourceLocation.tryParse(string(value.getAsJsonObject(),"entity",""));if(entity==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)){errors.add("unknown entity type "+entity);continue;}members.add(new EncounterTemplate.Member(entity,integer(value.getAsJsonObject(),"count",1)));}if(id==null)errors.add("valid namespaced id is required");if(members.isEmpty())errors.add("at least one valid member is required");if(id==null||controller==null||members.isEmpty())return null;try{return new EncounterTemplate(id,integer(root,"version",1),controller,members,integer(root,"extra_per_player",0),integer(root,"max_party_size",4),integer(root,"placement_attempts",16),integer(root,"spawn_radius",8),enumValue(EncounterTemplate.RespawnPolicy.class,string(root,"respawn_policy","never"),EncounterTemplate.RespawnPolicy.NEVER),enumValue(EncounterTemplate.CleanupPolicy.class,string(root,"cleanup_policy","remove_survivors"),EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS),enumValue(EncounterTemplate.CompletionCondition.class,string(root,"completion_condition","all_defeated"),EncounterTemplate.CompletionCondition.ALL_DEFEATED));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}}
    private static ResourceLocation parseId(ResourceLocation source,String value){return value.contains(":")?ResourceLocation.tryParse(value):value.isBlank()?null:ResourceLocation.fromNamespaceAndPath(source.getNamespace(),value);}
    private static String string(JsonObject o,String k,String f){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():f;}private static int integer(JsonObject o,String k,int f){try{return o.has(k)?o.get(k).getAsInt():f;}catch(RuntimeException e){return f;}}private static <E extends Enum<E>>E enumValue(Class<E> t,String v,E f){try{return Enum.valueOf(t,v.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return f;}}
    private record Cache(MinecraftServer server,Map<ResourceLocation,EncounterTemplate> templates,Map<ResourceLocation,List<String>> diagnostics){}
}
