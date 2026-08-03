package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.resources.ResourceLocation;

public enum DuelLoadout {
    BARE_HANDED("bare_handed"),
    MELEE("melee"),
    RANGED("ranged"),
    ARMORED("armored"),
    BRING_YOUR_OWN("bring_your_own");

    private final ResourceLocation id;

    DuelLoadout(String path) {
        this.id = VillagerRetaliation.id(path);
    }

    public ResourceLocation id() {
        return this.id;
    }
}

