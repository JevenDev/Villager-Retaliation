package com.jvn.villagerretaliation.interaction;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerGiftPreferences {
    private static final int MAX_POSITIVE_REPUTATION = 120;
    private static final int MAX_NEGATIVE_REPUTATION = -80;

    private VillagerGiftPreferences() {
    }

    public static int reputationValue(VillagerProfession profession, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        int perItem = perItemValue(profession, stack);
        int value = perItem * stack.getCount();
        return Math.clamp(value, MAX_NEGATIVE_REPUTATION, MAX_POSITIVE_REPUTATION);
    }

    public static String responseFor(int reputationValue) {
        if (reputationValue >= 32) {
            return "This is wonderful. Thank you.";
        }
        if (reputationValue > 0) {
            return "Thank you. I appreciate it.";
        }
        if (reputationValue < 0) {
            return "This is not funny.";
        }
        return "Thank you, I suppose.";
    }

    private static int perItemValue(VillagerProfession profession, ItemStack stack) {
        if (isGloballyDisliked(stack)) {
            return -2;
        }
        if (isExcellentGift(stack)) {
            return 5;
        }
        if (isProfessionGift(profession, stack)) {
            return 3;
        }
        if (isGloballyLiked(stack)) {
            return 2;
        }
        return 1;
    }

    private static boolean isExcellentGift(ItemStack stack) {
        return stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLDEN_APPLE);
    }

    private static boolean isGloballyLiked(ItemStack stack) {
        return stack.is(Items.BREAD)
                || stack.is(Items.APPLE)
                || stack.is(Items.COOKIE)
                || stack.is(Items.HONEY_BOTTLE)
                || stack.is(Items.CAKE)
                || stack.is(Items.PUMPKIN_PIE)
                || stack.is(Items.SWEET_BERRIES)
                || stack.is(Items.GLOW_BERRIES);
    }

    private static boolean isGloballyDisliked(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.FERMENTED_SPIDER_EYE)
                || stack.is(Items.BONE)
                || stack.is(Items.GUNPOWDER);
    }

    private static boolean isProfessionGift(VillagerProfession profession, ItemStack stack) {
        if (profession == VillagerProfession.ARMORER) {
            return stack.is(Items.IRON_INGOT) || stack.is(Items.COAL) || stack.is(Items.SHIELD);
        }
        if (profession == VillagerProfession.BUTCHER) {
            return stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN);
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.COMPASS);
        }
        if (profession == VillagerProfession.CLERIC) {
            return stack.is(Items.REDSTONE) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.GLOWSTONE_DUST);
        }
        if (profession == VillagerProfession.FARMER) {
            return isAny(stack, Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE, Items.PUMPKIN);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.STRING);
        }
        if (profession == VillagerProfession.FLETCHER) {
            return stack.is(Items.ARROW) || stack.is(Items.FEATHER) || stack.is(Items.FLINT) || stack.is(Items.STICK);
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return stack.is(Items.LEATHER) || stack.is(Items.RABBIT_HIDE) || stack.is(Items.SADDLE);
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return stack.is(Items.BOOK) || stack.is(Items.PAPER) || stack.is(Items.INK_SAC) || stack.is(Items.BOOKSHELF);
        }
        if (profession == VillagerProfession.MASON) {
            return stack.is(Items.CLAY_BALL) || stack.is(Items.BRICK) || stack.is(Items.STONE) || stack.is(Items.SMOOTH_STONE);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return stack.is(Items.WHITE_WOOL) || stack.is(Items.WHITE_DYE) || stack.is(Items.SHEARS);
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return stack.is(Items.IRON_INGOT) || stack.is(Items.FLINT) || stack.is(Items.COAL);
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return stack.is(Items.IRON_INGOT) || stack.is(Items.COAL) || stack.is(Items.FLINT);
        }
        return false;
    }

    private static boolean isAny(ItemStack stack, Item first, Item second, Item third, Item fourth, Item fifth, Item sixth) {
        return stack.is(first) || stack.is(second) || stack.is(third) || stack.is(fourth) || stack.is(fifth) || stack.is(sixth);
    }
}
