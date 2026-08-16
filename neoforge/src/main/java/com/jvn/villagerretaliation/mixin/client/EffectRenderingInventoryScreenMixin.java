package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {
    /**
     * EffectRenderingInventoryScreen renders effects after the inventory screen itself. The party
     * and settings views replace that screen with full custom panels, so letting the vanilla pass
     * continue would always paint effect icons over those panels.
     */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$hideEffectsBehindCustomPartyPages(
            net.minecraft.client.gui.GuiGraphics graphics,
            int mouseX,
            int mouseY,
            CallbackInfo callbackInfo) {
        if ((Object) this instanceof InventoryScreen screen
                && PartyInventoryOverlay.isCustomPage(screen)) {
            callbackInfo.cancel();
        }
    }

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