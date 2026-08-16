package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.inventory.VillagerGiftReturnTracker;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

public final class VillagerGiftKeepsakes {
    private VillagerGiftKeepsakes() {
    }

    public static void storeGift(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ItemStack giftedStack,
            ResolvedGiftPreference giftPreference,
            int awardedReputation) {
        if (giftedStack.isEmpty()) {
            return;
        }
        if (VillagerRetaliationConfig.ENABLE_GIFT_KEEPSAKES.get()
                && maybeKeepGift(level, villager, player, giftedStack, giftPreference)) {
            VillagerGiftReturnTracker.recordStoredGift(level, villager, player, giftedStack, awardedReputation);
            return;
        }

        ItemStack remainder = VillagerInventoryAccess.addItem(
                villager,
                VillagerGiftReturnTracker.markStoredGift(giftedStack.copy(), player, villager)
        );
        int storedCount = giftedStack.getCount() - remainder.getCount();
        if (storedCount > 0) {
            VillagerGiftReturnTracker.recordStoredGift(
                    level,
                    villager,
                    player,
                    giftedStack.copyWithCount(storedCount),
                    storedReputationValue(giftedStack, storedCount, awardedReputation)
            );
        }
        if (!remainder.isEmpty()) {
            VillagerGiftReturnTracker.stripGiftTracking(remainder);
            villager.spawnAtLocation(remainder);
        }
    }

    private static boolean maybeKeepGift(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ItemStack giftedStack,
            ResolvedGiftPreference giftPreference) {
        if (giftedStack.isEmpty()
                || giftPreference.reputationValue() <= 0
                || !isKeepsakeReaction(giftPreference.reaction())
                || villager.isBaby()) {
            return false;
        }

        VillagerReputationLevel reputationLevel =
                VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        if (reputationLevel.trustRank() < VillagerReputationLevel.TRUSTED.trustRank()) {
            return false;
        }
        if (villager.getRandom().nextInt(100) >= keepsakeChance(reputationLevel, giftPreference.reaction())) {
            return false;
        }

        ItemStack keepsake = VillagerGiftReturnTracker.markStoredGift(giftedStack.copy(), player, villager);
        EquipmentSlot preferredSlot = equipmentSlotFor(keepsake);
        if (preferredSlot != null && tryEquip(villager, preferredSlot, keepsake)) {
            return true;
        }
        return preferredSlot != EquipmentSlot.MAINHAND && tryEquip(villager, EquipmentSlot.MAINHAND, keepsake);
    }

    private static int storedReputationValue(ItemStack giftedStack, int storedCount, int reputationValue) {
        if (reputationValue <= 0) {
            return reputationValue;
        }
        if (storedCount >= giftedStack.getCount()) {
            return reputationValue;
        }
        return Math.max(1, reputationValue * storedCount / giftedStack.getCount());
    }

    private static int keepsakeChance(
            VillagerReputationLevel reputationLevel,
            VillagerGiftPreferences.GiftReaction reaction) {
        int baseChance = switch (reputationLevel) {
            case ROYALTY -> 70;
            case REVERED -> 55;
            case RESPECTED -> 40;
            case TRUSTED -> 25;
            default -> 0;
        };
        int reactionBonus = reaction == VillagerGiftPreferences.GiftReaction.LOVED ? 15 : 0;
        return Math.min(90, baseChance + reactionBonus);
    }

    private static boolean isKeepsakeReaction(VillagerGiftPreferences.GiftReaction reaction) {
        return reaction == VillagerGiftPreferences.GiftReaction.LOVED
                || reaction == VillagerGiftPreferences.GiftReaction.LIKED;
    }

    private static EquipmentSlot equipmentSlotFor(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        if (equipable == null) {
            return EquipmentSlot.MAINHAND;
        }
        return switch (equipable.getEquipmentSlot()) {
            case HEAD, CHEST, LEGS, FEET, OFFHAND -> equipable.getEquipmentSlot();
            default -> EquipmentSlot.MAINHAND;
        };
    }

    private static boolean tryEquip(Villager villager, EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND
                && (VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)
                || !villager.getMainHandItem().isEmpty())) {
            return false;
        }
        if (slot != EquipmentSlot.MAINHAND && !villager.getItemBySlot(slot).isEmpty()) {
            return false;
        }

        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, stack);
        return true;
    }
}
