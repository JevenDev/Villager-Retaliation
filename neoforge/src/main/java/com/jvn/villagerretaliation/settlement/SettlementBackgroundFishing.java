package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.WorkBlocker;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.FishingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.skill.VillagerSkillPractice;
import com.jvn.villagerretaliation.villager.VillagerEquipmentMending;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class SettlementBackgroundFishing {
    private static final String CAST = "SettlementFishingCast";
    private static final String CATCH = "SettlementFishingCatch";
    private static final String EXPERIENCE = "SettlementFishingExperience";
    private static final String HOOK = "FishingActiveHookId";
    private static final String WATER = "FishingWaterPos";
    private static final String APPROACH = "FishingApproachPos";
    private static final List<String> RETURN_TAGS = List.of(CAST, CATCH, EXPERIENCE, WATER, APPROACH,
            "FishingCatchCompleted", "FishingCatchOverflow", "FishingCatchPracticeUnits", "FishingCatchRepetitionKey");

    private SettlementBackgroundFishing() {
    }

    static void capture(ServerLevel level, Villager villager, CompoundTag state) {
        var hook = ownedHook(level, villager, state);
        if (hook == null) return;
        state.remove(CAST);
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        var rod = HiredJobInventory.getJobInventory(villager).getItem(HiredJobInventory.MAINHAND_SLOT);
        if (duty == null || !hook.canResumeFishing() || !isRod(rod) || !state.contains(APPROACH, Tag.TAG_LONG)) return;
        var approach = BlockPos.of(state.getLong(APPROACH));
        var water = hook.blockPosition();
        if (water.distSqr(approach) > 64.0D) return;
        var spot = SettlementFishingSpot.capture(level, duty.area(), water, approach, null);
        if (spot == null || !spot.valid()) return;
        var spots = new ArrayList<SettlementFishingSpot>();
        spots.add(spot);
        duty.town().fishingSpots().getOrDefault(duty.area().id(), List.of()).stream()
                .filter(previous -> !previous.water().equals(water)).limit(SettlementFisheryService.MAX_SPOTS - 1L).forEach(spots::add);
        duty.town().recordFishingSpots(duty.area().id(), spots);
        state.put(CAST, cast(level, rod, water, approach, hook.captureFishingProgress()));
        state.putLong(WATER, water.asLong());
    }

    static void restore(SettlementWorkerState worker, Villager villager) {
        if (worker.role() != HiredVillagerRole.FISHING && !hasPendingCatch(worker.settings())
                && !worker.settings().contains(EXPERIENCE, Tag.TAG_LIST)) return;
        var state = HiredWorkStateStore.state(villager);
        var hook = ownedHook((ServerLevel) villager.level(), villager, state);
        if (hook != null) hook.discard();
        state.remove(HOOK);
        for (String key : RETURN_TAGS) {
            state.remove(key);
            if (worker.settings().contains(key)) state.put(key, worker.settings().get(key).copy());
        }
    }

    public static void discardCast(CompoundTag state) {
        state.remove(CAST);
    }

    public static boolean hasPendingCatch(CompoundTag state) {
        return state.contains(CATCH, Tag.TAG_COMPOUND);
    }

    public static void resume(CompoundTag state, ItemStack rod, BlockPos water, BlockPos approach, VillagerFishingHook hook) {
        var saved = state.getCompound(CAST);
        state.remove(CAST);
        if (matches((ServerLevel) hook.level(), saved, rod, water, approach)) {
            hook.resumeFishingAt(water, saved.getCompound("Progress"));
        }
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier, long now) {
        if (SettlementGatheringPolicy.hold(level, town, worker, elapsed, multiplier)) return;
        var before = SettlementGatheringPolicy.before(level, town, worker);
        try {
            workTick(level, town, worker, elapsed, multiplier, now);
        } finally {
            SettlementGatheringPolicy.received(level, town, worker, before);
        }
    }

    private static void workTick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier, long now) {
        migrateExperience(level, town, worker);
        var state = worker.settings();
        if (level.getEntity(worker.id()) instanceof Villager villager) {
            var liveState = HiredWorkStateStore.state(villager);
            var hook = ownedHook(level, villager, liveState);
            if (hook != null) {
                var observed = liveState.copy();
                capture(level, villager, observed);
                state.remove(CAST);
                if (observed.contains(CAST)) {
                    state.put(CAST, observed.getCompound(CAST).copy());
                    state.putLong(WATER, observed.getLong(WATER));
                    state.putLong(APPROACH, observed.getLong(APPROACH));
                }
                hook.discard();
                worker.changed();
            }
        }
        if (state.contains(HOOK)) {
            state.remove(HOOK);
            worker.changed();
        }
        if (state.getBoolean("FishingCatchCompleted")) {
            worker.practice(level, FishingWorker.takeCompletedPractice(state));
            worker.changed();
        }
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if ((!state.contains("AutoDepositOutputs") || state.getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) return;
        if (hasPendingCatch(state)) {
            finishBackgroundCatch(level, worker);
            return;
        }
        var carried = worker.items(level.registryAccess());
        if (!isRod(carried.get(HiredJobInventory.MAINHAND_SLOT))) {
            if (state.contains(CAST)) {
                state.remove(CAST);
                worker.changed();
            }
            SettlementBackgroundTools.fetchTool(level, town, worker, depots,
                    com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.of(
                            com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.Kind.ROD),
                    ignored -> 1, "rod", new SettlementWorkBudget(worker, elapsed, multiplier));
            return;
        }
        com.jvn.villagerretaliation.interaction.work.HiredFishingSpots preferences;
        try {
            preferences = com.jvn.villagerretaliation.interaction.work.HiredFishingSpots.read(state);
        } catch (IllegalArgumentException invalid) {
            worker.status("fishing_preferences_invalid");
            return;
        }
        var area = town.areas().get(worker.area());
        var activeCast = state.getCompound(CAST);
        var spots = town.fishingSpots().getOrDefault(worker.area(), List.of()).stream()
                .filter(spot -> spot.within(area) && spot.valid())
                .filter(spot -> activeCast.contains("Water", Tag.TAG_LONG) && activeCast.getLong("Water") == spot.water().asLong()
                        || preferences.selects(spot.water()))
                .sorted(Comparator.<SettlementFishingSpot>comparingInt(spot ->
                        activeCast.contains("Water", Tag.TAG_LONG) && activeCast.getLong("Water") == spot.water().asLong() ? 0 : 1)
                        .thenComparingInt(spot -> preferences.priority(spot.water()))
                        .thenComparingDouble(spot -> spot.approach().distSqr(worker.position()))).toList();
        if (spots.isEmpty()) {
            if (state.contains(CAST)) {
                state.remove(CAST);
                worker.changed();
            }
            worker.status(preferences.positions().isEmpty() ? "no_observed_fishery" : "fishing_selected_unavailable",
                    preferences.positions().isEmpty() ? WorkBlocker.NO_OBSERVED_WORK : WorkBlocker.NONE);
            return;
        }
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var spot = spots.stream().filter(candidate -> SettlementFishingClaims.available(level, worker.id(), candidate.water()))
                .findFirst().orElse(null);
        if (spot == null) {
            worker.status("fishing_spot_claimed");
            return;
        }
        owner.setPos(spot.approach().getBottomCenter());
        var inventory = HiredJobInventory.getJobInventory(owner);
        var rod = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        var saved = state.getCompound(CAST);
        long lastNativeTick = saved.contains("Progress") ? saved.getCompound("Progress").getLong("UpdatedAt") : -1L;
        int availableTicks = lastNativeTick < 0L ? elapsed : (int) Math.clamp(now - lastNativeTick, 0L, elapsed);
        worker.work(availableTicks, multiplier);
        if (!matches(level, saved, rod, spot.water(), spot.approach())) {
            double cost = 40.0D + 16.0D * Math.sqrt(worker.position().distSqr(spot.approach()));
            if (!worker.spend(cost)) {
                worker.status("moving_to_water");
                return;
            }
            var hook = hook(level, owner, rod, worker, spot);
            saved = cast(level, rod, spot.water(), spot.approach(), hook.captureFishingProgress());
            state.put(CAST, saved);
            state.putLong(WATER, spot.water().asLong());
            state.putLong(APPROACH, spot.approach().asLong());
            hook.discard();
        }
        var hook = hook(level, owner, rod, worker, spot);
        hook.restoreFishingProgress(saved.getCompound("Progress"));
        hook.backgroundOpenWater(hook.isOpenWaterFishing() && spot.openWater());
        int ticks = (int) Math.min(worker.progress(), Math.min(200, availableTicks));
        if (ticks <= 0 || !worker.spend(ticks)) {
            hook.discard();
            worker.status("waiting_for_bite");
            return;
        }
        hook.advanceBackground(ticks, level.isRaining() && spot.rainExposed(), spot.skyVisible());
        saved.put("Progress", hook.captureFishingProgress());
        if (!hook.isBiting()) {
            hook.discard();
            worker.status("waiting_for_bite");
            return;
        }
        var result = hook.retrieve(rod);
        boolean mending = VillagerEquipmentMending.hasRepairWithXpEffect(rod, EquipmentSlot.MAINHAND, owner);
        if (result.rodDamage() > 0) {
            rod.hurtAndBreak(result.rodDamage(), owner, EquipmentSlot.MAINHAND);
            inventory.syncMainHandEquipment();
            inventory.setChanged();
        }
        if (mending && result.experience() > 0) {
            var position = owner.position().add(0.0D, 0.5D, 0.5D);
            var experience = VillagerWorkExperience.create(level, owner, position, result.experience());
            SettlementGroundExperienceService.submit(level, town, experience, level.getGameTime());
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) HiredJobInventory.synchronizeEquipmentDurability(owner, slot);
        worker.inventorySnapshot(inventory.captureSnapshot());
        var pending = new CompoundTag();
        var items = new ListTag();
        for (ItemStack item : result.items()) {
            if (!item.isEmpty()) items.add(item.save(level.registryAccess()));
        }
        pending.put("Items", items);
        state.put(CATCH, pending);
        state.remove(CAST);
        worker.changed();
        finishBackgroundCatch(level, worker);
    }

    private static VillagerFishingHook hook(ServerLevel level, Villager owner, ItemStack rod,
                                            SettlementWorkerState worker, SettlementFishingSpot spot) {
        int luck = EnchantmentHelper.getFishingLuckBonus(level, rod, owner);
        int lure = (int) (EnchantmentHelper.getFishingTimeReduction(level, rod, owner) * 20.0F);
        var hook = new VillagerFishingHook(owner, level, spot.water().getCenter(), luck, lure,
                HiredVillagerRoles.skillWorkSpeedPercent(worker.aptitude()));
        hook.setPos(spot.water().getCenter());
        return hook;
    }

    private static CompoundTag cast(ServerLevel level, ItemStack rod, BlockPos water, BlockPos approach, CompoundTag progress) {
        var tag = new CompoundTag();
        tag.put("Rod", rod.save(level.registryAccess()));
        tag.putLong("Water", water.asLong());
        tag.putLong("Approach", approach.asLong());
        tag.put("Progress", progress);
        return tag;
    }

    private static boolean matches(ServerLevel level, CompoundTag cast, ItemStack rod, BlockPos water, BlockPos approach) {
        return cast.contains("Progress", Tag.TAG_COMPOUND)
                && cast.getLong("Water") == water.asLong() && cast.getLong("Approach") == approach.asLong()
                && ItemStack.matches(ItemStack.parseOptional(level.registryAccess(), cast.getCompound("Rod")), rod);
    }

    private static VillagerFishingHook ownedHook(ServerLevel level, Villager villager, CompoundTag state) {
        return state.contains(HOOK, Tag.TAG_INT) && level.getEntity(state.getInt(HOOK)) instanceof VillagerFishingHook hook
                && hook.isAlive() && hook.getOwner() == villager ? hook : null;
    }

    private static List<ItemStack> catchItems(ServerLevel level, CompoundTag state) {
        List<ItemStack> items = new ArrayList<>();
        for (Tag raw : state.getCompound(CATCH).getList("Items", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) raw);
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }

    private static List<VillagerSkillPractice> practice(List<ItemStack> items) {
        double units = 0.0D;
        long key = 1L;
        for (ItemStack item : items) {
            var practice = HiredWorkPractice.fishing(item).getFirst();
            units += practice.units();
            key = 31L * key + practice.repetitionKey();
        }
        return units > 0.0D ? HiredWorkPractice.fishing(Math.min(3.0D, units), key) : List.of();
    }

    private static void finishBackgroundCatch(ServerLevel level, SettlementWorkerState worker) {
        var items = catchItems(level, worker.settings());
        var updated = worker.items(level.registryAccess());
        if (!SettlementProductionInventory.insertOutputs(worker, updated, items)) {
            worker.status("output_full");
            return;
        }
        worker.updateInventory(level.registryAccess(), updated);
        worker.settings().remove(CATCH);
        worker.changed();
        worker.practice(level, practice(items));
        worker.status("caught_fish");
    }

    public static boolean finishReturnedCatch(ServerLevel level, Villager villager) {
        var state = HiredWorkStateStore.state(villager);
        if (!hasPendingCatch(state)) return true;
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        var worker = town == null ? null : town.workers().get(villager.getUUID());
        if (worker == null) return false;
        var snapshot = HiredJobInventory.getJobInventory(villager).captureSnapshot();
        var staged = worker.save();
        staged.put("Inventory", snapshot);
        var layout = SettlementWorkerState.load(staged);
        var updated = layout.items(level.registryAccess());
        var items = catchItems(level, state);
        if (!SettlementProductionInventory.insertOutputs(layout, updated, items)) return false;
        ContainerHelper.saveAllItems(snapshot, updated, true, level.registryAccess());
        HiredJobInventory.getJobInventory(villager).restoreSnapshot(snapshot);
        state.remove(CATCH);
        com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.caught(state);
        com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.validate(level, villager,
                com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.resolve(level, villager));
        com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.copyState(state, worker.settings());
        worker.settings().remove(CATCH);
        worker.inventorySnapshot(snapshot);
        HiredWorkSkillGrowthService.onPractice(level, villager, null, HiredVillagerRole.FISHING, state, practice(items));
        town.changed();
        return true;
    }

    static void migrateExperience(ServerLevel level, SettlementRecord town, SettlementWorkerState worker) {
        var state = worker.settings();
        var experience = state.getList(EXPERIENCE, Tag.TAG_COMPOUND);
        if (experience.isEmpty()) return;
        for (int index = 0; index < experience.size(); index++) {
            var xp = experience.getCompound(index);
            int value = xp.getInt("Value");
            long remaining = xp.getLong("Expires") - level.getGameTime();
            if (value <= 0 || remaining <= 0L) continue;
            String identity = "SettlementFishingExperience:" + worker.id() + ":" + xp.getLong("Expires") + ":" + index;
            var id = xp.hasUUID("Id") ? xp.getUUID("Id")
                    : java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var orb = VillagerWorkExperience.create(level, worker.id(), worker.position().getBottomCenter().add(0.0D, 0.5D, 0.5D), value);
            orb.setUUID(id);
            ((com.jvn.villagerretaliation.mixin.ExperienceOrbAccessor) orb).villagerretaliation$setAge((int) Math.clamp(6_000L - remaining, 0L, 6_000L));
            SettlementGroundExperienceService.submit(level, town, orb, level.getGameTime());
        }
        state.remove(EXPERIENCE);
        var entity = level.getEntity(worker.id());
        var villager = entity instanceof Villager nativeVillager ? nativeVillager : null;
        worker.settingsChanged(villager);
        if (villager != null) HiredWorkStateStore.state(villager).remove(EXPERIENCE);
        town.changed();
    }

    private static boolean isRod(ItemStack stack) {
        return FishingWorker.isFishingRod(stack);
    }
}
