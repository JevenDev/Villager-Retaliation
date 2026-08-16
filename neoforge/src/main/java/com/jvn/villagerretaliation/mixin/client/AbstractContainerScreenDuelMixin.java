package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.duel.DuelInventoryClientState;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenDuelMixin {
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideLockedDuelSlot(
            GuiGraphics graphics, Slot slot, CallbackInfo callbackInfo) {
        if ((Object) this instanceof InventoryScreen screen
                && !PartyInventoryOverlay.showsSlot(screen, slot)) {
            callbackInfo.cancel();
            return;
        }
        if ((Object) this instanceof InventoryScreen
                && !DuelInventoryClientState.visibleAssignedSlot(slot.index)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;IIF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void villagerretaliation$hideLockedDuelSlotHighlight(
            GuiGraphics graphics,
            Slot slot,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo) {
        if ((Object) this instanceof InventoryScreen screen
                && !PartyInventoryOverlay.showsSlot(screen, slot)) {
            callbackInfo.cancel();
            return;
        }
        if ((Object) this instanceof InventoryScreen
                && !DuelInventoryClientState.visibleAssignedSlot(slot.index)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$blockDuelSlotClick(
            Slot slot,
            int slotId,
            int mouseButton,
            ClickType clickType,
            CallbackInfo callbackInfo) {
        if ((Object) this instanceof InventoryScreen screen
                && PartyInventoryOverlay.isCustomPage(screen)
                && !PartyInventoryOverlay.showsSlot(screen, slot)
                // Vanilla sends the start and end of a quick-craft operation without a slot.
                // Blocking either one leaves right-click placement and drag splitting unfinished.
                && (slot != null || clickType != ClickType.QUICK_CRAFT)) {
            callbackInfo.cancel();
            return;
        }
        if (!DuelInventoryClientState.active()) return;
        int resolvedSlotId = slot == null ? slotId : slot.index;
        if (!((Object) this instanceof InventoryScreen)
                || !DuelInventoryClientState.allowsInventoryClick(resolvedSlotId, clickType)) {
            callbackInfo.cancel();
        }
    }
}
