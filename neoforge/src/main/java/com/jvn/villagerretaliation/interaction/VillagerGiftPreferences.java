package com.jvn.villagerretaliation.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerGiftPreferences {
    private static final int MAX_POSITIVE_REPUTATION = 120;
    private static final int MAX_NEGATIVE_REPUTATION = -100;

    private VillagerGiftPreferences() {
    }

    public static int reputationValue(VillagerProfession profession, ItemStack stack) {
        return evaluate(profession, stack).reputationValue();
    }

    public static GiftReaction reactionFor(VillagerProfession profession, ItemStack stack) {
        return evaluate(profession, stack).reaction();
    }

    public static GiftPreference evaluate(VillagerProfession profession, ItemStack stack) {
        if (stack.isEmpty()) {
            return new GiftPreference(GiftReaction.NEUTRAL, false, 0);
        }

        GiftReaction professionPreference = professionPreference(profession, stack);
        if (professionPreference != GiftReaction.NEUTRAL) {
            return new GiftPreference(professionPreference, true, reputationValue(professionPreference, stack));
        }
        GiftReaction globalPreference = globalPreference(stack);
        return new GiftPreference(globalPreference, false, reputationValue(globalPreference, stack));
    }

    public static List<GiftCandidate> giftCandidates(VillagerProfession profession) {
        List<GiftCandidate> candidates = new ArrayList<>();
        addGlobalCandidates(candidates);
        addProfessionCandidates(candidates, profession);
        return candidates;
    }

    private static void addGlobalCandidates(List<GiftCandidate> candidates) {
        add(candidates, GiftReaction.HATED, false,
                Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE,
                Items.GUNPOWDER, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.FLINT_AND_STEEL,
                Items.LAVA_BUCKET, Items.WITHER_ROSE, Items.WITHER_SKELETON_SKULL);
        add(candidates, GiftReaction.DISLIKED, false,
                Items.BONE, Items.BONE_MEAL, Items.DEAD_BUSH, Items.PUFFERFISH, Items.PHANTOM_MEMBRANE,
                Items.MAGMA_CREAM, Items.SLIME_BALL, Items.FIREWORK_ROCKET, Items.SUSPICIOUS_STEW);
        add(candidates, GiftReaction.LOVED, false,
                Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE);
        add(candidates, GiftReaction.LIKED, false,
                Items.BREAD, Items.APPLE, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE,
                Items.HONEY_BOTTLE, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.MILK_BUCKET);
    }

    private static void addProfessionCandidates(List<GiftCandidate> candidates, VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            add(candidates, GiftReaction.LOVED, true, Items.IRON_INGOT, Items.SHIELD, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE);
            add(candidates, GiftReaction.LIKED, true, Items.COAL, Items.BLAST_FURNACE, Items.IRON_HELMET, Items.IRON_BOOTS);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET, Items.WOODEN_SWORD, Items.DEAD_BUSH);
        } else if (profession == VillagerProfession.BUTCHER) {
            add(candidates, GiftReaction.LOVED, true, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);
            add(candidates, GiftReaction.LIKED, true, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.SMOKER);
            add(candidates, GiftReaction.HATED, true, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE, Items.SUSPICIOUS_STEW);
            add(candidates, GiftReaction.DISLIKED, true, Items.BONE, Items.BONE_MEAL, Items.WITHER_ROSE);
        } else if (profession == VillagerProfession.CARTOGRAPHER) {
            add(candidates, GiftReaction.LOVED, true, Items.MAP, Items.COMPASS, Items.CLOCK, Items.RECOVERY_COMPASS);
            add(candidates, GiftReaction.LIKED, true, Items.PAPER, Items.FEATHER, Items.INK_SAC, Items.CARTOGRAPHY_TABLE);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
            add(candidates, GiftReaction.DISLIKED, true, Items.DEAD_BUSH, Items.SUSPICIOUS_STEW, Items.ROTTEN_FLESH);
        } else if (profession == VillagerProfession.CLERIC) {
            add(candidates, GiftReaction.LOVED, true, Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL);
            add(candidates, GiftReaction.LIKED, true, Items.REDSTONE, Items.LAPIS_LAZULI, Items.BLAZE_POWDER, Items.GHAST_TEAR, Items.BREWING_STAND);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.WITHER_SKELETON_SKULL);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.FERMENTED_SPIDER_EYE, Items.POISONOUS_POTATO, Items.DEAD_BUSH);
        } else if (profession == VillagerProfession.FARMER) {
            add(candidates, GiftReaction.LOVED, true, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS);
            add(candidates, GiftReaction.LIKED, true, Items.WHEAT, Items.BEETROOT, Items.MELON_SLICE, Items.PUMPKIN, Items.HAY_BLOCK, Items.COMPOSTER);
            add(candidates, GiftReaction.HATED, true, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.WITHER_ROSE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.BONE_MEAL, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE);
        } else if (profession == VillagerProfession.FISHERMAN) {
            add(candidates, GiftReaction.LOVED, true, Items.FISHING_ROD, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.NAUTILUS_SHELL);
            add(candidates, GiftReaction.LIKED, true, Items.STRING, Items.KELP, Items.DRIED_KELP, Items.BARREL);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.PUFFERFISH, Items.ROTTEN_FLESH, Items.PHANTOM_MEMBRANE, Items.MAGMA_CREAM);
        } else if (profession == VillagerProfession.FLETCHER) {
            add(candidates, GiftReaction.LOVED, true, Items.FLINT, Items.FEATHER, Items.ARROW, Items.SPECTRAL_ARROW, Items.CROSSBOW);
            add(candidates, GiftReaction.LIKED, true, Items.STICK, Items.STRING, Items.TRIPWIRE_HOOK, Items.FLETCHING_TABLE);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE);
            add(candidates, GiftReaction.DISLIKED, true, Items.SHIELD, Items.LAVA_BUCKET, Items.ROTTEN_FLESH, Items.SLIME_BALL);
        } else if (profession == VillagerProfession.LEATHERWORKER) {
            add(candidates, GiftReaction.LOVED, true, Items.LEATHER, Items.RABBIT_HIDE, Items.SADDLE, Items.LEATHER_HORSE_ARMOR);
            add(candidates, GiftReaction.LIKED, true, Items.CAULDRON, Items.LEATHER_BOOTS, Items.LEATHER_HELMET, Items.LEAD);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.BONE, Items.BONE_MEAL, Items.POISONOUS_POTATO);
        } else if (profession == VillagerProfession.LIBRARIAN) {
            add(candidates, GiftReaction.LOVED, true, Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.BOOKSHELF, Items.ENCHANTED_BOOK);
            add(candidates, GiftReaction.LIKED, true, Items.PAPER, Items.INK_SAC, Items.FEATHER, Items.LECTERN, Items.NAME_TAG);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.SUSPICIOUS_STEW);
        } else if (profession == VillagerProfession.MASON) {
            add(candidates, GiftReaction.LOVED, true, Items.CLAY_BALL, Items.BRICK, Items.STONE, Items.SMOOTH_STONE, Items.QUARTZ);
            add(candidates, GiftReaction.LIKED, true, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.TERRACOTTA, Items.STONECUTTER);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.SAND, Items.GRAVEL, Items.ROTTEN_FLESH, Items.SLIME_BALL);
        } else if (profession == VillagerProfession.SHEPHERD) {
            add(candidates, GiftReaction.LOVED, true, Items.WHITE_WOOL, Items.SHEARS, Items.WHITE_DYE, Items.BLUE_DYE, Items.RED_DYE, Items.YELLOW_DYE);
            add(candidates, GiftReaction.LIKED, true, Items.BLACK_WOOL, Items.BROWN_WOOL, Items.PINK_WOOL, Items.LOOM, Items.LEAD);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.BONE, Items.WITHER_ROSE, Items.DEAD_BUSH);
        } else if (profession == VillagerProfession.TOOLSMITH) {
            add(candidates, GiftReaction.LOVED, true, Items.IRON_INGOT, Items.DIAMOND, Items.ANVIL, Items.SMITHING_TABLE);
            add(candidates, GiftReaction.LIKED, true, Items.COAL, Items.FLINT, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.ROTTEN_FLESH, Items.DEAD_BUSH);
        } else if (profession == VillagerProfession.WEAPONSMITH) {
            add(candidates, GiftReaction.LOVED, true, Items.IRON_INGOT, Items.DIAMOND, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.GRINDSTONE);
            add(candidates, GiftReaction.LIKED, true, Items.COAL, Items.FLINT, Items.IRON_AXE, Items.CROSSBOW);
            add(candidates, GiftReaction.HATED, true, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET);
            add(candidates, GiftReaction.DISLIKED, true, Items.WOODEN_SWORD, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SLIME_BALL);
        } else if (profession == VillagerProfession.NITWIT) {
            add(candidates, GiftReaction.LOVED, true, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.HONEY_BOTTLE);
            add(candidates, GiftReaction.LIKED, true, Items.SLIME_BALL, Items.SNOWBALL, Items.FLOWER_POT);
            add(candidates, GiftReaction.HATED, true, Items.POISONOUS_POTATO, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET, Items.WITHER_SKELETON_SKULL);
            add(candidates, GiftReaction.DISLIKED, true, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE, Items.DEAD_BUSH);
        }
    }

    private static void add(List<GiftCandidate> candidates, GiftReaction reaction, boolean professionSpecific, Item... items) {
        for (Item item : items) {
            candidates.add(new GiftCandidate(item, reaction, professionSpecific));
        }
    }

    private static int reputationValue(GiftReaction reaction, ItemStack stack) {
        int value = reaction.perItemReputation() * stack.getCount();
        return Math.clamp(value, MAX_NEGATIVE_REPUTATION, MAX_POSITIVE_REPUTATION);
    }

    private static GiftReaction globalPreference(ItemStack stack) {
        if (isAny(stack,
                Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE,
                Items.GUNPOWDER, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.FLINT_AND_STEEL,
                Items.LAVA_BUCKET, Items.WITHER_ROSE, Items.WITHER_SKELETON_SKULL)) {
            return GiftReaction.HATED;
        }
        if (isAny(stack,
                Items.BONE, Items.BONE_MEAL, Items.DEAD_BUSH, Items.PUFFERFISH, Items.PHANTOM_MEMBRANE,
                Items.MAGMA_CREAM, Items.SLIME_BALL, Items.FIREWORK_ROCKET, Items.SUSPICIOUS_STEW)) {
            return GiftReaction.DISLIKED;
        }
        if (isAny(stack,
                Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE)) {
            return GiftReaction.LOVED;
        }
        if (isAny(stack,
                Items.BREAD, Items.APPLE, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE,
                Items.HONEY_BOTTLE, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.MILK_BUCKET)) {
            return GiftReaction.LIKED;
        }
        return GiftReaction.NEUTRAL;
    }

    private static GiftReaction professionPreference(VillagerProfession profession, ItemStack stack) {
        if (profession == VillagerProfession.ARMORER) {
            if (isAny(stack, Items.IRON_INGOT, Items.SHIELD, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.COAL, Items.BLAST_FURNACE, Items.IRON_HELMET, Items.IRON_BOOTS)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET, Items.WOODEN_SWORD, Items.DEAD_BUSH)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.BUTCHER) {
            if (isAny(stack, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.SMOKER)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE,
                    Items.SUSPICIOUS_STEW)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.BONE, Items.BONE_MEAL, Items.WITHER_ROSE)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            if (isAny(stack, Items.MAP, Items.COMPASS, Items.CLOCK, Items.RECOVERY_COMPASS)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.PAPER, Items.FEATHER, Items.INK_SAC, Items.CARTOGRAPHY_TABLE)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.DEAD_BUSH, Items.SUSPICIOUS_STEW, Items.ROTTEN_FLESH)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.CLERIC) {
            if (isAny(stack, Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.REDSTONE, Items.LAPIS_LAZULI, Items.BLAZE_POWDER, Items.GHAST_TEAR, Items.BREWING_STAND)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.WITHER_SKELETON_SKULL)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.FERMENTED_SPIDER_EYE, Items.POISONOUS_POTATO, Items.DEAD_BUSH)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.FARMER) {
            if (isAny(stack, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.WHEAT, Items.BEETROOT, Items.MELON_SLICE, Items.PUMPKIN, Items.HAY_BLOCK, Items.COMPOSTER)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.WITHER_ROSE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.BONE_MEAL, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.FISHERMAN) {
            if (isAny(stack, Items.FISHING_ROD, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.NAUTILUS_SHELL)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.STRING, Items.KELP, Items.DRIED_KELP, Items.BARREL)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.PUFFERFISH, Items.ROTTEN_FLESH, Items.PHANTOM_MEMBRANE, Items.MAGMA_CREAM)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.FLETCHER) {
            if (isAny(stack, Items.FLINT, Items.FEATHER, Items.ARROW, Items.SPECTRAL_ARROW, Items.CROSSBOW)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.STICK, Items.STRING, Items.TRIPWIRE_HOOK, Items.FLETCHING_TABLE)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.SHIELD, Items.LAVA_BUCKET, Items.ROTTEN_FLESH, Items.SLIME_BALL)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            if (isAny(stack, Items.LEATHER, Items.RABBIT_HIDE, Items.SADDLE, Items.LEATHER_HORSE_ARMOR)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.CAULDRON, Items.LEATHER_BOOTS, Items.LEATHER_HELMET, Items.LEAD)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.BONE, Items.BONE_MEAL, Items.POISONOUS_POTATO)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            if (isAny(stack, Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.BOOKSHELF, Items.ENCHANTED_BOOK)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.PAPER, Items.INK_SAC, Items.FEATHER, Items.LECTERN, Items.NAME_TAG)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.SUSPICIOUS_STEW)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.MASON) {
            if (isAny(stack, Items.CLAY_BALL, Items.BRICK, Items.STONE, Items.SMOOTH_STONE, Items.QUARTZ)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.TERRACOTTA, Items.STONECUTTER)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.SAND, Items.GRAVEL, Items.ROTTEN_FLESH, Items.SLIME_BALL)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.SHEPHERD) {
            if (isAny(stack, Items.WHITE_WOOL, Items.SHEARS, Items.WHITE_DYE, Items.BLUE_DYE, Items.RED_DYE, Items.YELLOW_DYE)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.BLACK_WOOL, Items.BROWN_WOOL, Items.PINK_WOOL, Items.LOOM, Items.LEAD)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.BONE, Items.WITHER_ROSE, Items.DEAD_BUSH)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            if (isAny(stack, Items.IRON_INGOT, Items.DIAMOND, Items.ANVIL, Items.SMITHING_TABLE)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.COAL, Items.FLINT, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.ROTTEN_FLESH,
                    Items.DEAD_BUSH)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            if (isAny(stack, Items.IRON_INGOT, Items.DIAMOND, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.GRINDSTONE)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.COAL, Items.FLINT, Items.IRON_AXE, Items.CROSSBOW)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.TNT, Items.TNT_MINECART, Items.FIRE_CHARGE, Items.LAVA_BUCKET)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.WOODEN_SWORD, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SLIME_BALL)) {
                return GiftReaction.DISLIKED;
            }
        }
        if (profession == VillagerProfession.NITWIT) {
            if (isAny(stack, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.HONEY_BOTTLE)) {
                return GiftReaction.LOVED;
            }
            if (isAny(stack, Items.SLIME_BALL, Items.SNOWBALL, Items.FLOWER_POT)) {
                return GiftReaction.LIKED;
            }
            if (isAny(stack, Items.POISONOUS_POTATO, Items.TNT, Items.TNT_MINECART, Items.LAVA_BUCKET,
                    Items.WITHER_SKELETON_SKULL)) {
                return GiftReaction.HATED;
            }
            if (isAny(stack, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE, Items.DEAD_BUSH)) {
                return GiftReaction.DISLIKED;
            }
        }
        return GiftReaction.NEUTRAL;
    }

    private static boolean isAny(ItemStack stack, Item... items) {
        for (Item item : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    public enum GiftReaction {
        LOVED(6),
        LIKED(3),
        NEUTRAL(0),
        DISLIKED(-2),
        HATED(-5);

        private final int perItemReputation;

        GiftReaction(int perItemReputation) {
            this.perItemReputation = perItemReputation;
        }

        private int perItemReputation() {
            return this.perItemReputation;
        }

        private boolean isPositive() {
            return this.perItemReputation > 0;
        }
    }

    public record GiftPreference(GiftReaction reaction, boolean professionSpecific, int reputationValue) {
        private GiftPreference withReputationValue(int reputationValue) {
            return new GiftPreference(this.reaction, this.professionSpecific, reputationValue);
        }
    }

    public record GiftCandidate(Item item, GiftReaction reaction, boolean professionSpecific) {
        public boolean positive() {
            return this.reaction.isPositive();
        }
    }
}

