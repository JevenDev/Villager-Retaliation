package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;

public enum SettlementAreaType {
    HOME(null),
    FARM(HiredVillagerRole.FARMING),
    FISHERY(HiredVillagerRole.FISHING),
    HUNTING_GROUNDS(HiredVillagerRole.HUNTING),
    FORESTRY(HiredVillagerRole.LOGGING),
    MINE(HiredVillagerRole.MINING),
    ANIMAL_PEN(HiredVillagerRole.ANIMAL_HANDLING),
    KITCHEN(HiredVillagerRole.COOK),
    WORKSHOP(HiredVillagerRole.CRAFTSMAN),
    SMELTERY(HiredVillagerRole.SMELTER),
    BREWERY(HiredVillagerRole.BREWING),
    DEPOT(HiredVillagerRole.COURIER),
    TRADING_POST(HiredVillagerRole.NITWIT),
    GUARD_POST(HiredVillagerRole.COMBAT),
    CONSTRUCTION(HiredVillagerRole.BUILDER);

    private final HiredVillagerRole role;

    SettlementAreaType(HiredVillagerRole role) {
        this.role = role;
    }

    public HiredVillagerRole role() {
        return this.role;
    }

    boolean usesFields() {
        return this == FARM || this == ANIMAL_PEN;
    }

    boolean changesTerrain() {
        return this == FARM || this == ANIMAL_PEN || this == FORESTRY || this == MINE || this == CONSTRUCTION;
    }

    public boolean foodProduction() {
        return this == FARM || this == FISHERY || this == HUNTING_GROUNDS || this == KITCHEN;
    }
}
