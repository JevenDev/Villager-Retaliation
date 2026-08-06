package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.party.PartyInventoryOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Emerald Pouch out of the custom party pages
 */
@Pseudo
@Mixin(targets = "com.jvn.emeraldpouch.neoforge.client.EmeraldPouchClient", remap = false)
public abstract class EmeraldPouchClientMixin {
    @Inject(
            method = "onScreenRenderPost",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void villagerretaliation$hidePouchOverlay(
            ScreenEvent.Render.Post event, CallbackInfo callbackInfo) {
        if (isCustomPartyPage(event.getScreen())) callbackInfo.cancel();
    }

    @Inject(
            method = "onScreenMouseButtonPressedPre",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void villagerretaliation$disablePouchClick(
            ScreenEvent.MouseButtonPressed.Pre event, CallbackInfo callbackInfo) {
        if (isCustomPartyPage(event.getScreen())) callbackInfo.cancel();
    }

    @Inject(
            method = "onScreenMouseButtonPressedPost",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void villagerretaliation$disablePouchPostClick(
            ScreenEvent.MouseButtonPressed.Post event, CallbackInfo callbackInfo) {
        if (isCustomPartyPage(event.getScreen())) callbackInfo.cancel();
    }

    private static boolean isCustomPartyPage(Screen screen) {
        return screen instanceof InventoryScreen inventory
                && PartyInventoryOverlay.isCustomPage(inventory);
    }
}
