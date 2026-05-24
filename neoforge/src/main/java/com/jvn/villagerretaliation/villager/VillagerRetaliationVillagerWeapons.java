package com.jvn.villagerretaliation.villager;

import com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerRetaliationVillagerWeapons {
    public static final double WEAPON_SEARCH_RADIUS = 9.0D;
    public static final double WEAPON_PICKUP_REACH_SQR = 2.25D;
    private static final double WEAPON_SEARCH_RADIUS_SQR = WEAPON_SEARCH_RADIUS * WEAPON_SEARCH_RADIUS;
    private static final long WEAPON_SEARCH_CACHE_TICKS = 10L;
    private static final int MAX_WEAPON_SEARCH_CACHE_ENTRIES = 2048;
    private static final Map<UUID, CachedWeaponSearch> NEAREST_WEAPON_CACHE = new HashMap<>();

    private VillagerRetaliationVillagerWeapons() {
    }

    public static boolean hasUsableWeapon(AbstractVillager villager) {
        return isUsableWeapon(villager.getMainHandItem()) || isUsableWeapon(villager.getOffhandItem());
    }

    public static boolean hasTrackedPickup(AbstractVillager villager) {
        return VillagerRetaliationVillagerEquipment.hasPickedUpMainHand(villager);
    }

    public static ItemStack getPrimaryWeapon(AbstractVillager villager) {
        ItemStack mainHand = villager.getMainHandItem();
        if (isUsableWeapon(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = villager.getOffhandItem();
        if (isUsableWeapon(offHand)) {
            return offHand;
        }

        return !mainHand.isEmpty() ? mainHand : offHand;
    }

    public static InteractionHand getHoldingHand(LivingEntity entity, Predicate<ItemStack> predicate) {
        return predicate.test(entity.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static boolean isUsableWeapon(ItemStack stack) {
        return isMeleeWeapon(stack) || isRangedWeapon(stack);
    }

    public static Optional<ItemEntity> findNearestWeapon(AbstractVillager villager) {
        if (!canSearchForGroundWeapon(villager)) {
            clearNearestWeaponCache(villager);
            return Optional.empty();
        }

        UUID villagerId = villager.getUUID();
        long gameTime = villager.level().getGameTime();
        CachedWeaponSearch cached = NEAREST_WEAPON_CACHE.get(villagerId);
        if (cached != null && cached.isValid(gameTime)) {
            if (cached.itemEntity() == null) {
                return Optional.empty();
            }
            if (isCachedWeaponStillUsable(villager, cached.itemEntity())) {
                return Optional.of(cached.itemEntity());
            }
        }

        ItemEntity bestWeapon = findNearestWeaponUncached(villager);
        NEAREST_WEAPON_CACHE.put(villagerId, new CachedWeaponSearch(bestWeapon, gameTime + WEAPON_SEARCH_CACHE_TICKS));
        pruneNearestWeaponCache(gameTime);
        return Optional.ofNullable(bestWeapon);
    }

    private static ItemEntity findNearestWeaponUncached(AbstractVillager villager) {
        AABB searchBox = villager.getBoundingBox().inflate(WEAPON_SEARCH_RADIUS);
        ItemStack equippedWeapon = getPrimaryWeapon(villager);
        ItemEntity bestWeapon = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (ItemEntity itemEntity : villager.level().getEntitiesOfClass(ItemEntity.class, searchBox, VillagerRetaliationVillagerWeapons::canBeWantedGroundWeapon)) {
            ItemStack itemStack = itemEntity.getItem();
            if (!isEligibleVisibleGroundWeapon(villager, itemEntity, equippedWeapon, itemStack)) {
                continue;
            }

            double distanceSqr = villager.distanceToSqr(itemEntity);
            if (distanceSqr >= bestDistanceSqr) {
                continue;
            }

            bestWeapon = itemEntity;
            bestDistanceSqr = distanceSqr;
        }

        return bestWeapon;
    }

    public static void equipGroundWeapon(AbstractVillager villager, ItemEntity itemEntity) {
        if (VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)) {
            return;
        }

        ItemStack groundStack = itemEntity.getItem();
        if (groundStack.isEmpty()) {
            return;
        }

        villager.getNavigation().stop();
        ItemStack equippedStack = groundStack.copyWithCount(1);
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        if (!previousMainHand.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(previousMainHand);
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
        }

        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, 1);
        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, equippedStack);

        groundStack.shrink(1);
        if (groundStack.isEmpty()) {
            itemEntity.discard();
        }
        clearNearestWeaponCache(villager);
    }

    public static void ensurePickedMainHandDrop(AbstractVillager villager, LivingDropsEvent event) {
        ItemStack trackedPickup = VillagerRetaliationVillagerEquipment.pickedUpMainHand(villager);
        if (trackedPickup.isEmpty()) {
            return;
        }
        clearTrackedPickup(villager);

        ItemStack mainHand = villager.getMainHandItem();
        if (!mainHand.isEmpty() && ItemStack.isSameItem(mainHand, trackedPickup)) {
            ToucanLivingDrops.addDropIfNoMatchingItem(event, mainHand.copy());
            return;
        }

        ToucanLivingDrops.addDropIfNoMatchingItem(event, trackedPickup.copy());
    }

    public static boolean maintainAcquiredWeaponAuthority(AbstractVillager villager) {
        if (!VillagerRetaliationVillagerEquipment.hasPickedUpMainHand(villager)) {
            return false;
        }
        return VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
    }

    public static void prepareTrackedPickupForInventory(AbstractVillager villager) {
        ItemStack trackedPickup = VillagerRetaliationVillagerEquipment.pickedUpMainHand(villager);
        if (trackedPickup.isEmpty()) {
            return;
        }

        ItemStack mainHand = villager.getMainHandItem();
        if (!mainHand.isEmpty() && ItemStack.isSameItem(mainHand, trackedPickup)) {
            VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, mainHand);
            return;
        }

        if (!mainHand.isEmpty()) {
            ItemStack displacedMainHand = mainHand.copy();
            ItemStack remainder = villager.getInventory().addItem(displacedMainHand);
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
        }

        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, trackedPickup);
    }

    public static void clearTrackedPickup(AbstractVillager villager) {
        VillagerRetaliationVillagerEquipment.clearPickedUpMainHand(villager);
        clearNearestWeaponCache(villager);
    }

    public static void clearTrackedPickupCache(AbstractVillager villager) {
        VillagerRetaliationVillagerEquipment.clearTrackedMainHandCache(villager);
        clearNearestWeaponCache(villager);
    }

    public static void clearCache() {
        NEAREST_WEAPON_CACHE.clear();
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return isBowWeapon(stack)
                || isCrossbowWeapon(stack)
                || isTridentWeapon(stack);
    }

    public static boolean isBowWeapon(ItemStack stack) {
        return stack.is(Tags.Items.TOOLS_BOW) || stack.getItem() instanceof BowItem;
    }

    public static boolean isCrossbowWeapon(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isTridentWeapon(ItemStack stack) {
        return stack.is(Tags.Items.TOOLS_SPEAR) || stack.is(Items.TRIDENT) || stack.getItem() instanceof TridentItem;
    }

    public static boolean shouldPathfindForWeapon(AbstractVillager villager, ItemStack groundWeapon) {
        return shouldPathfindForWeapon(villager, getPrimaryWeapon(villager), groundWeapon);
    }

    public static boolean canSearchForGroundWeapon(AbstractVillager villager) {
        return villager.isAlive()
                && EventHooks.canEntityGrief(villager.level(), villager)
                && VillagerRetaliationVillagerCombatUtil.getMemoryIfRegistered(
                        villager,
                        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS
                ).isEmpty();
    }

    public static boolean isBetterWeaponChoice(ItemStack candidate, ItemStack current) {
        if (!isUsableWeapon(candidate)) {
            return false;
        }
        if (!isUsableWeapon(current)) {
            return true;
        }

        int candidatePriority = pickupPriority(candidate);
        int currentPriority = pickupPriority(current);
        if (candidatePriority != currentPriority) {
            return candidatePriority < currentPriority;
        }

        int candidateTier = weaponTier(candidate);
        int currentTier = weaponTier(current);
        if (candidateTier != currentTier) {
            return candidateTier > currentTier;
        }

        return candidate.isEnchanted() && !current.isEnchanted();
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(Tags.Items.MELEE_WEAPON_TOOLS)
                || stack.is(Tags.Items.MINING_TOOL_TOOLS)
                || stack.is(Tags.Items.TOOLS_MACE);
    }

    private static boolean canBeWantedGroundWeapon(ItemEntity itemEntity) {
        return !itemEntity.isRemoved() && !itemEntity.getItem().isEmpty();
    }

    private static boolean shouldPathfindForWeapon(AbstractVillager villager, ItemStack equippedWeapon, ItemStack groundWeapon) {
        if (!canSearchForGroundWeapon(villager)
                || !isUsableWeapon(groundWeapon)
                || VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)) {
            return false;
        }

        return isBetterWeaponChoice(groundWeapon, equippedWeapon);
    }

    private static boolean isCachedWeaponStillUsable(AbstractVillager villager, ItemEntity itemEntity) {
        return itemEntity.level() == villager.level()
                && isEligibleVisibleGroundWeapon(villager, itemEntity, getPrimaryWeapon(villager), itemEntity.getItem());
    }

    private static boolean isEligibleVisibleGroundWeapon(
            AbstractVillager villager,
            ItemEntity itemEntity,
            ItemStack equippedWeapon,
            ItemStack groundWeapon
    ) {
        return canBeWantedGroundWeapon(itemEntity)
                && villager.distanceToSqr(itemEntity) <= WEAPON_SEARCH_RADIUS_SQR
                && villager.hasLineOfSight(itemEntity)
                && villager.level().getWorldBorder().isWithinBounds(itemEntity.blockPosition())
                && shouldPathfindForWeapon(villager, equippedWeapon, groundWeapon);
    }

    private static void clearNearestWeaponCache(AbstractVillager villager) {
        NEAREST_WEAPON_CACHE.remove(villager.getUUID());
    }

    private static void pruneNearestWeaponCache(long gameTime) {
        NEAREST_WEAPON_CACHE.entrySet().removeIf(entry -> !entry.getValue().isValid(gameTime));
        if (NEAREST_WEAPON_CACHE.size() <= MAX_WEAPON_SEARCH_CACHE_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<UUID, CachedWeaponSearch>> iterator = NEAREST_WEAPON_CACHE.entrySet().iterator();
        while (NEAREST_WEAPON_CACHE.size() > MAX_WEAPON_SEARCH_CACHE_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static int pickupPriority(ItemStack stack) {
        if (isRangedWeapon(stack)) {
            return 0;
        }
        if (isMeleeWeapon(stack)) {
            return 1;
        }
        return 2;
    }

    private static int weaponTier(ItemStack stack) {
        if (isTridentWeapon(stack)) {
            return 4;
        }
        if (isCrossbowWeapon(stack)) {
            return 2;
        }
        if (isBowWeapon(stack)) {
            return 1;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (itemPath.startsWith("netherite_")) {
            return 4;
        }
        if (itemPath.startsWith("diamond_")) {
            return 3;
        }
        if (itemPath.startsWith("iron_")) {
            return 2;
        }
        if (itemPath.startsWith("stone_")) {
            return 1;
        }
        if (itemPath.startsWith("wooden_") || itemPath.startsWith("golden_")) {
            return 0;
        }
        return 0;
    }

    private record CachedWeaponSearch(ItemEntity itemEntity, long expiresGameTime) {
        private boolean isValid(long gameTime) {
            return gameTime < this.expiresGameTime;
        }
    }
}
