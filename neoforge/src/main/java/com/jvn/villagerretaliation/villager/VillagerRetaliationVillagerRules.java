package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRetaliationVillagerRules {
    private static final long CREEPER_THREAT_CACHE_TICKS = 10L;
    private static final Map<UUID, CachedCreeperThreat> CREEPER_THREAT_CACHE = new HashMap<>();

    private VillagerRetaliationVillagerRules() {
    }

    public static void clearCachedChecks() {
        CREEPER_THREAT_CACHE.clear();
    }

    public static void clearCachedChecks(Villager villager) {
        CREEPER_THREAT_CACHE.remove(villager.getUUID());
    }

    public static boolean shouldKeepFleeingBehavior(Villager villager) {
        if (villager.isBaby()) {
            return true;
        }
        if (villager.getVillagerData().getProfession() != VillagerProfession.NITWIT
                || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerInventoryAccess.hasUsableWeapon(villager)) {
            return false;
        }

        return !canPickUpGroundWeapons() || !VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        return shouldSuppressFleeingBehavior(villager, shouldKeepFleeingBehavior(villager));
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager, boolean keepFleeingBehavior) {
        if (keepFleeingBehavior) {
            return false;
        }
        if (!canStandGroundAgainstHostileMobs(villager)) {
            return false;
        }
        if (!VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
            return true;
        }

        return !hasCachedVisibleCreeperThreat(
                villager,
                VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get()
        );
    }

    public static boolean canStandGroundAgainstHostileMobs(Villager villager) {
        if (!VillagerRetaliationConfig.VILLAGERS_STAND_GROUND_AGAINST_HOSTILE_MOBS.get()) {
            return false;
        }

        return VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerRetaliationVillagerWeapons.hasTrackedPickup(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerInventoryAccess.hasUsableWeapon(villager)
                || canPickUpGroundWeapons() && VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }

    private static boolean canPickUpGroundWeapons() {
        return VillagerRetaliationConfig.VILLAGERS_PICK_UP_GROUND_WEAPONS.get();
    }

    private static boolean hasCachedVisibleCreeperThreat(Villager villager, double radius) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return VillagerRetaliationVillagerCombatUtil.hasVisibleCreeperThreat(villager, radius);
        }

        UUID villagerId = villager.getUUID();
        long gameTime = level.getGameTime();
        CachedCreeperThreat cached = CREEPER_THREAT_CACHE.get(villagerId);
        if (cached != null && cached.expiresGameTime() > gameTime) {
            return cached.visible();
        }
        if (cached == null) {
            long firstScan = gameTime + scanStagger(villagerId, CREEPER_THREAT_CACHE_TICKS);
            if (firstScan > gameTime) {
                CREEPER_THREAT_CACHE.put(villagerId, new CachedCreeperThreat(true, firstScan));
                return true;
            }
        }

        boolean visible = VillagerRetaliationVillagerCombatUtil.hasVisibleCreeperThreat(villager, radius);
        CREEPER_THREAT_CACHE.put(villagerId, new CachedCreeperThreat(visible, gameTime + CREEPER_THREAT_CACHE_TICKS));
        return visible;
    }

    private static long scanStagger(UUID villagerId, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return 0L;
        }
        return Math.floorMod(villagerId.getMostSignificantBits() ^ villagerId.getLeastSignificantBits(), intervalTicks);
    }

    private record CachedCreeperThreat(boolean visible, long expiresGameTime) {
    }
}
