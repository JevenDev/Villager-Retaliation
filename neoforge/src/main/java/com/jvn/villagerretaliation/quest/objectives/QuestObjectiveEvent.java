package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.state.BlockState;

public record QuestObjectiveEvent(
        QuestObjectiveEventKind kind,
        LivingEntity killedEntity,
        BlockPos blockPos,
        BlockState blockState,
        VillageEventMemory.MemoryEvent memoryEvent,
        ItemStack itemStack,
        VillagerGiftPreferences.GiftReaction giftReaction,
        AbstractVillager villager,
        MerchantOffer offer,
        Integer reputationValue,
        ResourceLocation criterion,
        Map<String, String> criterionData
) {
    public QuestObjectiveEvent {
        itemStack = itemStack == null ? ItemStack.EMPTY : itemStack;
        criterionData = criterionData == null ? Map.of() : Map.copyOf(criterionData);
    }

    public QuestObjectiveEvent(
            QuestObjectiveEventKind kind,
            LivingEntity killedEntity,
            BlockPos blockPos,
            BlockState blockState,
            VillageEventMemory.MemoryEvent memoryEvent,
            ItemStack itemStack,
            VillagerGiftPreferences.GiftReaction giftReaction,
            AbstractVillager villager,
            MerchantOffer offer,
            Integer reputationValue) {
        this(kind, killedEntity, blockPos, blockState, memoryEvent, itemStack, giftReaction, villager, offer,
                reputationValue, null, Map.of());
    }

    public static QuestObjectiveEvent mobKill(LivingEntity killedEntity) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.MOB_KILL, killedEntity, null, null, null,
                ItemStack.EMPTY, null, null, null, null);
    }

    public static QuestObjectiveEvent block(
            QuestObjectiveEventKind kind,
            BlockPos pos,
            BlockState state) {
        return new QuestObjectiveEvent(kind, null, pos, state, null, ItemStack.EMPTY, null, null, null, null);
    }

    public static QuestObjectiveEvent memory(VillageEventMemory.MemoryEvent event) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.MEMORY_EVENT, null, null, null, event,
                ItemStack.EMPTY, null, null, null, null);
    }

    public static QuestObjectiveEvent gift(
            ItemStack stack,
            VillagerGiftPreferences.GiftReaction reaction) {
        return gift(null, stack, reaction);
    }

    public static QuestObjectiveEvent gift(
            AbstractVillager villager,
            ItemStack stack,
            VillagerGiftPreferences.GiftReaction reaction) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.GIFT, null, null, null, null,
                stack, reaction, villager, null, null);
    }

    public static QuestObjectiveEvent trade(AbstractVillager villager, MerchantOffer offer) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.TRADE, null, null, null, null,
                ItemStack.EMPTY, null, villager, offer, null);
    }

    public static QuestObjectiveEvent reputation(int reputationValue) {
        return reputation(null, reputationValue);
    }

    public static QuestObjectiveEvent reputation(AbstractVillager villager, int reputationValue) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.REPUTATION, null, null, null, null,
                ItemStack.EMPTY, null, villager, null, reputationValue);
    }

    public static QuestObjectiveEvent criterion(
            ResourceLocation criterion,
            Map<String, String> data,
            ItemStack stack,
            LivingEntity entity) {
        return new QuestObjectiveEvent(QuestObjectiveEventKind.CRITERION, entity, null, null, null,
                stack, null, null, null, null, criterion, data);
    }
}
