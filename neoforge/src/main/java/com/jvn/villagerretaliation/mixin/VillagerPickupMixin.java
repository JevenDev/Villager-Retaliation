package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge;
import com.jvn.villagerretaliation.party.PartyVillagerDropCollection;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerItemPickupReach;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerPickupMixin {
    private static final double WANTED_ITEM_PICKUP_REACH_SQR = 2.25D;

    @Shadow
    protected abstract void pickUpItem(ItemEntity itemEntity);

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void villagerretaliation$pickUpReachableWantedItem(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (!(villager.level() instanceof ServerLevel)) {
            return;
        }
        ItemEntity wantedItem = villager.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                .orElse(null);
        if (wantedItem == null
                || !wantedItem.isAlive()
                || wantedItem.hasPickUpDelay()
                || wantedItem.getItem().isEmpty()
                || !villager.wantsToPickUp(wantedItem.getItem())
                || !VillagerItemPickupReach.isWithinReach(
                        villager,
                        wantedItem,
                        WANTED_ITEM_PICKUP_REACH_SQR)) {
            return;
        }
        this.pickUpItem(wantedItem);
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$routeHiredFarmerPickupPredicate(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        Villager villager = (Villager) (Object) this;
        if (villager.level() instanceof ServerLevel level) {
            if (DuelService.isParticipant(villager)
                    || VillagerRetaliationVillagerCombatUtil.isThreatened(villager)) {
                cir.setReturnValue(false);
                return;
            }
            Boolean partyResult = PartyVillagerDropCollection.wantsToPickUp(level, villager, stack);
            if (partyResult != null) {
                cir.setReturnValue(partyResult);
                return;
            }
            Boolean result = HiredFarmingInventoryBridge.wantsToPickUp(level, villager, stack);
            if (result != null) {
                cir.setReturnValue(result);
                return;
            }
            if (VillagerBehaviorSuppressionPolicy.suppresses(
                    villager, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_ITEM_PICKUP)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$routeHiredFarmerPickup(
            ItemEntity itemEntity,
            CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (DuelService.isParticipant(villager)
                || VillagerRetaliationVillagerCombatUtil.isThreatened(villager)) {
            ci.cancel();
            return;
        }
        if (villager.level() instanceof ServerLevel level) {
            if (PartyVillagerDropCollection.capturePickup(level, villager, itemEntity)
                    || HiredFarmingInventoryBridge.capturePickup(level, villager, itemEntity)
                    || VillagerBehaviorSuppressionPolicy.suppresses(
                            villager, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_ITEM_PICKUP)) {
                ci.cancel();
            }
        }
    }
}
