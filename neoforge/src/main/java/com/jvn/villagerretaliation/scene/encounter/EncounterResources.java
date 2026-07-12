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
import net.minecraft.world.entity.EquipmentSlot;

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
    public static EncounterTemplate parse(ResourceLocation source,JsonObject root,List<String> errors){
        if(root==null){errors.add("root must be an object");return null;}
        ResourceLocation schema=ResourceLocation.tryParse(string(root,"schema",""));if(!SCHEMA.equals(schema))errors.add("schema must be "+SCHEMA);
        ResourceLocation id=parseId(source,string(root,"id",""));
        ResourceLocation controller=parseId(source,string(root,"controller","villagerretaliation:controlled"));
        if(controller==null||VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES.get(controller).isEmpty())errors.add("unknown encounter controller "+controller);
        List<EncounterTemplate.Member> members=new ArrayList<>();JsonElement raw=root.get("members");
        if(raw==null||!raw.isJsonArray())errors.add("members must be an array");
        else for(JsonElement value:raw.getAsJsonArray()){
            if(!value.isJsonObject()){errors.add("encounter member must be an object");continue;}
            JsonObject member=value.getAsJsonObject();ResourceLocation entity=ResourceLocation.tryParse(string(member,"entity",""));
            if(entity==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)){errors.add("unknown entity type "+entity);continue;}
            members.add(new EncounterTemplate.Member(entity,integer(member,"count",1),parseEquipment(member,errors)));
        }
        if(id==null)errors.add("valid namespaced id is required");if(members.isEmpty())errors.add("at least one valid member is required");
        if(id==null||controller==null||members.isEmpty())return null;
        try{return new EncounterTemplate(id,integer(root,"version",1),controller,members,integer(root,"extra_per_player",0),
                integer(root,"max_party_size",4),integer(root,"placement_attempts",16),integer(root,"spawn_radius",8),
                enumValue(EncounterTemplate.RespawnPolicy.class,string(root,"respawn_policy","never"),EncounterTemplate.RespawnPolicy.NEVER),
                enumValue(EncounterTemplate.CleanupPolicy.class,string(root,"cleanup_policy","remove_survivors"),EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS),
                enumValue(EncounterTemplate.CompletionCondition.class,string(root,"completion_condition","all_defeated"),EncounterTemplate.CompletionCondition.ALL_DEFEATED),
                enumValue(EncounterTemplate.SpawnMode.class,string(root,"spawn_mode","group"),EncounterTemplate.SpawnMode.GROUP),
                integer(root,"wave_count",1),integer(root,"wave_interval_ticks",100),
                enumValue(EncounterTemplate.WaveTrigger.class,string(root,"wave_trigger","all_defeated"),EncounterTemplate.WaveTrigger.ALL_DEFEATED),
                bool(root,"boss_bar",true),string(root,"location_message",""),parseArea(root,errors));}catch(IllegalArgumentException e){errors.add(e.getMessage());return null;}
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
        try{return Enum.valueOf(type,value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){errors.add("unknown area."+key+" '"+value+"'");return fallback;}
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
    private static ResourceLocation parseId(ResourceLocation source,String value){return value.contains(":")?ResourceLocation.tryParse(value):value.isBlank()?null:ResourceLocation.fromNamespaceAndPath(source.getNamespace(),value);}
    private static String string(JsonObject o,String k,String f){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():f;}private static int integer(JsonObject o,String k,int f){try{return o.has(k)?o.get(k).getAsInt():f;}catch(RuntimeException e){return f;}}private static float decimal(JsonObject o,String k,float f){try{return o.has(k)?o.get(k).getAsFloat():f;}catch(RuntimeException e){return f;}}private static boolean bool(JsonObject o,String k,boolean f){try{return o.has(k)?o.get(k).getAsBoolean():f;}catch(RuntimeException e){return f;}}private static <E extends Enum<E>>E enumValue(Class<E> t,String v,E f){try{return Enum.valueOf(t,v.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return f;}}
    private record Cache(MinecraftServer server,Map<ResourceLocation,EncounterTemplate> templates,Map<ResourceLocation,List<String>> diagnostics){}
}
