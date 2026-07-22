package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.duel.DuelInventoryClientState;
import com.jvn.villagerretaliation.client.duel.DuelInventoryScreenAccess;
import com.jvn.villagerretaliation.client.duel.DuelInventoryScreenRenderer;
import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin implements DuelInventoryScreenAccess {
    @Shadow private float xMouse;
    @Shadow private float yMouse;
    @Shadow @Final private RecipeBookComponent recipeBookComponent;
    @Unique private int[] villagerretaliation$originalSlotX;
    @Unique private int[] villagerretaliation$originalSlotY;

    @Inject(method = "init", at = @At("TAIL"))
    private void villagerretaliation$configureDuelInventory(CallbackInfo callbackInfo) {
        if (!DuelInventoryClientState.assignedLoadout()) return;
        InventoryScreen screen = (InventoryScreen) (Object) this;
        this.villagerretaliation$originalSlotX = new int[screen.getMenu().slots.size()];
        this.villagerretaliation$originalSlotY = new int[screen.getMenu().slots.size()];
        for (int slotId = 0; slotId < screen.getMenu().slots.size(); slotId++) {
            Slot slot = screen.getMenu().slots.get(slotId);
            this.villagerretaliation$originalSlotX[slotId] = slot.x;
            this.villagerretaliation$originalSlotY[slotId] = slot.y;
        }
        ((RecipeBookComponentAccessor) this.recipeBookComponent).villagerretaliation$setVisible(false);
        ((ScreenInvoker) this).villagerretaliation$clearWidgets();
        ((AbstractContainerScreenAccessor) this).villagerretaliation$setLeftPos((screen.width - 176) / 2);
        ((AbstractContainerScreenAccessor) this).villagerretaliation$setTopPos(
                (screen.height - DuelInventoryScreenRenderer.HEIGHT) / 2);
        for (int slotId = 0; slotId < screen.getMenu().slots.size(); slotId++) {
            positionDuelSlot(screen.getMenu().slots.get(slotId), slotId);
        }
    }

    @Override
    public void villagerretaliation$restoreDuelInventorySlots() {
        if (this.villagerretaliation$originalSlotX == null) return;
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int count = Math.min(screen.getMenu().slots.size(), this.villagerretaliation$originalSlotX.length);
        for (int slotId = 0; slotId < count; slotId++) {
            SlotAccessor accessor = (SlotAccessor) screen.getMenu().slots.get(slotId);
            accessor.villagerretaliation$setX(this.villagerretaliation$originalSlotX[slotId]);
            accessor.villagerretaliation$setY(this.villagerretaliation$originalSlotY[slotId]);
        }
        this.villagerretaliation$originalSlotX = null;
        this.villagerretaliation$originalSlotY = null;
    }

    private static void positionDuelSlot(Slot slot, int slotId) {
        SlotAccessor accessor = (SlotAccessor) slot;
        if (slotId >= InventoryMenu.ARMOR_SLOT_START && slotId < InventoryMenu.ARMOR_SLOT_END) {
            accessor.villagerretaliation$setX(45);
            accessor.villagerretaliation$setY(8 + (slotId - InventoryMenu.ARMOR_SLOT_START) * 18);
        } else if (slotId >= InventoryMenu.USE_ROW_SLOT_START && slotId < InventoryMenu.USE_ROW_SLOT_END) {
            accessor.villagerretaliation$setX(8 + (slotId - InventoryMenu.USE_ROW_SLOT_START) * 18);
            accessor.villagerretaliation$setY(98);
        } else if (slotId == InventoryMenu.SHIELD_SLOT) {
            accessor.villagerretaliation$setX(114);
            accessor.villagerretaliation$setY(62);
        }
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$renderPartyOverlayBehindInventory(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY,
            CallbackInfo callbackInfo) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        if (DuelInventoryClientState.assignedLoadout()) {
            DuelInventoryScreenRenderer.render(graphics, screen, this.xMouse, this.yMouse);
            callbackInfo.cancel();
            return;
        }
        PartyInventoryOverlay.renderInventoryBackground(graphics, screen);
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideDuelInventoryLabels(
            GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callbackInfo) {
        if (DuelInventoryClientState.assignedLoadout()) callbackInfo.cancel();
    }
}
