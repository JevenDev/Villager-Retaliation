package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.duel.DuelService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuDuelMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$lockDuelInventory(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo callbackInfo) {
        if (player instanceof ServerPlayer serverPlayer
                && !DuelService.allowsInventoryClick(
                        serverPlayer, (AbstractContainerMenu) (Object) this, slotId, clickType)) {
            ((AbstractContainerMenu) (Object) this).broadcastFullState();
            callbackInfo.cancel();
        }
    }
}
