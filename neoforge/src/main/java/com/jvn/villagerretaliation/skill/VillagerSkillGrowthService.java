package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

public final class VillagerSkillGrowthService {
    private VillagerSkillGrowthService() {
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player) {
        return onTradeCompleted(level, villager, player, null, 1);
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player,
            int completedTrades) {
        return onTradeCompleted(level, villager, player, null, completedTrades);
    }

    public static VillagerSkillGrowthResult onTradeCompleted(
            ServerLevel level,
            AbstractVillager villager,
            @Nullable ServerPlayer player,
            @Nullable MerchantOffer offer,
            int completedTrades) {
        if (!(villager instanceof Villager villageResident) || villageResident.isBaby()) {
            return VillagerSkillGrowthResult.NONE;
        }

        int tradeCount = Math.max(1, completedTrades);
        int currentTradeLevel = Math.clamp(villageResident.getVillagerData().getLevel(), 1, 5);
        VillagerProfile profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        int previousAwardedLevel = profile.highestSkillGrowthTradeLevelAwarded();
        VillagerSkill primary = VillagerProfessionSkills.primarySkill(villager);
        long dayIndex = Math.floorDiv(level.getServer().overworld().getDayTime(), 24_000L);
        boolean profileChanged = migrateRegularTradeProgress(profile, level.getGameTime());
        List<VillagerSkillGrowthResult.SkillIncrease> increases = new ArrayList<>();

        if (VillagerRetaliationConfig.ENABLE_REGULAR_TRADE_SKILL_GROWTH.get()) {
            double xpPerTrade = Math.max(0.0D, VillagerRetaliationConfig.REGULAR_TRADE_SKILL_GROWTH_AMOUNT.get());
            if (Double.isFinite(xpPerTrade) && xpPerTrade > 0.0D) {
                VillagerSkillProgressionResult regular = VillagerSkillProgressionService.apply(
                        profile,
                        List.of(new VillagerSkillPractice(
                                primary,
                                1.0D,
                                "trade:regular_offer",
                                offerRepetitionKey(offer),
                                tradeCount)),
                        dayIndex,
                        level.getGameTime(),
                        xpPerTrade);
                profileChanged |= regular.profileChanged();
                appendIncreases(increases, regular, primary);
            }
        }

        if (VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FROM_TRADING_LEVELS.get()
                && currentTradeLevel > previousAwardedLevel) {
            RandomSource random = villager.getRandom();
            for (int milestone = previousAwardedLevel + 1; milestone <= currentTradeLevel; milestone++) {
                int primaryPoints = primaryGrowthAmount(milestone, random);
                if (primaryPoints > 0) {
                    double pointEquivalentXp = VillagerSkillProgressionService.requiredXp(profile.skills().get(primary))
                            * primaryPoints;
                    VillagerSkillProgressionResult milestoneResult = VillagerSkillProgressionService.apply(
                            profile,
                            List.of(new VillagerSkillPractice(
                                    primary, pointEquivalentXp, "trade:level_milestone", milestone)),
                            dayIndex,
                            level.getGameTime(),
                            1.0D);
                    profileChanged |= milestoneResult.profileChanged();
                    appendIncreases(increases, milestoneResult, primary);
                }
            }
            profileChanged |= profile.markSkillGrowthTradeLevelAwarded(currentTradeLevel, level.getGameTime());
        }

        VillagerSkillGrowthResult result = new VillagerSkillGrowthResult(previousAwardedLevel, currentTradeLevel, increases);
        if (profileChanged) {
            VillagerProfileSavedData.get(level).setDirty();
            if (player != null && result.changed()) {
                VillagerReputationNetworking.sendProfile(player, villager, profile);
                sendFeedback(player, villager, result);
            }
        }
        return result;
    }

    static boolean migrateRegularTradeProgress(VillagerProfile profile, long gameTime) {
        boolean changed = false;
        for (VillagerSkill skill : VillagerSkill.values()) {
            double oldFraction = profile.regularTradeSkillGrowthProgress(skill);
            if (!Double.isFinite(oldFraction) || oldFraction <= 0.0D) {
                continue;
            }
            double equivalentXp = oldFraction * VillagerSkillProgressionService.requiredXp(profile.skills().get(skill));
            changed |= profile.setSkillPracticeXp(skill, profile.skillPracticeXp(skill) + equivalentXp, gameTime);
            changed |= profile.setRegularTradeSkillGrowthProgress(skill, 0.0D, gameTime);
        }
        return changed;
    }

    public static long offerRepetitionKey(@Nullable MerchantOffer offer) {
        if (offer == null) {
            return 0L;
        }
        long signature = mix64(stackSignature(offer.getBaseCostA()));
        signature = mix64(signature ^ Long.rotateLeft(stackSignature(offer.getCostB()), 21));
        return mix64(signature ^ Long.rotateLeft(stackSignature(offer.getResult()), 42));
    }

    private static long stackSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        long itemHash = Integer.toUnsignedLong(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode());
        long countHash = Integer.toUnsignedLong(stack.getCount());
        long componentHash = Integer.toUnsignedLong(stack.getComponents().hashCode());
        return mix64(itemHash ^ Long.rotateLeft(countHash, 23) ^ Long.rotateLeft(componentHash, 41));
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return value;
    }

    private static void appendIncreases(
            List<VillagerSkillGrowthResult.SkillIncrease> destination,
            VillagerSkillProgressionResult result,
            VillagerSkill primary) {
        for (VillagerSkillProgressionResult.SkillIncrease increase : result.increases()) {
            destination.add(new VillagerSkillGrowthResult.SkillIncrease(
                    increase.skill(), increase.amount(), increase.newValue(), increase.skill() == primary));
        }
    }

    private static int primaryGrowthAmount(int tradeLevel, RandomSource random) {
        int configuredMin = Math.clamp(VillagerRetaliationConfig.SKILL_GROWTH_PRIMARY_MIN.get(), 0, 10);
        int configuredMax = Math.clamp(VillagerRetaliationConfig.SKILL_GROWTH_PRIMARY_MAX.get(), configuredMin, 10);
        int milestoneMin = switch (tradeLevel) {
            case 2 -> configuredMin;
            case 3, 4 -> Math.min(configuredMax, configuredMin + 1);
            case 5 -> Math.min(configuredMax, configuredMin + 2);
            default -> configuredMin;
        };
        int milestoneMax = switch (tradeLevel) {
            case 2 -> Math.min(configuredMax, Math.max(configuredMin, 2));
            case 3 -> Math.min(configuredMax, Math.max(configuredMin, 3));
            case 4 -> Math.min(configuredMax, Math.max(configuredMin, 4));
            case 5 -> configuredMax;
            default -> configuredMin;
        };
        return milestoneMax <= milestoneMin
                ? milestoneMin
                : milestoneMin + random.nextInt(milestoneMax - milestoneMin + 1);
    }

    private static void sendFeedback(
            ServerPlayer player,
            AbstractVillager villager,
            VillagerSkillGrowthResult result) {
        if (!VillagerRetaliationConfig.ENABLE_SKILL_GROWTH_FEEDBACK.get()) {
            return;
        }
        result.primaryIncrease()
                .or(() -> result.increases().stream().findFirst())
                .ifPresent(increase -> player.displayClientMessage(
                        Component.translatable(
                                "villagerretaliation.skill_growth.improved",
                                VillagerPresetNameRegistry.resolveDisplayName(villager),
                                Component.translatable(increase.skill().translationKey())),
                        true));
    }
}
