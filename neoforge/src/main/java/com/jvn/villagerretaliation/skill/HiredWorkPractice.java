package com.jvn.villagerretaliation.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Role-specific normalization constants for successful hired work. */
public final class HiredWorkPractice {
    private HiredWorkPractice() {
    }

    public static List<VillagerSkillPractice> mining(ServerLevel level, BlockPos pos, BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        double hardness = Math.max(0.0D, state.getDestroySpeed(level, pos));
        double oreBonus = blockId.contains("ore") || blockId.contains("ancient_debris") ? 0.35D : 0.0D;
        double units = Math.clamp(0.65D + hardness * 0.18D + oreBonus, 0.5D, 2.0D);
        return List.of(new VillagerSkillPractice(VillagerSkill.MINING, units, "hired:mining:block", blockId.hashCode()));
    }

    public static List<VillagerSkillPractice> logging(int logsCut) {
        int logs = Math.max(1, logsCut);
        double units = Math.min(5.0D, 0.5D + logs * 0.25D);
        return List.of(new VillagerSkillPractice(VillagerSkill.GATHERING, units, "hired:logging:tree", sizeBucket(logs)));
    }

    public static List<VillagerSkillPractice> farming(String action) {
        double units = switch (action) {
            case "harvest_replant" -> 1.25D;
            case "harvest" -> 1.0D;
            case "plant" -> 0.55D;
            case "till" -> 0.30D;
            default -> throw new IllegalArgumentException("Unknown farming practice action: " + action);
        };
        return List.of(new VillagerSkillPractice(VillagerSkill.FARMING, units, "hired:farming:" + action, action.hashCode()));
    }

    public static List<VillagerSkillPractice> builderPlacement(BlockState state) {
        long key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode();
        return List.of(
                new VillagerSkillPractice(VillagerSkill.MASONRY, 0.70D, "hired:builder:place", key),
                new VillagerSkillPractice(VillagerSkill.CRAFTING, 0.30D, "hired:builder:place", key));
    }

    public static List<VillagerSkillPractice> builderClearing(BlockState state) {
        long key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode();
        return List.of(new VillagerSkillPractice(VillagerSkill.GATHERING, 0.35D, "hired:builder:clear", key));
    }

    public static List<VillagerSkillPractice> courier(int deliveredItems, double routeDistance) {
        int count = Math.max(1, deliveredItems);
        double distanceFactor = Math.clamp(routeDistance / 32.0D, 0.35D, 2.0D);
        double units = Math.min(6.0D, Math.sqrt(count) * 0.35D * distanceFactor);
        long key = 31L * sizeBucket(count) + distanceBucket(routeDistance);
        return List.of(
                new VillagerSkillPractice(VillagerSkill.GATHERING, units * 0.7D, "hired:courier:delivery", key),
                new VillagerSkillPractice(VillagerSkill.SURVIVAL, units * 0.3D, "hired:courier:delivery", key));
    }

    public static List<VillagerSkillPractice> fishing(ItemStack caught) {
        String itemId = caught == null || caught.isEmpty()
                ? "catch"
                : BuiltInRegistries.ITEM.getKey(caught.getItem()).toString();
        double rarityBonus = itemId.contains("enchanted_book")
                || itemId.contains("name_tag")
                || itemId.contains("nautilus_shell")
                || itemId.contains("saddle") ? 0.35D : 0.0D;
        double units = caught == null || caught.isEmpty()
                ? 0.8D
                : Math.min(1.5D, 0.75D + caught.getCount() * 0.15D + rarityBonus);
        return List.of(new VillagerSkillPractice(VillagerSkill.FISHING, units, "hired:fishing:catch", itemId.hashCode()));
    }

    public static List<VillagerSkillPractice> fishing(double units, long catchCategory) {
        return List.of(new VillagerSkillPractice(
                VillagerSkill.FISHING,
                Math.clamp(units, 0.25D, 3.0D),
                "hired:fishing:catch",
                catchCategory));
    }

    public static List<VillagerSkillPractice> combatKill(boolean ranged, boolean hunting, double threat, long targetCategory) {
        double units = Math.clamp(threat, 0.4D, 2.0D);
        List<VillagerSkillPractice> practice = new ArrayList<>();
        practice.add(new VillagerSkillPractice(
                ranged ? VillagerSkill.ARCHERY : VillagerSkill.GUARDING,
                units,
                ranged ? "hired:combat:ranged_kill" : "hired:combat:melee_kill",
                targetCategory));
        if (hunting) {
            practice.add(new VillagerSkillPractice(
                    VillagerSkill.SURVIVAL, units * 0.4D, "hired:hunting:kill", targetCategory));
        }
        return List.copyOf(practice);
    }

    public static List<VillagerSkillPractice> batch(VillagerSkill skill, String source, int outputCount, long key) {
        double units = Math.min(6.0D, Math.max(1, outputCount) * 0.35D);
        return List.of(new VillagerSkillPractice(skill, units, source, key));
    }

    public static List<VillagerSkillPractice> animal(String action, double units, long key) {
        return List.of(new VillagerSkillPractice(VillagerSkill.ANIMAL_HANDLING, units, "hired:animals:" + action, key));
    }

    public static List<VillagerSkillPractice> nitwit(long key) {
        return List.of(new VillagerSkillPractice(VillagerSkill.DIPLOMACY, 0.15D, "hired:nitwit:report", key));
    }

    public static List<VillagerSkillPractice> combat(boolean ranged, boolean hunting, double damage, long targetCategory) {
        double units = Math.clamp(damage / 6.0D, 0.15D, 2.0D);
        List<VillagerSkillPractice> practice = new ArrayList<>();
        practice.add(new VillagerSkillPractice(
                ranged ? VillagerSkill.ARCHERY : VillagerSkill.GUARDING,
                units,
                ranged ? "hired:combat:ranged_damage" : "hired:combat:melee_damage",
                targetCategory));
        if (hunting) {
            practice.add(new VillagerSkillPractice(VillagerSkill.SURVIVAL, units * 0.3D, "hired:hunting:damage", targetCategory));
        }
        return List.copyOf(practice);
    }

    private static int sizeBucket(int count) {
        return count <= 1 ? 1 : count <= 4 ? 4 : count <= 16 ? 16 : count <= 64 ? 64 : 65;
    }

    private static int distanceBucket(double distance) {
        return distance < 8.0D ? 1 : distance < 24.0D ? 2 : distance < 64.0D ? 3 : 4;
    }
}
