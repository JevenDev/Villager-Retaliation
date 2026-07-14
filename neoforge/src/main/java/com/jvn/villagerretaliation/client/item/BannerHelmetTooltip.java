package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.item.BannerHelmetData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class BannerHelmetTooltip {
    private BannerHelmetTooltip() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null && event.getEntity() != null) {
            registries = event.getEntity().registryAccess();
        }
        if (registries == null) {
            return;
        }

        ItemStack banner = BannerHelmetData.getAttachedBanner(event.getItemStack(), registries).orElse(ItemStack.EMPTY);
        if (banner.isEmpty()) {
            return;
        }
        event.getToolTip().add(Component.translatable(
                "tooltip.villagerretaliation.attached_banner",
                banner.getHoverName()
        ).withStyle(ChatFormatting.GRAY));
        BannerItem.appendHoverTextFromBannerBlockEntityTag(banner, event.getToolTip());
    }
}
