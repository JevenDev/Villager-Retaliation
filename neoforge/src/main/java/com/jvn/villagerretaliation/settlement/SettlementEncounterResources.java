package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

final class SettlementEncounterResources {
    private static final Map<ResourceManager, List<SettlementEncounterDefinition>> CACHE = new WeakHashMap<>();
    private SettlementEncounterResources() { }
    static List<SettlementEncounterDefinition> get(MinecraftServer server) {
        return CACHE.computeIfAbsent(server.getResourceManager(), manager -> load(server, manager));
    }
    static List<SettlementEncounterDefinition> load(MinecraftServer server, ResourceManager manager) {
        var result = new java.util.ArrayList<SettlementEncounterDefinition>();
        var entries = manager.listResources("journey_encounters", id -> id.getPath().endsWith(".json"));
        if (entries.size() > 512) LogUtils.getLogger().error("Journey encounter limit exceeded: {} definitions, only the first 512 sorted identifiers are loaded", entries.size());
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).limit(512).forEach(entry -> {
                    var path = entry.getKey();
                    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(path.getNamespace(),
                            path.getPath().substring("journey_encounters/".length(), path.getPath().length() - 5));
                    try (var reader = entry.getValue().openAsReader()) {
                        var definition = SettlementEncounterDefinition.parse(id, JsonParser.parseReader(reader).getAsJsonObject());
                        for (var attacker : definition.attackers()) {
                            var entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(attacker.entity()).create(server.overworld());
                            if (!(entity instanceof net.minecraft.world.entity.Mob)) throw new IllegalArgumentException("attacker is not a mob: " + attacker.entity());
                            for (var equipment : attacker.equipment().entrySet()) net.minecraft.world.item.ItemStack.STRICT_CODEC
                                    .parse(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), equipment.getValue())
                                    .getOrThrow(message -> new IllegalArgumentException("invalid " + equipment.getKey() + " equipment: " + message));
                        }
                        result.add(definition);
                    } catch (java.io.IOException | IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException failure) {
                        LogUtils.getLogger().error("Invalid journey encounter {}, definition disabled: {}", path, failure.getMessage());
                    }
                });
        return List.copyOf(result);
    }
}
