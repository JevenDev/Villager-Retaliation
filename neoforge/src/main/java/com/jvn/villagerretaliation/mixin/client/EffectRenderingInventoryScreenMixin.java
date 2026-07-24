package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {
    @Redirect(
            method = {
                    "renderEffects",
                    "renderBackgrounds",
                    "renderIcons",
                    "renderLabels"
            },
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen;topPos:I",
                    opcode = Opcodes.GETFIELD))
    private int villagerretaliation$offsetPartyInventoryEffects(EffectRenderingInventoryScreen<?> screen) {
        int top = ((AbstractContainerScreenAccessor) screen).villagerretaliation$getTopPos();
        if (screen instanceof InventoryScreen inventoryScreen) {
            return top + PartyInventoryOverlay.effectListTopOffset(inventoryScreen);
        }
        return top;
    }
}