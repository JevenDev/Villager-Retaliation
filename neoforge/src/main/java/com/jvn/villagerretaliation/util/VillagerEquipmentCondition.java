package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.world.entity.npc.AbstractVillager;

public record VillagerEquipmentCondition(boolean requiresUnarmed, boolean requiresArmed) {
    private static final VillagerEquipmentCondition EMPTY = new VillagerEquipmentCondition(false, false);

    public static VillagerEquipmentCondition empty() {
        return EMPTY;
    }

    public static VillagerEquipmentCondition read(JsonObject entry) {
        return read(entry, "villager");
    }

    public static VillagerEquipmentCondition read(JsonObject entry, String subject) {
        boolean requiresUnarmed = readBoolean(entry, "requires_" + subject + "_unarmed")
                || readBoolean(entry, subject + "_unarmed");
        boolean requiresArmed = readBoolean(entry, "requires_" + subject + "_armed")
                || readBoolean(entry, subject + "_armed");
        if (!requiresUnarmed && !requiresArmed) {
            return EMPTY;
        }
        return new VillagerEquipmentCondition(requiresUnarmed, requiresArmed);
    }

    public boolean matches(AbstractVillager villager) {
        if (villager == null) {
            return isEmpty();
        }
        boolean hasUsableWeapon = VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager);
        if (this.requiresUnarmed && hasUsableWeapon) {
            return false;
        }
        return !this.requiresArmed || hasUsableWeapon;
    }

    public boolean isEmpty() {
        return !this.requiresUnarmed && !this.requiresArmed;
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
