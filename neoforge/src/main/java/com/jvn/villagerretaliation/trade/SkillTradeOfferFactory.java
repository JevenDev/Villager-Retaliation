package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
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
    private static final int DUPLICATE_RESULT_ATTEMPTS = 16;

    private SkillTradeOfferFactory() {
    }

    @Nullable
    public static MerchantOffer createVillagerOffer(
            ServerLevel level,
            AbstractVillager villager,
            ResourceLocation professionId,
            int villagerLevel,
            RandomSource random) {
        OfferSelection selection = createRememberedVillagerSelection(
                level,
                villager,
                professionId,
                random,
                definition -> definition.matchesVillager(professionId, villagerLevel),
                true,
                List.of());
        return selection == null ? null : selection.offer();
    }

    @Nullable
    public static MerchantOffer createVillagerRefreshOffer(
            ServerLevel level,
            AbstractVillager villager,
            ResourceLocation professionId,
            int villagerLevel,
            RandomSource random,
            List<ItemStack> excludedResultStacks) {
        OfferSelection selection = createRememberedVillagerSelection(
                level,
                villager,
                professionId,
                random,
                definition -> definition.matchesVillager(professionId, villagerLevel),
                false,
                excludedResultStacks);
        return selection == null ? null : selection.offer();
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
        OfferSelection selection = createOfferSelection(level, villager, random, filter, true, List.of(), Set.of());
        return selection == null ? null : selection.offer();
    }

    @Nullable
    public static MerchantOffer createVillagerOfferFromDefinition(
            ServerLevel level,
            AbstractVillager villager,
            SkillTradeDefinition definition,
            RandomSource random,
            List<ItemStack> excludedResultStacks) {
        return createVillagerOfferFromDefinition(level, villager, definition, random, excludedResultStacks, false);
    }

    @Nullable
    public static MerchantOffer createVillagerSpecialOrderOfferFromDefinition(
            ServerLevel level,
            AbstractVillager villager,
            SkillTradeDefinition definition,
            RandomSource random) {
        return createVillagerOfferFromDefinition(level, villager, definition, random, List.of(), true);
    }

    public static boolean isSkillUnlockedForSpecialOrder(
            ServerLevel level,
            AbstractVillager villager,
            SkillTradeDefinition definition) {
        return bestSkillValue(level, villager, definition) >= definition.minRank().minInclusive();
    }

    @Nullable
    private static MerchantOffer createVillagerOfferFromDefinition(
            ServerLevel level,
            AbstractVillager villager,
            SkillTradeDefinition definition,
            RandomSource random,
            List<ItemStack> excludedResultStacks,
            boolean ignoreMaxRank) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()
                || definition == null
                || !definition.conditions().matches()) {
            return null;
        }

        int skillValue = bestSkillValue(level, villager, definition);
        if (!isSkillEligible(definition, skillValue, ignoreMaxRank)) {
            return null;
        }

        SkillTradeScalingContext context = new SkillTradeScalingContext(definition, skillValue);
        return createSelectedOffer(level, random, definition, context, excludedResultStacks);
    }

    @Nullable
    private static OfferSelection createRememberedVillagerSelection(
            ServerLevel level,
            AbstractVillager villager,
            ResourceLocation professionId,
            RandomSource random,
            DefinitionFilter filter,
            boolean requireChanceRoll,
            List<ItemStack> excludedResultStacks) {
        Set<ResourceLocation> knownDefinitionIds = villager instanceof Villager resident
                ? Set.copyOf(VillagerTradeMemory.knownDefinitionIds(resident, professionId))
                : Set.of();
        OfferSelection selection = null;
        if (!knownDefinitionIds.isEmpty()) {
            selection = createOfferSelection(level, villager, random, filter, false, excludedResultStacks, knownDefinitionIds);
        }
        if (selection == null) {
            selection = createOfferSelection(level, villager, random, filter, requireChanceRoll, excludedResultStacks, Set.of());
        }
        if (selection != null && villager instanceof Villager resident) {
            VillagerTradeMemory.rememberDefinition(level, resident, professionId, selection.definition().id());
        }
        return selection;
    }

    @Nullable
    private static OfferSelection createOfferSelection(
            ServerLevel level,
            AbstractVillager villager,
            RandomSource random,
            DefinitionFilter filter,
            boolean requireChanceRoll,
            List<ItemStack> excludedResultStacks,
            Set<ResourceLocation> onlyDefinitionIds) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_TRADE_OVERHAUL.get()) {
            return null;
        }

        List<ResolvedDefinition> candidates = new ArrayList<>();
        for (SkillTradeDefinition definition : SkillTradeResources.definitions(level.getServer())) {
            if (!filter.matches(definition) || !definition.conditions().matches()) {
                continue;
            }
            if (!onlyDefinitionIds.isEmpty() && !onlyDefinitionIds.contains(definition.id())) {
                continue;
            }

            int skillValue = bestSkillValue(level, villager, definition);
            SkillTradeScalingContext context = new SkillTradeScalingContext(definition, skillValue);
            if (!definition.isSkillEligible(skillValue) || (requireChanceRoll && !passesChance(context, random))) {
                continue;
            }
            candidates.add(new ResolvedDefinition(definition, context));
        }
        if (candidates.isEmpty()) {
            return null;
        }

        List<ResolvedDefinition> remaining = new ArrayList<>(candidates);
        while (!remaining.isEmpty()) {
            ResolvedDefinition selected = selectWeighted(remaining, random);
            MerchantOffer offer = createSelectedOffer(level, random, selected.definition(), selected.context(), excludedResultStacks);
            if (offer != null) {
                return new OfferSelection(offer, selected.definition());
            }
            remaining.remove(selected);
        }
        return null;
    }

    public static int bestSkillValue(ServerLevel level, AbstractVillager villager, SkillTradeDefinition definition) {
        int best = 0;
        for (VillagerSkill skill : definition.skills()) {
            best = Math.max(best, VillagerProfileManager.getSkill(level, villager, skill));
        }
        return best;
    }

    private static boolean isSkillEligible(SkillTradeDefinition definition, int skillValue, boolean ignoreMaxRank) {
        return ignoreMaxRank
                ? skillValue >= definition.minRank().minInclusive()
                : definition.isSkillEligible(skillValue);
    }

    private static boolean passesChance(SkillTradeScalingContext context, RandomSource random) {
        return random.nextDouble() < SkillTradeQualityScaler.rareChance(context);
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
            SkillTradeScalingContext context,
            List<ItemStack> excludedResultStacks) {
        int attempts = excludedResultStacks.isEmpty() ? 1 : DUPLICATE_RESULT_ATTEMPTS;
        for (int attempt = 0; attempt < attempts; attempt++) {
            ItemStack result = definition.result().createBaseStack(random);
            if (result.isEmpty()) {
                return null;
            }

            result.setCount(SkillTradeQualityScaler.resultCount(context, result.getCount()));
            result = applyEnchantments(level, random, definition, result, context);
            if (matchesExcludedResult(result, excludedResultStacks)) {
                continue;
            }

            int baseCostCount = definition.cost().countForSkill(context.skillValue(), definition.minRank().minInclusive());
            int costCount = SkillTradeQualityScaler.currencyCost(level.getServer(), context, definition.cost().item(), baseCostCount);
            int baseMaxUses = definition.maxUses().valueForSkill(context.skillValue(), definition.minRank().minInclusive());
            int maxUses = SkillTradeQualityScaler.maxUses(context, baseMaxUses);
            int xp = SkillTradeQualityScaler.xp(context, definition.xp());
            return new MerchantOffer(
                    new ItemCost(definition.cost().item(), costCount),
                    result,
                    maxUses,
                    xp,
                    definition.priceMultiplier()
            );
        }
        return null;
    }

    private static boolean matchesExcludedResult(ItemStack result, List<ItemStack> excludedResultStacks) {
        for (ItemStack excluded : excludedResultStacks) {
            if (!excluded.isEmpty() && ItemStack.isSameItemSameComponents(result, excluded)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack applyEnchantments(
            ServerLevel level,
            RandomSource random,
            SkillTradeDefinition definition,
            ItemStack stack,
            SkillTradeScalingContext context) {
        SkillTradeEnchantments enchantments = definition.result().enchantments();
        if (!enchantments.enabled() || stack.isEmpty()) {
            return stack;
        }

        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return switch (enchantments.mode()) {
            case NONE -> stack;
            case RANDOM_FROM -> applyRandomEnchantment(registry, random, definition, stack, context, enchantments);
            case FIXED -> applyFixedEnchantments(registry, definition, stack, context, enchantments);
        };
    }

    private static ItemStack applyRandomEnchantment(
            Registry<Enchantment> registry,
            RandomSource random,
            SkillTradeDefinition definition,
            ItemStack stack,
            SkillTradeScalingContext context,
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
        int level = enchantmentLevel(context, selected, enchantments);
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
            SkillTradeScalingContext context,
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
                    : enchantmentLevel(context, enchantment, enchantments);
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
            SkillTradeScalingContext context,
            Holder<Enchantment> enchantment,
            SkillTradeEnchantments enchantments) {
        int requestedLevel = SkillTradeQualityScaler.requestedEnchantmentLevel(context, enchantments);
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

    private record ResolvedDefinition(SkillTradeDefinition definition, SkillTradeScalingContext context) {
    }

    private record OfferSelection(MerchantOffer offer, SkillTradeDefinition definition) {
    }
}
