package com.jvn.villagerretaliation.trade;

import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record SkillTradeDefinition(
        ResourceLocation id,
        Set<ResourceLocation> professions,
        Set<VillagerSkill> skills,
        VillagerSkillRank minRank,
        VillagerSkillRank maxRank,
        int villagerLevel,
        double chance,
        int weight,
        SkillTradeCost cost,
        SkillTradeResult result,
        SkillTradeMaxUses maxUses,
        int xp,
        float priceMultiplier,
        SkillTradeConditions conditions,
        SkillTradeQualityScaling qualityScaling,
        SkillTradePool pool) {
    public static final ResourceLocation WANDERING_TRADER_PROFESSION = ResourceLocation.withDefaultNamespace("wandering_trader");

    public SkillTradeDefinition {
        professions = professions == null ? Set.of() : Set.copyOf(professions);
        skills = skills == null ? Set.of() : Set.copyOf(skills);
        minRank = minRank == null ? VillagerSkillRank.NOVICE : minRank;
        villagerLevel = Math.clamp(villagerLevel, 1, 5);
        chance = Math.clamp(chance, 0.0D, 1.0D);
        weight = Math.clamp(weight, 1, 10_000);
        cost = cost == null ? SkillTradeCost.DEFAULT : cost;
        maxUses = maxUses == null ? SkillTradeMaxUses.DEFAULT : maxUses;
        xp = Math.clamp(xp, 0, 10_000);
        priceMultiplier = Math.clamp(priceMultiplier, 0.0F, 1.0F);
        conditions = conditions == null ? SkillTradeConditions.EMPTY : conditions;
        qualityScaling = qualityScaling == null ? SkillTradeQualityScaling.DISABLED : qualityScaling;
        pool = pool == null ? SkillTradePool.VILLAGER : pool;
    }

    public boolean matchesVillager(ResourceLocation professionId, int level) {
        return this.pool == SkillTradePool.VILLAGER
                && this.villagerLevel == level
                && (this.professions.isEmpty() || this.professions.contains(professionId));
    }

    public boolean matchesWanderingTrader(SkillTradePool requestedPool) {
        return this.pool == requestedPool
                && (this.professions.isEmpty() || this.professions.contains(WANDERING_TRADER_PROFESSION));
    }

    public boolean isSkillEligible(int skillValue) {
        return skillValue >= this.minRank.minInclusive()
                && (this.maxRank == null || skillValue <= this.maxRank.maxInclusive());
    }
}
