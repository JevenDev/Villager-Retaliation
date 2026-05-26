package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class SkillTradeOfferFactory {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SkillTradeOfferFactory() {
    }

    @Nullable
    public static MerchantOffer createVillagerOffer(
            ServerLevel level,
            AbstractVillager villager,
            ResourceLocation professionId,
            int villagerLevel,
            RandomSource random) {
        return createOffer(level, villager, random, definition ->
                definition.matchesVillager(professionId, villagerLevel));
    }

    @Nullable
    public static MerchantOffer createWanderingTraderOffer(
            ServerLevel level,
            AbstractVillager trader,
            SkillTradePool pool,
            RandomSource random) {
        return createOffer(level, trader, random, definition -> definition.matchesWanderingTrader(pool));
    }

    @Nullable
    private static MerchantOffer createOffer(
            ServerLevel level,
            AbstractVillager villager,
            RandomSource random,
            DefinitionFilter filter) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()) {
            return null;
        }

        List<ResolvedDefinition> candidates = new ArrayList<>();
        for (SkillTradeDefinition definition : SkillTradeResources.definitions(level.getServer())) {
            if (!filter.matches(definition) || !definition.conditions().matches()) {
                continue;
            }

            int skillValue = bestSkillValue(level, villager, definition);
            if (skillValue < definition.minRank().minInclusive() || !passesChance(definition, skillValue, random)) {
                continue;
            }
            candidates.add(new ResolvedDefinition(definition, skillValue));
        }
        if (candidates.isEmpty()) {
            return null;
        }

        ResolvedDefinition selected = selectWeighted(candidates, random);
        return createSelectedOffer(level, random, selected.definition(), selected.skillValue());
    }

    private static int bestSkillValue(ServerLevel level, AbstractVillager villager, SkillTradeDefinition definition) {
        int best = 0;
        for (VillagerSkill skill : definition.skills()) {
            best = Math.max(best, VillagerProfileManager.getSkill(level, villager, skill));
        }
        return best;
    }

    private static boolean passesChance(SkillTradeDefinition definition, int skillValue, RandomSource random) {
        double scaledChance = definition.chance();
        if (scaledChance < 1.0D) {
            scaledChance *= VillagerRetaliationConfig.SKILL_TRADE_RARE_CHANCE_MULTIPLIER.get();
            scaledChance += Math.max(0, skillValue - definition.minRank().minInclusive()) / 250.0D;
        }
        return random.nextDouble() < Math.clamp(scaledChance, 0.0D, 1.0D);
    }

    private static ResolvedDefinition selectWeighted(List<ResolvedDefinition> candidates, RandomSource random) {
        int totalWeight = candidates.stream()
                .mapToInt(candidate -> candidate.definition().weight())
                .sum();
        int selected = random.nextInt(Math.max(1, totalWeight));
        for (ResolvedDefinition candidate : candidates) {
            selected -= candidate.definition().weight();
            if (selected < 0) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    @Nullable
    private static MerchantOffer createSelectedOffer(
            ServerLevel level,
            RandomSource random,
            SkillTradeDefinition definition,
            int skillValue) {
        ItemStack result = definition.result().createBaseStack(random);
        if (result.isEmpty()) {
            return null;
        }

        result = applyEnchantments(level, random, definition, result, skillValue);
        int costCount = definition.cost().countForSkill(skillValue, definition.minRank().minInclusive());
        int maxUses = definition.maxUses().valueForSkill(skillValue, definition.minRank().minInclusive());
        return new MerchantOffer(
                new ItemCost(definition.cost().item(), costCount),
                result,
                maxUses,
                definition.xp(),
                definition.priceMultiplier()
        );
    }

    private static ItemStack applyEnchantments(
            ServerLevel level,
            RandomSource random,
            SkillTradeDefinition definition,
            ItemStack stack,
            int skillValue) {
        SkillTradeEnchantments enchantments = definition.result().enchantments();
        if (!enchantments.enabled() || stack.isEmpty()) {
            return stack;
        }

        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return switch (enchantments.mode()) {
            case NONE -> stack;
            case RANDOM_FROM -> applyRandomEnchantment(registry, random, definition, stack, skillValue, enchantments);
            case FIXED -> applyFixedEnchantments(registry, definition, stack, skillValue, enchantments);
        };
    }

    private static ItemStack applyRandomEnchantment(
            Registry<Enchantment> registry,
            RandomSource random,
            SkillTradeDefinition definition,
            ItemStack stack,
            int skillValue,
            SkillTradeEnchantments enchantments) {
        List<Holder.Reference<Enchantment>> candidates = enchantments.candidates()
                .stream()
                .map(registry::getHolder)
                .flatMap(java.util.Optional::stream)
                .filter(enchantment -> canApply(stack, enchantment))
                .toList();
        if (candidates.isEmpty()) {
            LOGGER.warn("Villager Retaliation skill trade {} had no compatible random enchantment candidates for {}.",
                    definition.id(), stack.getItem());
            return stack;
        }

        Holder.Reference<Enchantment> selected = candidates.get(random.nextInt(candidates.size()));
        int level = enchantmentLevel(skillValue, selected, enchantments);
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(selected, level));
        }
        stack.enchant(selected, level);
        return stack;
    }

    private static ItemStack applyFixedEnchantments(
            Registry<Enchantment> registry,
            SkillTradeDefinition definition,
            ItemStack stack,
            int skillValue,
            SkillTradeEnchantments enchantments) {
        Map<ResourceLocation, Integer> fixedLevels = enchantments.fixedLevels();
        List<ResourceLocation> ids = fixedLevels.isEmpty() ? enchantments.candidates() : List.copyOf(fixedLevels.keySet());
        for (ResourceLocation id : ids) {
            Holder.Reference<Enchantment> enchantment = registry.getHolder(id).orElse(null);
            if (enchantment == null) {
                LOGGER.warn("Villager Retaliation skill trade {} references unknown enchantment {}.", definition.id(), id);
                continue;
            }
            if (!canApply(stack, enchantment)) {
                LOGGER.warn("Villager Retaliation skill trade {} skipped incompatible enchantment {} for {}.",
                        definition.id(), id, stack.getItem());
                continue;
            }

            int configuredLevel = fixedLevels.getOrDefault(id, 0);
            int level = configuredLevel > 0
                    ? cappedEnchantmentLevel(configuredLevel, enchantment, enchantments)
                    : enchantmentLevel(skillValue, enchantment, enchantments);
            if (stack.is(Items.ENCHANTED_BOOK)) {
                return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
            }
            stack.enchant(enchantment, level);
        }
        return stack;
    }

    private static boolean canApply(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(stack);
    }

    private static int enchantmentLevel(
            int skillValue,
            Holder<Enchantment> enchantment,
            SkillTradeEnchantments enchantments) {
        int requestedLevel;
        if (enchantments.levelBySkill()) {
            requestedLevel = skillValue >= 90
                    ? enchantments.maxLevel()
                    : skillValue >= 72
                    ? Math.max(enchantments.minLevel(), Math.min(enchantments.maxLevel(), 2))
                    : enchantments.minLevel();
        } else {
            requestedLevel = enchantments.minLevel();
        }
        return cappedEnchantmentLevel(requestedLevel, enchantment, enchantments);
    }

    private static int cappedEnchantmentLevel(
            int requestedLevel,
            Holder<Enchantment> enchantment,
            SkillTradeEnchantments enchantments) {
        int cap = Math.min(enchantments.maxLevel(), VillagerRetaliationConfig.SKILL_TRADE_MAX_ENCHANTMENT_LEVEL.get());
        cap = Math.min(cap, enchantment.value().getMaxLevel());
        int max = Math.max(1, cap);
        int min = Math.min(Math.max(1, enchantments.minLevel()), max);
        return Math.clamp(requestedLevel, min, max);
    }

    @FunctionalInterface
    private interface DefinitionFilter {
        boolean matches(SkillTradeDefinition definition);
    }

    private record ResolvedDefinition(SkillTradeDefinition definition, int skillValue) {
    }
}
