package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import org.jetbrains.annotations.Nullable;

public final class VillagerSkillTradeEvents {
    private VillagerSkillTradeEvents() {
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        String professionKey = VillagerProfessionUtil.serializedKey(event.getType());
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        switch (professionKey) {
            case "farmer" -> addFarmerTrades(trades);
            case "fisherman" -> addFishermanTrades(trades);
            case "fletcher" -> addFletcherTrades(trades);
            case "librarian" -> addLibrarianTrades(trades);
            case "cleric" -> addClericTrades(trades);
            case "armorer" -> addArmorerTrades(trades);
            case "weaponsmith" -> addWeaponsmithTrades(trades);
            case "toolsmith" -> addToolsmithTrades(trades);
            case "mason" -> addMasonTrades(trades);
            case "leatherworker" -> addLeatherworkerTrades(trades);
            case "butcher" -> addButcherTrades(trades);
            case "cartographer" -> addCartographerTrades(trades);
            case "shepherd" -> addShepherdTrades(trades);
            default -> {
            }
        }
    }

    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(skillOffer(VillagerSkill.TRADING, VillagerSkillRank.SKILLED, 0.85D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 3, stack(Items.GLOW_BERRIES, 8), 8, 2, 0.05F)));
        event.getGenericTrades().add(skillOffer(VillagerSkill.SURVIVAL, VillagerSkillRank.EXPERT, 0.65D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 5, stack(Items.EXPERIENCE_BOTTLE, 3), 6, 5, 0.05F)));
        event.getRareTrades().add(skillOffer(VillagerSkill.TRADING, VillagerSkillRank.MASTER, 0.55D,
                (level, trader, random, skillValue) -> rareSpecialtyAllowed()
                        ? emeraldOffer(trader, random, 14, stack(Items.NAUTILUS_SHELL, 1), 2, 10, 0.05F)
                        : null));
    }

    private static void addFarmerTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.FARMING, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.BONE_MEAL, 16), 12, 4, 0.05F)));
        add(trades, 3, skillOffer(VillagerSkill.COOKING, VillagerSkillRank.SKILLED, 0.9D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 3, stack(Items.BREAD, 10), 10, 6, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.FARMING, VillagerSkillRank.EXPERT, 0.75D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 6, stack(Items.GOLDEN_CARROT, 4), 8, 12, 0.05F)));
        add(trades, 5, skillOffer(VillagerSkill.FARMING, VillagerSkillRank.MASTER, 0.45D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 16,
                        enchantedItem(level, random, stack(highTierAllowed() ? Items.DIAMOND_HOE : Items.IRON_HOE, 1), skillValue,
                                List.of(Enchantments.UNBREAKING, Enchantments.EFFICIENCY, Enchantments.FORTUNE)),
                        2, 20, 0.10F)));
    }

    private static void addFishermanTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.FISHING, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.COOKED_COD, 12), 12, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.FISHING, VillagerSkillRank.EXPERT, 0.7D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 10,
                        enchantedItem(level, random, stack(Items.FISHING_ROD, 1), skillValue,
                                List.of(Enchantments.LUCK_OF_THE_SEA, Enchantments.LURE, Enchantments.UNBREAKING)),
                        3, 15, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.FISHING, VillagerSkillRank.MASTER, 0.35D,
                (level, trader, random, skillValue) -> rareSpecialtyAllowed()
                        ? emeraldOffer(trader, random, 12, stack(Items.NAUTILUS_SHELL, 1), 2, 18, 0.05F)
                        : null));
    }

    private static void addFletcherTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.ARCHERY, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.ARROW, 24), 12, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.ARCHERY, VillagerSkillRank.EXPERT, 0.75D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 12,
                        enchantedItem(level, random, stack(random.nextBoolean() ? Items.BOW : Items.CROSSBOW, 1), skillValue,
                                List.of(Enchantments.POWER, Enchantments.PUNCH, Enchantments.PIERCING, Enchantments.UNBREAKING)),
                        3, 15, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.ARCHERY, VillagerSkillRank.MASTER, 0.5D,
                (level, trader, random, skillValue) -> specialArrowsAllowed()
                        ? emeraldOffer(trader, random, 5, stack(Items.SPECTRAL_ARROW, 16), 6, 18, 0.05F)
                        : null));
    }

    private static void addLibrarianTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 3, skillOffer(VillagerSkill.SCHOLARSHIP, VillagerSkillRank.SKILLED, 0.9D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 5, stack(Items.BOOKSHELF, 4), 10, 8, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.SCHOLARSHIP, VillagerSkillRank.EXPERT, 0.75D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 18,
                        enchantedBook(level, random, skillValue, List.of(Enchantments.UNBREAKING, Enchantments.EFFICIENCY, Enchantments.PROTECTION)),
                        4, 15, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.SCHOLARSHIP, VillagerSkillRank.MASTER, 0.45D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 28,
                        enchantedBook(level, random, skillValue, List.of(Enchantments.FORTUNE, Enchantments.POWER, Enchantments.LUCK_OF_THE_SEA)),
                        2, 25, 0.10F)));
    }

    private static void addClericTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.MEDICINE, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 3, stack(Items.GLISTERING_MELON_SLICE, 4), 10, 5, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.MEDICINE, VillagerSkillRank.EXPERT, 0.7D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 8, stack(Items.GHAST_TEAR, 1), 4, 15, 0.05F)));
        add(trades, 5, skillOffer(VillagerSkill.MEDICINE, VillagerSkillRank.MASTER, 0.35D,
                (level, trader, random, skillValue) -> rareSpecialtyAllowed()
                        ? emeraldOffer(trader, random, 18, stack(Items.GOLDEN_APPLE, 1), 2, 20, 0.05F)
                        : null));
    }

    private static void addArmorerTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 3, skillOffer(VillagerSkill.SMITHING, VillagerSkillRank.SKILLED, 0.9D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 9, stack(Items.IRON_CHESTPLATE, 1), 5, 10, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.SMITHING, VillagerSkillRank.EXPERT, 0.55D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 26,
                        enchantedItem(level, random, stack(highTierAllowed() ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE, 1), skillValue,
                                List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING)),
                        2, 25, 0.15F)));
    }

    private static void addWeaponsmithTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 3, skillOffer(VillagerSkill.SMITHING, VillagerSkillRank.SKILLED, 0.9D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 8, stack(Items.IRON_SWORD, 1), 6, 10, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.SMITHING, VillagerSkillRank.EXPERT, 0.6D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 24,
                        enchantedItem(level, random, stack(highTierAllowed() ? Items.DIAMOND_SWORD : Items.IRON_SWORD, 1), skillValue,
                                List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.LOOTING)),
                        2, 25, 0.15F)));
    }

    private static void addToolsmithTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 3, skillOffer(VillagerSkill.CRAFTING, VillagerSkillRank.SKILLED, 0.9D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 8, stack(Items.IRON_PICKAXE, 1), 6, 10, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.CRAFTING, VillagerSkillRank.EXPERT, 0.6D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 24,
                        enchantedItem(level, random, stack(highTierAllowed() ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE, 1), skillValue,
                                List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING, Enchantments.FORTUNE)),
                        2, 25, 0.15F)));
    }

    private static void addMasonTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.MASONRY, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.BRICKS, 16), 12, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.MASONRY, VillagerSkillRank.EXPERT, 0.75D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 5, stack(Items.QUARTZ_BLOCK, 8), 8, 12, 0.05F)));
    }

    private static void addLeatherworkerTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.LEATHERWORKING, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 4, stack(Items.LEATHER, 12), 10, 5, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.LEATHERWORKING, VillagerSkillRank.EXPERT, 0.7D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 10,
                        enchantedItem(level, random, stack(Items.LEATHER_CHESTPLATE, 1), skillValue,
                                List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING)),
                        4, 12, 0.10F)));
        add(trades, 5, skillOffer(VillagerSkill.ANIMAL_HANDLING, VillagerSkillRank.MASTER, 0.35D,
                (level, trader, random, skillValue) -> rareSpecialtyAllowed()
                        ? emeraldOffer(trader, random, 12, stack(Items.SADDLE, 1), 2, 20, 0.05F)
                        : null));
    }

    private static void addButcherTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.COOKING, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.COOKED_BEEF, 8), 12, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.COOKING, VillagerSkillRank.EXPERT, 0.75D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 6, stack(Items.COOKED_PORKCHOP, 18), 8, 12, 0.05F)));
    }

    private static void addCartographerTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.CARTOGRAPHY, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 3, stack(Items.MAP, 2), 10, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.CARTOGRAPHY, VillagerSkillRank.EXPERT, 0.7D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 7, stack(Items.COMPASS, 2), 8, 12, 0.05F)));
        add(trades, 5, skillOffer(VillagerSkill.CARTOGRAPHY, VillagerSkillRank.MASTER, 0.35D,
                (level, trader, random, skillValue) -> rareSpecialtyAllowed()
                        ? emeraldOffer(trader, random, 12, stack(Items.CLOCK, 1), 3, 18, 0.05F)
                        : null));
    }

    private static void addShepherdTrades(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        add(trades, 2, skillOffer(VillagerSkill.ANIMAL_HANDLING, VillagerSkillRank.APPRENTICE, 1.0D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 2, stack(Items.WHITE_WOOL, 16), 12, 4, 0.05F)));
        add(trades, 4, skillOffer(VillagerSkill.CRAFTING, VillagerSkillRank.EXPERT, 0.7D,
                (level, trader, random, skillValue) -> emeraldOffer(trader, random, 5, stack(Items.SCAFFOLDING, 16), 8, 12, 0.05F)));
    }

    private static void add(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, int level, VillagerTrades.ItemListing listing) {
        List<VillagerTrades.ItemListing> listings = trades.get(level);
        if (listings != null) {
            listings.add(listing);
        }
    }

    private static VillagerTrades.ItemListing skillOffer(
            VillagerSkill skill,
            VillagerSkillRank minimumRank,
            double chance,
            OfferFactory factory) {
        return new SkillOfferListing(skill, minimumRank, chance, factory);
    }

    private static MerchantOffer emeraldOffer(Entity trader, RandomSource random, int emeralds, ItemStack result, int maxTrades, int xp, float priceMultiplier) {
        return new BasicItemListing(emeralds, result, maxTrades, xp, priceMultiplier).getOffer(trader, random);
    }

    private static ItemStack enchantedItem(
            ServerLevel level,
            RandomSource random,
            ItemStack stack,
            int skillValue,
            List<ResourceKey<Enchantment>> enchantments) {
        if (stack.isEmpty() || enchantments.isEmpty()) {
            return stack;
        }

        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> enchantment = registry.getHolderOrThrow(enchantments.get(random.nextInt(enchantments.size())));
        int levelValue = Math.min(enchantmentLevel(skillValue), enchantment.value().getMaxLevel());
        stack.enchant(enchantment, levelValue);
        return stack;
    }

    private static ItemStack enchantedBook(
            ServerLevel level,
            RandomSource random,
            int skillValue,
            List<ResourceKey<Enchantment>> enchantments) {
        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> enchantment = registry.getHolderOrThrow(enchantments.get(random.nextInt(enchantments.size())));
        int levelValue = Math.min(enchantmentLevel(skillValue), enchantment.value().getMaxLevel());
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, levelValue));
    }

    private static int enchantmentLevel(int skillValue) {
        int level = skillValue >= 90 ? 3 : skillValue >= 72 ? 2 : 1;
        return Math.clamp(level, 1, VillagerRetaliationConfig.SKILL_TRADE_MAX_ENCHANTMENT_LEVEL.get());
    }

    private static boolean highTierAllowed() {
        return VillagerRetaliationConfig.SKILL_TRADE_ALLOW_HIGH_TIER_EQUIPMENT.get();
    }

    private static boolean specialArrowsAllowed() {
        return VillagerRetaliationConfig.SKILL_TRADE_ALLOW_SPECIAL_ARROWS.get();
    }

    private static boolean rareSpecialtyAllowed() {
        return VillagerRetaliationConfig.SKILL_TRADE_ALLOW_RARE_SPECIALTY_TRADES.get();
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private record SkillOfferListing(
            VillagerSkill skill,
            VillagerSkillRank minimumRank,
            double chance,
            OfferFactory factory) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                    || !(trader instanceof AbstractVillager villager)
                    || !(villager.level() instanceof ServerLevel level)) {
                return null;
            }

            int skillValue = VillagerProfileManager.getSkill(level, villager, this.skill);
            if (skillValue < this.minimumRank.minInclusive() || !passesChance(random, skillValue)) {
                return null;
            }
            return this.factory.create(level, villager, random, skillValue);
        }

        private boolean passesChance(RandomSource random, int skillValue) {
            double scaledChance = this.chance;
            if (this.chance < 1.0D) {
                scaledChance *= VillagerRetaliationConfig.SKILL_TRADE_RARE_CHANCE_MULTIPLIER.get();
                scaledChance += Math.max(0, skillValue - this.minimumRank.minInclusive()) / 250.0D;
            }
            return random.nextDouble() < Math.clamp(scaledChance, 0.0D, 1.0D);
        }
    }

    @FunctionalInterface
    private interface OfferFactory {
        @Nullable
        MerchantOffer create(ServerLevel level, AbstractVillager trader, RandomSource random, int skillValue);
    }
}
