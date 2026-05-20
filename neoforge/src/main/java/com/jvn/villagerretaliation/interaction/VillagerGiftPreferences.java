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

    public static String responseFor(VillagerProfession profession, ItemStack stack, int reputationValue) {
        return responseFor(profession, stack, evaluate(profession, stack).withReputationValue(reputationValue));
    }

    public static String responseFor(VillagerProfession profession, ItemStack stack, GiftPreference preference) {
        String itemName = stack.getHoverName().getString();
        int professionHash = profession == null ? 0 : profession.hashCode();
        int variant = Math.floorMod(itemName.hashCode() + professionHash + stack.getCount(), 4);

        String specificResponse = specificResponse(profession, stack, preference.reaction(), preference.professionSpecific(), itemName, variant);
        if (specificResponse != null) {
            return specificResponse;
        }

        return switch (preference.reaction()) {
            case LOVED -> lovedResponse(itemName, variant);
            case LIKED -> likedResponse(itemName, variant);
            case NEUTRAL -> neutralResponse(itemName, variant, preference.reputationValue());
            case DISLIKED -> dislikedResponse(itemName, variant);
            case HATED -> hatedResponse(itemName, variant);
        };
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

    private static String specificResponse(VillagerProfession profession, ItemStack stack, GiftReaction preference, boolean professionSpecific, String itemName, int variant) {
        if (professionSpecific && profession == VillagerProfession.ARMORER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Fine material. I can hear the forge thanking you.";
                case 1 -> "This will keep someone standing when the night gets loud.";
                case 2 -> itemName + " has honest weight. I like that.";
                default -> "Bring me metal like this and I will remember your name.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.BUTCHER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Fresh stock. That is a gift a butcher can respect.";
                case 1 -> "This will feed more than one table tonight.";
                case 2 -> "Good cut, clean smell. You chose well.";
                default -> "I'll make sure none of this goes to waste.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.CARTOGRAPHER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Ah, tools for finding edges of the world. Wonderful.";
                case 1 -> "Give me this and I may give you better directions someday.";
                case 2 -> itemName + " belongs near a map table, not forgotten in a pack.";
                default -> "The roads feel shorter with gifts like this.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.CLERIC && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "There is a quiet power in this. I accept it gladly.";
                case 1 -> "Useful for brewing, blessing, or both if the day is strange.";
                case 2 -> itemName + " has the shimmer of a proper offering.";
                default -> "May this kindness return to you when you need it most.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.FARMER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Good seed, good soil, good neighbor.";
                case 1 -> "The field will make better use of this than any chest would.";
                case 2 -> itemName + " means work, but the satisfying kind.";
                default -> "You understand the village begins with the harvest.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.FISHERMAN && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "That smells like river work and early mornings.";
                case 1 -> "A practical gift. The dock will appreciate it too.";
                case 2 -> itemName + " is exactly the sort of thing I keep close.";
                default -> "You have the sense of someone who watches the water.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.FLETCHER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Straight flights start with gifts like this.";
                case 1 -> "I can make good use of this before sunset.";
                case 2 -> itemName + " has the makings of a clean shot.";
                default -> "You know what keeps a village ready.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.LEATHERWORKER && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Good hide and patient hands make lasting work.";
                case 1 -> "This will take dye well. I can tell.";
                case 2 -> itemName + " has promise under the needle.";
                default -> "A thoughtful gift. Practical, sturdy, appreciated.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.LIBRARIAN && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Knowledge, ink, paper. The civilized gifts.";
                case 1 -> "This deserves a dry shelf and careful hands.";
                case 2 -> itemName + " is worth more than its weight if used properly.";
                default -> "Excellent. The library grows one kindness at a time.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.MASON && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Good material. It will sit square and hold true.";
                case 1 -> "I can already see the wall this wants to become.";
                case 2 -> itemName + " has a clean face to it.";
                default -> "A village lasts because someone values solid things.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.SHEPHERD && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Soft work, bright work. This is lovely.";
                case 1 -> "The flock and the loom both approve.";
                case 2 -> itemName + " will make someone warmer or happier.";
                default -> "You brought color to a working day.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.TOOLSMITH && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "That will make a tool with a proper bite.";
                case 1 -> "Useful metal is better than pretty promises.";
                case 2 -> itemName + " belongs beside a hammer.";
                default -> "A sharp gift. I mean that kindly.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.WEAPONSMITH && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "A village sleeps easier with supplies like this.";
                case 1 -> "This has balance, edge, and purpose.";
                case 2 -> itemName + " is not a toy. Good.";
                default -> "You are thinking like someone who expects night to answer back.";
            };
        }
        if (professionSpecific && profession == VillagerProfession.NITWIT && preference.isPositive()) {
            return switch (variant) {
                case 0 -> "Oh! I know exactly where to put this. Probably.";
                case 1 -> "This is excellent. No notes. Many crumbs, maybe.";
                case 2 -> itemName + "? For me? That is suspiciously nice.";
                default -> "I will treasure this until I forget where I treasured it.";
            };
        }
        if (isAny(stack, Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT)) {
            return switch (variant) {
                case 0 -> "That is no small kindness. The village will hear of it.";
                case 1 -> "A gift with real weight. Thank you.";
                case 2 -> "You trust me with " + itemName + "? I will not forget that.";
                default -> "Generous. Very generous.";
            };
        }
        if (isAny(stack, Items.BREAD, Items.APPLE, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.HONEY_BOTTLE)) {
            return switch (variant) {
                case 0 -> "Food is never just food here. Thank you.";
                case 1 -> "That will make the day softer around the edges.";
                case 2 -> itemName + " is a kind thing to bring to a working village.";
                default -> "Simple, useful, and welcome.";
            };
        }
        if (preference == GiftReaction.HATED && isAny(stack, Items.TNT, Items.GUNPOWDER, Items.FLINT_AND_STEEL)) {
            return switch (variant) {
                case 0 -> "Do not bring danger and call it a present.";
                case 1 -> "That belongs far from homes, beds, and children.";
                case 2 -> "I know a threat when it is wrapped like a gift.";
                default -> "Take that away before I call the others.";
            };
        }
        if (preference == GiftReaction.HATED && isAny(stack, Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE)) {
            return switch (variant) {
                case 0 -> "That is foul, and you know it.";
                case 1 -> "I have seen compost with better manners.";
                case 2 -> "No. Absolutely not.";
                default -> "Keep your sickness out of my hands.";
            };
        }
        return null;
    }

    private static String lovedResponse(String itemName, int variant) {
        return switch (variant) {
            case 0 -> "This is wonderful. You chose like someone who knows me.";
            case 1 -> itemName + "? That is more thoughtful than I expected.";
            case 2 -> "I will remember this. Truly.";
            default -> "A gift like this changes the shape of a day.";
        };
    }

    private static String likedResponse(String itemName, int variant) {
        return switch (variant) {
            case 0 -> "Thank you. I can make good use of this.";
            case 1 -> itemName + " is a welcome gift.";
            case 2 -> "Practical and kind. I appreciate it.";
            default -> "That was thoughtful of you.";
        };
    }

    private static String neutralResponse(String itemName, int variant, int reputationValue) {
        if (reputationValue != 0) {
            return "Thank you. I suppose this has its uses.";
        }
        return switch (variant) {
            case 0 -> "Thank you, I suppose.";
            case 1 -> itemName + "? I am not sure what to do with it, but I accept it.";
            case 2 -> "A curious gift. I will think about it.";
            default -> "That is... something. Thank you.";
        };
    }

    private static String dislikedResponse(String itemName, int variant) {
        return switch (variant) {
            case 0 -> "I do not have much use for " + itemName + ".";
            case 1 -> "That is not exactly welcome.";
            case 2 -> "You may mean well, but this feels careless.";
            default -> "I will take it, but I am not pleased.";
        };
    }

    private static String hatedResponse(String itemName, int variant) {
        return switch (variant) {
            case 0 -> "This is not funny.";
            case 1 -> "Why would you hand me " + itemName + "?";
            case 2 -> "That is an insult, not a gift.";
            default -> "Take better care with what you offer people.";
        };
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

