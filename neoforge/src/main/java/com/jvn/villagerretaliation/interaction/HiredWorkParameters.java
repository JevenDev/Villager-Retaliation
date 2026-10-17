package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class HiredWorkParameters {
    static final int MIN_WORK_RADIUS = 4;
    private static final int SKILL_RADIUS_BASELINE = 50;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;
    private static final int HUNTING_WORK_RADIUS = 64;
    private static final int FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS = 10;
    private static final int FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS = 4;

    private HiredWorkParameters() {
    }

    static HiredJobSite jobSite(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            int maxRadius) {
        var duty = com.jvn.villagerretaliation.settlement.SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) {
            if (role == HiredVillagerRole.COURIER) {
                int radius = duty.town().radius();
                BlockPos center = duty.town().center();
                return HiredJobSite.fromWorkArea(HiredWorkArea.fromBounds(
                        new BlockPos(center.getX() - radius, level.getMinBuildHeight(), center.getZ() - radius),
                        new BlockPos(center.getX() + radius, level.getMaxBuildHeight() - 1, center.getZ() + radius), true));
            }
            return HiredJobSite.fromWorkArea(HiredWorkArea.fromBounds(duty.area().min(), duty.area().max(), true));
        }
        HiredWorkArea storedArea = HiredWorkStateStore.workAreaWithinMax(state, villager, maxRadius);
        if (role == HiredVillagerRole.BUILDER) {
            return HiredJobSite.fromWorkArea(storedArea.asUsable(false));
        }
        BlockPos claimedJobSite = role == HiredVillagerRole.FARMING ? HiredWorkAreaNavigation.claimedJobSitePos(level, villager) : null;
        if (storedArea.usable()) {
            return claimedJobSite == null
                    ? HiredJobSite.fromWorkArea(storedArea)
                    : HiredJobSite.withAnchor(
                            storedArea,
                            claimedJobSite,
                            HiredJobSite.AnchorSource.VANILLA_JOB_SITE,
                            FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS,
                            FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS);
        }
        if (claimedJobSite != null) {
            return HiredJobSite.fromAnchor(
                    claimedJobSite,
                    FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS,
                    FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS,
                    HiredJobSite.AnchorSource.VANILLA_JOB_SITE);
        }
        return HiredJobSite.fromWorkArea(storedArea);
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return maxWorkRadius(level, villager, role, HiredVillagerRoles.roleScore(level, villager, role));
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role, int roleScore) {
        return maxWorkRadiusForScore(
                role,
                roleScore,
                VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get());
    }

    public static int maxWorkRadiusForScore(
            HiredVillagerRole role,
            int roleScore,
            int configuredDefaultRadius,
            int configuredMaxRadius) {
        if (role == HiredVillagerRole.HUNTING) {
            return HUNTING_WORK_RADIUS;
        }
        int base = Mth.clamp(configuredDefaultRadius, MIN_WORK_RADIUS, MAX_SKILLED_WORK_RADIUS);
        int max = Mth.clamp(configuredMaxRadius, base, MAX_SKILLED_WORK_RADIUS);
        int score = Mth.clamp(roleScore, 0, 100);
        double progress = Math.max(0.0D, Math.min(1.0D, (score - SKILL_RADIUS_BASELINE) / 50.0D));
        return Mth.clamp(base + (int) Math.round((max - base) * progress), MIN_WORK_RADIUS, max);
    }

    static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        return efficiencyPercent(level, villager, role, state, inventory, HiredVillagerRoles.roleScore(level, villager, role));
    }

    static int efficiencyPercent(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            HiredJobInventory inventory,
            int roleScore) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int base = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get());
        int skillWorkSpeed = HiredVillagerRoles.roleCadencePercent(role, roleScore);
        VillagerMoodState mood = VillagerMoodService.mood(level, villager);
        int moodModifier = switch (mood.primaryMood()) {
            case CONTENT, GRATEFUL, PROUD, HOPEFUL -> 8;
            case ANGRY, AFRAID, STRESSED, GRIEVING -> -15;
            case SUSPICIOUS, LONELY -> -8;
            default -> 0;
        };
        ItemStack tool = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        boolean missingRequiredTool = (role == HiredVillagerRole.MINING
                || role == HiredVillagerRole.LOGGING
                || role == HiredVillagerRole.FARMING
                || role == HiredVillagerRole.FISHING) && tool.isEmpty();
        int efficiency = calculateEfficiencyPercent(base, skillWorkSpeed, moodModifier, missingRequiredTool, min, max);
        return Math.max(1, (int) Math.round(efficiency
                * com.jvn.villagerretaliation.settlement.SettlementFoodService.workMultiplier(level, villager)));
    }

    public static int calculateEfficiencyPercent(
            int configuredBase,
            int skillWorkSpeedPercent,
            int moodModifier,
            boolean missingRequiredTool,
            int configuredMinimum,
            int configuredMaximum) {
        int minimum = Math.max(1, configuredMinimum);
        int maximum = Math.max(minimum, configuredMaximum);
        int efficiency = Math.round(Math.max(1, configuredBase)
                * Math.clamp(skillWorkSpeedPercent, 50, 125) / 100.0F);
        efficiency += moodModifier;
        if (missingRequiredTool) {
            efficiency -= 20;
        }
        return Mth.clamp(efficiency, minimum, maximum);
    }

    public static int effectiveWorkTickInterval(int efficiency) {
        int base = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        return effectiveWorkTickInterval(base, efficiency);
    }

    public static int effectiveWorkTickInterval(int baseInterval, int efficiency) {
        int base = Math.max(10, baseInterval);
        return Mth.clamp(Math.round(base * 100.0F / Math.max(1.0F, efficiency)), 5, base * 4);
    }

    public static int completedTaskCooldownTicks(int efficiency) {
        return Mth.clamp(Math.round(80.0F * 100.0F / Math.max(25.0F, efficiency)), 10, 200);
    }
}
