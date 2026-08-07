package com.jvn.villagerretaliation.api;

import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Public bridge for mods that want to drive data-defined quest objectives. */
public final class QuestCriterionApi {
    private QuestCriterionApi() {
    }

    public static void trigger(
            ServerPlayer player,
            ResourceLocation criterion,
            Map<String, String> data) {
        trigger(player, criterion, data, ItemStack.EMPTY, null);
    }

    public static void trigger(
            ServerPlayer player,
            ResourceLocation criterion,
            Map<String, String> data,
            ItemStack item,
            LivingEntity entity) {
        if (player == null || criterion == null) {
            return;
        }
        VillagerQuestService.onCriterion(
                player.serverLevel(),
                player,
                criterion,
                data == null ? Map.of() : data,
                item == null ? ItemStack.EMPTY : item,
                entity);
    }
}
