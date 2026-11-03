package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

public final class SettlementEncounterActions {
    public interface Action {
        void validate(JsonObject parameters);
        void apply(ServerLevel level, SettlementFoodRequest participant, JsonObject parameters);
    }
    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();
    static {
        register("lose_items", new Action() {
            public void validate(JsonObject json) {
                SettlementEncounterDefinition.keys(json, "type", "fraction");
                SettlementEncounterDefinition.number(json, "fraction", 0, 0, 1);
            }
            public void apply(ServerLevel level, SettlementFoodRequest participant, JsonObject json) {
                var villager = SettlementJourneyEncounters.context(level, participant.journey);
                if (villager == null) return;
                var items = com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(villager);
                double fraction = SettlementEncounterDefinition.number(json, "fraction", 0, 0, 1);
                boolean changed = false;
                for (var item : items) if (!item.isEmpty() && !SettlementEncounterRecovery.protectedItem(item)) {
                    int count = (int) Math.floor(item.getCount() * fraction);
                    if (count > 0) { item.shrink(count); changed = true; }
                }
                if (changed) {
                    com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(villager, items);
                    SettlementCourierService.capture(participant.journey, villager);
                }
            }
        });
        register("message", new Action() {
            public void validate(JsonObject json) {
                SettlementEncounterDefinition.keys(json, "type", "key");
                if (SettlementEncounterDefinition.string(json, "key", "").isEmpty()) throw new IllegalArgumentException("message key required");
            }
            public void apply(ServerLevel level, SettlementFoodRequest participant, JsonObject json) {
                SettlementJourneyEncounters.report(level, participant, net.minecraft.network.chat.Component.translatable(json.get("key").getAsString()));
            }
        });
    }
    private SettlementEncounterActions() { }
    public static void register(String id, Action action) {
        if (id == null || id.isBlank() || action == null || ACTIONS.putIfAbsent(id, action) != null)
            throw new IllegalArgumentException("Duplicate or invalid journey outcome action " + id);
    }
    static Action get(JsonObject json) {
        String type = SettlementEncounterDefinition.string(json, "type", "");
        var action = ACTIONS.get(type);
        if (action == null) throw new IllegalArgumentException("Unknown journey outcome action " + type);
        return action;
    }
}
