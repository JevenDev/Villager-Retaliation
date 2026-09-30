package com.jvn.villagerretaliation.interaction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerWorkAppearance {
    private VillagerWorkAppearance() { }

    public static void refresh(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)
                || !(villager instanceof VillagerWorkAppearanceHolder holder)) return;
        holder.villagerretaliation$assignedWorkRole(villager.isBaby() ? null : VillagerWorkAuthority.assignedRole(level, villager));
    }

    public static VillagerProfession profession(AbstractVillager villager) {
        return villager.isBaby() || !(villager instanceof VillagerWorkAppearanceHolder holder)
                ? VillagerProfession.NONE : profession(holder.villagerretaliation$assignedWorkRole());
    }

    public static VillagerProfession profession(HiredVillagerRole role) {
        if (role == null) return VillagerProfession.NONE;
        return switch (role) {
            case FARMING -> VillagerProfession.FARMER;
            case FISHING -> VillagerProfession.FISHERMAN;
            case BREWING -> VillagerProfession.CLERIC;
            case CRAFTSMAN, MINING -> VillagerProfession.TOOLSMITH;
            case BUILDER -> VillagerProfession.MASON;
            case ANIMAL_HANDLING -> VillagerProfession.SHEPHERD;
            case COOK -> VillagerProfession.BUTCHER;
            case SMELTER, COMBAT -> VillagerProfession.ARMORER;
            case LOGGING -> VillagerProfession.FLETCHER;
            case HUNTING -> VillagerProfession.LEATHERWORKER;
            case COURIER -> VillagerProfession.CARTOGRAPHER;
            case NITWIT -> VillagerProfession.LIBRARIAN;
        };
    }
}
