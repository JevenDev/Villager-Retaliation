package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.progress.ExplorationHistoryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class PlayerPossessionMenuMixin {
    @Inject(method = "clicked", at = @At("RETURN"))
    private void villagerretaliation$rememberCompletedTransfer(int slot, int button, ClickType type, Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) ExplorationHistoryService.observeInventory(serverPlayer);
    }
}
