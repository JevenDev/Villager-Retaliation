package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.raid.Raid;

/** Identifies ominous-banner symbols displayed as worn or held equipment. */
public final class OminousBannerRecognition {
    public static final TagKey<Item> PATTERN_CARRIERS = TagKey.create(
            Registries.ITEM, VillagerRetaliation.id("ominous_banner_pattern_carriers"));
    public static final TagKey<Item> EQUIVALENTS = TagKey.create(
            Registries.ITEM, VillagerRetaliation.id("ominous_banner_equivalents"));

    private OminousBannerRecognition() {
    }

    public static boolean isDisplaying(ServerPlayer player) {
        return isDisplaying((LivingEntity) player);
    }

    public static boolean isDisplaying(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        ItemStack headStack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (isOminousBanner(headStack, entity)) {
            return true;
        }
        if (BannerHelmetData.getAttachedBanner(headStack, entity.registryAccess())
                .filter(stack -> isOminousBanner(stack, entity))
                .isPresent()) {
            return true;
        }
        return isOminousBanner(entity.getMainHandItem(), entity)
                || isOminousBanner(entity.getOffhandItem(), entity);
    }

    /** Retained as a source-compatible alias for the original helmet-only API. */
    public static boolean isWearing(ServerPlayer player) {
        return isDisplaying(player);
    }

    static boolean isOminousBanner(ItemStack stack, LivingEntity entity) {
        if (stack == null || stack.isEmpty() || entity == null) {
            return false;
        }
        if (stack.is(EQUIVALENTS)) {
            return true;
        }
        if (!stack.is(PATTERN_CARRIERS)) {
            return false;
        }
        ItemStack ominousBanner = Raid.getLeaderBannerInstance(
                entity.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
        if (!Objects.equals(
                stack.get(DataComponents.BANNER_PATTERNS),
                ominousBanner.get(DataComponents.BANNER_PATTERNS))) {
            return false;
        }
        if (stack.getItem() instanceof BannerItem bannerItem) {
            return bannerItem.getColor() == DyeColor.WHITE;
        }
        return stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE) == DyeColor.WHITE;
    }
}
