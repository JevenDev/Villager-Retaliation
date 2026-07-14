package com.jvn.villagerretaliation.mixin;

import com.jvn.villagerretaliation.item.BannerHelmetData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class BannerHelmetContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$handleBannerHelmetClick(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo callback
    ) {
        if (clickType != ClickType.PICKUP || button != 1) {
            return;
        }

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(slotId);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return;
        }

        ItemStack helmet = slot.getItem();
        // Result/output slots must complete their normal transaction before their item can be modified.
        if (!slot.mayPlace(helmet)) {
            return;
        }
        ItemStack carried = menu.getCarried();
        if (BannerHelmetData.canAttach(helmet, carried)) {
            BannerHelmetData.attach(helmet, carried, player.registryAccess());
            carried.shrink(1);
            if (carried.isEmpty()) {
                menu.setCarried(ItemStack.EMPTY);
            }
            slot.setChanged();
            villagerretaliation$playBannerSound(player);
            callback.cancel();
            return;
        }

        if (carried.isEmpty()) {
            BannerHelmetData.getAttachedBanner(helmet, player.registryAccess()).ifPresent(banner -> {
                BannerHelmetData.removeAttachedBanner(helmet);
                menu.setCarried(banner);
                slot.setChanged();
                villagerretaliation$playBannerSound(player);
                callback.cancel();
            });
        }
    }

    private static void villagerretaliation$playBannerSound(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        float pitch = 0.975F + player.getRandom().nextFloat() * 0.05F;
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BUNDLE_INSERT,
                SoundSource.PLAYERS,
                0.8F,
                pitch
        );
    }
}
