package com.jvn.commonfolk.loot;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.util.CommonfolkLootUtil;
import com.jvn.commonfolk.util.CommonfolkRandomUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerLootHandler {
    private VillagerLootHandler() {
    }

    public static void addDrops(Villager villager, LivingDropsEvent event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_DROPS.get() || villager.isBaby()) {
            return;
        }

        RandomSource random = villager.getRandom();
        if (CommonfolkRandomUtil.chance(random, CommonfolkConfig.VILLAGER_EMERALD_DROP_CHANCE.get())) {
            CommonfolkLootUtil.addDrop(event, new ItemStack(Items.EMERALD, CommonfolkRandomUtil.between(random, 1, 5)));
        }

        if (CommonfolkRandomUtil.chance(random, CommonfolkConfig.VILLAGER_BREAD_DROP_CHANCE.get())) {
            CommonfolkLootUtil.addDrop(event, new ItemStack(Items.BREAD, CommonfolkRandomUtil.between(random, 1, 3)));
        }

        if (CommonfolkConfig.REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT.get() && !(event.getSource().getEntity() instanceof Player)) {
            return;
        }

        if (!CommonfolkRandomUtil.chance(random, CommonfolkConfig.PROFESSION_DROP_CHANCE.get())) {
            return;
        }

        VillagerProfession profession = villager.getVillagerData().getProfession();
        for (ItemStack stack : ProfessionLootPools.roll(profession, random)) {
            CommonfolkLootUtil.addDrop(event, stack);
        }
    }
}
