package com.jvn.villagerretaliation.settlement;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/** One replaceable data-pack document. Cached against resource-manager identity (including /reload). */
final class AutonomousVillageRules {
    private static ResourceManager resources;
    private static Rules cached;
    static final ResourceLocation FILE=ResourceLocation.parse("villagerretaliation:autonomous_villages/default.json");
    record Rules(boolean enabled,int cadence,int sustained,int population,int projects,int expansions,int expansionPrice,
                 int reserve,int dailyBudget,int incomeInterval,int incomeCap,int fundsCap,int foodPrice,Map<SettlementAreaType,ResourceLocation> catalog) {}
    static Rules get(MinecraftServer server) {
        if(resources==server.getResourceManager() && cached!=null) return cached;
        resources=server.getResourceManager();
        try(var reader=resources.getResourceOrThrow(FILE).openAsReader()) { cached=parse(JsonParser.parseReader(reader).getAsJsonObject()); }
        catch(Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("Invalid autonomous village rules; autonomous planning disabled",failure);
            cached=new Rules(false,1200,3,16,8,4,32,8,16,2400,8,256,2,Map.of());
        }
        return cached;
    }
    static Rules parse(JsonObject json) {
        var catalog=new EnumMap<SettlementAreaType,ResourceLocation>(SettlementAreaType.class);
        json.getAsJsonObject("catalog").entrySet().forEach(entry -> catalog.put(SettlementAreaType.valueOf(entry.getKey()),ResourceLocation.parse(entry.getValue().getAsString())));
        return new Rules(json.get("enabled").getAsBoolean(),integer(json,"cadence_ticks",200,24000),integer(json,"sustained_observations",2,100),
                integer(json,"population_target",1,128),integer(json,"project_limit",0,32),integer(json,"expansion_limit",0,64),
                integer(json,"expansion_price",1,1000000),integer(json,"reserve",0,1000000),integer(json,"daily_budget",0,1000000),
                integer(json,"income_interval",200,24000),integer(json,"daily_income_cap",0,128),integer(json,"funds_cap",0,1000000),integer(json,"food_unit_price_limit",1,1000000),Map.copyOf(catalog));
    }
    private static int integer(JsonObject json,String key,int min,int max) {
        int value=json.get(key).getAsInt(); if(value<min||value>max) throw new IllegalArgumentException(key); return value;
    }
}
