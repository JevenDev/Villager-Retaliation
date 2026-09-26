package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.inventory.ContainerFilterResolver;
import com.jvn.villagerretaliation.settlement.SettlementDepotRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrame.class)
public abstract class ItemFrameSettlementMixin {
    @Inject(method = "setItem(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"))
    private void villagerretaliation$observeDepotRule(ItemStack stack, boolean updateNeighbours, CallbackInfo callback) {
        ItemFrame frame = (ItemFrame) (Object) this;
        if (frame.isAddedToLevel() && frame.level() instanceof ServerLevel level && level.getServer().isSameThread()) {
            ContainerFilterResolver.invalidateFrame(level, frame);
            SettlementDepotRules.changed(frame);
        }
    }
}
