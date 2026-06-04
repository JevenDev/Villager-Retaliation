package com.jvn.villagerretaliation.loot;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.toucanlib.util.ToucanItemStacks;
import com.jvn.toucanlib.util.ToucanRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class ProfessionLootPools {
    @FunctionalInterface
    public interface ProfessionLootPool {
        List<ItemStack> roll(Villager villager, RandomSource random);
    }

    private record Entry(Function<RandomSource, ItemStack> factory) {
        private ItemStack create(RandomSource random) {
            return factory.apply(random);
        }
    }

    private static final Item[] ALL_DYES = {
            Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE,
            Items.YELLOW_DYE, Items.LIME_DYE, Items.PINK_DYE, Items.GRAY_DYE,
            Items.LIGHT_GRAY_DYE, Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
            Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE
    };
    private static final Item[] ALL_WOOL = {
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
            Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL
    };
    private static final Item[] ALL_CARPETS = {
            Items.WHITE_CARPET, Items.ORANGE_CARPET, Items.MAGENTA_CARPET, Items.LIGHT_BLUE_CARPET,
            Items.YELLOW_CARPET, Items.LIME_CARPET, Items.PINK_CARPET, Items.GRAY_CARPET,
            Items.LIGHT_GRAY_CARPET, Items.CYAN_CARPET, Items.PURPLE_CARPET, Items.BLUE_CARPET,
            Items.BROWN_CARPET, Items.GREEN_CARPET, Items.RED_CARPET, Items.BLACK_CARPET
    };
    private static final Item[] ALL_BANNERS = {
            Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER, Items.LIGHT_BLUE_BANNER,
            Items.YELLOW_BANNER, Items.LIME_BANNER, Items.PINK_BANNER, Items.GRAY_BANNER,
            Items.LIGHT_GRAY_BANNER, Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
            Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER
    };
    private static final Item[] ALL_FLOWERS = {
            Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
            Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY,
            Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY
    };
    private static final Map<VillagerProfession, ProfessionLootPool> POOLS = new HashMap<>();

    static {
        POOLS.put(VillagerProfession.FARMER, ProfessionLootPools::farmer);
        POOLS.put(VillagerProfession.LEATHERWORKER, ProfessionLootPools::leatherworker);
        POOLS.put(VillagerProfession.FISHERMAN, ProfessionLootPools::fisherman);
        POOLS.put(VillagerProfession.LIBRARIAN, ProfessionLootPools::librarian);
        POOLS.put(VillagerProfession.SHEPHERD, ProfessionLootPools::shepherd);
        POOLS.put(VillagerProfession.BUTCHER, ProfessionLootPools::butcher);
        POOLS.put(VillagerProfession.CLERIC, ProfessionLootPools::cleric);
        POOLS.put(VillagerProfession.CARTOGRAPHER, ProfessionLootPools::cartographer);
        POOLS.put(VillagerProfession.TOOLSMITH, ProfessionLootPools::toolsmith);
        POOLS.put(VillagerProfession.WEAPONSMITH, ProfessionLootPools::weaponsmith);
        POOLS.put(VillagerProfession.ARMORER, ProfessionLootPools::armorer);
        POOLS.put(VillagerProfession.FLETCHER, ProfessionLootPools::fletcher);
        POOLS.put(VillagerProfession.MASON, ProfessionLootPools::mason);
        POOLS.put(VillagerProfession.NITWIT, ProfessionLootPools::nitwit);
        POOLS.put(VillagerProfession.NONE, ProfessionLootPools::unemployed);
    }

    private ProfessionLootPools() {
    }

    public static List<ItemStack> roll(Villager villager, RandomSource random) {
        ProfessionLootPool pool = POOLS.get(villager.getVillagerData().getProfession());
        if (pool == null) {
            return List.of();
        }

        return pool.roll(villager, random).stream().filter(stack -> !stack.isEmpty()).toList();
    }

    private static List<ItemStack> farmer(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.WHEAT, 1, 10), stack(Items.BEETROOT, 1, 6), stack(Items.CARROT, 1, 8),
                stack(Items.POTATO, 1, 6), stack(Items.APPLE, 1, 2), stack(Items.PUMPKIN_SEEDS, 1, 1),
                stack(Items.MELON_SEEDS, 1, 1), stack(Items.WHEAT_SEEDS, 1, 12), entry(ProfessionLootPools::suspiciousStew));
        addRare(random, drops, new ItemStack(Items.MILK_BUCKET));
        return drops;
    }

    private static List<ItemStack> leatherworker(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 3,
                stack(Items.LEATHER, 1, 5), stack(Items.RABBIT_HIDE, 1, 3),
                damaged(randomItem(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)),
                preferredDyeStack(Items.BROWN_DYE, Items.BLACK_DYE, Items.WHITE_DYE, Items.RED_DYE, Items.GRAY_DYE));
        addVeryRare(random, drops, new ItemStack(Items.SADDLE));
        return drops;
    }

    private static List<ItemStack> fisherman(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.COD, 1, 4), stack(Items.SALMON, 1, 3), damaged(Items.FISHING_ROD),
                stack(Items.STRING, 1, 3), stack(Items.STICK, 1, 2));
        addRare(random, drops, new ItemStack(Items.TROPICAL_FISH));
        addRare(random, drops, new ItemStack(Items.PUFFERFISH));
        addRare(random, drops, new ItemStack(Items.LILY_PAD));
        addVeryRare(random, drops, new ItemStack(Items.NAUTILUS_SHELL));
        return drops;
    }

    private static List<ItemStack> librarian(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.PAPER, 1, 5), stack(Items.BOOK, 1, 3), stack(Items.INK_SAC, 1, 1), stack(Items.FEATHER, 1, 1));
        addRare(random, drops, new ItemStack(Items.BOOKSHELF));
        if (ToucanRandom.chance(random, VillagerRetaliationConfig.VERY_RARE_DROP_CHANCE.get())) {
            drops.add(soldLibrarianEnchantedBook(villager, random).orElseGet(() -> enchantedBook(villager, random)));
        }
        return drops;
    }

    private static List<ItemStack> shepherd(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.WHITE_WOOL, 1, 8), stack(randomWool(random), 1, 4), damaged(Items.SHEARS),
                stack(Items.WHEAT, 1, 4), randomDye(), stack(randomCarpet(random), 1, 1));
        addRare(random, drops, new ItemStack(randomBanner(random)));
        return drops;
    }

    private static List<ItemStack> butcher(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.BEEF, 1, 4), stack(Items.PORKCHOP, 1, 4), stack(Items.CHICKEN, 1, 3),
                stack(Items.MUTTON, 1, 3), stack(Items.RABBIT, 1, 2), stack(Items.LEATHER, 1, 2));
        addRare(random, drops, new ItemStack(Items.SMOKER));
        return drops;
    }

    private static List<ItemStack> cleric(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.ROTTEN_FLESH, 1, 4), stack(Items.REDSTONE, 1, 3), stack(Items.LAPIS_LAZULI, 1, 3),
                stack(Items.GLOWSTONE_DUST, 1, 2));
        addRare(random, drops, new ItemStack(Items.EXPERIENCE_BOTTLE));
        addRare(random, drops, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
        addRare(random, drops, PotionContents.createItemStack(Items.POTION, Potions.REGENERATION));
        addVeryRare(random, drops, new ItemStack(Items.ENDER_PEARL));
        return drops;
    }

    private static List<ItemStack> cartographer(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.PAPER, 1, 6), stack(Items.FILLED_MAP, 1, 2), stack(Items.COMPASS, 1, 1),
                stack(Items.MAP, 1, 1), stack(Items.INK_SAC, 1, 2), stack(Items.FEATHER, 1, 2));
        addRare(random, drops, new ItemStack(Items.CARTOGRAPHY_TABLE));
        return drops;
    }

    private static List<ItemStack> toolsmith(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                damaged(randomItem(Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE)),
                stack(Items.IRON_INGOT, 1, 5), stack(Items.COAL, 1, 8), stack(Items.STICK, 1, 3), stack(Items.FLINT, 1, 2));
        addRare(random, drops, new ItemStack(Items.SMITHING_TABLE));
        return drops;
    }

    private static List<ItemStack> weaponsmith(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.IRON_INGOT, 1, 5), stack(Items.COAL, 1, 8), stack(Items.STICK, 1, 3));
        addRare(random, drops, new ItemStack(Items.GRINDSTONE));
        return drops;
    }

    private static List<ItemStack> armorer(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                damaged(randomItem(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)),
                stack(Items.IRON_INGOT, 1, 5), stack(Items.COAL, 1, 8), stack(Items.CHAIN, 1, 2));
        addRare(random, drops, new ItemStack(Items.BLAST_FURNACE));
        return drops;
    }

    private static List<ItemStack> fletcher(Villager villager, RandomSource random) {
        return rollCommon(random, 3, 5,
                stack(Items.ARROW, 1, 3), stack(Items.FLINT, 1, 2),
                stack(Items.FEATHER, 1, 4), stack(Items.STICK, 1, 8), stack(Items.STRING, 1, 2), stack(Items.TRIPWIRE_HOOK, 1, 2));
    }

    private static List<ItemStack> mason(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.CLAY_BALL, 1, 4), stack(Items.BRICK, 1, 3), stack(Items.QUARTZ, 1, 2),
                stack(Items.STONE, 1, 4), stack(Items.TERRACOTTA, 1, 3));
        addRare(random, drops, new ItemStack(Items.FLOWER_POT));
        addRare(random, drops, new ItemStack(Items.STONECUTTER));
        return drops;
    }

    private static List<ItemStack> nitwit(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 4,
                stack(Items.BREAD, 1, 3), stack(Items.STICK, 1, 4), stack(Items.POISONOUS_POTATO, 1, 1),
                stack(randomFlower(random), 1, 1), stack(Items.DIRT, 1, 1));
        addRare(random, drops, currencyStack(villager, random, 1, 2));
        return drops;
    }

    private static List<ItemStack> unemployed(Villager villager, RandomSource random) {
        List<ItemStack> drops = rollCommon(random, 2, 3,
                stack(Items.BREAD, 1, 3), stack(Items.STICK, 1, 3), stack(Items.WHEAT_SEEDS, 1, 2));
        addRare(random, drops, currencyStack(villager, random, 1, 2));
        addRare(random, drops, new ItemStack(Items.APPLE));
        return drops;
    }

    private static ItemStack currencyStack(Villager villager, RandomSource random, int min, int max) {
        return VillagerCurrencyResources.createStack(villager.level().getServer(), ToucanRandom.betweenInclusive(random, min, max));
    }

    private static List<ItemStack> rollCommon(RandomSource random, int minRolls, int maxRolls, Entry... entries) {
        int rolls = Math.max(1, ToucanRandom.betweenInclusive(random, minRolls, maxRolls));
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            drops.add(entries[random.nextInt(entries.length)].create(random));
        }

        return drops;
    }

    private static void addRare(RandomSource random, List<ItemStack> drops, ItemStack stack) {
        if (ToucanRandom.chance(random, VillagerRetaliationConfig.RARE_DROP_CHANCE.get())) {
            drops.add(stack);
        }
    }

    private static void addVeryRare(RandomSource random, List<ItemStack> drops, ItemStack stack) {
        if (ToucanRandom.chance(random, VillagerRetaliationConfig.VERY_RARE_DROP_CHANCE.get())) {
            drops.add(stack);
        }
    }

    private static Entry stack(Item item, int minCount, int maxCount) {
        return entry(random -> new ItemStack(item, ToucanRandom.betweenInclusive(random, minCount, maxCount)));
    }

    private static Entry damaged(Item item) {
        return entry(random -> ToucanItemStacks.withRandomMobDropDamage(new ItemStack(item), random));
    }

    private static Entry damaged(Entry itemEntry) {
        return entry(random -> ToucanItemStacks.withRandomMobDropDamage(itemEntry.create(random), random));
    }

    private static Entry randomItem(Item... items) {
        return entry(random -> new ItemStack(items[random.nextInt(items.length)]));
    }

    private static Entry randomDye() {
        return entry(random -> new ItemStack(randomDyeItem(random), ToucanRandom.betweenInclusive(random, 1, 3)));
    }

    private static Entry preferredDyeStack(Item... preferred) {
        return entry(random -> new ItemStack(preferred[random.nextInt(preferred.length)], ToucanRandom.betweenInclusive(random, 1, 2)));
    }

    private static Entry entry(Function<RandomSource, ItemStack> factory) {
        return new Entry(factory);
    }

    private static ItemStack suspiciousStew(RandomSource random) {
        List<SuspiciousStewEffects.Entry> effects = List.of(
                new SuspiciousStewEffects.Entry(MobEffects.NIGHT_VISION, 100),
                new SuspiciousStewEffects.Entry(MobEffects.JUMP, 120),
                new SuspiciousStewEffects.Entry(MobEffects.WEAKNESS, 160),
                new SuspiciousStewEffects.Entry(MobEffects.BLINDNESS, 120),
                new SuspiciousStewEffects.Entry(MobEffects.POISON, 160),
                new SuspiciousStewEffects.Entry(MobEffects.SATURATION, 7),
                new SuspiciousStewEffects.Entry(MobEffects.FIRE_RESISTANCE, 80),
                new SuspiciousStewEffects.Entry(MobEffects.REGENERATION, 160));
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
        stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(List.of(effects.get(random.nextInt(effects.size())))));
        return stew;
    }

    private static ItemStack enchantedBook(Villager villager, RandomSource random) {
        ResourceKey<Enchantment> key = ToucanRandom.choose(random, List.of(
                Enchantments.UNBREAKING, Enchantments.EFFICIENCY, Enchantments.POWER, Enchantments.PROTECTION, Enchantments.LUCK_OF_THE_SEA));
        Registry<Enchantment> registry = villager.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> enchantment = registry.getHolderOrThrow(key);
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1));
    }

    private static Optional<ItemStack> soldLibrarianEnchantedBook(Villager villager, RandomSource random) {
        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> enchantedBookOffers = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (result.is(Items.ENCHANTED_BOOK)) {
                enchantedBookOffers.add(result.copy());
            }
        }

        if (enchantedBookOffers.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(enchantedBookOffers.get(random.nextInt(enchantedBookOffers.size())));
    }

    private static Item randomDyeItem(RandomSource random) {
        return randomEntry(random, ALL_DYES);
    }

    private static Item randomWool(RandomSource random) {
        return randomEntry(random, ALL_WOOL);
    }

    private static Item randomCarpet(RandomSource random) {
        return randomEntry(random, ALL_CARPETS);
    }

    private static Item randomBanner(RandomSource random) {
        return randomEntry(random, ALL_BANNERS);
    }

    private static Item randomFlower(RandomSource random) {
        return randomEntry(random, ALL_FLOWERS);
    }

    private static Item randomEntry(RandomSource random, Item[] items) {
        return items[random.nextInt(items.length)];
    }
}
